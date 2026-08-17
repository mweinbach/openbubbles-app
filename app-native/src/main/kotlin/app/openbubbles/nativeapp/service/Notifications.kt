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
import app.openbubbles.nativeapp.data.resolveNotificationSenderLabel
import uniffi.rust_lib_bluebubbles.UMessage
import uniffi.rust_lib_bluebubbles.UMessageInst

/**
 * Incoming-message notification pipeline for the native app — the counterpart
 * of the Flutter app's CreateIncomingMessageNotification.kt, minus the Dart
 * round-trips:
 *
 *  - one [NotificationChannel] per chat (lazily created, named after the chat)
 *  - one stable notification per chat; each new message updates its
 *    MessagingStyle history instead of adding another notification card
 *  - MessagingStyle with named senders: direct chats reuse the conversation
 *    title for every remote history row; groups resolve each sender through
 *    contacts. Last 4 store messages plus the current message, chat guid as
 *    the conversation id (shortcutId) and person/search key
 *  - stable per-chat request codes whose PendingIntent extras update to the
 *    newest message
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
    const val EXTRA_MESSAGE_GUID = "message_guid"
    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val EXTRA_CANCEL_NOTIFICATIONS = "cancel_notifications"

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
        messageGuid: String? = null,
    ) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.areNotificationsEnabled()) return
        val notificationId = conversationNotificationId(chatId)
        val activeNotifications = nm.activeNotifications
        val duplicate = messageGuid != null && activeNotifications.any {
            it.id == notificationId &&
                it.notification.extras.getString(EXTRA_MESSAGE_GUID) == messageGuid
        }
        activeNotifications
            .filter {
                it.id != notificationId &&
                    it.notification.extras.getLong(EXTRA_CHAT_ID) == chatId
            }
            .forEach { nm.cancel(it.id) }
        if (duplicate) return

        val prefs = NotifPrefs(context)
        val hide = prefs.hidePreviews
        val shownTitle = if (hide) "iMessage" else title
        val shownText = if (hide) "New message" else text

        val channelId = ensureChannel(nm, chatGuid, title, isGroup)
        val requestCode = notificationId

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
            markReadIntent(context, chatId, messageGuid, cancelNotifications = false),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val markReadIntent = PendingIntent.getBroadcast(
            context,
            requestCode + 2,
            markReadIntent(context, chatId, messageGuid, cancelNotifications = true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val markReadAction = NotificationCompat.Action.Builder(0, "Mark As Read", markReadIntent)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        val extras = Bundle().apply {
            putString(EXTRA_CHAT_GUID, chatGuid)
            putLong(EXTRA_CHAT_ID, chatId)
            messageGuid?.let { putString(EXTRA_MESSAGE_GUID, it) }
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
                    history = if (hide) {
                        emptyList()
                    } else {
                        readHistory(
                            chatId = chatId,
                            currentText = text,
                            currentMessageGuid = messageGuid,
                            isGroup = isGroup,
                            conversationTitle = title,
                            conversationKey = chatGuid,
                        )
                    },
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
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .setColor(ACCENT_COLOR)
            // The chat's conversation identity (Android Conversations API):
            // shortcutId == the conversation id, mirrored in the extras.
            .setShortcutId(chatGuid)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                publicVersion(context, channelId, contentIntent),
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
                    .putExtra(EXTRA_MESSAGE_GUID, messageGuid)
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
    }

    internal fun previewForIncoming(inst: UMessageInst): String? {
        val reactionTarget = (inst.message as? UMessage.React)
            ?.let { storedMessagePreview(it.toUuid) }
        return notificationPreview(inst, reactionTarget)
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
        isGroup: Boolean = false,
    ) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.areNotificationsEnabled()) return

        val prefs = NotifPrefs(context)
        val hide = prefs.hidePreviews
        val shownTitle = if (hide) "iMessage" else title
        val shownText = if (hide) "Reply sent" else "You: $replyText"

        val channelId = ensureChannel(nm, chatGuid, title, isGroup)
        val requestCode = if (notificationId != -1) {
            notificationId
        } else {
            conversationNotificationId(chatId)
        }
        cancelOtherChatNotifications(nm, chatId, requestCode)
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
            markReadIntent(context, chatId, messageGuid = null, cancelNotifications = false),
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
                    isGroup = isGroup,
                    nowMs = System.currentTimeMillis(),
                    history = if (hide) {
                        emptyList()
                    } else {
                        readHistory(
                            chatId = chatId,
                            currentText = replyText,
                            isGroup = isGroup,
                            conversationTitle = title,
                            conversationKey = chatGuid,
                        )
                    },
                    currentText = shownText,
                    currentSenderPerson = null,
                    currentFromMe = true,
                ),
            )
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setDeleteIntent(deleteIntent)
            .setColor(ACCENT_COLOR)
            .setShortcutId(chatGuid)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                publicVersion(context, channelId, contentIntent),
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

    /** Replaces the action notification with an explicit, tap-to-recover failure. */
    fun postReplyFailed(
        context: Context,
        notificationId: Int,
        chatId: Long,
        chatGuid: String?,
        title: String,
    ) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (!nm.areNotificationsEnabled()) return
        val conversationId = chatGuid ?: "chat-$chatId"
        val channelId = ensureChannel(nm, conversationId, title, isGroup = false)
        val requestCode = if (notificationId != -1) {
            notificationId
        } else {
            conversationNotificationId(chatId)
        }
        cancelOtherChatNotifications(nm, chatId, requestCode)
        val openIntent = Intent(context, NativeMainActivity::class.java).apply {
            chatGuid?.let { putExtra(EXTRA_CHAT_GUID, it) }
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            requestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(SMALL_ICON)
            .setContentTitle("Reply not sent")
            .setContentText("Tap to open OpenBubbles and try again")
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setColor(ACCENT_COLOR)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(context, channelId, contentIntent))
            .setExtras(Bundle().apply {
                chatGuid?.let { putString(EXTRA_CHAT_GUID, it) }
                putLong(EXTRA_CHAT_ID, chatId)
                putString(EXTRA_CONVERSATION_ID, conversationId)
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

    /** Removes redundant notifications left by older per-message notification builds. */
    fun collapseActiveConversationNotifications(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val entries = nm.activeNotifications.map { active ->
            ConversationNotificationEntry(
                id = active.id,
                chatId = active.notification.extras.getLong(EXTRA_CHAT_ID),
                postedAtMs = active.postTime,
                isSummary = active.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            )
        }
        redundantConversationNotificationIds(entries).forEach(nm::cancel)
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
        val localPerson = Person.Builder()
            .setName("You")
            .setKey("openbubbles-local-user")
            .build()
        // Remote person per chat: name = chat title, key = chat guid.
        val chatPerson = Person.Builder()
            .setName(chatTitle)
            .setKey(chatGuid)
            .setImportant(true)
            .build()
        val style = NotificationCompat.MessagingStyle(localPerson)
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
                    entry.senderPerson,
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
    private fun readHistory(
        chatId: Long,
        currentText: String?,
        currentMessageGuid: String? = null,
        isGroup: Boolean,
        conversationTitle: String,
        conversationKey: String,
    ): List<HistoryEntry> {
        if (chatId <= 0L) return emptyList()
        val store = CoreGraph.store ?: return emptyList()
        return runCatching {
            val box = store.boxFor(app.openbubbles.db.Message::class.java)
            val rows = box.query()
                .equal(
                    app.openbubbles.db.Message_.chatId,
                    chatId,
                )
                .isNull(app.openbubbles.db.Message_.dateDeleted)
                .orderDesc(app.openbubbles.db.Message_.dateCreated)
                .build()
                .use { query -> query.find(0, (HISTORY_DEPTH + 1).toLong()) }
                .asReversed() // oldest -> newest

            // The newest row is usually the message being notified (it is
            // ingested before posting); drop it so the current message is not
            // duplicated by the appended one.
            val priorRows = if (
                currentMessageGuid != null && rows.lastOrNull()?.guid == currentMessageGuid
            ) {
                rows.dropLast(1)
            } else {
                withoutCurrentNotificationRow(rows, currentText) { it.text }
            }
            val contactNames = HashMap<String, String?>()
            fun contactNameFor(address: String): String? {
                if (contactNames.containsKey(address)) return contactNames[address]
                val name = CoreGraph.contactDisplayInfo(address)?.first?.takeIf { it.isNotBlank() }
                contactNames[address] = name
                return name
            }
            priorRows
                .mapNotNull { row ->
                    historyEntry(
                        row = row,
                        isGroup = isGroup,
                        conversationTitle = conversationTitle,
                        conversationKey = conversationKey,
                        contactNameFor = ::contactNameFor,
                    )
                }
                .takeLast(HISTORY_DEPTH)
        }.getOrDefault(emptyList())
    }

    private fun historyEntry(
        row: app.openbubbles.db.Message,
        isGroup: Boolean,
        conversationTitle: String,
        conversationKey: String,
        contactNameFor: (String) -> String?,
    ): HistoryEntry? {
        // Skip group events (name/photo changes etc.) and empty rows.
        if (row.itemType != null && row.itemType != 0L) return null
        val text = if (
            row.associatedMessageGuid != null && row.associatedMessageType != null
        ) {
            reactionNotificationText(
                rawType = row.associatedMessageType,
                emoji = row.associatedMessageEmoji,
                targetText = storedMessagePreview(row.associatedMessageGuid),
            )
        } else {
            storedMessagePreview(row)
        } ?: return null
        val timestamp = row.dateCreated?.time ?: return null
        val sender = if (row.isFromMe) {
            null // local user
        } else {
            val handle = runCatching { row.handleRelation.target }.getOrNull()
            val address = handle?.address
            val name = resolveNotificationSenderLabel(
                address = address,
                formattedAddress = handle?.formattedAddress,
                isGroup = isGroup,
                conversationTitle = conversationTitle,
                contactNameFor = contactNameFor,
            )
            val person = Person.Builder().setName(name)
            if (isGroup) {
                address?.takeIf { it.isNotBlank() }?.let(person::setKey)
            } else {
                person.setKey(conversationKey)
            }
            person.build()
        }
        return HistoryEntry(text = text, timestampMs = timestamp, senderPerson = sender)
    }

    private fun storedMessagePreview(guid: String): String? {
        val store = CoreGraph.store ?: return null
        val row = runCatching {
            store.boxFor(app.openbubbles.db.Message::class.java)
                .query()
                .equal(
                    app.openbubbles.db.Message_.guid,
                    guid,
                    io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE,
                )
                .build().use { it.findFirst() }
        }.getOrNull() ?: return null
        return storedMessagePreview(row)
    }

    private fun storedMessagePreview(row: app.openbubbles.db.Message): String? {
        row.text?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        row.subject?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val attachments = runCatching { row.dbAttachments.toList() }.getOrDefault(emptyList())
        if (attachments.size == 1) {
            return attachments.single().transferName?.trim()?.takeIf { it.isNotEmpty() }
                ?: "an attachment"
        }
        if (attachments.size > 1) return "${attachments.size} attachments"
        return if (row.hasAttachments) "an attachment" else null
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun markReadIntent(
        context: Context,
        chatId: Long,
        messageGuid: String?,
        cancelNotifications: Boolean,
    ) =
        Intent(context, MarkReadReceiver::class.java)
            .putExtra(EXTRA_CHAT_ID, chatId)
            .putExtra(EXTRA_MESSAGE_GUID, messageGuid)
            .putExtra(EXTRA_CANCEL_NOTIFICATIONS, cancelNotifications)

    /**
     * Lockscreen-safe mirror of the notification: already-sanitized content
     * when previews are hidden, the real content otherwise (the app-level
     * setting is then the single source of truth).
     */
    private fun publicVersion(
        context: Context,
        channelId: String,
        contentIntent: PendingIntent,
    ): Notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(SMALL_ICON)
        .setContentTitle("OpenBubbles")
        .setContentText("New message")
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setAutoCancel(true)
        .setContentIntent(contentIntent)
        .build()

    private fun cancelOtherChatNotifications(
        nm: NotificationManager,
        chatId: Long,
        keepNotificationId: Int,
    ) {
        nm.activeNotifications
            .filter {
                it.id != keepNotificationId &&
                    it.notification.extras.getLong(EXTRA_CHAT_ID) == chatId
            }
            .forEach { nm.cancel(it.id) }
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

internal fun conversationNotificationId(chatId: Long): Int =
    "openbubbles-chat:$chatId".hashCode()

internal data class ConversationNotificationEntry(
    val id: Int,
    val chatId: Long,
    val postedAtMs: Long,
    val isSummary: Boolean,
)

internal fun redundantConversationNotificationIds(
    entries: List<ConversationNotificationEntry>,
): List<Int> = entries
    .filter { it.chatId > 0L }
    .groupBy { it.chatId }
    .flatMap { (chatId, chatEntries) ->
        if (chatEntries.size <= 1) return@flatMap emptyList()
        val keep = chatEntries.firstOrNull { it.id == conversationNotificationId(chatId) }
            ?: chatEntries.filterNot { it.isSummary }.maxByOrNull { it.postedAtMs }
            ?: chatEntries.maxBy { it.postedAtMs }
        chatEntries.filter { it.id != keep.id }.map { it.id }
    }
