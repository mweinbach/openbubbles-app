import 'package:bluebubbles/app/layouts/findmy/findmy_controller.dart';
import 'package:bluebubbles/app/layouts/findmy/widgets/findmy_raw_data_dialog.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter_slidable/flutter_slidable.dart';
import 'package:get/get.dart';
import 'package:latlong2/latlong.dart';
import 'package:maps_launcher/maps_launcher.dart';

class FindMyDeviceListTile extends StatelessWidget {
  final FindMyDevice item;
  final FindMyController controller;
  final bool isItem;

  const FindMyDeviceListTile({
    super.key,
    required this.item,
    required this.controller,
    this.isItem = false,
  });

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      final displayName = SettingsSvc.settings.redactedMode.value
          ? (isItem ? "Item" : "Device")
          : (item.name ?? (isItem ? "Unknown Item" : "Unknown Device"));

      final displayLocation = SettingsSvc.settings.redactedMode.value
          ? "Location"
          : (item.role?["sharingActive"] == 0
              ? "${item.role?["sharingName"]} wants to share this item with you."
              : (item.address?.label ?? item.address?.mapItemFullAddress ?? "No location found"));

      final tile = ListTile(
        mouseCursor: MouseCursor.defer,
        title: Text(displayName),
        subtitle: item.role?["sharingActive"] == 0
            ? Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(displayLocation),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: context.theme.colorScheme.outline.withAlpha(64),
                          padding: const EdgeInsets.symmetric(vertical: 5, horizontal: 13),
                          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                          elevation: 0.0,
                          minimumSize: Size.zero,
                        ),
                        onPressed: () async {
                          await PushSvc.wrapPromise(controller.deleteShared(item), "Removing...");
                        },
                        child: Text("Don't Add", style: context.theme.textTheme.titleMedium),
                      ),
                      const SizedBox(width: 10),
                      ElevatedButton(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: context.theme.colorScheme.primary,
                          padding: const EdgeInsets.symmetric(vertical: 5, horizontal: 13),
                          tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                          elevation: 0.0,
                          minimumSize: Size.zero,
                        ),
                        onPressed: () async {
                          await PushSvc.wrapPromise(controller.addShared(item), "Adding...");
                        },
                        child: Text(
                          "Add",
                          style: context.theme.textTheme.titleMedium?.apply(color: context.theme.colorScheme.onPrimary),
                        ),
                      ),
                    ],
                  ),
                ],
              )
            : Text(displayLocation),
        onTap: item.location?.latitude != null && item.location?.longitude != null
            ? () async {
                if (context.isPhone) {
                  await controller.panelController.close();
                }
                await controller.completer.future;
                final marker =
                    controller.markers.values.firstWhere((e) => (e.key as ValueKey?)?.value == "device-${item.id}");
                controller.popupController.showPopupsOnlyFor([marker]);
                controller.mapController.move(LatLng(item.location!.latitude!, item.location!.longitude!), 10);
              }
            : null,
        trailing: item.location?.latitude != null && item.location?.longitude != null
            ? ButtonTheme(
                minWidth: 1,
                child: TextButton(
                  style: TextButton.styleFrom(
                    shape: const CircleBorder(),
                    backgroundColor: context.theme.colorScheme.primaryContainer,
                  ),
                  onPressed: () async {
                    await MapsLauncher.launchCoordinates(item.location!.latitude!, item.location!.longitude!);
                  },
                  child: const Icon(Icons.directions, size: 20),
                ),
              )
            : null,
        onLongPress: () async {
          showDialog(
            context: context,
            builder: (context) => FindMyRawDataDialog(item: item),
          );
        },
      );

      if (item.role?['sharingId'] != null) {
        return Slidable(
          endActionPane: ActionPane(
            motion: const StretchMotion(),
            extentRatio: 0.30,
            children: [
              SlidableAction(
                label: 'Remove',
                backgroundColor: Colors.red,
                icon: SettingsSvc.settings.skin.value == Skins.iOS ? CupertinoIcons.trash : Icons.delete_outlined,
                onPressed: (_) async {
                  showDialog(
                    context: context,
                    barrierDismissible: false,
                    builder: (BuildContext context) {
                      return AlertDialog(
                        backgroundColor: context.theme.colorScheme.surfaceContainerHighest,
                        title: Text(
                          "Deleting...",
                          style: context.theme.textTheme.titleLarge,
                        ),
                        content: SizedBox(
                          height: 70,
                          child: Center(child: buildProgressIndicator(context)),
                        ),
                      );
                    },
                  );
                  await controller.deleteShared(item);
                  Get.back();
                },
              ),
            ],
          ),
          child: tile,
        );
      }

      return tile;
    }); // end Obx
  }
}
