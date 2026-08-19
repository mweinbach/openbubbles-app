package app.openbubbles.nativeapp.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.ManageHistory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openbubbles.nativeapp.data.AutoDownloadLimit
import app.openbubbles.nativeapp.data.MessagingPrefs
import app.openbubbles.nativeapp.data.NotifPrefs
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.facetime.fullScreenCallSettingsIntent
import app.openbubbles.nativeapp.facetime.shouldOfferFullScreenCallSettings
import app.openbubbles.nativeapp.sms.SmsRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SHARED_FOCUS_GUID = "0f58d6c8-0d40-4b40-9d48-e4ac18e38155"

/** Power, Notifications, and Messaging groups plus their pickers. */
@Composable
internal fun rememberMessagingSection(
    archivedCount: Int,
    recentlyDeletedCount: Int,
    onOpenArchived: () -> Unit,
    onOpenRecentlyDeleted: () -> Unit,
): SettingsSectionSlice {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pushState by PushStateHolder.stateFlow.collectAsStateWithLifecycle()
    val registeredHandles by PushStateHolder.myHandlesFlow.collectAsStateWithLifecycle()
    val messagingPrefs = remember(context) { MessagingPrefs(context) }
    var defaultSendingHandle by remember {
        mutableStateOf(messagingPrefs.defaultSendingHandle)
    }
    var showDefaultSendingHandleDialog by remember { mutableStateOf(false) }
    var autoDownloadLimit by remember {
        mutableStateOf(AutoDownloadLimit.fromPersistedValue(messagingPrefs.autoDownloadMaxBytes))
    }
    var showAutoDownloadDialog by remember { mutableStateOf(false) }
    val availableSendingHandles = remember(registeredHandles) {
        registeredHandles.sortedWith(
            compareBy<String>(
                { if (it.startsWith("tel:")) 0 else 1 },
                { sendingHandleLabel(it).lowercase() },
            ),
        )
    }

    var isDefaultSmsApp by remember { mutableStateOf(SmsRole.isHeld(context)) }
    var offerFullScreenCalls by remember {
        mutableStateOf(shouldOfferFullScreenCallSettings(context))
    }
    val smsRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        isDefaultSmsApp = SmsRole.isHeld(context)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultSmsApp = SmsRole.isHeld(context)
                offerFullScreenCalls = shouldOfferFullScreenCallSettings(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return SettingsSectionSlice(
        groups = { filter, showTitles ->
            if (filter == null || filter == SettingsSection.Power) SettingsGroup(
                title = if (showTitles) "Power" else null,
            ) {
                val ctx = context
                var batterySaver by remember {
                    androidx.compose.runtime.mutableStateOf(
                        app.openbubbles.nativeapp.service.BatterySaver.isEnabled(ctx))
                }
                SettingsToggleItem(
                    title = "Battery saver",
                    // Subtitle must not change with state — the switch carries
                    // that. State-dependent copy reflows the row on toggle.
                    supporting = "Check iCloud every 15 min instead of a live connection — messages may be delayed",
                    checked = batterySaver,
                    onCheckedChange = { enabled ->
                        // Flip the switch now; stopService + WorkManager +
                        // startForegroundService are binder work that stalls
                        // the tap frame if run inline.
                        batterySaver = enabled
                        scope.launch(Dispatchers.IO) {
                            app.openbubbles.nativeapp.service.BatterySaver.setEnabled(ctx, enabled)
                        }
                    },
                    index = 0,
                    count = 1,
                    icon = Icons.Filled.BatterySaver,
                )
            }

            if (filter == null || filter == SettingsSection.Notifications) SettingsGroup(
                title = if (showTitles) "Notifications" else null,
            ) {
                val notifPrefs = remember { NotifPrefs(context) }
                var hidePreviews by remember { mutableStateOf(notifPrefs.hidePreviews) }
                var replyEnabled by remember { mutableStateOf(notifPrefs.replyEnabled) }
                var notifyReactions by remember { mutableStateOf(notifPrefs.notifyReactions) }
                var quickTapback by remember { mutableStateOf(notifPrefs.quickTapbackEnabled) }
                val notifCount = if (offerFullScreenCalls) 5 else 4
                SettingsToggleItem(
                    title = "Hide message previews",
                    supporting = "Show \"iMessage\" instead of message content on notifications",
                    checked = hidePreviews,
                    onCheckedChange = { enabled ->
                        hidePreviews = enabled
                        notifPrefs.hidePreviews = enabled
                    },
                    index = 0,
                    count = notifCount,
                    icon = Icons.Filled.VisibilityOff,
                )
                SettingsToggleItem(
                    title = "Quick reply",
                    supporting = "Show the Reply action on message notifications",
                    checked = replyEnabled,
                    onCheckedChange = { enabled ->
                        replyEnabled = enabled
                        notifPrefs.replyEnabled = enabled
                    },
                    index = 1,
                    count = notifCount,
                    icon = Icons.AutoMirrored.Filled.Reply,
                )
                SettingsToggleItem(
                    title = "Reaction notifications",
                    supporting = "Notify when someone reacts to a message",
                    checked = notifyReactions,
                    onCheckedChange = { enabled ->
                        notifyReactions = enabled
                        notifPrefs.notifyReactions = enabled
                    },
                    index = 2,
                    count = notifCount,
                    icon = Icons.Filled.EmojiEmotions,
                )
                SettingsToggleItem(
                    title = "Quick tapback",
                    supporting = "React with a heart from the notification",
                    checked = quickTapback,
                    onCheckedChange = { enabled ->
                        quickTapback = enabled
                        notifPrefs.quickTapbackEnabled = enabled
                    },
                    index = 3,
                    count = notifCount,
                    icon = Icons.Filled.Favorite,
                )
                if (offerFullScreenCalls) {
                    SettingsActionItem(
                        title = "Full-screen FaceTime alerts",
                        supporting = "Android is blocking incoming calls from taking over the lock screen. Tap to allow them.",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    fullScreenCallSettingsIntent(context.packageName),
                                )
                            }
                        },
                        index = 4,
                        count = notifCount,
                        multiline = true,
                        icon = Icons.Filled.Videocam,
                        iconTone = SettingsRowTone.Error,
                    )
                }
            }

            if (filter == null || filter == SettingsSection.Messaging) {
                SettingsGroup(
                    title = if (showTitles) "Messaging" else null,
                ) {
                    var sendReadReceipts by remember {
                        mutableStateOf(messagingPrefs.sendReadReceipts)
                    }
                    var sendTypingIndicators by remember {
                        mutableStateOf(messagingPrefs.sendTypingIndicators)
                    }
                    var showDeliveryTimestamps by remember {
                        mutableStateOf(messagingPrefs.showDeliveryTimestamps)
                    }
                    var shareFocusStatus by remember {
                        mutableStateOf(messagingPrefs.shareFocusStatus)
                    }
                    var sendSubjectLines by remember {
                        mutableStateOf(messagingPrefs.sendSubjectLines)
                    }
                    var wifiOnlyAutoDownload by remember {
                        mutableStateOf(messagingPrefs.wifiOnlyAutoDownload)
                    }
                    var autoSaveMedia by remember {
                        mutableStateOf(messagingPrefs.autoSaveMedia)
                    }
                    var replaceEmoticons by remember {
                        mutableStateOf(messagingPrefs.replaceEmoticons)
                    }
                    var showAvatarsInDirectChats by remember {
                        mutableStateOf(messagingPrefs.showAvatarsInDirectChats)
                    }
                    val rows = buildList<SettingsRowContent> {
                        add { index, count ->
                            SettingsActionItem(
                                title = "Default sending address",
                                supporting = defaultSendingHandle?.let(::sendingHandleLabel) ?: "Automatic",
                                onClick = { showDefaultSendingHandleDialog = true },
                                index = index,
                                count = count,
                                enabled = availableSendingHandles.isNotEmpty() || defaultSendingHandle != null,
                                icon = Icons.AutoMirrored.Filled.Send,
                            )
                        }
                        add { index, count ->
                            SettingsToggleItem(
                                title = "Send read receipts",
                                supporting = "Tell people in direct iMessage chats when you read their messages",
                                checked = sendReadReceipts,
                                onCheckedChange = { enabled ->
                                    sendReadReceipts = enabled
                                    messagingPrefs.sendReadReceipts = enabled
                                },
                                index = index,
                                count = count,
                                icon = Icons.Filled.DoneAll,
                            )
                        }
                        add { index, count ->
                            SettingsToggleItem(
                                title = "Send typing indicators",
                                supporting = "Show people in iMessage chats when you are typing",
                                checked = sendTypingIndicators,
                                onCheckedChange = { enabled ->
                                    sendTypingIndicators = enabled
                                    messagingPrefs.sendTypingIndicators = enabled
                                },
                                index = index,
                                count = count,
                                icon = Icons.Filled.Keyboard,
                            )
                        }
                        add { index, count ->
                            SettingsToggleItem(
                                title = "Delivery timestamps",
                                supporting = "Show delivered and read times below outgoing messages",
                                checked = showDeliveryTimestamps,
                                onCheckedChange = { enabled ->
                                    showDeliveryTimestamps = enabled
                                    messagingPrefs.showDeliveryTimestamps = enabled
                                },
                                index = index,
                                count = count,
                                icon = Icons.Filled.ManageHistory,
                            )
                        }
                        add { index, count ->
                            SettingsToggleItem(
                                title = "Share Focus",
                                supporting = "Publish a silenced Focus status to iMessage contacts",
                                checked = shareFocusStatus,
                                onCheckedChange = { enabled ->
                                    shareFocusStatus = enabled
                                    messagingPrefs.shareFocusStatus = enabled
                                    scope.launch(Dispatchers.IO) {
                                        runCatching {
                                            pushState?.publishStatus(if (enabled) SHARED_FOCUS_GUID else null)
                                        }.onFailure {
                                            withContext(Dispatchers.Main) {
                                                shareFocusStatus = !enabled
                                                messagingPrefs.shareFocusStatus = !enabled
                                            }
                                        }
                                    }
                                },
                                index = index,
                                count = count,
                                icon = Icons.Filled.Notifications,
                            )
                        }
                        add { index, count ->
                            SettingsToggleItem(
                                title = "Show subject field",
                                supporting = "Add an optional subject line above the message composer",
                                checked = sendSubjectLines,
                                onCheckedChange = { enabled ->
                                    sendSubjectLines = enabled
                                    messagingPrefs.sendSubjectLines = enabled
                                },
                                index = index,
                                count = count,
                                icon = Icons.Filled.AlternateEmail,
                            )
                        }
                        add { index, count ->
                            SettingsActionItem(
                                title = "Auto-download media",
                                supporting = autoDownloadLimit.title,
                                onClick = { showAutoDownloadDialog = true },
                                index = index,
                                count = count,
                                icon = Icons.Filled.DownloadForOffline,
                            )
                        }
                        add { index, count ->
                            SettingsToggleItem(
                                title = "Wi-Fi only auto-download",
                                supporting = "Wait for an unmetered connection before fetching media",
                                checked = wifiOnlyAutoDownload,
                                onCheckedChange = { enabled ->
                                    wifiOnlyAutoDownload = enabled
                                    messagingPrefs.wifiOnlyAutoDownload = enabled
                                },
                                index = index,
                                count = count,
                                icon = Icons.Filled.Wifi,
                            )
                        }
                        add { index, count ->
                            SettingsToggleItem(
                                title = "Auto-save media",
                                supporting = "Copy downloaded photos and videos into Downloads",
                                checked = autoSaveMedia,
                                onCheckedChange = { enabled ->
                                    autoSaveMedia = enabled
                                    messagingPrefs.autoSaveMedia = enabled
                                },
                                index = index,
                                count = count,
                                icon = Icons.Filled.SaveAlt,
                            )
                        }
                        add { index, count ->
                            SettingsToggleItem(
                                title = "Replace emoticons",
                                supporting = "Turn :) and similar shortcuts into emoji when you send",
                                checked = replaceEmoticons,
                                onCheckedChange = { enabled ->
                                    replaceEmoticons = enabled
                                    messagingPrefs.replaceEmoticons = enabled
                                },
                                index = index,
                                count = count,
                                icon = Icons.Filled.EmojiEmotions,
                            )
                        }
                        add { index, count ->
                            SettingsToggleItem(
                                title = "Avatars in direct chats",
                                supporting = "Show contact photos next to one-on-one conversations",
                                checked = showAvatarsInDirectChats,
                                onCheckedChange = { enabled ->
                                    showAvatarsInDirectChats = enabled
                                    messagingPrefs.showAvatarsInDirectChats = enabled
                                },
                                index = index,
                                count = count,
                                icon = Icons.Filled.Face,
                            )
                        }
                        add { index, count ->
                            SettingsActionItem(
                                title = "Archived conversations",
                                supporting = if (archivedCount == 0) {
                                    "None"
                                } else {
                                    "$archivedCount archived"
                                },
                                onClick = onOpenArchived,
                                index = index,
                                count = count,
                                icon = Icons.Filled.Archive,
                            )
                        }
                        add { index, count ->
                            SettingsActionItem(
                                title = "Recently Deleted",
                                supporting = if (recentlyDeletedCount == 0) {
                                    "None"
                                } else {
                                    "$recentlyDeletedCount recoverable"
                                },
                                onClick = onOpenRecentlyDeleted,
                                index = index,
                                count = count,
                                icon = Icons.Filled.Restore,
                            )
                        }
                        // One row for the SMS role: the chip tone says whether it is
                        // active, the tap opens the system role picker either way.
                        add { index, count ->
                            SettingsActionItem(
                                title = "SMS & MMS",
                                supporting = if (isDefaultSmsApp) {
                                    "On — incoming and outgoing SMS stay in this app and in Android's message store"
                                } else {
                                    "Off — set OpenGarden as the default SMS app so carrier SMS, MMS, and media arrive here"
                                },
                                onClick = {
                                    SmsRole.requestIntent(context)?.let(smsRoleLauncher::launch)
                                },
                                index = index,
                                count = count,
                                multiline = true,
                                icon = Icons.Filled.Sms,
                                iconTone = if (isDefaultSmsApp) {
                                    SettingsRowTone.Active
                                } else {
                                    SettingsRowTone.Neutral
                                },
                            )
                        }
                    }
                    rows.forEachIndexed { index, row -> row(index, rows.size) }
                }
            }
        },
        dialogs = {
            if (showDefaultSendingHandleDialog) {
                val optionCount = availableSendingHandles.size + 1
                AlertDialog(
                    onDismissRequest = { showDefaultSendingHandleDialog = false },
                    title = { Text("Default sending address") },
                    text = {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "Used for every conversation, including ones that started on " +
                                    "another address. Long-press a conversation to give it its " +
                                    "own send-from address instead.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SettingsGroup {
                                SettingsChoiceItem(
                                    title = "Automatic",
                                    supporting = "Use the conversation address, then the first registered address",
                                    selected = defaultSendingHandle == null,
                                    onClick = {
                                        defaultSendingHandle = null
                                        messagingPrefs.defaultSendingHandle = null
                                        showDefaultSendingHandleDialog = false
                                    },
                                    index = 0,
                                    count = optionCount,
                                )
                                availableSendingHandles.forEachIndexed { index, handle ->
                                    SettingsChoiceItem(
                                        title = sendingHandleLabel(handle),
                                        supporting = sendingHandleType(handle),
                                        selected = defaultSendingHandle == handle,
                                        onClick = {
                                            defaultSendingHandle = handle
                                            messagingPrefs.defaultSendingHandle = handle
                                            showDefaultSendingHandleDialog = false
                                        },
                                        index = index + 1,
                                        count = optionCount,
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showDefaultSendingHandleDialog = false }) {
                            Text("Done")
                        }
                    },
                )
            }

            if (showAutoDownloadDialog) {
                AlertDialog(
                    onDismissRequest = { showAutoDownloadDialog = false },
                    title = { Text("Auto-download media") },
                    text = {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 480.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                "Incoming photos, videos, and voice memos up to the selected " +
                                    "size download on their own. Anything larger shows a " +
                                    "download button instead.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SettingsGroup {
                                AutoDownloadLimit.entries.forEachIndexed { index, option ->
                                    SettingsChoiceItem(
                                        title = option.title,
                                        supporting = option.description,
                                        selected = autoDownloadLimit == option,
                                        onClick = {
                                            autoDownloadLimit = option
                                            messagingPrefs.autoDownloadMaxBytes = option.persistedValue
                                        },
                                        index = index,
                                        count = AutoDownloadLimit.entries.size,
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showAutoDownloadDialog = false }) {
                            Text("Done")
                        }
                    },
                )
            }
        },
    )
}

private fun sendingHandleLabel(handle: String): String = handle.substringAfter(':', handle)

private fun sendingHandleType(handle: String): String = when {
    handle.startsWith("tel:") -> "Phone number"
    handle.startsWith("mailto:") -> "Email address"
    else -> "Registered address"
}
