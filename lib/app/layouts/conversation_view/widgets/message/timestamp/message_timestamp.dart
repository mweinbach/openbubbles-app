import 'dart:math';

import 'package:bluebubbles/app/state/message_state.dart';

import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

class MessageTimestamp extends StatelessWidget {
  const MessageTimestamp({super.key, required this.controller, required this.cvController});

  final MessageState controller;
  final ConversationViewController cvController;

  Message get message => controller.message;

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      // Use MessageState observable for reactivity
      final dateCreated = controller.dateCreated.value;
      final oneLine =
          SettingsSvc.settings.skin.value == Skins.Samsung ? true : buildDate(dateCreated) == buildTime(dateCreated);
      final time = oneLine
          ? "   ${buildTime(dateCreated)}"
          : "   ${buildDate(dateCreated)}\n   ${buildTime(dateCreated).toLowerCase()}";
      return AnimatedContainer(
        duration: Duration(milliseconds: cvController.timestampOffset.value == 0 ? 150 : 0),
        width: SettingsSvc.settings.skin.value == Skins.Samsung
            ? null
            : min(max(-cvController.timestampOffset.value, 0), 70),
        child: Offstage(
          offstage: SettingsSvc.settings.skin.value != Skins.Samsung && cvController.timestampOffset.value == 0,
          child: Text(
            time,
            style: context.theme.textTheme.labelSmall!
                .copyWith(color: context.theme.colorScheme.outline, fontWeight: FontWeight.normal),
            overflow: TextOverflow.visible,
            softWrap: false,
            maxLines: oneLine ? 1 : 2,
          ),
        ),
      );
    });
  }
}
