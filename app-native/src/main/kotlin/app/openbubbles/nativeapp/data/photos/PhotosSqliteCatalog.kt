package app.openbubbles.nativeapp.data.photos

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import app.openbubbles.core.photos.CachedPhotos
import app.openbubbles.core.photos.PhotoMediaKind
import app.openbubbles.core.photos.PhotoResourceKind
import app.openbubbles.core.photos.PhotoSummary
import app.openbubbles.core.photos.PhotoTransfer
import app.openbubbles.core.photos.PhotoTransferDirection
import app.openbubbles.core.photos.PhotoTransferOrigin
import app.openbubbles.core.photos.PhotoTransferState
import app.openbubbles.core.photos.PhotosCatalog
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Separate, disposable Photos catalog. This deliberately does not touch the
 * legacy ObjectBox model or its in-place-upgrade path.
 */
class PhotosSqliteCatalog(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION),
    PhotosCatalog {

    private val metadataMutationLock = Any()

    @Volatile
    private var metadataBaseline: PhotoMetadataBaseline? = null

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(database: SQLiteDatabase) {
        super.onConfigure(database)
        database.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(database: SQLiteDatabase) {
        CREATE_STATEMENTS.forEach(database::execSQL)
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        migrationStatements(oldVersion, newVersion).forEach(database::execSQL)
    }

    override suspend fun loadMetadata(): CachedPhotos = withContext(Dispatchers.IO) {
        val database = readableDatabase
        val assets = database.query(
            ASSETS_TABLE,
            null,
            null,
            null,
            null,
            null,
            "captured_at_ms DESC, added_at_ms DESC, master_id",
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.photoSummary()) } }
        val nextCursor = database.query(
            SYNC_TABLE,
            arrayOf("next_cursor"),
            "sync_key = ?",
            arrayOf(METADATA_SYNC_KEY),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
        }
        CachedPhotos(assets = assets, nextCursor = nextCursor)
    }

    override suspend fun replaceMetadata(
        assets: List<PhotoSummary>,
        nextCursor: String?,
    ): Unit = withContext(Dispatchers.IO) {
        synchronized(metadataMutationLock) {
            val database = writableDatabase
            var committedBaseline: PhotoMetadataBaseline? = null
            database.transaction {
                val currentRevision = metadataRevision(database)
                val previousAssets = metadataBaseline
                    ?.takeIf { baseline -> currentRevision != null && baseline.revision == currentRevision }
                    ?.assetsById
                    ?: persistedMetadata(database)
                val mutation = planPhotoMetadataMutation(previousAssets, assets)

                photoMetadataDeletionBatches(mutation.removedAssetIds).forEach { removedIds ->
                    val placeholders = List(removedIds.size) { "?" }.joinToString(",")
                    check(
                        database.delete(
                            ASSETS_TABLE,
                            "master_id IN ($placeholders)",
                            removedIds.toTypedArray(),
                        ) == removedIds.size,
                    ) { "Photos metadata changed during a deletion" }
                }
                mutation.insertedAssets.forEach { asset ->
                    database.insertOrThrow(ASSETS_TABLE, null, asset.contentValues())
                }
                mutation.updatedAssets.forEach { asset ->
                    check(
                        database.update(
                            ASSETS_TABLE,
                            asset.contentValues(),
                            "master_id = ?",
                            arrayOf(asset.id),
                        ) == 1,
                    ) { "Photos metadata changed during an update" }
                }

                val nextRevision = photoMetadataRevisionAllocator.reserve(
                    currentRevision,
                    System.currentTimeMillis(),
                )
                database.insertWithOnConflict(
                    SYNC_TABLE,
                    null,
                    ContentValues().apply {
                        put("sync_key", METADATA_SYNC_KEY)
                        if (nextCursor == null) putNull("next_cursor") else put("next_cursor", nextCursor)
                        put("updated_at_ms", nextRevision)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                committedBaseline = PhotoMetadataBaseline(nextRevision, mutation.assetsById)
            }
            // Never expose an optimistic baseline: a failed transaction must
            // make the next pass derive its state from committed SQLite rows.
            metadataBaseline = checkNotNull(committedBaseline)
        }
    }

    private fun metadataRevision(database: SQLiteDatabase): Long? = database.query(
        SYNC_TABLE,
        arrayOf("updated_at_ms"),
        "sync_key = ?",
        arrayOf(METADATA_SYNC_KEY),
        null,
        null,
        null,
        "1",
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

    private fun persistedMetadata(database: SQLiteDatabase): Map<String, PhotoSummary> = database.query(
        ASSETS_TABLE,
        null,
        null,
        null,
        null,
        null,
        null,
    ).use { cursor ->
        buildMap {
            while (cursor.moveToNext()) {
                val asset = cursor.photoSummary()
                put(asset.id, asset)
            }
        }
    }

    override suspend fun transfers(): List<PhotoTransfer> = withContext(Dispatchers.IO) {
        readableDatabase.query(
            TRANSFERS_TABLE,
            null,
            null,
            null,
            null,
            null,
            "updated_at_ms DESC",
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.photoTransfer()) } }
    }

    override suspend fun transfer(id: String): PhotoTransfer? = withContext(Dispatchers.IO) {
        readableDatabase.query(
            TRANSFERS_TABLE,
            null,
            "transfer_id = ?",
            arrayOf(id),
            null,
            null,
            null,
            "1",
        ).use { cursor -> if (cursor.moveToFirst()) cursor.photoTransfer() else null }
    }

    override suspend fun putTransfer(transfer: PhotoTransfer): Unit = withContext(Dispatchers.IO) {
        writableDatabase.insertWithOnConflict(
            TRANSFERS_TABLE,
            null,
            transfer.contentValues(),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    override suspend fun recoverInterruptedTransfers(): Unit = withContext(Dispatchers.IO) {
        writableDatabase.update(
            TRANSFERS_TABLE,
            ContentValues().apply {
                put("state", PhotoTransferState.Queued.name)
                put("last_error", "Transfer interrupted by app restart")
                put("updated_at_ms", System.currentTimeMillis())
            },
            "state = ?",
            arrayOf(PhotoTransferState.Running.name),
        )
    }

    /** Clears every account-derived catalog row in one transaction. */
    suspend fun clearAccountData(): Unit = withContext(Dispatchers.IO) {
        synchronized(metadataMutationLock) {
            val database = writableDatabase
            database.transaction {
                ACCOUNT_CLEAR_TABLES.forEach { table -> database.delete(table, null, null) }
            }
            metadataBaseline = null
        }
    }

    private fun PhotoSummary.contentValues() = ContentValues().apply {
        put("master_id", id)
        put("asset_id", assetId)
        putNullable("filename", filename)
        put("media_kind", mediaKind.name)
        put("live_photo", livePhoto.asInt())
        putNullable("width", width)
        putNullable("height", height)
        putNullable("original_size", originalSize)
        putNullable("preview_size", previewSize)
        putNullable("live_photo_video_size", livePhotoVideoSize)
        putNullable("captured_at_ms", capturedAtMs)
        putNullable("added_at_ms", addedAtMs)
        put("favorite", favorite.asInt())
        put("hidden", hidden.asInt())
    }

    private fun PhotoTransfer.contentValues() = ContentValues().apply {
        put("transfer_id", id)
        putNullable("asset_id", assetId)
        put("direction", direction.name)
        put("resource_kind", resourceKind.name)
        put("local_path", localPath)
        putNullable("filename", filename)
        putNullable("mime_type", mimeType)
        put("state", state.name)
        put("bytes_done", bytesDone)
        put("total_bytes", totalBytes)
        put("attempt_count", attemptCount)
        putNullable("last_error", lastError)
        put("created_at_ms", createdAtMs)
        put("updated_at_ms", updatedAtMs)
        put("origin", origin.name)
    }

    private fun Cursor.photoSummary() = PhotoSummary(
        id = string("master_id"),
        assetId = string("asset_id"),
        filename = nullableString("filename"),
        mediaKind = enumValue("media_kind", PhotoMediaKind.Unknown),
        livePhoto = int("live_photo") != 0,
        width = nullableInt("width"),
        height = nullableInt("height"),
        originalSize = nullableLong("original_size"),
        previewSize = nullableLong("preview_size"),
        capturedAtMs = nullableLong("captured_at_ms"),
        addedAtMs = nullableLong("added_at_ms"),
        favorite = int("favorite") != 0,
        hidden = int("hidden") != 0,
        livePhotoVideoSize = nullableLong("live_photo_video_size"),
    )

    private fun Cursor.photoTransfer() = PhotoTransfer(
        id = string("transfer_id"),
        assetId = nullableString("asset_id"),
        direction = enumValue("direction", PhotoTransferDirection.Download),
        resourceKind = enumValue("resource_kind", PhotoResourceKind.Preview),
        localPath = string("local_path"),
        filename = nullableString("filename"),
        mimeType = nullableString("mime_type"),
        state = enumValue("state", PhotoTransferState.Failed),
        bytesDone = long("bytes_done"),
        totalBytes = long("total_bytes"),
        attemptCount = int("attempt_count"),
        lastError = nullableString("last_error"),
        createdAtMs = long("created_at_ms"),
        updatedAtMs = long("updated_at_ms"),
        origin = enumValue("origin", PhotoTransferOrigin.Manual),
    )

    private inline fun <reified T : Enum<T>> Cursor.enumValue(column: String, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == string(column) } ?: fallback

    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))
    private fun Cursor.nullableString(column: String): String? =
        getColumnIndexOrThrow(column).let { index -> if (isNull(index)) null else getString(index) }
    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
    private fun Cursor.nullableInt(column: String): Int? =
        getColumnIndexOrThrow(column).let { index -> if (isNull(index)) null else getInt(index) }
    private fun Cursor.nullableLong(column: String): Long? =
        getColumnIndexOrThrow(column).let { index -> if (isNull(index)) null else getLong(index) }

    private fun Boolean.asInt(): Int = if (this) 1 else 0

    private fun ContentValues.putNullable(key: String, value: String?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun ContentValues.putNullable(key: String, value: Int?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun ContentValues.putNullable(key: String, value: Long?) {
        if (value == null) putNull(key) else put(key, value)
    }

    companion object {
        const val DATABASE_NAME = "openbubbles-photos.db"
        const val DATABASE_VERSION = 3

        private const val ASSETS_TABLE = "photo_assets"
        private const val SYNC_TABLE = "photo_sync_state"
        private const val TRANSFERS_TABLE = "photo_transfers"
        private const val METADATA_SYNC_KEY = "personal_metadata"

        internal val ACCOUNT_CLEAR_TABLES = listOf(
            TRANSFERS_TABLE,
            ASSETS_TABLE,
            SYNC_TABLE,
        )

        private const val CREATE_ASSETS = """
            CREATE TABLE photo_assets (
                master_id TEXT PRIMARY KEY NOT NULL,
                asset_id TEXT NOT NULL,
                filename TEXT,
                media_kind TEXT NOT NULL,
                live_photo INTEGER NOT NULL,
                width INTEGER,
                height INTEGER,
                original_size INTEGER,
                preview_size INTEGER,
                live_photo_video_size INTEGER,
                captured_at_ms INTEGER,
                added_at_ms INTEGER,
                favorite INTEGER NOT NULL,
                hidden INTEGER NOT NULL
            )
        """
        private const val CREATE_SYNC_STATE = """
            CREATE TABLE photo_sync_state (
                sync_key TEXT PRIMARY KEY NOT NULL,
                next_cursor TEXT,
                updated_at_ms INTEGER NOT NULL
            )
        """
        private const val CREATE_TRANSFERS = """
            CREATE TABLE photo_transfers (
                transfer_id TEXT PRIMARY KEY NOT NULL,
                asset_id TEXT,
                direction TEXT NOT NULL,
                resource_kind TEXT NOT NULL,
                local_path TEXT NOT NULL,
                filename TEXT,
                mime_type TEXT,
                state TEXT NOT NULL,
                bytes_done INTEGER NOT NULL,
                total_bytes INTEGER NOT NULL,
                attempt_count INTEGER NOT NULL,
                last_error TEXT,
                created_at_ms INTEGER NOT NULL,
                updated_at_ms INTEGER NOT NULL,
                origin TEXT NOT NULL DEFAULT 'Manual'
            )
        """
        private const val CREATE_TRANSFER_ASSET_INDEX =
            "CREATE INDEX photo_transfers_asset_idx ON photo_transfers(asset_id)"
        private const val CREATE_TRANSFER_STATE_INDEX =
            "CREATE INDEX photo_transfers_state_idx ON photo_transfers(state)"

        internal val CREATE_STATEMENTS = listOf(
            CREATE_ASSETS.trimIndent(),
            CREATE_SYNC_STATE.trimIndent(),
            CREATE_TRANSFERS.trimIndent(),
            CREATE_TRANSFER_ASSET_INDEX,
            CREATE_TRANSFER_STATE_INDEX,
        )

        internal fun migrationStatements(oldVersion: Int, newVersion: Int): List<String> = when {
            oldVersion == newVersion -> emptyList()
            oldVersion == 1 && newVersion == 2 -> listOf(
                "ALTER TABLE photo_transfers ADD COLUMN origin TEXT NOT NULL DEFAULT 'Manual'",
            )
            oldVersion == 2 && newVersion == 3 -> listOf(
                "ALTER TABLE photo_assets ADD COLUMN live_photo_video_size INTEGER",
            )
            oldVersion == 1 && newVersion == 3 -> listOf(
                "ALTER TABLE photo_transfers ADD COLUMN origin TEXT NOT NULL DEFAULT 'Manual'",
                "ALTER TABLE photo_assets ADD COLUMN live_photo_video_size INTEGER",
            )
            else -> error(
                "Missing Photos catalog migration from version $oldVersion to $newVersion",
            )
        }
    }
}

private data class PhotoMetadataBaseline(
    val revision: Long,
    val assetsById: Map<String, PhotoSummary>,
)

private val photoMetadataRevisionAllocator = PhotoMetadataRevisionAllocator()

/** SQLite work is proportional to a changed page, even when callers pass the full accumulated snapshot. */
internal data class PhotoMetadataMutationPlan(
    val insertedAssets: List<PhotoSummary>,
    val updatedAssets: List<PhotoSummary>,
    val removedAssetIds: List<String>,
    val assetsById: Map<String, PhotoSummary>,
) {
    val assetWriteCount: Int
        get() = insertedAssets.size + updatedAssets.size + removedAssetIds.size
}

/** Duplicate records resolve to the newest occurrence, matching [app.openbubbles.core.photos.PhotosBrowser.next]. */
internal fun planPhotoMetadataMutation(
    previousAssets: Map<String, PhotoSummary>,
    incomingAssets: List<PhotoSummary>,
): PhotoMetadataMutationPlan {
    val assetsById = LinkedHashMap<String, PhotoSummary>(incomingAssets.size)
    incomingAssets.forEach { asset -> assetsById[asset.id] = asset }

    val inserted = ArrayList<PhotoSummary>()
    val updated = ArrayList<PhotoSummary>()
    assetsById.forEach { (assetId, asset) ->
        val previous = previousAssets[assetId]
        when {
            previous == null -> inserted += asset
            previous != asset -> updated += asset
        }
    }
    val removed = previousAssets.keys.filterNot(assetsById::containsKey)
    return PhotoMetadataMutationPlan(inserted, updated, removed, assetsById)
}

/** Keep IN clauses comfortably below Android's SQLite host-parameter limit. */
internal const val MAX_PHOTO_METADATA_DELETE_BATCH_SIZE = 250

internal fun photoMetadataDeletionBatches(assetIds: List<String>): List<List<String>> =
    assetIds.chunked(MAX_PHOTO_METADATA_DELETE_BATCH_SIZE)

/** Revisions remain unique across helper instances even after account cleanup deletes the sync row. */
internal class PhotoMetadataRevisionAllocator(initialFloor: Long = Long.MIN_VALUE) {
    private val processFloor = AtomicLong(initialFloor)

    fun reserve(previousRevision: Long?, nowMs: Long): Long {
        val persistedMinimum = nextPhotoMetadataRevision(previousRevision, nowMs)
        while (true) {
            val observedFloor = processFloor.get()
            check(observedFloor != Long.MAX_VALUE) { "Photos metadata revision is exhausted" }
            val nextRevision = maxOf(persistedMinimum, observedFloor + 1)
            if (processFloor.compareAndSet(observedFloor, nextRevision)) return nextRevision
        }
    }
}

/** Wall-clock collisions or backwards clock changes must not hide another catalog instance's write. */
internal fun nextPhotoMetadataRevision(previousRevision: Long?, nowMs: Long): Long {
    check(previousRevision != Long.MAX_VALUE) { "Photos metadata revision is exhausted" }
    return maxOf(nowMs, previousRevision?.plus(1) ?: nowMs)
}
