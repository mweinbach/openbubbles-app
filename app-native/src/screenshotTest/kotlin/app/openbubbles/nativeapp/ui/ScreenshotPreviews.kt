package app.openbubbles.nativeapp.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.ui.chatlist.ChatListRow
import app.openbubbles.nativeapp.ui.chatlist.ChatListScreen
import app.openbubbles.nativeapp.ui.chatlist.ChatListUiState
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme

/**
 * Golden-image coverage for the surfaces the Material 3 Expressive work touched.
 *
 * These exist because the app has no UI tests at all, so every visual change so
 * far — the token-aligned shape scale, the flexible app bars, the list-detail
 * split — has been verifiable only by eye. Record with
 * `:app-native:updateDebugScreenshotTest`, check with
 * `:app-native:validateDebugScreenshotTest`.
 *
 * Fixed timestamps on purpose: relative times ("2m ago") would make every
 * golden fail a minute after it was recorded.
 */
private const val FIXED_NOW = 1_760_000_000_000L

@Preview(name = "phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "tablet", device = Devices.TABLET, showBackground = true)
annotation class FormFactorPreviews

private fun sampleState() = ChatListUiState(
    pinned = listOf(
        ChatListItem(
            id = 1,
            title = "Family",
            snippet = "Dinner at 7? I can bring dessert.",
            date = FIXED_NOW - 12 * 60_000L,
            unread = 3,
            pinned = true,
            avatarColor = 0xFF6750A4,
        ),
    ),
    chats = listOf(
        ChatListItem(
            id = 2,
            title = "Alex Chen",
            snippet = "The photos turned out great!",
            date = FIXED_NOW - 52 * 60_000L,
            unread = 1,
            pinned = false,
            avatarColor = 0xFF006C4C,
        ),
        ChatListItem(
            id = 3,
            title = "Design Team",
            snippet = "Maya: pushed the new mocks to Figma",
            date = FIXED_NOW - 3 * 60 * 60_000L,
            unread = 0,
            pinned = false,
            avatarColor = 0xFF8C4A60,
        ),
        ChatListItem(
            id = 4,
            title = "Weekend hike",
            snippet = "sounds good — see you at the trailhead",
            date = FIXED_NOW - 22 * 60 * 60_000L,
            unread = 0,
            pinned = false,
            muted = true,
            avatarColor = 0xFF386A20,
        ),
    ),
)

/** Collapsing flexible app bar, section headers, and the width cap on wide windows. */
@PreviewTest
@FormFactorPreviews
@Composable
fun ChatListScreenScreenshot() {
    OpenBubblesTheme {
        ChatListScreen(
            uiState = sampleState(),
            onQueryChange = {},
            onChatClick = {},
        )
    }
}

/** Empty state: the branded call to action, not just blank space. */
@PreviewTest
@Preview(name = "empty", device = Devices.PHONE, showBackground = true)
@Composable
fun ChatListEmptyScreenshot() {
    OpenBubblesTheme {
        ChatListScreen(
            uiState = ChatListUiState(),
            onQueryChange = {},
            onChatClick = {},
        )
    }
}

/** Row emphasis: unread uses the emphasized type roles, read does not. */
@PreviewTest
@Preview(name = "row-light", showBackground = true)
@Preview(name = "row-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ChatListRowScreenshot() {
    OpenBubblesTheme {
        ChatListRow(
            chat = ChatListItem(
                id = 1,
                title = "Alex Chen",
                snippet = "sounds good — see you at the trailhead",
                date = FIXED_NOW - 52 * 60_000L,
                unread = 2,
                pinned = false,
                avatarColor = 0xFF34C759,
            ),
            onClick = {},
        )
    }
}
