import 'dart:async';
import 'dart:math';
import 'dart:ui';

import 'package:async_task/async_task_extension.dart';
import 'package:audio_waveforms/audio_waveforms.dart' as aw;
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/misc/tail_clipper.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/app/state/message_state.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/services/ui/chat/send_data.dart';
import 'package:dotted_border/dotted_border.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:mime_type/mime_type.dart';
import 'package:simple_animations/simple_animations.dart';
import 'package:uuid/uuid.dart';

class SendAnimation extends CustomStateful<ConversationViewController> {
  const SendAnimation({super.key, required super.parentController});

  @override
  CustomState createState() => _SendAnimationState();
}

class _SendAnimationState extends CustomState<SendAnimation, SendData, ConversationViewController> {
  Message? message;
  Tween<double> tween = Tween<double>(begin: 1, end: 0);
  Control control = Control.stop;

  // The padding applied to the ConversationTextField in its closed state
  // (bottom: 10 + top: 10) plus
  // the visual gap between the text field top edge and the bottom of the message list.
  static const double _textFieldVerticalPadding = 17.5;

  // Fallback typing-indicator height when the row hasn't been laid out yet.
  static const double _typingIndicatorFallbackHeight = 50.0;

  // Height of the text field component at its resting (empty, single-line) size,
  // measured from the RenderBox once after the first frame. We avoid using a
  // live getter because during a multi-line send the AnimatedSize is still
  // shrinking the text field, which causes AnimatedPositioned to chase a moving
  // target and overshoot. Using the frozen resting height keeps the target
  // constant so the animated bubble always lands where the permanent message is.
  double _textFieldSize = 0;

  // Height of the focus-info widget (NotificationsSilencedBanner) above the text field.
  double get focusInfoSize =>
      (controller.focusInfoKey.currentContext?.findRenderObject() as RenderBox?)?.size.height ?? 0;

  // Extra vertical offset that differs between the iOS skin and Material/Samsung skins.
  double get _platformVerticalOffset => iOS ? -4.0 : 14.5;

  // Offset for typing indicator when it is visible.
  double get _typingIndicatorOffset {
    final measured = (controller.typingInfoKey.currentContext?.findRenderObject() as RenderBox?)?.size.height;
    if (measured != null && measured > 0) {
      return measured;
    }
    return controller.showTypingIndicatorFor.isNotEmpty ? _typingIndicatorFallbackHeight : 0;
  }

  // Offset for smart reply row when it is visible.
  double get _smartReplyOffset => controller.showSmartReplyRow.value ? controller.smartReplyRowHeight.value : 0;

  // Total bottom offset for the AnimatedPositioned — how far above the bottom
  // of the Stack the animation bubble should land at the end of its travel.
  // Uses the stored resting text field height (_textFieldSize) so the target
  // never changes during the animation, even while the field shrinks.
  double get _animationBottomOffset =>
      _textFieldSize +
      focusInfoSize +
      _textFieldVerticalPadding +
      _typingIndicatorOffset +
      _smartReplyOffset +
      _platformVerticalOffset;

  @override
  void initState() {
    super.initState();
    controller.sendFunc = send;
    // Capture the resting (empty, single-line) text field height from the
    // RenderBox after the first layout pass. Must be deferred because the widget hasn't been laid
    // out yet during initState.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final box = controller.textFieldKey.currentContext?.findRenderObject() as RenderBox?;
      final h = box?.size.height;
      if (h != null && h > 0) _textFieldSize = h;
    });

    // If ChatCreator pre-queued a send before navigating here, fire it now.
    //
    // We wait on messagesViewReady instead of a bare addPostFrameCallback so
    // that the send only fires after MessagesView has *fully* initialised —
    // handlers registered AND _listKey recreated (async loadChunk path).
    // Without this wait the sendAnimation's addPostFrameCallback can fire
    // between the _listKey recreation and the following setState flush, so
    // handleNewMessage's insertItem call finds a null currentState and silently
    // no-ops, causing the sent message to never appear in the list.
    if (controller.pendingSend != null) {
      final pendingData = controller.pendingSend!;
      controller.pendingSend = null;
      controller.messagesViewReady.then((_) {
        if (!mounted) return;
        WidgetsBinding.instance.addPostFrameCallback((_) async {
          // Some extra time to ensure the list is fully ready and the insertItem
          // call in handleNewMessage doesn't find a null currentState and no-op,
          // causing the sent message to never appear in the list.
          await Future.delayed(const Duration(milliseconds: 250));
          if (!mounted) return;
          await send(pendingData);

          // Clear the text field and attachments now that the send has been queued,
          // mirroring what ConversationTextField.sendMessage() does for normal sends.
          controller.pickedAttachments.clear();
          controller.textController.clear();
          controller.subjectTextController.clear();
          controller.replyToMessage = null;
        });
      });
    }
  }

  Future<void> send(SendData data) async {
    // do not add anything above this line, the attachments must be extracted first
    final attachments = List<PlatformFile>.from(data.attachments);
    // text is mutable — reassigned during annotation processing below
    String text = data.text;
    final annotations = data.annotations;
    final schedule = data.schedule;
    if (SettingsSvc.settings.scrollToBottomOnSend.value) {
      await controller.scrollToTime(schedule ?? DateTime.now());
    }
    if (SettingsSvc.settings.sendSoundPath.value != null &&
        !(isNullOrEmptyString(annotations.string) &&
            isNullOrEmptyString(data.subject) &&
            attachments.isEmpty &&
            data.payloadData == null)) {
      if (kIsDesktop) {
        Player player = Player();
        await player.setVolume(SettingsSvc.settings.soundVolume.value.toDouble());
        await player.open(Media(SettingsSvc.settings.sendSoundPath.value!));
        player.stream.completed
            .firstWhere((completed) => completed)
            .then((_) async => Future.delayed(const Duration(milliseconds: 450), () async => await player.dispose()));
      } else {
        aw.PlayerController controller = aw.PlayerController();
        controller
            .preparePlayer(
                path: SettingsSvc.settings.sendSoundPath.value!, volume: SettingsSvc.settings.soundVolume.value / 100)
            .then((_) => controller.startPlayer());
      }
    }

    final replyRun = data.replyPart != null ? Message.findOne(guid: data.replyGuid)?.replyPart(data.replyPart!) : null;

    for (int i = 0; i < attachments.length; i++) {
      final file = attachments[i];
      final attachment = Attachment(
        isOutgoing: true,
        mimeType: mime(file.path) ?? mime(file.name),
        uti: "public.jpg",
        transferName: file.name,
        totalBytes: file.size,
        // Store the original source path in metadata so prepAttachment can copy it.
        // For bytes-only files (clipboard/GIF keyboard), store bytes in the transient field
        // so prepAttachment can write them to disk.
        metadata: file.path != null ? {'source_path': file.path} : null,
        bytes: file.path == null ? file.bytes : null,
      );

      final message = Message(
        text: "",
        dateCreated: DateTime.now(),
        hasAttachments: true,
        balloonBundleId: file.balloonBundleId,
        isFromMe: true,
        handleId: 0,
        threadOriginatorGuid: i == 0 ? data.replyGuid : null,
        threadOriginatorPart: i == 0 ? replyRun : null,
        expressiveSendStyleId: data.effectId,
        payloadData: data.payloadData,
      );
      message.dateScheduled = schedule;
      if (data.payloadData != null) {
        message.balloonBundleId = data.payloadData?.bundleId;
        message.stagingGuid = const Uuid().v4().toUpperCase();
      }
      message.generateTempGuid();
      attachment.guid = message.guid;
      await OutgoingMsgHandler.queue(
        OutgoingAttachment(
          chat: controller.chat,
          message: message,
          attachment: attachment,
          isAudioMessage: data.isAudioMessage,
        ),
      );
    }

    if (attachments.isEmpty && data.payloadData != null) {
      final message = Message(
        text: "",
        dateCreated: DateTime.now(),
        hasAttachments: false,
        isFromMe: true,
        handleId: 0,
        threadOriginatorGuid: data.replyGuid,
        threadOriginatorPart: replyRun,
        expressiveSendStyleId: data.effectId,
        payloadData: data.payloadData,
        balloonBundleId: data.payloadData!.bundleId,
        hasApplePayloadData: true,
      );
      message.dateScheduled = schedule;
      message.stagingGuid = const Uuid().v4().toUpperCase();
      message.generateTempGuid();
      await OutgoingMsgHandler.queue(
        OutgoingMessage(
          chat: controller.chat,
          message: message,
        ),
      );
    }

    if (annotations.string.trim().isNotEmpty || data.subject.isNotEmpty) {
      text = annotations.string;
      final _message = Message(
        text: text.isEmpty && data.subject.isNotEmpty ? data.subject : text,
        subject: text.isEmpty && data.subject.isNotEmpty ? null : data.subject,
        threadOriginatorGuid: attachments.isEmpty ? data.replyGuid : null,
        threadOriginatorPart: attachments.isEmpty ? replyRun : null,
        expressiveSendStyleId: data.effectId,
        dateCreated: DateTime.now(),
        hasAttachments: false,
        isFromMe: true,
        handleId: 0,
        hasDdResults: true,
        attributedBody: [
          if (annotations.string.isNotEmpty) annotations,
        ],
      );
      _message.dateScheduled = schedule;
      _message.generateTempGuid();
      OutgoingMsgHandler.queue(
        OutgoingMessage(
          chat: controller.chat,
          message: _message,
        ),
      );
      setState(() {
        tween = Tween<double>(
          begin: 0.9,
          end: 0,
        );
        control = Control.play;
        message = _message;
      });
    }
    super.updateWidget(data);
  }

  @override
  Widget build(BuildContext context) {
    final typicalWidth = message?.isBigEmoji ?? false
        ? NavigationSvc.width(context)
        : NavigationSvc.width(context) * MessageState.maxBubbleSizeFactor - 40;
    const duration = 500;
    const curve = Curves.easeInOut;
    const buttonSize = 44;
    final messageBoxSize = NavigationSvc.width(context) - buttonSize;
    return AnimatedPositioned(
      duration: Duration(milliseconds: message != null ? duration : 0),
      bottom: message != null ? _animationBottomOffset : 0,
      right: samsung ? -38 : -5.0,
      curve: curve,
      onEnd: () async {
        if (message != null) {
          await Future.delayed(const Duration(milliseconds: 200));
          setState(() {
            tween = Tween<double>(begin: 1, end: 0);
            control = Control.stop;
            message = null;
          });
        }
      },
      child: Visibility(
        visible: message != null,
        child: CustomAnimationBuilder<double>(
          control: control,
          tween: tween,
          duration: Duration(milliseconds: message != null ? duration : 0),
          builder: (context, linear, child) {
            var value = curve.transform(linear);
            var exp = Curves.easeIn.transform(linear);
            var bubble = ClipPath(
              clipper: TailClipper(
                isFromMe: true,
                showTail: true,
                connectLower: false,
                connectUpper: false,
              ),
              child: BackdropFilter(
                filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
                child: Container(
                    constraints: BoxConstraints(
                      maxWidth: max(messageBoxSize * exp, typicalWidth) - (message?.dateScheduled != null ? 4 : 0),
                      minWidth: max(messageBoxSize * exp - (message?.dateScheduled != null ? 4 : 0), 0),
                      minHeight: 40 - (message?.dateScheduled != null ? 4 : 0),
                    ),
                    color: !message!.isBigEmoji && message?.dateScheduled == null
                        ? context.theme.colorScheme.primary.withAlpha(((1 - value) * 255).toInt())
                        : null,
                    padding: EdgeInsets.symmetric(
                            vertical: 10 - (message?.dateScheduled != null ? 4 : 0),
                            horizontal: 15 - (message?.dateScheduled != null ? 4 : 0))
                        .add(EdgeInsets.only(
                            left: message?.dateScheduled != null && message!.isBigEmoji
                                ? -8
                                : message!.isFromMe! || message!.isBigEmoji
                                    ? 0
                                    : 10,
                            right: message!.isFromMe! && !message!.isBigEmoji ? 10 : 0)),
                    child: Align(
                      alignment: Alignment.centerLeft,
                      widthFactor: 1,
                      child: Padding(
                        padding:
                            message!.fullText.length == 1 ? const EdgeInsets.only(left: 3, right: 3) : EdgeInsets.zero,
                        child: RichText(
                          text: TextSpan(
                            children: buildMessageSpans(
                              context,
                              MessagePart(part: 0, text: message!.text, subject: message!.subject),
                              message!,
                              colorOverride: Color.lerp(
                                context.theme.colorScheme.onSurfaceVariant,
                                message?.dateScheduled != null
                                    ? context.theme.colorScheme.primary
                                    : context.theme.colorScheme.onPrimary,
                                1 - value,
                              ),
                            ),
                          ),
                        ),
                      ),
                    )),
              ),
            );
            return Transform.scale(
              scale: (1 - value) < .5 ? lerpDouble(1.1, .9, (1 - value) / .5) : lerpDouble(.9, 1, (.5 - value) / .5),
              alignment: Alignment.centerRight,
              child: message!.dateScheduled != null
                  ? DottedBorder(
                      customPath: (size) => TailClipper(
                        isFromMe: true,
                        showTail: true,
                        connectLower: false,
                        connectUpper: false,
                      ).getClip(size),
                      color: context.theme.colorScheme.primaryContainer,
                      strokeWidth: 2,
                      dashPattern: const [7, 4],
                      child: bubble,
                    )
                  : bubble,
            );
          },
        ),
      ),
    );
  }
}
