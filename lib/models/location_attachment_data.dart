import 'package:flutter/foundation.dart';

@immutable
class LocationAttachmentData {
  final String guid;
  final String fileName;
  final Uint8List bytes;
  final String mapImageUrl;
  final String? title;

  const LocationAttachmentData({
    required this.guid,
    required this.fileName,
    required this.bytes,
    required this.mapImageUrl,
    this.title,
  });
}
