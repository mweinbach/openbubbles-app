import 'dart:convert';
import 'dart:io';

import 'package:logger/logger.dart';
import 'package:path/path.dart';

/// A [LogOutput] that writes to [latestFileName] and rotates it once the file
/// exceeds [maxFileSizeKB].  Rotation closes the write handle before renaming,
/// which is required on Windows — `AdvancedFileOutput` skips this step and
/// fails with errno 32 (ERROR_SHARING_VIOLATION).  If rename still fails (e.g.
/// another handle is open), falls back to copy + truncate.  All rotation
/// failures are caught silently so the logger never crashes the app.
class RotatingFileOutput extends LogOutput {
  final String dirPath;
  final String latestFileName;
  final int maxFileSizeKB;
  final int maxRotatedFilesCount;
  final Encoding encoding;
  final String Function(DateTime) fileNameFormatter;

  File? _file;
  RandomAccessFile? _raf;
  int _bytesWritten = 0;

  RotatingFileOutput({
    required this.dirPath,
    required this.latestFileName,
    this.maxFileSizeKB = 1024,
    this.maxRotatedFilesCount = 5,
    this.encoding = utf8,
    required this.fileNameFormatter,
  });

  @override
  Future<void> init() async {
    _openFile();
  }

  void _openFile() {
    try {
      _file = File(join(dirPath, latestFileName));
      if (!_file!.existsSync()) {
        _file!.createSync(recursive: true);
      }
      _bytesWritten = _file!.lengthSync();
      _raf = _file!.openSync(mode: FileMode.append);
    } catch (_) {
      _raf = null;
    }
  }

  @override
  void output(OutputEvent event) {
    final raf = _raf;
    if (raf == null) return;
    try {
      for (final line in event.lines) {
        final bytes = encoding.encode('$line\n');
        raf.writeFromSync(bytes);
        _bytesWritten += bytes.length;
      }
    } catch (_) {}
    if (_bytesWritten >= maxFileSizeKB * 1024) {
      _rotate();
    }
  }

  void _rotate() {
    try {
      // Close our write handle before renaming — required on Windows.
      _raf?.closeSync();
      _raf = null;

      final rotatedPath = join(dirPath, fileNameFormatter(DateTime.now()));
      bool rotated = false;

      try {
        _file!.renameSync(rotatedPath);
        rotated = true;
      } catch (_) {
        // Rename failed (another process holds the file open). Try copy + truncate.
        try {
          _file!.copySync(rotatedPath);
          rotated = true;
        } catch (_) {}

        if (rotated) {
          try {
            _file!.writeAsBytesSync([], mode: FileMode.write, flush: true);
          } catch (_) {
            // Truncate failed — old content stays. New writes will still append,
            // causing some duplication with the rotated copy, but no log loss.
          }
        }
      }

      _openFile();
      if (rotated) _pruneOldFiles();
    } catch (_) {
      // Rotation must never crash the logger.
      if (_raf == null) _openFile();
    }
  }

  void _pruneOldFiles() {
    try {
      final dir = Directory(dirPath);
      if (!dir.existsSync()) return;

      final rotated = dir
          .listSync()
          .whereType<File>()
          .where((f) => f.path.endsWith('.log') && !f.path.endsWith(latestFileName))
          .toList()
        ..sort((a, b) => a.statSync().modified.compareTo(b.statSync().modified));

      while (rotated.length > maxRotatedFilesCount) {
        try {
          rotated.removeAt(0).deleteSync();
        } catch (_) {}
      }
    } catch (_) {}
  }

  @override
  Future<void> destroy() async {
    try {
      _raf?.closeSync();
    } catch (_) {}
    _raf = null;
  }
}
