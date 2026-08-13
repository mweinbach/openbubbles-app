import 'package:bluebubbles/app/layouts/settings/pages/scheduling/cupertino_create_scheduled_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/scheduling/material_create_scheduled_panel.dart';
import 'package:bluebubbles/app/layouts/settings/pages/scheduling/samsung_create_scheduled_panel.dart';
import 'package:bluebubbles/app/wrappers/theme_switcher.dart';
import 'package:bluebubbles/database/models.dart';
import 'package:flutter/material.dart';

class CreateScheduledMessage extends StatelessWidget {
  const CreateScheduledMessage({super.key, this.existing});

  final ScheduledMessage? existing;

  @override
  Widget build(BuildContext context) {
    return ThemeSwitcher(
      iOSSkin: CupertinoCreateScheduledMessage(existing: existing),
      materialSkin: MaterialCreateScheduledMessage(existing: existing),
      samsungSkin: SamsungCreateScheduledMessage(existing: existing),
    );
  }
}
