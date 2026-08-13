import 'dart:async';
import 'dart:math';

import 'package:bluebubbles/app/components/avatars/contact_avatar_widget.dart';
import 'package:bluebubbles/app/layouts/chat_creator/chat_creator.dart';
import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/app/wrappers/bb_app_bar.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/app/layouts/conversation_list/pages/conversation_list.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/conversation_tile.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/pinned_conversation_tile.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/conversation_list_fab.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/header/cupertino_header.dart';
import 'package:bluebubbles/app/wrappers/scrollbar_wrapper.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_acrylic/flutter_acrylic.dart';
import 'package:get/get.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:smooth_page_indicator/smooth_page_indicator.dart';
import 'package:universal_io/io.dart';

class CupertinoConversationList extends StatefulWidget {
  const CupertinoConversationList({super.key, required this.parentController});

  final ConversationListController parentController;

  @override
  State<StatefulWidget> createState() => CupertinoConversationListState();
}

class CupertinoConversationListState extends State<CupertinoConversationList> with ThemeHelpers {
  bool get showArchived => widget.parentController.showArchivedChats;

  bool get showUnknown => widget.parentController.showUnknownSenders;

  bool get showDeleted => widget.parentController.showDeletedMessages;

  RxList<Chat> get deletedChats => widget.parentController.deletedChats;
  List<String> handles = [];
  bool canPnr = true;

  Color get backgroundColor => SettingsSvc.settings.windowEffect.value == WindowEffect.disabled
      ? context.theme.colorScheme.surface
      : Colors.transparent;

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
    (() async {
      await PushSvc.initFuture;
      handles = await api.getHandles(state: PushSvc.state!.client);
      final deviceState = await api.getDeviceInfo(config: PushSvc.state!.osConfig);
      canPnr =
          deviceState.name.contains("iPhone") || deviceState.name.contains("iPod") || deviceState.name.contains("iPad");
      if (mounted) setState(() {});
    })();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: SettingsSvc.settings.windowEffect.value != WindowEffect.disabled
          ? Colors.transparent
          : context.theme.colorScheme.surface,
      extendBodyBehindAppBar: !showArchived && !showUnknown && !showDeleted,
      floatingActionButton: Obx(() =>
          !SettingsSvc.settings.moveChatCreatorToHeader.value && !showArchived && !showUnknown && !showDeleted
              ? ConversationListFAB(parentController: controller)
              : const SizedBox.shrink()),
      appBar: showArchived || showUnknown || showDeleted
          ? BBAppBar(
              titleText: showDeleted
                  ? "Recently Deleted"
                  : showArchived
                      ? "Archive"
                      : "Unknown Senders",
              leading: buildBackButton(context),
              centerTitle: true,
              backgroundColor: Colors.transparent,
            )
          : null,
      body: Stack(
        children: [
          ScrollbarWrapper(
            showScrollbar: true,
            controller: controller.iosScrollController,
            child: Obx(() => CustomScrollView(
                  controller: controller.iosScrollController,
                  physics: ThemeSvc.scrollPhysics,
                  slivers: <Widget>[
                    if (!showArchived && !showUnknown && !showDeleted) CupertinoHeader(controller: controller),
                    Obx(() {
                      // Force reactivity by accessing observable values first
                      // ignore: unused_local_variable
                      final loaded = ChatsSvc.loadedFirstChatBatch.value;
                      // Observe chatListVersion so pinned section rebuilds when a chat is pinned/unpinned
                      // ignore: unused_local_variable
                      final _version = ChatsSvc.chatListVersion.value;
                      NavigationSvc.listener.value;
                      final _chats = showDeleted
                          ? <Chat>[].obs
                          : ChatsSvc.getFilteredChats(
                              showArchived: showArchived,
                              showUnknown: showUnknown,
                              pinnedOnly: true,
                            );

                      if (_chats.isEmpty) {
                        return const SliverToBoxAdapter(child: SizedBox.shrink());
                      }

                      int rowCount = context.mediaQuery.orientation == Orientation.portrait || kIsDesktop
                          ? SettingsSvc.settings.pinRowsPortrait.value
                          : SettingsSvc.settings.pinRowsLandscape.value;
                      int colCount = kIsDesktop
                          ? SettingsSvc.settings.pinColumnsLandscape.value
                          : SettingsSvc.settings.pinColumnsPortrait.value;
                      int pinCount = _chats.length;
                      int usedRowCount = min((pinCount / colCount).ceil(), rowCount);
                      int maxOnPage = rowCount * colCount;
                      PageController _controller = PageController();
                      int _pageCount = (pinCount / maxOnPage).ceil();

                      return SliverPadding(
                        padding: const EdgeInsets.only(top: 10),
                        sliver: SliverToBoxAdapter(
                          child: LayoutBuilder(builder: (BuildContext context, BoxConstraints constraints) {
                            // Horizontal overhead per tile: margins (4+4) + padding (11+11) + extra gap
                            const double tileHOverhead = 42.0;
                            // Vertical overhead per tile: AnimatedContainer margins (top:1) + padding (4+2)
                            //   + ChatTitle fixed padding (top:6 + bottom:4)
                            const double tileVOverhead = 17.0;
                            // PageView horizontal padding (10 each side)
                            const double pageHPadding = 20.0;

                            // Derive a clean, capped avatar size from the actual available width
                            final double rawAvatarSize =
                                (constraints.maxWidth - pageHPadding - colCount * tileHOverhead) / colCount;
                            final double avatarSize =
                                clampDouble(rawAvatarSize, 70.0, Platform.isAndroid ? 120.0 : 140.0);
                            final double tileWidth = avatarSize + tileHOverhead;

                            final TextStyle style = context.theme.textTheme.bodyMedium!;
                            final double textHeight = (style.height ?? 1.2) * (style.fontSize ?? 14);
                            final double tileHeight = avatarSize + textHeight + tileVOverhead;
                            final double totalHeight = usedRowCount * tileHeight;

                            // avatar only
                            if (NavigationSvc.isAvatarOnly(context)) {
                              return Column(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  ListView.builder(
                                    shrinkWrap: true,
                                    itemCount: _chats.length,
                                    findChildIndexCallback: (key) =>
                                        findChildIndexByKey(_chats, key, (item) => item.guid),
                                    itemBuilder: (context, index) {
                                      final chat = _chats[index];
                                      return Center(
                                        heightFactor: 1,
                                        child: ConversationTile(
                                          key: Key(chat.guid),
                                          chat: chat,
                                          controller: controller,
                                          autofocus: index == 0,
                                        ),
                                      );
                                    },
                                  ),
                                  Padding(
                                    padding: const EdgeInsets.symmetric(horizontal: 20),
                                    child: Divider(
                                      color: context.theme.colorScheme.outline.withValues(alpha: 0.5),
                                      thickness: 2,
                                      height: 2,
                                    ),
                                  )
                                ],
                              );
                            }

                            return Column(
                              children: <Widget>[
                                SizedBox(
                                  height: totalHeight,
                                  child: PageView.builder(
                                    clipBehavior: Clip.none,
                                    physics: const BouncingScrollPhysics(parent: AlwaysScrollableScrollPhysics()),
                                    scrollDirection: Axis.horizontal,
                                    controller: _controller,
                                    itemCount: _pageCount,
                                    itemBuilder: (context, pageIndex) {
                                      final int start = pageIndex * maxOnPage;
                                      final List<Chat> pageChats =
                                          _chats.sublist(start, min(start + maxOnPage, pinCount));

                                      return Padding(
                                        padding: const EdgeInsets.symmetric(horizontal: 10),
                                        child: Column(
                                          mainAxisAlignment: MainAxisAlignment.center,
                                          children: List.generate(usedRowCount, (rowIndex) {
                                            final int rowStart = rowIndex * colCount;
                                            final List<Chat> rowChats =
                                                pageChats.skip(rowStart).take(colCount).toList();
                                            final bool singleRow = usedRowCount == 1;

                                            return Row(
                                              mainAxisAlignment:
                                                  singleRow ? MainAxisAlignment.center : MainAxisAlignment.start,
                                              children: [
                                                for (final chat in rowChats)
                                                  SizedBox(
                                                    width: tileWidth,
                                                    child: PinnedConversationTile(
                                                      key: Key(chat.guid),
                                                      chat: chat,
                                                      avatarSize: avatarSize,
                                                      controller: controller,
                                                    ),
                                                  ),
                                                // Fill empty slots in multi-row mode so rows align
                                                if (!singleRow)
                                                  for (int i = rowChats.length; i < colCount; i++)
                                                    SizedBox(width: tileWidth),
                                              ],
                                            );
                                          }),
                                        ),
                                      );
                                    },
                                  ),
                                ),
                                if (_pageCount > 1)
                                  MouseRegion(
                                    cursor: MouseCursor.defer,
                                    hitTestBehavior: HitTestBehavior.deferToChild,
                                    child: Padding(
                                      padding: const EdgeInsets.only(bottom: 10),
                                      child: SmoothPageIndicator(
                                        count: _pageCount,
                                        controller: _controller,
                                        onDotClicked: kIsDesktop || kIsWeb
                                            ? (page) => _controller.animateToPage(
                                                  page,
                                                  curve: Curves.linear,
                                                  duration: const Duration(milliseconds: 150),
                                                )
                                            : null,
                                        effect: ColorTransitionEffect(
                                          activeDotColor: context.theme.colorScheme.primary,
                                          dotColor: context.theme.colorScheme.outline,
                                          dotWidth: avatarSize * 0.1,
                                          dotHeight: avatarSize * 0.1,
                                          spacing: avatarSize * 0.07,
                                        ),
                                      ),
                                    ),
                                  ),
                              ],
                            );
                          }),
                        ),
                      );
                    }),
                    Obx(() {
                      // Force reactivity by accessing observable values first
                      final loaded = ChatsSvc.loadedFirstChatBatch.value;
                      // Observe chat list version to trigger rebuild when order changes
                      final _ = ChatsSvc.chatListVersion.value;
                      final _chats = showDeleted
                          ? deletedChats
                          : ChatsSvc.getFilteredChats(
                              showArchived: showArchived,
                              showUnknown: showUnknown,
                              excludePinned: true,
                            );
                      final _pinnedChats = showDeleted
                          ? <Chat>[].obs
                          : ChatsSvc.getFilteredChats(
                              showArchived: showArchived,
                              showUnknown: showUnknown,
                              pinnedOnly: true,
                            );
                      final hasPinnedChats = _pinnedChats.isNotEmpty;

                      if (!loaded || _chats.isEmpty) {
                        return SliverToBoxAdapter(
                          child: Center(
                            child: Padding(
                              padding: const EdgeInsets.only(top: 50.0),
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
                                      style: context.textTheme.labelLarge,
                                      textAlign: TextAlign.center,
                                    ),
                                  ),
                                  if (!loaded) buildProgressIndicator(context, size: 15),
                                ],
                              ),
                            ),
                          ),
                        );
                      }

                      return SliverPadding(
                        padding: const EdgeInsets.only(top: 10),
                        sliver: SliverList(
                          delegate: SliverChildBuilderDelegate(
                            (context, index) {
                              final chat = showDeleted ? _chats[index] : ChatsSvc.findChatByGuid(_chats[index].guid)!;

                              // No need for Obx here - ConversationTile handles its own reactivity
                              final child = ConversationTile(
                                key: Key(chat.guid),
                                chat: chat,
                                deletedMode: showDeleted,
                                controller: controller,
                                autofocus: index == 0,
                              );

                              final separator = Obx(() => !SettingsSvc.settings.hideDividers.value
                                  ? Padding(
                                      padding:
                                          EdgeInsets.only(left: SettingsSvc.settings.denseChatTiles.value ? 70 : 82),
                                      child: Divider(
                                        color: context.theme.colorScheme.outline.withValues(alpha: 0.4),
                                        thickness: 0.5,
                                        height: 0.5,
                                      ),
                                    )
                                  : const SizedBox.shrink());

                              final topDivider = index == 0 && !hasPinnedChats
                                  ? Obx(() => !SettingsSvc.settings.hideDividers.value
                                      ? Padding(
                                          padding: EdgeInsets.only(
                                              left: SettingsSvc.settings.denseChatTiles.value ? 70 : 82),
                                          child: Divider(
                                            color: context.theme.colorScheme.outline.withValues(alpha: 0.4),
                                            thickness: 0.5,
                                            height: 0.5,
                                          ),
                                        )
                                      : const SizedBox.shrink())
                                  : const SizedBox.shrink();

                              return Column(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  topDivider,
                                  child,
                                  separator,
                                ],
                              );
                            },
                            childCount: _chats.length,
                          ),
                        ),
                      );
                    }),
                  ],
                )),
          ),
          if (!showArchived && !showUnknown && !showDeleted) CupertinoMiniHeader(controller: controller),
          if (!showArchived && !showUnknown && !showDeleted) Obx(() {
            final _ = ChatsSvc.chatListVersion.value;
            if (!(ChatsSvc.isEmpty && ChatsSvc.loadedFirstChatBatch.value && !SettingsSvc.settings.isDumb.value))
              return SizedBox.shrink();

            return Positioned(
              bottom: 0,
              right: 0,
              left: 0,
              child: Container(
                decoration: const BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topLeft,
                    end: Alignment.bottomLeft,
                    colors: [
                      Color(0x005CA7F8),
                      Color(0xFF5CA7F8),
                    ],
                  ),
                ),
                padding: const EdgeInsets.fromLTRB(10, 150, 10, 100),
                child: Obx(
                  () => Column(
                    children: [
                      Center(
                        child: Text(
                          'Start chatting',
                          style: TextStyle(
                            fontSize: 32.0,
                            fontWeight: FontWeight.bold,
                            color: context.theme.colorScheme.onSurface,
                          ),
                        ),
                      ),
                      const SizedBox(height: 32),
                      Text(
                        "You can be reached on iMessage at",
                        style: context.theme.textTheme.bodyLarge!
                            .apply(fontSizeDelta: 1.5, color: context.theme.colorScheme.onSurface)
                            .copyWith(height: 2),
                      ),
                      ...handles.map(
                        (e) => Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 0, vertical: 8),
                          child: Text(
                            e.replaceFirst("tel:", "").replaceAll("mailto:", ""),
                            style:
                                context.theme.textTheme.titleMedium?.apply(color: context.theme.colorScheme.onSurface),
                          ),
                        ),
                      ),
                      if (!canPnr && !kIsDesktop)
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 0, vertical: 8),
                          child: GestureDetector(
                            onTap: () {
                              PushSvc.wantAddNumber();
                            },
                            child: Row(
                              mainAxisAlignment: MainAxisAlignment.center,
                              children: [
                                Icon(Icons.add, color: context.theme.colorScheme.onSurface),
                                const SizedBox(width: 5),
                                Text(
                                  "Add your number",
                                  style: context.theme.textTheme.titleMedium
                                      ?.apply(color: context.theme.colorScheme.onSurface),
                                ),
                              ],
                            ),
                          ),
                        ),
                      if (ChatsSvc.suggestedHandles.isNotEmpty) const SizedBox(height: 16),
                      if (ChatsSvc.suggestedHandles.isNotEmpty)
                        Padding(
                          padding: const EdgeInsets.only(left: 5),
                          child: Align(
                            alignment: Alignment.centerLeft,
                            child: Text(
                              "Start Chatting",
                              style: context.theme.textTheme.titleMedium?.apply(
                                color: context.theme.colorScheme.onSurface,
                                heightDelta: 1,
                              ),
                            ),
                          ),
                        ),
                      if (ChatsSvc.suggestedHandles.isNotEmpty)
                        Container(
                          decoration: BoxDecoration(
                            color: tileColor.withAlpha(210),
                            borderRadius: BorderRadius.circular(10),
                          ),
                          padding: const EdgeInsets.all(5),
                          child: Column(
                            children: ChatsSvc.suggestedHandles
                                .map(
                                  (child) => InkWell(
                                    onTap: () {
                                      NavigationSvc.pushAndRemoveUntil(
                                        context,
                                        ChatCreator(
                                          initialSelected: [
                                            SelectedContact(
                                              displayName: child.displayName,
                                              address: child.address,
                                              serviceType: ChatServiceType.iMessage,
                                            ),
                                          ],
                                        ),
                                        (route) => route.isFirst,
                                      );
                                    },
                                    child: Container(
                                      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 5),
                                      child: Row(
                                        children: [
                                          ContactAvatarWidget(
                                            handle: child,
                                            size: 38,
                                            preferHighResAvatar: true,
                                            scaleSize: false,
                                          ),
                                          const SizedBox(width: 10),
                                          Text(child.displayName, style: context.theme.textTheme.titleMedium),
                                        ],
                                      ),
                                    ),
                                  ),
                                )
                                .toList(),
                          ),
                        ),
                    ],
                  ),
                ),
              ),
            );
          }),
        ],
      ),
    );
  }
}
