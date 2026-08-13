import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/types/helpers/message_helper.dart';
import 'package:bluebubbles/utils/logger/logger.dart';

class MessageActions {
  static Future<List<Message>?> getMessages() async {
    // Fetch a message with a limit of 1 using ObjectBox
    final oneDayAgo = DateTime.now().subtract(const Duration(days: 1));
    final messages = (await Database.messages
        .query(Message_.dateCreated.greaterThan(oneDayAgo.millisecondsSinceEpoch))
        .build()
        .findAsync());
    if (messages.isNotEmpty) {
      return messages;
    }
    return null;
  }

  static Future<int> replaceMessage(dynamic data) async {
    final oldGuid = data['oldGuid'] as String?;
    final newMessageData = data['newMessageData'] as Map<String, dynamic>;

    return Database.runInTransaction(TxMode.write, () {
      final messageBox = Database.messages;
      final inputNewMessage = Message.fromMap(newMessageData);

      // Find existing message
      final query = messageBox.query(Message_.guid.equals(oldGuid ?? '')).build();
      query.limit = 1;
      final existing = query.findFirst();
      query.close();

      if (existing == null) {
        throw Exception("Cannot replace on a null existing message!!");
      }

      // We just need to update the timestamps & error
      if (existing.guid != inputNewMessage.guid) {
        existing.guid = inputNewMessage.guid;
      }
      if (inputNewMessage.text != null) {
        existing.text = inputNewMessage.text;
      }

      existing.dateDelivered = inputNewMessage.dateDelivered ?? existing.dateDelivered;
      existing.isDelivered = inputNewMessage.isDelivered;
      existing.dateRead = inputNewMessage.dateRead ?? existing.dateRead;
      existing.dateEdited = inputNewMessage.dateEdited ?? existing.dateEdited;
      existing.attributedBody =
          inputNewMessage.attributedBody.isNotEmpty ? inputNewMessage.attributedBody : existing.attributedBody;
      existing.messageSummaryInfo = inputNewMessage.messageSummaryInfo.isNotEmpty
          ? inputNewMessage.messageSummaryInfo
          : existing.messageSummaryInfo;
      existing.payloadData = inputNewMessage.payloadData ?? existing.payloadData;
      existing.wasDeliveredQuietly =
          inputNewMessage.wasDeliveredQuietly ? inputNewMessage.wasDeliveredQuietly : existing.wasDeliveredQuietly;
      existing.didNotifyRecipient =
          inputNewMessage.didNotifyRecipient ? inputNewMessage.didNotifyRecipient : existing.didNotifyRecipient;
      existing.error = inputNewMessage.error;
      existing.errorMessage = inputNewMessage.errorMessage;
      existing.stagingGuid = inputNewMessage.stagingGuid ?? existing.stagingGuid;
      existing.verificationFailed = inputNewMessage.verificationFailed;
      existing.amkSessionId = inputNewMessage.amkSessionId ?? existing.amkSessionId;
      if (existing.dateScheduled != null && inputNewMessage.dateScheduled == null) {
        existing.dateCreated = inputNewMessage.dateCreated ?? existing.dateCreated;
      }
      existing.dateScheduled = inputNewMessage.dateScheduled;

      try {
        messageBox.put(existing, mode: PutMode.update);
      } catch (ex) {
        Logger.warn(
            'Failed to replace message! This is likely due to a unique constraint being violated: ${ex.toString()}');
      }

      // Return just the ID for efficient transfer across isolates
      return existing.id!;
    });
  }

  static Future<void> deleteMessage(dynamic data) async {
    final guid = data['guid'] as String;

    Database.runInTransaction(TxMode.write, () {
      final messageBox = Database.messages;

      final query = messageBox.query(Message_.guid.equals(guid)).build();
      final result = query.findFirst();
      query.close();
      if (result?.id != null) {
        messageBox.remove(result!.id!);
      }
    });
  }

  static Future<void> softDeleteMessage(dynamic data) async {
    final guid = data['guid'] as String;

    Database.runInTransaction(TxMode.write, () {
      final messageBox = Database.messages;

      final query = messageBox.query(Message_.guid.equals(guid)).build();
      query.limit = 1;
      final toDelete = query.findFirst();
      query.close();

      if (toDelete != null) {
        toDelete.dateDeleted = DateTime.now().toUtc();
        messageBox.put(toDelete);
      }
    });
  }

  /// Bulk-deletes every message older than [data]'s `cutoffMs` across all chats,
  /// then removes any chat left empty and refreshes the latest-message date on the
  /// chats that remain. Runs inside the background isolate so the heavy query and
  /// write transaction never block the UI thread.
  ///
  /// The chat whose GUID matches `preservedChatGuid` (the one the user is currently
  /// viewing) is never deleted, even if it ends up empty.
  ///
  /// Returns `{ 'deletedCount': int, 'deletedChatGuids': List<String> }` so the
  /// caller can tear down the corresponding service state on the main thread.
  static Future<Map<String, dynamic>> deleteOldMessages(dynamic data) async {
    final cutoffMs = data['cutoffMs'] as int;
    final preservedChatGuid = data['preservedChatGuid'] as String?;
    final cutoff = DateTime.fromMillisecondsSinceEpoch(cutoffMs);

    return Database.runInTransaction(TxMode.write, () {
      final messageBox = Database.messages;
      final chatBox = Database.chats;

      // Delete every message older than the cutoff.
      final oldMessageQuery = messageBox
          .query(Message_.dateCreated.notNull().and(Message_.dateCreated.lessThanDate(cutoff)))
          .build();
      final oldMessageIds = oldMessageQuery.findIds();
      oldMessageQuery.close();
      if (oldMessageIds.isNotEmpty) {
        messageBox.removeMany(oldMessageIds);
      }

      // Recompute latest-message dates and collect chats that are now empty.
      final chatsToUpdate = <Chat>[];
      final chatIdsToDelete = <int>[];
      final chatGuidsToDelete = <String>[];

      for (final chat in chatBox.getAll()) {
        if (chat.id == null) continue;

        final anyMessageQuery = (messageBox.query()..link(Message_.chat, Chat_.id.equals(chat.id!))).build();
        anyMessageQuery.limit = 1;
        final hasMessages = anyMessageQuery.findFirst() != null;
        anyMessageQuery.close();

        if (!hasMessages && chat.guid != preservedChatGuid) {
          chatIdsToDelete.add(chat.id!);
          chatGuidsToDelete.add(chat.guid);
          continue;
        }

        final latestMessageQuery = (messageBox.query(
          Message_.dateDeleted.isNull().and(Message_.dateCreated.notNull()),
        )
              ..link(Message_.chat, Chat_.id.equals(chat.id!))
              ..order(Message_.dateCreated, flags: Order.descending))
            .build();
        latestMessageQuery.limit = 1;
        final latestMessage = latestMessageQuery.findFirst();
        latestMessageQuery.close();

        final newDate = latestMessage?.dateCreated ?? DateTime.fromMillisecondsSinceEpoch(0);
        if (chat.dbOnlyLatestMessageDate != newDate) {
          chat.dbOnlyLatestMessageDate = newDate;
          chatsToUpdate.add(chat);
        }
      }

      if (chatsToUpdate.isNotEmpty) chatBox.putMany(chatsToUpdate);
      if (chatIdsToDelete.isNotEmpty) chatBox.removeMany(chatIdsToDelete);

      return {
        'deletedCount': oldMessageIds.length,
        'deletedChatGuids': chatGuidsToDelete,
      };
    });
  }

  static Future<Map<String, dynamic>> fetchAssociatedMessagesAsync(dynamic data) async {
    final messageGuid = data['messageGuid'] as String;
    final threadOriginatorGuid = data['threadOriginatorGuid'] as String?;

    return Database.runInTransaction(TxMode.read, () {
      final messageBox = Database.messages;

      // Fetch associated messages (reactions)
      final query = messageBox.query(Message_.associatedMessageGuid.equals(messageGuid)).build();
      List<Message> associatedMessages = query.find();
      query.close();

      associatedMessages = MessageHelper.normalizedAssociatedMessages(associatedMessages);

      // If there's a thread originator, fetch it
      if (threadOriginatorGuid != null) {
        final originatorQuery = messageBox.query(Message_.guid.equals(threadOriginatorGuid)).build();
        originatorQuery.limit = 1;
        final threadOriginator = originatorQuery.findFirst();
        originatorQuery.close();

        if (threadOriginator != null) {
          associatedMessages.add(threadOriginator);
        }
      }

      associatedMessages.sort((a, b) => a.originalROWID!.compareTo(b.originalROWID!));

      return {
        'associatedMessages': associatedMessages.map((e) => e.toMap()).toList(),
      };
    });
  }

  static Future<int> saveMessageAsync(dynamic data) async {
    final messageData = data['messageData'] as Map<String, dynamic>;
    final chatData = data['chatData'] as Map<String, dynamic>?;
    final updateIsBookmarked = data['updateIsBookmarked'] as bool;
    final updateSendingServiceId = data['updateSendingServiceId'] as bool;

    return Database.runInTransaction(TxMode.write, () {
      final messageBox = Database.messages;
      final handleBox = Database.handles;

      final inputMessage = Message.fromMap(messageData);
      final inputChat = chatData != null ? Chat.fromMap(chatData) : null;

      // Find existing message
      final existingQuery = messageBox.query(Message_.guid.equals(inputMessage.guid ?? '')).build();
      existingQuery.limit = 1;
      final existing = existingQuery.findFirst();
      existingQuery.close();

      if (existing != null) {
        inputMessage.id = existing.id;
        inputMessage.text ??= existing.text;

        // Preserve existing handleRelation if available
        if (existing.handleRelation.hasValue && !inputMessage.handleRelation.hasValue) {
          inputMessage.handleRelation.target = existing.handleRelation.target;
          inputMessage.handle = existing.handleRelation.target;
        }
      }

      // Save the participant & set the handle ID to the new participant
      // Only do handleId lookup if we don't already have a handleRelation
      if (inputMessage.handle == null && !inputMessage.handleRelation.hasValue && inputMessage.handleId != null) {
        final handleQuery = handleBox.query(Handle_.originalROWID.equals(inputMessage.handleId!)).build();
        handleQuery.limit = 1;
        final foundHandle = handleQuery.findFirst();
        handleQuery.close();
        inputMessage.handle = foundHandle;

        // Set up handleRelation for the ToOne relationship
        if (foundHandle != null && foundHandle.id != null) {
          inputMessage.handleRelation.target = foundHandle;
        }
      } else if (inputMessage.handleRelation.hasValue && inputMessage.handle == null) {
        // Use existing relationship to populate handle field
        inputMessage.handle = inputMessage.handleRelation.target;
      }

      // Save associated messages or the original message (depending on whether
      // this message is a reaction or regular message
      if (inputMessage.associatedMessageType != null && inputMessage.associatedMessageGuid != null) {
        final associatedQuery = messageBox.query(Message_.guid.equals(inputMessage.associatedMessageGuid!)).build();
        associatedQuery.limit = 1;
        final associatedMessage = associatedQuery.findFirst();
        associatedQuery.close();

        if (associatedMessage != null) {
          associatedMessage.hasReactions = true;
          messageBox.put(associatedMessage);
        }
      } else if (!inputMessage.hasReactions) {
        final reactionQuery = messageBox.query(Message_.associatedMessageGuid.equals(inputMessage.guid ?? '')).build();
        reactionQuery.limit = 1;
        final reaction = reactionQuery.findFirst();
        reactionQuery.close();

        if (reaction != null) {
          inputMessage.hasReactions = true;
        }
      }

      if (!updateIsBookmarked) {
        inputMessage.isBookmarked = existing?.isBookmarked ?? inputMessage.isBookmarked;
      }
      if (!updateSendingServiceId) {
        inputMessage.sendingServiceId = existing?.sendingServiceId ?? inputMessage.sendingServiceId;
      }

      try {
        if (inputChat != null) inputMessage.chat.target = inputChat;
        inputMessage.id = messageBox.put(inputMessage);
      } on UniqueViolationException catch (_) {}

      // Return just the ID for efficient transfer across isolates
      return inputMessage.id!;
    });
  }

  static Future<int?> findOneAsync(dynamic data) async {
    final guid = data['guid'] as String?;
    final associatedMessageGuid = data['associatedMessageGuid'] as String?;

    return Database.runInTransaction(TxMode.read, () {
      final messageBox = Database.messages;

      Message? result;

      if (guid != null) {
        final query = messageBox.query(Message_.guid.equals(guid)).build();
        query.limit = 1;
        result = query.findFirst();
        query.close();
      } else if (associatedMessageGuid != null) {
        final query = messageBox.query(Message_.associatedMessageGuid.equals(associatedMessageGuid)).build();
        query.limit = 1;
        result = query.findFirst();
        query.close();
      }

      // Return just the ID for efficient transfer across isolates
      return result?.id;
    });
  }

  static Future<List<int>> findAsync(dynamic data) async {
    // For now, we'll support finding all messages
    // A more sophisticated implementation would deserialize the condition JSON
    return Database.runInTransaction(TxMode.read, () {
      final messageBox = Database.messages;

      final query = messageBox.query().build();
      final results = query.find();
      query.close();

      // Return just the IDs for efficient transfer across isolates
      return results.map((e) => e.id!).toList();
    });
  }
}
