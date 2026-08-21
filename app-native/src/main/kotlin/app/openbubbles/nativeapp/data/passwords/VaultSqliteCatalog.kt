package app.openbubbles.nativeapp.data.passwords

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.openbubbles.core.passwords.AesGcmVaultFieldCrypto
import app.openbubbles.core.passwords.CachedVault
import app.openbubbles.core.passwords.VaultCatalog
import app.openbubbles.core.passwords.VaultCatalogUnreadable
import app.openbubbles.core.passwords.VaultFieldCrypto
import app.openbubbles.core.passwords.VaultGroupMemberRecord
import app.openbubbles.core.passwords.VaultGroupRecord
import app.openbubbles.core.passwords.VaultInviteRecord
import app.openbubbles.core.passwords.VaultItemKind
import app.openbubbles.core.passwords.VaultItemRecord
import app.openbubbles.core.passwords.VaultSiteSnapshot
import app.openbubbles.core.passwords.openOrNull
import app.openbubbles.core.passwords.sealOrNull
import app.openbubbles.core.passwords.vaultSiteKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Durable iCloud Keychain metadata for Android: the Passwords screen and the
 * credential provider both paint from here before Rust finishes booting.
 *
 * Deliberately its own database, like the Photos catalog — the legacy ObjectBox
 * model is an in-place-upgrade compatibility boundary and must not grow vault
 * tables. Rows hold identity and labels only; every secret stays in the Rust
 * keychain state and is fetched for the lifetime of one request. Even so, the
 * label columns are sealed with an AndroidKeyStore key and sites are looked up
 * through a keyed blind index, because the list of sites and account names is
 * itself an inventory of the user's logins.
 */
class VaultSqliteCatalog(
    context: Context,
    private val crypto: VaultFieldCrypto,
) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION),
    VaultCatalog {

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

    override suspend fun load(): CachedVault = withContext(Dispatchers.IO) {
        readOrReset {
            writableDatabase.inTransaction {
                ensureIndexKey()
                val markers = syncMarkers()
                CachedVault(
                    items = query(
                        ITEMS_TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "kind, sort_position, record_id",
                    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.itemRecord()) } },
                    groups = groups(),
                    invites = query(
                        INVITES_TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "sort_position, invite_id",
                    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.inviteRecord()) } },
                    syncedKinds = VaultItemKind.entries.filter { it.syncKey() in markers.keys }.toSet(),
                    groupsSynced = GROUPS_SYNC_KEY in markers.keys,
                    syncedAtMs = markers.values.maxOrNull(),
                )
            }
        } ?: CachedVault()
    }

    override suspend fun replaceItems(
        kind: VaultItemKind,
        items: List<VaultItemRecord>,
        syncedAtMs: Long,
    ): Unit = withContext(Dispatchers.IO) {
        val database = writableDatabase
        database.inTransaction {
            ensureIndexKey()
            delete(ITEMS_TABLE, "kind = ?", arrayOf(kind.name))
            items.forEachIndexed { index, item ->
                insertWithOnConflict(
                    ITEMS_TABLE,
                    null,
                    item.contentValues(kind, index),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            markSynced(kind.syncKey(), syncedAtMs)
        }
    }

    override suspend fun replaceGroups(
        groups: List<VaultGroupRecord>,
        invites: List<VaultInviteRecord>,
        syncedAtMs: Long,
    ): Unit = withContext(Dispatchers.IO) {
        val database = writableDatabase
        database.inTransaction {
            ensureIndexKey()
            delete(GROUP_MEMBERS_TABLE, null, null)
            delete(GROUPS_TABLE, null, null)
            delete(INVITES_TABLE, null, null)
            groups.forEachIndexed { groupIndex, group ->
                insertWithOnConflict(
                    GROUPS_TABLE,
                    null,
                    ContentValues().apply {
                        put("group_id", group.id)
                        put("name", crypto.seal(group.name))
                        put("owner", group.owner.asInt())
                        put("member_count", group.memberCount)
                        put("sort_position", groupIndex)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
                group.members.forEachIndexed { memberIndex, member ->
                    insertWithOnConflict(
                        GROUP_MEMBERS_TABLE,
                        null,
                        ContentValues().apply {
                            put("group_id", group.id)
                            put("sort_position", memberIndex)
                            putNullable("name", crypto.sealOrNull(member.name))
                            put("handle", crypto.seal(member.handle))
                            put("joined", member.joined.asInt())
                            put("current_user", member.currentUser.asInt())
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            }
            invites.forEachIndexed { index, invite ->
                insertWithOnConflict(
                    INVITES_TABLE,
                    null,
                    ContentValues().apply {
                        put("invite_id", invite.id)
                        put("group_name", crypto.seal(invite.groupName))
                        put("inviter", crypto.seal(invite.inviter))
                        put("sort_position", index)
                    },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            markSynced(GROUPS_SYNC_KEY, syncedAtMs)
        }
    }

    override suspend fun mergeSiteItems(
        site: String,
        kind: VaultItemKind,
        items: List<VaultItemRecord>,
    ): Unit = withContext(Dispatchers.IO) {
        val siteIndex = siteIndex(site) ?: return@withContext
        val database = writableDatabase
        database.inTransaction {
            ensureIndexKey()
            // A per-site hydration replaces exactly that site's rows for the
            // kind and never touches the sync marker: it is not a full listing,
            // so it must not make a cold catalog look complete.
            delete(ITEMS_TABLE, "kind = ? AND site_index = ?", arrayOf(kind.name, siteIndex))
            items.forEachIndexed { index, item ->
                insertWithOnConflict(
                    ITEMS_TABLE,
                    null,
                    item.contentValues(kind, index),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
    }

    override suspend fun credentialsForSite(
        site: String,
        kinds: Set<VaultItemKind>,
    ): VaultSiteSnapshot = withContext(Dispatchers.IO) {
        val siteKey = vaultSiteKey(site)
        if (siteKey == null || kinds.isEmpty()) return@withContext VaultSiteSnapshot(siteKey = siteKey)
        readOrReset {
            writableDatabase.inTransaction {
                ensureIndexKey()
                val markers = syncMarkers()
                val placeholders = kinds.joinToString(",") { "?" }
                VaultSiteSnapshot(
                    siteKey = siteKey,
                    items = query(
                        ITEMS_TABLE,
                        null,
                        "site_index = ? AND kind IN ($placeholders)",
                        arrayOf(crypto.index(siteKey)) + kinds.map { it.name },
                        null,
                        null,
                        "kind, sort_position, record_id",
                    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.itemRecord()) } },
                    syncedKinds = kinds.filter { it.syncKey() in markers.keys }.toSet(),
                    syncedAtMs = kinds.mapNotNull { markers[it.syncKey()] }.minOrNull(),
                )
            }
        } ?: VaultSiteSnapshot(siteKey = siteKey)
    }

    override suspend fun clearAccountData(): Unit = withContext(Dispatchers.IO) {
        writableDatabase.inTransaction {
            ACCOUNT_CLEAR_TABLES.forEach { table -> delete(table, null, null) }
        }
    }

    /**
     * A rotated or invalidated catalog key makes every row unreadable. Wipe the
     * cache and report a cold catalog so the caller re-reads Apple, instead of
     * surfacing a decryption failure as an empty vault.
     */
    private fun <T> readOrReset(read: () -> T): T? = try {
        read()
    } catch (unreadable: VaultCatalogUnreadable) {
        runCatching { writableDatabase.inTransaction { ACCOUNT_CLEAR_TABLES.forEach { delete(it, null, null) } } }
        null
    }

    private fun siteIndex(site: String): String? = vaultSiteKey(site)?.let(crypto::index)

    private fun VaultItemRecord.contentValues(kind: VaultItemKind, position: Int) = ContentValues().apply {
        put("record_id", id)
        put("kind", kind.name)
        put("site_index", siteIndex(site) ?: UNINDEXED_SITE)
        put("site", crypto.seal(site))
        put("title", crypto.seal(title))
        putNullable("username", crypto.sealOrNull(username))
        putNullable("display_name", crypto.sealOrNull(displayName))
        putNullable("webauthn_credential_id", crypto.sealOrNull(webauthnCredentialId))
        putNullable("group_id", groupId)
        putNullable("modified_at_ms", modifiedAtMs)
        put("sort_position", position)
    }

    private fun Cursor.itemRecord() = VaultItemRecord(
        id = string("record_id"),
        kind = VaultItemKind.valueOf(string("kind")),
        site = crypto.open(string("site")),
        title = crypto.open(string("title")),
        username = crypto.openOrNull(nullableString("username")),
        displayName = crypto.openOrNull(nullableString("display_name")),
        webauthnCredentialId = crypto.openOrNull(nullableString("webauthn_credential_id")),
        groupId = nullableString("group_id"),
        modifiedAtMs = nullableLong("modified_at_ms"),
    )

    private fun Cursor.inviteRecord() = VaultInviteRecord(
        id = string("invite_id"),
        groupName = crypto.open(string("group_name")),
        inviter = crypto.open(string("inviter")),
    )

    private fun SQLiteDatabase.groups(): List<VaultGroupRecord> {
        val members = query(
            GROUP_MEMBERS_TABLE,
            null,
            null,
            null,
            null,
            null,
            "group_id, sort_position",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        cursor.string("group_id") to VaultGroupMemberRecord(
                            name = crypto.openOrNull(cursor.nullableString("name")),
                            handle = crypto.open(cursor.string("handle")),
                            joined = cursor.int("joined") != 0,
                            currentUser = cursor.int("current_user") != 0,
                        ),
                    )
                }
            }
        }.groupBy({ it.first }, { it.second })

        return query(
            GROUPS_TABLE,
            null,
            null,
            null,
            null,
            null,
            "sort_position, group_id",
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.string("group_id")
                    add(
                        VaultGroupRecord(
                            id = id,
                            name = crypto.open(cursor.string("name")),
                            owner = cursor.int("owner") != 0,
                            memberCount = cursor.int("member_count"),
                            members = members[id].orEmpty(),
                        ),
                    )
                }
            }
        }
    }

    private fun SQLiteDatabase.syncMarkers(): Map<String, Long> = query(
        SYNC_TABLE,
        arrayOf("sync_key", "updated_at_ms"),
        null,
        null,
        null,
        null,
        null,
    ).use { cursor ->
        buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getLong(1)) }
    }

    private fun SQLiteDatabase.markSynced(syncKey: String, syncedAtMs: Long) {
        insertWithOnConflict(
            SYNC_TABLE,
            null,
            ContentValues().apply {
                put("sync_key", syncKey)
                put("updated_at_ms", syncedAtMs)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    /**
     * A database can outlive its AndroidKeyStore index key after restore or
     * invalidation. Rows indexed by the old HMAC key are authoritative misses,
     * so bind the cache to a keyed marker and make any mismatch cold.
     */
    private fun SQLiteDatabase.ensureIndexKey() {
        val expected = crypto.index(INDEX_KEY_PROBE)
        val stored = query(
            KEY_STATE_TABLE,
            arrayOf("key_check"),
            "key_name = ?",
            arrayOf(INDEX_KEY_NAME),
            null,
            null,
            null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        if (stored == expected) return

        ACCOUNT_CLEAR_TABLES.forEach { table -> delete(table, null, null) }
        insertWithOnConflict(
            KEY_STATE_TABLE,
            null,
            ContentValues().apply {
                put("key_name", INDEX_KEY_NAME)
                put("key_check", expected)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private inline fun <T> SQLiteDatabase.inTransaction(body: SQLiteDatabase.() -> T): T {
        beginTransaction()
        return try {
            body().also { setTransactionSuccessful() }
        } finally {
            endTransaction()
        }
    }

    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))
    private fun Cursor.nullableString(column: String): String? =
        getColumnIndexOrThrow(column).let { index -> if (isNull(index)) null else getString(index) }
    private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
    private fun Cursor.nullableLong(column: String): Long? =
        getColumnIndexOrThrow(column).let { index -> if (isNull(index)) null else getLong(index) }

    private fun Boolean.asInt(): Int = if (this) 1 else 0

    private fun ContentValues.putNullable(key: String, value: String?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun ContentValues.putNullable(key: String, value: Long?) {
        if (value == null) putNull(key) else put(key, value)
    }

    companion object {
        const val DATABASE_NAME = "openbubbles-vault.db"
        const val DATABASE_VERSION = 2

        private const val ITEMS_TABLE = "vault_items"
        private const val GROUPS_TABLE = "vault_groups"
        private const val GROUP_MEMBERS_TABLE = "vault_group_members"
        private const val INVITES_TABLE = "vault_invites"
        private const val SYNC_TABLE = "vault_sync_state"
        private const val KEY_STATE_TABLE = "vault_key_state"
        internal const val GROUPS_SYNC_KEY = "groups"

        private const val INDEX_KEY_NAME = "site-index-v1"
        private const val INDEX_KEY_PROBE = "openbubbles-vault-index-key-check-v1"

        /** Sites that are not hosts (a Wi-Fi SSID) can never match a request. */
        private const val UNINDEXED_SITE = ""

        internal fun VaultItemKind.syncKey(): String = "items:$name"

        internal val ACCOUNT_CLEAR_TABLES = listOf(
            GROUP_MEMBERS_TABLE,
            GROUPS_TABLE,
            INVITES_TABLE,
            ITEMS_TABLE,
            SYNC_TABLE,
            KEY_STATE_TABLE,
        )

        private const val CREATE_ITEMS = """
            CREATE TABLE vault_items (
                record_id TEXT NOT NULL,
                kind TEXT NOT NULL,
                site_index TEXT NOT NULL,
                site TEXT NOT NULL,
                title TEXT NOT NULL,
                username TEXT,
                display_name TEXT,
                webauthn_credential_id TEXT,
                group_id TEXT,
                modified_at_ms INTEGER,
                sort_position INTEGER NOT NULL,
                PRIMARY KEY (record_id, kind)
            )
        """
        private const val CREATE_ITEMS_LOOKUP_INDEX =
            "CREATE INDEX vault_items_site_idx ON vault_items(kind, site_index)"
        private const val CREATE_GROUPS = """
            CREATE TABLE vault_groups (
                group_id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                owner INTEGER NOT NULL,
                member_count INTEGER NOT NULL,
                sort_position INTEGER NOT NULL
            )
        """
        private const val CREATE_GROUP_MEMBERS = """
            CREATE TABLE vault_group_members (
                group_id TEXT NOT NULL,
                sort_position INTEGER NOT NULL,
                name TEXT,
                handle TEXT NOT NULL,
                joined INTEGER NOT NULL,
                current_user INTEGER NOT NULL,
                PRIMARY KEY (group_id, sort_position),
                FOREIGN KEY (group_id) REFERENCES vault_groups(group_id) ON DELETE CASCADE
            )
        """
        private const val CREATE_INVITES = """
            CREATE TABLE vault_invites (
                invite_id TEXT PRIMARY KEY NOT NULL,
                group_name TEXT NOT NULL,
                inviter TEXT NOT NULL,
                sort_position INTEGER NOT NULL
            )
        """
        private const val CREATE_SYNC_STATE = """
            CREATE TABLE vault_sync_state (
                sync_key TEXT PRIMARY KEY NOT NULL,
                updated_at_ms INTEGER NOT NULL
            )
        """
        private const val CREATE_KEY_STATE = """
            CREATE TABLE vault_key_state (
                key_name TEXT PRIMARY KEY NOT NULL,
                key_check TEXT NOT NULL
            )
        """

        internal val CREATE_STATEMENTS = listOf(
            CREATE_ITEMS.trimIndent(),
            CREATE_ITEMS_LOOKUP_INDEX,
            CREATE_GROUPS.trimIndent(),
            CREATE_GROUP_MEMBERS.trimIndent(),
            CREATE_INVITES.trimIndent(),
            CREATE_SYNC_STATE.trimIndent(),
            CREATE_KEY_STATE.trimIndent(),
        )

        internal fun migrationStatements(oldVersion: Int, newVersion: Int): List<String> = when {
            oldVersion == newVersion -> emptyList()
            oldVersion == 1 && newVersion == 2 -> listOf(
                "DROP TABLE vault_items",
                CREATE_ITEMS.trimIndent(),
                CREATE_ITEMS_LOOKUP_INDEX,
                "DELETE FROM vault_sync_state",
                CREATE_KEY_STATE.trimIndent(),
            )
            else -> error(
                "Missing vault catalog migration from version $oldVersion to $newVersion",
            )
        }

        /** Production catalog, sealed with the AndroidKeyStore-backed catalog keys. */
        fun create(context: Context): VaultSqliteCatalog = VaultSqliteCatalog(
            context,
            AesGcmVaultFieldCrypto(VaultCatalogKeystoreKeys()),
        )
    }
}
