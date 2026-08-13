import 'package:auto_size_text/auto_size_text.dart';
import 'package:bluebubbles/app/components/avatars/contact_avatar_widget.dart';
import 'package:bluebubbles/app/layouts/conversation_list/pages/search/search_view.dart';
import 'package:bluebubbles/app/layouts/conversation_view/pages/conversation_view.dart';
import 'package:bluebubbles/app/layouts/findmy/findmy_page.dart';
import 'package:bluebubbles/app/layouts/facetime/facetime.dart';
import 'package:bluebubbles/app/layouts/settings/pages/misc/shared_streams_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/passwords/passwords_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/profile/profile_panel.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/app/layouts/conversation_list/pages/conversation_list.dart';
import 'package:bluebubbles/app/layouts/settings/settings_page.dart';
import 'package:bluebubbles/app/layouts/setup/setup_view.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/app/wrappers/titlebar_wrapper.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:dpad/dpad.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_acrylic/flutter_acrylic.dart';
import 'package:get/get.dart';
import 'package:pull_down_button/pull_down_button.dart';

class HeaderText extends StatelessWidget {
  const HeaderText({super.key, required this.controller, this.fontSize});

  final ConversationListController controller;
  final double? fontSize;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(right: 10.0),
      child: AutoSizeText(
        controller.showArchivedChats
            ? "Archive"
            : controller.showUnknownSenders
                ? "Unknown Senders"
                : controller.showDeletedMessages
                    ? "Recently Deleted"
                    : "Messages",
        style: context.textTheme.headlineLarge!.copyWith(
          color: context.theme.colorScheme.onSurface,
          fontWeight: FontWeight.w600,
          fontSize: fontSize,
        ),
        maxLines: 1,
      ),
    );
  }
}

class SyncIndicator extends StatelessWidget {
  final double size;

  const SyncIndicator({super.key, this.size = 12});

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      if (!SettingsSvc.settings.showSyncIndicator.value || !SyncSvc.isIncrementalSyncing.value) {
        return const SizedBox.shrink();
      }
      return buildProgressIndicator(context, size: size);
    });
  }
}

class OverflowMenu extends StatelessWidget {
  final bool extraItems;
  final ConversationListController? controller;
  const OverflowMenu({super.key, this.extraItems = false, this.controller});

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      // The native iOS PullDownButton menu items have no focus/keyboard support, so they
      // can't be reached with a D-pad. In "dumb" (D-pad) mode fall back to the custom
      // overlay menu, which is fully focus-navigable, for every skin.
      if (SettingsSvc.settings.skin.value == Skins.iOS && !SettingsSvc.settings.isDumb.value) {
        return CupertinoOverflowMenu(extraItems: extraItems, controller: controller);
      }

      return MaterialAvatarMenu(controller: controller, extraItems: extraItems);
    });
  }
}

class MaterialAvatarMenu extends StatefulWidget {
  const MaterialAvatarMenu({
    super.key,
    required this.controller,
    required this.extraItems,
  });

  final ConversationListController? controller;
  final bool extraItems;

  @override
  State<MaterialAvatarMenu> createState() => _MaterialAvatarMenuState();
}

class _MaterialAvatarMenuState extends State<MaterialAvatarMenu> {
  bool _menuOpen = false;

  void _showMenu() {
    if (_menuOpen) return;
    final box = context.findRenderObject() as RenderBox?;
    // Use the nearest navigator (under AdaptiveTheme/GetMaterialApp) — NOT the root
    // navigator, which lives above AdaptiveTheme and would break theming inside the menu.
    final navigator = Navigator.of(context);
    final overlayBox = navigator.overlay?.context.findRenderObject() as RenderBox?;
    if (box == null || !box.hasSize || overlayBox == null) return;
    // Anchor the menu to the avatar's position, in overlay coordinates.
    final anchorRect = box.localToGlobal(Offset.zero, ancestor: overlayBox) & box.size;
    _menuOpen = true;
    // Showing the menu as a route means the system/D-pad back button pops the menu
    // instead of the app, and tapping the barrier dismisses it.
    navigator.push(_AvatarMenuRoute(anchorRect: anchorRect, builder: _buildMenuCard)).whenComplete(() {
      _menuOpen = false;
    });
  }

  // Returns a Future so the existing `_hideMenu().then(...)` item handlers keep working;
  // the pop itself is synchronous, the future just lets the action run right after.
  Future<void> _hideMenu() async {
    if (_menuOpen) Navigator.of(context).pop();
  }

  Widget _buildMenuCard(BuildContext overlayContext) {
    final navContext = context;
    final windowEffect = SettingsSvc.settings.windowEffect.value;
    final cardColor = overlayContext.theme.colorScheme.surfaceContainerHighest
        .withValues(alpha: windowEffect != WindowEffect.disabled ? 0.95 : 1.0);
    final filterUnknownSenders = SettingsSvc.settings.filterUnknownSenders.value;
    final moveChatCreatorToHeader = SettingsSvc.settings.moveChatCreatorToHeader.value;
    final userName = SettingsSvc.settings.userName.value;
    final iCloudAccount = SettingsSvc.settings.iCloudAccount.value;
    final isDumb = SettingsSvc.settings.isDumb.value;
    final screenWidth = MediaQuery.of(overlayContext).size.width;
    final menuWidth = screenWidth - 16 < 240 ? screenWidth - 16 : 240.0;

    return CallbackShortcuts(
      // Hardware-keyboard escape also dismisses (Android/D-pad back is handled by the route).
      bindings: {
        const SingleActivator(LogicalKeyboardKey.escape): _hideMenu,
      },
      child: Material(
        elevation: 8,
        borderRadius: BorderRadius.circular(16),
        color: cardColor,
        child: SizedBox(
          width: menuWidth,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(16),
            child: SingleChildScrollView(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                            // Profile header
                            InkWell(
                              borderRadius: const BorderRadius.only(
                                topLeft: Radius.circular(16),
                                topRight: Radius.circular(16),
                              ),
                              onTap: () => _hideMenu().then((_) => goToProfile(navContext)),
                              child: Padding(
                                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                                child: Row(
                                  children: [
                                    const ContactAvatarWidget(
                                      size: 50,
                                      preferHighResAvatar: true,
                                      borderThickness: 0.1,
                                      editable: false,
                                      fontSize: 16,
                                    ),
                                    const SizedBox(width: 12),
                                    Expanded(
                                      child: Column(
                                        crossAxisAlignment: CrossAxisAlignment.start,
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          Text(
                                            userName.isNotEmpty ? userName : 'My Account',
                                            style: overlayContext.theme.textTheme.titleSmall?.copyWith(
                                              color: overlayContext.theme.colorScheme.onSurfaceVariant,
                                              fontWeight: FontWeight.w600,
                                            ),
                                            maxLines: 1,
                                            overflow: TextOverflow.ellipsis,
                                          ),
                                          const SizedBox(height: 2),
                                          Text(
                                            iCloudAccount.isNotEmpty ? iCloudAccount : 'Tap to open profile',
                                            style: overlayContext.theme.textTheme.bodySmall?.copyWith(
                                              color: overlayContext.theme.colorScheme.onSurfaceVariant
                                                  .withValues(alpha: 0.6),
                                            ),
                                            maxLines: 1,
                                            overflow: TextOverflow.ellipsis,
                                          ),
                                        ],
                                      ),
                                    ),
                                    const SizedBox(width: 8),
                                    Icon(
                                      Icons.chevron_right,
                                      color: overlayContext.theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.4),
                                      size: 20,
                                    ),
                                  ],
                                ),
                              ),
                            ),
                            Divider(
                              height: 1,
                              thickness: 1,
                              indent: 16,
                              endIndent: 16,
                              color: overlayContext.theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.1),
                            ),
                            // Menu items
                            _MenuItemRow(
                              icon: Icons.done_all_outlined,
                              label: 'Mark All As Read',
                              autofocus: isDumb,
                              onTap: () => _hideMenu().then((_) => ChatsSvc.markAllAsRead()),
                            ),
                            _MenuItemRow(
                              icon: Icons.archive_outlined,
                              label: 'Archived',
                              onTap: () => _hideMenu().then((_) => goToArchived(navContext)),
                            ),
                            if (filterUnknownSenders)
                              _MenuItemRow(
                                icon: Icons.person_off_outlined,
                                label: 'Unknown Senders',
                                onTap: () => _hideMenu().then((_) => goToUnknownSenders(navContext)),
                              ),
                            _MenuItemRow(
                              icon: Icons.delete_outline,
                              label: 'Recently Deleted',
                              onTap: () => _hideMenu().then((_) => goToRecentlyDeleted(navContext)),
                            ),
                            if (BackendSvc.supportsFindMy())
                              _MenuItemRow(
                                icon: Icons.location_on_outlined,
                                label: 'Map',
                                onTap: () => _hideMenu().then((_) => goToFindMy(navContext)),
                              ),
                            if (PushSvc.state?.icloudServices?.sharedstreams != null)
                              _MenuItemRow(
                                icon: Icons.photo_outlined,
                                label: 'Shared Albums',
                                onTap: () => _hideMenu().then((_) => goToSharedStreams(navContext)),
                              ),
                            _MenuItemRow(
                              icon: Icons.video_call_outlined,
                              label: 'Video Calls',
                              onTap: () => _hideMenu().then((_) => goToFaceTime(navContext)),
                            ),
                            if (PushSvc.state?.icloudServices?.keychain != null)
                              _MenuItemRow(
                                icon: Icons.key_outlined,
                                label: 'Passwords',
                                onTap: () => _hideMenu().then((_) => goToPasswords(navContext)),
                              ),
                            if (widget.extraItems)
                              _MenuItemRow(
                                icon: Icons.search,
                                label: 'Search',
                                onTap: () => _hideMenu().then((_) => goToSearch(navContext)),
                              ),
                            if (widget.extraItems && moveChatCreatorToHeader)
                              _MenuItemRow(
                                icon: Icons.edit_outlined,
                                label: 'New Chat',
                                onTap: () => _hideMenu().then((_) => widget.controller?.openNewChatCreator(navContext)),
                              ),
                            _MenuItemRow(
                              icon: Icons.settings_outlined,
                              label: 'Settings',
                              onTap: () => _hideMenu().then((_) => goToSettings(navContext)),
                            ),
                            if (kIsWeb)
                              _MenuItemRow(
                                icon: Icons.logout,
                                label: 'Logout',
                                onTap: () => _hideMenu().then((_) => logout(navContext)),
                              ),
                  const SizedBox(height: 4),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return DpadFocusable(
      onSelect: _showMenu,
      child: GestureDetector(
        onTap: _showMenu,
        child: Container(
          padding: const EdgeInsets.all(2),
          child: const ContactAvatarWidget(
            size: 32,
            preferHighResAvatar: true,
            borderThickness: 0.1,
            editable: false,
            fontSize: 12,
            scaleSize: false,
          ),
        ),
      ),
    );
  }
}

/// Popup route for the avatar overflow menu. Using a route (rather than a raw
/// [OverlayEntry]) lets the system / D-pad back button pop the menu instead of the
/// app, and dismisses the menu when the barrier is tapped.
class _AvatarMenuRoute extends PopupRoute<void> {
  _AvatarMenuRoute({required this.anchorRect, required this.builder});

  final Rect anchorRect;
  final WidgetBuilder builder;

  @override
  Color? get barrierColor => null;

  @override
  bool get barrierDismissible => true;

  @override
  String get barrierLabel => 'Dismiss menu';

  @override
  Duration get transitionDuration => const Duration(milliseconds: 250);

  @override
  Duration get reverseTransitionDuration => const Duration(milliseconds: 200);

  @override
  Widget buildPage(BuildContext context, Animation<double> animation, Animation<double> secondaryAnimation) {
    return CustomSingleChildLayout(
      delegate: _AvatarMenuLayoutDelegate(anchorRect, MediaQuery.of(context).padding),
      child: builder(context),
    );
  }

  @override
  Widget buildTransitions(
      BuildContext context, Animation<double> animation, Animation<double> secondaryAnimation, Widget child) {
    return FadeTransition(
      opacity: CurvedAnimation(parent: animation, curve: Curves.easeOut),
      child: ScaleTransition(
        scale: CurvedAnimation(parent: animation, curve: Curves.easeOutBack),
        alignment: Alignment.topRight,
        child: child,
      ),
    );
  }
}

/// Positions the avatar menu just below its trigger, right-aligned, and clamps it so it
/// never runs off any edge of the screen. The child is given a bounded height so it can
/// scroll when the menu is taller than the available space.
class _AvatarMenuLayoutDelegate extends SingleChildLayoutDelegate {
  _AvatarMenuLayoutDelegate(this.anchorRect, this.padding);

  final Rect anchorRect;
  final EdgeInsets padding;

  static const double _margin = 8;

  @override
  BoxConstraints getConstraintsForChild(BoxConstraints constraints) {
    final maxHeight = constraints.maxHeight - padding.top - padding.bottom - _margin * 2;
    return BoxConstraints.loose(Size(
      constraints.maxWidth,
      maxHeight > 0 ? maxHeight : constraints.maxHeight,
    ));
  }

  @override
  Offset getPositionForChild(Size size, Size childSize) {
    double x = anchorRect.right - childSize.width;
    final maxX = size.width - childSize.width - _margin;
    if (x > maxX) x = maxX;
    if (x < _margin) x = _margin;

    double y = anchorRect.bottom + _margin;
    final bottomLimit = size.height - padding.bottom - _margin;
    if (y + childSize.height > bottomLimit) {
      final aboveY = anchorRect.top - childSize.height - _margin;
      if (aboveY >= padding.top + _margin) {
        y = aboveY;
      } else {
        y = bottomLimit - childSize.height;
        if (y < padding.top + _margin) y = padding.top + _margin;
      }
    }
    return Offset(x, y);
  }

  @override
  bool shouldRelayout(_AvatarMenuLayoutDelegate oldDelegate) =>
      anchorRect != oldDelegate.anchorRect || padding != oldDelegate.padding;
}

class _MenuItemRow extends StatelessWidget {
  const _MenuItemRow({
    required this.icon,
    required this.label,
    required this.onTap,
    this.autofocus = false,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool autofocus;

  @override
  Widget build(BuildContext context) {
    return DpadFocusable(
      autofocus: autofocus,
      onSelect: onTap,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          child: SizedBox(
            height: 44,
            child: Row(
              children: [
                Icon(
                  icon,
                  size: 20,
                  color: context.theme.colorScheme.onSurfaceVariant.withValues(alpha: 0.85),
                ),
                const SizedBox(width: 16),
                Text(
                  label,
                  style: context.theme.textTheme.bodyLarge?.copyWith(
                    color: context.theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class CupertinoOverflowMenu extends StatelessWidget {
  const CupertinoOverflowMenu({
    super.key,
    required this.extraItems,
    required this.controller,
  });

  final bool extraItems;
  final ConversationListController? controller;

  @override
  Widget build(BuildContext context) {
    final userName = SettingsSvc.settings.userName.value;
    final filterUnknownSenders = SettingsSvc.settings.filterUnknownSenders.value;
    final moveChatCreatorToHeader = SettingsSvc.settings.moveChatCreatorToHeader.value;

    final itemTheme = PullDownMenuItemTheme(
      textStyle: TextStyle(
        color: context.theme.colorScheme.onSurface,
      ),
      onHoverTextColor: context.theme.colorScheme.onSurface,
      onHoverBackgroundColor: context.theme.colorScheme.primaryContainer.withValues(alpha: 0.3),
      subtitleStyle: TextStyle(
        color: context.theme.colorScheme.onSurface.withValues(alpha: 0.7),
      ),
    );

    return PullDownButton(
      animationAlignmentOverride: Alignment.topRight,
      routeTheme: PullDownMenuRouteTheme(
          backgroundColor: context.theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.9)),
      itemBuilder: (context) => [
        PullDownMenuHeader(
          itemTheme: itemTheme,
          title: SettingsSvc.settings.redactedMode.value ? "User Name" : userName,
          icon: CupertinoIcons.chevron_right,
          leadingBuilder: (context, constraints) {
            return Container(
                constraints: constraints,
                child: const ContactAvatarWidget(
                    size: 50, preferHighResAvatar: true, borderThickness: 0.1, editable: false, fontSize: 16));
          },
          subtitle: "Tap to open profile",
          onTap: () => goToProfile(context),
        ),
        PullDownMenuItem(
          itemTheme: itemTheme,
          title: 'Mark All As Read',
          icon: CupertinoIcons.check_mark_circled,
          onTap: ChatsSvc.markAllAsRead,
        ),
        PullDownMenuItem(
          itemTheme: itemTheme,
          title: 'Recently Deleted',
          icon: CupertinoIcons.delete,
          onTap: () => goToRecentlyDeleted(context),
        ),
        if (filterUnknownSenders)
          PullDownMenuItem(
            itemTheme: itemTheme,
            title: 'Unknown Senders',
            icon: CupertinoIcons.person_crop_circle_badge_xmark,
            onTap: () => goToUnknownSenders(context),
          ),
        if (BackendSvc.supportsFindMy())
          PullDownMenuItem(
            itemTheme: itemTheme,
            title: 'Map',
            icon: CupertinoIcons.location,
            onTap: () => goToFindMy(context),
          ),
        if (PushSvc.state?.icloudServices?.sharedstreams != null)
          PullDownMenuItem(
            itemTheme: itemTheme,
            title: 'Shared Albums',
            icon: CupertinoIcons.photo,
            onTap: () => goToSharedStreams(context),
          ),
        PullDownMenuItem(
          itemTheme: itemTheme,
          title: 'Video Calls',
          icon: CupertinoIcons.video_camera,
          onTap: () => goToFaceTime(context),
        ),
        if (PushSvc.state?.icloudServices?.keychain != null)
          PullDownMenuItem(
            itemTheme: itemTheme,
            title: 'Passwords',
            icon: Icons.key,
            onTap: () => goToPasswords(context),
          ),
        if (extraItems)
          PullDownMenuItem(
            itemTheme: itemTheme,
            title: 'Search',
            icon: CupertinoIcons.search,
            onTap: () => goToSearch(context),
          ),
        if (extraItems && moveChatCreatorToHeader)
          PullDownMenuItem(
              itemTheme: itemTheme,
              title: 'New Chat',
              icon: CupertinoIcons.plus,
              onTap: () => controller?.openNewChatCreator(context)),
        PullDownMenuItem(
          itemTheme: itemTheme,
          title: 'Archived',
          icon: CupertinoIcons.archivebox,
          onTap: () => goToArchived(context),
        ),
        PullDownMenuItem(
          itemTheme: itemTheme,
          title: 'Settings',
          icon: CupertinoIcons.gear,
          onTap: () => goToSettings(context),
        ),
        if (kIsWeb)
          PullDownMenuItem(
            itemTheme: itemTheme,
            title: 'Logout',
            icon: CupertinoIcons.power,
            onTap: () => logout(context),
          ),
      ],
      buttonBuilder: (context, showMenu) => ThemeSwitcher(
          iOSSkin: ClipOval(
            child: Material(
              color: context.theme.colorScheme.surfaceContainerHighest,
              child: SizedBox(
                width: 30,
                height: 30,
                child: InkWell(
                  onTap: showMenu,
                  child: Icon(
                    Icons.more_horiz,
                    color: context.theme.colorScheme.onSurfaceVariant,
                    size: 20,
                  ),
                ),
              ),
            ),
          ),
          materialSkin: const SizedBox.shrink(),
          samsungSkin: const SizedBox.shrink()),
    );
  }
}

Future<void> goToSearch(BuildContext context) async {
  final current = NavigationSvc.ratio(context);
  EventDispatcherSvc.emit("override-split", 0.3);
  await NavigationSvc.pushLeft(context, const SearchView());
  EventDispatcherSvc.emit("override-split", current);
}

Future<void> goToRecentlyDeleted(BuildContext context) async {
  NavigationSvc.pushLeft(
      context,
      ConversationList(
        showArchivedChats: false,
        showUnknownSenders: false,
        showDeletedMessages: true,
      ));
}

Future<void> goToFindMy(BuildContext context) async {
  final currentChat = ChatsSvc.activeChat?.chat;
  NavigationSvc.closeAllConversationView(context);
  ChatsSvc.setAllInactive();
  await Navigator.of(Get.context!).push(
    ThemeSwitcher.buildPageRoute(
      builder: (BuildContext context) {
        return FindMyPage();
      },
    ),
  );
  if (currentChat != null) {
    await ChatsSvc.setActiveChat(currentChat);
    if (SettingsSvc.settings.tabletMode.value) {
      NavigationSvc.pushAndRemoveUntil(
        context,
        ConversationView(
          chat: currentChat,
        ),
        (route) => route.isFirst,
      );
    } else {
      cvc(currentChat).close();
    }
  }
}

Future<void> goToFaceTime(BuildContext context) async {
  final currentChat = ChatsSvc.activeChat?.chat;
  NavigationSvc.closeAllConversationView(context);
  await ChatsSvc.setAllInactive();
  await Navigator.of(Get.context!).push(
    ThemeSwitcher.buildPageRoute(
      builder: (BuildContext context) {
        return FaceTimePanel();
      },
    ),
  );
  if (currentChat != null) {
    await ChatsSvc.setActiveChat(currentChat);
    if (SettingsSvc.settings.tabletMode.value) {
      NavigationSvc.pushAndRemoveUntil(
        context,
        ConversationView(
          chat: currentChat,
        ),
        (route) => route.isFirst,
      );
    } else {
      cvc(currentChat).close();
    }
  }
}

Future<void> goToPasswords(BuildContext context) async {
  final currentChat = ChatsSvc.activeChat?.chat;
  NavigationSvc.closeAllConversationView(context);
  await ChatsSvc.setAllInactive();
  await Navigator.of(Get.context!).push(
    ThemeSwitcher.buildPageRoute(
      builder: (BuildContext context) {
        return const PasswordsPanel();
      },
    ),
  );
  if (currentChat != null) {
    await ChatsSvc.setActiveChat(currentChat);
    if (SettingsSvc.settings.tabletMode.value) {
      NavigationSvc.pushAndRemoveUntil(
        context,
        ConversationView(
          chat: currentChat,
        ),
        (route) => route.isFirst,
      );
    } else {
      cvc(currentChat).close();
    }
  }
}

Future<void> goToSharedStreams(BuildContext context) async {
  final currentChat = ChatsSvc.activeChat?.chat;
  NavigationSvc.closeAllConversationView(context);
  await ChatsSvc.setAllInactive();
  await Navigator.of(Get.context!).push(
    ThemeSwitcher.buildPageRoute(
      builder: (BuildContext context) {
        return SharedStreamsPanel();
      },
    ),
  );
  if (currentChat != null) {
    ChatsSvc.setActiveChat(currentChat);
    if (SettingsSvc.settings.tabletMode.value) {
      NavigationSvc.pushAndRemoveUntil(
        context,
        ConversationView(
          chat: currentChat,
        ),
        (route) => route.isFirst,
      );
    } else {
      cvc(currentChat).close();
    }
  }
}

void logout(BuildContext context) {
  showBBDialog(
    barrierDismissible: false,
    context: context,
    title: "Are you sure?",
    actions: [
      BBDialogAction(
        text: "No",
        onPressed: () => Navigator.of(context, rootNavigator: true).pop(),
      ),
      BBDialogAction(
        text: "Yes",
        isDefault: true,
        onPressed: () async {
          Navigator.of(context, rootNavigator: true).pop();
          FilesystemSvc.deleteDB();
          SocketSvc.forgetConnection();
          SettingsSvc.settings = Settings();
          SettingsSvc.fcmData = FCMData();
          await PrefsSvc.admin.clearAll();
          await PrefsSvc.theme.setSelectedThemes(
            darkTheme: "OLED Dark",
            lightTheme: "Bright White",
          );
          Get.offAll(
              () => const PopScope(
                    canPop: false,
                    child: TitleBarWrapper(child: SetupView()),
                  ),
              duration: Duration.zero,
              transition: Transition.noTransition);
        },
      ),
    ],
  );
}

void goToUnknownSenders(BuildContext context) {
  NavigationSvc.pushLeft(
      context,
      ConversationList(
        showArchivedChats: false,
        showUnknownSenders: true,
      ));
}

Future<void> goToSettings(BuildContext context) async {
  final currentChat = ChatsSvc.activeChat?.chat;
  NavigationSvc.closeAllConversationView(context);
  ChatsSvc.setAllInactive();
  await Navigator.of(Get.context!).push(
    ThemeSwitcher.buildPageRoute(
      builder: (BuildContext context) {
        return const SettingsPage();
      },
    ),
  );
  if (currentChat != null) {
    ChatsSvc.setActiveChat(currentChat);
    if (SettingsSvc.settings.tabletMode.value) {
      NavigationSvc.pushAndRemoveUntil(
        context,
        ConversationView(
          chat: currentChat,
        ),
        (route) => route.isFirst,
      ).onError((error, stackTrace) => ChatsSvc.setAllInactive());
    } else {
      cvc(currentChat).close();
    }
  }
}

void goToArchived(BuildContext context) {
  NavigationSvc.pushLeft(
      context,
      ConversationList(
        showArchivedChats: true,
        showUnknownSenders: false,
      ));
}

void goToProfile(BuildContext context) {
  NavigationSvc.pushLeft(context, const ProfilePanel());
}
