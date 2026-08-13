import 'dart:isolate';

import 'package:bluebubbles/database/models.dart';
import 'package:image/image.dart';
import 'package:universal_io/io.dart';

Future<Image?> decodeIsolate(PlatformFile file) async {
  try {
    return decodeImage(file.bytes ?? await File(file.path!).readAsBytes())!;
  } catch (_) {
    return null;
  }
}

class IsolateData {
  final PlatformFile file;
  final SendPort sendPort;

  IsolateData(this.file, this.sendPort);
}
