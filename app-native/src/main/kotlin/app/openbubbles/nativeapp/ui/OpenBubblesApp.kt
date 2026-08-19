package app.openbubbles.nativeapp.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.toShape
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.openbubbles.nativeapp.BuildConfig
import app.openbubbles.nativeapp.MainLaunchAction
import app.openbubbles.nativeapp.NativeMainActivity
import app.openbubbles.nativeapp.SmsComposeRequest
import app.openbubbles.nativeapp.IncomingShareRequest
import app.openbubbles.nativeapp.data.AppContext
import app.openbubbles.nativeapp.data.AppGraph
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.ContactDisplayWarmCache
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessagingPrefs
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.credentials.CredentialUserAuth
import app.openbubbles.nativeapp.service.BatterySaver
import app.openbubbles.nativeapp.service.NativePushService
import app.openbubbles.nativeapp.service.Notifications
import app.openbubbles.nativeapp.ui.attachmentviewer.AttachmentViewerScreen
import app.openbubbles.nativeapp.ui.chat.BookmarkedMessagesScreen
import app.openbubbles.nativeapp.ui.chat.ChatScreen
import app.openbubbles.nativeapp.ui.chat.RecentlyDeletedScreen
import app.openbubbles.nativeapp.ui.chat.prepareOutgoingAttachment
import app.openbubbles.nativeapp.ui.chat.ChatViewModel
import app.openbubbles.nativeapp.ui.chatinfo.ChatInfoScreen
import app.openbubbles.nativeapp.ui.chatinfo.rememberParticipantRows
import app.openbubbles.nativeapp.ui.chatcreator.NewChatScreen
import app.openbubbles.nativeapp.ui.chatlist.ChatListKind
import app.openbubbles.nativeapp.ui.chatlist.ChatListScreen
import app.openbubbles.nativeapp.ui.chatlist.ChatListViewModel
import app.openbubbles.nativeapp.ui.chatlist.sendFromChoices
import app.openbubbles.nativeapp.ui.findmy.FindMyScreen
import app.openbubbles.nativeapp.ui.findmy.FindMyViewModel
import app.openbubbles.nativeapp.ui.login.LoginScreen
import app.openbubbles.nativeapp.ui.login.RustLoginHandle
import app.openbubbles.nativeapp.ui.onboarding.OnboardingScreen
import app.openbubbles.nativeapp.ui.passwords.PasswordsScreen
import app.openbubbles.nativeapp.ui.passwords.PasswordsViewModel
import app.openbubbles.nativeapp.ui.passwords.VaultCategory
import app.openbubbles.nativeapp.ui.passwords.VaultGroupDetailScreen
import app.openbubbles.nativeapp.ui.passwords.VaultGroupDetailViewModel
import app.openbubbles.nativeapp.ui.passwords.VaultItemDetailScreen
import app.openbubbles.nativeapp.ui.passwords.VaultItemDetailViewModel
import app.openbubbles.nativeapp.ui.passwords.VaultItemUi
import app.openbubbles.nativeapp.ui.photos.PhotosScreen
import app.openbubbles.nativeapp.ui.photos.PhotosViewModel
import app.openbubbles.nativeapp.ui.search.SearchScreen
import app.openbubbles.nativeapp.ui.share.ShareTargetPickerScreen
import app.openbubbles.nativeapp.ui.search.SearchViewModel
import app.openbubbles.nativeapp.ui.sharedalbums.SharedAlbumsScreen
import app.openbubbles.nativeapp.ui.sharedalbums.SharedAlbumsViewModel
import app.openbubbles.nativeapp.ui.settings.SettingsScreen
import app.openbubbles.nativeapp.ui.adaptive.messagingListDetailDirective
import app.openbubbles.nativeapp.ui.common.LocalAppSharedTransitionScope
import app.openbubbles.nativeapp.ui.common.LocalIsMultiPane
import app.openbubbles.nativeapp.ui.theme.LocalReduceMotion
import app.openbubbles.nativeapp.ui.theme.defaultEffectsSpec
import app.openbubbles.nativeapp.ui.theme.defaultSpatialSpec
import app.openbubbles.nativeapp.ui.theme.fastEffectsSpec

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import uniffi.rust_lib_bluebubbles.hasSavedUsers

/**
 * Route strings are the persistence format shared with [NativeMainActivity],
 * which stores the current route so the Compose tree can be released while the
 * push service keeps running and rebuilt on the way back. The navigation model
 * underneath is Navigation3, so these strings exist only at that boundary.
 */
object Routes {
    const val CHATS = "chats"
    const val LOGIN = "login"
    const val SETTINGS = "settings"
    const val PASSWORDS = "passwords"
    const val PHOTOS = "photos"
    const val SHARED_ALBUMS = "sharedalbums"
    const val ARCHIVED = "archived"
    const val FIND_MY = "findmy"
    const val SEARCH = "search"
    const val NEW_CHAT = "newchat"
    const val SHARE = "share"
    const val RECENTLY_DELETED = "recently-deleted"
    fun bookmarks(chatId: Long): String = "bookmarks/$chatId"
    fun chat(chatId: Long): String = "chat/$chatId"
    fun chatInfo(chatId: Long): String = "chatinfo/$chatId"
    fun newChat(recipients: List<String>, body: String?, useSms: Boolean): String = buildString {
        append(NEW_CHAT)
        val params = buildList {
            if (recipients.isNotEmpty()) add("to=${Uri.encode(recipients.joinToString("\u001f"))}")
            if (!body.isNullOrEmpty()) add("body=${Uri.encode(body)}")
            if (useSms) add("sms=1")
        }
        if (params.isNotEmpty()) append('?').append(params.joinToString("&"))
    }

    /** Attachment guids can contain ':'/'/' — encode for the path segment. */
    fun attachment(guid: String, chatId: Long?): String = buildString {
        append("attachment/").append(Uri.encode(guid))
        if (chatId != null) append("?chat=").append(chatId)
    }
}

// --------------------------------------------------------------- destinations

@Serializable
data object ChatsKey : NavKey

@Serializable
data class ChatKey(
    val chatId: Long,
    val initialDraft: String? = null,
    val sharedUris: List<String> = emptyList(),
) : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object PasswordsKey : NavKey

/**
 * One vault item opened as its own page. Carries the list row's snapshot so
 * the page renders instantly; the secret itself is only fetched on-page
 * behind user authentication.
 */
@Serializable
data class VaultItemKey(
    val id: String,
    val category: VaultCategory,
    val title: String,
    val username: String? = null,
    val groupId: String? = null,
    val modifiedAtMs: Long? = null,
) : NavKey

/** One shared-password group opened as its own page. */
@Serializable
data class VaultGroupKey(val id: String, val name: String) : NavKey

@Serializable
data object SharedAlbumsKey : NavKey

@Serializable
data object PhotosKey : NavKey

@Serializable
data object ArchivedChatsKey : NavKey

@Serializable
data object RecentlyDeletedKey : NavKey

@Serializable
data class BookmarksKey(val chatId: Long) : NavKey

@Serializable
data object FindMyKey : NavKey

@Serializable
data class NewChatKey(
    val recipients: List<String> = emptyList(),
    val body: String? = null,
    val useSms: Boolean = false,
    val sharedUris: List<String> = emptyList(),
) : NavKey

@Serializable
data class ShareTargetPickerKey(val request: IncomingShareRequest) : NavKey

@Serializable
data class ChatInfoKey(val chatId: Long) : NavKey

@Serializable
data object SearchKey : NavKey

@Serializable
data class AttachmentKey(val guid: String, val chatId: Long? = null) : NavKey

@Serializable
data object LoginKey : NavKey

private fun NavKey.toRoute(): String = when (this) {
    is ChatsKey -> Routes.CHATS
    is ChatKey -> Routes.chat(chatId)
    is SettingsKey -> Routes.SETTINGS
    is PasswordsKey -> Routes.PASSWORDS
    // Secrets never persist across process death; resume on the vault list.
    is VaultItemKey -> Routes.PASSWORDS
    is VaultGroupKey -> Routes.PASSWORDS
    is SharedAlbumsKey -> Routes.SHARED_ALBUMS
    is PhotosKey -> Routes.PHOTOS
    is ArchivedChatsKey -> Routes.ARCHIVED
    is RecentlyDeletedKey -> Routes.RECENTLY_DELETED
    is BookmarksKey -> Routes.bookmarks(chatId)
    is FindMyKey -> Routes.FIND_MY
    is SearchKey -> Routes.SEARCH
    is NewChatKey -> Routes.newChat(recipients, body, useSms)
    is ShareTargetPickerKey -> Routes.SHARE
    is ChatInfoKey -> Routes.chatInfo(chatId)
    is AttachmentKey -> Routes.attachment(guid, chatId)
    is LoginKey -> Routes.LOGIN
    else -> Routes.CHATS
}

private fun routeParameter(route: String, name: String): String? =
    route.substringAfter('?', "")
        .split('&')
        .firstOrNull { it.substringBefore('=') == name }
        ?.substringAfter('=', "")
        ?.let(Uri::decode)

private fun routeToKey(route: String): NavKey? = when {
    route == Routes.CHATS -> ChatsKey
    route == Routes.SETTINGS -> SettingsKey
    route == Routes.PASSWORDS -> PasswordsKey
    route == Routes.SHARED_ALBUMS -> SharedAlbumsKey
    route == Routes.PHOTOS -> PhotosKey
    route == Routes.ARCHIVED -> ArchivedChatsKey
    route == Routes.RECENTLY_DELETED -> RecentlyDeletedKey
    route.startsWith("bookmarks/") -> route.removePrefix("bookmarks/").toLongOrNull()?.let(::BookmarksKey)
    route == Routes.FIND_MY -> FindMyKey
    route == Routes.SEARCH -> SearchKey
    route.substringBefore('?') == Routes.NEW_CHAT -> NewChatKey(
        recipients = routeParameter(route, "to")?.split('\u001f')?.filter(String::isNotBlank).orEmpty(),
        body = routeParameter(route, "body"),
        useSms = routeParameter(route, "sms") == "1",
    )
    route == Routes.LOGIN -> LoginKey
    route.startsWith("chat/") -> route.substringBefore('?').removePrefix("chat/").toLongOrNull()?.let(::ChatKey)
    route.startsWith("chatinfo/") -> route.removePrefix("chatinfo/").toLongOrNull()?.let(::ChatInfoKey)
    route.startsWith("attachment/") -> AttachmentKey(
        guid = Uri.decode(route.substringBefore('?').removePrefix("attachment/")),
        chatId = routeParameter(route, "chat")?.toLongOrNull(),
    )
    else -> null
}

/**
 * Root scaffold.
 *
 * The chat list and a conversation are a list-detail pair: on a compact window
 * they are separate full-screen destinations, and from medium width up they sit
 * side by side. Conversation details take the third pane only when three
 * partitions fit; otherwise they replace the conversation as the detail pane.
 * That behavior comes from [ListDetailSceneStrategy] reading pane metadata off
 * the back stack rather than from any explicit width branching here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OpenBubblesApp(
    modifier: Modifier = Modifier,
    debugLines: List<String> = emptyList(),
    /** Chat guid from a notification tap; resolved and consumed once. */
    startChatGuid: String? = null,
    startComposeRequest: SmsComposeRequest? = null,
    onComposeRequestConsumed: () -> Unit = {},
    startShareRequest: IncomingShareRequest? = null,
    onShareRequestConsumed: () -> Unit = {},
    /** Explicit launch into a route (credential settings, Passwords icon); consumed once. */
    startRouteRequest: MainLaunchAction.OpenRoute? = null,
    onRouteRequestConsumed: () -> Unit = {},
    /** Main-icon tap while a standalone launch owns the stack; consumed once. */
    startHomeRequest: Boolean = false,
    onHomeRequestConsumed: () -> Unit = {},
    /** Standalone launch: the requested route is the root and back exits the activity. */
    standaloneTask: Boolean = false,
    /** Actual route restored after the hidden Compose tree was released. */
    resumeRoute: String? = null,
    onRouteChanged: (String?) -> Unit = {},
) {
    val hostActivity = LocalActivity.current
    val backStack = rememberNavBackStack(ChatsKey)
    val current = backStack.lastOrNull()
    val pushState by PushStateHolder.stateFlow.collectAsStateWithLifecycle()
    val registrationState by PushStateHolder.registrationStateFlow.collectAsStateWithLifecycle()
    val pushError by PushStateHolder.lastErrorFlow.collectAsStateWithLifecycle()
    val accountConnection = accountConnectionUiState(
        hasLiveState = pushState != null,
        registration = registrationState,
        lastError = pushError,
    )
    val prefetchScope = rememberCoroutineScope()
    var transcriptPrefetchJob by remember { mutableStateOf<Job?>(null) }

    // A conversation shown beside its list must not offer a back arrow, and the
    // navigation container stays visible in that layout. The directive is the
    // v2 window-info variant with the two-panes-on-medium override: a messaging
    // client is the canonical list-detail app, so foldables and portrait
    // tablets get list|chat instead of a stretched phone layout, and from
    // 1200dp up conversation details get the third pane. Below that they
    // replace the conversation so the page is never a levitated card.
    val directive = messagingListDetailDirective(currentWindowAdaptiveInfoV2())
    val isMultiPane = directive.maxHorizontalPartitions > 1
    val threePane = directive.maxHorizontalPartitions >= 3

    fun navigateTo(key: NavKey) {
        if (backStack.lastOrNull() == key) return
        backStack.add(key)
    }

    fun popBack() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        } else if (standaloneTask) {
            // The standalone Passwords icon seeded this root; leaving it is
            // leaving the "app", not a hop back into messaging.
            hostActivity?.finish()
        }
    }

    /**
     * Opening a conversation from the list SWAPS the open one instead of
     * stacking on top of it. In two-pane this is the Material list-detail
     * contract — selecting a list item replaces the detail pane; without it,
     * browsing five chats buries five conversations on the back stack and the
     * back gesture walks through every one. Single-pane gets the same
     * behaviour, which matches every mainstream messaging app: back from a
     * conversation always lands on the list.
     */
    fun openChat(chatId: Long) {
        transcriptPrefetchJob?.cancel()
        val last = backStack.lastOrNull()
        // Tapping the already-selected row while chat info replaced the
        // conversation pops back to that transcript. The list is visible
        // in multi-pane, so this is the list-detail "same item" contract.
        if (last is ChatInfoKey && last.chatId == chatId) {
            popBack()
            return
        }
        val key = ChatKey(chatId)
        if (last == key) return
        // A conversation belongs to the Chats tab: entering one from another
        // top-level destination (notification tap while in Settings) replaces
        // it so the detail pane never renders orphaned beside nothing.
        backStack.removeAll {
            it is SettingsKey || it is FindMyKey || it is NewChatKey ||
                it is LoginKey || it is ArchivedChatsKey || it is RecentlyDeletedKey ||
                it is SearchKey || it is BookmarksKey
        }
        while (backStack.size > 1 &&
            (
                backStack.last() is ChatKey || backStack.last() is ChatInfoKey ||
                    backStack.last() is AttachmentKey || backStack.last() is BookmarksKey
                )
        ) {
            backStack.removeAt(backStack.lastIndex)
        }
        backStack.add(key)
    }

    /**
     * Search sits beside the list on wide windows (detail pane) and is a
     * full-screen overlay on compact. Opening it swaps the open conversation
     * the same way [openChat] does, so back lands on the list placeholder.
     */
    fun openSearch() {
        if (backStack.lastOrNull() is SearchKey) return
        backStack.removeAll {
            it is SettingsKey || it is FindMyKey || it is NewChatKey ||
                it is LoginKey || it is ArchivedChatsKey
        }
        while (backStack.size > 1 &&
            (
                backStack.last() is ChatKey ||
                    backStack.last() is ChatInfoKey ||
                    backStack.last() is AttachmentKey ||
                    backStack.last() is SearchKey
                )
        ) {
            backStack.removeAt(backStack.lastIndex)
        }
        backStack.add(SearchKey)
    }

    // The conversation whose row should read as selected in the list pane.
    // Only meaningful when the list is visible beside the detail.
    val selectedChatId = if (isMultiPane) {
        (backStack.lastOrNull { it is ChatKey } as? ChatKey)?.chatId
    } else {
        null
    }

    fun navigateHome() {
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Restores the persisted route at the recorded depth (chat info keeps its chat underneath). */
    fun restoreResumeRoute() {
        if (backStack.lastOrNull() != ChatsKey) return
        when (val key = resumeRoute?.takeIf { it != Routes.CHATS }?.let(::routeToKey)) {
            is ChatInfoKey -> {
                backStack.add(ChatKey(key.chatId))
                backStack.add(key)
            }
            is AttachmentKey -> {
                key.chatId?.let { backStack.add(ChatKey(it)) }
                backStack.add(key)
            }
            null -> Unit
            else -> backStack.add(key)
        }
    }

    LaunchedEffect(resumeRoute, startChatGuid, startComposeRequest) {
        if (startChatGuid == null && startComposeRequest == null && startShareRequest == null) restoreResumeRoute()
    }

    // Explicit route launch, cold or warm. A standalone launch makes the
    // route the only entry so back exits, and redelivery after process
    // death converges to the same stack.
    LaunchedEffect(startRouteRequest) {
        val request = startRouteRequest ?: return@LaunchedEffect
        routeToKey(request.route)?.let { key ->
            if (request.standaloneTask) {
                backStack.add(key)
                while (backStack.size > 1) backStack.removeAt(0)
            } else if (backStack.lastOrNull() != key) {
                backStack.add(key)
            }
        }
        onRouteRequestConsumed()
    }

    // The main icon must always reach messaging: reset the standalone
    // Passwords stack to the chat list, converging like the effect above.
    LaunchedEffect(startHomeRequest) {
        if (!startHomeRequest) return@LaunchedEffect
        backStack.add(ChatsKey)
        while (backStack.size > 1) backStack.removeAt(0)
        onHomeRequestConsumed()
    }

    LaunchedEffect(current) {
        onRouteChanged(current?.toRoute())
    }

    // Deep link from a notification tap: resolve the guid to a chat id and
    // navigate once, then clear the pending static so config changes (and
    // any recomposition) don't re-trigger it. When resolution fails, the
    // user still gets the route their previous session left behind.
    LaunchedEffect(startChatGuid) {
        val guid = startChatGuid?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val chatId = withContext(Dispatchers.IO) { CoreGraph.chatIdForGuid(guid) }
        if (chatId != null && chatId > 0L) {
            AppContext.current?.let { Notifications.cancelForChat(it, chatId) }
            if (backStack.none { it is ChatKey && it.chatId == chatId }) {
                openChat(chatId)
            }
        } else {
            restoreResumeRoute()
        }
        NativeMainActivity.pendingChatGuid = null
    }

    LaunchedEffect(startComposeRequest) {
        val request = startComposeRequest ?: return@LaunchedEffect
        navigateHome()
        navigateTo(NewChatKey(request.recipients, request.body, request.useSms))
        onComposeRequestConsumed()
    }

    LaunchedEffect(startShareRequest) {
        val request = startShareRequest ?: return@LaunchedEffect
        navigateHome()
        val resumedChat = resumeRoute?.let(::routeToKey) as? ChatKey
        if (resumedChat != null && request.streams.isNotEmpty()) {
            navigateTo(resumedChat.copy(initialDraft = request.text, sharedUris = request.streams))
            onShareRequestConsumed()
        } else {
            navigateTo(ShareTargetPickerKey(request))
        }
    }

    // First-run gate: full-screen onboarding until sign-in completes once.
    val context = AppContext.current
    val onboardingPrefs = remember(context) {
        context?.getSharedPreferences("native_setup", android.content.Context.MODE_PRIVATE)
    }
    var onboardingComplete by remember(onboardingPrefs) {
        androidx.compose.runtime.mutableStateOf(onboardingPrefs?.getBoolean("onboarding_complete", false) ?: true)
    }

    // A force-stop or process restart clears the in-memory holder even though
    // IDS registration remains on disk. Returning users must restore the live
    // state when the activity becomes usable again; boot broadcasts alone are
    // insufficient because Android suppresses them for force-stopped apps.
    LaunchedEffect(context, onboardingComplete) {
        val ctx = context ?: return@LaunchedEffect
        if (!onboardingComplete) return@LaunchedEffect
        val hasRegistration = withContext(Dispatchers.IO) {
            runCatching { hasSavedUsers(ctx.filesDir.absolutePath) }.getOrDefault(false)
        }
        if (!hasRegistration) return@LaunchedEffect
        if (BatterySaver.isEnabled(ctx)) {
            BatterySaver.schedule(ctx)
        } else {
            NativePushService.start(ctx)
        }
    }

    if (pushState == null && !onboardingComplete && context != null) {
        OnboardingScreen(
            onFinished = {
                onboardingPrefs?.edit { putBoolean("onboarding_complete", true) }
                onboardingComplete = true
                NativePushService.reloadAfterLogin(context)
                requestBatteryExemptionOnce(context)
            },
            onLaunchSignIn = { },
        )
        return
    }

    // User-resizable split: the drag handle between the panes is the Material
    // affordance for pane expansion, and PaneExpansionState remembers where the
    // user put it. Anchors keep the split at sane proportions instead of
    // letting either pane be dragged to an unusable sliver.
    val paneExpansionState = rememberPaneExpansionState(
        anchors = listOf(
            PaneExpansionAnchor.Proportion(0.4f),
            PaneExpansionAnchor.Proportion(0.5f),
            PaneExpansionAnchor.Proportion(0.6f),
        ),
    )
    // rememberListDetailSceneStrategy keys on this lambda. An inline
    // lambda would rebuild the strategy (and drop the user's split) on
    // every recomposition, including a fold / unfold.
    val paneExpansionDragHandle:
        @Composable ThreePaneScaffoldScope.(PaneExpansionState) -> Unit = remember {
            { state ->
                val interactionSource = remember { MutableInteractionSource() }
                VerticalDragHandle(
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .paneExpansionDraggable(
                            state,
                            LocalMinimumInteractiveComponentSize.current,
                            interactionSource,
                        )
                        .systemGestureExclusion(),
                )
            }
        }
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(
        directive = directive,
        paneExpansionState = paneExpansionState,
        paneExpansionDragHandle = paneExpansionDragHandle,
    )

    // Hoisted spec reads: the transition lambdas are not composable.
    val navEnterFade = defaultEffectsSpec<Float>()
    val navExitFade = fastEffectsSpec<Float>()
    val navScale = defaultSpatialSpec<Float>()
    val overlaySpatial = defaultSpatialSpec<IntOffset>()
    val reduceMotion = LocalReduceMotion.current
    val overlayMetadata = remember(
        overlaySpatial,
        navEnterFade,
        navExitFade,
        navScale,
        reduceMotion,
    ) {
        NavTransitions.overlayMetadata(
            spatial = overlaySpatial,
            enterFade = navEnterFade,
            exitFade = navExitFade,
            scale = navScale,
            reduceMotion = reduceMotion,
        )
    }

    val appContent: @Composable () -> Unit = {
        // In two-pane the panes sit on a surfaceContainer canvas and separate
        // tonally (list on surfaceContainerLow, conversation on surface) —
        // Material's layering for list-detail. Single-pane keeps plain surface.
        Surface(
            color = if (isMultiPane) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            modifier = Modifier.fillMaxSize(),
        ) {
        CompositionLocalProvider(LocalIsMultiPane provides isMultiPane) {
        SharedTransitionLayout {
        CompositionLocalProvider(LocalAppSharedTransitionScope provides this@SharedTransitionLayout) {
        NavDisplay(
            backStack = backStack,
            onBack = { popBack() },
            sceneStrategies = listOf(listDetailStrategy),
            sharedTransitionScope = this@SharedTransitionLayout,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                // Per-entry ViewModelStores: popping a conversation clears its
                // ChatViewModel instead of accumulating one per opened chat.
                rememberViewModelStoreNavEntryDecorator(),
            ),
            transitionSpec = { NavTransitions.fade(navEnterFade, navExitFade) },
            popTransitionSpec = { NavTransitions.fade(navEnterFade, navExitFade) },
            predictivePopTransitionSpec = { edge ->
                NavTransitions.predictivePop(
                    swipeEdge = edge,
                    enterFade = navEnterFade,
                    exitFade = navExitFade,
                    scale = navScale,
                    reduceMotion = reduceMotion,
                )
            },
            modifier = Modifier.fillMaxSize(),
            entryProvider = entryProvider {
                entry<ChatsKey>(
                    metadata = ListDetailSceneStrategy.listPane(
                        detailPlaceholder = { NoConversationSelected() },
                    ),
                ) {
                    val viewModel: ChatListViewModel =
                        viewModel(factory = ChatListViewModel.factory(AppGraph.chats))
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val registeredHandles by PushStateHolder.myHandlesFlow
                        .collectAsStateWithLifecycle()
                    val listContext = LocalContext.current
                    ChatListScreen(
                        uiState = state,
                        onChatClick = { chat -> openChat(chat.id) },
                        sendFromChoices = remember(registeredHandles) {
                            sendFromChoices(registeredHandles)
                        },
                        defaultSendingHandle = {
                            MessagingPrefs(listContext).defaultSendingHandle
                        },
                        onSetSendFrom = viewModel::setSenderOverride,
                        onOpenSearch = { openSearch() },
                        onVisibleChatsChanged = { ids ->
                            transcriptPrefetchJob?.cancel()
                            val visibleAddresses = state.chats
                                .filter { it.id in ids.toHashSet() }
                                .mapNotNull { it.avatarAddress }
                            transcriptPrefetchJob = prefetchScope.launch {
                                // Contacts first: cheap, and a row tapped
                                // right away gets a warm header.
                                ContactDisplayWarmCache.warm(visibleAddresses)
                                AppGraph.messages.prefetch(ids)
                            }
                        },
                        selectedChatId = selectedChatId,
                        containerColor = if (isMultiPane) {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        onNewChat = { navigateTo(NewChatKey()) },
                        onOpenFindMy = { navigateTo(FindMyKey) },
                        onOpenSettings = { navigateTo(SettingsKey) },
                        onTogglePinned = viewModel::togglePinned,
                        onToggleMuted = viewModel::toggleMuted,
                        onMuteFor = viewModel::muteFor,
                        onArchive = viewModel::archive,
                        onUnarchive = viewModel::unarchive,
                        onDelete = { ids ->
                            viewModel.delete(ids)
                            if (selectedChatId != null && selectedChatId in ids) navigateHome()
                        },
                        header = {
                            accountConnection?.let { connection ->
                                AccountConnectionBanner(
                                    state = connection,
                                    onAction = {
                                        when (connection.action) {
                                            AccountConnectionAction.SignIn -> navigateTo(LoginKey)
                                            AccountConnectionAction.Retry ->
                                                NativePushService.reloadAfterLogin(listContext)
                                            null -> Unit
                                        }
                                    },
                                )
                            }
                        },
                        footer = { DebugStatusFooter(debugLines) },
                    )
                }

                entry<ChatKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                    val chatId = key.chatId
                    val conversationContext = LocalContext.current
                    DisposableEffect(chatId) {
                        Notifications.onConversationVisible(conversationContext, chatId)
                        onDispose { Notifications.onConversationHidden(chatId) }
                    }
                    // Size-capped media auto-download (Settings → Messaging):
                    // opening a conversation fetches eligible photos, videos,
                    // and voice memos so their bubbles render/play inline.
                    LaunchedEffect(chatId) {
                        AppGraph.autoDownloadForChat(chatId)
                    }
                    val viewModel: ChatViewModel = viewModel(
                        key = "chat-$chatId",
                        factory = ChatViewModel.factory(
                            chatId,
                            AppGraph.chats,
                            AppGraph.messages,
                            AppGraph.sender,
                            AppGraph.messageActions,
                            AppGraph.attachmentSender,
                            AppGraph.stickerSender,
                            AppGraph.typing,
                            AppGraph.readReceipts,
                            AppGraph.faceTimeCaller,
                            initialInput = key.initialDraft,
                        ),
                    )
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    LaunchedEffect(key.sharedUris) {
                        if (key.sharedUris.isNotEmpty()) {
                            val staged = key.sharedUris.mapNotNull { raw ->
                                prepareOutgoingAttachment(conversationContext, raw.toUri())
                            }
                            viewModel.stageAttachments(staged)
                        }
                    }
                    ChatScreen(
                        uiState = state,
                        onInputChange = viewModel::onInputChange,
                        onSubjectChange = viewModel::onSubjectChange,
                        onInsertMention = viewModel::insertMention,
                        onSend = viewModel::sendMessage,
                        onLoadOlder = viewModel::loadOlder,
                        onStageAttachments = viewModel::stageAttachments,
                        onRemovePendingAttachment = viewModel::removePendingAttachment,
                        onReply = viewModel::beginReply,
                        onOpenReplyThread = viewModel::openReplyThread,
                        onCloseReplyThread = viewModel::closeReplyThread,
                        onReplyFromThread = viewModel::replyFromThread,
                        onSendSticker = viewModel::sendSticker,
                        onEdit = viewModel::beginEdit,
                        onReact = viewModel::react,
                        onUnsend = viewModel::unsend,
                        onCancelComposerAction = viewModel::cancelComposerAction,
                        onActionErrorShown = viewModel::clearActionError,
                        onStartFaceTime = viewModel::startFaceTime,
                        onFaceTimeLaunchConsumed = viewModel::consumeFaceTimeLaunch,
                        onScreenEffectConsumed = viewModel::consumeScreenEffect,
                        onOutgoingSendEventConsumed = viewModel::consumeOutgoingSendEvent,
                        onBack = { popBack() },
                        // Beside its own list there is nothing to go back to.
                        showBackButton = !isMultiPane,
                        onOpenChatInfo = { navigateTo(ChatInfoKey(chatId)) },
                        onOpenAttachment = { guid -> navigateTo(AttachmentKey(guid, chatId)) },
                        onDownloadAttachment = { attachment ->
                            AppGraph.requestAttachmentDownload(attachment.guid)
                        },
                        attachmentFile = AppGraph.attachments::localFile,
                    )
                }

                entry<ChatInfoKey>(
                    metadata = if (threePane) {
                        ListDetailSceneStrategy.extraPane()
                    } else {
                        ListDetailSceneStrategy.detailPane()
                    },
                ) { key ->
                    val chatId = key.chatId
                    val chats by remember(chatId) { AppGraph.chats.chats() }
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    val chat = chats.firstOrNull { it.id == chatId || chatId in it.memberChatIds }
                    var participantRevision by remember(chatId) {
                        androidx.compose.runtime.mutableIntStateOf(0)
                    }
                    val addresses by produceState<List<String>>(
                        initialValue = listOfNotNull(chat?.avatarAddress),
                        chatId,
                        chat?.memberChatIds,
                        chat?.avatarAddress,
                        participantRevision,
                    ) {
                        val loaded = withContext(Dispatchers.IO) {
                            AppGraph.chatInfo.participantAddresses(chatId)
                        }
                        value = loaded.ifEmpty { listOfNotNull(chat?.avatarAddress) }
                    }
                    val participants = rememberParticipantRows(addresses)
                    ChatInfoScreen(
                        chat = chat,
                        participants = participants,
                        onBack = { popBack() },
                        // The list is visible whenever there is more than one
                        // pane, so chat info does not offer a back arrow. Two-
                        // pane still replaces the conversation; tapping the
                        // selected list row pops back to it.
                        showBackButton = !isMultiPane,
                        onRename = { name ->
                            AppGraph.chatInfoActions.rename(chatId, name)
                        },
                        onAddParticipant = { address ->
                            AppGraph.chatInfoActions.addParticipant(chatId, address)
                            participantRevision++
                        },
                        onRemoveParticipant = { address ->
                            AppGraph.chatInfoActions.removeParticipant(chatId, address)
                            participantRevision++
                        },
                        onSetGroupIcon = { file -> AppGraph.chatInfoActions.setGroupIcon(chatId, file) },
                        onRemoveGroupIcon = { AppGraph.chatInfoActions.removeGroupIcon(chatId) },
                        onSetBackground = { file -> AppGraph.chatBackgroundActions.setLocalBackground(chatId, file) },
                        onClearBackground = { AppGraph.chatBackgroundActions.clearLocalBackground(chatId) },
                        onLeaveChat = { AppGraph.chatInfoActions.leave(chatId) },
                        onReportJunk = { AppGraph.chatInfoActions.reportJunk(chatId) },
                        onOpenBookmarks = { navigateTo(BookmarksKey(chatId)) },
                        onOpenChat = { targetId ->
                            popBack()
                            openChat(targetId)
                        },
                        onOpenAttachment = { guid ->
                            navigateTo(AttachmentKey(guid, chatId))
                        },
                        attachmentFile = AppGraph.attachments::localFile,
                    )
                }

                entry<NewChatKey>(metadata = overlayMetadata) { key ->
                    NewChatScreen(
                        initialRecipients = key.recipients,
                        initialUseSms = key.useSms,
                        onChatOpened = { chatId ->
                            // Drop the creator, then open the new conversation.
                            popBack()
                            navigateTo(ChatKey(chatId, key.body, key.sharedUris))
                        },
                        onBack = { popBack() },
                    )
                }

                entry<ShareTargetPickerKey>(metadata = overlayMetadata) { key ->
                    val pickerModel: ChatListViewModel =
                        viewModel(factory = ChatListViewModel.factory(AppGraph.chats))
                    val pickerState by pickerModel.uiState.collectAsStateWithLifecycle()
                    ShareTargetPickerScreen(
                        uiState = pickerState,
                        onChatClick = { chat ->
                            popBack()
                            navigateTo(ChatKey(chat.id, key.request.text, key.request.streams))
                            onShareRequestConsumed()
                        },
                        onNewChat = {
                            navigateTo(NewChatKey(body = key.request.text, sharedUris = key.request.streams))
                            onShareRequestConsumed()
                        },
                        onBack = {
                            popBack()
                            onShareRequestConsumed()
                        },
                    )
                }

                entry<SettingsKey>(metadata = overlayMetadata) {
                    val archivedModel: ChatListViewModel =
                        viewModel(factory = ChatListViewModel.factory(AppGraph.chats))
                    val listState by archivedModel.uiState.collectAsStateWithLifecycle()
                    val recentlyDeletedCount by produceState(0) {
                        value = withContext(Dispatchers.IO) {
                            AppGraph.chats.recentlyDeletedCount()
                        }
                    }
                    SettingsScreen(
                        onBack = { popBack() },
                        onOpenFindMy = { navigateTo(FindMyKey) },
                        onOpenArchived = { navigateTo(ArchivedChatsKey) },
                        onOpenRecentlyDeleted = { navigateTo(RecentlyDeletedKey) },
                        onOpenPasswords = { navigateTo(PasswordsKey) },
                        onOpenPhotos = { navigateTo(PhotosKey) },
                        onOpenSharedAlbums = { navigateTo(SharedAlbumsKey) },
                        onOpenSignIn = { navigateTo(LoginKey) },
                        archivedCount = listState.archived.size,
                        recentlyDeletedCount = recentlyDeletedCount,
                        showBackButton = true,
                    )
                }

                entry<PasswordsKey>(metadata = overlayMetadata) {
                    val viewModel: PasswordsViewModel = viewModel(factory = PasswordsViewModel.factory())
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val context = LocalContext.current
                    PasswordsScreen(
                        uiState = state,
                        onBack = { popBack() },
                        onRefresh = viewModel::refresh,
                        onOpenICloudSettings = { popBack() },
                        onCategory = viewModel::setCategory,
                        onQuery = viewModel::setQuery,
                        onSelect = { item ->
                            navigateTo(
                                VaultItemKey(
                                    id = item.id,
                                    category = item.category,
                                    title = item.title,
                                    username = item.username,
                                    groupId = item.groupId,
                                    modifiedAtMs = item.modifiedAtMs,
                                ),
                            )
                        },
                        onOpenGroup = { group -> navigateTo(VaultGroupKey(group.id, group.name)) },
                        onPrepareCreatePassword = viewModel::prepareCreatePassword,
                        onCreatePassword = viewModel::createPassword,
                        onCreateGroup = viewModel::createGroup,
                        onAcceptInvite = viewModel::acceptInvite,
                        onDeclineInvite = viewModel::declineInvite,
                    )
                }

                entry<VaultItemKey>(metadata = overlayMetadata) { key ->
                    val item = remember(key) {
                        VaultItemUi(
                            id = key.id,
                            category = key.category,
                            title = key.title,
                            username = key.username,
                            groupId = key.groupId,
                            modifiedAtMs = key.modifiedAtMs,
                        )
                    }
                    val viewModel: VaultItemDetailViewModel =
                        viewModel(factory = VaultItemDetailViewModel.factory(item))
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val context = LocalContext.current
                    VaultItemDetailScreen(
                        uiState = state,
                        onBack = { popBack() },
                        onRequestReveal = {
                            val activity = context as? androidx.fragment.app.FragmentActivity
                            if (activity != null) {
                                CredentialUserAuth.authenticate(
                                    activity = activity,
                                    title = "Reveal iCloud Password",
                                    subtitle = "Authenticate to reveal or copy this secret",
                                    onSuccess = viewModel::reveal,
                                    onFailure = {},
                                )
                            }
                        },
                        // Verification codes roll over on-page after the user
                        // already authenticated for the first reveal.
                        onRefreshCode = viewModel::reveal,
                        onCopy = { value ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("iCloud Password", value))
                        },
                        onDelete = {
                            val activity = context as? androidx.fragment.app.FragmentActivity
                            if (activity != null) {
                                CredentialUserAuth.authenticate(
                                    activity = activity,
                                    title = "Delete iCloud item",
                                    subtitle = "Authenticate to delete this item from all your devices",
                                    onSuccess = viewModel::delete,
                                    onFailure = {},
                                )
                            } else {
                                viewModel.delete()
                            }
                        },
                        onAddTotp = viewModel::addTotp,
                    )
                }

                entry<VaultGroupKey>(metadata = overlayMetadata) { key ->
                    val viewModel: VaultGroupDetailViewModel = viewModel(
                        factory = VaultGroupDetailViewModel.factory(key.id, key.name),
                    )
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    VaultGroupDetailScreen(
                        uiState = state,
                        onBack = { popBack() },
                        onRename = viewModel::rename,
                        onInviteMember = viewModel::inviteMember,
                        onRemoveMember = viewModel::removeMember,
                        onDeleteOrLeave = viewModel::deleteOrLeave,
                    )
                }

                entry<SharedAlbumsKey>(metadata = overlayMetadata) {
                    val viewModel: SharedAlbumsViewModel = viewModel(factory = SharedAlbumsViewModel.factory())
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val context = LocalContext.current
                    SharedAlbumsScreen(
                        uiState = state,
                        onBack = { popBack() },
                        onRefresh = { viewModel.refresh(true) },
                        onSyncNow = viewModel::syncNow,
                        onSelect = viewModel::select,
                        onAccept = viewModel::accept,
                        onAcceptToken = viewModel::acceptToken,
                        onClearError = viewModel::clearError,
                        onSetSync = { album, enabled ->
                            val folder = if (enabled) {
                                val safeName = album.name.replace(Regex("[^A-Za-z0-9._ -]"), "_")
                                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                                    ?.resolve("Shared Albums")
                                    ?.resolve(safeName)
                                    ?.also { it.mkdirs() }
                                    ?.absolutePath
                            } else {
                                null
                            }
                            viewModel.setSync(album, folder)
                        },
                    )
                }

                entry<PhotosKey>(metadata = overlayMetadata) {
                    val viewModel: PhotosViewModel = viewModel(factory = PhotosViewModel.factory())
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    PhotosScreen(
                        uiState = state,
                        onBack = { popBack() },
                        onRefresh = viewModel::refresh,
                        onLoadMore = viewModel::loadMore,
                        onDownloadPreview = viewModel::downloadPreview,
                    )
                }

                entry<ArchivedChatsKey>(metadata = overlayMetadata) {
                    val viewModel: ChatListViewModel =
                        viewModel(factory = ChatListViewModel.factory(AppGraph.chats))
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    ChatListScreen(
                        uiState = state,
                        kind = ChatListKind.Archive,
                        showBackButton = true,
                        onBack = { popBack() },
                        onChatClick = { chat -> openChat(chat.id) },
                        onArchive = viewModel::archive,
                        onUnarchive = viewModel::unarchive,
                        onDelete = { ids ->
                            viewModel.delete(ids)
                            if (selectedChatId != null && selectedChatId in ids) navigateHome()
                        },
                    )
                }

                entry<RecentlyDeletedKey>(metadata = overlayMetadata) {
                    val chats by produceState(emptyList<ChatListItem>()) {
                        value = withContext(Dispatchers.IO) { AppGraph.chats.recentlyDeleted() }
                    }
                    val messages by produceState(emptyList<MessageItem>()) {
                        value = withContext(Dispatchers.IO) { AppGraph.messages.recentlyDeleted() }
                    }
                    RecentlyDeletedScreen(
                        chats = chats,
                        messages = messages,
                        onBack = { popBack() },
                        onRestoreChat = { chat -> AppGraph.chats.restoreDeleted(chat.id) },
                        onDeleteChat = { chat -> AppGraph.chats.permanentlyDelete(chat.id) },
                        onRestoreMessage = { message ->
                            AppGraph.messages.restoreDeleted(listOf(message.id))
                        },
                        onDeleteMessage = { message ->
                            AppGraph.messages.deleteLocal(listOf(message.id))
                        },
                    )
                }

                entry<BookmarksKey>(metadata = overlayMetadata) { key ->
                    val messages by produceState(emptyList<MessageItem>(), key.chatId) {
                        value = withContext(Dispatchers.IO) { AppGraph.messages.bookmarked(key.chatId) }
                    }
                    BookmarkedMessagesScreen(
                        messages = messages,
                        onBack = { popBack() },
                        onOpenChat = { openChat(key.chatId) },
                        onUnbookmark = { message ->
                            AppGraph.messages.setBookmarked(listOf(message.id), false)
                        },
                    )
                }

                entry<FindMyKey>(metadata = overlayMetadata) {
                    val viewModel: FindMyViewModel = viewModel(factory = FindMyViewModel.factory())
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    FindMyScreen(
                        uiState = state,
                        onRefresh = viewModel::refresh,
                        onBack = { popBack() },
                        showBackButton = true,
                    )
                }

                entry<SearchKey>(
                    metadata = if (isMultiPane) {
                        ListDetailSceneStrategy.detailPane()
                    } else {
                        overlayMetadata
                    },
                ) {
                    val viewModel: SearchViewModel =
                        viewModel(factory = SearchViewModel.factory(AppGraph.search, AppGraph.chats))
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val searchScope = rememberCoroutineScope()
                    fun openResult(chatId: Long) {
                        // The page is transient — land back on the list, not on search.
                        popBack()
                        openChat(chatId)
                    }
                    SearchScreen(
                        uiState = state,
                        onQueryChange = viewModel::onQueryChange,
                        onOpenChat = ::openResult,
                        onOpenContact = { contact ->
                            val address = contact.addresses.firstOrNull() ?: return@SearchScreen
                            searchScope.launch {
                                val chatId = withContext(Dispatchers.IO) {
                                    runCatching {
                                        CoreGraph.findOrCreateChat(listOf(address), sms = false)
                                    }.getOrNull()
                                }
                                if (chatId != null) openResult(chatId)
                            }
                        },
                        onBack = { popBack() },
                        docked = isMultiPane,
                    )
                }

                entry<AttachmentKey> { key ->
                    AttachmentViewerScreen(
                        guid = key.guid,
                        provider = AppGraph.attachments,
                        onBack = { popBack() },
                    )
                }

                entry<LoginKey>(metadata = overlayMetadata) {
                    val ctx = AppContext.current
                    val confDir = ctx?.filesDir?.absolutePath ?: ""
                    var provisioned by androidx.compose.runtime.saveable.rememberSaveable(confDir) {
                        androidx.compose.runtime.mutableStateOf(false)
                    }
                    if (!provisioned) {
                        LaunchedEffect(confDir) {
                            if (app.openbubbles.nativeapp.ui.login.isProvisioned(confDir)) {
                                provisioned = true
                            }
                        }
                    }

                    if (provisioned) {
                        LoginScreen(
                            handle = RustLoginHandle(path = confDir),
                            onFinished = { _ ->
                                ctx?.let { c ->
                                    NativePushService.reloadAfterLogin(c)
                                    requestBatteryExemptionOnce(c)
                                }
                                navigateHome()
                            },
                            onBack = { popBack() },
                            onRedoSetup = { provisioned = false },
                        )
                    } else {
                        app.openbubbles.nativeapp.ui.login.ProvisionScreen(
                            confDir = confDir,
                            onProvisioned = { provisioned = true },
                            onBack = { popBack() },
                        )
                    }
                }
            },
        )
        }
        }
        }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.captionBar),
    ) {
        // A restore's point of no return flips this BEFORE it mutates any
        // other UI-observed state, so the same recomposition pass that sees
        // the push-state shutdown disposes the nav entries instead of
        // re-running their synchronous store queries against a closed store.
        val restoreShutdown by CoreGraph.restoreShutdownStarted.collectAsStateWithLifecycle()
        if (restoreShutdown) {
            RestoreShutdownOverlay()
        } else {
            appContent()
        }
    }
}

/**
 * Detail-pane placeholder shown on wide windows before a conversation is picked.
 *
 * An empty state is one of the sanctioned decorative moments in Expressive, so
 * the icon backdrop uses a MaterialShapes polygon on primaryContainer and the
 * headline uses the emphasized type role. Deliberately static: a morph or loop
 * here would burn motion budget on a screen whose job is to be waited past.
 */
@Composable
private fun NoConversationSelected() {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = MaterialShapes.SoftBurst.toShape(),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            Text(
                text = "Choose a conversation",
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.padding(top = 18.dp),
            )
            Text(
                text = "Pick one from the list to read it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Persistent, actionable account state above the chat list. */
@Composable
private fun AccountConnectionBanner(
    state: AccountConnectionUiState,
    onAction: () -> Unit,
) {
    val containerColor = when (state.tone) {
        AccountConnectionTone.Neutral -> MaterialTheme.colorScheme.surfaceContainerHigh
        AccountConnectionTone.Attention -> MaterialTheme.colorScheme.tertiaryContainer
        AccountConnectionTone.Error -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (state.tone) {
        AccountConnectionTone.Neutral -> MaterialTheme.colorScheme.onSurface
        AccountConnectionTone.Attention -> MaterialTheme.colorScheme.onTertiaryContainer
        AccountConnectionTone.Error -> MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics {
                isTraversalGroup = true
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = contentColor.copy(alpha = 0.12f),
                contentColor = contentColor,
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = state.supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.78f),
                )
            }
            if (state.busy) {
                CircularProgressIndicator(
                    color = contentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                )
            } else if (state.action != null && state.actionLabel != null) {
                FilledTonalButton(onClick = onAction) {
                    Text(state.actionLabel)
                }
            }
        }
    }
}

/** Small smoke-test status (uniffi boot + core greeting) — debug builds only. */
@Composable
private fun DebugStatusFooter(lines: List<String>) {
    if (lines.isEmpty() || !BuildConfig.DEBUG) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


/**
 * One-time system dialog asking to exempt the app from battery optimization.
 * The push connection is the app's lifeline — OEM killers (and Doze on some
 * ROMs) will murder a "normal" background service; the exemption is what the
 * Flutter app asked for too (disable_battery_optimization plugin).
 */
@SuppressLint("BatteryLife") // Persistent APNs push needs the exemption; user-initiated one-time prompt.
private fun requestBatteryExemptionOnce(context: android.content.Context) {
    val prefs = context.getSharedPreferences("native_setup", android.content.Context.MODE_PRIVATE)
    if (prefs.getBoolean("battery_exemption_asked", false)) return
    prefs.edit { putBoolean("battery_exemption_asked", true) }

    runCatching {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            "package:${context.packageName}".toUri(),
        )
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
