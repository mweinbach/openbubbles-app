package app.openbubbles.nativeapp.ui.map

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.openbubbles.nativeapp.data.MapTileDownloadFence
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.coroutines.resume

/**
 * Where map imagery comes from.
 *
 * The default is the OpenStreetMap standard raster style: no key, no account,
 * and an attribution requirement the map chrome satisfies. [attribution] is not
 * decorative — it is a licence condition, so it renders whenever tiles do.
 */
data class MapTileSource(
    val name: String,
    val urlTemplate: String,
    val attribution: String,
    val maxZoom: Int,
) {
    fun url(tile: TileId): String = urlTemplate
        .replace("{z}", tile.zoom.toString())
        .replace("{x}", tile.x.toString())
        .replace("{y}", tile.y.toString())

    companion object {
        val OpenStreetMap = MapTileSource(
            name = "OpenStreetMap",
            urlTemplate = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
            attribution = "© OpenStreetMap contributors",
            maxZoom = 19,
        )
    }
}

/**
 * Fetches and caches raster map tiles.
 *
 * Three layers, cheapest first: decoded bitmaps in memory, PNG bytes on disk,
 * then the network. A tile that cannot be fetched is simply absent — the map
 * draws its own graticule underneath, so the markers stay usable offline
 * instead of the screen going blank.
 *
 * Tile requests reveal roughly where a located device is to whoever serves the
 * imagery, so they only ever happen for tiles the user is actually looking at,
 * and the whole layer can be switched off (see `MapPrefs`).
 */
class MapTileStore(
    private val cacheRoot: File,
    private val client: OkHttpClient,
    private val userAgent: String,
    val source: MapTileSource = MapTileSource.OpenStreetMap,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    private val memory = object : LruCache<TileId, ImageBitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: TileId, value: ImageBitmap): Int =
            (value.width.toLong() * value.height * 4L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /** Retry deadlines for transient failures; a brief outage must not poison a tile forever. */
    private val failedUntilNanos = ConcurrentHashMap<TileId, Long>()

    // Serving a screen of tiles must not open twenty sockets at once.
    private val network = Semaphore(4)

    fun cached(tile: TileId): ImageBitmap? = memory[tile]

    /**
     * Returns the tile, loading it from disk or the network when needed, or null
     * when it is unavailable. Cancellation is cooperative: a tile scrolled out of
     * view stops being fetched.
     */
    suspend fun load(tile: TileId): ImageBitmap? {
        memory[tile]?.let { return it }
        failedUntilNanos[tile]?.let { retryAt ->
            if (nowNanos() < retryAt) return null
            failedUntilNanos.remove(tile, retryAt)
        }
        if (tile.zoom > source.maxZoom) return null
        return withContext(Dispatchers.IO) {
            val file = fileFor(tile)
            decode(file)?.let { return@withContext it.also { image -> memory.put(tile, image) } }
            if (file.exists()) runCatching { file.delete() }
            network.withPermit {
                val call = client.newCall(requestFor(tile))
                val lease = MapTileDownloadFence.begin(call::cancel)
                try {
                    val bytes = download(call) ?: run {
                        failedUntilNanos[tile] = nowNanos() + FAILURE_RETRY_NANOS
                        return@withPermit null
                    }
                    if (!MapTileDownloadFence.isCurrent(lease)) return@withPermit null
                    runCatching {
                        file.parentFile?.mkdirs()
                        val part = File(file.parentFile, file.name + ".part")
                        part.writeBytes(bytes)
                        // Publish atomically: a half-written tile must never be decoded
                        // on the next pan.
                        if (!part.renameTo(file)) part.delete()
                    }
                    pruneIfNeeded()
                    val decoded = decode(file) ?: decodeBytes(bytes)
                    if (decoded == null) {
                        runCatching { file.delete() }
                        failedUntilNanos[tile] = nowNanos() + FAILURE_RETRY_NANOS
                    } else if (MapTileDownloadFence.isCurrent(lease)) {
                        failedUntilNanos.remove(tile)
                        memory.put(tile, decoded)
                    }
                    decoded.takeIf { MapTileDownloadFence.isCurrent(lease) }
                } finally {
                    MapTileDownloadFence.complete(lease)
                }
            }
        }
    }

    fun clear() {
        memory.evictAll()
        failedUntilNanos.clear()
        runCatching { cacheRoot.deleteRecursively() }
    }

    private fun fileFor(tile: TileId): File =
        File(cacheRoot, "${tile.zoom}/${tile.x}_${tile.y}.png")

    private fun decode(file: File): ImageBitmap? {
        if (!file.isFile || file.length() !in 1L..MAX_ENCODED_BYTES.toLong()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (!validBounds(bounds)) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }
            .getOrNull()
    }

    private fun decodeBytes(bytes: ByteArray): ImageBitmap? {
        if (bytes.isEmpty() || bytes.size > MAX_ENCODED_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (!validBounds(bounds)) return null
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
            .getOrNull()
    }

    private fun validBounds(options: BitmapFactory.Options): Boolean =
        options.outWidth in 1..MAX_IMAGE_DIMENSION &&
            options.outHeight in 1..MAX_IMAGE_DIMENSION &&
            options.outWidth.toLong() * options.outHeight <= MAX_IMAGE_PIXELS

    private fun requestFor(tile: TileId): Request = Request.Builder()
            .url(source.url(tile))
            // Tile servers require a real identifying agent and reject generic
            // library defaults.
            .header("User-Agent", userAgent)
            .header("Accept", "image/png,image/*")
            .build()

    private suspend fun download(call: Call): ByteArray? =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            val result = execute(call)
            if (continuation.isActive) continuation.resume(result)
        }

    private fun execute(call: Call): ByteArray? = try {
        call.execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            val contentLength = body.contentLength()
            if (contentLength > MAX_ENCODED_BYTES.toLong()) return null
            body.byteStream().use(::readBounded)
        }
    } catch (error: IOException) {
        null
    }

    private fun readBounded(input: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_ENCODED_BYTES) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    /**
     * Keeps the on-disk cache bounded. Oldest files go first; imagery is
     * re-fetchable, so dropping too much is only a slower pan.
     */
    private fun pruneIfNeeded() {
        val files = cacheRoot.walkTopDown().filter { it.isFile }.toList()
        if (files.size <= DISK_TILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - DISK_TILES / 2)
            .forEach { runCatching { it.delete() } }
    }

    companion object {
        private const val MEMORY_BYTES = 24 * 1024 * 1024
        private const val DISK_TILES = 1_200
        private const val MAX_ENCODED_BYTES = 1024 * 1024
        private const val MAX_IMAGE_DIMENSION = 1024
        private const val MAX_IMAGE_PIXELS = 1024L * 1024L
        private const val FAILURE_RETRY_NANOS = 30L * 1_000_000_000L

        fun create(
            context: Context,
            versionName: String,
            source: MapTileSource = MapTileSource.OpenStreetMap,
        ): MapTileStore = MapTileStore(
            cacheRoot = File(context.cacheDir, "map_tiles"),
            client = OkHttpClient(),
            userAgent = "OpenBubbles/$versionName (Android; +https://github.com/mweinbach/openbubbles-app)",
            source = source,
        )
    }
}
