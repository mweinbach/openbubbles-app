import 'package:bluebubbles/app/layouts/findmy/findmy_controller.dart';
import 'package:bluebubbles/app/layouts/findmy/widgets/findmy_device_list_tile.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/content/next_button.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/settings_widgets.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/rustpush/rustpush_service.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class FindMyItemsTabView extends StatelessWidget {
  final FindMyController controller;

  const FindMyItemsTabView({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      final allItems = controller.devices.where((item) => item.isConsideredAccessory).toList();

      final itemsWithLocation =
          allItems.where((item) => (item.location?.latitude != null || item.role?["sharingActive"] == 0)).toList();
      final itemsWithoutLocation =
          allItems.where((item) => item.location?.latitude == null && item.role?["sharingActive"] != 0).toList();

      return SliverList(
        delegate: SliverChildListDelegate([
          if (controller.fetching.value == null ||
              controller.fetching.value == true ||
              (controller.fetching.value == false && allItems.isEmpty))
            _buildEmptyState(context),
          if (itemsWithLocation.isNotEmpty || !controller.isInClique.value)
            SettingsHeader(
              iosSubtitle: context.theme.textTheme.labelLarge!.copyWith(
                color: context.theme.colorScheme.onSurface.withValues(alpha: 0.6),
                fontWeight: FontWeight.w300,
              ),
              materialSubtitle: context.theme.textTheme.labelLarge!.copyWith(
                color: context.theme.colorScheme.primary,
                fontWeight: FontWeight.bold,
              ),
              text: "Items",
            ),
          if (!controller.isInClique.value)
            SettingsSection(
              backgroundColor: context.tileColor,
              children: [
                SettingsTile(
                  title: "Show Airtags",
                  onTap: () async {
                    if (PushSvc.state?.icloudServices?.keychain == null) {
                      showSnackbar("Relog required!", "Relog required to use Backup! Relog in Settings -> Reconfigure");
                      return;
                    }
                    if (!await PushSvc.joinClique()) return;
                    controller.beaconCacheDate = null;
                    await controller.getLocations();
                  },
                  trailing: const NextButton(),
                ),
              ],
            ),
          if (itemsWithLocation.isNotEmpty)
            SettingsSection(
              backgroundColor: context.tileColor,
              children: [
                Material(
                  color: Colors.transparent,
                  child: ListView.builder(
                    physics: const NeverScrollableScrollPhysics(),
                    shrinkWrap: true,
                    padding: EdgeInsets.zero,
                    itemBuilder: (context, i) => FindMyDeviceListTile(
                      item: itemsWithLocation[i],
                      controller: controller,
                      isItem: true,
                    ),
                    itemCount: itemsWithLocation.length,
                  ),
                ),
              ],
            ),
          if (itemsWithoutLocation.isNotEmpty)
            const SizedBox(
              height: 10,
            ),
          if (itemsWithoutLocation.isNotEmpty)
            SettingsSection(
              backgroundColor: context.tileColor,
              children: [
                Material(
                  color: Colors.transparent,
                  child: ExpansionTile(
                    shape: const RoundedRectangleBorder(side: BorderSide(color: Colors.transparent)),
                    title: const Text("Items without locations"),
                    initiallyExpanded: true,
                    children: itemsWithoutLocation
                        .map((item) => FindMyDeviceListTile(
                              item: item,
                              controller: controller,
                              isItem: true,
                            ))
                        .toList(),
                  ),
                ),
              ],
            ),
        ]),
      );
    });
  }

  Widget _buildEmptyState(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.only(top: 100),
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: Text(
                controller.fetching.value == null
                    ? "Something went wrong!"
                    : controller.fetching.value == false
                        ? "You have no accessories."
                        : "Getting FindMy data...",
                style: context.theme.textTheme.labelLarge,
              ),
            ),
            if (controller.fetching.value == true) buildProgressIndicator(context, size: 15),
          ],
        ),
      ),
    );
  }
}
