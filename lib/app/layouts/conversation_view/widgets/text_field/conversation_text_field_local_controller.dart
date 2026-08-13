import 'dart:async';

import 'package:flutter/services.dart';
import 'package:get/get.dart';

/// Controller for managing local state within the ConversationTextField
/// This handles typing indicators, emoji/mention matching, draft saving,
/// and text selection tracking - all the internal state that doesn't need
/// to be exposed to the parent ConversationViewController
class ConversationTextFieldLocalController extends GetxController {
  // Timers for debouncing
  Timer? debounceTyping;
  Timer? debounceEmojiSearch;
  Timer? debounceMentionSearch;
  Timer? debounceDraftSave;

  // Previous state tracking
  final RxString oldText = "\n".obs;
  final Rx<TextSelection> oldTextFieldSelection = const TextSelection.collapsed(offset: 0).obs;

  // Attachment picker visibility (moved from setState)
  final RxBool showAttachmentPickerLocal = false.obs;

  @override
  void onClose() {
    cancelAllTimers();
    super.onClose();
  }

  void cancelAllTimers() {
    debounceTyping?.cancel();
    debounceEmojiSearch?.cancel();
    debounceMentionSearch?.cancel();
    debounceDraftSave?.cancel();
  }

  void updateOldText(String newText) {
    oldText.value = newText;
  }

  void updateOldSelection(TextSelection selection) {
    oldTextFieldSelection.value = selection;
  }
}
