import 'package:bluebubbles/app/layouts/conversation_list/dialogs/conversation_peek_view.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/conversation_tile.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/trailing_state_mixin.dart';
import 'package:bluebubbles/app/state/chat_state_scope.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class CupertinoConversationTile extends CustomStateful<ConversationTileController> {
  const CupertinoConversationTile({super.key, required super.parentController, this.deletedMode = false});

  final bool deletedMode;

  @override
  State<StatefulWidget> createState() => _CupertinoConversationTileState();
}

class _CupertinoConversationTileState extends CustomState<CupertinoConversationTile, void, ConversationTileController> {
  Offset? longPressPosition;

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
    final leading = ChatLeading(
      controller: controller,
      unreadIcon: UnreadIcon(parentController: controller),
    );
    final child = Material(
      color: Colors.transparent,
      child: InkWell(
        mouseCursor: MouseCursor.defer,
        onTap: () => controller.onTap(context, widget.deletedMode),
        onSecondaryTapUp: widget.deletedMode ? null : (details) => controller.onSecondaryTap(Get.context!, details),
        onLongPress: kIsDesktop || kIsWeb || widget.deletedMode
            ? null
            : () async {
                await peekChat(context, controller.chat, longPressPosition ?? Offset.zero);
              },
        onTapDown: (details) {
          longPressPosition = details.globalPosition;
        },
        child: Obx(() => ListTile(
            mouseCursor: MouseCursor.defer,
            enableFeedback: true,
            dense: SettingsSvc.settings.denseChatTiles.value,
            contentPadding: const EdgeInsets.only(left: 0),
            visualDensity: SettingsSvc.settings.denseChatTiles.value ? VisualDensity.compact : null,
            minVerticalPadding: 6,
            horizontalTitleGap: 10,
            title: Row(
              children: [
                Expanded(
                  child: ChatTitle(
                    parentController: controller,
                    style: context.theme.textTheme.bodyLarge!.copyWith(
                        fontWeight: controller.shouldHighlight.value ? FontWeight.w600 : FontWeight.w500,
                        color: controller.shouldHighlight.value
                            ? context.theme.colorScheme.onBubble(context, controller.chat.isIMessage)
                            : null),
                  ),
                ),
                if (!widget.deletedMode) const SizedBox(width: 10),
                if (!widget.deletedMode) CupertinoTrailing(parentController: controller),
                if (widget.deletedMode)
                  Builder(builder: (context) {
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
                  }),
              ],
            ),
            subtitle: Padding(
              padding: const EdgeInsets.only(right: 20.0),
              child: widget.deletedMode
                  ? Builder(builder: (context) {
                      final count = controller.chat.messages.where((i) => i.dateDeleted != null).length;
                      return Text("$count message${count == 1 ? '' : 's'}");
                    })
                  : controller.subtitle ??
                      ChatSubtitle(
                        parentController: controller,
                        style: context.theme.textTheme.bodyMedium!.copyWith(
                          color: controller.shouldHighlight.value
                              ? context.theme.colorScheme
                                  .onBubble(context, controller.chat.isIMessage)
                                  .withValues(alpha: 0.85)
                              : context.theme.colorScheme.outline,
                          height: 1.5,
                        ),
                      ),
            ),
            leading: leading)),
      ),
    );

    return ChatStateScope(
      chatState: controller.chatState,
      child: Obx(() {
        NavigationSvc.listener.value;
        return AnimatedContainer(
          duration: const Duration(milliseconds: 100),
          decoration: BoxDecoration(
            color: controller.shouldPartialHighlight.value
                ? context.theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.5)
                : controller.shouldHighlight.value
                    ? context.theme.colorScheme.bubble(context, controller.chat.isIMessage)
                    : Colors.transparent,
            borderRadius: BorderRadius.circular(
                controller.shouldHighlight.value || controller.shouldPartialHighlight.value ? 8 : 0),
          ),
          child: NavigationSvc.isAvatarOnly(context)
              ? InkWell(
                  mouseCursor: MouseCursor.defer,
                  onTap: () => controller.onTap(context, widget.deletedMode),
                  onSecondaryTapUp:
                      widget.deletedMode ? null : (details) => controller.onSecondaryTap(Get.context!, details),
                  onLongPress: kIsDesktop || kIsWeb || widget.deletedMode
                      ? null
                      : () async {
                          await peekChat(context, controller.chat, longPressPosition ?? Offset.zero);
                        },
                  onTapDown: (details) {
                    longPressPosition = details.globalPosition;
                  },
                  child: Padding(
                    padding: EdgeInsets.symmetric(vertical: 10.0, horizontal: (NavigationSvc.width(context) - 100) / 2)
                        .add(const EdgeInsets.only(right: 15)),
                    child: leading,
                  ),
                )
              : child,
        );
      }),
    );
  }
}

class CupertinoTrailing extends CustomStateful<ConversationTileController> {
  const CupertinoTrailing({super.key, required super.parentController});

  @override
  State<StatefulWidget> createState() => _CupertinoTrailingState();
}

class _CupertinoTrailingState extends CustomState<CupertinoTrailing, void, ConversationTileController>
    with TrailingStateMixin<CupertinoTrailing> {
  @override
  Widget build(BuildContext context) {
    final chatState = ChatStateScope.of(context);
    return Padding(
      padding: const EdgeInsets.only(right: 15),
      child: Obx(() {
        final message = chatState.latestMessage.value;
        final indicator = computeIndicatorText(chatState.latestMessageStatus.value, controller.chat.isGroup);
        final hasError = (message?.error ?? 0) > 0;
        return Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.end,
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Text(
              hasError ? "Error" : "${indicator.isNotEmpty ? "$indicator\n" : ""}${buildDate(message?.chatViewDate)}",
              textAlign: TextAlign.right,
              style: context.theme.textTheme.bodySmall!
                  .copyWith(
                    color: hasError
                        ? context.theme.colorScheme.error
                        : controller.shouldHighlight.value
                            ? context.theme.colorScheme.onBubble(context, controller.chat.isIMessage)
                            : context.theme.colorScheme.outline.withValues(alpha: 0.75),
                    fontWeight: controller.shouldHighlight.value ? FontWeight.w500 : null,
                  )
                  .apply(fontSizeFactor: 1.15),
              overflow: TextOverflow.clip,
            ),
            const SizedBox(width: 8),
            Stack(
              clipBehavior: Clip.none,
              alignment: Alignment.topCenter,
              children: [
                Padding(
                  padding: const EdgeInsets.only(top: 1),
                  child: Icon(
                    CupertinoIcons.forward,
                    color: controller.shouldHighlight.value
                        ? context.theme.colorScheme.onBubble(context, controller.chat.isIMessage)
                        : context.theme.colorScheme.outline.withValues(alpha: 0.75),
                    size: 16,
                  ),
                ),
                if (chatState.muteType.value == "mute")
                  Positioned(
                    top: 22,
                    left: 0,
                    right: 0,
                    child: Icon(
                      CupertinoIcons.bell_slash_fill,
                      color: controller.shouldHighlight.value
                          ? context.theme.colorScheme.onBubble(context, controller.chat.isIMessage)
                          : context.theme.colorScheme.outline.withValues(alpha: 0.85),
                      size: 12,
                    ),
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
    return Padding(
        padding: const EdgeInsets.only(left: 8.0, right: 6.0),
        child: Obx(
          () => (controller.hasUnreadReactive)
              ? Container(
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(35),
                    color: context.theme.colorScheme.primary,
                  ),
                  width: 12,
                  height: 12,
                )
              : const SizedBox(width: 12),
        ));
  }
}
