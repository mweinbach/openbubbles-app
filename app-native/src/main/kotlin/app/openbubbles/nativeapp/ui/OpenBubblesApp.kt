package app.openbubbles.nativeapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.openbubbles.nativeapp.data.AppGraph
import app.openbubbles.nativeapp.ui.chat.ChatScreen
import app.openbubbles.nativeapp.ui.chat.ChatViewModel
import app.openbubbles.nativeapp.ui.chatlist.ChatListScreen
import app.openbubbles.nativeapp.ui.chatlist.ChatListViewModel

object Routes {
    const val CHATS = "chats"
    const val CHAT_PATTERN = "chat/{id}"
    const val CHAT_ARG = "id"
    fun chat(chatId: Long): String = "chat/$chatId"
}

/** Root scaffold: navigation between the chat list and conversations. */
@Composable
fun OpenBubblesApp(
    debugLines: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.CHATS,
        modifier = modifier,
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
