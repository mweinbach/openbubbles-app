import 'package:flutter/foundation.dart';

@immutable
class DispatchedEvent {
  final String type;
  final dynamic data;

  const DispatchedEvent(this.type, this.data);
}
