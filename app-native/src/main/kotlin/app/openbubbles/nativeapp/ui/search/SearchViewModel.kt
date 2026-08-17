package app.openbubbles.nativeapp.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.openbubbles.core.contacts.RawContact
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.ChatListRepository
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.RichLinkPreview
import app.openbubbles.nativeapp.data.SearchRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/** One message- or link-result row, joined with its conversation. */
data class SearchMessageRow(
    val guid: String,
    /** Conversation to open on tap (grouped-contact aware). */
    val chatId: Long,
    /** The conversation list row, when known (avatar + title); null when it left the flow. */
    val chat: ChatListItem?,
    val text: String,
    val dateMillis: Long,
    /** Parsed link payload; non-null for rows in the links section. */
    val link: RichLinkPreview? = null,
)

data class SearchUiState(
    val query: String = "",
    val chats: List<ChatListItem> = emptyList(),
    val people: List<RawContact> = emptyList(),
    val messages: List<SearchMessageRow> = emptyList(),
    val links: List<SearchMessageRow> = emptyList(),
) {
    /** The debounced query drives sections, so the raw field text is not authoritative. */
    val hasResults: Boolean
        get() = chats.isNotEmpty() || people.isNotEmpty() || messages.isNotEmpty() || links.isNotEmpty()
}

/** Single-character queries match half the store; two is the useful floor. */
private const val MinQueryLength = 2

/** Chats and people cap so transcript hits stay visible without scrolling. */
private const val SectionCap = 4

/**
 * Dedicated cross-store search: chats from the list flow, people from the
 * synced contacts, messages and links from the transcript store. Queries
 * debounce until a typing pause; every section cancels and recomputes on the
 * newest query (mapLatest), and IO runs off the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val search: SearchRepository,
    chatsRepository: ChatListRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    // Blank/short resets land immediately; real queries wait out a typing pause.
    private val activeQuery: StateFlow<String> = query
        .map { it.trim() }
        .debounce { if (it.length < MinQueryLength) 0L else 250L }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val chats: StateFlow<List<ChatListItem>> = chatsRepository.chats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Transcript hits as (message matches, link matches). Implementations dispatch their own IO. */
    private val hits: StateFlow<Pair<List<MessageItem>, List<MessageItem>>> = activeQuery
        .mapLatest { trimmed ->
            if (trimmed.length < MinQueryLength) {
                Pair(emptyList(), emptyList())
            } else {
                val messages = runCatching { search.searchMessages(trimmed) }
                    .getOrDefault(emptyList())
                val links = runCatching { search.searchLinks(trimmed) }
                    .getOrDefault(emptyList())
                messages to links
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Pair(emptyList(), emptyList()))

    private val people: StateFlow<List<RawContact>> = activeQuery
        .mapLatest { trimmed ->
            if (trimmed.length < MinQueryLength) {
                emptyList()
            } else {
                runCatching { search.contacts() }.getOrDefault(emptyList())
                    .filter { contact -> contact.matches(trimmed) }
                    .sortedBy { it.displayName.orEmpty() }
                    .take(SectionCap)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<SearchUiState> =
        combine(query, activeQuery, chats, hits, people) { rawQuery, trimmed, chatList, hitPair, peopleMatches ->
            val chatMatches = if (trimmed.length < MinQueryLength) {
                emptyList()
            } else {
                chatList.filter { chat ->
                    chat.title.contains(trimmed, ignoreCase = true) ||
                        chat.avatarAddress?.contains(trimmed, ignoreCase = true) == true ||
                        chat.snippet?.contains(trimmed, ignoreCase = true) == true
                }.take(SectionCap)
            }
            fun rowFor(item: MessageItem, link: RichLinkPreview? = null): SearchMessageRow? {
                val protocolChatId = item.chatId ?: return null
                val chat = chatList.firstOrNull { protocolChatId in it.memberChatIds }
                return SearchMessageRow(
                    guid = item.guid,
                    chatId = chat?.id ?: protocolChatId,
                    chat = chat,
                    text = item.text,
                    dateMillis = item.date,
                    link = link,
                )
            }
            // A message surfaced as a link does not repeat in message results.
            val linkGuids = hitPair.second.mapTo(HashSet()) { it.guid }
            SearchUiState(
                query = rawQuery,
                chats = chatMatches,
                people = peopleMatches,
                messages = hitPair.first
                    .filter { it.guid !in linkGuids }
                    .mapNotNull { rowFor(it) },
                links = hitPair.second.mapNotNull { item ->
                    item.richLink?.let { rowFor(item, it) }
                },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    companion object {
        fun factory(
            search: SearchRepository,
            chats: ChatListRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SearchViewModel(search, chats) }
        }
    }
}

private fun RawContact.matches(query: String): Boolean =
    displayName?.contains(query, ignoreCase = true) == true ||
        addresses.any { it.contains(query, ignoreCase = true) }
