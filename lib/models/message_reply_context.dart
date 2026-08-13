import 'package:flutter/foundation.dart';
import 'package:bluebubbles/database/models.dart';

@immutable
class MessageReplyContext {
  final Message message;
  final int partIndex;
  final String? attachmentGuid;

  const MessageReplyContext(this.message, this.partIndex, {this.attachmentGuid});
}
