package app.openbubbles.nativeapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import app.openbubbles.nativeapp.NativeMainActivity
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.NotifPrefs

/**
 * Incoming-message notification pipeline for the native app — the counterpart
 * of the Flutter app's CreateIncomingMessageNotification.kt, minus the Dart
 * round-trips:
 *
 *  - one [NotificationChannel] per chat (lazily created, named after the chat)
 *  - one notification per message, grouped under a per-chat GROUP key, with a
 *    group summary posted once more than one message is pending in a chat
 *  - MessagingStyle with a Person per chat (title + guid as the person key),
 *    conversation history from the store (last 4 messages, current appended),
 *    the chat guid as the conversation id (shortcutId) and person/search key
 *  - unique request codes (chatId-hash + timestamp) so every message's
 *    PendingIntents carry their own extras
 *  - tap deep-links into the chat (`chat_guid` extra), swipe-dismiss marks the
 *    chat read, "Mark As Read" / "Reply" actions mirror the Flutter actions
 *  - sanitized content when previews are hidden ([NotifPrefs.hidePreviews]):
 *    history is dropped entirely so no message body leaks
 *
 * The push service's notifyIncoming() hands off to [postIncoming].
 */
object Notifications {

    // Intent / extras contract shared with ReplyReceiver, MarkReadReceiver and
    // the deep link in NativeMainActivity.
    const val EXTRA_CHAT_GUID = NativeMainActivity.EXTRA_CHAT_GUID
    const val EXTRA_CHAT_ID = "chat_id"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val EXTRA_CANCEL_NOTIFICATIONS = "cancel_notifications"
    const val EXTRA_IS_SUMMARY = "is_summary"

    /**
     * Conversation identity: NotificationCompat has no setConversationId /
     * setSearchTerm, so the chat guid rides the conversation shortcutId
     * (Android's conversation-space identifier), the MessagingStyle Person
     * key, and this extras entry for notification-listener search.
     */
    const val EXTRA_CONVERSATION_ID = "conversation_id"
    const val EXTRA_SEARCH_KEY = "search_key"

    /** RemoteInput key for the inline-reply action (Flutter parity: "text_reply"). */
    const val KEY_TEXT_REPLY = "text_reply"

    private const val CHANNEL_PREFIX = "chat_"
    private const val GROUP_PREFIX = "chat_group_"
    private const val SUMMARY_SUFFIX = ":summary"

    /** Messages of conversation history shown above the new message. */
    private const val HISTORY_DEPTH = 4

    /** Flutter's accent color (0x4A90F6) for parity. */
    private const val ACCENT_COLOR = 4888294
    private val SMALL_ICON = app.openbubbles.nativeapp.R.drawable.ic_stat_message

    /**
     * Posts a notification for one incoming message in [chatId]/[chatGuid].
     * [title] is the chat title, [isGroup] shapes the channel description and
     * the MessagingStyle (group conversation + per-sender history entries).
     */
    fun postIncoming(
        context: Context,
        chatId: Long,
        chatGuid: String,
        title: String,
        text: String,
        isGroup: Boolean,
        senderName: String? = null,
    ) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.areNotificationsEnabled()) return

        val prefs = NotifPrefs(context)
        val hide = prefs.hidePreviews
        val shownTitle = if (hide) "iMessage" else title
        val shownText = if (hide) "New message" else text

        val channelId = ensureChannel(nm, chatGuid, title, isGroup)
        val groupKey = GROUP_PREFIX + chatGuid

        // Unique per message (chatId-hash + timestamp) — reused as the
        // notification id and as the PendingIntent request-code base.
        val requestCode = "$chatId:${System.currentTimeMillis()}".hashCode()
        val notificationId = requestCode

        val contentIntent = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, NativeMainActivity::class.java)
                .putExtra(EXTRA_CHAT_GUID, chatGuid),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Swipe-dismiss: mark the chat read, silently (no cancel — the
        // dismissed notification is already gone).
        val deleteIntent = PendingIntent.getBroadcast(
            context,
            requestCode + 1,
            markReadIntent(context, chatId, cancelNotifications = false),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val markReadIntent = PendingIntent.getBroadcast(
            context,
            requestCode + 2,
            markReadIntent(context, chatId, cancelNotifications = true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val markReadAction = NotificationCompat.Action.Builder(0, "Mark As Read", markReadIntent)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        val extras = Bundle().apply {
            putString(EXTRA_CHAT_GUID, chatGuid)
            putLong(EXTRA_CHAT_ID, chatId)
            putString(EXTRA_CONVERSATION_ID, chatGuid)
            putString(EXTRA_SEARCH_KEY, chatGuid)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(SMALL_ICON)
            .setContentTitle(shownTitle)
            .setContentText(shownText)
            .setStyle(
                messagingStyle(
                    chatGuid = chatGuid,
                    chatTitle = shownTitle,
                    isGroup = isGroup,
                    nowMs = System.currentTimeMillis(),
                    // Store history only when previews are shown; the current
                    // message is always appended as the newest message.
                    history = if (hide) emptyList() else readHistory(chatId, text),
                    currentText = shownText,
                    currentSenderPerson = senderName?.takeIf { isGroup }?.let {
                        Person.Builder().setName(it).build()
                    },
                    currentFromMe = false,
                ),
            )
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(groupKey)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .setColor(ACCENT_COLOR)
            // The chat's conversation identity (Android Conversations API):
            // shortcutId == the conversation id, mirrored in the extras.
            .setShortcutId(chatGuid)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                publicVersion(context, channelId, shownTitle, shownText, contentIntent),
            )
            .setExtras(extras)
            .addAction(markReadAction)

        if (prefs.replyEnabled) {
            // RemoteInput results are attached by the system, so this
            // PendingIntent must be mutable (required on API 31+).
            val replyIntent = PendingIntent.getBroadcast(
                context,
                requestCode + 3,
                Intent(context, ReplyReceiver::class.java)
                    .putExtra(EXTRA_CHAT_ID, chatId)
                    .putExtra(EXTRA_CHAT_GUID, chatGuid)
                    .putExtra(EXTRA_NOTIFICATION_ID, notificationId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            val replyAction = NotificationCompat.Action.Builder(0, "Reply", replyIntent)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .setAllowGeneratedReplies(true)
                .addRemoteInput(
                    RemoteInput.Builder(KEY_TEXT_REPLY).setLabel("Reply").build(),
                )
                .build()
            builder.addAction(replyAction)
        }

        nm.notify(notificationId, builder.build())

        // Per-chat summary once more than one message is pending (counts only
        // message children, never the summary itself).
        val pending = nm.activeNotifications.count {
            it.notification.extras.getString(EXTRA_CHAT_GUID) == chatGuid &&
                !it.notification.extras.getBoolean(EXTRA_IS_SUMMARY)
        }
        if (pending > 1) postSummary(context, nm, chatId, chatGuid, channelId, title, hide, pending)
    }

    /**
     * Re-posts the tapped notification (same id) as the sent-reply
     * confirmation, iMessage-style: shows "You: <reply>" (or a placeholder
     * when previews are hidden) without re-alerting, with the conversation's
     * recent history for context.
     */
    fun postReplySent(
        context: Context,
        notificationId: Int,
        chatId: Long,
        chatGuid: String,
        title: String,
        replyText: String,
    ) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.areNotificationsEnabled()) return

        val prefs = NotifPrefs(context)
        val hide = prefs.hidePreviews
        val shownTitle = if (hide) "iMessage" else title
        val shownText = if (hide) "Reply sent" else "You: $replyText"

        val channelId = ensureChannel(nm, chatGuid, title, isGroup = false)
        val requestCode = if (notificationId > 0) notificationId else {
            "$chatId:${System.currentTimeMillis()}".hashCode()
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, NativeMainActivity::class.java)
                .putExtra(EXTRA_CHAT_GUID, chatGuid),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val deleteIntent = PendingIntent.getBroadcast(
            context,
            requestCode + 1,
            markReadIntent(context, chatId, cancelNotifications = false),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(SMALL_ICON)
            .setContentTitle(shownTitle)
            .setContentText(shownText)
            .setStyle(
                messagingStyle(
                    chatGuid = chatGuid,
                    chatTitle = shownTitle,
                    isGroup = false,
                    nowMs = System.currentTimeMillis(),
                    history = if (hide) emptyList() else readHistory(chatId, replyText),
                    currentText = shownText,
                    currentSenderPerson = null,
                    currentFromMe = true,
                ),
            )
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_PREFIX + chatGuid)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .setColor(ACCENT_COLOR)
            .setShortcutId(chatGuid)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                publicVersion(context, channelId, shownTitle, shownText, contentIntent),
            )
            .setExtras(Bundle().apply {
                putString(EXTRA_CHAT_GUID, chatGuid)
                putLong(EXTRA_CHAT_ID, chatId)
                putString(EXTRA_CONVERSATION_ID, chatGuid)
                putString(EXTRA_SEARCH_KEY, chatGuid)
            })
            .build()
        nm.notify(requestCode, notification)
    }

    /** Cancels every notification posted for [chatId] (children + summary). */
    fun cancelForChat(context: Context, chatId: Long) {
        if (chatId <= 0L) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.activeNotifications
            .filter { it.notification.extras.getLong(EXTRA_CHAT_ID) == chatId }
            .forEach { nm.cancel(it.id) }
    }

    // ------------------------------------------------------------------
    // MessagingStyle assembly
    // ------------------------------------------------------------------

    /** One store-backed history entry (already display-sanitized). */
    private data class HistoryEntry(
        val text: String,
        val timestampMs: Long,
        /** Null = sent by the local user ("you"). */
        val senderPerson: Person?,
    )

    /**
     * Builds the MessagingStyle: a Person per chat (title + guid key), the
     * store's last messages as history, and the current message appended as
     * the newest entry. Conversation-id semantics ride [chatGuid] via the
     * caller's shortcutId/extras and the person key.
     */
    private fun messagingStyle(
        chatGuid: String,
        chatTitle: String,
        isGroup: Boolean,
        nowMs: Long,
        history: List<HistoryEntry>,
        currentText: String,
        currentSenderPerson: Person?,
        currentFromMe: Boolean,
    ): NotificationCompat.MessagingStyle {
        // Person per chat: name = chat title, key = chat guid (search/dedupe).
        val chatPerson = Person.Builder()
            .setName(chatTitle)
            .setKey(chatGuid)
            .setImportant(true)
            .build()
        val style = NotificationCompat.MessagingStyle(chatPerson)
            .setGroupConversation(isGroup)
        if (isGroup) {
            // The conversation title is redundant for 1:1 chats (the person
            // name already renders); groups need it as the thread label.
            style.setConversationTitle(chatTitle)
        }
        history.forEach { entry ->
            style.addMessage(
                NotificationCompat.MessagingStyle.Message(
                    entry.text,
                    entry.timestampMs,
                    entry.senderPerson ?: chatPerson,
                ),
            )
        }
        // Current message: incoming renders as the chat person; own replies
        // ("You: …") render with the local-user sender (null person).
        style.addMessage(
            NotificationCompat.MessagingStyle.Message(
                currentText,
                nowMs,
                if (currentFromMe) null else (currentSenderPerson ?: chatPerson),
            ),
        )
        return style
    }

    /**
     * Last [HISTORY_DEPTH] displayable messages of the chat from the store
     * (read-only), oldest-first, excluding [currentText] when it was just
     * ingested as the newest row. Returns an empty list when the store is
     * unavailable — the notification then shows only the current message.
     */
    private fun readHistory(chatId: Long, currentText: String?): List<HistoryEntry> {
        if (chatId <= 0L) return emptyList()
        val store = CoreGraph.store ?: return emptyList()
        return runCatching {
            val box = store.boxFor(app.openbubbles.db.Message::class.java)
            val rows = box.query()
                .equal(
                    app.openbubbles.db.Message_.chatId,
                    chatId,
                )
                .orderDesc(app.openbubbles.db.Message_.dateCreated)
                .build()
                .use { query -> query.find(0, (HISTORY_DEPTH + 1).toLong()) }
                .asReversed() // oldest -> newest

            // The newest row is usually the message being notified (it is
            // ingested before posting); drop it so the current message is not
            // duplicated by the appended one.
            withoutCurrentNotificationRow(rows, currentText) { it.text }
                .mapNotNull { row -> historyEntry(row) }
                .takeLast(HISTORY_DEPTH)
        }.getOrDefault(emptyList())
    }

    private fun historyEntry(row: app.openbubbles.db.Message): HistoryEntry? {
        // Skip group events (name/photo changes etc.) and empty rows.
        if (row.itemType != null && row.itemType != 0L) return null
        val text = row.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (row.hasAttachments) "[Attachment]" else return null
        val timestamp = row.dateCreated?.time ?: return null
        val sender = if (row.isFromMe) {
            null // local user
        } else {
            val address = runCatching {
                row.handleRelation.target?.formattedAddress
                    ?: row.handleRelation.target?.address
            }.getOrNull()
            Person.Builder()
                .setName(address ?: "Sender")
                .build()
        }
        return HistoryEntry(text = text, timestampMs = timestamp, senderPerson = sender)
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun markReadIntent(context: Context, chatId: Long, cancelNotifications: Boolean) =
        Intent(context, MarkReadReceiver::class.java)
            .putExtra(EXTRA_CHAT_ID, chatId)
            .putExtra(EXTRA_CANCEL_NOTIFICATIONS, cancelNotifications)

    /**
     * Lockscreen-safe mirror of the notification: already-sanitized content
     * when previews are hidden, the real content otherwise (the app-level
     * setting is then the single source of truth).
     */
    private fun publicVersion(
        context: Context,
        channelId: String,
        title: String,
        text: String,
        contentIntent: PendingIntent,
    ): Notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(SMALL_ICON)
        .setContentTitle(title)
        .setContentText(text)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setAutoCancel(true)
        .setContentIntent(contentIntent)
        .build()

    private fun postSummary(
        context: Context,
        nm: NotificationManager,
        chatId: Long,
        chatGuid: String,
        channelId: String,
        title: String,
        hidePreviews: Boolean,
        count: Int,
    ) {
        val summaryId = "$chatId$SUMMARY_SUFFIX".hashCode()
        // The summary opens the app root (chat list), like the Flutter app.
        val contentIntent = PendingIntent.getActivity(
            context,
            summaryId,
            Intent(context, NativeMainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val summary = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(SMALL_ICON)
            .setContentTitle(if (hidePreviews) "iMessage" else title)
            .setContentText("$count new messages")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup(GROUP_PREFIX + chatGuid)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setColor(ACCENT_COLOR)
            .setExtras(Bundle().apply {
                putString(EXTRA_CHAT_GUID, chatGuid)
                putLong(EXTRA_CHAT_ID, chatId)
                putBoolean(EXTRA_IS_SUMMARY, true)
            })
            .build()
        nm.notify(summaryId, summary)
    }

    /**
     * Lazily creates (and keeps renamed) the chat's channel: IMPORTANCE_HIGH
     * with the default notification sound and vibration, mirroring the Flutter
     * app's per-chat channels on Android R+ (`com.bluebubbles.new_messages.<guid>`).
     */
    private fun ensureChannel(
        nm: NotificationManager,
        chatGuid: String,
        title: String,
        isGroup: Boolean,
    ): String {
        val id = CHANNEL_PREFIX + chatGuid
        val name = title.ifBlank { "Messages" }
        val channel = NotificationChannel(
            id,
            name,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = if (isGroup) "Group conversation" else "Direct message"
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        // Idempotent: creates or updates the user-visible name if the chat
        // was renamed (importance/sound of an existing channel are immutable).
        nm.createNotificationChannel(channel)
        return id
    }
}
