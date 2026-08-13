import 'package:bluebubbles/database/models.dart';

/// Encapsulates all data required to perform a single send operation.
///
/// Passed from the text field / recorder through [ConversationViewController]
/// to [SendAnimation], replacing the previous ad-hoc Tuple6 + isAudioMessage pair.
class SendData {
  const SendData({
    required this.attachments,
    required this.text,
    required this.annotations,
    required this.subject,
    this.replyGuid,
    this.replyPart,
    this.effectId,
    this.payloadData,
    this.isAudioMessage = false,
    this.schedule,
  });

  final List<PlatformFile> attachments;
  final String text;
  final AttributedBody annotations;
  final String subject;
  final String? replyGuid;
  final int? replyPart;
  final String? effectId;
  final PayloadData? payloadData;

  /// Whether the attached file is a voice/audio recording.
  final bool isAudioMessage;
  final DateTime? schedule;
}
