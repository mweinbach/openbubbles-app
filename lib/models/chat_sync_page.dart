import 'package:bluebubbles/database/models.dart';
import 'package:flutter/foundation.dart';

@immutable
class ChatSyncPage {
  final double progress;
  final List<Chat> chats;
  final int filteredCount;

  const ChatSyncPage(this.progress, this.chats, [this.filteredCount = 0]);
}

@immutable
class MessageSyncPage {
  final double progress;
  final List<Map<String, dynamic>> messages;

  const MessageSyncPage(this.progress, this.messages);
}
