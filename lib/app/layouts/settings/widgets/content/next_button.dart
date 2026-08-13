import 'package:bluebubbles/helpers/types/constants.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class NextButton extends StatelessWidget {
  const NextButton({super.key});

  @override
  Widget build(BuildContext context) {
    return Obx(() => SettingsSvc.settings.skin.value != Skins.Material
        ? Icon(
            SettingsSvc.settings.skin.value != Skins.Material ? CupertinoIcons.chevron_right : Icons.arrow_forward,
            color: context.theme.colorScheme.outline.withValues(alpha: 0.5),
            size: 18,
          )
        : const SizedBox.shrink());
  }
}
