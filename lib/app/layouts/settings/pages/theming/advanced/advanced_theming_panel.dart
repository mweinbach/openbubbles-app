import 'dart:async';

import 'package:bluebubbles/app/wrappers/bb_app_bar.dart';
import 'package:bluebubbles/app/wrappers/bb_scaffold.dart';
import 'package:bluebubbles/app/layouts/settings/dialogs/old_themes_dialog.dart';
import 'package:bluebubbles/app/layouts/settings/pages/theming/advanced/advanced_theming_content.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class AdvancedThemingPanel extends StatefulWidget {
  const AdvancedThemingPanel({super.key});

  @override
  State<AdvancedThemingPanel> createState() => _AdvancedThemingPanelState();
}

class _AdvancedThemingPanelState extends State<AdvancedThemingPanel> with SingleTickerProviderStateMixin, ThemeHelpers {
  int index = ThemeSvc.inDarkMode(Get.context!) ? 1 : 0;
  final StreamController streamController = StreamController.broadcast();
  late final TabController controller;
  // ignore: deprecated_member_use_from_same_package
  late final List<ThemeObject> oldThemes;

  @override
  void initState() {
    super.initState();
    controller = TabController(length: 2, vsync: this, initialIndex: index);
    // ignore: deprecated_member_use_from_same_package
    oldThemes = ThemeObject.getThemes().where((e) => !e.isPreset).toList();
  }

  @override
  void dispose() {
    streamController.close();
    super.dispose();
  }

  void clearOld() {
    oldThemes.clear();
    setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    return BBScaffold(
      backgroundColor: material ? tileColor : headerColor,
      appBar: BBAppBar(
        titleText: "Advanced Theming",
        leading: buildBackButton(context),
        actions: [
          if (oldThemes.isNotEmpty)
            TextButton(
              child: Text("View Old",
                  style: context.theme.textTheme.bodyLarge!.copyWith(color: context.theme.colorScheme.primary)),
              onPressed: () {
                showDialog(
                    context: context,
                    builder: (context) => OldThemesDialog(
                          oldThemes,
                          clearOld,
                        ));
              },
            ),
        ],
      ),
      body: TabBarView(
        controller: controller,
        physics: const NeverScrollableScrollPhysics(),
        children: <Widget>[
          AdvancedThemingContent(
            isDarkMode: false,
            controller: streamController,
          ),
          AdvancedThemingContent(
            isDarkMode: true,
            controller: streamController,
          )
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
          backgroundColor: context.theme.colorScheme.primary,
          onPressed: () {
            streamController.sink.add(null);
          },
          label: Text("Create New",
              style: context.theme.textTheme.labelLarge!.copyWith(color: context.theme.colorScheme.onPrimary)),
          icon: Icon(
            iOS ? CupertinoIcons.pencil : Icons.edit,
            color: context.theme.colorScheme.onPrimary,
          )),
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        backgroundColor: headerColor,
        destinations: [
          NavigationDestination(
            icon: Icon(iOS ? CupertinoIcons.sun_max : Icons.brightness_high),
            label: "LIGHT THEME",
          ),
          NavigationDestination(
            icon: Icon(
              iOS ? CupertinoIcons.moon : Icons.brightness_3,
            ),
            label: "DARK THEME",
          ),
        ],
        onDestinationSelected: (page) {
          setState(() {
            index = page;
          });
          controller.animateTo(page);
        },
      ),
    );
  }
}
