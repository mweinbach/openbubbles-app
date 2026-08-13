import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:flutter/foundation.dart';
import 'package:get/get.dart';
import 'package:google_ml_kit/google_ml_kit.dart' hide Message;

/// Manages smart reply generation via ML Kit.
///
/// Responsibilities:
/// - Initialize ML Kit smart reply processor on startup
/// - Add incoming messages to conversation context
/// - Generate reply suggestions when new messages arrive
/// - Clear suggestions when user sends a message
/// - Refresh suggestions when non-user messages arrive
class SmartRepliesManager {
  late final SmartReply smartReply;

  final RxList<String> smartReplies = <String>[].obs;

  SmartRepliesManager() {
    smartReply = GoogleMlKit.nlp.smartReply();
  }

  bool shouldShowSmartReplies(bool messagesEmpty) {
    return !messagesEmpty && smartReplies.isNotEmpty;
  }

  void addMessageToContext(Message message) {
    if (message.isFromMe ?? false) {
      smartReply.addMessageToConversationFromLocalUser(
        message.fullText,
        message.dateCreated!.millisecondsSinceEpoch,
      );
    } else {
      smartReply.addMessageToConversationFromRemoteUser(
        message.fullText,
        message.dateCreated!.millisecondsSinceEpoch,
        message.handleRelation.target?.address ?? "participant",
      );
    }
  }

  /// Generate smart reply suggestions based on current conversation context.
  /// Call this after adding messages or when new context arrives.
  Future<void> generateSuggestions() async {
    try {
      SmartReplySuggestionResult results = await smartReply.suggestReplies();

      if (results.status == SmartReplySuggestionResultStatus.success) {
        smartReplies.value = results.suggestions;
      } else {
        smartReplies.clear();
      }
    } catch (e) {
      // Silently fail if ML Kit is unavailable
    }
  }

  /// Clean up resources (close ML Kit processor).
  void dispose() {
    if (!kIsWeb && !kIsDesktop) {
      smartReply.close();
    }
  }
}
