import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/env.dart';
import 'package:bluebubbles/services/backend/actions/sync_actions.dart';
import 'package:bluebubbles/services/isolates/incremental_sync_isolate.dart';
import 'package:get_it/get_it.dart';
import 'package:bluebubbles/services/isolates/global_isolate.dart';

class SyncInterface {
  /// Unified sync: persists handles, chats, messages, and attachments from raw
  /// API maps. Replaces the three older bulk-sync entry points.
  static Future<({List<Message> messages, List<Chat> chats})> bulkSyncData({
    Map<String, dynamic>? chatData,
    required List<Map<String, dynamic>> messagesData,
  }) async {
    final data = {
      'chatData': chatData,
      'messagesData': messagesData,
    };

    late Map<String, dynamic> result;
    if (isIsolate) {
      result = await SyncActions.bulkSyncData(data);
    } else {
      result = await GetIt.I<GlobalIsolate>().send<Map<String, dynamic>>(IsolateRequestType.bulkSyncData, input: data);
    }

    final messageIds = (result['messageIds'] as List).cast<int>();
    final chatIds = (result['chatIds'] as List).cast<int>();
    return (
      messages: Database.messages.getMany(messageIds).whereType<Message>().toList(),
      chats: Database.chats.getMany(chatIds).whereType<Chat>().toList(),
    );
  }

  /// Performs an incremental sync in the isolate.
  /// Returns the latest [Message] object per synced chat, hydrated from the local DB.
  /// Callers use these messages to update [ChatState] subtitles via [ChatsService].
  static Future<List<Message>> performIncrementalSync({bool useGlobalIsolate = false}) async {
    late List<int> messageIds = [];
    if (isIsolate) {
      messageIds = await SyncActions.performIncrementalSync({});
    } else {
      if (useGlobalIsolate) {
        messageIds =
            await GetIt.I<GlobalIsolate>().send<List<int>>(IsolateRequestType.performIncrementalSync, input: {});
      } else {
        messageIds = await GetIt.I<IncrementalSyncIsolate>()
            .send<List<int>>(IsolateRequestType.performIncrementalSync, input: {});
      }
    }

    return Database.messages.getMany(messageIds).whereType<Message>().toList();
  }
}
