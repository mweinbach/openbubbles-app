import 'package:flutter/foundation.dart';
import 'package:bluebubbles/database/models.dart';

@immutable
class MessageUpdateEvent {
  final Message message;
  final String? tempGuid;
  final Chat? chat;

  const MessageUpdateEvent(this.message, this.tempGuid, this.chat);
}

@immutable
class NewMessageEvent {
  final Message message;
  final Chat? chat;

  const NewMessageEvent(this.message, this.chat);
}
