import 'dart:async';
import 'dart:math';

import 'package:audio_waveforms/audio_waveforms.dart';
import 'package:bluebubbles/app/components/custom_text_editing_controllers.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/media_picker/text_field_attachment_picker.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/send_animation.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/text_field/conversation_text_field_local_controller.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/text_field/helpers/text_field_match_helper.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/text_field/text_field_component.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/text_field/text_field_emoji_picker_section.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/text_field/text_field_icon_bar.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/text_field/text_field_recording_overlay.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/text_field/text_field_suffix.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/services/ui/chat/send_data.dart';
import 'package:collection/collection.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path/path.dart' hide context;
import 'package:permission_handler/permission_handler.dart';
import 'package:universal_io/io.dart';

export 'text_field_component.dart' show TextFieldComponent, TextFieldComponentState;

class ConversationTextField extends CustomStateful<ConversationViewController> {
  const ConversationTextField({
    super.key,
    required super.parentController,
  });

  static ConversationTextFieldState? of(BuildContext context) {
    return context.findAncestorStateOfType<ConversationTextFieldState>();
  }

  @override
  ConversationTextFieldState createState() => ConversationTextFieldState();
}

class ConversationTextFieldState extends CustomState<ConversationTextField, void, ConversationViewController>
    with TickerProviderStateMixin {
  final recorderController = kIsWeb ? null : RecorderController();
  final localController = ConversationTextFieldLocalController();
  final _emojiScrollController = ScrollController();
  final attachmentPicker = GlobalKey<AttachmentPickerState>();

  Chat get chat => controller.chat;

  String get chatGuid => chat.guid;

  bool get showAttachmentPicker => controller.showAttachmentPicker.value;

  late final double emojiPickerHeight = max(256, context.height * 0.4);
  late final emojiColumns =
      NavigationSvc.width(context) ~/ 56; // Intentionally not responsive to prevent rebuilds when resizing
  RxBool get showEmojiPicker => controller.showEmojiPicker;

  final proxyController = TextEditingController();

  @override
  void initState() {
    super.initState();
    forceDelete = false;

    // Load the initial chat drafts
    getDrafts();

    // Save state
    localController.oldTextFieldSelection.value = controller.textController.selection;

    if (controller.fromChatCreator || (SettingsSvc.settings.autoOpenKeyboard.value && !controller.fromSearchResult)) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (!mounted) return;
        controller.focusNode.requestFocus();
      });
    }

    controller.focusNode.addListener(() => focusListener(false));
    controller.subjectFocusNode.addListener(() => focusListener(true));

    controller.textController.addListener(() => textListener(false));
    controller.subjectTextController.addListener(() => textListener(true));

    if (kIsDesktop || kIsWeb) {
      proxyController.addListener(() {
        if (proxyController.text.isEmpty) return;
        String emoji = proxyController.text;
        proxyController.clear();
        TextEditingController realController =
            controller.editing.lastOrNull?.controller ?? controller.lastFocusedTextController;
        String text = realController.text;
        TextSelection selection = realController.selection;

        if (realController is SpellCheckTextEditingController) {
          realController.insert(selection, emoji);
          realController.selection = TextSelection.collapsed(offset: selection.start + emoji.length);
        } else {
          realController.text = text.substring(0, selection.start) + emoji + text.substring(selection.end);
          realController.selection = TextSelection.collapsed(offset: selection.start + emoji.length);
        }

        (controller.editing.lastOrNull?.controller.focusNode ?? controller.lastFocusedNode).requestFocus();
      });
    }
  }

  void getDrafts() async {
    getTextDraft();
    await getAttachmentDrafts();
  }

  void getTextDraft({String? text}) {
    // Skip restoring a draft when navigating from the chat creator — the send path
    // clears both the text controller and the persisted draft before navigating, so
    // any non-empty value here would be a stale artifact on the CVC's chat object.
    if (controller.fromChatCreator) return;
    // Read from ChatState — it is the source of truth and is always up-to-date,
    // even before the async DB write from a previous session has completed.
    final incomingText = text ?? ChatsSvc.getChatState(chatGuid)?.textFieldText.value ?? chat.textFieldText;
    if (incomingText != null && incomingText.isNotEmpty && incomingText != controller.textController.text) {
      if (chat.textFieldAnnotations != null) {
        controller.textController.restoreAnnotations(incomingText, chat.textFieldAnnotations!);
      } else {
        controller.textController.text = incomingText;
      }
    }
  }

  Future<void> getAttachmentDrafts({List<String> attachments = const []}) async {
    // Read from ChatState — it is the source of truth and is always up-to-date.
    // Fall back to chat.textFieldAttachments for the first load after a cold start
    // (before ChatState has been updated by any setChatTextFieldAttachments call).
    final incomingAttachments = attachments.isNotEmpty
        ? attachments
        : (ChatsSvc.getChatState(chatGuid)?.textFieldAttachments.toList() ?? chat.textFieldAttachments);
    final currentPicked = controller.pickedAttachments.map((element) => element.path).toList();
    if (incomingAttachments.any((element) => !currentPicked.contains(element))) {
      controller.pickedAttachments.clear();
    }

    for (String s in incomingAttachments) {
      final file = File(s);
      if (!currentPicked.contains(s) && await file.exists()) {
        final bytes = await file.readAsBytes();
        controller.pickedAttachments.add(PlatformFile(
          name: basename(file.path),
          bytes: bytes,
          size: bytes.length,
          path: s,
        ));
      }
    }
  }

  void focusListener(bool subject) async {
    final _focusNode = subject ? controller.subjectFocusNode : controller.focusNode;
    // OPTIMIZATION: Only update if state actually needs to change
    if (_focusNode.hasFocus && controller.showAttachmentPicker.value) {
      controller.showAttachmentPicker.value = false;
    }
  }

  void textListener(bool subject) {
    // OPTIMIZATION: Debounce draft saving to avoid database writes on every keystroke
    if (!subject) {
      localController.debounceDraftSave?.cancel();
      localController.debounceDraftSave = Timer(const Duration(milliseconds: 500), () {
        unawaited(ChatsSvc.setChatTextFieldText(chat, controller.textController.text));
        chat.textFieldAnnotations =
            controller.textController.text.isNotEmpty ? controller.textController.saveAnnotations() : null;
        chat.save(updateTextFieldText: true, updateTextFieldAnnotations: true);
      });
    }

    // typing indicators and text change detection
    final newText = "${controller.subjectTextController.text}\n${controller.textController.text}";

    // OPTIMIZATION: Early exit if only selection changed (cursor moved), not text content
    if (newText == localController.oldText.value) {
      // Text unchanged, only update selection tracking for mentions
      if (!subject) {
        localController.oldTextFieldSelection.value = controller.textController.selection;
      }
      return;
    }

    if (!subject) {
      localController.oldTextFieldSelection.value = controller.textController.selection;
    }

    localController.debounceTyping?.cancel();
    localController.oldText.value = newText;
    if (newText.trim().isNotEmpty) controller.triggerTypingIndicator();

    // OPTIMIZATION: Only run expensive emoji/mention matching if relevant characters present
    final _controller = subject ? controller.subjectTextController : controller.textController;
    final newEmojiText = _controller.text;

    // Debounce emoji search to avoid running regex on every keystroke
    if (newEmojiText.contains(":")) {
      localController.debounceEmojiSearch?.cancel();
      localController.debounceEmojiSearch = Timer(const Duration(milliseconds: 150), () {
        TextFieldMatchHelper.processEmojiMatches(controller, _controller, subject);
      });
    } else {
      localController.debounceEmojiSearch?.cancel();
      controller.emojiMatches.value = [];
      controller.emojiSelectedIndex.value = 0;
    }

    // Debounce mention search to avoid running regex on every keystroke
    if (SettingsSvc.settings.enablePrivateAPI.value && !subject && newEmojiText.contains("@")) {
      localController.debounceMentionSearch?.cancel();
      localController.debounceMentionSearch = Timer(const Duration(milliseconds: 150), () {
        TextFieldMatchHelper.processMentionMatches(controller, _controller, subject);
      });
    } else {
      localController.debounceMentionSearch?.cancel();
      controller.mentionMatches.value = [];
      controller.mentionSelectedIndex.value = 0;
    }
  }

  @override
  void dispose() {
    final draftText = controller.textController.text.trim().isNotEmpty ? controller.textController.text : '';
    final draftAttachments = controller.pickedAttachments.where((e) => e.path != null).map((e) => e.path!).toList();
    chat.textFieldAnnotations = draftText.isNotEmpty ? controller.textController.saveAnnotations() : null;
    // Update ChatState synchronously and fire DB save in the background.
    unawaited(ChatsSvc.setChatTextFieldText(chat, draftText));
    unawaited(ChatsSvc.setChatTextFieldAttachments(chat, draftAttachments));
    chat.save(updateTextFieldText: true, updateTextFieldAnnotations: true, updateTextFieldAttachments: true);

    controller.focusNode.dispose();
    controller.subjectFocusNode.dispose();
    controller.textController.dispose();
    controller.subjectTextController.dispose();
    recorderController?.dispose();
    _emojiScrollController.dispose();
    controller.showAttachmentPicker.value = false;
    localController.cancelAllTimers();
    Get.delete<ConversationTextFieldLocalController>();
    if (chat.autoSendTypingIndicators ?? SettingsSvc.settings.privateSendTypingIndicators.value) {
      BackendSvc.stoppedTyping(chat);
    }

    super.dispose();
  }

  Future<void> sendMessage({String? effect}) async {
    final text = controller.textController.text;
    if (text.isEmpty &&
        controller.subjectTextController.text.isEmpty &&
        !SettingsSvc.settings.privateAPIAttachmentSend.value) {
      if (controller.replyToMessage != null) {
        return showSnackbar("Error", "Turn on Private API Attachment Send to send replies with media!");
      } else if (effect != null) {
        return showSnackbar("Error", "Turn on Private API Attachment Send to send effects with media!");
      }
    }
    if (effect == null && SettingsSvc.settings.enablePrivateAPI.value) {
      final cleansed = text.replaceAll("!", "").toLowerCase();
      switch (cleansed) {
        case "congratulations":
        case "congrats":
          effect = effectMap["confetti"];
          break;
        case "happy birthday":
          effect = effectMap["balloons"];
          break;
        case "happy new year":
          effect = effectMap["fireworks"];
          break;
        case "happy chinese new year":
        case "happy lunar new year":
          effect = effectMap["celebration"];
          break;
        case "pew pew":
          effect = effectMap["lasers"];
          break;
      }
    }
    // Always return focus to the message field after sending. The send button (touch InkWell
    // or D-pad DpadFocusable) pulls focus to itself when activated, so re-request focus on the
    // next frame — after that steal settles — so it reliably lands back on the text field
    // regardless of timing. Scheduling before the async send keeps the keyboard from dropping.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) controller.focusNode.requestFocus();
    });

    await controller.send(SendData(
      attachments: controller.pickedApp.value?.$1 != null
          ? [controller.pickedApp.value!.$1!]
          : controller.pickedAttachments.toList(),
      text: text,
      annotations: controller.buildAttributedBody(),
      subject: controller.subjectTextController.text,
      replyGuid: controller.replyToMessage?.message.threadOriginatorGuid ?? controller.replyToMessage?.message.guid,
      replyPart: controller.replyToMessage?.partIndex,
      effectId: effect,
      payloadData: controller.pickedApp.value?.$2,
      schedule: controller.scheduledDate.value,
    ));
    controller.pickedApp.value = null;
    controller.pickedAttachments.clear();
    controller.textController.clear();
    controller.subjectTextController.clear();
    controller.replyToMessage = null;
    controller.scheduledDate.value = null;
    controller.clearTypingState();
    // Clear the draft now that the message has been sent.
    unawaited(ChatsSvc.setChatTextFieldText(chat, ''));
    unawaited(ChatsSvc.setChatTextFieldAttachments(chat, []));
    chat.textFieldAnnotations = null;
    chat.save(updateTextFieldText: true, updateTextFieldAnnotations: true, updateTextFieldAttachments: true);
  }

  Future<void> openFullCamera({String type = 'camera'}) async {
    bool granted = (await Permission.camera.request()).isGranted;
    if (!granted) {
      showSnackbar("Error", "Camera access was denied!");
      return;
    }

    if (type == 'video') {
      final micGranted = (await Permission.microphone.request()).isGranted;
      if (!micGranted) {
        showSnackbar("Error", "Microphone access was denied!");
        return;
      }
    }

    final XFile? file;
    if (type == 'video') {
      file = await ImagePicker().pickVideo(source: ImageSource.camera);
    } else {
      file = await ImagePicker().pickImage(source: ImageSource.camera);
    }

    if (file != null) {
      controller.pickedAttachments.add(PlatformFile(
        path: file.path,
        name: file.path.split('/').last,
        size: await file.length(),
        bytes: await file.readAsBytes(),
      ));
    }
  }

  @override
  Widget build(BuildContext context) {
    // Track keyboard height directly so only this widget — not the entire
    // Scaffold body — relays out on each keyboard animation frame.
    // (Scaffold.resizeToAvoidBottomInset is false on ConversationView.)
    final keyboardInset = MediaQuery.viewInsetsOf(context).bottom;
    return SafeArea(
      left: false,
      right: false,
      top: false,
      child: Padding(
        padding: EdgeInsets.only(bottom: 10.0 + keyboardInset, top: 10.0),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
              TextFieldIconBar(
                controller: controller,
                localController: localController,
                onToggleAttachmentPicker: () {
                  final pickerState = attachmentPicker.currentState;
                  if (pickerState?.currentApp != null) {
                    pickerState!.setState(() {
                      pickerState.currentApp = null;
                    });
                    return;
                  }

                  if (!showAttachmentPicker) {
                    controller.focusNode.unfocus();
                    controller.subjectFocusNode.unfocus();
                  }
                  controller.showAttachmentPicker.value = !showAttachmentPicker;
                },
              ),
              Expanded(
                child: Stack(
                  alignment: Alignment.centerLeft,
                  clipBehavior: Clip.none,
                  children: [
                    TextFieldComponent(
                      key: controller.textFieldKey,
                      subjectTextController: controller.subjectTextController,
                      textController: controller.textController,
                      controller: controller,
                      recorderController: recorderController,
                      sendMessage: sendMessage,
                    ),
                    if (!kIsWeb)
                      Positioned(
                        top: 0,
                        bottom: 0,
                        child: TextFieldRecordingOverlay(
                          controller: controller,
                          recorderController: recorderController,
                        ),
                      ),
                    SendAnimation(parentController: controller),
                  ],
                ),
              ),
              if (iOS || material) const SizedBox(width: 10),
              if (samsung)
                Padding(
                  padding: const EdgeInsets.only(right: 5.0),
                  child: TextFieldSuffix(
                    subjectTextController: controller.subjectTextController,
                    textController: controller.textController,
                    controller: controller,
                    recorderController: recorderController,
                    sendMessage: sendMessage,
                  ),
                ),
            ]),
            Builder(builder: (context) {
              // Capture width outside the Obx lambda so the reactive builder does not
              // register a MediaQuery.of dependency and rebuild on keyboard animation frames.
              // sizeOf only notifies on actual display-size changes (rotation / resize).
              final pickerWidth = MediaQuery.sizeOf(context).width;
              return Obx(() => AnimatedSize(
                    duration: const Duration(milliseconds: 250),
                    curve: Curves.easeIn,
                    alignment: Alignment.bottomCenter,
                    child: !showAttachmentPicker
                        ? SizedBox(width: pickerWidth)
                        : Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const SizedBox(height: 8),
                              AttachmentPicker(
                                key: attachmentPicker,
                                controller: controller,
                              ),
                            ],
                          ),
                  ));
            }),
            TextFieldEmojiPickerSection(
              controller: controller,
              proxyController: proxyController,
              emojiScrollController: _emojiScrollController,
              emojiPickerHeight: emojiPickerHeight,
              emojiColumns: emojiColumns,
            ),
          ],
        ),
      ),
    );
  }
}
