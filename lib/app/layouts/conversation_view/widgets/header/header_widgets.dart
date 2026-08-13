import 'dart:async';
import 'dart:typed_data';

import 'package:bluebubbles/app/layouts/chat_creator/new_chat_creator.dart';
import 'package:bluebubbles/app/state/chat_state_scope.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/network/backend_service.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:universal_io/io.dart';
import 'package:url_launcher/url_launcher.dart';

bool get iOS => SettingsSvc.settings.skin.value == Skins.iOS;

class ManualMark extends StatefulWidget {
  const ManualMark({super.key, required this.controller});

  final ConversationViewController controller;

  @override
  State<StatefulWidget> createState() => ManualMarkState();
}

class ManualMarkState extends State<ManualMark> with ThemeHelpers {
  bool marked = false;
  bool marking = false;

  Chat get chat => widget.controller.chat;

  @override
  Widget build(BuildContext context) {
    final manualMark = SettingsSvc.settings.enablePrivateAPI.value &&
        SettingsSvc.settings.privateManualMarkAsRead.value &&
        !(chat.autoSendReadReceipts ?? false);
    return Obx(() {
      if (!manualMark && !widget.controller.inSelectMode.value) return const SizedBox.shrink();
      return Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          IconButton(
            icon: Icon(
              widget.controller.inSelectMode.value
                  ? (iOS ? CupertinoIcons.trash : Icons.delete_outlined)
                  : marking
                      ? (iOS ? CupertinoIcons.arrow_2_circlepath : Icons.sync)
                      : marked
                          ? (iOS ? CupertinoIcons.app : Icons.mark_chat_read_outlined)
                          : (iOS ? CupertinoIcons.app_badge : Icons.mark_chat_unread_outlined),
              color: !iOS
                  ? context.theme.colorScheme.onSurface
                  : (!marked && !marking || widget.controller.inSelectMode.value)
                      ? context.theme.colorScheme.primary
                      : context.theme.colorScheme.outline,
            ),
            tooltip: widget.controller.inSelectMode.value
                ? "Delete"
                : marking
                    ? null
                    : marked
                        ? "Mark Unread"
                        : "Mark Read",
            onPressed: () async {
              if (widget.controller.inSelectMode.value) {
                for (Message m in widget.controller.selected) {
                  await MessagesSvc(chat.guid).softDeleteMessage(m);
                }
                widget.controller.inSelectMode.value = false;
                widget.controller.selected.clear();
                return;
              }
              if (marking) return;
              setState(() {
                marking = true;
              });
              if (!marked) {
                await BackendSvc.markRead(chat, SettingsSvc.settings.privateMarkChatAsRead.value);
              } else {
                await BackendSvc.markUnread(chat);
              }
              setState(() {
                marking = false;
                marked = !marked;
              });
            },
          ),
          if (widget.controller.inSelectMode.value)
            IconButton(
              icon: Icon(
                iOS ? CupertinoIcons.arrow_right : Icons.forward_outlined,
                color: !iOS ? context.theme.colorScheme.onSurface : context.theme.colorScheme.primary,
              ),
              onPressed: () async {
                List<PlatformFile> attachments = [];
                String text = "";
                widget.controller.selected.sort((a, b) => Message.sort(a, b, descending: false));
                for (Message m in widget.controller.selected) {
                  final _attachments = m.dbAttachments
                      .where((e) => AttachmentsSvc.getContent(e, autoDownload: false) is PlatformFile)
                      .map((e) => AttachmentsSvc.getContent(e, autoDownload: false) as PlatformFile);
                  for (PlatformFile a in _attachments) {
                    Uint8List? bytes = a.bytes;
                    bytes ??= await File(a.path!).readAsBytes();
                    attachments.add(PlatformFile(
                      name: a.name,
                      path: a.path,
                      size: bytes.length,
                      bytes: bytes,
                    ));
                  }
                  if (!isNullOrEmpty(m.text)) {
                    if (text.isEmpty) {
                      text = m.text!;
                    } else {
                      text = "$text\n\n${m.text}";
                    }
                  }
                }
                widget.controller.inSelectMode.value = false;
                widget.controller.selected.clear();
                NavigationSvc.pushAndRemoveUntil(
                  context,
                  NewChatCreator(
                    initialText: text,
                    initialAttachments: attachments,
                  ),
                  (route) => route.isFirst,
                );
              },
            ),
        ],
      );
    });
  }
}

class FaceTimeBtn extends StatefulWidget {
  const FaceTimeBtn({super.key, required this.controller});

  final ConversationViewController controller;

  @override
  State<StatefulWidget> createState() => FaceTimeBtnState();
}

class FaceTimeBtnState extends State<FaceTimeBtn> {
  bool marked = false;
  bool marking = false;
  List<String> ftSupportedParticipants = [];

  Chat get chat => widget.controller.chat;

  @override
  void initState() {
    super.initState();
    (() async {
      final data = await chat.getConversationData();
      ftSupportedParticipants = await api.validateTargetsFacetime(
        state: PushSvc.state!.client,
        targets: data.participants,
        sender: await chat.ensureHandle(),
      );
      if (mounted) {
        setState(() {});
      }
    })();
  }

  @override
  Widget build(BuildContext context) {
    if (ftSupportedParticipants.length != (chat.handles.length + 1)) {
      return const SizedBox.shrink();
    }

    return Obx(() {
      final session = PushSvc.activeSessions.firstWhereOrNull(
        (s) => chat.handles.every(
          (p) => s.members.any((m) => m.handle == RustPushBBUtils.bbHandleToRust(p)),
        ),
      );
      Logger.info("Guid $session");

      if (session != null) {
        return Padding(
          padding: iOS ? const EdgeInsets.only(top: 15) : const EdgeInsets.symmetric(horizontal: 5),
          child: Material(
            borderRadius: BorderRadius.circular(100),
            color: context.theme.colorScheme.bubble(context, false),
            child: InkWell(
              onTap: () async {
                final participants = session.members.where((a) => !session.myHandles.contains(a.handle)).map((a) {
                  if (a.nickname != null) {
                    return Handle(address: "Maybe: ${a.nickname}");
                  }
                  return RustPushBBUtils.rustHandleToBB(a.handle);
                }).toList();

                PushSvc.chosenFTRoomGuid = session.groupId;
                final link = await api.getFtLink(facetime: PushSvc.state!.ftClient, usage: "next");
                final desc = participants.map((p) => p.displayName).join(" & ");

                PushSvc.rotateLink().catchError((e, s) {
                  Logger.error("Failed to rotate link", error: e, trace: s);
                });

                if (Platform.isAndroid) {
                  await MethodChannelSvc.invokeMethod("launch-facetime", {
                    'link': link,
                    'desc': desc,
                    'callUuid': session.groupId,
                  });
                } else {
                  await launchUrl(
                    Uri.parse(link),
                    mode: LaunchMode.externalApplication,
                  );
                }
              },
              borderRadius: BorderRadius.circular(100),
              child: SizedBox(
                height: 35,
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 10),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(
                        iOS ? CupertinoIcons.video_camera_solid : Icons.video_call,
                        color: context.theme.colorScheme.onBubble(context, false),
                        size: 20,
                      ),
                      const SizedBox(width: 2.5),
                      Text(
                        "Join",
                        style: context.theme.textTheme.bodyMedium!.copyWith(
                          color: context.theme.colorScheme.onBubble(context, false),
                          fontSize: 15,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        );
      }

      return Padding(
        padding: iOS ? const EdgeInsets.only(top: 5) : EdgeInsets.zero,
        child: IconButton(
          icon: Icon(
            iOS ? CupertinoIcons.video_camera : Icons.videocam_outlined,
            color: !iOS
                ? context.theme.colorScheme.onSurface
                : (!marked && !marking || widget.controller.inSelectMode.value)
                    ? context.theme.colorScheme.primary
                    : context.theme.colorScheme.outline,
            size: iOS ? 35 : null,
          ),
          tooltip: "FaceTime Call",
          onPressed: () async {
            final data = await chat.getConversationData();
            final handle = await chat.ensureHandle();
            final handles = data.participants;
            handles.remove(handle);
            await PushSvc.placeOutgoingCall(handle, handles);
          },
        ),
      );
    });
  }
}

class ConnectionIndicator extends StatefulWidget {
  const ConnectionIndicator({super.key});

  @override
  State<ConnectionIndicator> createState() => _ConnectionIndicatorState();
}

class _ConnectionIndicatorState extends State<ConnectionIndicator> {
  bool _isVisible = false;
  bool _hasHadConnectionFailure = false;
  SocketState _displayState = SocketState.connected;
  Timer? _hideTimer;
  Worker? _worker;

  @override
  void initState() {
    super.initState();
    // Only pre-show if already mid-reconnect (e.g. widget remounted during retry cycle)
    final initial = SocketSvc.state.value;
    if (initial == SocketState.reconnecting || initial == SocketState.error) {
      _isVisible = true;
      _displayState = initial;
      _hasHadConnectionFailure = true;
    }
    _worker = ever(SocketSvc.state, _onSocketStateChanged);
  }

  void _onSocketStateChanged(SocketState state) {
    if (!mounted) return;
    if (state == SocketState.reconnecting) {
      _hasHadConnectionFailure = true;
      _hideTimer?.cancel();
      setState(() {
        _displayState = SocketState.reconnecting;
        _isVisible = true;
      });
    } else if (state == SocketState.error) {
      _hasHadConnectionFailure = true;
      _hideTimer?.cancel();
      setState(() {
        _displayState = SocketState.error;
        _isVisible = true;
      });
    } else if (state == SocketState.connected && _hasHadConnectionFailure) {
      _hideTimer?.cancel();
      setState(() {
        _displayState = SocketState.connected;
        _isVisible = true;
      });
      _hideTimer = Timer(const Duration(milliseconds: 1500), () {
        if (mounted) {
          setState(() {
            _isVisible = false;
            _hasHadConnectionFailure = false;
          });
        }
      });
    }
    // connecting and disconnected: no indicator change
  }

  @override
  void dispose() {
    _worker?.dispose();
    _hideTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final topPadding = MediaQuery.of(context).viewPadding.top;
    return Positioned(
      top: topPadding,
      left: 0,
      right: 0,
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 300),
        curve: Curves.easeInOut,
        height: _isVisible ? 4.0 : 0.0,
        color: getIndicatorColor(_displayState),
      ),
    );
  }
}

/// A send-progress [LinearProgressIndicator] shared by both header skins.
///
/// Reads [Chat.sendProgress] from [ChatStateScope] so it never needs a
/// [Chat] constructor parameter.  Place it in a [Positioned] at the bottom
/// of the header stack.
class HeaderProgressIndicator extends StatelessWidget {
  const HeaderProgressIndicator({super.key});

  @override
  Widget build(BuildContext context) {
    final chat = ChatStateScope.chatOf(context);
    return Obx(() => TweenAnimationBuilder<double>(
          duration: chat.sendProgress.value == 0
              ? Duration.zero
              : chat.sendProgress.value == 1
                  ? const Duration(milliseconds: 250)
                  : const Duration(seconds: 10),
          curve: chat.sendProgress.value == 1 ? Curves.easeInOut : Curves.easeOutExpo,
          tween: Tween<double>(
            begin: 0,
            end: chat.sendProgress.value,
          ),
          builder: (context, value, _) => AnimatedOpacity(
            opacity: value == 1 ? 0 : 1,
            duration: const Duration(milliseconds: 250),
            child: LinearProgressIndicator(
              value: value,
              backgroundColor: Colors.transparent,
              minHeight: 3,
            ),
          ),
        ));
  }
}
