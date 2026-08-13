import 'dart:async';
import 'dart:io';
import 'dart:typed_data';

import 'package:bluebubbles/database/global/platform_file.dart';
import 'package:bluebubbles/services/services.dart';
import 'package:desktop_drop/desktop_drop.dart';
import 'package:get/get.dart';
import 'package:universal_io/io.dart';

/// Manages drag-and-drop file operations on the message list.
///
/// Responsibilities:
/// - Track drag state (files over the drop zone)
/// - Validate dropped files
/// - Convert dropped files to PlatformFile objects
/// - Add files to the attachment picker
class DropZoneManager {
  final ConversationViewController controller;

  /// Whether files are currently being dragged over the drop zone
  final RxBool dragging = false.obs;

  /// Number of valid files in the current drag session
  final RxInt numFiles = 0.obs;

  DropZoneManager({required this.controller});

  /// Check if drop is allowed (only copy operations with file format items)
  void onDropOver(DropEventDetails event) {
    dragging.value = true;
  }

  /// Handle drag leaving the drop zone
  void onDropLeave(DropEventDetails event) {
    dragging.value = false;
  }

  /// Process dropped files and add them to attachments.
  ///
  /// Awaits all reader callbacks before returning so the native drop session
  /// stays alive until data has been fully read.
  Future<void> onPerformDrop(
    DropDoneDetails event,
    ConversationViewController controller,
  ) async {
    for (DropItem item in event.files) {
      if (await FileSystemEntity.type(item.path) != FileSystemEntityType.file) continue;

      Uint8List bytes = await item.readAsBytes();
      String fileName = item.name;

      if (fileName.isEmpty) {
        fileName = "Dragged_File_${controller.pickedAttachments.length + 1}";
      }

      controller.pickedAttachments.add(PlatformFile(
        name: fileName,
        size: bytes.length,
        bytes: bytes,
      ));
    }

    dragging.value = false;
  }
}
