import 'dart:async';

import 'package:bluebubbles/database/models.dart';

enum QueueType { sendMessage, sendReaction, sendAttachment, sendMultipart }

abstract class QueueItem {
  QueueType type;
  Completer<void>? completer;

  QueueItem({required this.type, this.completer});
}

abstract class OutgoingQueueItem extends QueueItem {
  Chat chat;
  Message message;

  OutgoingQueueItem({
    required super.type,
    super.completer,
    required this.chat,
    required this.message,
  });
}

class OutgoingMessage extends OutgoingQueueItem {
  bool isRetry;
  bool clearNotificationsIfFromMe;

  OutgoingMessage({
    super.completer,
    required super.chat,
    required super.message,
    this.isRetry = false,
    this.clearNotificationsIfFromMe = true,
  }) : super(type: QueueType.sendMessage);
}

class OutgoingReaction extends OutgoingQueueItem {
  Message selectedMessage;
  String reaction;
  bool isRetry;
  bool clearNotificationsIfFromMe;

  OutgoingReaction({
    super.completer,
    required super.chat,
    required super.message,
    required this.selectedMessage,
    required this.reaction,
    this.isRetry = false,
    this.clearNotificationsIfFromMe = true,
  }) : super(type: QueueType.sendReaction);
}

class OutgoingAttachment extends OutgoingQueueItem {
  Attachment attachment;
  bool isAudioMessage;
  bool isRetry;

  OutgoingAttachment({
    super.completer,
    required super.chat,
    required super.message,
    required this.attachment,
    this.isAudioMessage = false,
    this.isRetry = false,
  }) : super(type: QueueType.sendAttachment);
}

class OutgoingMultipartMessage extends OutgoingQueueItem {
  bool isRetry;
  bool clearNotificationsIfFromMe;

  OutgoingMultipartMessage({
    super.completer,
    required super.chat,
    required super.message,
    this.isRetry = false,
    this.clearNotificationsIfFromMe = true,
  }) : super(type: QueueType.sendMultipart);
}
