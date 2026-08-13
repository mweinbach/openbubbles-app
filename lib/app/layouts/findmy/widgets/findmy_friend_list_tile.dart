import 'package:bluebubbles/app/components/avatars/contact_avatar_widget.dart';
import 'package:bluebubbles/app/layouts/findmy/findmy_controller.dart';
import 'package:bluebubbles/app/layouts/findmy/widgets/findmy_raw_data_dialog.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:latlong2/latlong.dart';
import 'package:maps_launcher/maps_launcher.dart';

class FindMyFriendListTile extends StatelessWidget {
  final FindMyFriend item;
  final FindMyController controller;
  final bool withLocation;

  const FindMyFriendListTile({
    super.key,
    required this.item,
    required this.controller,
    this.withLocation = true,
  });

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      final displayLocation = SettingsSvc.settings.redactedMode.value
          ? "Location"
          : withLocation
              ? ("${item.shortAddress ?? "No location found"}${item.lastUpdated == null || item.status == LocationStatus.live ? "" : "\nLast updated ${buildDate(item.lastUpdated)}"}")
              : (item.longAddress ?? "No location found");

      return ListTile(
        mouseCursor: MouseCursor.defer,
        leading: ContactAvatarWidget(handle: item.handle),
        title: Text(item.handle?.displayName ?? item.title ?? "Unknown Friend"),
        subtitle: Text(displayLocation),
        trailing: withLocation && item.latitude != null && item.longitude != null
            ? Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  if (item.status == LocationStatus.live) const Icon(CupertinoIcons.largecircle_fill_circle),
                  if (item.locatingInProgress) buildProgressIndicator(context),
                  ButtonTheme(
                    minWidth: 1,
                    child: TextButton(
                      style: TextButton.styleFrom(
                        shape: const CircleBorder(),
                        backgroundColor: context.theme.colorScheme.primaryContainer,
                      ),
                      onPressed: () async {
                        await MapsLauncher.launchCoordinates(item.latitude!, item.longitude!);
                      },
                      child: const Icon(Icons.directions, size: 20),
                    ),
                  ),
                ],
              )
            : null,
        onTap: withLocation
            ? () async {
                if (controller.fmfClient != null && item.id != null) {
                  await api.selectFriend(
                    config: PushSvc.state!.osConfig,
                    client: controller.fmfClient!,
                    friend: item.id,
                  );
                }
                if (context.isPhone) {
                  await controller.panelController.close();
                }
                await controller.completer.future;
                final marker = controller.markers[item.stableId];
                if (marker == null) return;
                controller.popupController.showPopupsOnlyFor([marker]);
                controller.mapController.move(LatLng(item.latitude!, item.longitude!), 10);
              }
            : null,
        onLongPress: () async {
          showDialog(
            context: context,
            builder: (context) => FindMyRawDataDialog(item: item),
          );
        },
      );
    });
  }
}
