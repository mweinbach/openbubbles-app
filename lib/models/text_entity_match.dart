import 'package:flutter/foundation.dart';

@immutable
class TextEntityMatch {
  final String type;
  final int start;
  final int end;
  final List? metadata;

  const TextEntityMatch(this.type, this.start, this.end, this.metadata);
}
