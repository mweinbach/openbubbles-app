import 'package:bluebubbles/app/layouts/settings/pages/server/server_management_panel.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/content/next_button.dart';
import 'package:bluebubbles/app/layouts/settings/widgets/settings_widgets.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:get/get.dart';
import 'package:universal_io/io.dart';

/// Optimized reactive tile for Device settings
/// Only rebuilds when socket.state changes
class ConnectionServerTile extends StatelessWidget {
  final Color tileColor;
  final bool samsung;
  final bool iOS;
  final bool material;

  const ConnectionServerTile({
    super.key,
    required this.tileColor,
    required this.samsung,
    required this.iOS,
    required this.material,
  });

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      String? subtitle;
      switch (SocketSvc.state.value) {
        case SocketState.connected:
          subtitle = "Connected";
          break;
        case SocketState.disconnected:
          subtitle = "Disconnected";
          break;
        case SocketState.error:
          subtitle = "Error";
          break;
        case SocketState.connecting:
          subtitle = "Connecting";
          break;
        case SocketState.reconnecting:
          subtitle = "Reconnecting";
          break;
      }

      return SettingsTile(
        backgroundColor: tileColor,
        title: "Connection & Server",
        onTap: () {
          NavigationSvc.pushAndRemoveSettingsUntil(
            context,
            ServerManagementPanel(),
            (Route route) => route.isFirst,
          );
        },
        onLongPress: () {
          Clipboard.setData(ClipboardData(text: HttpSvc.origin));
          if (!Platform.isAndroid || (FilesystemSvc.androidInfo?.version.sdkInt ?? 0) < 33) {
            showToast("Server address copied to clipboard");
          }
        },
        leading: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Material(
              shape: samsung
                  ? SquircleBorder(
                      side: BorderSide(
                        color: getIndicatorColor(SocketSvc.state.value),
                        width: 3.0,
                      ),
                    )
                  : null,
              color: SettingsSvc.settings.skin.value != Skins.Material
                  ? getIndicatorColor(SocketSvc.state.value)
                  : Colors.transparent,
              borderRadius: iOS ? BorderRadius.circular(6) : null,
              child: SizedBox(
                width: 30,
                height: 30,
                child: Stack(
                  alignment: Alignment.center,
                  children: [
                    Icon(
                      iOS ? CupertinoIcons.antenna_radiowaves_left_right : Icons.router,
                      color: SettingsSvc.settings.skin.value != Skins.Material ? Colors.white : Colors.grey,
                      size: SettingsSvc.settings.skin.value != Skins.Material ? 21 : 28,
                    ),
                    if (material)
                      Positioned.fill(
                        child: Align(
                          alignment: Alignment.bottomRight,
                          child: getSocketStateIndicatorIcon(
                            SocketSvc.state.value,
                            size: 12,
                            showAlpha: false,
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ],
        ),
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              subtitle,
              style: context.theme.textTheme.bodyMedium!.apply(color: context.theme.colorScheme.outline.withAlpha(220)),
            ),
            const SizedBox(width: 5),
            const NextButton(),
          ],
        ),
      );
    });
  }
}
