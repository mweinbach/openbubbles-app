import 'package:audio_waveforms/audio_waveforms.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/effects/send_effect_picker.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/attachment/audio_player.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/text_field/send_button.dart';
import 'package:bluebubbles/app/wrappers/cupertino_icon_wrapper.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/services/ui/chat/send_data.dart';
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:multi_value_listenable_builder/multi_value_listenable_builder.dart';
import 'package:path/path.dart';
import 'package:record/record.dart';
import 'package:system_info2/system_info2.dart';
import 'package:universal_io/io.dart';

class TextFieldSuffix extends StatefulWidget {
  const TextFieldSuffix({
    super.key,
    required this.subjectTextController,
    required this.textController,
    required this.controller,
    required this.recorderController,
    required this.sendMessage,
    this.isChatCreator = false,
    this.alwaysShowSend = false,
    this.hasInitialAttachments = false,
  });

  final TextEditingController? subjectTextController;
  final TextEditingController textController;
  final ConversationViewController? controller;
  final RecorderController? recorderController;
  final Future<void> Function({String? effect}) sendMessage;
  final bool isChatCreator;

  /// When true the send button is always shown, regardless of whether there
  /// is text or attachments. Used in the chat creator when an existing chat
  /// has been resolved so the user can open the conversation without typing.
  final bool alwaysShowSend;

  /// Mirrors `initialAttachments.isNotEmpty` from the parent TextFieldComponent.
  /// Used in isChatCreator mode to show the send button when attachments are
  /// pre-loaded (e.g. shared from another app) but no text has been typed yet.
  final bool hasInitialAttachments;

  @override
  State<StatefulWidget> createState() => _TextFieldSuffixState();
}

class _TextFieldSuffixState extends State<TextFieldSuffix> with ThemeHelpers {
  final AudioRecorder audioRecorder = AudioRecorder();

  // Cache these values at init to avoid repeated platform checks
  late final bool _isWeb = kIsWeb;
  late final bool _isDesktop = kIsDesktop;
  late final bool _isLinuxArm64 =
      kIsDesktop && Platform.isLinux && SysInfo.kernelArchitecture == ProcessorArchitecture.arm64;

  bool get isChatCreator => widget.isChatCreator;
  bool get alwaysShowSend => widget.alwaysShowSend;

  void deleteAudioRecording(String path) {
    File(path).delete();
  }

  @override
  Widget build(BuildContext context) {
    return MultiValueListenableBuilder(
      valueListenables: [widget.textController, widget.subjectTextController].nonNulls.toList(),
      builder: (context, values, _) {
        // Extract text checks outside Obx - these are already reactive via MultiValueListenableBuilder
        final hasText = widget.textController.text.isNotEmpty;
        final hasSubject = widget.subjectTextController?.text.isNotEmpty ?? false;

        // For chat creator, we don't have a controller, so skip Obx.
        // Only show the send button when there is actually content to send;
        // otherwise the button is hidden entirely to avoid a no-op tap.
        if (isChatCreator) {
          // When a controller is present (existing chat resolved), use Obx to
          // reactively watch pickedAttachments and respect alwaysShowSend.
          if (widget.controller != null) {
            return Obx(() {
              final hasAttachments = widget.controller!.pickedAttachments.isNotEmpty;
              final canSend = alwaysShowSend || hasText || hasAttachments || widget.hasInitialAttachments;
              if (!canSend) return const SizedBox.shrink();
              return Padding(
                padding: const EdgeInsets.all(3.0),
                child: SendButton(
                  sendMessage: widget.sendMessage,
                  onLongPress: () {},
                ),
              );
            });
          }
          // No controller — check static values only.
          final canSendInCreator = hasText || widget.hasInitialAttachments;
          if (!canSendInCreator) return const SizedBox.shrink();
          return Padding(
            padding: const EdgeInsets.all(3.0),
            child: SendButton(
              sendMessage: widget.sendMessage,
              onLongPress: () {},
            ),
          );
        }

        return Obx(() {
          // Only reactive values in Obx scope - controller is guaranteed non-null here
          final hasAttachments = widget.controller!.pickedAttachments.isNotEmpty;
          final hasPickedApp = widget.controller!.pickedApp.value != null;
          final showRecording = widget.controller!.showRecording.value && widget.recorderController != null;
          final canSend = alwaysShowSend || hasText || hasSubject || hasAttachments || hasPickedApp;

          return Padding(
            padding: const EdgeInsets.all(3.0),
            child: AnimatedCrossFade(
              crossFadeState: canSend && !showRecording ? CrossFadeState.showSecond : CrossFadeState.showFirst,
              duration: const Duration(milliseconds: 150),
              firstChild: _RecordingButton(
                isWeb: _isWeb,
                isDesktop: _isDesktop,
                isLinuxArm64: _isLinuxArm64,
                isChatCreator: isChatCreator,
                alwaysShowSend: alwaysShowSend,
                showRecording: showRecording,
                controller: widget.controller,
                recorderController: widget.recorderController,
                audioRecorder: audioRecorder,
                onDeleteRecording: deleteAudioRecording,
              ),
              secondChild: SendButton(
                sendMessage: widget.sendMessage,
                previousFocusNode: widget.controller?.lastFocusedNode,
                onLongPress: () {
                  if (widget.controller!.scheduledDate.value != null || !widget.controller!.chat.isIMessage) return;
                  sendEffectAction(
                    context,
                    widget.controller!,
                    widget.controller!.buildAttributedBody(),
                    widget.subjectTextController?.text.trim() ?? "",
                    widget.controller!.replyToMessage?.message.guid,
                    widget.controller!.replyToMessage?.partIndex,
                    widget.controller!.chat.guid,
                    widget.sendMessage,
                    widget.controller!.scheduledDate.value,
                  );
                },
              ),
            ),
          );
        });
      },
    );
  }

  @override
  void dispose() {
    audioRecorder.dispose();

    super.dispose();
  }
}

/// Extracted recording button to reduce Obx rebuild scope and prevent
/// unnecessary rebuilds of the complex recording UI
class _RecordingButton extends StatelessWidget {
  const _RecordingButton({
    required this.isWeb,
    required this.isDesktop,
    required this.isLinuxArm64,
    required this.isChatCreator,
    required this.alwaysShowSend,
    required this.showRecording,
    required this.controller,
    required this.recorderController,
    required this.audioRecorder,
    required this.onDeleteRecording,
  });

  final bool isWeb;
  final bool isDesktop;
  final bool isLinuxArm64;
  final bool isChatCreator;
  final bool alwaysShowSend;
  final bool showRecording;
  final ConversationViewController? controller;
  final RecorderController? recorderController;
  final AudioRecorder audioRecorder;
  final Function(String) onDeleteRecording;

  @override
  Widget build(BuildContext context) {
    if (isWeb) {
      return const SizedBox(height: 32, width: 32);
    }

    return Obx(() {
      final isIOS = SettingsSvc.settings.skin.value == Skins.iOS;

      return CallbackShortcuts(
        bindings: {
          const SingleActivator(LogicalKeyboardKey.arrowLeft): () => controller?.lastFocusedNode.requestFocus(),
          const SingleActivator(LogicalKeyboardKey.enter): () => _toggleRecording(context),
          const SingleActivator(LogicalKeyboardKey.select): () => _toggleRecording(context),
          const SingleActivator(LogicalKeyboardKey.space): () => _toggleRecording(context),
        },
        child: TextButton(
          focusNode: controller?.recordButtonFocusNode,
          style: TextButton.styleFrom(
            backgroundColor: !isIOS || (isIOS && !isChatCreator && !showRecording)
                ? null
                : !isChatCreator && !showRecording
                    ? context.theme.colorScheme.outline
                    : context.theme.colorScheme.primary.withValues(alpha: 0.4),
            shape: const CircleBorder(),
            padding: const EdgeInsets.all(0),
            maximumSize: isDesktop ? const Size(40, 40) : const Size(32, 32),
            minimumSize: isDesktop ? const Size(40, 40) : const Size(32, 32),
            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
          ),
          child: isLinuxArm64
              ? const SizedBox(height: 40)
              : !isChatCreator && !showRecording
                  ? CupertinoIconWrapper(
                      icon: Icon(
                        isIOS ? CupertinoIcons.waveform : Icons.mic_none,
                        color: isIOS ? context.theme.colorScheme.outline : context.theme.colorScheme.onSurfaceVariant,
                        size: isIOS ? 24 : 20,
                      ),
                    )
                  : CupertinoIconWrapper(
                      icon: Icon(
                        isIOS ? CupertinoIcons.stop_fill : Icons.stop_circle,
                        color: isIOS ? context.theme.colorScheme.primary : context.theme.colorScheme.onSurfaceVariant,
                        size: 15,
                      ),
                    ),
          onPressed: () async => _toggleRecording(context),
        ),
      );
    });
  }

  Future<void> _toggleRecording(BuildContext context) async {
    if (controller == null) return;
    // Dumb (D-pad) devices use the desktop recording path (the `record` package) instead of
    // audio_waveforms, which has been unreliable on mobile since the 2.x upgrade.
    final useRecordPackage = isDesktop || SettingsSvc.settings.isDumb.value;
    controller!.showRecording.toggle();

    if (controller!.showRecording.value) {
      if (useRecordPackage) {
        // Pre-check without prompting so we know whether the system mic dialog will appear.
        final alreadyGranted = await audioRecorder.hasPermission(request: false);
        if (!alreadyGranted) {
          // The prompt will appear and steal window focus; a same-frame requestFocus won't stick.
          // Re-focus the record button only once the app regains focus (the dialog has closed),
          // and tell the lifecycle service not to yank focus back to the text field on that resume.
          controller!.suppressResumeRefocus = true;
          var refocused = false;
          AppLifecycleListener? lifecycle;
          lifecycle = AppLifecycleListener(onResume: () {
            if (refocused) return;
            refocused = true;
            controller?.recordButtonFocusNode.requestFocus();
            lifecycle?.dispose();
          });
          if (!await audioRecorder.hasPermission()) {
            controller!.showRecording.value = false; // revert the toggle — we're not recording
            showSnackbar("Error", "Microphone access was denied!");
            return; // onResume still fires after the dialog closes and disposes the listener
          }
        } else {
          // Already granted — no prompt, so just keep focus on the button on the next frame.
          WidgetsBinding.instance.addPostFrameCallback((_) => controller?.recordButtonFocusNode.requestFocus());
        }
        final temp = File(join(
          FilesystemSvc.appDocDir.path,
          "temp",
          "recorder",
          "${controller!.chat.guid.characters.where((c) => c.isAlphabetOnly || c.isNumericOnly).join()}.m4a",
        ));
        temp.createSync(recursive: true);
        await audioRecorder.start(const RecordConfig(bitRate: 320000), path: temp.path);
        return;
      }
      await recorderController!.record(
        recorderSettings: const RecorderSettings(
          sampleRate: 44100,
          bitRate: 320000,
        ),
      );
      return;
    }

    late final String? path;
    late final PlatformFile file;
    final previewFocusNode = FocusNode();
    final discardFocusNode = FocusNode();
    final sendFocusNode = FocusNode();

    if (useRecordPackage) {
      path = await audioRecorder.stop();
      if (path == null) return;
      final _file = File(path);
      file = PlatformFile(
        name: basename(_file.path),
        path: _file.path,
        bytes: await _file.readAsBytes(),
        size: await _file.length(),
      );
    } else {
      path = await recorderController!.stop();
      if (path == null) return;
      final _file = File(path);
      file = PlatformFile(
        name: basename(_file.path),
        path: _file.path,
        bytes: await _file.readAsBytes(),
        size: await _file.length(),
      );
    }

    // Autofocus the play/pause button once the dialog is on screen. Down/right from it moves to
    // the Discard/Send buttons via the AudioPlayer's nextFocusNode wiring, so focus isn't trapped.
    WidgetsBinding.instance.addPostFrameCallback((_) => previewFocusNode.requestFocus());

    // While this dialog is up, mark an overlay so the lifecycle service doesn't pull focus back to
    // the text field when the window regains focus (e.g. after the system volume overlay appears).
    controller!.showingOverlays = true;
    try {
      await showDialog(
        context: context,
        barrierDismissible: false,
        builder: (BuildContext context) {
          final focusedBackground = context.theme.colorScheme.outline.withValues(alpha: 0.2);
          return AlertDialog(
            backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
            title: Text("Send it?", style: context.theme.textTheme.titleLarge),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  "Review your audio snippet before sending it",
                  style: context.theme.textTheme.bodyLarge,
                ),
                Container(height: 10.0),
                ConstrainedBox(
                  constraints: BoxConstraints(maxWidth: context.width * 0.6),
                  child: AudioPlayer(
                    key: Key("AudioMessage-$path"),
                    file: file,
                    attachment: null,
                    playButtonFocusNode: previewFocusNode,
                    nextFocusNode: discardFocusNode,
                  ),
                )
              ],
            ),
            actions: <Widget>[
              CallbackShortcuts(
                bindings: {
                  const SingleActivator(LogicalKeyboardKey.arrowLeft): () => previewFocusNode.requestFocus(),
                  const SingleActivator(LogicalKeyboardKey.arrowUp): () => previewFocusNode.requestFocus(),
                  const SingleActivator(LogicalKeyboardKey.arrowRight): () => sendFocusNode.requestFocus(),
                  const SingleActivator(LogicalKeyboardKey.arrowDown): () => sendFocusNode.requestFocus(),
                  const SingleActivator(LogicalKeyboardKey.enter): () {
                    onDeleteRecording(file.path!);
                    Navigator.of(context, rootNavigator: true).pop();
                  },
                  const SingleActivator(LogicalKeyboardKey.select): () {
                    onDeleteRecording(file.path!);
                    Navigator.of(context, rootNavigator: true).pop();
                  },
                  const SingleActivator(LogicalKeyboardKey.space): () {
                    onDeleteRecording(file.path!);
                    Navigator.of(context, rootNavigator: true).pop();
                  },
                },
                child: TextButton(
                  focusNode: discardFocusNode,
                  style: ButtonStyle(
                    backgroundColor: WidgetStateProperty.resolveWith(
                      (states) => states.contains(WidgetState.focused) ? focusedBackground : null,
                    ),
                  ),
                  onPressed: () {
                    onDeleteRecording(file.path!);
                    Navigator.of(context, rootNavigator: true).pop();
                  },
                  child: Text(
                    "Discard",
                    style: context.theme.textTheme.bodyLarge!.copyWith(color: Get.context!.theme.colorScheme.primary),
                  ),
                ),
              ),
              CallbackShortcuts(
                bindings: {
                  const SingleActivator(LogicalKeyboardKey.arrowLeft): () => discardFocusNode.requestFocus(),
                  const SingleActivator(LogicalKeyboardKey.arrowUp): () => discardFocusNode.requestFocus(),
                  const SingleActivator(LogicalKeyboardKey.enter): () async {
                    await controller!.send(SendData(
                      attachments: [file],
                      text: "",
                      annotations: AttributedBody.empty(),
                      subject: "",
                      isAudioMessage: true,
                    ));
                    onDeleteRecording(file.path!);
                    Navigator.of(context, rootNavigator: true).pop();
                  },
                  const SingleActivator(LogicalKeyboardKey.select): () async {
                    await controller!.send(SendData(
                      attachments: [file],
                      text: "",
                      annotations: AttributedBody.empty(),
                      subject: "",
                      isAudioMessage: true,
                    ));
                    onDeleteRecording(file.path!);
                    Navigator.of(context, rootNavigator: true).pop();
                  },
                  const SingleActivator(LogicalKeyboardKey.space): () async {
                    await controller!.send(SendData(
                      attachments: [file],
                      text: "",
                      annotations: AttributedBody.empty(),
                      subject: "",
                      isAudioMessage: true,
                    ));
                    onDeleteRecording(file.path!);
                    Navigator.of(context, rootNavigator: true).pop();
                  },
                },
                child: TextButton(
                  focusNode: sendFocusNode,
                  style: ButtonStyle(
                    backgroundColor: WidgetStateProperty.resolveWith(
                      (states) => states.contains(WidgetState.focused) ? focusedBackground : null,
                    ),
                  ),
                  onPressed: () async {
                    await controller!.send(SendData(
                      attachments: [file],
                      text: "",
                      annotations: AttributedBody.empty(),
                      subject: "",
                      isAudioMessage: true,
                    ));
                    onDeleteRecording(file.path!);
                    Navigator.of(context, rootNavigator: true).pop();
                  },
                  child: Text(
                    "Send",
                    style: context.theme.textTheme.bodyLarge!.copyWith(color: Get.context!.theme.colorScheme.primary),
                  ),
                ),
              ),
            ],
          );
        },
      );
    } finally {
      controller?.showingOverlays = false;
      previewFocusNode.dispose();
      discardFocusNode.dispose();
      sendFocusNode.dispose();
      // Return focus to the message input once the dialog is gone (sent, discarded, or dismissed).
      WidgetsBinding.instance.addPostFrameCallback((_) => controller?.focusNode.requestFocus());
    }
  }
}
