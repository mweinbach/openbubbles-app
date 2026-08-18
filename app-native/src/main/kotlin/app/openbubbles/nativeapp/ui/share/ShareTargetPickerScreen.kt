package app.openbubbles.nativeapp.ui.share

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.ui.chatlist.ChatListScreen
import app.openbubbles.nativeapp.ui.chatlist.ChatListUiState

@Composable
fun ShareTargetPickerScreen(
    uiState: ChatListUiState,
    onChatClick: (ChatListItem) -> Unit,
    onNewChat: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChatListScreen(
        uiState = uiState,
        onChatClick = onChatClick,
        onNewChat = onNewChat,
        showBackButton = true,
        onBack = onBack,
        header = {
            Text(
                "Choose a conversation to share into",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
        modifier = modifier,
    )
}
