import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:bluebubbles/app/layouts/settings/pages/profile/posterkit.dart';
import 'package:bluebubbles/helpers/types/constants.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/helpers/ui/ui_helpers.dart';
import 'package:bluebubbles/services/backend/java_dart_interop/intents_service.dart';
import 'package:bluebubbles/services/backend/java_dart_interop/method_channel_service.dart';
import 'package:bluebubbles/services/backend/notifications/notifications_service.dart';
import 'package:bluebubbles/services/backend/settings/settings_service.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:faker/faker.dart' hide Color, Image;
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:url_launcher/url_launcher.dart';

Map<String, Route> faceTimeOverlays = {}; // Map from call uuid to overlay route

/// Hides the FaceTime overlay with the given [callUuid]
/// Also calls [NotificationsService.clearFaceTimeNotification] to clear the notification
void hideFaceTimeOverlay(String callUuid, {bool timeout = false}) {
  if (Platform.isAndroid && timeout) {
    MethodChannelSvc.invokeMethod("update-call-state", {
      "callUuid": callUuid,
      "state": "timeout",
    });
  }
  NotificationsSvc.clearFaceTimeNotification(callUuid);
  if (faceTimeOverlays.containsKey(callUuid)) {
    Get.removeRoute(faceTimeOverlays[callUuid]!);
    faceTimeOverlays.remove(callUuid);
  }
}

/// Shows a FaceTime overlay with the given [callUuid], [caller], [chatIcon], and [link]
/// Saves the overlay route in [faceTimeOverlays]
Future<void> showFaceTimeOverlay(String callUuid, String caller, Uint8List? chatIcon, String link) async {
  if (SettingsSvc.settings.redactedMode.value && SettingsSvc.settings.hideContactInfo.value) {
    if (chatIcon != null) chatIcon = null;
    caller = faker.person.name();
  }
  chatIcon ??= (await rootBundle.load("assets/images/person64.png")).buffer.asUint8List();
  chatIcon = await clip(chatIcon, size: 256, circle: true);

  // If we are somehow already showing an overlay for this call, close it
  hideFaceTimeOverlay(callUuid);

  showDialog(
      context: Get.context!,
      barrierDismissible: false,
      useRootNavigator: false,
      builder: (_) {
        return BackdropFilter(
          filter: ui.ImageFilter.blur(sigmaX: 2, sigmaY: 2),
          child: AlertDialog(
            icon: Image.memory(chatIcon!, width: 48, height: 48),
            title: Text(caller),
            content: const Text(
              "Incoming FaceTime Call",
              textAlign: TextAlign.center,
            ),
            actionsAlignment: MainAxisAlignment.center,
            actions: [
              MaterialButton(
                elevation: 0,
                hoverElevation: 0,
                color: Colors.red.withValues(alpha: 0.2),
                splashColor: Colors.red,
                highlightColor: Colors.red.withValues(alpha: 0.5),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8.0)),
                padding: const EdgeInsets.symmetric(vertical: 16.0, horizontal: 36.0),
                child: Column(
                  children: [
                    Icon(
                      SettingsSvc.settings.skin.value == Skins.iOS
                          ? CupertinoIcons.phone_down
                          : Icons.call_end_outlined,
                      color: Colors.red,
                    ),
                    const Text(
                      "Decline",
                    ),
                  ],
                ),
                onPressed: () async {
                  hideFaceTimeOverlay(callUuid);
                  await api.declineFacetime(facetime: PushSvc.state!.ftClient, guid: callUuid);
                },
              ),
              const SizedBox(width: 16.0),
              MaterialButton(
                elevation: 0,
                hoverElevation: 0,
                color: Colors.green.withValues(alpha: 0.2),
                splashColor: Colors.green,
                highlightColor: Colors.green.withValues(alpha: 0.5),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8.0)),
                padding: const EdgeInsets.symmetric(vertical: 16.0, horizontal: 36.0),
                child: Column(
                  children: [
                    Icon(
                      SettingsSvc.settings.skin.value == Skins.iOS ? CupertinoIcons.phone : Icons.call_outlined,
                      color: Colors.green,
                    ),
                    const Text(
                      "Accept",
                    ),
                  ],
                ),
                onPressed: () async {
                  hideFaceTimeOverlay(callUuid);
                  if (Platform.isAndroid) {
                    await MethodChannelSvc.invokeMethod("launch-facetime", {
                      "link": link,
                      "callUuid": callUuid,
                      "desc": caller,
                      "name": SettingsSvc.settings.userName.value == "You"
                          ? (await api.getHandles(state: PushSvc.state!.client))
                              .first
                              .replaceFirst("tel:", "")
                              .replaceFirst("mailto:", "")
                          : SettingsSvc.settings.userName.value,
                      "answer": true,
                    });
                  } else {
                    await launchUrl(Uri.parse(link), mode: LaunchMode.externalApplication);
                  }
                },
              ),
            ],
          ),
        );
      }).then((_) => faceTimeOverlays.remove(
          callUuid) /* Not explicitly necessary since all ways of closing the dialog do this, but just in case */
      );

  // Save dialog as overlay route
  faceTimeOverlays[callUuid] = Get.rawRoute!;
}

Widget phoneButton(String text, Color color, IconData icon, void Function() pressed) {
  return Column(
    mainAxisSize: MainAxisSize.min,
    children: [
      MaterialButton(
        elevation: 0,
        hoverElevation: 0,
        color: color,
        splashColor: color,
        minWidth: 0,
        highlightColor: color.withOpacity(0.5),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(64.0)),
        padding: const EdgeInsets.symmetric(vertical: 20.0, horizontal: 20.0),
        child: Icon(
          icon,
          color: Colors.white,
          size: 28,
        ),
        onPressed: pressed,
      ),
      const SizedBox(
        height: 10,
      ),
      Text(
        text,
        style: Get.context!.theme.textTheme.labelLarge?.copyWith(fontWeight: ui.FontWeight.w400),
      ),
    ],
  );
}

const List<double> darkMatrix = <double>[
  1.385, -0.56, -0.112, 0.0, 0.3, //
  -0.315, 1.14, -0.112, 0.0, 0.3, //
  -0.315, -0.56, 1.588, 0.0, 0.3, //
  0.0, 0.0, 0.0, 1.0, 0.0
];

const List<double> lightMatrix = <double>[
  1.74, -0.4, -0.17, 0.0, 0.0, //
  -0.26, 1.6, -0.17, 0.0, 0.0, //
  -0.26, -0.4, 1.83, 0.0, 0.0, //
  0.0, 0.0, 0.0, 1.0, 0.0
];

Future<void> showOutgoingFaceTimeOverlay(RxString callState, String desc, String caller, List<String> targets,
    Uint8List? chatIcon, String link, String? posterPath) async {
  api.SimplifiedIncomingCallPoster? poster;
  Map<String, ui.Image> images = {};

  // loading the poster can take a second, don't let them get too eager
  showDialog(
    context: Get.context!,
    barrierDismissible: false,
    builder: (context) => const SizedBox.shrink(),
  );

  var callUuid = callState.value;
  try {
    if (SettingsSvc.settings.redactedMode.value && SettingsSvc.settings.hideContactInfo.value) {
      if (chatIcon != null) chatIcon = null;
      desc = faker.person.name();
    }
    chatIcon ??= (await rootBundle.load("assets/images/person64.png")).buffer.asUint8List();
    chatIcon = await clip(chatIcon, size: 256, circle: true);

    if (posterPath != null && !kIsDesktop) {
      var data = await File("$posterPath.jpg").readAsBytes();
      print("Parsing file");
      poster = await api.fromPosterSave(poster: data);
      images = await loadPosterImages(posterPath, poster.poster);
    }

    // If we are somehow already showing an overlay for this call, close it
    hideFaceTimeOverlay(callUuid);
  } finally {
    Navigator.of(Get.context!, rootNavigator: true).pop();
  }

  showDialog(
      context: Get.context!,
      barrierDismissible: false,
      useSafeArea: false,
      useRootNavigator: false,
      builder: (context) {
        return Obx(() {
          var state = callState.value;
          var hasEnded = state == "timeout" || state == "declined";
          return Stack(children: [
            if (poster == null)
              BackdropFilter(
                  filter: ui.ImageFilter.compose(
                      outer: ui.ImageFilter.blur(sigmaX: 15, sigmaY: 15),
                      inner: ColorFilter.matrix(
                        CupertinoTheme.maybeBrightnessOf(Get.context!) == Brightness.dark ? darkMatrix : lightMatrix,
                      )),
                  child: Container(
                    decoration: BoxDecoration(
                      color: context.theme.colorScheme.surfaceContainerHighest.withOpacity(0.5),
                    ),
                  )),
            if (poster != null)
              ImagePoster(
                poster: poster.poster,
                images: images,
                name: desc,
                desc: hasEnded ? "Not Available" : "Ringing...",
              ),
            Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                if (poster == null)
                  SizedBox(
                    height: MediaQuery.of(Get.context!).viewPadding.top + 100,
                  ),
                if (poster == null) Image.memory(chatIcon!, width: 96, height: 96),
                if (poster == null)
                  const SizedBox(
                    height: 20,
                  ),
                if (poster == null)
                  Text(
                    desc,
                    style: context.theme.textTheme.displayMedium,
                    textAlign: TextAlign.center,
                  ),
                if (poster == null)
                  const SizedBox(
                    height: 20,
                  ),
                if (poster == null)
                  Text(hasEnded ? "Not Available" : "Ringing...",
                      textAlign: TextAlign.center,
                      style: context.theme.textTheme.titleMedium?.copyWith(color: context.theme.colorScheme.outline)),
                const Spacer(),
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    if (hasEnded) const SizedBox(width: 60),
                    if (hasEnded)
                      phoneButton(
                        "Cancel",
                        Colors.red,
                        Icons.close,
                        () async {
                          hideFaceTimeOverlay(callUuid);
                        },
                      ),
                    if (hasEnded) const Spacer(),
                    if (hasEnded)
                      phoneButton(
                        "Call Again",
                        Colors.green,
                        SettingsSvc.settings.skin.value == Skins.iOS ? CupertinoIcons.phone_fill : Icons.call,
                        () async {
                          hideFaceTimeOverlay(callUuid);
                          await PushSvc.placeOutgoingCall(caller, targets);
                        },
                      ),
                    if (hasEnded) const SizedBox(width: 60),
                    if (!hasEnded)
                      phoneButton(
                        "End",
                        Colors.red,
                        SettingsSvc.settings.skin.value == Skins.iOS ? CupertinoIcons.phone_down_fill : Icons.call_end,
                        () async {
                          hideFaceTimeOverlay(callUuid, timeout: true);
                          PushSvc.outgoingCallTimer?.cancel();
                          await api.cancelFacetime(facetime: PushSvc.state!.ftClient, guid: callUuid);
                          PushSvc.currentOutgoingCall = null;
                        },
                      ),
                  ],
                ),
                const SizedBox(
                  height: 60,
                ),
              ],
            ),
          ]);
        });
      }).then((_) async {
    faceTimeOverlays.remove(callUuid);
  });

  // Save dialog as overlay route
  faceTimeOverlays[callUuid] = Get.rawRoute!;
}
