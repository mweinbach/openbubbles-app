import 'dart:isolate';

bool isIsolateOverride = false;
String? isolateNameOverride;

bool get isIsolate =>
    isIsolateOverride ||
    (isolateNameOverride ?? Isolate.current.debugName) != null &&
        (isolateNameOverride ?? Isolate.current.debugName) != 'main';
