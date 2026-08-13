import 'dart:async';

import 'package:bluebubbles/app/layouts/chat_creator/new_chat_creator.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/conversation_list_fab.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/footer/samsung_footer.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/header/material_header.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/header/samsung_header.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/initial_widget_right.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/conversation_tile.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/material_conversation_tile.dart';
import 'package:bluebubbles/app/layouts/conversation_list/widgets/tile/samsung_conversation_tile.dart';
import 'package:bluebubbles/app/wrappers/bb_scaffold.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/app/wrappers/tablet_mode_wrapper.dart';
import 'package:bluebubbles/database/database.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/foundation.dart';
import 'package:image_picker/image_picker.dart';
import 'package:path/path.dart' hide context;
import 'package:permission_handler/permission_handler.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/app/layouts/conversation_list/pages/cupertino_conversation_list.dart';
import 'package:bluebubbles/app/layouts/conversation_list/pages/material_conversation_list.dart';
import 'package:bluebubbles/app/layouts/conversation_list/pages/samsung_conversation_list.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';

class ConversationListController extends StatefulController {
  final bool showArchivedChats;
  final bool showUnknownSenders;
  final bool showDeletedMessages;
  final ScrollController iosScrollController = ScrollController();
  final ScrollController materialScrollController = ScrollController();
  final ScrollController samsungScrollController = ScrollController();
  final FocusNode newMessageFocusNode = FocusNode(skipTraversal: true);
  final List<Chat> selectedChats = [];
  final RxList<Chat> deletedChats = <Chat>[].obs;
  bool showMaterialFABText = true;
  double materialScrollStartPosition = 0;
  StreamSubscription? sub;

  ConversationListController({
    required this.showArchivedChats,
    required this.showUnknownSenders,
    this.showDeletedMessages = false,
  }) {
    if (!kIsWeb && showDeletedMessages) {
      final subscription = (Database.chats.query()..backlink(Message_.chat, Message_.dateDeleted.notNull()))
          .watch(triggerImmediately: true);
      sub = subscription.listen((Query<Chat> query) {
        deletedChats.value = query.find();
      });
    }
  }

  void updateSelectedChats() {
    if (SettingsSvc.settings.skin.value == Skins.Material) {
      updateWidgets<MaterialHeader>(null);
      updateMaterialFAB();
    } else if (SettingsSvc.settings.skin.value == Skins.Samsung) {
      updateWidgets<SamsungFooter>(null);
      updateWidgets<ExpandedHeaderText>(null);
    }
  }

  @override
  void dispose() {
    sub?.cancel();
    newMessageFocusNode.dispose();
    super.dispose();
  }

  void clearSelectedChats() {
    final copy = List.from(selectedChats);
    for (Chat c in copy) {
      selectedChats.removeWhere((element) => element.guid == c.guid);
      Get.find<ConversationTileController>(tag: c.guid).updateWidgets<MaterialConversationTile>(null);
      Get.find<ConversationTileController>(tag: c.guid).updateWidgets<SamsungConversationTile>(null);
    }
    updateSelectedChats();
  }

  void updateMaterialFAB() {
    updateWidgets<ConversationListFAB>(null);
  }

  void openCamera(BuildContext context) async {
    bool camera = await Permission.camera.isGranted;
    if (!camera) {
      bool granted = (await Permission.camera.request()) == PermissionStatus.granted;
      if (!granted) {
        showSnackbar("Error", "Camera was denied");
        return;
      }
    }

    final XFile? file = await ImagePicker().pickImage(source: ImageSource.camera);
    if (file == null) return;

    openNewChatCreator(context, existing: [
      PlatformFile(
        name: basename(file.path),
        path: file.path,
        bytes: await file.readAsBytes(),
        size: await file.length(),
      )
    ]);
  }

  void openNewChatCreator(BuildContext context, {List<PlatformFile>? existing}) async {
    NavigationSvc.pushAndRemoveUntil(
      context,
      NewChatCreator(initialAttachments: existing ?? []),
      (route) => route.isFirst,
    );
  }
}

class ConversationList extends CustomStateful<ConversationListController> {
  ConversationList({
    super.key,
    required bool showArchivedChats,
    required bool showUnknownSenders,
    bool showDeletedMessages = false,
  }) : super(
            parentController: Get.put(
                ConversationListController(
                  showArchivedChats: showArchivedChats,
                  showUnknownSenders: showUnknownSenders,
                  showDeletedMessages: showDeletedMessages,
                ),
                tag: showArchivedChats
                    ? "Archived"
                    : showUnknownSenders
                        ? "Unknown"
                        : showDeletedMessages
                            ? "Recently Deleted"
                            : "Messages"));

  @override
  State<StatefulWidget> createState() => _ConversationListState();
}

class _ConversationListState extends CustomState<ConversationList, void, ConversationListController> {
  Timer? _initTimer;

  @override
  void initState() {
    super.initState();
    tag = controller.showArchivedChats
        ? "Archived"
        : controller.showUnknownSenders
            ? "Unknown"
            : controller.showDeletedMessages
                ? "Recently Deleted"
                : "Messages";

    if (!SettingsSvc.settings.reachedConversationList.value) {
      _initTimer = Timer.periodic(const Duration(seconds: 1), (Timer t) {
        if (!mounted) {
          t.cancel();
          return;
        }

        bool notInSettings = NavigationSvc.isTabletMode(context)
            ? !Get.keys.containsKey(3) || Get.keys[3]?.currentContext == null
            : Get.rawRoute?.settings.name == "/";
        // This only runs once
        if (notInSettings) {
          SettingsSvc.settings.reachedConversationList.value = true;
          SettingsSvc.settings.saveOneAsync('reachedConversationList');
          unawaited(SettingsSvc.refreshServerDetails());
          t.cancel();
        }
      });
    }
  }

  @override
  void dispose() {
    _initTimer?.cancel();
    super.dispose();
  }

  /// If focus leaks onto the chat list while a conversation is open in the foreground (phone
  /// layout), send focus back to the conversation's text field. In tablet/split-view the list is
  /// legitimately focusable alongside the conversation, so this is a no-op there.
  void _refocusConversationIfForeground(BuildContext context) {
    if (NavigationSvc.isTabletMode(context)) return;
    final active = ChatsSvc.activeChat;
    if (active == null || !active.isChatActive || active.controller == null) return;
    // Defer to avoid re-entrant focus changes, then re-check the conditions still hold.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final a = ChatsSvc.activeChat;
      if (a != null && a == active && a.isChatActive && a.controller != null) {
        a.controller!.focusNode.requestFocus();
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final child = Focus(
      // Observe-only (not a focus stop): watch for focus leaking onto the chat list.
      canRequestFocus: false,
      skipTraversal: true,
      onFocusChange: (focused) {
        if (focused) _refocusConversationIfForeground(context);
      },
      child: ThemeSwitcher(
        iOSSkin: CupertinoConversationList(parentController: controller),
        materialSkin: MaterialConversationList(parentController: controller),
        samsungSkin: SamsungConversationList(parentController: controller),
      ),
    );

    if (controller.showArchivedChats || controller.showUnknownSenders || controller.showDeletedMessages) return child;

    return BBScaffold(
      safeAreaLeft: false,
      safeAreaRight: false,
      body: TabletModeWrapper(
        initialRatio: 0.4,
        minWidthLeft: kIsDesktop || kIsWeb ? 150 : null,
        minRatio: kIsDesktop || kIsWeb ? 0.1 : 0.33,
        maxRatio: 0.5,
        allowResize: true,
        left: !showAltLayout
            ? child
            : LayoutBuilder(builder: (context, constraints) {
                NavigationSvc.maxWidthLeft = constraints.maxWidth;
                return PopScope(
                  canPop: false,
                  onPopInvokedWithResult: <T>(bool _, T? __) async {
                    Get.until((route) {
                      bool id2result = false;
                      // check if we should pop the left side first
                      Get.until((route) {
                        if (route.settings.name != "initial") {
                          Get.back(id: 2);
                          id2result = true;
                        }
                        if (!(Get.global(2).currentState?.canPop() ?? true)) {
                          if (ChatsSvc.activeChat != null) {
                            cvc(ChatsSvc.activeChat!.chat).close();
                          }
                          EventDispatcherSvc.emit('update-highlight', null);
                        }
                        return true;
                      }, id: 2);
                      if (!id2result) {
                        if (route.settings.name == "initial") {
                          SystemNavigator.pop();
                        } else {
                          Get.back(id: 1);
                        }
                      }
                      return true;
                    }, id: 1);
                  },
                  child: Navigator(
                    key: Get.nestedKey(1),
                    requestFocus: false,
                    onPopPage: (route, _) {
                      return false;
                    },
                    pages: [
                      CupertinoPage(
                        name: "initial",
                        child: child,
                      )
                    ],
                  ),
                );
              }),
        right: LayoutBuilder(
          builder: (context, constraints) {
            NavigationSvc.maxWidthRight = constraints.maxWidth;
            return PopScope(
              canPop: false,
              onPopInvokedWithResult: <T>(bool _, T? __) async {
                Get.back(id: 2);
              },
              child: Navigator(
                key: Get.nestedKey(2),
                onPopPage: (route, _) {
                  return false;
                },
                pages: [
                  const CupertinoPage(
                    name: "initial",
                    child: InitialWidgetRight(),
                  ),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}
