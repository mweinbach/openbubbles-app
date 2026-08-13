import 'dart:async';
import 'dart:io';

import 'package:bluebubbles/app/layouts/conversation_view/widgets/message/typing/typing_indicator.dart';
import 'package:bluebubbles/app/state/chat_state.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/app/layouts/conversation_list/pages/conversation_list.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/cupertino_conversation_tile.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/material_conversation_tile.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/samsung_conversation_tile.dart';
import 'package:bluebubbles/app/layouts/conversation_view/pages/conversation_view.dart';
import 'package:bluebubbles/app/components/avatars/contact_avatar_group_widget.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:dpad/dpad.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:universal_html/html.dart' as html;

class ConversationTileController extends StatefulController {
  final RxBool shouldHighlight = false.obs;
  final RxBool shouldPartialHighlight = false.obs;
  final RxBool hoverHighlight = false.obs;
  final ChatState chatState;
  final ConversationListController listController;
  final Function(bool)? onSelect;
  final bool inSelectMode;
  final Widget? subtitle;

  Chat get chat => chatState.chat;

  bool get isSelected => listController.selectedChats.firstWhereOrNull((e) => e.guid == chat.guid) != null;

  /// Unread state for the tile, read in an `Obx`-safe way.
  ///
  /// For an active chat we read the live [ChatState] tracked by [ChatsSvc] so the
  /// tile stays reactive. For a chat no longer tracked — e.g. a soft-deleted chat
  /// shown in Recently Deleted — [ChatsSvc.getChatState] returns null; we still
  /// touch an observable so the enclosing [Obx] registers a dependency (otherwise
  /// GetX throws "improper use of GetX" and the leading fails to build), but always
  /// report false since a deleted chat can't be unread.
  bool get hasUnreadReactive {
    final live = ChatsSvc.getChatState(chat.guid);
    if (live != null) return live.hasUnreadMessage.value;
    chatState.hasUnreadMessage.value; // touch an observable to satisfy Obx
    return false;
  }

  ConversationTileController({
    Key? key,
    required this.chatState,
    required this.listController,
    this.onSelect,
    this.inSelectMode = false,
    this.subtitle,
  });

  void onTap(BuildContext context, [bool deleteMode = false]) {
    if (deleteMode) {
      var chat = Chat.findOne(guid: this.chat.guid)!;
      final messages = chat.messages.where((i) => i.dateDeleted != null).length;

      DateTime oldestDeletion = DateTime.now();
      for (final message in chat.messages) {
        if (message.dateDeleted == null) continue;
        if (message.dateDeleted!.compareTo(oldestDeletion) < 0) {
          oldestDeletion = message.dateDeleted!;
        }
      }

      final deleteDate = oldestDeletion.add(const Duration(days: 30));
      final diff = deleteDate.difference(DateTime.now());
      final String d;
      if (diff.inDays != 0) {
        d = "${diff.inDays} days";
      } else if (diff.inHours != 0) {
        d = "${diff.inHours} hours";
      } else {
        d = "${diff.inMinutes} minutes";
      }

      showDialog(
        context: Get.context!,
        builder: (BuildContext context) {
          return AlertDialog(
            title: Text(
              "$messages message selected.",
              style: context.theme.textTheme.titleLarge,
            ),
            content: Text(
              diff.isNegative ? "They are to be deleted imminently." : "They will start being deleted $d from now.",
              style: context.theme.textTheme.bodyLarge,
            ),
            backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
            actions: <Widget>[
              TextButton(
                child: Text(
                  "Close",
                  style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary),
                ),
                onPressed: () {
                  Navigator.of(context, rootNavigator: true).pop();
                },
              ),
              TextButton(
                child: Text(
                  "Recover",
                  style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary),
                ),
                onPressed: () async {
                  await ChatsSvc.unDeleteChat(chat);
                  chat.restoreTranscript();
                  await ChatsSvc.addChat(chat);
                  chat.restoreTranscript();
                  Navigator.of(context, rootNavigator: true).pop();

                  await BackendSvc.restoreChat(chat);
                },
              ),
              TextButton(
                child: Text(
                  "Delete",
                  style: context.theme.textTheme.bodyLarge!.copyWith(color: Colors.red[700]),
                ),
                onPressed: () async {
                  final msg2 = chat;
                  if (msg2.dateDeleted != null) {
                    await ChatsSvc.deleteChat(msg2);
                  } else {
                    final query = (Database.messages.query(Message_.dateDeleted.notNull())
                          ..link(Message_.chat, Chat_.id.equals(msg2.id!)))
                        .build();
                    for (final message in query.find()) {
                      for (final attachment in (message.fetchAttachments() ?? [])) {
                        if (attachment == null) continue;
                        try {
                          File(attachment.getFile().path!).deleteSync();
                        } catch (_) {}
                      }
                      Message.delete(message.guid!);
                    }
                  }
                  Navigator.of(context, rootNavigator: true).pop();

                  await BackendSvc.permanentlyDeleteChat(chat);
                },
              ),
            ],
          );
        },
      );
      return;
    }

    if ((inSelectMode || listController.selectedChats.isNotEmpty) && onSelect != null) {
      onLongPress();
    } else if ((!kIsDesktop && !kIsWeb) || ChatsSvc.activeChat?.chat.guid != chat.guid) {
      NavigationSvc.pushAndRemoveUntil(
        context,
        ConversationView(
          chat: chat,
        ),
        (route) => route.isFirst,
      );
    } else if (NavigationSvc.isTabletMode(context) && ChatsSvc.activeChat?.isAlive.value == false) {
      // Pops chat details
      Get.back(id: 2);
    } else {
      cvc(chat).lastFocusedNode.requestFocus();
    }
  }

  Future<void> onSecondaryTap(BuildContext context, TapUpDetails details) async {
    if (kIsWeb) {
      (await html.document.onContextMenu.first).preventDefault();
    }
    shouldPartialHighlight.value = true;
    if (!context.mounted) return;
    await showConversationTileMenu(
      context,
      this,
      chat,
      details.globalPosition,
      context.textTheme,
    );
    shouldPartialHighlight.value = false;
  }

  void onLongPress() {
    onSelected();
    HapticFeedback.lightImpact();
  }

  void onSelected() {
    onSelect?.call(!isSelected);
    if (SettingsSvc.settings.skin.value == Skins.Material) {
      updateWidgets<MaterialConversationTile>(null);
    }
    if (SettingsSvc.settings.skin.value == Skins.Samsung) {
      updateWidgets<SamsungConversationTile>(null);
    }
  }
}

class ConversationTile extends CustomStateful<ConversationTileController> {
  ConversationTile({
    super.key,
    required Chat chat,
    required ConversationListController controller,
    Function(bool)? onSelect,
    bool inSelectMode = false,
    this.deletedMode = false,
    this.autofocus = false,
    Widget? subtitle,
  }) : super(
            parentController: !inSelectMode && Get.isRegistered<ConversationTileController>(tag: chat.guid)
                ? Get.find<ConversationTileController>(tag: chat.guid)
                : Get.put(
                    ConversationTileController(
                      chatState: ChatsSvc.getOrCreateChatState(chat),
                      listController: controller,
                      onSelect: onSelect,
                      inSelectMode: inSelectMode,
                      subtitle: subtitle,
                    ),
                    tag: inSelectMode ? randomString(8) : chat.guid,
                    permanent: kIsDesktop || kIsWeb));

  final bool deletedMode;
  final bool autofocus;

  @override
  State<ConversationTile> createState() => _ConversationTileState();
}

class _ConversationTileState extends CustomState<ConversationTile, void, ConversationTileController>
    with AutomaticKeepAliveClientMixin {
  ConversationListController get listController => controller.listController;

  // Bumped to re-create the DpadFocusable (re-running its autofocus) when asked to
  // re-focus the first chat, e.g. on returning from the new message page.
  int _refocusGen = 0;
  // One-shot: forces THIS tile to autofocus when asked to focus a specific chat (e.g. returning
  // to the list from that chat), even though it isn't the first/autofocus tile.
  bool _forceFocus = false;

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    tag = controller.chat.guid;
    // keep controller in memory since the widget is part of a list
    // (it will be disposed when scrolled out of view)
    forceDelete = false;

    if (kIsDesktop || kIsWeb) {
      controller.shouldHighlight.value = ChatsSvc.activeChat?.chat.guid == controller.chat.guid;
    }

    EventDispatcherSvc.stream.listen((event) {
      if (event.type == 'update-highlight' && mounted) {
        if ((kIsDesktop || kIsWeb) && event.data == controller.chat.guid) {
          controller.shouldHighlight.value = true;
        } else if (controller.shouldHighlight.value) {
          controller.shouldHighlight.value = false;
        }
      }
      // Re-focus the first chat (autofocus tile) on request, e.g. returning from the
      // new message page in D-pad mode.
      if (event.type == 'focus-first-chat' && mounted && widget.autofocus) {
        setState(() => _refocusGen++);
      }
      // Return focus to a specific chat (the one just exited) on request, in D-pad mode.
      if (event.type == 'focus-chat' && mounted && event.data == controller.chat.guid) {
        setState(() {
          _forceFocus = true;
          _refocusGen++;
        });
        // Clear the one-shot after the DpadFocusable has re-created and grabbed focus.
        WidgetsBinding.instance.addPostFrameCallback((_) => _forceFocus = false);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return Focus(
      canRequestFocus: false,
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent &&
            event.logicalKey == LogicalKeyboardKey.arrowRight &&
            !listController.showArchivedChats &&
            !listController.showUnknownSenders &&
            !listController.showDeletedMessages) {
          listController.newMessageFocusNode.requestFocus();
          return KeyEventResult.handled;
        }
        if (event is KeyDownEvent &&
            event.logicalKey == LogicalKeyboardKey.arrowDown &&
            !listController.showArchivedChats &&
            !listController.showUnknownSenders &&
            !listController.showDeletedMessages) {
          // Move to the next tile if there is one; from the last tile (nothing focusable
          // below), jump to the compose FAB.
          if (!FocusScope.of(context).focusInDirection(TraversalDirection.down)) {
            listController.newMessageFocusNode.requestFocus();
          }
          return KeyEventResult.handled;
        }
        if (event is KeyDownEvent &&
            event.logicalKey == LogicalKeyboardKey.arrowUp &&
            widget.autofocus /*autofocus to detect first one*/) {
          final scrollController = SettingsSvc.settings.skin.value == Skins.iOS
              ? listController.iosScrollController
              : SettingsSvc.settings.skin.value == Skins.Samsung
                  ? listController.samsungScrollController
                  : listController.materialScrollController;
          if (scrollController.hasClients && scrollController.offset > 0) {
            unawaited(scrollController.animateTo(
              0,
              duration: const Duration(milliseconds: 150),
              curve: Curves.easeOut,
            ));
            return KeyEventResult.ignored;
          }
        }
        return KeyEventResult.ignored;
      },
      child: DpadFocusable(
        key: ValueKey('dpad_${controller.chat.guid}_$_refocusGen'),
        onSelect: () => controller.onTap(context, widget.deletedMode),
        autofocus: (widget.autofocus || _forceFocus) && SettingsSvc.settings.isDumb.value,
        child: MouseRegion(
          onEnter: (event) => controller.hoverHighlight.value = true,
          onExit: (event) => controller.hoverHighlight.value = false,
          cursor: SystemMouseCursors.click,
          child: ThemeSwitcher(
            iOSSkin: CupertinoConversationTile(
              parentController: controller,
              deletedMode: widget.deletedMode,
            ),
            materialSkin: MaterialConversationTile(
              parentController: controller,
              deletedMode: widget.deletedMode,
            ),
            samsungSkin: SamsungConversationTile(
              parentController: controller,
              deletedMode: widget.deletedMode,
            ),
          ),
        ),
      ),
    );
  }
}

class ChatTitle extends CustomStateful<ConversationTileController> {
  const ChatTitle({super.key, required super.parentController, required this.style});

  final TextStyle style;

  @override
  State<StatefulWidget> createState() => _ChatTitleState();
}

class _ChatTitleState extends CustomState<ChatTitle, void, ConversationTileController> {
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
      // Get title from ChatState - it handles all title logic including redacted mode
      final _title = controller.chatState.title.value ?? controller.chat.getTitle();

      return RichText(
        text: TextSpan(
          children: MessageHelper.buildEmojiText(
            _title,
            widget.style,
          ),
        ),
        overflow: TextOverflow.ellipsis,
      );
    });
  }
}

class ChatSubtitle extends CustomStateful<ConversationTileController> {
  const ChatSubtitle({super.key, required super.parentController, required this.style});

  final TextStyle style;

  @override
  State<StatefulWidget> createState() => _ChatSubtitleState();
}

class _ChatSubtitleState extends CustomState<ChatSubtitle, void, ConversationTileController> {
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
      final chatState = controller.chatState;
      final latestMessage = chatState.latestMessage.value;
      final isFromMe = latestMessage?.isFromMe ?? false;
      final isDelivered =
          controller.chat.isGroup || !isFromMe || latestMessage?.isDelivered == true || latestMessage?.dateRead != null;

      // subtitle.value is already contact-info-free when redacted mode is on
      // (ChatState.redactContactInfo / updateChatLatestMessage ensure this).
      final String _subtitle = chatState.subtitle.value ?? '';

      // Draft detection — show "Draft: ..." when there is staged text or attachments.
      final draftText = chatState.textFieldText.value ?? '';
      final hasDraftText = draftText.isNotEmpty;
      final hasDraftAttachments = chatState.textFieldAttachments.isNotEmpty;
      final hasDraft = hasDraftText || hasDraftAttachments;

      final maxLines = SettingsSvc.settings.denseChatTiles.value ? 1 : 2;
      final lineHeight = (widget.style.fontSize ?? 14) * (widget.style.height ?? 1.5);

      // For material DMs with a message from me, show a delivery check icon
      // instead of italic styling — mirrors the Google Messages visual pattern.
      // Suppress when showing a draft so the layout stays clean.
      final showDeliveryIcon = material && isFromMe && !controller.chat.isGroup && !hasDraft;
      final isMonet = ThemeSvc.isAnyMaterialYouSelected;
      final iconColor = isMonet ? context.theme.colorScheme.primary : context.theme.colorScheme.outline;

      final TextSpan subtitleSpan;
      if (hasDraft) {
        final draftBody = hasDraftText ? draftText : 'Attachment';
        subtitleSpan = TextSpan(children: [
          TextSpan(
            text: 'Draft: ',
            style: widget.style.copyWith(
              color: context.theme.colorScheme.error,
              fontStyle: FontStyle.normal,
            ),
          ),
          ...MessageHelper.buildEmojiText(draftBody, widget.style),
        ]);
      } else {
        subtitleSpan = TextSpan(
          children: MessageHelper.buildEmojiText(
            "${!iOS && isFromMe ? "You: " : ""}$_subtitle",
            widget.style.copyWith(fontStyle: !iOS && !material && !isDelivered ? FontStyle.italic : null),
          ),
        );
      }

      final richText = RichText(
        text: subtitleSpan,
        overflow: TextOverflow.ellipsis,
        maxLines: maxLines,
      );

      return Padding(
        padding: const EdgeInsets.only(right: 10),
        child: ConstrainedBox(
          constraints: BoxConstraints(minHeight: lineHeight * (material ? 1 : maxLines)),
          child: showDeliveryIcon
              ? Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Padding(
                      padding: EdgeInsets.only(
                          right: 4, top: ((widget.style.fontSize ?? 14) * (widget.style.height ?? 1.5) - 14) / 2),
                      child: Opacity(
                        opacity: isDelivered ? 1.0 : 0.35,
                        child: Icon(
                          Icons.check_circle_outline,
                          size: 14,
                          color: iconColor,
                        ),
                      ),
                    ),
                    Expanded(child: richText),
                  ],
                )
              : richText,
        ),
      );
    });
  }
}

class ChatLeading extends StatefulWidget {
  final ConversationTileController controller;
  final Widget? unreadIcon;

  const ChatLeading({super.key, required this.controller, this.unreadIcon});

  @override
  ChatLeadingState createState() => ChatLeadingState();
}

class ChatLeadingState extends State<ChatLeading> with ThemeHelpers {
  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (widget.unreadIcon != null && iOS) widget.unreadIcon!,
        Obx(() {
          final showTypingIndicator = cvc(widget.controller.chat).showTypingIndicatorFor.isNotEmpty;
          double height = Theme.of(context).textTheme.labelLarge!.fontSize! * 1.25;
          return Stack(
            clipBehavior: Clip.none,
            children: <Widget>[
              Padding(
                padding: const EdgeInsets.only(top: 2, right: 2),
                child: widget.controller.isSelected
                    ? Container(
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(30),
                          color: context.theme.colorScheme.primary,
                        ),
                        width: SettingsSvc.settings.denseChatTiles.value ? 36 : (material ? 50 : 45),
                        height: SettingsSvc.settings.denseChatTiles.value ? 36 : (material ? 50 : 45),
                        child: Center(
                          child: Icon(
                            Icons.check,
                            color: context.theme.colorScheme.onPrimary,
                            size: 26,
                          ),
                        ),
                      )
                    : ContactAvatarGroupWidget(
                        chat: widget.controller.chat,
                        size: SettingsSvc.settings.denseChatTiles.value ? 36 : (material ? 50 : 45),
                        editable: false,
                      ),
              ),
              if (showTypingIndicator)
                Positioned(
                  top: 30,
                  left: 20,
                  height: height,
                  child: const FittedBox(
                    alignment: Alignment.centerLeft,
                    child: TypingIndicator(
                      visible: true,
                    ),
                  ),
                ),
              if (widget.unreadIcon != null && samsung)
                Positioned(
                  top: 0,
                  right: 0,
                  height: height * 0.75,
                  child: FittedBox(
                    alignment: Alignment.centerRight,
                    child: widget.unreadIcon,
                  ),
                ),
            ],
          );
        })
      ],
    );
  }
}
