import 'package:audio_waveforms/audio_waveforms.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/text_field/voice_message_recorder.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// Animated overlay shown on top of the text field while audio is recording.
///
/// Internally wraps an [Obx] subscribed to [controller.showRecording] so that
/// only this subtree rebuilds on recording state changes.
class TextFieldRecordingOverlay extends StatelessWidget {
  const TextFieldRecordingOverlay({
    super.key,
    required this.controller,
    this.recorderController,
  });

  final ConversationViewController controller;
  final RecorderController? recorderController;

  @override
  Widget build(BuildContext context) {
    return Obx(() => AnimatedSize(
          duration: const Duration(milliseconds: 500),
          curve: controller.showRecording.value ? Curves.easeOutBack : Curves.easeOut,
          child: !controller.showRecording.value
              ? const SizedBox.shrink()
              : Builder(builder: (context) {
                  final bool iOS = SettingsSvc.settings.skin.value == Skins.iOS;
                  final bool samsung = SettingsSvc.settings.skin.value == Skins.Samsung;
                  final box = controller.textFieldKey.currentContext?.findRenderObject() as RenderBox?;
                  final textFieldSize = box?.size ?? const Size(250, 35);
                  final start = DateTime.now();
                  // Dumb (D-pad) devices record via the `record` package (like desktop), so
                  // show the simple timer overlay instead of the audio_waveforms recorder.
                  return kIsDesktop || SettingsSvc.settings.isDumb.value
                      ? StreamBuilder(
                          stream: Stream.periodic(const Duration(milliseconds: 100)),
                          builder: (context, snapshot) {
                            Duration elapsed = DateTime.now().difference(start);
                            return Container(
                              padding: const EdgeInsets.symmetric(vertical: 5, horizontal: 15),
                              width: textFieldSize.width - (samsung ? 0 : 80),
                              height: textFieldSize.height - 15,
                              decoration: BoxDecoration(
                                border: Border.fromBorderSide(BorderSide(
                                  color: context.theme.colorScheme.outline,
                                  width: 1,
                                )),
                                borderRadius: BorderRadius.circular(20),
                                color: context.theme.colorScheme.surfaceContainerHighest,
                              ),
                              child: Center(
                                child: AnimatedOpacity(
                                  duration: const Duration(seconds: 1),
                                  opacity: (elapsed.inMilliseconds ~/ 1200 % 2 + 0.5).clamp(0, 1),
                                  child: Text("Recording... (${prettyDuration(elapsed)})",
                                      style: context.textTheme.titleMedium),
                                ),
                              ),
                            );
                          })
                      : VoiceMessageRecorder(
                          recorderController: recorderController,
                          textFieldSize: textFieldSize,
                          iOS: iOS,
                          samsung: samsung,
                        );
                }),
        ));
  }
}
