import 'package:bluebubbles/helpers/helpers.dart';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';

class StatItemConfig {
  final String key;
  final String label;
  final IconData iosIcon;
  final IconData materialIcon;
  final Color containerColor;
  final bool isFullWidth;

  const StatItemConfig({
    required this.key,
    required this.label,
    required this.iosIcon,
    required this.materialIcon,
    required this.containerColor,
    this.isFullWidth = false,
  });
}

mixin IMessageStatsHelpersMixin {
  static const List<StatItemConfig> kStatItems = [
    StatItemConfig(
      key: 'messages',
      label: 'Messages',
      iosIcon: CupertinoIcons.bubble_left_bubble_right_fill,
      materialIcon: Icons.message,
      containerColor: Colors.blue,
    ),
    StatItemConfig(
      key: 'chats',
      label: 'Chats',
      iosIcon: CupertinoIcons.chat_bubble_2_fill,
      materialIcon: Icons.chat_bubble,
      containerColor: Colors.purple,
    ),
    StatItemConfig(
      key: 'handles',
      label: 'iMessage Numbers',
      iosIcon: CupertinoIcons.person_fill,
      materialIcon: Icons.person,
      containerColor: Colors.orange,
    ),
    StatItemConfig(
      key: 'attachments',
      label: 'Attachments',
      iosIcon: CupertinoIcons.paperclip,
      materialIcon: Icons.attach_file,
      containerColor: Colors.teal,
    ),
    StatItemConfig(
      key: 'images',
      label: 'Images',
      iosIcon: CupertinoIcons.photo_fill,
      materialIcon: Icons.image,
      containerColor: Colors.pink,
      isFullWidth: true,
    ),
    StatItemConfig(
      key: 'videos',
      label: 'Videos',
      iosIcon: CupertinoIcons.video_camera_solid,
      materialIcon: Icons.videocam,
      containerColor: Colors.red,
      isFullWidth: true,
    ),
    StatItemConfig(
      key: 'locations',
      label: 'Locations',
      iosIcon: CupertinoIcons.location_fill,
      materialIcon: Icons.location_on,
      containerColor: Colors.green,
      isFullWidth: true,
    ),
  ];

  /// Format a raw stat value (int or String) into a compact display string.
  String formatCount(dynamic value) {
    if (value == null) return '—';
    final num? n = value is num ? value : num.tryParse(value.toString());
    return n?.formatStatCount() ?? '—';
  }
}
