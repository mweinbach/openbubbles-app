package app.openbubbles.nativeapp.ui.chatinfo

import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.SharedContentPreview
import app.openbubbles.nativeapp.ui.findmy.RustFindMyPort
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

/**
 * Warm cache for the conversation-details pane.
 *
 * While a chat is on screen, [warm] preloads what the details pane would
 * otherwise start fetching on tap — the shared-photos strip, and for direct
 * chats the contact card, poster image, and Find My state — so the pane
 * opens with content instead of placeholders. Entries are seeds, not truth:
 * every consumer still performs its normal load and writes the fresh result
 * back, so nothing here can go visibly stale.
 */
internal object ChatInfoWarmCache {
    private const val MaxEntries = 8

    private class SmallLru<K, V> : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
            size > MaxEntries
    }

    private val contactDetails = Collections.synchronizedMap(SmallLru<String, ContactDetails>())
    private val sharedContent = Collections.synchronizedMap(SmallLru<Long, List<SharedContentPreview>>())
    private val locations = Collections.synchronizedMap(SmallLru<String, ContactLocationUi>())
    private val posters = Collections.synchronizedMap(SmallLru<String, File>())

    fun contactDetails(address: String): ContactDetails? = contactDetails[address]

    fun putContactDetails(address: String, details: ContactDetails) {
        if (address.isNotBlank()) contactDetails[address] = details
    }

    fun sharedContent(chatId: Long): List<SharedContentPreview>? = sharedContent[chatId]

    fun putSharedContent(chatId: Long, content: List<SharedContentPreview>) {
        sharedContent[chatId] = content
    }

    fun location(handleAddress: String): ContactLocationUi? = locations[handleAddress]

    fun putLocation(handleAddress: String, location: ContactLocationUi) {
        if (handleAddress.isNotBlank() && location !is ContactLocationUi.Loading) {
            locations[handleAddress] = location
        }
    }

    fun poster(address: String): File? = posters[address]?.takeIf { it.isFile }

    fun putPoster(address: String, file: File) {
        if (address.isNotBlank()) posters[address] = file
    }

    /**
     * Preloads the details-pane data for [chat] from local sources only —
     * the database, the synced contact list, and the already-cached Find My
     * friends list. Deliberately no network: warming must stay side-effect
     * free, and a contact without a cached fix still resolves on open.
     */
    suspend fun warm(chat: ChatListItem) {
        runCatching { CoreGraph.chatInfo.sharedContent(chat.id) }.getOrNull()
            ?.let { putSharedContent(chat.id, it) }
        // Groups render the participant list; only direct chats have a card.
        if (chat.isGroup) return
        val address = directContactAddress(chat.avatarAddress, emptyList())
        if (address.isBlank()) return
        val details = resolveContactDetails(
            address,
            chat.title,
            runCatching { CoreGraph.preferredContacts() }.getOrDefault(emptyList()),
        )
        putContactDetails(address, details)
        loadHandlePosterFile(address)?.let { putPoster(address, it) }
        val port = RustFindMyPort { PushStateHolder.state }
        if (port.isAvailable()) {
            runCatching { port.friends() }.getOrNull()?.let { friends ->
                putLocation(
                    address,
                    contactLocationFromFriends(details.allAddresses, friends, available = true),
                )
            }
        }
    }
}

/**
 * Resolves a participant's poster path from the db (read-only): the Handle
 * whose address or formattedAddress matches [address], when it carries a
 * `posterPath` that points at an existing file.
 */
internal suspend fun loadHandlePosterFile(address: String): File? = withContext(Dispatchers.IO) {
    runCatching {
        val box = CoreGraph.store?.boxFor(app.openbubbles.db.Handle::class.java)
            ?: return@runCatching null
        val handle = box.query()
            .equal(
                app.openbubbles.db.Handle_.address,
                address,
                QueryBuilder.StringOrder.CASE_SENSITIVE,
            )
            .or()
            .equal(
                app.openbubbles.db.Handle_.formattedAddress,
                address,
                QueryBuilder.StringOrder.CASE_SENSITIVE,
            )
            .build()
            .use { it.findFirst() }
            ?: return@runCatching null
        handle.posterPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile }
    }.getOrNull()
}
