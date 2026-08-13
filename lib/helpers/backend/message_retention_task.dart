import 'dart:async';

import 'package:bluebubbles/env.dart';
import 'package:bluebubbles/services/backend/interfaces/message_interface.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:flutter/foundation.dart';
import 'package:get/get.dart';
import 'package:get_it/get_it.dart';

class MessageRetentionTask {
  static const Duration _dayDuration = Duration(days: 1);
  static Timer? _timer;
  static bool _running = false;

  static void start() {
    if (kIsWeb || _timer != null) return;

    run();
    _timer = Timer.periodic(_dayDuration, (_) => run());
  }

  static Future<void> run({bool force = false}) async {
    if (kIsWeb || _running || !SettingsSvc.settings.finishedSetup.value || !SettingsSvc.settings.deleteMessagesAfterDays.value) return;

    final days = SettingsSvc.settings.deleteMessagesAfterDaysCount.value;
    if (days <= 0) return;

    final now = DateTime.now();
    final lastCleanup = SettingsSvc.settings.lastMessageRetentionCleanup.value;
    if (!force && lastCleanup > 0) {
      final lastCleanupDate = DateTime.fromMillisecondsSinceEpoch(lastCleanup);
      if (now.difference(lastCleanupDate) < _dayDuration) return;
    }

    _running = true;
    try {
      final cutoff = now.subtract(_dayDuration * days);

      // Never delete the chat the user is currently viewing, even if it empties out.
      final activeChatGuid = _canUpdateUi ? ChatsSvc.activeChat?.chat.guid : null;

      // Do the heavy query + deletion on the background isolate so the UI thread is
      // never blocked. The isolate also drops any now-empty chats and refreshes the
      // latest-message date on the chats that survive.
      final result = await MessageInterface.deleteOldMessages(
        cutoffMs: cutoff.millisecondsSinceEpoch,
        preservedChatGuid: activeChatGuid,
      );

      // Reconcile the in-memory service state on the main thread so the UI stays in
      // sync with the DB. Skipped on headless/background isolates, where no UI exists.
      if (_canUpdateUi) {
        _tearDownDeletedChats(result.deletedChatGuids);

        // Reload the open conversation (if any) so the messages that were just
        // deleted disappear from the transcript without a manual refresh.
        if (activeChatGuid != null && Get.isRegistered<MessagesService>(tag: activeChatGuid)) {
          MessagesSvc(activeChatGuid).reload();
        }
      }

      SettingsSvc.settings.lastMessageRetentionCleanup.value = now.millisecondsSinceEpoch;
      await SettingsSvc.settings.saveOneAsync('lastMessageRetentionCleanup');
      Logger.info(
        "Deleted ${result.deletedCount} messages older than $days days and ${result.deletedChatGuids.length} empty chats",
        tag: "MessageRetention",
      );
    } catch (e, s) {
      Logger.warn("Failed to delete old messages", error: e, trace: s, tag: "MessageRetention");
    } finally {
      _running = false;
    }
  }

  /// Whether we're on the main isolate with a live, foreground [ChatsService] to
  /// update. Background/headless cleanup runs DB-only and skips UI reconciliation.
  static bool get _canUpdateUi => !isIsolate && GetIt.I.isRegistered<ChatsService>() && !ChatsSvc.headless;

  /// Deregister chats the isolate deleted from the DB: clear their notifications,
  /// dispose any open message service, and remove their [ChatState] / list entry.
  static void _tearDownDeletedChats(List<String> deletedChatGuids) {
    for (final guid in deletedChatGuids) {
      final chat = ChatsSvc.getChatState(guid)?.chat ?? ChatsSvc.allChats.firstWhereOrNull((c) => c.guid == guid);
      if (chat == null) continue;

      // Clear any lingering notification for the chat.
      if (chat.id != null) {
        MethodChannelSvc.actions.deleteNotification(
          notificationId: chat.id!,
          tag: NotificationsService.NEW_MESSAGE_TAG,
        );
      }

      // Dispose the per-chat message service so its states/controllers are released.
      if (Get.isRegistered<MessagesService>(tag: guid)) {
        MessagesSvc(guid).close(force: true);
      }

      // Drop the ChatState and remove the chat from the sorted list.
      ChatsSvc.removeChat(chat);
    }
  }
}
