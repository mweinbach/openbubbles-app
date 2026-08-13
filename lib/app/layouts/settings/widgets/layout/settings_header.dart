import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';

class SettingsHeader extends StatelessWidget {
  final TextStyle? iosSubtitle;
  final TextStyle? materialSubtitle;
  final String text;
  final double? height;

  const SettingsHeader({
    super.key,
    required this.iosSubtitle,
    required this.materialSubtitle,
    required this.text,
    this.height,
  });

  @override
  Widget build(BuildContext context) {
    if (SettingsSvc.settings.skin.value == Skins.Samsung) return const SizedBox(height: 10);
    return Container(
      height: height ?? (SettingsSvc.settings.skin.value == Skins.iOS ? 60 : 40),
      alignment: Alignment.bottomLeft,
      color: Colors.transparent,
      child: Padding(
        padding: EdgeInsets.only(bottom: 8.0, left: SettingsSvc.settings.skin.value == Skins.iOS ? 30 : 15),
        child: Text(text.psCapitalize,
            style: SettingsSvc.settings.skin.value == Skins.iOS ? iosSubtitle : materialSubtitle),
      ),
    );
  }
}
