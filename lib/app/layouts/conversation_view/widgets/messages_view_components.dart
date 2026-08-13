import 'package:bluebubbles/app/components/avatars/contact_avatar_group_widget.dart';
import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/typing/typing_indicator.dart';
import 'package:bluebubbles/app/state/chat_state_scope.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

// Helper to check if iOS skin is active
bool get iOS => SettingsSvc.settings.skin.value == Skins.iOS;

/// Extracted widget for typing indicator row with avatar
class TypingIndicatorRow extends StatelessWidget {
  const TypingIndicatorRow({
    super.key,
    required this.controller,
  });

  final ConversationViewController controller;

  @override
  Widget build(BuildContext context) {
    final chat = ChatStateScope.chatOf(context);
    return Obx(() => Row(
          key: controller.typingInfoKey,
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            if (controller.showTypingIndicatorFor.isNotEmpty &&
                (chat.isGroup || SettingsSvc.settings.alwaysShowAvatars.value) &&
                iOS)
              Padding(
                padding: const EdgeInsets.only(left: 10.0),
                child: ContactAvatarGroupWidget(
                  participants: [...controller.showTypingIndicatorFor],
                  size: 30,
                  editable: false,
                ),
              ),
            TypingIndicator(
              controller: controller,
            )
          ],
        ));
  }
}

/// Extracted widget for notifications silenced banner
class NotificationsSilencedBanner extends StatelessWidget {
  const NotificationsSilencedBanner({
    super.key,
    required this.controller,
    required this.chat,
    required this.latestMessage,
  });

  final ConversationViewController controller;
  final Chat chat;
  final Message? latestMessage;

  @override
  Widget build(BuildContext context) {
    final chat = ChatStateScope.chatOf(context);
    const moonIcon = CupertinoIcons.moon_fill;

    return AnimatedSize(
      key: controller.focusInfoKey,
      duration: const Duration(milliseconds: 250),
      child: Obx(() => controller.recipientNotifsSilenced.value
          ? Padding(
              padding: const EdgeInsets.only(top: 20, bottom: 10),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        String.fromCharCode(moonIcon.codePoint),
                        style: TextStyle(
                          fontFamily: moonIcon.fontFamily,
                          package: moonIcon.fontPackage,
                          fontSize: context.theme.textTheme.bodyLarge!.fontSize,
                          color: _showNotifyAnyways ? context.theme.colorScheme.outline : Colors.deepPurple,
                        ),
                      ),
                      Text(
                        " ${chat.getTitle()} has notifications silenced",
                        style: context.theme.textTheme.bodyLarge!.copyWith(
                            color: _showNotifyAnyways ? context.theme.colorScheme.outline : Colors.deepPurple),
                      ),
                    ],
                  ),
                  _NotifyAnywayButton(chat: chat, latestMessage: latestMessage),
                ],
              ),
            )
          : ConstrainedBox(
              constraints: const BoxConstraints(
                minWidth: double.infinity,
                maxWidth: double.infinity,
              ),
              child: const SizedBox.shrink(),
            )),
    );
  }

  bool get _showNotifyAnyways =>
      latestMessage?.isFromMe == true &&
      latestMessage?.dateRead == null &&
      latestMessage?.wasDeliveredQuietly == true &&
      latestMessage?.didNotifyRecipient == false;
}

/// Nested widget for "Notify Anyway" button to further isolate rebuilds
class _NotifyAnywayButton extends StatelessWidget {
  const _NotifyAnywayButton({required this.chat, required this.latestMessage});

  final Chat chat;
  final Message? latestMessage;

  @override
  Widget build(BuildContext context) {
    // Check conditions for showing the button
    if (latestMessage?.isFromMe == true &&
        latestMessage?.dateRead == null &&
        latestMessage?.wasDeliveredQuietly == true &&
        latestMessage?.didNotifyRecipient == false) {
      return TextButton(
        child: Text(
          "Notify Anyway",
          style: context.theme.textTheme.labelLarge!.copyWith(color: Colors.deepPurple),
        ),
        style: TextButton.styleFrom(
          padding: EdgeInsets.zero,
          minimumSize: const Size(50, 30),
          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
          alignment: Alignment.centerLeft,
        ),
        onPressed: () async {
          final msg = await api.newMsg(
            conversation: await chat.getConversationData(),
            sender: await chat.ensureHandle(),
            message: const api.Message.notifyAnyways(),
          );
          msg.id = latestMessage!.guid!;
          try {
            await (BackendSvc as RustPushBackend).sendMsg(msg);
          } catch (e, s) {
            Logger.error("Failed to notify anyways", error: e, trace: s);
            if (!chat.isRpSms) {
              rethrow;
            }
          }
          latestMessage!.wasDeliveredQuietly = false;
          latestMessage!.save();
          EventDispatcherSvc.emit("message-updated-${latestMessage!.guid}");
          chat.dateNotifiedAnyways = DateTime.now();
          chat.save(updateDateNotifiedAnyways: true);
        },
      );
    }
    return const SizedBox.shrink();
  }
}

class ReportJunkBanner extends StatelessWidget {
  const ReportJunkBanner({
    super.key,
    required this.controller,
    required this.chat,
  });

  final ConversationViewController controller;
  final Chat chat;

  @override
  Widget build(BuildContext context) {
    return AnimatedSize(
      duration: const Duration(milliseconds: 250),
      child: Obx(
        () => controller.reportJunkAvailable.value
            ? Padding(
                padding: const EdgeInsets.only(top: 20, bottom: 10),
                child: GestureDetector(
                  child: RichText(
                    textAlign: TextAlign.center,
                    text: TextSpan(
                      style: context.theme.textTheme.labelMedium!
                          .copyWith(color: context.theme.colorScheme.outline, fontWeight: FontWeight.normal),
                      children: [
                        TextSpan(
                          text: "This sender is not in your contacts\n",
                          style: context.theme.textTheme.labelMedium!.copyWith(
                            fontWeight: FontWeight.w600,
                            color: context.theme.colorScheme.outline,
                            height: 2.5,
                          ),
                        ),
                        TextSpan(
                          text: "Report Junk",
                          style: context.theme.textTheme.labelMedium!
                              .copyWith(fontWeight: FontWeight.w600, color: context.theme.primaryColor),
                        ),
                      ],
                    ),
                  ),
                  onTap: () async {
                    showDialog(
                      context: context,
                      builder: (BuildContext dialogContext) {
                        return AlertDialog(
                          title: Text(
                            "Report junk?",
                            style: dialogContext.theme.textTheme.titleLarge,
                          ),
                          backgroundColor: dialogContext.theme.colorScheme.surface,
                          content: Text(
                            "You can report this message to Apple.",
                            style: dialogContext.theme.textTheme.bodyLarge,
                          ),
                          actions: <Widget>[
                            TextButton(
                              child: Text(
                                "Cancel",
                                style: dialogContext.theme.textTheme.bodyLarge!
                                    .copyWith(color: dialogContext.theme.colorScheme.primary),
                              ),
                              onPressed: () {
                                Navigator.of(dialogContext).pop();
                              },
                            ),
                            TextButton(
                              child: Text(
                                "Delete and Report",
                                style: dialogContext.theme.textTheme.bodyLarge!
                                    .copyWith(color: dialogContext.theme.colorScheme.primary),
                              ),
                              onPressed: () async {
                                Navigator.of(dialogContext).pop();
                                Navigator.of(dialogContext).pop();
                                try {
                                  await PushSvc.markAsSpam(chat);
                                } catch (e, s) {
                                  showSnackbar("Failed to mark as spam!", "$e");
                                  Logger.error("Failed to mark as spam", error: e, trace: s);
                                  rethrow;
                                }
                              },
                            ),
                          ],
                        );
                      },
                    );
                  },
                ),
              )
            : ConstrainedBox(
                constraints: const BoxConstraints(
                  minWidth: double.infinity,
                  maxWidth: double.infinity,
                ),
                child: const SizedBox.shrink(),
              ),
      ),
    );
  }
}

/// Extracted widget for smart replies row
class SmartRepliesRow extends StatelessWidget {
  const SmartRepliesRow({
    super.key,
    required this.controller,
    required this.smartReplies,
    required this.internalSmartReplies,
  });

  final ConversationViewController controller;
  final RxList<String> smartReplies;
  final RxMap<String, Widget> internalSmartReplies;

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      final bool visible = smartReplies.isNotEmpty || internalSmartReplies.isNotEmpty;
      final double rowHeight = context.theme.extension<BubbleText>()!.bubbleText.fontSize! + 35;
      final double topPadding = iOS ? 8.0 : 0.0;
      final double totalHeight = visible ? rowHeight + topPadding : 0.0;

      controller.updateSmartReplyLayout(visible: visible, height: totalHeight);

      return AnimatedSize(
        duration: const Duration(milliseconds: 400),
        child: visible
            ? Padding(
                padding: EdgeInsets.only(top: topPadding, right: 5),
                child: SizedBox(
                  height: rowHeight,
                  child: ListView(
                    scrollDirection: Axis.horizontal,
                    reverse: true,
                    children: smartReplies.map((suggestion) => _buildReplyWidget(context, suggestion)).toList()
                      ..addAll(internalSmartReplies.values),
                  ),
                ),
              )
            : const SizedBox.shrink(),
      );
    });
  }

  Widget _buildReplyWidget(BuildContext context, String suggestion) {
    final hasBackground = ChatsSvc.getChatState(controller.chat.guid)?.customBackgroundPath.value?.isNotEmpty == true;
    return Container(
      margin: const EdgeInsets.all(5),
      decoration: hasBackground
          ? BoxDecoration(
              color: context.theme.colorScheme.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(19),
            )
          : BoxDecoration(
              border: Border.all(
                width: 2,
                style: BorderStyle.solid,
                color: context.theme.colorScheme.surfaceContainerHighest,
              ),
              borderRadius: BorderRadius.circular(19),
            ),
      child: InkWell(
        borderRadius: BorderRadius.circular(19),
        onTap: () {
          OutgoingMsgHandler.queue(OutgoingMessage(
            chat: controller.chat,
            message: Message(
              text: suggestion,
              dateCreated: DateTime.now(),
              hasAttachments: false,
              isFromMe: true,
              handleId: 0,
            ),
          ));
        },
        child: Center(
          child: Padding(
            padding: const EdgeInsets.only(bottom: 1.5, left: 13.0, right: 13.0),
            child: RichText(
              text: TextSpan(
                children: MessageHelper.buildEmojiText(
                  suggestion,
                  context.theme.extension<BubbleText>()!.bubbleText,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Extracted widget for scroll down button
class ScrollDownButton extends StatelessWidget {
  const ScrollDownButton({
    super.key,
    required this.controller,
  });

  final ConversationViewController controller;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: iOS ? Alignment.bottomRight : Alignment.bottomCenter,
      child: Padding(
        padding: const EdgeInsets.only(bottom: 10, right: 10, left: 10),
        child: Obx(
          () => IgnorePointer(
            ignoring: !controller.showScrollDown.value,
            child: AnimatedOpacity(
              opacity: controller.showScrollDown.value ? 1 : 0,
              duration: const Duration(milliseconds: 300),
              child: iOS
                  ? TextButton(
                      style: TextButton.styleFrom(
                        backgroundColor: context.theme.colorScheme.secondary,
                        shape: const CircleBorder(),
                        padding: const EdgeInsets.all(0),
                        maximumSize: const Size(32, 32),
                        minimumSize: const Size(32, 32),
                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                      onPressed: controller.scrollToBottom,
                      child: Container(
                        constraints: const BoxConstraints(minHeight: 32, minWidth: 32),
                        decoration: const BoxDecoration(
                          shape: BoxShape.circle,
                        ),
                        padding: const EdgeInsets.only(top: 3, left: 1),
                        alignment: Alignment.center,
                        child: Icon(
                          CupertinoIcons.chevron_down,
                          color: context.theme.colorScheme.onSecondary,
                          size: 20,
                        ),
                      ),
                    )
                  : FloatingActionButton.small(
                      heroTag: null,
                      onPressed: controller.scrollToBottom,
                      backgroundColor: context.theme.colorScheme.secondary,
                      child: Icon(
                        Icons.arrow_downward,
                        color: context.theme.colorScheme.onSecondary,
                      ),
                    ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Extracted widget for drag and drop overlay
class DragDropOverlay extends StatelessWidget {
  const DragDropOverlay({
    super.key,
    required this.dragging,
  });

  final RxBool dragging;

  @override
  Widget build(BuildContext context) {
    return Obx(
      () => AnimatedContainer(
        duration: const Duration(milliseconds: 150),
        color: context.theme.colorScheme.surface.withValues(alpha: dragging.value ? 0.4 : 0),
        child: dragging.value
            ? Center(
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      iOS ? CupertinoIcons.paperclip : Icons.attach_file,
                      color: context.theme.colorScheme.primary,
                      size: 50,
                    ),
                    Text(
                      "Attach File(s)",
                      style: context.theme.textTheme.headlineLarge!.copyWith(color: context.theme.colorScheme.primary),
                    ),
                  ],
                ),
              )
            : const SizedBox.shrink(),
      ),
    );
  }
}
