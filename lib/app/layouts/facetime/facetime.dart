import 'dart:async';
import 'dart:io';

import 'package:bluebubbles/app/components/avatars/contact_avatar_group_widget.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/content/next_button.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/database/io/handle.dart';
import 'package:bluebubbles/database/io/chat.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/settings_widgets.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_slidable/flutter_slidable.dart';
import 'package:get/get.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:external_path/external_path.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:photo_manager/photo_manager.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:bluebubbles/app/layouts/facetime/facetime_creator.dart';

class FaceTimePanel extends StatefulWidget {
  const FaceTimePanel({super.key});

  @override
  State<StatefulWidget> createState() => _FaceTimePanelState();
}

class _FaceTimePanelState extends State<FaceTimePanel> with ThemeHelpers {
  @override
  void initState() {
    super.initState();
    PushSvc.updateState();
  }

  @override
  void dispose() {
    // refreshTimer?.cancel();
    super.dispose();
  }

  Widget buildSession(api.FTSession session, bool active) {
    var participants = session.members.where((a) => !session.myHandles.contains(a.handle)).map((a) {
      if (a.nickname != null) {
        return Handle(address: "Maybe: ${a.nickname}");
      } else {
        return RustPushBBUtils.rustHandleToBB(a.handle);
      }
    }).toList();
    return SettingsTile(
      key: ValueKey(session.groupId),
      title: participants.isEmpty ? "Empty" : participants.map((a) => a.displayName).join(", "),
      subtitle: active ? "Tap to Join" : "FaceTime",
      leading: ContactAvatarGroupWidget(
        participants: participants,
        size: 40,
        editable: false,
      ),
      trailing: active
          ? Container(
              child: Icon(
                CupertinoIcons.videocam_fill,
                size: 20,
                color: context.theme.colorScheme.onBubble(context, false),
              ),
              padding: const EdgeInsets.all(4),
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: context.theme.colorScheme.bubble(context, false),
              ),
            )
          : Row(mainAxisSize: MainAxisSize.min, children: [
              if (session.mode == api.FTMode.outgoing)
                Icon(
                  Icons.call_made,
                  color: context.theme.colorScheme.bubble(context, false),
                  size: 17,
                ),
              if (session.mode == api.FTMode.incoming)
                Icon(
                  Icons.call_received,
                  color: context.theme.colorScheme.bubble(context, false),
                  size: 17,
                ),
              if (session.mode == api.FTMode.missed) const Icon(Icons.call_missed, color: Colors.redAccent, size: 17),
              if (session.mode == api.FTMode.missedOutgoing)
                const Icon(Icons.call_missed_outgoing, color: Colors.redAccent, size: 17),
              if (session.mode != null) const SizedBox(width: 5),
              if (session.startTime != null)
                Text(
                  buildDate(DateTime.fromMillisecondsSinceEpoch(session.startTime!)),
                  style: context.theme.textTheme.bodyMedium!
                      .apply(color: context.theme.colorScheme.outline.withOpacity(0.85)),
                ),
            ]),
      onTap: () async {
        if (session.members.length == 2 && !active) {
          // 1-1 facetime, call again
          await PushSvc.placeOutgoingCall(session.myHandles.first,
              [session.members.firstWhere((a) => !session.myHandles.contains(a.handle)).handle]);
          return;
        }
        PushSvc.chosenFTRoomGuid = session.groupId;
        // should be cached
        var link = await api.getFtLink(facetime: PushSvc.state!.ftClient, usage: "next");
        var desc = participants.map((p) => p.displayName).join(" & ");
        // rotate link
        PushSvc.rotateLink().catchError((e, s) {
          Logger.error("Failed to rotate link", error: e, trace: s);
        });

        if (Platform.isAndroid) {
          await MethodChannelSvc.invokeMethod(
              "launch-facetime", {'link': link, 'desc': desc, 'callUuid': session.groupId});
        } else {
          await launchUrl(Uri.parse(link), mode: LaunchMode.externalApplication);
        }
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Obx(() => SettingsScaffold(
            title: "Video Calls",
            initialHeader: PushSvc.activeSessions.isEmpty ? "Calls" : "Active",
            iosSubtitle: iosSubtitle,
            materialSubtitle: materialSubtitle,
            tileColor: tileColor,
            headerColor: headerColor,
            actions: [
              IconButton(
                icon: Icon(iOS ? CupertinoIcons.add : Icons.add, color: context.theme.colorScheme.primary),
                onPressed: () {
                  Navigator.of(context).push(
                    ThemeSwitcher.buildPageRoute(
                      builder: (context) => const FaceTimeCreator(),
                    ),
                  );
                },
              ),
            ],
            bodySlivers: [
              SliverList(
                delegate: SliverChildListDelegate(<Widget>[
                  if (PushSvc.activeSessions.isNotEmpty)
                    SettingsSection(
                      backgroundColor: tileColor,
                      children: PushSvc.activeSessions.map((s) => buildSession(s, true)).toList(),
                    ),
                  if (PushSvc.activeSessions.isNotEmpty && PushSvc.sessions.isNotEmpty)
                    SettingsHeader(iosSubtitle: iosSubtitle, materialSubtitle: materialSubtitle, text: "Calls"),
                  if (PushSvc.sessions.isNotEmpty)
                    SettingsSection(backgroundColor: tileColor, children: [
                      ListView.builder(
                        itemBuilder: (context, idx) => buildSession(PushSvc.sessions[idx], false),
                        itemCount: PushSvc.sessions.length,
                        shrinkWrap: true,
                        physics: const NeverScrollableScrollPhysics(),
                        findChildIndexCallback: (key) =>
                            findChildIndexByKey(PushSvc.sessions, key, (item) => item.groupId),
                      )
                    ]),
                  if (PushSvc.sessions.isEmpty && PushSvc.activeSessions.isEmpty)
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 30),
                      child: Text(
                        "No Calls! Users can add you to FaceTime calls. They'll show up here, where you can join them.",
                        style: context.textTheme.bodyMedium!,
                      ),
                    ),
                ]),
              )
            ]));
  }

  void saveSettings() {
    SettingsSvc.settings.saveAsync();
  }
}
