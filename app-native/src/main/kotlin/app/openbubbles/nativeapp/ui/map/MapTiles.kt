package app.openbubbles.nativeapp.ui.map

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
) {
    private val memory = LruCache<TileId, ImageBitmap>(MEMORY_TILES)

    /** Tiles that returned an error; retried only when the store is cleared. */
    private val failed = HashSet<TileId>()

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
        if (tile in failed) return null
        if (tile.zoom > source.maxZoom) return null
        return withContext(Dispatchers.IO) {
            val file = fileFor(tile)
            decode(file)?.let { return@withContext it.also { image -> memory.put(tile, image) } }
            val bytes = network.withPermit { download(tile) } ?: run {
                failed += tile
                return@withContext null
            }
            runCatching {
                file.parentFile?.mkdirs()
                val part = File(file.parentFile, file.name + ".part")
                part.writeBytes(bytes)
                // Publish atomically: a half-written tile must never be decoded
                // on the next pan.
                if (!part.renameTo(file)) part.delete()
            }
            pruneIfNeeded()
            decode(file)?.also { memory.put(tile, it) }
                ?: decodeBytes(bytes)?.also { memory.put(tile, it) }
        }
    }

    fun clear() {
        memory.evictAll()
        failed.clear()
        runCatching { cacheRoot.deleteRecursively() }
    }

    private fun fileFor(tile: TileId): File =
        File(cacheRoot, "${tile.zoom}/${tile.x}_${tile.y}.png")

    private fun decode(file: File): ImageBitmap? {
        if (!file.isFile || file.length() <= 0L) return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() }
            .getOrNull()
    }

    private fun decodeBytes(bytes: ByteArray): ImageBitmap? =
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
            .getOrNull()

    private fun download(tile: TileId): ByteArray? {
        val request = Request.Builder()
            .url(source.url(tile))
            // Tile servers require a real identifying agent and reject generic
            // library defaults.
            .header("User-Agent", userAgent)
            .header("Accept", "image/png,image/*")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.bytes()
            }
        } catch (error: IOException) {
            null
        }
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
        private const val MEMORY_TILES = 160
        private const val DISK_TILES = 1_200

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
