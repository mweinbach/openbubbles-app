package app.openbubbles.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.openbubbles.desktop.login.LoginController
import app.openbubbles.desktop.login.LoginScreen
import app.openbubbles.desktop.login.RustLoginHandle
import app.openbubbles.desktop.ui.ChatScreen
import app.openbubbles.desktop.ui.ChatsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.awt.Dimension

/**
 * Compose Desktop entry point for the native (no-Dart) OpenBubbles client.
 *
 * Login -> Chats -> Chat, simple state-based navigation (no nav library).
 * At startup `hasSavedUsers` decides between the login flow and an
 * automatic daemon boot straight to the chat list.
 */
fun main() {
    application {
        val state = rememberWindowState(width = 480.dp, height = 900.dp)
        Window(
            title = "OpenBubbles",
            state = state,
            onCloseRequest = ::exitApplication,
        ) {
            // Compose Desktop has no declarative minimum size; pin the
            // underlying AWT window instead (px == dp at desktop density 1).
            window.minimumSize = Dimension(420, 720)

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App()
                }
            }
        }
    }
}

/** Navigation destinations. */
private sealed interface Screen {
    data object Splash : Screen
    data object Login : Screen
    data object Chats : Screen
    data class Chat(val chatId: Long) : Screen
}

@Composable
private fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Splash) }

    // Startup: restore login state from ~/.openbubbles-natives (id.plist).
    LaunchedEffect(Unit) {
        val saved = withContext(Dispatchers.IO) {
            runCatching { DesktopGraph.hasSavedUsers() }.getOrDefault(false)
        }
        if (saved) {
            DesktopGraph.startDaemon()
            screen = Screen.Chats
        } else {
            screen = Screen.Login
        }
    }

    // A login completing registration rebuilds the daemon (initNative picks
    // up the fresh id.plist) and drops the user on the chat list.
    val loginController = remember {
        LoginController(
            handle = RustLoginHandle(path = DesktopGraph.dataDir.absolutePath),
            onRegistered = { DesktopGraph.startDaemon() },
        )
    }
    val loginUi by loginController.screen.collectAsState()
    LaunchedEffect(loginUi) {
        if (loginUi is app.openbubbles.desktop.login.LoginScreen.Done && screen == Screen.Login) {
            screen = Screen.Chats
        }
    }

    when (val s = screen) {
        Screen.Splash -> CenteredBusy("Starting…")
        Screen.Login -> LoginScreen(controller = loginController)
        Screen.Chats -> {
            if (DesktopGraph.store == null) {
                StoreError()
            } else {
                ChatsScreen(
                    chats = DesktopGraph.chatRepo?.observeChats() ?: flowOf(emptyList()),
                    connected = DesktopGraph.PushStateHolder.stateFlow.collectAsState().value != null,
                    onOpenChat = { id -> screen = Screen.Chat(id) },
                    onReconnect = { DesktopGraph.startDaemon() },
                )
            }
        }
        is Screen.Chat -> ChatScreen(
            chatId = s.chatId,
            onBack = { screen = Screen.Chats },
        )
    }
}

@Composable
private fun CenteredBusy(label: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(label)
        }
    }
}

@Composable
private fun StoreError() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "Could not open the message database in\n${DesktopGraph.dataDir.absolutePath}",
            color = MaterialTheme.colorScheme.error,
        )
    }
}
