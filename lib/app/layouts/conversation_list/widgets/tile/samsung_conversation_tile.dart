import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/conversation_tile.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/trailing_state_mixin.dart';
import 'package:bluebubbles/app/state/chat_state_scope.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class SamsungConversationTile extends CustomStateful<ConversationTileController> {
  const SamsungConversationTile({super.key, required super.parentController, this.deletedMode = false});

  final bool deletedMode;

  @override
  State<StatefulWidget> createState() => _SamsungConversationTileState();
}

class _SamsungConversationTileState extends CustomState<SamsungConversationTile, void, ConversationTileController> {
  bool get shouldPartialHighlight => controller.shouldPartialHighlight.value;

  bool get shouldHighlight => controller.shouldHighlight.value;

  @override
  void initState() {
    super.initState();
    tag = controller.chat.guid;
    // keep controller in memory since the widget is part of a list
    // (it will be disposed when scrolled out of view)
    forceDelete = false;
  }

  @override
  Widget build(BuildContext context) {
    final leading = ChatLeading(controller: controller, unreadIcon: UnreadIcon(parentController: controller));
    final child = Material(
      color: Colors.transparent,
      child: InkWell(
        mouseCursor: MouseCursor.defer,
        onTap: () => controller.onTap(context, widget.deletedMode),
        onSecondaryTapUp: widget.deletedMode ? null : (details) => controller.onSecondaryTap(Get.context!, details),
        onLongPress: widget.deletedMode ? null : controller.onLongPress,
        child: Obx(() => ListTile(
              mouseCursor: MouseCursor.defer,
              dense: SettingsSvc.settings.denseChatTiles.value,
              visualDensity: SettingsSvc.settings.denseChatTiles.value ? VisualDensity.compact : null,
              minVerticalPadding: SettingsSvc.settings.denseChatTiles.value ? 7.5 : 10,
              title: Obx(() => ChatTitle(
                    parentController: controller,
                    style: context.theme.textTheme.bodyLarge!.copyWith(
                      fontWeight: controller.shouldHighlight.value ? FontWeight.w600 : null,
                    ),
                  )),
              subtitle: widget.deletedMode
                  ? Builder(builder: (context) {
                      var count = controller.chat.messages.where((i) => i.dateDeleted != null).length;
                      return Text("$count message${count == 1 ? '' : 's'}");
                    })
                  : controller.subtitle ??
                      Obx(() => ChatSubtitle(
                            parentController: controller,
                            style: context.theme.textTheme.bodyMedium!.copyWith(
                              color: controller.shouldHighlight.value
                                  ? context.theme.colorScheme.onSurface
                                  : context.theme.colorScheme.outline,
                              height: 1.5,
                            ),
                          )),
              leading: leading,
              trailing: widget.deletedMode
                  ? Builder(builder: (context) {
                      DateTime oldestDeletion = DateTime.now();
                      for (var message in controller.chat.messages) {
                        if (message.dateDeleted == null) continue;
                        // we are less than the oldest
                        if (message.dateDeleted!.compareTo(oldestDeletion) < 0) {
                          oldestDeletion = message.dateDeleted!;
                        }
                      }

                      var deleteDate = oldestDeletion.add(const Duration(days: 30));
                      var diff = deleteDate.difference(DateTime.now());
                      String d;
                      if (diff.isNegative) {
                        d = "Pending Deletion";
                      } else if (diff.inDays != 0) {
                        d = "${diff.inDays}d";
                      } else if (diff.inHours != 0) {
                        d = "${diff.inHours}h";
                      } else {
                        d = "${diff.inMinutes}m";
                      }

                      var bodyStyle = context.theme.textTheme.bodySmall!
                          .copyWith(
                            color: controller.shouldHighlight.value
                                ? context.theme.colorScheme.onBubble(context, controller.chat.isIMessage)
                                : context.theme.colorScheme.outline,
                            fontWeight: controller.shouldHighlight.value ? FontWeight.w500 : null,
                          )
                          .apply(fontSizeFactor: 1.1);
                      return Padding(padding: const EdgeInsets.only(right: 8), child: Text(d, style: bodyStyle));
                    })
                  : SamsungTrailing(parentController: controller),
            )),
      ),
    );

    return ChatStateScope(
      chatState: controller.chatState,
      child: Obx(() {
        NavigationSvc.listener.value;
        return AnimatedContainer(
          clipBehavior: Clip.antiAlias,
          decoration: BoxDecoration(
            color: controller.isSelected
                ? context.theme.colorScheme.primaryContainer.withValues(alpha: 0.5)
                : shouldPartialHighlight
                    ? context.theme.colorScheme.surfaceContainerHighest
                    : shouldHighlight
                        ? context.theme.colorScheme.primaryContainer
                        : Colors.transparent,
          ),
          duration: const Duration(milliseconds: 100),
          child: NavigationSvc.isAvatarOnly(context)
              ? InkWell(
                  mouseCursor: MouseCursor.defer,
                  onTap: () => controller.onTap(context, widget.deletedMode),
                  onSecondaryTapUp:
                      widget.deletedMode ? null : (details) => controller.onSecondaryTap(Get.context!, details),
                  onLongPress: widget.deletedMode ? null : controller.onLongPress,
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 10.0, horizontal: 15),
                    child: Center(child: leading),
                  ),
                )
              : child,
        );
      }),
    );
  }
}

class SamsungTrailing extends CustomStateful<ConversationTileController> {
  const SamsungTrailing({super.key, required super.parentController});

  @override
  State<StatefulWidget> createState() => _SamsungTrailingState();
}

class _SamsungTrailingState extends CustomState<SamsungTrailing, void, ConversationTileController>
    with TrailingStateMixin<SamsungTrailing> {
  @override
  Widget build(BuildContext context) {
    final chatState = ChatStateScope.of(context);
    return Obx(() {
      final message = chatState.latestMessage.value;
      final indicator = computeIndicatorText(chatState.latestMessageStatus.value, controller.chat.isGroup);
      final hasError = (message?.error ?? 0) > 0;
      final unread = chatState.hasUnreadMessage.value;
      final muteType = chatState.muteType.value;
      final isPinned = chatState.isPinned.value;
      return Padding(
        padding: const EdgeInsets.only(right: 3),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.center,
          mainAxisAlignment: MainAxisAlignment.end,
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
                padding: const EdgeInsets.only(top: 1),
                child: Text(
                  hasError
                      ? "Error"
                      : "${indicator.isNotEmpty ? "$indicator\n" : ""}${buildDate(message?.chatViewDate)}",
                  textAlign: TextAlign.right,
                  style: context.theme.textTheme.bodySmall!.copyWith(
                    color: hasError
                        ? context.theme.colorScheme.error
                        : controller.shouldHighlight.value || unread
                            ? context.theme.colorScheme.onSurface
                            : context.theme.colorScheme.outline,
                    fontWeight: controller.shouldHighlight.value ? FontWeight.w500 : null,
                  ),
                  overflow: TextOverflow.clip,
                )),
            Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (isPinned) const SizedBox(width: 5.0),
                if (muteType == "mute")
                  Icon(
                    Icons.notifications_off,
                    color: controller.shouldHighlight.value || unread
                        ? context.theme.colorScheme.onSurface
                        : context.theme.colorScheme.outline,
                    size: 16,
                  ),
                if (muteType == "mute") const SizedBox(width: 2.0),
                if (isPinned) Icon(Icons.star, size: 16, color: context.theme.colorScheme.tertiary),
              ],
            ),
          ],
        ),
      );
    });
  }
}

class UnreadIcon extends CustomStateful<ConversationTileController> {
  const UnreadIcon({super.key, required super.parentController});

  @override
  State<StatefulWidget> createState() => _UnreadIconState();
}

class _UnreadIconState extends CustomState<UnreadIcon, void, ConversationTileController> {
  @override
  void initState() {
    super.initState();
    tag = controller.chat.guid;
    // keep controller in memory since the widget is part of a list
    // (it will be disposed when scrolled out of view)
    forceDelete = false;
  }

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      final unread = controller.chatState.hasUnreadMessage.value;
      return (unread)
          ? Container(
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: context.theme.colorScheme.primary,
              ),
              width: 15,
              height: 15,
            )
          : const SizedBox(width: 10, height: 10);
    });
  }
}
