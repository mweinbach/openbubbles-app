import 'package:logger/logger.dart';

/// Wrapper for file output that strips ANSI color codes before writing
class FileOutputWrapper extends LogOutput {
  final LogOutput fileOutput;

  FileOutputWrapper(this.fileOutput);

  @override
  void output(OutputEvent event) {
    // Strip ANSI escape sequences from all lines before writing to file
    final cleanedLines = event.lines.map((line) => line.replaceAll(RegExp(r'\x1B\[[0-?]*[ -/]*[@-~]'), '')).toList();

    // Save original lines to restore after output
    final originalLines = List<String>.from(event.lines);

    try {
      event.lines.clear();
      event.lines.addAll(cleanedLines);
      fileOutput.output(event);
    } finally {
      // Restore original lines in case other outputs need them
      event.lines.clear();
      event.lines.addAll(originalLines);
    }
  }

  @override
  Future<void> init() {
    return fileOutput.init();
  }

  @override
  Future<void> destroy() {
    return fileOutput.destroy();
  }
}
