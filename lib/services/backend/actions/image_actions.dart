import 'package:bluebubbles/utils/logger/logger.dart';
import 'package:convert/convert.dart';
import 'package:exif/exif.dart';
import 'package:flutter/foundation.dart';
import 'package:image/image.dart';
import 'package:universal_io/io.dart';

class ImageActions {
  static Uint8List? convertToPng(Map<String, dynamic> fileData) {
    try {
      final path = fileData['path'] as String?;
      final bytes = fileData['bytes'] as Uint8List?;

      // Get image bytes from either bytes or file path
      final Uint8List? imageBytes = bytes ?? (path != null && !kIsWeb ? File(path).readAsBytesSync() : null);

      if (imageBytes == null) {
        return null;
      }

      final image = decodeImage(imageBytes);
      if (image == null) {
        return null;
      }

      return Uint8List.fromList(encodePng(image));
    } catch (e) {
      Logger.warn('Error converting image to PNG: $e');
      return null;
    }
  }

  /// Reads EXIF data from an image file
  /// Input: Map with 'path' key containing file path
  /// Output: Map<String, String> with EXIF tag names and their printable values
  static Future<Map<String, String>?> readExifData(dynamic input) async {
    try {
      final path = input['path'] as String?;
      if (path == null || kIsWeb) {
        return null;
      }

      final file = File(path);
      if (!file.existsSync()) {
        return null;
      }

      // Read EXIF data
      final exifData = await readExifFromFile(file);

      // Convert IfdTag values to strings for serialization.
      final result = <String, String>{};
      for (var entry in exifData.entries) {
        // Only save certain EXIF tags.
        // There rae encoding issues when storing some into ObjectBox.
        // This filter allows us to keep the most useful tags, that the app will actually use.
        if (!entry.key.contains('Image') && !entry.key.contains('EXIF')) continue;
        result[entry.key] = entry.value.printable.toString();
      }

      return result;
    } catch (e) {
      Logger.warn('Error reading EXIF data: $e');
      return null;
    }
  }

  /// Reads GIF dimensions from a file without loading entire file into memory
  /// Input: Map with 'path' key containing file path
  /// Output: Map with 'width' and 'height' keys
  static Future<Map<String, int>?> getGifDimensions(dynamic input) async {
    try {
      final path = input['path'] as String?;
      if (path == null || kIsWeb) {
        return null;
      }

      final file = File(path);
      if (!file.existsSync()) {
        return null;
      }

      // Only read the first 10 bytes needed for GIF dimensions
      final bytes = await file.openRead(0, 10).first;

      String hexString = "";
      // Bytes 6 and 7 are the width bytes of a gif
      hexString += hex.encode(bytes.sublist(7, 8));
      hexString += hex.encode(bytes.sublist(6, 7));
      int width = int.parse(hexString, radix: 16);

      hexString = "";
      // Bytes 8 and 9 are the height bytes of a gif
      hexString += hex.encode(bytes.sublist(9, 10));
      hexString += hex.encode(bytes.sublist(8, 9));
      int height = int.parse(hexString, radix: 16);

      return {'width': width, 'height': height};
    } catch (e) {
      Logger.warn('Error reading GIF dimensions: $e');
      return null;
    }
  }
}
