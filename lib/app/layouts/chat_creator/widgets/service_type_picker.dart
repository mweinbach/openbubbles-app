import 'package:bluebubbles/app/layouts/chat_creator/chat_creator_controller.dart';
import 'package:bluebubbles/app/layouts/chat_creator/chat_service_type.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// A CupertinoSegmentedControl that lets the user choose between visible
/// [ChatServiceType] values (e.g. iMessage and SMS). RCS is defined in the
/// enum but hidden until ready.
class ServiceTypePicker extends StatelessWidget {
  const ServiceTypePicker({super.key, required this.controller});

  final ChatCreatorController controller;

  @override
  Widget build(BuildContext context) {
    final visibleTypes = ChatServiceType.values.where((t) => t.isVisible).toList();
    final primary = context.theme.colorScheme.primary;
    final onPrimary = context.theme.colorScheme.onPrimary;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
      child: Obx(() => CupertinoSegmentedControl<ChatServiceType>(
            groupValue: controller.selectedService.value,
            onValueChanged: controller.onServiceChanged,
            borderColor: primary,
            selectedColor: primary,
            unselectedColor: Colors.transparent,
            pressedColor: primary.withValues(alpha: 0.1),
            children: {
              for (final type in visibleTypes)
                type: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12.0, vertical: 6.0),
                  child: Text(
                    type.label,
                    style: context.theme.textTheme.bodyMedium?.copyWith(
                      color: controller.selectedService.value == type ? onPrimary : primary,
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ),
            },
          )),
    );
  }
}
