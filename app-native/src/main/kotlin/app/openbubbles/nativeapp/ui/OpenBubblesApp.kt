package app.openbubbles.nativeapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.service.NativePushService
import app.openbubbles.nativeapp.ui.chat.ChatScreen
import app.openbubbles.nativeapp.ui.chat.ChatViewModel
import app.openbubbles.nativeapp.ui.chatlist.ChatListScreen
import app.openbubbles.nativeapp.ui.chatlist.ChatListViewModel
import app.openbubbles.nativeapp.ui.login.LoginScreen
import app.openbubbles.nativeapp.ui.login.RustLoginHandle

object Routes {
    const val CHATS = "chats"
    const val CHAT_PATTERN = "chat/{id}"
    const val LOGIN = "login"
    const val CHAT_ARG = "id"
    fun chat(chatId: Long): String = "chat/$chatId"
}

/** Root scaffold: navigation between the chat list, conversations, and login. */
@Composable
fun OpenBubblesApp(
    debugLines: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val pushState by PushStateHolder.stateFlow.collectAsStateWithLifecycle()

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
                    factory = ChatViewModel.factory(chatId, AppGraph.chats, AppGraph.messages, AppGraph.sender),
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ChatScreen(
                    uiState = state,
                    onInputChange = viewModel::onInputChange,
                    onSend = viewModel::sendMessage,
                    onLoadOlder = viewModel::loadOlder,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.LOGIN) {
                val context = NativeMainActivity.appContext
                LoginScreen(
                    handle = RustLoginHandle(
                        path = context?.filesDir?.absolutePath ?: "",
                    ),
                    onFinished = { _ ->
                        context?.let { NativePushService.start(it) }
                        navController.popBackStack(Routes.CHATS, inclusive = false)
                    },
                    onBack = { navController.popBackStack() },
                )
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
