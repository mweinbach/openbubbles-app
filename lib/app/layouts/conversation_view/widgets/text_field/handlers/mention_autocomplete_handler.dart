import 'dart:math';

import 'package:bluebubbles/app/components/custom_text_editing_controllers.dart';
import 'package:bluebubbles/app/layouts/conversation_view/dialogs/custom_mention_dialog.dart' as mention_dialog;
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';

/// Handles mention autocomplete detection, matching, and selection via keyboard shortcuts.
///
/// This handler intercepts:
/// - Down arrow: cycle through mention matches
/// - Up arrow: cycle backwards through mention matches
/// - Tab/Enter: insert selected mention at cursor position
/// - Escape: clear mention matches
/// - Custom mention dialog: initiated from context menu (see text_field_component.dart)
class MentionAutocompleteHandler {
  final ConversationViewController controller;
  final TextEditingController textField;
  final BuildContext buildContext;

  MentionAutocompleteHandler({
    required this.controller,
    required this.textField,
    required this.buildContext,
  });

  /// Handle mention-related key events. Returns true if the event was handled.
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
    // Escape key: clear mention matches
    if (isEscapeKey) {
      if (controller.mentionMatches.isNotEmpty) {
        controller.mentionMatches.value = <Mentionable>[];
        return true;
      }
    }

    // Down arrow: cycle through matches
    if (isDownArrow) {
      if (controller.mentionMatches.length > controller.mentionSelectedIndex.value) {
        controller.mentionSelectedIndex.value++;
        if (controller.mentionSelectedIndex.value >= downMovementIndex! &&
            controller.mentionSelectedIndex < controller.mentionMatches.length - maxShown! + downMovementIndex + 1) {
          controller.emojiScrollController.jumpTo(max((controller.mentionSelectedIndex.value - downMovementIndex) * 40,
              controller.emojiScrollController.offset));
        }
        return true;
      }
    }

    // Up arrow: cycle backwards through matches
    if (isUpArrow) {
      if (controller.mentionSelectedIndex.value > 0) {
        controller.mentionSelectedIndex.value--;
        if (controller.mentionSelectedIndex.value >= upMovementIndex! &&
            controller.mentionSelectedIndex < controller.mentionMatches.length - maxShown! + upMovementIndex + 1) {
          controller.emojiScrollController.jumpTo(min(
              (controller.mentionSelectedIndex.value - upMovementIndex) * 40, controller.emojiScrollController.offset));
        }
        return true;
      }
    }

    // Tab or Enter: insert mention at cursor
    if (isTabOrEnter) {
      if (controller.mentionMatches.isEmpty ||
          controller.mentionMatches.length <= controller.mentionSelectedIndex.value) {
        return false;
      }

      int index = controller.mentionSelectedIndex.value;
      String text = textField.text;
      RegExp regExp = RegExp(r"@(?:[^@ \n]+|$)(?=[ \n]|$)", multiLine: true);
      Iterable<RegExpMatch> matches = regExp.allMatches(text);

      if (matches.isNotEmpty && matches.any((m) => m.start < textField.selection.start)) {
        RegExpMatch match = matches.lastWhere((m) => m.start < textField.selection.start);
        controller.textController.addMention(
          text.substring(match.start, match.end),
          controller.mentionMatches[index],
        );
      } else {
        // If user moved cursor before inserting, reset picker
        controller.emojiScrollController.jumpTo(0);
      }

      controller.mentionSelectedIndex.value = 0;
      controller.mentionMatches.value = <Mentionable>[];

      return true;
    }

    return false;
  }

  /// Show custom mention dialog to edit the display name for a mention.
  /// Called from the context menu (see text_field_component.dart).
  Future<void> showCustomMentionDialog(Mentionable? mention) async {
    if (mention == null) return;

    if (kIsDesktop || kIsWeb) {
      controller.showingOverlays = true;
    }

    final changed = await mention_dialog.showCustomMentionDialog(buildContext, mention.customDisplayName);

    if (kIsDesktop || kIsWeb) {
      controller.showingOverlays = false;
    }

    if (!isNullOrEmpty(changed)) {
      mention.customDisplayName = changed!;
    }
  }
}
