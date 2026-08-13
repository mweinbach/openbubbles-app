import 'package:flutter/foundation.dart';
import 'package:bluebubbles/database/models.dart';

@immutable
class MessageSaveResult {
  final Message message;
  final bool isNewer;

  const MessageSaveResult(this.message, this.isNewer);
}
