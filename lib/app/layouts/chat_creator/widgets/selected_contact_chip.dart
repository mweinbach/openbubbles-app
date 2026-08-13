import 'package:bluebubbles/app/layouts/chat_creator/chat_creator.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// Extracted widget to prevent rebuilding all chips when one chip's iMessage status changes
class SelectedContactChip extends StatelessWidget {
  const SelectedContactChip({
    super.key,
    required this.contact,
    required this.onRemove,
  });

  final SelectedContact contact;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 2.5),
      child: Obx(() => Material(
            color: contact.serviceType.value == ChatServiceType.iMessage
                ? context.theme.colorScheme.bubble(context, true).withValues(alpha: 0.2)
                : contact.serviceType.value == ChatServiceType.sms
                    ? context.theme.colorScheme.bubble(context, false).withValues(alpha: 0.2)
                    : context.theme.colorScheme.surfaceContainerHighest,
            borderRadius: BorderRadius.circular(5),
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              onTap: onRemove,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 7.5, vertical: 7.0),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: <Widget>[
                    Text(
                        contact.displayName.isPhoneNumber
                            ? formatPhoneNumber(contact.displayName)
                            : contact.displayName,
                        style: context.theme.textTheme.bodyMedium!.copyWith(
                          color: contact.serviceType.value == ChatServiceType.iMessage
                              ? context.theme.colorScheme.bubble(context, true)
                              : contact.serviceType.value == ChatServiceType.sms
                                  ? context.theme.colorScheme.bubble(context, false)
                                  : context.theme.colorScheme.onSurfaceVariant,
                        )),
                    const SizedBox(width: 5.0),
                    Icon(
                      SettingsSvc.settings.skin.value == Skins.iOS ? CupertinoIcons.xmark : Icons.close,
                      size: 15.0,
                    ),
                  ],
                ),
              ),
            ),
          )),
    );
  }
}
