package app.openbubbles.nativeapp.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.data.RichLinkPreview
import app.openbubbles.nativeapp.data.SharedContentPreview
import app.openbubbles.nativeapp.ui.chat.ChatScreen
import app.openbubbles.nativeapp.ui.chat.ChatUiState
import app.openbubbles.nativeapp.ui.chatinfo.ChatInfoScreen
import app.openbubbles.nativeapp.ui.chatinfo.ContactDetails
import app.openbubbles.nativeapp.ui.chatinfo.ContactDetailsCard
import app.openbubbles.nativeapp.ui.chatinfo.ContactLocationUi
import app.openbubbles.nativeapp.ui.chatinfo.ParticipantRow
import app.openbubbles.nativeapp.ui.chatlist.ChatListKind
import app.openbubbles.nativeapp.ui.chatlist.ChatListRow
import app.openbubbles.nativeapp.ui.chatlist.ChatListScreen
import app.openbubbles.nativeapp.ui.chatlist.ChatListUiState
import app.openbubbles.nativeapp.ui.onboarding.OnboardingScreen
import app.openbubbles.nativeapp.ui.settings.SettingsScreen
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
        ChatListItem(
            id = 5,
            title = "Carolina",
            snippet = "Landing in twenty minutes",
            date = FIXED_NOW - 18 * 60_000L,
            unread = 0,
            pinned = true,
            avatarColor = 0xFF8C4A60,
        ),
        ChatListItem(
            id = 6,
            title = "Emily",
            snippet = "Shared a photo",
            date = FIXED_NOW - 24 * 60_000L,
            unread = 0,
            pinned = true,
            avatarColor = 0xFF5066A8,
        ),
        ChatListItem(
            id = 7,
            title = "Weekend Crew",
            snippet = "Meet at the trailhead",
            date = FIXED_NOW - 31 * 60_000L,
            unread = 1,
            pinned = true,
            avatarColor = 0xFF386A20,
        ),
        ChatListItem(
            id = 8,
            title = "Design Team",
            snippet = "New mocks are ready",
            date = FIXED_NOW - 42 * 60_000L,
            unread = 0,
            pinned = true,
            avatarColor = 0xFF006C4C,
        ),
        ChatListItem(
            id = 9,
            title = "Neighbors",
            snippet = "Block party on Sunday",
            date = FIXED_NOW - 48 * 60_000L,
            unread = 2,
            pinned = true,
            avatarColor = 0xFF9C4146,
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

/** Compact Messages-style app bar, flat rows, and the width cap on wide windows. */
@PreviewTest
@FormFactorPreviews
@Composable
fun ChatListScreenScreenshot() {
    // dynamicColor = false: Layoutlib has no wallpaper, so the dynamic path
    // would make goldens renderer-dependent.
    OpenBubblesTheme(dynamicColor = false) {
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
    OpenBubblesTheme(dynamicColor = false) {
        ChatListScreen(
            uiState = ChatListUiState(),
            onQueryChange = {},
            onChatClick = {},
        )
    }
}

/** Settings-managed archive: empty state explains long-press on the inbox. */
@PreviewTest
@Preview(name = "archive-empty", device = Devices.PHONE, showBackground = true)
@Composable
fun ChatListArchiveEmptyScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        ChatListScreen(
            uiState = ChatListUiState(),
            kind = ChatListKind.Archive,
            showBackButton = true,
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
    OpenBubblesTheme(dynamicColor = false) {
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

/** Selected state: secondaryContainer + rounder shape when open in the detail pane. */
@PreviewTest
@Preview(name = "row-selected", showBackground = true)
@Preview(name = "row-selected-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ChatListRowSelectedScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        ChatListRow(
            chat = ChatListItem(
                id = 3,
                title = "Design Team",
                snippet = "Maya: pushed the new mocks to Figma",
                date = FIXED_NOW - 18 * 60_000L,
                unread = 0,
                pinned = true,
                avatarColor = 0xFF8C4A60,
            ),
            onClick = {},
            selected = true,
        )
    }
}

private fun message(
    id: Long,
    text: String,
    fromMe: Boolean,
    status: MessageStatus = MessageStatus.READ,
    richLink: RichLinkPreview? = null,
) =
    MessageItem(
        id = id,
        text = text,
        isFromMe = fromMe,
        date = FIXED_NOW - (10 - id) * 60_000L,
        status = status,
        isGroupEvent = false,
        reactionEmoji = null,
        richLink = richLink,
    )

/**
 * The transcript: grouped bubbles, the expressive composer, and the
 * SMS-green service identity on an SMS conversation.
 */
@PreviewTest
@Preview(name = "chat-imessage", device = Devices.PHONE, showBackground = true)
@Preview(name = "chat-dark", device = Devices.PHONE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ChatScreenScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        ChatScreen(
            uiState = ChatUiState(
                chat = ChatListItem(
                    id = 2,
                    title = "Alex Chen",
                    snippet = null,
                    date = FIXED_NOW,
                    unread = 0,
                    pinned = false,
                    avatarColor = 0xFF006C4C,
                    isSms = false,
                ),
                messages = listOf(
                    message(1, "hey! still on for the hike saturday?", fromMe = false),
                    message(
                        id = 2,
                        text = "https://www.nps.gov/yose/index.htm",
                        fromMe = false,
                        richLink = RichLinkPreview(
                            url = "https://www.nps.gov/yose/index.htm",
                            displayHost = "nps.gov",
                            title = "Yosemite National Park",
                            summary = "Plan the route, check conditions, and get ready for Saturday's hike.",
                            imageBytes = null,
                            imageMime = null,
                            iconBytes = null,
                            iconMime = null,
                        ),
                    ),
                    message(3, "grabbing coffee now, want anything?", fromMe = true, status = MessageStatus.DELIVERED),
                ),
                typingSenders = listOf("alex@icloud.com"),
            ),
            onInputChange = {},
            onSend = {},
            onLoadOlder = {},
            onBack = {},
        )
    }
}

/** SMS transcript: outgoing bubbles take the fixed green service color. */
@PreviewTest
@Preview(name = "chat-sms", device = Devices.PHONE, showBackground = true)
@Composable
fun ChatScreenSmsScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        ChatScreen(
            uiState = ChatUiState(
                chat = ChatListItem(
                    id = 5,
                    title = "Sam (SMS)",
                    snippet = null,
                    date = FIXED_NOW,
                    unread = 0,
                    pinned = false,
                    avatarColor = 0xFF3949AB,
                    isSms = true,
                ),
                messages = listOf(
                    message(1, "carrier thread below", fromMe = false),
                    message(2, "green bubble out", fromMe = true, status = MessageStatus.SENT),
                ),
            ),
            onInputChange = {},
            onSend = {},
            onLoadOlder = {},
            onBack = {},
        )
    }
}

/** Preference groups: Messages-style segmented list, foldable small app bar. */
@PreviewTest
@FormFactorPreviews
@Composable
fun SettingsScreenScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        SettingsScreen(onBack = {})
    }
}

/** First-run brand moment: expressive display type, gradient bubble. */
@PreviewTest
@Preview(name = "onboarding-phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "onboarding-dark", device = Devices.PHONE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun OnboardingScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        OnboardingScreen(onFinished = {}, onLaunchSignIn = {})
    }
}

/**
 * The contact preview opened from a conversation header: avatar identity,
 * action row, shared-photo strip (placeholders — no local files in the
 * renderer), contact info, and Find My.
 */
@PreviewTest
@Preview(name = "contact-card", device = Devices.PHONE, showBackground = true)
@Preview(name = "contact-card-dark", device = Devices.PHONE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ContactDetailsCardScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        ContactDetailsCard(
            details = ContactDetails(
                displayName = "Alex Chen",
                avatarPath = null,
                phones = listOf("+1 (555) 010-2030"),
                emails = listOf("alex@icloud.com"),
                handleAddress = "alex@icloud.com",
            ),
            location = ContactLocationUi.NotSharing,
            sharedContent = listOf(
                SharedContentPreview("p1", "trailhead.jpg", attachmentGuid = "g1", isImage = true),
                SharedContentPreview("p2", "summit.png", attachmentGuid = "g2", isImage = true),
                SharedContentPreview("p3", "swim.jpeg", attachmentGuid = "g3", isImage = true),
                SharedContentPreview("l1", "Yosemite - NPS", url = "https://www.nps.gov/yose"),
            ),
            conversationTitle = "iMessage",
            conversationSubtitle = "Last active 4:12 PM",
            smsChat = false,
            onMessage = {},
            onFaceTime = {},
            onOpenAttachment = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

/**
 * Full conversation-details destination for a 1:1 chat. Must stay a
 * full-screen contact page — never an empty "No participants found" card.
 */
@PreviewTest
@Preview(name = "chat-info-direct", device = Devices.PHONE, showBackground = true)
@Preview(name = "chat-info-direct-dark", device = Devices.PHONE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ChatInfoDirectScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        ChatInfoScreen(
            chat = ChatListItem(
                id = 2,
                title = "Alex Chen",
                snippet = null,
                date = FIXED_NOW,
                unread = 0,
                pinned = false,
                avatarColor = 0xFF006C4C,
                avatarAddress = "alex@icloud.com",
                isGroup = false,
            ),
            participants = listOf(ParticipantRow("alex@icloud.com", "Alex Chen")),
            onBack = {},
        )
    }
}
