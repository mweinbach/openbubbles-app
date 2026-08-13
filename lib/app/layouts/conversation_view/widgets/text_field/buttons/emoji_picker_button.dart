import 'package:bluebubbles/services/ui/chat/conversation_view_controller.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// Widget for the emoji picker button (desktop/web only)
class EmojiPickerButton extends StatelessWidget {
  final ConversationViewController controller;

  const EmojiPickerButton({
    super.key,
    required this.controller,
  });

  @override
  Widget build(BuildContext context) {
    if (!kIsDesktop && !kIsWeb) {
      return const SizedBox.shrink();
    }

    return IconButton(
      icon: Icon(
        context.iOS ? CupertinoIcons.smiley_fill : Icons.emoji_emotions,
        color: context.theme.colorScheme.outline,
        size: 28,
      ),
      onPressed: () {
        controller.showEmojiPicker.value = !controller.showEmojiPicker.value;
        (controller.editing.lastOrNull?.controller.focusNode ?? controller.lastFocusedNode).requestFocus();
      },
    );
  }
}
