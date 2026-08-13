import 'package:bluebubbles/app/layouts/settings/pages/scheduling/cupertino_scheduled_messages_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/scheduling/material_scheduled_messages_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/scheduling/samsung_scheduled_messages_panel.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:flutter/material.dart';

class ScheduledMessagesPanel extends StatelessWidget {
  const ScheduledMessagesPanel({super.key});

  @override
  Widget build(BuildContext context) {
    return const ThemeSwitcher(
      iOSSkin: CupertinoScheduledMessagesPanel(),
      materialSkin: MaterialScheduledMessagesPanel(),
      samsungSkin: SamsungScheduledMessagesPanel(),
    );
  }
}
