import 'package:bluebubbles/database/models.dart';
import 'package:bluebubbles/env.dart';
import 'package:bluebubbles/services/backend/actions/image_actions.dart';
import 'package:flutter/foundation.dart';
import 'package:get_it/get_it.dart';
import 'package:bluebubbles/services/isolates/global_isolate.dart';

class ImageInterface {
  static Future<Uint8List?> convertToPng(PlatformFile file) async {
    final fileData = {
      'name': file.name,
      'path': file.path,
      'bytes': file.bytes,
      'size': file.size,
    };

    if (isIsolate) {
      return ImageActions.convertToPng(fileData);
    } else {
      return await GetIt.I<GlobalIsolate>().send<Uint8List?>(IsolateRequestType.convertImageToPng, input: fileData);
    }
  }

  /// Reads EXIF data from an image file in the global isolate
  /// Returns a map of EXIF tag names to their string values
  static Future<Map<String, String>?> readExifData(String filePath) async {
    final input = {'path': filePath};

    if (isIsolate) {
      return await ImageActions.readExifData(input);
    } else {
      return await GetIt.I<GlobalIsolate>().send<Map<String, String>?>(IsolateRequestType.readExifData, input: input);
    }
  }

  /// Reads GIF dimensions from a file in the global isolate
  /// Returns a map with 'width' and 'height' keys
  static Future<Map<String, int>?> getGifDimensions(String filePath) async {
    final input = {'path': filePath};

    if (isIsolate) {
      return await ImageActions.getGifDimensions(input);
    } else {
      return await GetIt.I<GlobalIsolate>().send<Map<String, int>?>(IsolateRequestType.getGifDimensions, input: input);
    }
  }
}
