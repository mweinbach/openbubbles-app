package app.openbubbles.nativeapp.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.openbubbles.nativeapp.NativeMainActivity
import app.openbubbles.nativeapp.data.AppGraph
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.service.NativePushService
import app.openbubbles.nativeapp.ui.attachmentviewer.AttachmentViewerScreen
import app.openbubbles.nativeapp.ui.chat.ChatScreen
import app.openbubbles.nativeapp.ui.chat.ChatViewModel
import app.openbubbles.nativeapp.ui.chatinfo.ChatInfoScreen
import app.openbubbles.nativeapp.ui.chatinfo.rememberParticipantRows
import app.openbubbles.nativeapp.ui.chatlist.ChatListScreen
import app.openbubbles.nativeapp.ui.chatlist.ChatListViewModel
import app.openbubbles.nativeapp.ui.login.LoginScreen
import app.openbubbles.nativeapp.ui.login.RustLoginHandle
import app.openbubbles.nativeapp.ui.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Routes {
    const val CHATS = "chats"
    const val CHAT_PATTERN = "chat/{id}"
    const val LOGIN = "login"
    const val SETTINGS = "settings"
    const val CHAT_INFO_PATTERN = "chatinfo/{id}"
    const val ATTACHMENT_PATTERN = "attachment/{guid}"
    const val CHAT_ARG = "id"
    const val ATTACHMENT_ARG = "guid"
    fun chat(chatId: Long): String = "chat/$chatId"
    fun chatInfo(chatId: Long): String = "chatinfo/$chatId"

    /** Attachment guids can contain ':'/'/' — encode for the path segment. */
    fun attachment(guid: String): String = "attachment/${Uri.encode(guid)}"
}

/** Root scaffold: navigation between the chat list, conversations, viewer, info, and login. */
@Composable
fun OpenBubblesApp(
    debugLines: List<String> = emptyList(),
    /** Chat guid from a notification tap; resolved and consumed once. */
    startChatGuid: String? = null,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val pushState by PushStateHolder.stateFlow.collectAsStateWithLifecycle()

    // Deep link from a notification tap: resolve the guid to a chat id and
    // navigate once, then clear the pending static so config changes (and
    // any recomposition) don't re-trigger it.
    LaunchedEffect(startChatGuid) {
        val guid = startChatGuid?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val chatId = withContext(Dispatchers.IO) { CoreGraph.chatIdForGuid(guid) }
        if (chatId != null && chatId > 0L &&
            navController.currentDestination?.route != Routes.chat(chatId)
        ) {
            navController.navigate(Routes.chat(chatId))
        }
        NativeMainActivity.pendingChatGuid = null
    }

    Column(modifier = modifier) {
        if (route == Routes.CHATS && pushState == null) {
            SignInBanner(onSignIn = { navController.navigate(Routes.LOGIN) })
        }
        NavHost(
            navController = navController,
            startDestination = Routes.CHATS,
            modifier = Modifier.weight(1f),
        ) {
            composable(Routes.CHATS) {
                val viewModel: ChatListViewModel = viewModel(factory = ChatListViewModel.factory(AppGraph.chats))
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ChatListScreen(
                    uiState = state,
                    onQueryChange = viewModel::onQueryChange,
                    onChatClick = { chat -> navController.navigate(Routes.chat(chat.id)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    footer = { DebugStatusFooter(debugLines) },
                )
            }
            composable(
                route = Routes.CHAT_PATTERN,
                arguments = listOf(navArgument(Routes.CHAT_ARG) { type = NavType.LongType }),
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getLong(Routes.CHAT_ARG) ?: 0L
                val viewModel: ChatViewModel = viewModel(
                    key = "chat-$chatId",
                    factory = ChatViewModel.factory(
                        chatId,
                        AppGraph.chats,
                        AppGraph.messages,
                        AppGraph.sender,
                        AppGraph.attachmentSender,
                        AppGraph.typing,
                    ),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ChatScreen(
                    uiState = state,
                    onInputChange = viewModel::onInputChange,
                    onSend = viewModel::sendMessage,
                    onLoadOlder = viewModel::loadOlder,
                    onSendAttachment = viewModel::sendAttachment,
                    onBack = { navController.popBackStack() },
                    onOpenChatInfo = { navController.navigate(Routes.chatInfo(chatId)) },
                    onOpenAttachment = { guid -> navController.navigate(Routes.attachment(guid)) },
                    onDownloadAttachment = { attachment -> AppGraph.requestAttachmentDownload(attachment.guid) },
                    attachmentFile = AppGraph.attachments::localFile,
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.CHAT_INFO_PATTERN,
                arguments = listOf(navArgument(Routes.CHAT_ARG) { type = NavType.LongType }),
            ) { backStackEntry ->
                val chatId = backStackEntry.arguments?.getLong(Routes.CHAT_ARG) ?: 0L
                val chats by remember(chatId) { AppGraph.chats.chats() }
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val chat = chats.firstOrNull { it.id == chatId }
                val addresses by produceState<List<String>>(initialValue = emptyList(), chatId) {
                    value = withContext(Dispatchers.IO) { AppGraph.chatInfo.participantAddresses(chatId) }
                }
                val participants = rememberParticipantRows(addresses)
                ChatInfoScreen(
                    chat = chat,
                    participants = participants,
                    onBack = { navController.popBackStack() },
                    onLeaveChat = {
                        app.openbubbles.nativeapp.data.CoreGraph.leaveChat(chatId)
                        navController.popBackStack()
                    },
                )
            }
            composable(
                route = Routes.ATTACHMENT_PATTERN,
                arguments = listOf(navArgument(Routes.ATTACHMENT_ARG) { type = NavType.StringType }),
            ) { backStackEntry ->
                val guid = backStackEntry.arguments?.getString(Routes.ATTACHMENT_ARG).orEmpty()
                AttachmentViewerScreen(
                    guid = guid,
                    provider = AppGraph.attachments,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.LOGIN) {
                val context = NativeMainActivity.appContext
                val confDir = context?.filesDir?.absolutePath ?: ""
                var provisioned by androidx.compose.runtime.saveable.rememberSaveable(confDir) {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                if (!provisioned) {
                    androidx.compose.runtime.LaunchedEffect(confDir) {
                        if (app.openbubbles.nativeapp.ui.login.isProvisioned(confDir)) {
                            provisioned = true
                        }
                    }
                }

                if (provisioned) {
                    LoginScreen(
                        handle = RustLoginHandle(path = confDir),
                        onFinished = { _ ->
                            context?.let { ctx ->
                                NativePushService.start(ctx)
                                requestBatteryExemptionOnce(ctx)
                            }
                            navController.popBackStack(Routes.CHATS, inclusive = false)
                        },
                        onBack = { navController.popBackStack() },
                    )
                } else {
                    app.openbubbles.nativeapp.ui.login.ProvisionScreen(
                        confDir = confDir,
                        onProvisioned = { provisioned = true },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

/** Shown when no live push state is installed: gate to the login flow. */
@Composable
private fun SignInBanner(onSignIn: () -> Unit) {
    Surface(
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onSignIn)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sign in with your Apple ID to send and receive messages",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** Small, always-visible smoke-test status (uniffi boot + shared greeting). */
@Composable
private fun DebugStatusFooter(lines: List<String>) {
    if (lines.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall,
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
