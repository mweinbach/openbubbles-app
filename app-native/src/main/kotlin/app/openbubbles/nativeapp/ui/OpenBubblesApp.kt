package app.openbubbles.nativeapp.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AdaptStrategy
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.toShape
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.rememberNavigationSuiteScaffoldState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.openbubbles.nativeapp.BuildConfig
import app.openbubbles.nativeapp.NativeMainActivity
import app.openbubbles.nativeapp.data.AppContext
import app.openbubbles.nativeapp.data.AppGraph
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.service.BatterySaver
import app.openbubbles.nativeapp.service.NativePushService
import app.openbubbles.nativeapp.ui.attachmentviewer.AttachmentViewerScreen
import app.openbubbles.nativeapp.ui.chat.ChatScreen
import app.openbubbles.nativeapp.ui.chat.ChatViewModel
import app.openbubbles.nativeapp.ui.chatinfo.ChatInfoScreen
import app.openbubbles.nativeapp.ui.chatinfo.rememberParticipantRows
import app.openbubbles.nativeapp.ui.chatcreator.NewChatScreen
import app.openbubbles.nativeapp.ui.chatlist.ChatListScreen
import app.openbubbles.nativeapp.ui.chatlist.ChatListViewModel
import app.openbubbles.nativeapp.ui.findmy.FindMyScreen
import app.openbubbles.nativeapp.ui.findmy.FindMyViewModel
import app.openbubbles.nativeapp.ui.login.LoginScreen
import app.openbubbles.nativeapp.ui.login.RustLoginHandle
import app.openbubbles.nativeapp.ui.onboarding.OnboardingScreen
import app.openbubbles.nativeapp.ui.settings.SettingsScreen
import app.openbubbles.nativeapp.ui.common.LocalAppSharedTransitionScope
import app.openbubbles.nativeapp.ui.common.LocalIsMultiPane
import app.openbubbles.nativeapp.ui.theme.defaultEffectsSpec
import app.openbubbles.nativeapp.ui.theme.fastEffectsSpec
import kotlinx.coroutines.Dispatchers
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
    const val FIND_MY = "findmy"
    const val NEW_CHAT = "newchat"
    fun chat(chatId: Long): String = "chat/$chatId"
    fun chatInfo(chatId: Long): String = "chatinfo/$chatId"

    /** Attachment guids can contain ':'/'/' — encode for the path segment. */
    fun attachment(guid: String): String = "attachment/${Uri.encode(guid)}"
}

// --------------------------------------------------------------- destinations

@Serializable
data object ChatsKey : NavKey

@Serializable
data class ChatKey(val chatId: Long) : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object FindMyKey : NavKey

@Serializable
data object NewChatKey : NavKey

@Serializable
data class ChatInfoKey(val chatId: Long) : NavKey

@Serializable
data class AttachmentKey(val guid: String) : NavKey

@Serializable
data object LoginKey : NavKey

private fun NavKey.toRoute(): String = when (this) {
    is ChatsKey -> Routes.CHATS
    is ChatKey -> Routes.chat(chatId)
    is SettingsKey -> Routes.SETTINGS
    is FindMyKey -> Routes.FIND_MY
    is NewChatKey -> Routes.NEW_CHAT
    is ChatInfoKey -> Routes.chatInfo(chatId)
    is AttachmentKey -> Routes.attachment(guid)
    is LoginKey -> Routes.LOGIN
    else -> Routes.CHATS
}

private fun routeToKey(route: String): NavKey? = when {
    route == Routes.CHATS -> ChatsKey
    route == Routes.SETTINGS -> SettingsKey
    route == Routes.FIND_MY -> FindMyKey
    route == Routes.NEW_CHAT -> NewChatKey
    route == Routes.LOGIN -> LoginKey
    route.startsWith("chat/") -> route.removePrefix("chat/").toLongOrNull()?.let(::ChatKey)
    route.startsWith("chatinfo/") -> route.removePrefix("chatinfo/").toLongOrNull()?.let(::ChatInfoKey)
    route.startsWith("attachment/") -> AttachmentKey(Uri.decode(route.removePrefix("attachment/")))
    else -> null
}

private data class TopLevelDestination(
    val key: NavKey,
    val label: String,
    val icon: ImageVector,
)

private val TopLevelDestinations = listOf(
    TopLevelDestination(ChatsKey, "Chats", Icons.AutoMirrored.Filled.Chat),
    TopLevelDestination(FindMyKey, "Find My", Icons.Filled.LocationOn),
    TopLevelDestination(SettingsKey, "Settings", Icons.Filled.Settings),
)

/**
 * Root scaffold.
 *
 * The chat list and a conversation are a list-detail pair: on a compact window
 * they are separate full-screen destinations, and from medium width up they sit
 * side by side, with conversation details taking the third pane. That behavior
 * comes from [ListDetailSceneStrategy] reading pane metadata off the back stack
 * rather than from any explicit width branching here.
 */
@Composable
fun OpenBubblesApp(
    debugLines: List<String> = emptyList(),
    /** Chat guid from a notification tap; resolved and consumed once. */
    startChatGuid: String? = null,
    /** Actual route restored after the hidden Compose tree was released. */
    resumeRoute: String? = null,
    onRouteChanged: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(ChatsKey)
    val current = backStack.lastOrNull()
    val pushState by PushStateHolder.stateFlow.collectAsStateWithLifecycle()

    // A conversation shown beside its list must not offer a back arrow, and the
    // navigation container stays visible in that layout. The directive is the
    // v2 window-info variant with the two-panes-on-medium override: a messaging
    // client is the canonical list-detail app, so foldables and portrait
    // tablets get list|chat instead of a stretched phone layout, and from
    // 1200dp up conversation details get the third pane.
    val directive = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(
        currentWindowAdaptiveInfoV2(),
    )
    val isMultiPane = directive.maxHorizontalPartitions > 1

    fun navigateTo(key: NavKey) {
        if (backStack.lastOrNull() == key) return
        backStack.add(key)
    }

    fun popBack() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
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
        val key = ChatKey(chatId)
        if (backStack.lastOrNull() == key) return
        // A conversation belongs to the Chats tab: entering one from another
        // top-level destination (notification tap while in Settings) replaces
        // it so the detail pane never renders orphaned beside nothing.
        backStack.removeAll { it is SettingsKey || it is FindMyKey }
        while (backStack.size > 1 &&
            (backStack.last() is ChatKey || backStack.last() is ChatInfoKey || backStack.last() is AttachmentKey)
        ) {
            backStack.removeAt(backStack.lastIndex)
        }
        backStack.add(key)
    }

    // The conversation whose row should read as selected in the list pane.
    // Only meaningful when the list is visible beside the detail.
    val selectedChatId = if (isMultiPane) {
        (backStack.lastOrNull { it is ChatKey } as? ChatKey)?.chatId
    } else {
        null
    }

    /**
     * Tab switches preserve the Chats conversation stack: Chats pops back to
     * whatever the user had open; other destinations sit on top of it and
     * replace each other. Clearing the stack on every tab tap threw away the
     * open conversation, which is not what tabs do.
     */
    fun navigateTopLevel(destination: TopLevelDestination) {
        if (backStack.lastOrNull() == destination.key) return
        if (destination.key == ChatsKey) {
            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        } else {
            backStack.removeAll { it is SettingsKey || it is FindMyKey }
            backStack.add(destination.key)
        }
    }

    /** Restores the persisted route at the recorded depth (chat info keeps its chat underneath). */
    fun restoreResumeRoute() {
        if (backStack.lastOrNull() != ChatsKey) return
        when (val key = resumeRoute?.takeIf { it != Routes.CHATS }?.let(::routeToKey)) {
            is ChatInfoKey -> {
                backStack.add(ChatKey(key.chatId))
                backStack.add(key)
            }
            null -> Unit
            else -> backStack.add(key)
        }
    }

    LaunchedEffect(resumeRoute, startChatGuid) {
        if (startChatGuid == null) restoreResumeRoute()
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
            if (backStack.none { it is ChatKey && it.chatId == chatId }) {
                openChat(chatId)
            }
        } else {
            restoreResumeRoute()
        }
        NativeMainActivity.pendingChatGuid = null
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
                onboardingPrefs?.edit()?.putBoolean("onboarding_complete", true)?.apply()
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
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(
        directive = directive,
        paneExpansionState = paneExpansionState,
        // Two panes can't fit list + chat + details: details levitate as a
        // dialog there instead of hiding outright (the default), which made
        // the details button a dead end on foldables and portrait tablets.
        adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(
            extraPaneAdaptStrategy = AdaptStrategy.Levitate(),
        ),
        paneExpansionDragHandle = { state ->
            // One interaction source for both the drag modifier and the handle,
            // so the handle's pressed shape morph tracks the actual drag.
            val interactionSource = remember { MutableInteractionSource() }
            VerticalDragHandle(
                interactionSource = interactionSource,
                modifier = Modifier.paneExpansionDraggable(state, 48.dp, interactionSource),
            )
        },
    )

    // Hoisted spec reads: the transition lambdas are not composable.
    val navEnterSpec = defaultEffectsSpec<Float>()
    val navExitSpec = fastEffectsSpec<Float>()

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
            transitionSpec = { fadeIn(navEnterSpec) togetherWith fadeOut(navExitSpec) },
            popTransitionSpec = { fadeIn(navEnterSpec) togetherWith fadeOut(navExitSpec) },
            predictivePopTransitionSpec = { fadeIn(navEnterSpec) togetherWith fadeOut(navExitSpec) },
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
                    ChatListScreen(
                        uiState = state,
                        onQueryChange = viewModel::onQueryChange,
                        onChatClick = { chat -> openChat(chat.id) },
                        selectedChatId = selectedChatId,
                        containerColor = if (isMultiPane) {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        onNewChat = { navigateTo(NewChatKey) },
                        onTogglePinned = viewModel::togglePinned,
                        onToggleMuted = viewModel::toggleMuted,
                        onMuteFor = viewModel::muteFor,
                        onToggleArchived = viewModel::toggleArchived,
                        onDelete = viewModel::delete,
                        header = {
                            if (pushState == null) {
                                SignInBanner(onSignIn = { navigateTo(LoginKey) })
                            }
                        },
                        footer = { DebugStatusFooter(debugLines) },
                    )
                }

                entry<ChatKey>(metadata = ListDetailSceneStrategy.detailPane()) { key ->
                    val chatId = key.chatId
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
                        ),
                    )
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    ChatScreen(
                        uiState = state,
                        onInputChange = viewModel::onInputChange,
                        onSend = viewModel::sendMessage,
                        onLoadOlder = viewModel::loadOlder,
                        onSendAttachment = viewModel::sendAttachment,
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
                        onBack = { popBack() },
                        // Beside its own list there is nothing to go back to.
                        showBackButton = !isMultiPane,
                        onOpenChatInfo = { navigateTo(ChatInfoKey(chatId)) },
                        onOpenAttachment = { guid -> navigateTo(AttachmentKey(guid)) },
                        onDownloadAttachment = { attachment ->
                            AppGraph.requestAttachmentDownload(attachment.guid)
                        },
                        attachmentFile = AppGraph.attachments::localFile,
                    )
                }

                entry<ChatInfoKey>(metadata = ListDetailSceneStrategy.extraPane()) { key ->
                    val chatId = key.chatId
                    val chats by remember(chatId) { AppGraph.chats.chats() }
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    val chat = chats.firstOrNull { it.id == chatId }
                    var participantRevision by remember(chatId) {
                        androidx.compose.runtime.mutableIntStateOf(0)
                    }
                    val addresses by produceState<List<String>>(
                        initialValue = emptyList(),
                        chatId,
                        participantRevision,
                    ) {
                        value = withContext(Dispatchers.IO) { AppGraph.chatInfo.participantAddresses(chatId) }
                    }
                    val participants = rememberParticipantRows(addresses)
                    ChatInfoScreen(
                        chat = chat,
                        participants = participants,
                        onBack = { popBack() },
                        // A visible extra pane (or levitated dialog) has
                        // nothing to navigate back to.
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
                    )
                }

                entry<NewChatKey> {
                    NewChatScreen(
                        onChatOpened = { chatId ->
                            // Drop the creator, then open the new conversation.
                            popBack()
                            navigateTo(ChatKey(chatId))
                        },
                        onBack = { popBack() },
                    )
                }

                entry<SettingsKey> {
                    SettingsScreen(
                        onBack = { navigateTopLevel(TopLevelDestinations.first()) },
                        onOpenFindMy = { navigateTo(FindMyKey) },
                        showBackButton = false,
                    )
                }

                entry<FindMyKey> {
                    val viewModel: FindMyViewModel = viewModel(factory = FindMyViewModel.factory())
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    FindMyScreen(
                        uiState = state,
                        onRefresh = viewModel::refresh,
                        onBack = { navigateTopLevel(TopLevelDestinations.first()) },
                        showBackButton = false,
                    )
                }

                entry<AttachmentKey> { key ->
                    AttachmentViewerScreen(
                        guid = key.guid,
                        provider = AppGraph.attachments,
                        onBack = { popBack() },
                    )
                }

                entry<LoginKey> {
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
                                navigateTopLevel(TopLevelDestinations.first())
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

    // The navigation container belongs on top-level destinations, and also
    // alongside a conversation once the list stays on screen next to it.
    val showNavigationSuite = TopLevelDestinations.any { it.key == current } ||
        (isMultiPane && (current is ChatKey || current is ChatInfoKey))

    // The scaffold stays mounted and animates its navigation component in and
    // out — conditionally composing it used to dispose and recreate the whole
    // NavDisplay subtree on every full-screen transition.
    val suiteState = rememberNavigationSuiteScaffoldState()
    LaunchedEffect(showNavigationSuite) {
        if (showNavigationSuite) suiteState.show() else suiteState.hide()
    }

    NavigationSuiteScaffold(
        state = suiteState,
        modifier = modifier,
        navigationSuiteItems = {
            TopLevelDestinations.forEach { destination ->
                val selected = current == destination.key ||
                    (destination.key == ChatsKey && (current is ChatKey || current is ChatInfoKey))
                item(
                    selected = selected,
                    onClick = { navigateTopLevel(destination) },
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(destination.label) },
                )
            }
        },
        content = appContent,
    )
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

/** Shown when no live push state is installed: gate to the login flow. */
@Composable
private fun SignInBanner(onSignIn: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
                    text = "Sign in to message",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Use your Apple ID to send and receive iMessages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onSignIn) {
                Text("Sign in")
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
private fun requestBatteryExemptionOnce(context: android.content.Context) {
    val prefs = context.getSharedPreferences("native_setup", android.content.Context.MODE_PRIVATE)
    if (prefs.getBoolean("battery_exemption_asked", false)) return
    prefs.edit().putBoolean("battery_exemption_asked", true).apply()

    runCatching {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            android.net.Uri.parse("package:${context.packageName}"),
        )
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
