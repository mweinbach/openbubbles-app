import 'dart:async';
import 'dart:math';

import 'package:audio_waveforms/audio_waveforms.dart';
import 'package:bluebubbles/app/components/custom/custom_bouncing_scroll_physics.dart';
import 'package:bluebubbles/app/components/custom_text_editing_controllers.dart';
import 'package:bluebubbles/app/layouts/conversation_view/dialogs/custom_mention_dialog.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/media_picker/text_field_attachment_picker.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/text_field/picked_attachments_holder.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/text_field/reply_holder.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/text_field/text_field_suffix.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:file_picker/file_picker.dart' as fp;
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:universal_io/io.dart';
import 'handlers/emoji_autocomplete_handler.dart';
import 'handlers/mention_autocomplete_handler.dart';
import 'handlers/keyboard_shortcut_handler.dart';
import 'handlers/clipboard_paste_handler.dart';

class TextFieldComponent extends StatefulWidget {
  const TextFieldComponent({
    super.key,
    this.subjectTextController,
    required this.textController,
    required this.controller,
    required this.recorderController,
    required this.sendMessage,
    this.focusNode,
    this.initialAttachments = const [],
    this.alwaysShowSend = false,
    this.hideMediaPicker = false,
  });

  final SpellCheckTextEditingController? subjectTextController;
  final MentionTextEditingController textController;
  final ConversationViewController? controller;
  final RecorderController? recorderController;
  final Future<void> Function({String? effect}) sendMessage;
  final FocusNode? focusNode;

  final List<PlatformFile> initialAttachments;

  /// When true the send button is always shown even with no text/attachments.
  /// Used by the chat creator when an existing chat has been resolved.
  final bool alwaysShowSend;

  /// When true, the camera, attachment picker, and GIF icons are hidden.
  /// Used by the chat creator when no existing chat has been matched yet.
  final bool hideMediaPicker;

  @override
  State<StatefulWidget> createState() => TextFieldComponentState();
}

class TextFieldComponentState extends State<TextFieldComponent> {
  late ConversationViewController? controller;
  late final FocusNode? focusNode;
  late final RecorderController? recorderController;
  late final List<PlatformFile> initialAttachments;
  late final MentionTextEditingController textController;
  late final SpellCheckTextEditingController? subjectTextController;
  late final Future<void> Function({String? effect}) sendMessage;

  late final ValueNotifier<bool> isRecordingNotifier;
  EmojiAutocompleteHandler? emojiHandler;
  MentionAutocompleteHandler? mentionHandler;
  KeyboardShortcutHandler? keyboardHandler;
  ClipboardPasteHandler? clipboardHandler;

  TextFieldComponentState() : isRecordingNotifier = ValueNotifier<bool>(false);

  @override
  void initState() {
    super.initState();
    controller = widget.controller;
    focusNode = widget.focusNode;
    recorderController = widget.recorderController;
    initialAttachments = widget.initialAttachments;
    textController = widget.textController;
    subjectTextController = widget.subjectTextController;
    sendMessage = widget.sendMessage;

    // add a listener to recorderController to update isRecordingNotifier
    recorderController?.addListener(() {
      isRecordingNotifier.value = recorderController?.isRecording ?? false;
    });

    _configureHandlers();

    assert(!(subjectTextController == null &&
        !isChatCreator &&
        SettingsSvc.settings.enablePrivateAPI.value &&
        SettingsSvc.settings.privateSubjectLine.value &&
        chat!.isIMessage));
  }

  @override
  void dispose() {
    // dispose of the ValueNotifier when the state is disposed
    isRecordingNotifier.dispose();
    super.dispose();
  }

  @override
  void didUpdateWidget(TextFieldComponent oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.controller != oldWidget.controller) {
      controller = widget.controller;
      _configureHandlers();
    }
  }

  void _configureHandlers() {
    final ctrl = controller;
    if (ctrl == null) {
      emojiHandler = null;
      mentionHandler = null;
      keyboardHandler = null;
      clipboardHandler = null;
      return;
    }

    emojiHandler = EmojiAutocompleteHandler(controller: ctrl, textField: textController);
    mentionHandler = MentionAutocompleteHandler(controller: ctrl, textField: textController, buildContext: context);
    keyboardHandler = KeyboardShortcutHandler(
      controller: ctrl,
      sendMessage: sendMessage,
      subjectTextController: subjectTextController ?? textController,
      messageTextController: textController,
      isChatCreator: focusNode != null,
    );
    clipboardHandler = ClipboardPasteHandler(controller: ctrl);
  }

  bool get iOS => SettingsSvc.settings.skin.value == Skins.iOS;

  bool get material => SettingsSvc.settings.skin.value == Skins.Material;

  bool get samsung => SettingsSvc.settings.skin.value == Skins.Samsung;

  Chat? get chat => controller?.chat;

  bool get isChatCreator => focusNode != null;

  bool _showAttachmentPickerLocal = false;
  bool _pasteHandledOnKeyDown = false;

  @override
  Widget build(BuildContext context) {
    final txtController = controller?.textController ?? textController;
    final subjController = controller?.subjectTextController ?? subjectTextController;
    final showIcons = isChatCreator && !widget.hideMediaPicker && controller != null;
    // Captured here because contextMenuBuilder receives its own `context` that shadows this
    // one and may not have the dark theme properly applied (it's a detached overlay context).
    final outerTheme = Theme.of(context);
    Widget focusWidget = Focus(
      onKeyEvent: (_, ev) => handleKey(_, ev, context, isChatCreator),
      child: ValueListenableBuilder<bool>(
          valueListenable: isRecordingNotifier,
          builder: (context, isRecording, child) {
            final hasBackground = chat != null
                ? (ChatsSvc.getChatState(chat!.guid)?.customBackgroundPath.value?.isNotEmpty == true)
                : false;
            return Container(
              // Border is placed in the foregroundDecoration so it paints on top of
              // child content (ReplyHolder, attachments, etc.) and remains visible
              // at all corners instead of being covered by opaque children.
              foregroundDecoration: iOS
                  ? BoxDecoration(
                      border: Border.fromBorderSide(BorderSide(
                        color: (isRecording & iOS)
                            ? context.theme.colorScheme.primary.withValues(alpha: 1.0)
                            : context.theme.colorScheme.outlineVariant.withValues(alpha: 0.25),
                        width: 1,
                      )),
                      borderRadius: BorderRadius.circular(20),
                    )
                  : null,
              decoration: BoxDecoration(
                color: (!iOS || hasBackground) ? context.theme.colorScheme.surfaceContainerHighest : null,
                borderRadius: BorderRadius.circular(20),
              ),
              clipBehavior: Clip.antiAlias,
              child: AnimatedSize(
                duration: const Duration(milliseconds: 400),
                alignment: Alignment.bottomCenter,
                // easeOutBack overshoots its target size, which works fine in the full
                // conversation view but causes a brief layout overflow in chat creator
                // where the available vertical space is tighter (keyboard is open).
                curve: isChatCreator ? Curves.easeOut : Curves.easeOutBack,
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (widget.controller != null) ReplyHolder(controller: widget.controller!),
                    if (initialAttachments.isNotEmpty || !isChatCreator || widget.controller != null)
                      PickedAttachmentsHolder(
                        controller: widget.controller,
                        textController: txtController,
                        initialAttachments: initialAttachments,
                      ),
                    if (!isChatCreator)
                      Obx(() {
                        if (controller!.pickedAttachments.isNotEmpty && iOS) {
                          return Divider(
                            height: 1.5,
                            thickness: 1.5,
                            color: context.theme.colorScheme.surfaceContainerHighest,
                          );
                        }
                        return const SizedBox.shrink();
                      }),
                    if (!isChatCreator &&
                        SettingsSvc.settings.enablePrivateAPI.value &&
                        SettingsSvc.settings.privateSubjectLine.value &&
                        chat!.isIMessage)
                      TextField(
                        textCapitalization: TextCapitalization.sentences,
                        focusNode: controller!.subjectFocusNode,
                        autocorrect: true,
                        controller: subjController,
                        scrollPhysics: const CustomBouncingScrollPhysics(),
                        style: context.theme.extension<BubbleText>()!.bubbleText.copyWith(fontWeight: FontWeight.bold),
                        keyboardType: TextInputType.multiline,
                        maxLines: 14,
                        minLines: 1,
                        enableIMEPersonalizedLearning: !SettingsSvc.settings.incognitoKeyboard.value,
                        textInputAction: TextInputAction.next,
                        cursorColor: context.theme.colorScheme.primary,
                        cursorHeight: context.theme.extension<BubbleText>()!.bubbleText.fontSize! * 1.25,
                        decoration: InputDecoration(
                          contentPadding: EdgeInsets.all(iOS && !kIsDesktop && !kIsWeb ? 10 : 12.5),
                          isDense: true,
                          isCollapsed: true,
                          hintText: "Subject",
                          enabledBorder: InputBorder.none,
                          border: InputBorder.none,
                          focusedBorder: InputBorder.none,
                          fillColor: Colors.transparent,
                          hintStyle: context.theme
                              .extension<BubbleText>()!
                              .bubbleText
                              .copyWith(color: context.theme.colorScheme.outline, fontWeight: FontWeight.bold),
                          suffixIconConstraints: const BoxConstraints(minHeight: 0),
                        ),
                        onTap: () {
                          HapticFeedback.selectionClick();
                        },
                        onSubmitted: (String value) {
                          controller?.subjectFocusNode.requestFocus();
                        },
                        contentInsertionConfiguration:
                            ContentInsertionConfiguration(onContentInserted: onContentCommit),
                      ),
                    if (!isChatCreator &&
                        SettingsSvc.settings.enablePrivateAPI.value &&
                        SettingsSvc.settings.privateSubjectLine.value &&
                        chat!.isIMessage &&
                        iOS)
                      Divider(
                        height: 1.5,
                        thickness: 1.5,
                        indent: 10,
                        color: context.theme.colorScheme.surfaceContainerHighest,
                      ),
                    Obx(() {
                      final chatTitle =
                          chat == null ? null : (ChatsSvc.getChatState(chat!.guid)?.title.value ?? chat!.getTitle());
                      return TextField(
                        textCapitalization: TextCapitalization.sentences,
                        focusNode: controller?.focusNode ?? focusNode,
                        autocorrect: true,
                        controller: txtController,
                        scrollPhysics: const CustomBouncingScrollPhysics(),
                        style: context.theme.extension<BubbleText>()!.bubbleText,
                        keyboardType: TextInputType.multiline,
                        maxLines: 14,
                        minLines: 1,
                        autofocus: (kIsWeb || kIsDesktop) && !isChatCreator,
                        enableIMEPersonalizedLearning: !SettingsSvc.settings.incognitoKeyboard.value,
                        textInputAction: SettingsSvc.settings.sendWithReturn.value && !kIsWeb && !kIsDesktop
                            ? TextInputAction.send
                            : TextInputAction.newline,
                        cursorColor: context.theme.colorScheme.primary,
                        cursorHeight: context.theme.extension<BubbleText>()!.bubbleText.fontSize! * 1.25,
                        decoration: InputDecoration(
                          contentPadding: EdgeInsets.all(iOS && !kIsDesktop && !kIsWeb ? 10 : 12.5),
                          isDense: true,
                          isCollapsed: true,
                          hintText: isChatCreator
                              ? "New Message"
                              : SettingsSvc.settings.recipientAsPlaceholder.value == true
                                  ? isRecording
                                      ? ""
                                      : chatTitle ?? ""
                                  : (chat!.isTextForwarding && !isRecording)
                                      ? "Text Forwarding"
                                      : (!isRecording) // Only show iMessage when not recording
                                          ? "iMessage"
                                          : "",
                          enabledBorder: InputBorder.none,
                          border: InputBorder.none,
                          focusedBorder: InputBorder.none,
                          filled: (isRecording & iOS),
                          fillColor: (isRecording & iOS)
                              ? context.theme.colorScheme.primary.withValues(alpha: 0.3)
                              : Colors.transparent,
                          hintStyle: context.theme
                              .extension<BubbleText>()!
                              .bubbleText
                              .copyWith(color: context.theme.colorScheme.outline),
                          suffixIconConstraints: const BoxConstraints(minHeight: 0),
                          suffixIcon: samsung && !isChatCreator
                              ? null
                              : Padding(
                                  padding: EdgeInsets.only(right: iOS ? 0.0 : 5.0),
                                  child: TextFieldSuffix(
                                    subjectTextController: subjController,
                                    textController: txtController,
                                    controller: controller,
                                    recorderController: recorderController,
                                    sendMessage: sendMessage,
                                    isChatCreator: isChatCreator,
                                    alwaysShowSend: widget.alwaysShowSend,
                                    hasInitialAttachments: initialAttachments.isNotEmpty,
                                  ),
                                ),
                        ),
                        contextMenuBuilder: (BuildContext context, EditableTextState editableTextState) {
                          final toolbar = txtController.getContextMenuBuilder()?.call(context, editableTextState) ??
                              AdaptiveTextSelectionToolbar.editableText(
                                editableTextState: editableTextState,
                              );

                          // Use outerTheme (captured from the real build context) because the
                          // contextMenuBuilder's own `context` parameter shadows the build context
                          // and may not have the dark theme applied (it's a detached overlay context).
                          return Theme(
                            data: outerTheme.copyWith(
                              cardColor: outerTheme.colorScheme.surfaceContainerHighest,
                              colorScheme: outerTheme.colorScheme.copyWith(
                                surface: outerTheme.colorScheme.surfaceContainerHighest,
                              ),
                            ),
                            child: toolbar,
                          );
                        },
                        onTap: () {
                          HapticFeedback.selectionClick();
                        },
                        onSubmitted: (String value) {
                          controller?.focusNode.requestFocus();
                          if (isNullOrEmpty(value) && (controller?.pickedAttachments.isEmpty ?? false)) return;
                          sendMessage.call();
                        },
                        contentInsertionConfiguration:
                            ContentInsertionConfiguration(onContentInserted: onContentCommit),
                      );
                    }),
                  ],
                ),
              ),
            );
          }),
    );
    // On Windows, wrap with Shortcuts/Actions to intercept Ctrl+V on KeyDown.
    // This prevents Win+V (clipboard history) from double-firing a paste.
    Widget textInput;
    if (!kIsWeb && Platform.isWindows) {
      textInput = Shortcuts(
        shortcuts: {
          const SingleActivator(LogicalKeyboardKey.keyV, control: true, includeRepeats: false): const _PasteIntent(),
        },
        child: Actions(
          actions: {
            _PasteIntent: CallbackAction<_PasteIntent>(onInvoke: (_) {
              _pasteHandledOnKeyDown = true;
              clipboardHandler?.handlePasteEvent();
              return null;
            }),
          },
          child: focusWidget,
        ),
      );
    } else {
      textInput = focusWidget;
    }
    if (!showIcons) return textInput;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
          Padding(
            padding: const EdgeInsets.only(right: 10),
            child: IconButton(
              style: IconButton.styleFrom(
                backgroundColor: context.theme.colorScheme.outline.withValues(alpha: 0.2),
                shape: const CircleBorder(),
                padding: EdgeInsets.zero,
                minimumSize: const Size(36, 36),
                fixedSize: const Size(36, 36),
              ),
              icon: Icon(
                Icons.add,
                color: context.theme.colorScheme.outline,
                size: 22,
              ),
              visualDensity: Platform.isAndroid ? VisualDensity.compact : null,
              onPressed: () async {
                if (kIsDesktop) {
                  final res = await fp.FilePicker.pickFiles(withReadStream: true, allowMultiple: true);
                  if (res == null || res.files.isEmpty || res.files.first.readStream == null) return;
                  for (fp.PlatformFile e in res.files) {
                    if (e.size / 1024000 > 1000) {
                      showSnackbar("Error", "This file is over 1 GB! Please compress it before sending.");
                      continue;
                    }
                    controller!.pickedAttachments.add(PlatformFile(
                      path: e.path,
                      name: e.name,
                      size: e.size,
                      bytes: await readByteStream(e.readStream!),
                    ));
                  }
                } else {
                  if (!_showAttachmentPickerLocal) FocusScope.of(context).unfocus();
                  setState(() => _showAttachmentPickerLocal = !_showAttachmentPickerLocal);
                }
              },
            ),
          ),
          Expanded(child: textInput),
        ]),
        AnimatedSize(
          duration: const Duration(milliseconds: 250),
          curve: Curves.easeIn,
          alignment: Alignment.bottomCenter,
          child: _showAttachmentPickerLocal
              ? AttachmentPicker(controller: controller!)
              : SizedBox(width: NavigationSvc.width(context)),
        ),
      ],
    );
  }

  void onContentCommit(KeyboardInsertedContent content) async {
    // Delegate to clipboard handler
    clipboardHandler?.handleKeyboardInsertedContent(content);
  }

  KeyEventResult handleKey(FocusNode _, KeyEvent ev, BuildContext context, bool isChatCreator) {
    // Windows: Win+V clipboard history sends Ctrl on KeyUp, not KeyDown.
    // The Shortcuts widget handles normal Ctrl+V on KeyDown; this catches Win+V.
    if (!kIsWeb && Platform.isWindows) {
      if (ev is KeyUpEvent && ev.logicalKey == LogicalKeyboardKey.keyV && HardwareKeyboard.instance.isControlPressed) {
        if (_pasteHandledOnKeyDown) {
          _pasteHandledOnKeyDown = false;
          return KeyEventResult.ignored;
        }
        clipboardHandler?.handlePasteEvent();
        return KeyEventResult.handled;
      }
    }

    if (ev is! KeyDownEvent) return KeyEventResult.ignored;

    // Handle clipboard paste (Ctrl+V) on Linux
    if (!kIsWeb &&
        Platform.isLinux &&
        (ev.physicalKey == PhysicalKeyboardKey.keyV || ev.logicalKey == LogicalKeyboardKey.keyV) &&
        HardwareKeyboard.instance.isControlPressed) {
      final handler = clipboardHandler;
      if (handler != null) {
        unawaited(handler.handlePasteEvent());
        return KeyEventResult.handled;
      }
      return KeyEventResult.ignored;
    }

    if (HardwareKeyboard.instance.isMetaPressed ||
        HardwareKeyboard.instance.isControlPressed ||
        HardwareKeyboard.instance.isAltPressed) {
      return KeyEventResult.ignored;
    }

    final activeTextField = controller != null
        ? (controller!.subjectFocusNode.hasPrimaryFocus
            ? controller!.subjectTextController
            : controller!.textController)
        : ((subjectTextController != null && (subjectTextController!.focusNode?.hasPrimaryFocus ?? false))
            ? subjectTextController!
            : textController);

    // Cursor at start: move focus to the header back button / previous field
    if (ev.logicalKey == LogicalKeyboardKey.arrowLeft) {
      final selection = activeTextField.selection;
      final isCursorAtStart = selection.isValid && selection.isCollapsed && selection.extentOffset <= 0;

      if (isCursorAtStart) {
        if (controller != null) {
          // Move to the attachment (+) button immediately left of the field; fall back to
          // the header back button when the icon bar isn't present (e.g. chat creator).
          if (controller!.attachmentPickerFocusNode.context != null) {
            controller!.attachmentPickerFocusNode.requestFocus();
          } else {
            controller!.headerBackFocusNode.requestFocus();
          }
          return KeyEventResult.handled;
        }
        if (FocusScope.of(context).focusInDirection(TraversalDirection.left)) {
          return KeyEventResult.handled;
        }
      }
    }

    // Cursor at end: move focus to the next field
    if (ev.logicalKey == LogicalKeyboardKey.arrowRight) {
      final selection = activeTextField.selection;
      final isCursorAtEnd =
          selection.isValid && selection.isCollapsed && selection.extentOffset >= activeTextField.text.length;

      if (isCursorAtEnd && FocusScope.of(context).focusInDirection(TraversalDirection.right)) {
        return KeyEventResult.handled;
      }
    }

    if (isChatCreator) {
      if (ev.logicalKey == LogicalKeyboardKey.enter && !HardwareKeyboard.instance.isShiftPressed) {
        sendMessage();
        return KeyEventResult.handled;
      }
      return KeyEventResult.ignored;
    }

    // Calculate movement indices for emoji/mention pickers
    int maxShown = context.height / 3 ~/ 40;
    int upMovementIndex = maxShown ~/ 3;
    int downMovementIndex = maxShown * 2 ~/ 3;

    // Check which key was pressed
    final isEscapeKey = ev.logicalKey == LogicalKeyboardKey.escape;
    final isTabOrEnter = ev.logicalKey == LogicalKeyboardKey.tab || ev.logicalKey == LogicalKeyboardKey.enter;
    final isDownArrow = ev.logicalKey == LogicalKeyboardKey.arrowDown;
    final isUpArrow = ev.logicalKey == LogicalKeyboardKey.arrowUp;

    // Try emoji handler first (emoji matches have priority over mentions if both active)
    bool handledByEmoji = emojiHandler?.handleKeyEvent(
          logicalKey: ev.logicalKey,
          isEscapeKey: isEscapeKey,
          isTabOrEnter: isTabOrEnter,
          isDownArrow: isDownArrow,
          isUpArrow: isUpArrow,
          maxShown: maxShown,
          upMovementIndex: upMovementIndex,
          downMovementIndex: downMovementIndex,
        ) ??
        false;
    if (handledByEmoji) return KeyEventResult.handled;

    // Try mention handler
    bool handledByMention = mentionHandler?.handleKeyEvent(
          logicalKey: ev.logicalKey,
          isEscapeKey: isEscapeKey,
          isTabOrEnter: isTabOrEnter,
          isDownArrow: isDownArrow,
          isUpArrow: isUpArrow,
          maxShown: maxShown,
          upMovementIndex: upMovementIndex,
          downMovementIndex: downMovementIndex,
        ) ??
        false;
    if (handledByMention) return KeyEventResult.handled;

    // Up arrow: when the cursor is at the very start, hand focus to the most recent message
    // (so the user can keyboard-navigate up into the conversation), otherwise edit the last
    // sent message. Runs before the keyboard handler so editing reuses the mention-aware
    // controller and preserves the message's annotations.
    if (ev.logicalKey == LogicalKeyboardKey.arrowUp) {
      final selection = activeTextField.selection;
      final atStart = selection.isValid && selection.isCollapsed && selection.extentOffset == 0;
      // At the start of the field, go up to the staged attachment delete buttons first.
      if (atStart && (controller?.pickedAttachments.isNotEmpty ?? false)) {
        final deletes = controller!.pickedAttachmentsListNode.traversalDescendants;
        if (deletes.isNotEmpty) {
          deletes.first.requestFocus();
          return KeyEventResult.handled;
        }
      }
      if (atStart && controller?.bottomMessageFocusNode != null) {
        controller!.bottomMessageFocusNode!.requestFocus();
        return KeyEventResult.handled;
      }
      if (chat != null &&
          controller != null &&
          controller!.lastFocusedTextController.text.isEmpty &&
          SettingsSvc.settings.editLastSentMessageOnUpArrow.value &&
          SettingsSvc.serverDetails.isMinVentura &&
          SettingsSvc.serverDetails.supportsEditAndUnsend) {
        final message = MessagesSvc(chat!.guid).mostRecentSent;
        if (message != null) {
          final messageController = MessagesSvc(chat!.guid).getOrCreateState(message);
          final isSending = messageController.isSending.value;
          if (!isSending) {
            final parts = messageController.parts;
            final part = parts.where((p) => p.text?.isNotEmpty ?? false).lastOrNull;
            if (part != null) {
              final FocusNode? node = kIsDesktop || kIsWeb ? FocusNode() : null;
              controller!.editing.add(MessageEditEntry(
                  message: message,
                  part: part,
                  controller: MentionTextEditingController(
                    text: part.text!,
                    focusNode: node,
                  )..annotations = List<Annotation>.from(part.annotations)));
              node?.requestFocus();
              return KeyEventResult.handled;
            }
          }
        }
      }
    }

    // Try keyboard shortcut handler (escape, enter, etc.)
    KeyEventResult shortcutResult = keyboardHandler?.handleKeyEvent(ev) ?? KeyEventResult.ignored;
    if (shortcutResult == KeyEventResult.handled) return KeyEventResult.handled;

    // Escape: clear picker state if not handled by handlers above
    if (isEscapeKey) {
      final ctrl = controller;
      if (ctrl == null) return KeyEventResult.ignored;

      if (ctrl.showEmojiPicker.value) {
        ctrl.showEmojiPicker.value = false;
        return KeyEventResult.handled;
      }
      if (ctrl.replyToMessage != null) {
        ctrl.replyToMessage = null;
        return KeyEventResult.handled;
      }
      if (ctrl.pickedAttachments.isNotEmpty) {
        ctrl.pickedAttachments.clear();
        return KeyEventResult.handled;
      }
    }

    return KeyEventResult.ignored;
  }

  /// Convert a file stream to bytes
  Future<Uint8List> readByteStream(Stream<List<int>> stream) async {
    final chunks = <Uint8List>[];
    await for (final chunk in stream) {
      chunks.add(Uint8List.fromList(chunk));
    }
    return Uint8List.fromList(chunks.expand((e) => e).toList());
  }
}

class _PasteIntent extends Intent {
  const _PasteIntent();
}
