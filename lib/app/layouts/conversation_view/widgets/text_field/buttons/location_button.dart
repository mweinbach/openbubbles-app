import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/helpers/helpers.dart';
import 'package:bluebubbles/utils/share.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:universal_io/io.dart';

/// Widget for the location sharing button (desktop only, not Linux)
class LocationButton extends StatelessWidget {
  final Chat chat;

  const LocationButton({
    super.key,
    required this.chat,
  });

  @override
  Widget build(BuildContext context) {
    if (!kIsDesktop || Platform.isLinux) {
      return const SizedBox.shrink();
    }

    return IconButton(
      icon: Icon(
        context.iOS ? CupertinoIcons.location_solid : Icons.location_on_outlined,
        color: context.theme.colorScheme.outline,
        size: 28,
      ),
      onPressed: () async {
        await Share.location(chat);
      },
    );
  }
}
