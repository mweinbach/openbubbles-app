import 'package:bluebubbles/app/layouts/findmy/findmy_controller.dart';
import 'package:bluebubbles/app/wrappers/trackpad_bug_wrapper.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:bluebubbles/src/rust/api/api.dart' as api;
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_map_marker_popup/flutter_map_marker_popup.dart';
import 'package:get/get.dart';
import 'package:latlong2/latlong.dart';
import 'package:maps_launcher/maps_launcher.dart';
import 'package:url_launcher/url_launcher.dart';

class FindMyMapWidget extends StatelessWidget {
  final FindMyController controller;

  const FindMyMapWidget({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return TrackpadBugWrapper(builder: (context, bugDetected) {
      final lastLocation = SettingsSvc.settings.lastLocation.value?.split(",");
      final savedLocation = lastLocation != null
          ? LatLng(double.parse(lastLocation[0]), double.parse(lastLocation[1]))
          : const LatLng(0, 0);
      return Obx(() => FlutterMap(
            mapController: controller.mapController,
            options: MapOptions(
              initialZoom: controller.location.value == null ? 14.0 : 15.0,
              minZoom: 1.0,
              maxZoom: 18.0,
              initialCenter: controller.location.value == null
                  ? savedLocation
                  : LatLng(controller.location.value!.latitude, controller.location.value!.longitude),
              onTap: (_, __) => controller.popupController.hideAllPopups(),
              keepAlive: true,
              interactionOptions: InteractionOptions(
                flags: InteractiveFlag.all & ~InteractiveFlag.rotate,
                forceOnlySinglePinchGesture: bugDetected,
              ),
              onMapReady: () {
                if (!controller.completer.isCompleted) {
                  controller.completer.complete();
                }
              },
            ),
            children: [
              TileLayer(
                urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                userAgentPackageName: 'com.bluebubbles.app',
              ),
              PopupMarkerLayer(
                options: PopupMarkerLayerOptions(
                  popupController: controller.popupController,
                  markers: controller.markers.values.toList(),
                  onPopupEvent: (ev, markers) async {
                    final item = markers.isEmpty
                        ? null
                        : controller.friends
                            .firstWhereOrNull((e) =>
                                e.latitude == markers[0].point.latitude && e.longitude == markers[0].point.longitude)
                            ?.id;
                    if (item != null && controller.fmfClient != null) {
                      await api.selectFriend(
                          config: PushSvc.state!.osConfig, client: controller.fmfClient!, friend: item);
                    }
                  },
                  popupDisplayOptions: PopupDisplayOptions(
                    builder: (context, marker) => _buildMarkerPopup(context, marker),
                  ),
                ),
              ),
              RichAttributionWidget(
                attributions: [
                  TextSourceAttribution(
                    'OpenStreetMap contributors',
                    onTap: () => launchUrl(Uri.parse('https://openstreetmap.org/copyright')),
                  ),
                ],
              ),
            ],
          ));
    });
  }

  Widget _buildMarkerPopup(BuildContext context, Marker marker) {
    final ValueKey? key = marker.key as ValueKey?;
    final keyStr = key?.value as String? ?? '';
    if (keyStr == "current") return const SizedBox();

    if (keyStr.startsWith("device-")) {
      final deviceId = keyStr.substring("device-".length);
      final item = controller.devices.firstWhereOrNull((e) => (e.id ?? '') == deviceId);
      if (item == null) return const SizedBox();
      return _buildDevicePopup(context, item);
    } else if (keyStr.startsWith("friend-")) {
      final stableId = keyStr.substring("friend-".length);
      final item = controller.friends.firstWhereOrNull((e) => e.stableId == stableId);
      if (item == null) return const SizedBox();
      return _buildFriendPopup(context, item);
    }

    return const SizedBox();
  }

  Widget _buildDevicePopup(BuildContext context, FindMyDevice item) {
    return Obx(() => Padding(
          padding: const EdgeInsets.only(bottom: 5.0),
          child: Container(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(10),
              color: context.theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.8),
            ),
            padding: const EdgeInsets.fromLTRB(10, 10, 0, 10),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(SettingsSvc.settings.redactedMode.value ? "Device" : (item.name ?? "Unknown Device"),
                        style: context.theme.textTheme.labelLarge),
                    Text(
                      SettingsSvc.settings.redactedMode.value
                          ? "Location"
                          : (item.location?.latitude != null
                              ? "${item.location?.latitude}, ${item.location?.longitude}"
                              : ""),
                      style: context.theme.textTheme.bodySmall,
                    ),
                  ],
                ),
                if (item.location?.latitude != null && item.location?.longitude != null)
                  ButtonTheme(
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
                  ),
              ],
            ),
          ),
        ));
  }

  Widget _buildFriendPopup(BuildContext context, FindMyFriend item) {
    return Obx(() => Padding(
          padding: const EdgeInsets.only(bottom: 5.0),
          child: Container(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(10),
              color: context.theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.8),
            ),
            padding: const EdgeInsets.all(10),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(item.handle?.displayName ?? item.title ?? "Unknown Friend",
                    style: context.theme.textTheme.labelLarge),
                Text(SettingsSvc.settings.redactedMode.value ? "Location" : (item.longAddress ?? "No location found"),
                    style: context.theme.textTheme.bodySmall),
                if (item.lastUpdated != null && item.status != LocationStatus.live)
                  Text("Last updated ${buildDate(item.lastUpdated)}", style: context.theme.textTheme.bodySmall),
                if (item.status != null)
                  Text("${item.status!.name.capitalize!} Location", style: context.theme.textTheme.bodySmall),
              ],
            ),
          ),
        ));
  }
}
