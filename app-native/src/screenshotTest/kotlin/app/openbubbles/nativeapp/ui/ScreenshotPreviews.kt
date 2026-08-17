package app.openbubbles.nativeapp.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.data.OutgoingAttachment
import app.openbubbles.nativeapp.data.RichLinkPreview
import app.openbubbles.nativeapp.data.SharedContentPreview
import app.openbubbles.nativeapp.ui.chat.ChatScreen
import app.openbubbles.nativeapp.ui.chat.ChatUiState
import app.openbubbles.nativeapp.ui.chat.ReplyTarget
import app.openbubbles.nativeapp.ui.chat.ReplyThreadState
import app.openbubbles.nativeapp.ui.chatinfo.ChatInfoScreen
import app.openbubbles.nativeapp.ui.chatinfo.ContactDetails
import app.openbubbles.nativeapp.ui.chatinfo.ContactDetailsCard
import app.openbubbles.nativeapp.ui.chatinfo.ContactLocationUi
import app.openbubbles.nativeapp.ui.chatinfo.ParticipantRow
import app.openbubbles.nativeapp.ui.chatlist.ChatListKind
import app.openbubbles.nativeapp.ui.chatlist.ChatListRow
import app.openbubbles.nativeapp.ui.chatlist.ChatListScreen
import app.openbubbles.nativeapp.ui.chatlist.ChatListUiState
import app.openbubbles.nativeapp.ui.chatlist.SendFromDialog
import app.openbubbles.nativeapp.ui.onboarding.OnboardingScreen
import app.openbubbles.nativeapp.ui.search.SearchMessageRow
import app.openbubbles.nativeapp.ui.search.SearchScreen
import app.openbubbles.nativeapp.ui.search.SearchUiState
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
            onChatClick = {},
        )
    }
}

/**
 * Per-chat send-from override picker (chat list long-press → Send from…).
 * The received-on address is annotated so the user can deliberately reply
 * from the address a thread actually arrived at.
 */
@PreviewTest
@Preview(name = "send-from", device = Devices.PHONE, showBackground = true)
@Preview(name = "send-from-dark", device = Devices.PHONE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SendFromDialogScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        SendFromDialog(
            chat = ChatListItem(
                id = 1,
                title = "Weekend Crew",
                snippet = "Meet at the trailhead",
                date = FIXED_NOW - 31 * 60_000L,
                unread = 0,
                pinned = false,
                avatarColor = 0xFF386A20,
                isGroup = true,
                senderOverride = null,
                receivedOnHandle = "mailto:me@icloud.com",
            ),
            choices = listOf("tel:+15550102030", "mailto:me@icloud.com"),
            defaultHandle = "tel:+15550102030",
            onPick = {},
            onDismiss = {},
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

/** Dedicated search: sectioned chats/people/messages/links with match highlighting. */
@PreviewTest
@Preview(name = "search", device = Devices.PHONE, showBackground = true)
@Preview(name = "search-dark", device = Devices.PHONE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SearchScreenScreenshot() {
    val chat = ChatListItem(
        id = 2,
        title = "Alex Chen",
        snippet = "grabbing coffee now, want anything?",
        date = FIXED_NOW - 52 * 60_000L,
        unread = 0,
        pinned = false,
        avatarColor = 0xFF006C4C,
    )
    OpenBubblesTheme(dynamicColor = false) {
        SearchScreen(
            uiState = SearchUiState(
                query = "coffee",
                chats = listOf(chat),
                people = listOf(
                    app.openbubbles.core.contacts.RawContact(
                        id = "p1",
                        displayName = "Courtney Coffeeson",
                        firstName = "Courtney",
                        lastName = "Coffeeson",
                        avatarPath = null,
                        addresses = listOf("courtney@icloud.com"),
                    ),
                ),
                messages = listOf(
                    SearchMessageRow(
                        guid = "m1",
                        chatId = 2,
                        chat = chat,
                        text = "coffee sounds perfect — see you at the trailhead",
                        dateMillis = FIXED_NOW - 55 * 60_000L,
                    ),
                ),
                links = listOf(
                    SearchMessageRow(
                        guid = "m2",
                        chatId = 2,
                        chat = chat,
                        text = "https://www.nps.gov/yose/index.htm",
                        dateMillis = FIXED_NOW - 60 * 60_000L,
                        link = RichLinkPreview(
                            url = "https://www.nps.gov/yose/index.htm",
                            displayHost = "nps.gov",
                            title = "Coffee Country: Yosemite National Park",
                            summary = null,
                            imageBytes = null,
                            imageMime = null,
                            iconBytes = null,
                            iconMime = null,
                        ),
                    ),
                ),
            ),
            onQueryChange = {},
            onOpenChat = {},
            onOpenContact = {},
            onBack = {},
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

/**
 * Group transcript: sender avatars anchored to the bottom bubble of each
 * incoming run, name labels on run starts, and a mid-conversation timestamp
 * separator where a three-hour quiet gap splits the clusters.
 */
@PreviewTest
@Preview(name = "chat-group", device = Devices.PHONE, showBackground = true)
@Preview(name = "chat-group-dark", device = Devices.PHONE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ChatScreenGroupScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        ChatScreen(
            uiState = ChatUiState(
                chat = ChatListItem(
                    id = 7,
                    title = "Weekend Crew",
                    snippet = null,
                    date = FIXED_NOW,
                    unread = 0,
                    pinned = false,
                    avatarColor = 0xFF386A20,
                    isSms = false,
                    isGroup = true,
                ),
                messages = listOf(
                    message(1, "who's driving saturday?", fromMe = false)
                        .copy(date = FIXED_NOW - 3 * 60 * 60_000L, senderAddress = "alex@icloud.com"),
                    message(2, "i can take three", fromMe = false)
                        .copy(senderAddress = "sam@icloud.com"),
                    message(3, "i'll grab the snacks too", fromMe = false)
                        .copy(senderAddress = "sam@icloud.com"),
                    message(4, "perfect — see everyone at 8", fromMe = true, status = MessageStatus.DELIVERED),
                ),
            ),
            onInputChange = {},
            onSend = {},
            onLoadOlder = {},
            onBack = {},
        )
    }
}

/**
 * Draft attachment strip: picked media stages on the composer instead of
 * sending instantly, each thumbnail removable, all riding the next send.
 */
@PreviewTest
@Preview(name = "chat-attachments", device = Devices.PHONE, showBackground = true)
@Preview(name = "chat-attachments-dark", device = Devices.PHONE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ChatScreenAttachmentsScreenshot() {
    val staged = remember {
        // Real PNGs decoded by the thumbnail strip (hand-encoded so the
        // preview does not depend on android.graphics, which the JVM
        // screenshot environment cannot encode).
        listOf(
            "trailhead.jpg" to STAGED_PHOTO_ONE_PNG,
            "summit.jpg" to STAGED_PHOTO_TWO_PNG,
        ).map { (name, png) ->
            // createTempFile: the JVM screenshot engine resolves cacheDir to
            // the module directory, which must not collect preview artifacts.
            val file = java.io.File.createTempFile("staged-$name", ".png")
            file.writeBytes(java.util.Base64.getDecoder().decode(png))
            // Note: thumbnails render their placeholder tiles here — the
            // screenshot engine stubs android.graphics, so the photo branch
            // (the same Image(bitmap) the sticker sheet uses) is not
            // pixel-verifiable on JVM.
            OutgoingAttachment(
                file = file,
                mime = "image/jpeg",
                uti = "public.jpeg",
                name = name,
                sizeBytes = file.length(),
            )
        }
    }
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
                    message(1, "send me the trail photos when you can!", fromMe = false),
                    message(2, "on it — picking the keepers now", fromMe = true, status = MessageStatus.DELIVERED),
                ),
                input = "here are the best two",
                pendingAttachments = staged,
            ),
            onInputChange = {},
            onSend = {},
            onLoadOlder = {},
            onBack = {},
        )
    }
}

/** 120x120 green gradient PNG (base64), rendered by the first draft thumb. */
private const val STAGED_PHOTO_ONE_PNG =
    "iVBORw0KGgoAAAANSUhEUgAAAHgAAAB4CAYAAAA5ZDbSAAACd0lEQVR42u3RVVYQABSE4VkOCIigICEgCJsQWzGwFQNbUQSxu7u7u7u7Xcz1rmPO//CdM8/zq2DV8IAvcYJ74K4csKWC1a0BXypc0xrwlYFHBHypsDsHbKlwbQ7YUr+ekQFfBLYP3Dsq4Ev91uWALRX1jQ74IrB94PU5YEtFG8YEfKl445iArww8NuBLxZtywJaKN+eALZVsGRfwRWD7wFvHB3ypZFsO2FL/7RMCvghsH3hHDthS/51tAV8q3dUW8JWBJwZ8qXR3DthS6Z4csKUBeycFfBHYPvC+yQFfGrA/B2yp7MCUgC8C2wc+mAO2VHaoPeBL5YfbA74y8NSAL5UfyQFbKj+aA7Y08Ni0gC8C2wc+Pj3gSwNP5IAtDTo5I+CLwPaBT+WALQ06PTPgSxVnZgZ8ZeBZAV+qOJsDtlRxLgdsqfL87IAvAtsHvjAn4EuVF3PAlgZfmhvwRWD7wJdzwJYGX+kI+FLV1Y6Arww8L+BLVddywJaqrueALVXfmB/wRWD7wDcXBHyp+lYO2FLN7YUBXwS2D3wnB2yp5m5nwJdq73UGfGXgRQFfqr2fA7ZU+yAHbGnIw8UBXwS2D/xoScCXhjzOAVuqe7I04IvA9oGf5oAt1T1bFvCl+ufLAr4y8PKAL9W/yAFbqn+ZA7bU8GpFwBeB7QO/XhnwpYY3OWBLQ9+uCvgisH3gdzlgS0PfdwV8qfFDV8BXBl4d8KXGjzlgS42fcsCWmj6vCfgisH3gL90BX2r6mgO2NOzb2oAvAtsH/p4DtjTsR0/Al5p/9gR8ZeDegC81/8oBW2r+nQO21PJnXcAXge0D/+0L+FLLvxyw9R/yzXHefokhJAAAAABJRU5ErkJggg=="

/** 120x120 orange gradient PNG (base64), rendered by the second draft thumb. */
private const val STAGED_PHOTO_TWO_PNG =
    "iVBORw0KGgoAAAANSUhEUgAAAHgAAAB4CAYAAAA5ZDbSAAACLUlEQVR42u3RaTpQYRgAUAsTEREREdlJFEVRFEVRFO2kQZMmTZo0adKkSZN+vT33u3cVPefHWcEpObOjNDJnM52lca5zXXK+KzfbVRazO8viQuHirvLkUqa7PC53r0+u9OTmeipibndFXC1c21OZXM/0VsaN3g3Jzb7cfF9VzO+tiluF2/uqkzuZ/uq4278xuTeQWxioiYX9NXG/8OBAbfIwM1gbjwY3JY+HcotDdbF4sC6eFJ4eqk+eZYbr4/nw5uTFSG5ppCGWDjfEy8KrI43J68xoY7wZ3ZK8HcstjzXF8tGmeFd4f6w5+ZAZb46P41uTTxO5lYmWWDneEp8LX060Jl8zk63xbXJb8n0qtzrVFqsn2+JH4eep9uRXZro9fk9vT/7M5NZmOmLtdEf8LZQIFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsGDBggULFixYsOD/OfgfobRQ+nhKO+AAAAAASUVORK5CYII="

/** Inline reply quotes and the focused thread view. */
@PreviewTest
@Preview(name = "chat-reply", device = Devices.PHONE, showBackground = true)
@Preview(name = "chat-reply-dark", device = Devices.PHONE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ChatScreenReplyScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        val original = message(
            1,
            "For the contact sheet I gave it a screenshot and said when I click contact sheet it opens to this",
            fromMe = true,
            status = MessageStatus.DELIVERED,
        ).copy(guid = "root")
        val reply = message(
            2,
            "Btw is there a way to auto download photos? Or is that a setting I totally missed?",
            fromMe = false,
        ).copy(
            guid = "child",
            replyToGuid = "root",
            replyPreviewText = original.text,
            senderAddress = "mark@icloud.com",
        )
        ChatScreen(
            uiState = ChatUiState(
                chat = ChatListItem(
                    id = 2,
                    title = "Mark",
                    snippet = null,
                    date = FIXED_NOW,
                    unread = 0,
                    pinned = false,
                    avatarColor = 0xFF006C4C,
                    isSms = false,
                ),
                messages = listOf(original, reply),
            ),
            onInputChange = {},
            onSend = {},
            onLoadOlder = {},
            onBack = {},
        )
    }
}

@PreviewTest
@Preview(name = "chat-thread", device = Devices.PHONE, showBackground = true)
@Preview(name = "chat-thread-dark", device = Devices.PHONE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun ChatScreenThreadScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        val original = message(
            1,
            "For the contact sheet I gave it a screenshot and said when I click contact sheet it opens to this",
            fromMe = true,
            status = MessageStatus.DELIVERED,
        ).copy(guid = "root")
        val reply = message(
            2,
            "Btw is there a way to auto download photos? Or is that a setting I totally missed?",
            fromMe = false,
        ).copy(guid = "child", replyToGuid = "root", senderAddress = "mark@icloud.com")
        val follow = message(
            3,
            "I'll add one, the models like to be considerate of storage and data by default",
            fromMe = true,
            status = MessageStatus.DELIVERED,
        ).copy(guid = "follow", replyToGuid = "root")
        ChatScreen(
            uiState = ChatUiState(
                chat = ChatListItem(
                    id = 2,
                    title = "Mark",
                    snippet = null,
                    date = FIXED_NOW,
                    unread = 0,
                    pinned = false,
                    avatarColor = 0xFF006C4C,
                    isSms = false,
                ),
                messages = listOf(original, reply, follow),
                input = "I clicked on",
                replyingTo = ReplyTarget(
                    message = original,
                    rootGuid = "root",
                    part = 0L,
                    partLocator = "0:0:20",
                ),
                replyThread = ReplyThreadState(
                    rootGuid = "root",
                    part = 0L,
                    messages = listOf(original, reply, follow),
                    loading = false,
                    sourceMessage = reply,
                ),
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
