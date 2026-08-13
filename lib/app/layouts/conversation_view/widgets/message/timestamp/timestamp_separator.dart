import 'package:bluebubbles/app/state/chat_state_scope.dart';
import 'package:bluebubbles/app/state/message_state_scope.dart';
import 'package:bluebubbles/app/layouts/conversation_details/dialogs/timeframe_picker.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:pull_down_button/pull_down_button.dart';
import 'package:universal_io/io.dart';

class _TimestampParts {
  final String? date;
  final String time;
  const _TimestampParts({this.date, required this.time});
}

class TimestampSeparator extends StatelessWidget {
  const TimestampSeparator({
    super.key,
    required this.olderMessage,
  });
  final Message? olderMessage;

  bool withinTimeThreshold(Message first, Message? second) {
    if (second == null) return true;
    final diff = second.chatViewDate!.difference(first.chatViewDate!).inMinutes.abs();
    return diff > 30 ||
        (first.dateScheduled != null) != (second.dateScheduled != null) ||
        (diff > 5 && first.dateScheduled != null);
  }

  _TimestampParts? _buildTimeStamp(Message message) {
    if (SettingsSvc.settings.skin.value == Skins.Samsung &&
        message.chatViewDate?.day != olderMessage?.chatViewDate?.day) {
      return _TimestampParts(time: buildSeparatorDateSamsung(message.chatViewDate!));
    } else if (SettingsSvc.settings.skin.value != Skins.Samsung && withinTimeThreshold(message, olderMessage)) {
      final time = message.chatViewDate!;
      if (SettingsSvc.settings.skin.value == Skins.iOS) {
        return _TimestampParts(date: time.isToday() ? "Today" : buildDate(time), time: buildTime(time));
      } else {
        return _TimestampParts(
            date: time.isToday() ? "Today" : buildSeparatorDateMaterial(time), time: buildTime(time));
      }
    } else {
      return null;
    }
  }

  @override
  Widget build(BuildContext context) {
    final message = MessageStateScope.messageOf(context);
    final timestamp = _buildTimeStamp(message);
    final hasBackground = ChatStateScope.maybeOf(context)?.customBackgroundPath.value?.isNotEmpty == true;
    final textColor = hasBackground ? context.theme.colorScheme.onSurfaceVariant : context.theme.colorScheme.outline;

    final richText = timestamp != null
        ? RichText(
            textAlign: TextAlign.center,
            text: TextSpan(
              style: context.theme.textTheme.labelSmall!.copyWith(color: textColor, fontWeight: FontWeight.normal),
              children: [
                if (message.dateScheduled != null && olderMessage?.dateScheduled == null)
                  TextSpan(
                    text: "Send Later\n",
                    style: context.theme.textTheme.labelSmall!.copyWith(
                      fontWeight: FontWeight.w600,
                      color: textColor,
                      height: 2.5,
                    ),
                  ),
                if (timestamp.date != null)
                  TextSpan(
                    text: "${timestamp.date!} ",
                    style: context.theme.textTheme.labelSmall!.copyWith(fontWeight: FontWeight.w600, color: textColor),
                  ),
                TextSpan(text: timestamp.time),
                if (message.dateScheduled != null)
                  TextSpan(
                    text: " Edit",
                    style: context.theme.textTheme.labelSmall!
                        .copyWith(fontWeight: FontWeight.w600, color: context.theme.primaryColor),
                  ),
              ],
            ),
          )
        : null;

    final child = richText != null
        ? Padding(
            padding: const EdgeInsets.all(14.0),
            child: hasBackground
                ? Center(
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 3, horizontal: 10),
                      decoration: BoxDecoration(
                        color: context.theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.75),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: richText,
                    ),
                  )
                : richText,
          )
        : const SizedBox.shrink();

    if (message.dateScheduled == null) return child;

    return PullDownButton(
      routeTheme: PullDownMenuRouteTheme(
        backgroundColor: context.theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.9),
      ),
      animationAlignmentOverride: Alignment.bottomCenter,
      itemBuilder: (context) {
        final responsibleMessages = MessagesSvc(message.getChat()!.guid)
            .struct
            .messages
            .where((m) =>
                m.dateScheduled != null &&
                m.dateScheduled!.difference(message.dateScheduled!).inMinutes < 5 &&
                m.dateScheduled!.difference(message.dateScheduled!).inMinutes >= 0)
            .toList()
          ..sort(Message.sort);
        return [
          PullDownMenuItem(
            title: responsibleMessages.length == 1 ? 'Send Message' : 'Send ${responsibleMessages.length} Messages',
            icon: CupertinoIcons.arrow_up_circle,
            onTap: () async {
              for (final scheduled in responsibleMessages) {
                scheduled.dateScheduled = null;
                scheduled.stagingGuid = scheduled.guid;
                scheduled.dateCreated = DateTime.now();
                scheduled.generateTempGuid();
                scheduled.save();
                await OutgoingMsgHandler.queue(
                  OutgoingMessage(
                    chat: scheduled.getChat()!,
                    message: scheduled,
                  ),
                );
              }
            },
          ),
          PullDownMenuItem(
            title: 'Edit Time',
            icon: CupertinoIcons.clock,
            onTap: () async {
              final date = await showTimeframePicker("Pick date and time", context, presetsAhead: true);
              if (date == null || !date.isAfter(DateTime.now())) return;
              for (final scheduled in responsibleMessages) {
                scheduled.dateScheduled = date;
                scheduled.save();
                await OutgoingMsgHandler.queue(
                  OutgoingMessage(
                    chat: scheduled.getChat()!,
                    message: scheduled,
                  ),
                );
              }
            },
          ),
          PullDownMenuItem(
            title: responsibleMessages.length == 1 ? 'Delete Message' : 'Delete ${responsibleMessages.length} Messages',
            icon: CupertinoIcons.trash,
            iconColor: Colors.red[700],
            onTap: () async {
              for (final scheduled in responsibleMessages) {
                await BackendSvc.moveToRecycleBin(scheduled.getChat()!, scheduled);
                for (final attachment in (scheduled.fetchAttachments() ?? [])) {
                  if (attachment == null) continue;
                  try {
                    File(attachment.getFile().path!).deleteSync();
                  } catch (e) {
                    Logger.debug("Failed to rm attachment $e");
                  }
                }
                MessagesSvc(scheduled.getChat()!.guid).removeMessage(scheduled);
                Message.delete(scheduled.guid!);
              }
            },
          ),
        ];
      },
      buttonBuilder: (context, showMenu) => GestureDetector(
        onTap: showMenu,
        child: child,
      ),
    );
  }
}
