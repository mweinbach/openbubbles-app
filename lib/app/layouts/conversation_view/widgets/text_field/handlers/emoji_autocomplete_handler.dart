import 'dart:math';

import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:unicode_emojis/unicode_emojis.dart';

/// Handles emoji autocomplete detection, matching, and selection via keyboard shortcuts.
///
/// This handler intercepts:
/// - Down arrow: cycle through emoji matches
/// - Up arrow: cycle backwards through emoji matches
/// - Tab/Enter: insert selected emoji at cursor position
/// - Escape: clear emoji matches
class EmojiAutocompleteHandler {
  final ConversationViewController controller;
  final TextEditingController textField;

  EmojiAutocompleteHandler({
    required this.controller,
    required this.textField,
  });

  /// Handle emoji-related key events. Returns true if the event was handled.
  bool handleKeyEvent({
    required LogicalKeyboardKey logicalKey,
    required bool isEscapeKey,
    required bool isTabOrEnter,
    required bool isDownArrow,
    required bool isUpArrow,
    required int? maxShown,
    required int? upMovementIndex,
    required int? downMovementIndex,
  }) {
    // Escape key: clear emoji matches
    if (isEscapeKey) {
      if (controller.emojiMatches.isNotEmpty) {
        controller.emojiMatches.value = <Emoji>[];
        return true;
      }
    }

    // Down arrow: cycle through matches
    if (isDownArrow) {
      if (controller.emojiMatches.length > controller.emojiSelectedIndex.value) {
        controller.emojiSelectedIndex.value++;
        if (controller.emojiSelectedIndex.value >= downMovementIndex! &&
            controller.emojiSelectedIndex < controller.emojiMatches.length - maxShown! + downMovementIndex + 1) {
          controller.emojiScrollController.jumpTo(max(
              (controller.emojiSelectedIndex.value - downMovementIndex) * 40, controller.emojiScrollController.offset));
        }
        return true;
      }
    }

    // Up arrow: cycle backwards through matches
    if (isUpArrow) {
      if (controller.emojiSelectedIndex.value > 0) {
        controller.emojiSelectedIndex.value--;
        if (controller.emojiSelectedIndex.value >= upMovementIndex! &&
            controller.emojiSelectedIndex < controller.emojiMatches.length - maxShown! + upMovementIndex + 1) {
          controller.emojiScrollController.jumpTo(min(
              (controller.emojiSelectedIndex.value - upMovementIndex) * 40, controller.emojiScrollController.offset));
        }
        return true;
      }
    }

    // Tab or Enter: insert emoji at cursor
    if (isTabOrEnter) {
      if (controller.emojiMatches.isEmpty || controller.emojiMatches.length <= controller.emojiSelectedIndex.value) {
        return false;
      }

      int index = controller.emojiSelectedIndex.value;
      String text = textField.text;
      RegExp regExp = RegExp(r':[^: \n]{2,}(?=[ \n]|$)', multiLine: true);
      Iterable<RegExpMatch> matches = regExp.allMatches(text);

      if (matches.isNotEmpty && matches.any((m) => m.start < textField.selection.start)) {
        RegExpMatch match = matches.lastWhere((m) => m.start < textField.selection.start);
        String emoji = controller.emojiMatches[index].emoji;
        String newText = "${text.substring(0, match.start)}$emoji ${text.substring(match.end)}";
        textField.value = TextEditingValue(
          text: newText,
          selection: TextSelection.collapsed(offset: match.start + emoji.length + 1),
        );
      } else {
        // If user moved cursor before inserting, reset picker
        controller.emojiScrollController.jumpTo(0);
      }

      controller.emojiSelectedIndex.value = 0;
      controller.emojiMatches.value = <Emoji>[];

      return true;
    }

    return false;
  }
}
