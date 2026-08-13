/// A parsed log entry returned from the isolate.
///
/// Crosses the isolate boundary as a plain [Map] via [toMap]/[fromMap].
/// Integer [level] constants allow O(1) filter comparisons on the UI side
/// without any string scanning.
class ParsedLogEntry {
  static const int trace = 0;
  static const int debug = 1;
  static const int info = 2;
  static const int warn = 3;
  static const int error = 4;
  static const int fatal = 5;
  static const int unknown = 6;

  final String timestamp;
  final int level;

  /// Everything after the timestamp on the first line, plus any continuation
  /// lines (stack traces, etc.), already stripped of the leading date token.
  final String body;

  const ParsedLogEntry({
    required this.timestamp,
    required this.level,
    required this.body,
  });

  Map<String, dynamic> toMap() => {
        'ts': timestamp,
        'lv': level,
        'b': body,
      };

  factory ParsedLogEntry.fromMap(Map<String, dynamic> map) => ParsedLogEntry(
        timestamp: map['ts'] as String? ?? '',
        level: map['lv'] as int? ?? ParsedLogEntry.unknown,
        body: map['b'] as String? ?? '',
      );
}
