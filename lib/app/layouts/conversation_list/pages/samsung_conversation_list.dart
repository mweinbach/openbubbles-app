import 'package:bluebubbles/app/components/sliver_decoration.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/app/layouts/conversation_list/pages/conversation_list.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/conversation_list_fab.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/footer/samsung_footer.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/header/samsung_header.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/list_item.dart';
import 'package:bluebubbles/app/wrappers/scrollbar_wrapper.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_acrylic/flutter_acrylic.dart';
import 'package:get/get.dart';

class SamsungConversationList extends StatefulWidget {
  const SamsungConversationList({super.key, required this.parentController});

  final ConversationListController parentController;

  @override
  State<SamsungConversationList> createState() => _SamsungConversationListState();
}

class _SamsungConversationListState extends State<SamsungConversationList> with ThemeHelpers {
  bool get showArchived => widget.parentController.showArchivedChats;
  bool get showUnknown => widget.parentController.showUnknownSenders;
  bool get showDeleted => widget.parentController.showDeletedMessages;
  RxList<Chat> get deletedChats => widget.parentController.deletedChats;
  Color get backgroundColor =>
      SettingsSvc.settings.windowEffect.value == WindowEffect.disabled ? headerColor : Colors.transparent;
  Color get _tileColor =>
      SettingsSvc.settings.windowEffect.value == WindowEffect.disabled ? tileColor : Colors.transparent;
  ConversationListController get controller => widget.parentController;

  @override
  void initState() {
    super.initState();
    // update widget when background color changes
    if (kIsDesktop) {
      SettingsSvc.settings.windowEffect.listen((WindowEffect effect) {
        if (mounted) {
          setState(() {});
        }
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return PopScope(
      canPop: false,
      onPopInvokedWithResult: <T>(bool didPop, T? other) {
        if (didPop) return;
        if (controller.selectedChats.isNotEmpty) {
          controller.clearSelectedChats();
          return;
        } else if (controller.showArchivedChats || controller.showUnknownSenders || controller.showDeletedMessages) {
          // Pop the current page
          Navigator.of(context).pop();
        } else {
          // Pop the app to exit the app
          SystemNavigator.pop();
        }
      },
      child: Scaffold(
        backgroundColor: backgroundColor,
        floatingActionButton: !showArchived && !showUnknown && !showDeleted
            ? ConversationListFAB(parentController: controller)
            : const SizedBox.shrink(),
        body: SafeArea(
          child: NotificationListener<ScrollEndNotification>(
            onNotification: (_) {
              if (kIsWeb || kIsDesktop) return false;
              final scrollDistance = context.height / 3 - 57;
              if (controller.samsungScrollController.offset > 0 &&
                  controller.samsungScrollController.offset < scrollDistance &&
                  controller.samsungScrollController.offset !=
                      controller.samsungScrollController.position.maxScrollExtent) {
                final double snapOffset =
                    controller.samsungScrollController.offset / scrollDistance > 0.5 ? scrollDistance : 0;

                Future.microtask(() => controller.samsungScrollController
                    .animateTo(snapOffset, duration: const Duration(milliseconds: 200), curve: Curves.linear));
              }
              return false;
            },
            child: ScrollbarWrapper(
              showScrollbar: true,
              controller: controller.samsungScrollController,
              child: Obx(() {
                // Force reactivity by accessing observable values first
                final loaded = ChatsSvc.loadedFirstChatBatch.value;
                // Observe chat list version to trigger rebuild when order changes
                final _ = ChatsSvc.chatListVersion.value;
                final _chats = showDeleted
                    ? deletedChats
                    : ChatsSvc.getFilteredChats(
                        showArchived: controller.showArchivedChats,
                        showUnknown: controller.showUnknownSenders,
                      );
                final _pinnedChats = _chats.where((e) => e.isPinned ?? false).toList();
                final _unpinnedChats = _chats.where((e) => !(e.isPinned ?? false)).toList();

                return CustomScrollView(
                  physics: ThemeSwitcher.getScrollPhysics(),
                  controller: controller.samsungScrollController,
                  slivers: [
                    SamsungHeader(parentController: controller),
                    if (!loaded || _unpinnedChats.isEmpty)
                      SliverToBoxAdapter(
                        child: Center(
                          child: Padding(
                            padding: const EdgeInsets.only(top: 50),
                            child: Column(
                              children: [
                                Padding(
                                  padding: const EdgeInsets.all(8.0),
                                  child: Text(
                                    !loaded
                                        ? "Loading chats..."
                                        : showArchived
                                            ? "You have no archived chats"
                                            : showUnknown
                                                ? "You have no messages from unknown senders :)"
                                                : showDeleted
                                                    ? "You have no deleted chats"
                                                    : "Future chats will show here",
                                    style: context.theme.textTheme.labelLarge,
                                    textAlign: TextAlign.center,
                                  ),
                                ),
                                if (!loaded) buildProgressIndicator(context, size: 15),
                              ],
                            ),
                          ),
                        ),
                      ),
                    if (_pinnedChats.isNotEmpty)
                      SliverPadding(
                        padding: const EdgeInsets.only(bottom: 15),
                        sliver: SliverDecoration(
                          color: _tileColor,
                          borderRadius: BorderRadius.circular(25),
                          sliver: SliverList(
                              delegate: SliverChildBuilderDelegate(
                            (context, index) {
                              final chat = _pinnedChats[index];
                              return ListItem(
                                  chat: chat,
                                  controller: controller,
                                  showDeleted: showDeleted,
                                  autofocus: index == 0,
                                  update: () {
                                    setState(() {});
                                  });
                            },
                            childCount: _pinnedChats.length,
                          )),
                        ),
                      ),
                    SliverPadding(
                      padding: const EdgeInsets.only(bottom: 15),
                      sliver: SliverDecoration(
                        color: _tileColor,
                        borderRadius: BorderRadius.circular(25),
                        sliver: SliverList(
                          delegate: SliverChildBuilderDelegate(
                            (context, index) {
                              final chat = _unpinnedChats[index];
                              return ListItem(
                                  chat: chat,
                                  controller: controller,
                                  showDeleted: showDeleted,
                                  autofocus: _pinnedChats.isEmpty && index == 0,
                                  update: () {
                                    setState(() {});
                                  });
                            },
                            childCount: _unpinnedChats.length,
                          ),
                        ),
                      ),
                    ),
                  ],
                );
              }),
            ),
          ),
        ),
        bottomNavigationBar: SamsungFooter(parentController: controller),
      ),
    );
  }
}
