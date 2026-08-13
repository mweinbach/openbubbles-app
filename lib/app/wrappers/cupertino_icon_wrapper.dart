import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/widgets.dart';
import 'package:get/get.dart';

class CupertinoIconWrapper extends StatelessWidget {
  const CupertinoIconWrapper({super.key, required Icon this.icon});

  final Widget icon;

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      if (SettingsSvc.settings.skin.value != Skins.iOS) return icon;
      return Padding(padding: const EdgeInsets.only(left: 1.0), child: icon);
    });
  }
}
