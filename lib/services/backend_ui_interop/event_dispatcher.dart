import 'dart:async';

import 'package:bluebubbles/models/models.dart';
import 'package:get_it/get_it.dart';

// ignore: non_constant_identifier_names
EventDispatcher get EventDispatcherSvc => GetIt.I<EventDispatcher>();

class EventDispatcher {
  final StreamController<DispatchedEvent> _stream = StreamController<DispatchedEvent>.broadcast();
  Stream<DispatchedEvent> get stream => _stream.stream;

  void close() {
    _stream.close();
  }

  void emit(String type, [dynamic data]) {
    _stream.sink.add(DispatchedEvent(type, data));
  }
}
