import 'package:bluebubbles/app/components/custom_text_editing_controllers.dart';
import 'package:bluebubbles/services/ui/chat/conversation_view_controller.dart';
import 'package:bluebubbles/utils/emoji.dart';
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:flutter/material.dart';
import 'package:unicode_emojis/unicode_emojis.dart';

/// Helper class for processing emoji and mention matches in the text field
class TextFieldMatchHelper {
  /// Process emoji matches based on text input
  /// This is debounced to avoid running regex on every keystroke
  static void processEmojiMatches(
    ConversationViewController controller,
    TextEditingController textController,
    bool isSubject,
  ) {
    final newEmojiText = textController.text;

    if (!newEmojiText.contains(":")) {
      controller.emojiMatches.value = [];
      controller.emojiSelectedIndex.value = 0;
      return;
    }

    final regExp = RegExp(r"(?<=^|[^a-zA-Z\d]):[^: \n]{2,}(?:(?=[ \n]|$)|:)", multiLine: true);
    final matches = regExp.allMatches(newEmojiText);
    List<Emoji> allMatches = [];
    String emojiName = "";

    if (matches.isNotEmpty && matches.first.start < textController.selection.start) {
      RegExpMatch match = matches.lastWhere((m) => m.start < textController.selection.start);
      if (newEmojiText[match.end - 1] == ":") {
        // This will get handled by the text field controller
      } else if (match.end >= textController.selection.start) {
        emojiName = newEmojiText.substring(match.start + 1, match.end).toLowerCase();
        allMatches = limitGenerator(emojiQuery(emojiName), limit: 50).toSet().toList();
      }
      Logger.info("${allMatches.length} matches found for: $emojiName");
    }

    if (allMatches.isNotEmpty) {
      controller.mentionMatches.value = [];
      controller.mentionSelectedIndex.value = 0;
      controller.emojiMatches.value = allMatches;
      controller.emojiSelectedIndex.value = 0;
    } else {
      controller.emojiMatches.value = [];
      controller.emojiSelectedIndex.value = 0;
    }
  }

  /// Process mention matches based on text input
  /// This is debounced to avoid running regex on every keystroke
  static void processMentionMatches(
    ConversationViewController controller,
    TextEditingController textController,
    bool isSubject,
  ) {
    final newEmojiText = textController.text;

    if (isSubject || !newEmojiText.contains("@")) {
      controller.mentionMatches.value = [];
      controller.mentionSelectedIndex.value = 0;
      return;
    }

    final regExp = RegExp(r"(?<=^|[^a-zA-Z\d])@(?:[^@ \n]+|$)(?=[ \n]|$)", multiLine: true);
    final matches = regExp.allMatches(newEmojiText);
    List<Mentionable> allMatches = [];
    String mentionName = "";

    if (matches.isNotEmpty && matches.first.start < textController.selection.start) {
      RegExpMatch match = matches.lastWhere((m) => m.start < textController.selection.start);
      final text = newEmojiText.substring(match.start, match.end);

      if (text.endsWith("@")) {
        allMatches = controller.mentionables;
      } else if (newEmojiText[match.end - 1] == "@") {
        mentionName = newEmojiText.substring(match.start + 1, match.end - 1).toLowerCase();
        allMatches = controller.mentionables
            .where((e) =>
                e.address.toLowerCase().startsWith(mentionName.toLowerCase()) ||
                e.displayName.toLowerCase().startsWith(mentionName.toLowerCase()))
            .toList();
        allMatches.addAll(controller.mentionables
            .where((e) =>
                !allMatches.contains(e) &&
                (e.address.toLowerCase().contains(mentionName) || e.displayName.toLowerCase().contains(mentionName)))
            .toList());
      } else if (match.end >= textController.selection.start) {
        mentionName = newEmojiText.substring(match.start + 1, match.end).toLowerCase();
        allMatches = controller.mentionables
            .where((e) =>
                e.address.toLowerCase().startsWith(mentionName.toLowerCase()) ||
                e.displayName.toLowerCase().startsWith(mentionName.toLowerCase()))
            .toList();
        allMatches.addAll(controller.mentionables
            .where((e) =>
                !allMatches.contains(e) &&
                (e.address.toLowerCase().contains(mentionName) || e.displayName.toLowerCase().contains(mentionName)))
            .toList());
      }
      Logger.info("${allMatches.length} matches found for: $mentionName");
    }

    if (allMatches.isNotEmpty) {
      controller.emojiMatches.value = [];
      controller.emojiSelectedIndex.value = 0;
      controller.mentionMatches.value = allMatches;
      controller.mentionSelectedIndex.value = 0;
    } else {
      controller.mentionMatches.value = [];
      controller.mentionSelectedIndex.value = 0;
    }
  }
}
