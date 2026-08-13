import 'package:bluebubbles/app/layouts/settings/pages/server/connection_panel/cupertino_connection_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/server/connection_panel/material_connection_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/server/connection_panel/samsung_connection_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/server/server_management_panel.dart';
import 'package:bluebubbles/app/wrappers/stateful_boilerplate.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:flutter/material.dart';

class ConnectionPanel extends CustomStateful<ServerManagementPanelController> {
  const ConnectionPanel({super.key, required super.parentController});

  @override
  State<StatefulWidget> createState() => _ConnectionPanelState();
}

class _ConnectionPanelState extends CustomState<ConnectionPanel, void, ServerManagementPanelController> {
  @override
  void initState() {
    super.initState();
    forceDelete = false;
  }

  @override
  Widget build(BuildContext context) {
    return ThemeSwitcher(
      iOSSkin: CupertinoConnectionPanel(parentController: controller),
      materialSkin: MaterialConnectionPanel(parentController: controller),
      samsungSkin: SamsungConnectionPanel(parentController: controller),
    );
  }
}
