import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// Extracted widget to isolate rebuilds to just the toggle buttons
class MessageTypeToggle extends StatelessWidget {
  const MessageTypeToggle({
    super.key,
    required this.selectedService,
    required this.onToggle,
  });

  final ChatServiceType selectedService;
  final Function(int) onToggle;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 15.0).add(const EdgeInsets.only(bottom: 5.0)),
      child: ToggleButtons(
        constraints: BoxConstraints(minWidth: (NavigationSvc.width(context) - 35) / 2),
        fillColor: context.theme.colorScheme.bubble(context, selectedService.isIMessageService).withValues(alpha: 0.2),
        splashColor:
            context.theme.colorScheme.bubble(context, selectedService.isIMessageService).withValues(alpha: 0.2),
        borderRadius: BorderRadius.circular(20),
        selectedBorderColor: context.theme.colorScheme.bubble(context, selectedService.isIMessageService),
        selectedColor: context.theme.colorScheme.bubble(context, selectedService.isIMessageService),
        isSelected: [selectedService == ChatServiceType.iMessage, selectedService == ChatServiceType.sms],
        onPressed: onToggle,
        children: const [
          Row(
            children: [
              Padding(
                padding: EdgeInsets.all(8.0),
                child: Text("iMessage"),
              ),
              Icon(CupertinoIcons.chat_bubble, size: 16),
            ],
          ),
          Row(
            children: [
              Padding(
                padding: EdgeInsets.all(8.0),
                child: Text("Text Message"),
              ),
              Icon(Icons.messenger_outline, size: 16),
            ],
          ),
        ],
      ),
    );
  }
}
