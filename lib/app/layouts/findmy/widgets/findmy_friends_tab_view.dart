import 'package:bluebubbles/app/layouts/findmy/findmy_controller.dart';
import 'package:bluebubbles/app/layouts/findmy/widgets/findmy_friend_list_tile.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/settings_widgets.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class FindMyFriendsTabView extends StatelessWidget {
  final FindMyController controller;

  const FindMyFriendsTabView({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      return SliverList(
        delegate: SliverChildListDelegate([
          if (controller.fetching2.value == null ||
              controller.fetching2.value == true ||
              (controller.fetching2.value == false && controller.friends.isEmpty))
            _buildEmptyState(context),
          if (controller.friends.isNotEmpty)
            SettingsHeader(
              iosSubtitle: context.theme.textTheme.labelLarge!.copyWith(
                color: context.theme.colorScheme.onSurface.withValues(alpha: 0.6),
                fontWeight: FontWeight.w300,
              ),
              materialSubtitle: context.theme.textTheme.labelLarge!.copyWith(
                color: context.theme.colorScheme.primary,
                fontWeight: FontWeight.bold,
              ),
              text: "Friends",
            ),
          if (controller.friendsWithLocation.isNotEmpty)
            SettingsSection(
              backgroundColor: context.tileColor,
              children: [
                Material(
                  color: Colors.transparent,
                  child: ListView.builder(
                    physics: const NeverScrollableScrollPhysics(),
                    shrinkWrap: true,
                    padding: EdgeInsets.zero,
                    itemBuilder: (context, i) => FindMyFriendListTile(
                      item: controller.friendsWithLocation[i],
                      controller: controller,
                      withLocation: true,
                    ),
                    itemCount: controller.friendsWithLocation.length,
                  ),
                ),
              ],
            ),
          if (controller.friendsWithoutLocation.isNotEmpty)
            const SizedBox(
              height: 10,
            ),
          if (controller.friendsWithoutLocation.isNotEmpty)
            SettingsSection(
              backgroundColor: context.tileColor,
              children: [
                Material(
                  color: Colors.transparent,
                  child: ExpansionTile(
                    shape: const RoundedRectangleBorder(side: BorderSide(color: Colors.transparent)),
                    title: const Text("Friends without locations"),
                    initiallyExpanded: true,
                    children: controller.friendsWithoutLocation
                        .map((item) => FindMyFriendListTile(
                              item: item,
                              controller: controller,
                              withLocation: false,
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
                controller.fetching2.value == null
                    ? "Something went wrong!"
                    : controller.fetching2.value == false
                        ? "You have no friends."
                        : "Getting FindMy data...",
                style: context.theme.textTheme.labelLarge,
              ),
            ),
            if (controller.fetching2.value == true) buildProgressIndicator(context, size: 15),
          ],
        ),
      ),
    );
  }
}
