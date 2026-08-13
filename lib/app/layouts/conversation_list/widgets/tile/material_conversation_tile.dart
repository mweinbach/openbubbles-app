import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/conversation_tile.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/trailing_state_mixin.dart';
import 'package:bluebubbles/app/state/chat_state_scope.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class MaterialConversationTile extends CustomStateful<ConversationTileController> {
  const MaterialConversationTile({super.key, required super.parentController, this.deletedMode = false});

  final bool deletedMode;

  @override
  State<StatefulWidget> createState() => _MaterialConversationTileState();
}

class _MaterialConversationTileState extends CustomState<MaterialConversationTile, void, ConversationTileController> {
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
    final leading = ChatLeading(controller: controller);
    final child = Material(
      color: Colors.transparent,
      borderRadius: const BorderRadius.only(
        topLeft: Radius.circular(25),
        bottomLeft: Radius.circular(25),
      ),
      child: InkWell(
        mouseCursor: MouseCursor.defer,
        onTap: () => controller.onTap(context, widget.deletedMode),
        onSecondaryTapUp: widget.deletedMode ? null : (details) => controller.onSecondaryTap(Get.context!, details),
        onLongPress: widget.deletedMode ? null : controller.onLongPress,
        borderRadius: const BorderRadius.only(
          topLeft: Radius.circular(20),
          bottomLeft: Radius.circular(20),
        ),
        child: ListTile(
          mouseCursor: MouseCursor.defer,
          dense: SettingsSvc.settings.denseChatTiles.value,
          visualDensity: SettingsSvc.settings.denseChatTiles.value ? VisualDensity.compact : null,
          minVerticalPadding: SettingsSvc.settings.denseChatTiles.value ? 7.5 : 10,
          title: Obx(() => ChatTitle(
                parentController: controller,
                style: context.theme.textTheme.bodyLarge!
                    .copyWith(
                      fontWeight: controller.shouldHighlight.value
                          ? FontWeight.w500
                          : (controller.hasUnreadReactive)
                              ? FontWeight.bold
                              : null,
                      color: ThemeSvc.isAnyMaterialYouSelected ? context.theme.colorScheme.onSurface : null,
                    )
                    .apply(fontSizeFactor: 1.1),
              )),
          subtitle: widget.deletedMode
              ? Builder(builder: (context) {
                  final count = controller.chat.messages.where((i) => i.dateDeleted != null).length;
                  return Text("$count message${count == 1 ? '' : 's'}");
                })
              : controller.subtitle ??
                  Obx(() {
                    final unread = controller.hasUnreadReactive;
                    final isMonet = ThemeSvc.isAnyMaterialYouSelected;
                    return ChatSubtitle(
                      parentController: controller,
                      style: context.theme.textTheme.bodyMedium!
                          .copyWith(
                            fontWeight: unread ? FontWeight.w500 : null,
                            color: controller.shouldHighlight.value || unread
                                ? isMonet
                                    ? context.theme.colorScheme.onSurface
                                    : context.textTheme.bodyMedium!.color
                                : isMonet
                                    ? context.theme.colorScheme.onSurfaceVariant
                                    : context.theme.colorScheme.outline,
                            height: 1.5,
                          )
                          .apply(fontSizeFactor: 1.05),
                    );
                  }),
          contentPadding: const EdgeInsets.only(left: 6, right: 16),
          leading: leading,
          trailing: widget.deletedMode
              ? Builder(builder: (context) {
                  DateTime oldestDeletion = DateTime.now();
                  for (final message in controller.chat.messages) {
                    if (message.dateDeleted == null) continue;
                    if (message.dateDeleted!.compareTo(oldestDeletion) < 0) {
                      oldestDeletion = message.dateDeleted!;
                    }
                  }

                  final deleteDate = oldestDeletion.add(const Duration(days: 30));
                  final diff = deleteDate.difference(DateTime.now());
                  final String d;
                  if (diff.isNegative) {
                    d = "Pending Deletion";
                  } else if (diff.inDays != 0) {
                    d = "${diff.inDays}d";
                  } else if (diff.inHours != 0) {
                    d = "${diff.inHours}h";
                  } else {
                    d = "${diff.inMinutes}m";
                  }

                  final bodyStyle = context.theme.textTheme.bodySmall!
                      .copyWith(
                        color: controller.shouldHighlight.value
                            ? context.theme.colorScheme.onBubble(context, controller.chat.isIMessage)
                            : context.theme.colorScheme.outline,
                        fontWeight: controller.shouldHighlight.value ? FontWeight.w500 : null,
                      )
                      .apply(fontSizeFactor: 1.1);
                  return Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: Text(d, style: bodyStyle),
                  );
                })
              : MaterialTrailing(parentController: controller),
        ),
      ),
    );

    return ChatStateScope(
      chatState: controller.chatState,
      child: Obx(() {
        NavigationSvc.listener.value;
        return AnimatedContainer(
          padding: const EdgeInsets.only(left: 10),
          clipBehavior: Clip.antiAlias,
          decoration: BoxDecoration(
            borderRadius: const BorderRadius.only(
              topLeft: Radius.circular(20),
              bottomLeft: Radius.circular(20),
            ),
            color: controller.isSelected
                ? context.theme.colorScheme.primaryContainer.withValues(alpha: 0.5)
                : shouldPartialHighlight
                    ? context.theme.colorScheme.surfaceContainerHighest
                    : shouldHighlight
                        ? context.theme.colorScheme.primaryContainer
                        : ThemeSvc.isMaterialYouActive(context)
                            ? context.theme.colorScheme.surface
                            : null,
          ),
          duration: const Duration(milliseconds: 100),
          child: NavigationSvc.isAvatarOnly(context)
              ? InkWell(
                  mouseCursor: MouseCursor.defer,
                  onTap: () => controller.onTap(context, widget.deletedMode),
                  onSecondaryTapUp:
                      widget.deletedMode ? null : (details) => controller.onSecondaryTap(Get.context!, details),
                  onLongPress: widget.deletedMode ? null : controller.onLongPress,
                  borderRadius: const BorderRadius.only(
                    topLeft: Radius.circular(20),
                    bottomLeft: Radius.circular(20),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 10.0, horizontal: 15.0),
                    child: Center(child: leading),
                  ),
                )
              : child,
        );
      }),
    );
  }
}

class MaterialTrailing extends CustomStateful<ConversationTileController> {
  const MaterialTrailing({super.key, required super.parentController});

  @override
  State<StatefulWidget> createState() => _MaterialTrailingState();
}

class _MaterialTrailingState extends CustomState<MaterialTrailing, void, ConversationTileController>
    with TrailingStateMixin<MaterialTrailing> {
  @override
  Widget build(BuildContext context) {
    final chatState = ChatStateScope.of(context);
    return Padding(
      padding: const EdgeInsets.only(right: 3),
      child: Obx(() {
        final message = chatState.latestMessage.value;
        final indicator = computeIndicatorText(chatState.latestMessageStatus.value, controller.chat.isGroup);
        final hasError = (message?.error ?? 0) > 0;
        final unread = chatState.hasUnreadMessage.value;
        final muteType = chatState.muteType.value;
        return Column(
          crossAxisAlignment: CrossAxisAlignment.end,
          mainAxisAlignment: MainAxisAlignment.center,
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Row(
              mainAxisSize: MainAxisSize.min,
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                Text(
                  hasError
                      ? "Error"
                      : "${indicator.isNotEmpty ? "$indicator\n" : ""}${buildChatListDateMaterial(message?.chatViewDate)}",
                  textAlign: TextAlign.right,
                  style: context.theme.textTheme.bodySmall!
                      .copyWith(
                        color: hasError
                            ? context.theme.colorScheme.error
                            : controller.shouldHighlight.value || unread
                                ? context.theme.colorScheme.onSurface
                                : context.theme.colorScheme.outline,
                        fontWeight: unread
                            ? FontWeight.w600
                            : controller.shouldHighlight.value
                                ? FontWeight.w500
                                : null,
                      )
                      .apply(fontSizeFactor: 1.1),
                  overflow: TextOverflow.clip,
                ),
              ],
            ),
            const SizedBox(height: 5),
            Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              mainAxisAlignment: MainAxisAlignment.end,
              mainAxisSize: MainAxisSize.min,
              children: [
                if (chatState.isPinned.value)
                  Icon(Icons.push_pin_outlined, size: 18, color: context.theme.colorScheme.outline),
                if (muteType != "mute" && unread) ...[
                  if (chatState.isPinned.value) const SizedBox(width: 5),
                  Container(
                    width: 13,
                    height: 13,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: context.theme.colorScheme.primary,
                    ),
                  ),
                ],
                if (muteType == "mute") const SizedBox(width: 5),
                if (muteType == "mute")
                  Icon(
                    Icons.notifications_off_outlined,
                    color: controller.shouldHighlight.value || unread
                        ? context.theme.colorScheme.primary
                        : context.theme.colorScheme.outline,
                    size: 18,
                  ),
              ],
            ),
          ],
        );
      }),
    );
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
    return Obx(() => Padding(
          padding: const EdgeInsets.only(left: 5.0, right: 5.0),
          child: (controller.hasUnreadReactive)
              ? Container(
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(35),
                    color: context.theme.colorScheme.primary,
                  ),
                  width: 10,
                  height: 10,
                )
              : const SizedBox(width: 10),
        ));
  }
}
