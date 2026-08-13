import 'dart:async';

import 'package:bluebubbles/app/layouts/chat_creator/chat_creator.dart';
import 'package:bluebubbles/app/layouts/facetime/facetime.dart';
import 'package:bluebubbles/app/layouts/findmy/findmy_page.dart';
import 'package:bluebubbles/app/layouts/chat_creator/new_chat_creator.dart';
import 'package:bluebubbles/app/layouts/settings/pages/scheduling/scheduled_messages_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/misc/shared_streams_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/passwords/passwords_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/profile/profile_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/server/server_management_panel.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/helpers/backend/startup_tasks.dart';
import 'package:bluebubbles/helpers/ui/facetime_helpers.dart';

import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/app/layouts/conversation_view/pages/conversation_view.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart' hide Intent;
import 'package:get/get.dart';
import 'package:bluebubbles/models/models.dart' show HandleLookupKey;
import 'package:path/path.dart';
import 'package:receive_intent/receive_intent.dart';
import 'package:universal_io/io.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:get_it/get_it.dart';

// ignore: non_constant_identifier_names
IntentsService get IntentsSvc => GetIt.I<IntentsService>();

class IntentsService {
  late final StreamSubscription sub;

  /// When a notification tap triggers navigation to a specific chat, this is
  /// set synchronously (before any async gap) so that [onAppResume] can skip
  /// marking the previously-active chat as read while the redirect is pending.
  String? pendingOpenChatGuid;

  Future<void> init() async {
    if (kIsWeb || kIsDesktop) return;

    final intent = await ReceiveIntent.getInitialIntent();
    handleIntent(intent);

    sub = ReceiveIntent.receivedIntentStream.listen((Intent? intent) {
      handleIntent(intent);
    }, onError: (err) {
      Logger.error("Failed to get intent!", error: err);
    });
  }

  void close() async {
    await sub.cancel();
  }

  void handleIntent(Intent? intent) async {
    if (intent == null) return;

    switch (intent.action) {
      case "com.bluebubbles.messaging.CallBackFT":
        final id = intent.extra!["callUuid"];
        var call = PushSvc.activeSessions.firstWhereOrNull((a) => a.groupId == id);
        if (call == null) {
          call = PushSvc.sessions.firstWhereOrNull((a) => a.groupId == id);
          if (call == null) {
            Logger.warn("callback uuid $id not found!");
            return;
          }
        }
        var handles = call.members
            .map((a) => a.handle)
            .where((a) => a != call!.myHandles.first && !a.startsWith("temp:"))
            .toList();
        PushSvc.placeOutgoingCall(call.myHandles.first, handles);
        MethodChannelSvc.invokeMethod("delete-notification",
            {"notification_id": intent.extra!["notificationId"], "tag": NotificationsService.NEW_MESSAGE_TAG});
        return;
      case "com.bluebubbles.messaging.RecentCalls":
        Navigator.of(Get.context!).push(
          ThemeSwitcher.buildPageRoute(
            builder: (BuildContext context) {
              return FaceTimePanel();
            },
          ),
        );
        return;
      case "android.intent.action.SEND":
      case "android.intent.action.SEND_MULTIPLE":
        final id = intent.extra?["android.intent.extra.shortcut.ID"];
        final text = intent.extra?["android.intent.extra.TEXT"];
        final files = <PlatformFile>[];
        if (intent.extra?["android.intent.extra.STREAM"] != null) {
          final data = intent.extra!["android.intent.extra.STREAM"];
          if (data is List) {
            for (String? s in data) {
              if (s == null) continue;
              final path = await MethodChannelSvc.actions.getContentUriPath(uri: s);
              final bytes = await File(path).length();
              files.add(PlatformFile(
                path: path,
                name: basename(path),
                size: bytes,
              ));
            }
          } else if (data != null) {
            final path = await MethodChannelSvc.actions.getContentUriPath(uri: data.toString());
            final bytes = await File(path).length();
            files.add(PlatformFile(
              path: path,
              name: basename(path),
              size: bytes,
            ));
          }
        }
        await openChat(id, text: text, attachments: files);
        return;
      default:
        if (intent.data?.startsWith("imessage://") ?? false) {
          final uri =
              Uri.tryParse(intent.data!.replaceFirst("imessage://", "imessage:").replaceFirst("&body=", "?body="));
          if (uri != null) {
            final address = uri.path;
            final handle = Handle.findOne(addressAndService: HandleLookupKey(address, "iMessage"));
            NavigationSvc.pushAndRemoveUntil(
              Get.context!,
              NewChatCreator(
                initialSelected: [SelectedContact(displayName: handle?.displayName ?? address, address: address)],
                initialText: uri.queryParameters['body'],
              ),
              (route) => route.isFirst,
            );
          }
        } else if (intent.extra?["chatGuid"] != null) {
          final guid = intent.extra!["chatGuid"]!;
          final bubble = intent.extra!["bubble"] == true;
          LifecycleSvc.isBubble = bubble;
          await openChat(guid);
        } else if (intent.extra?["callUuid"] != null) {
          await StartupTasks.waitForUI();
          Logger.info(intent.action);
        }
    }
  }

  Future<void> answerFaceTime(String callUuid) async {
    if (Get.context != null) {
      showDialog(
          context: Get.context!,
          builder: (BuildContext context) {
            return AlertDialog(
              backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
              title: Text(
                "Generating link for call...",
                style: context.theme.textTheme.titleLarge,
              ),
              content: SizedBox(
                height: 70,
                child: Center(
                  child: CircularProgressIndicator(
                    backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                    valueColor: AlwaysStoppedAnimation<Color>(context.theme.colorScheme.primary),
                  ),
                ),
              ),
            );
          });
      hideFaceTimeOverlay(callUuid);
    }

    String? link;
    try {
      final call = await HttpSvc.faceTime.answer(callUuid);
      link = call.data?["data"]?["link"];
    } catch (e, s) {
      Logger.warn("Failed to fetch FaceTime answer link", error: e, trace: s, tag: 'IntentsService');
    }
    if (Get.context != null) {
      Navigator.of(Get.context!).pop();
    }
    if (link == null) {
      return showSnackbar("Failed to answer FaceTime", "Unable to generate FaceTime link!");
    }

    if (!kIsWeb) {
      await launchUrl(Uri.parse(link), mode: LaunchMode.externalApplication);
    } else if (kIsWeb) {
      // TODO: Implement web FaceTime
    }
  }

  Future<void> openChat(String? guid, {String? text, List<PlatformFile> attachments = const []}) async {
    Logger.info("Handling open chat intent with guid: $guid", tag: "IntentsService");

    if (guid == null) {
      Logger.debug("Opening new chat creator..", tag: "IntentsService");
      await StartupTasks.waitForUI();
      NavigationSvc.pushAndRemoveUntil(
        Get.context!,
        NewChatCreator(
          initialAttachments: attachments,
          initialText: text,
        ),
        (route) => route.isFirst,
      );
    } else if (guid == "-1") {
      Logger.debug("Popping all routes...", tag: "IntentsService");
      if (ChatsSvc.activeChat != null) {
        Navigator.of(Get.context!).popUntil((route) => route.isFirst);
      }
    } else if (guid == "-2") {
      Logger.debug("Opening server management panel...", tag: "IntentsService");
      Navigator.of(Get.context!).push(
        ThemeSwitcher.buildPageRoute(
          builder: (BuildContext context) {
            return ServerManagementPanel();
          },
        ),
      );
    } else if (guid == "-51" || guid == '-53') {
      Logger.debug("Opening profile panel...", tag: "IntentsService");
      NavigationSvc.pushLeft(Get.context!, ProfilePanel());
    } else if (guid == "-52") {
      Logger.debug("Opening shared streams panel...", tag: "IntentsService");
      NavigationSvc.pushLeft(Get.context!, SharedStreamsPanel());
    } else if (guid == "-54") {
      Logger.debug("Opening find my panel...", tag: "IntentsService");
      NavigationSvc.pushLeft(Get.context!, FindMyPage());
    } else if (guid == "-55") {
      Logger.debug("Opening passwords panel...", tag: "IntentsService");
      NavigationSvc.pushLeft(Get.context!, const PasswordsPanel());
    } else if (guid.contains("scheduled")) {
      Logger.debug("Opening scheduled messages panel...", tag: "IntentsService");
      Navigator.of(Get.context!).push(
        ThemeSwitcher.buildPageRoute(
          builder: (BuildContext context) {
            return const ScheduledMessagesPanel();
          },
        ),
      );
    } else {
      Logger.debug("Opening existing chat (Attachments: ${attachments.length}; Text: ${text?.shorten(10) ?? 'N/A'})",
          tag: "IntentsService");
      final chat = Chat.findOne(guid: guid);
      if (chat == null) {
        Logger.debug("Chat not found with guid: $guid", tag: "IntentsService");
        return;
      }

      await StartupTasks.waitForUI();

      bool chatIsOpen = ChatsSvc.activeChat?.chat.guid == guid;
      Logger.debug("Chat is active: $chatIsOpen", tag: "IntentsService");

      setPickedAttachments() {
        if (attachments.isNotEmpty) {
          cvc(chat).pickedAttachments.value = attachments;
        }

        if (text != null && text.isNotEmpty) {
          cvc(chat).textController.text = text;
        }
      }

      if (!chatIsOpen) {
        // Mark the navigation as pending BEFORE any await so that onAppResume,
        // which fires while we are suspended at waitForUI / Future.delayed, can
        // see that we are about to switch chats and must not mark the current
        // active chat as read prematurely.
        pendingOpenChatGuid = guid;
        Logger.debug("Navigating to conversation view...", tag: "IntentsService");

        // Rather than waiting for paging to eventually reach this chat,
        // proactively seed its ChatState now. getOrCreateChatState() inserts
        // a fully valid ChatState immediately and is a no-op if the batch
        // loader already added it.
        ChatsSvc.getOrCreateChatState(chat);

        // Pre-populate text/attachments on the controller before navigating so
        // the ConversationView text field is pre-filled on first build.
        setPickedAttachments();
        pendingOpenChatGuid = null;

        await NavigationSvc.pushAndRemoveUntil(
          Get.context!,
          ConversationView(chat: chat),
          (route) => route.isFirst,
        );
      } else {
        Logger.debug("Chat is already open, not navigating", tag: "IntentsService");
        setPickedAttachments();
      }
    }
  }
}
