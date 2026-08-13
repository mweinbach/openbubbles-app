# API Rules — Backend & Network

## HTTP Calls

All HTTP methods live in `lib/services/network/http_service.dart`.

**Always** wrap calls in `runApiGuarded()`:
```dart
Future<Response> myEndpoint(String param) {
  return runApiGuarded(() async {
    final params = buildQueryParams({'key': param}); // adds auth GUID automatically
    return await dio.get('/endpoint', queryParameters: params);
  });
}
```

- `buildQueryParams()` must be called for every request — it injects the server auth key.
- Return `Future<Response>` for raw endpoints; typed futures when you parse the response.
- `runApiGuarded()` handles retries on 502 and propagates all other errors via `Future.error(e, s)`.
- Timeouts are configured globally from settings (`apiTimeout`) — don't set per-request timeouts.

## Interface → Action Pattern

For any new domain operation, follow the three-layer pattern:

```
Interface (lib/services/backend/interfaces/)
  ↓  builds Map<String, dynamic>, routes to isolate or direct call
Action  (lib/services/backend/actions/)
  ↓  extracts typed params, runs DB transaction, returns IDs
Interface hydrates full objects from DB using returned IDs
```

**Interface method:**
```dart
static Future<MyModel> doThing({required String guid, required int count}) async {
  final data = {'guid': guid, 'count': count};
  final id = isIsolate
      ? await MyActions.doThing(data)
      : await GetIt.I<GlobalIsolate>().send<int>(IsolateRequestType.doThing, input: data);
  return Database.myBox.get(id)!;
}
```

**Action method:**
```dart
static Future<int> doThing(Map<String, dynamic> data) async {
  final guid  = data['guid']  as String;
  final count = data['count'] as int;
  return Database.runInTransaction(TxMode.write, () {
    // ... DB work
    return newId;
  });
}
```

Rules:
- Actions always receive `Map<String, dynamic>` and extract with `as Type`.
- Provide `?? default` for optional values: `data['offset'] as int? ?? 0`.
- Actions return primitive IDs (or lists of IDs) — never full objects across isolate boundaries.
- Interfaces hydrate full objects after receiving IDs via `Database.myBox.get(id)`.
- New operation types must be added to `IsolateRequestType` enum and routed in the isolate handler.

## Socket / Real-Time Events

- Socket connection managed in `lib/services/network/socket_service.dart` — don't create additional socket instances.
- State tracked as `Rx<SocketState>` — listen to `SocketSvc.state` for connectivity changes.
- Socket reconnect logic lives in `socket_service.dart`; don't implement retry loops elsewhere.

## Backend → UI Events

Use `EventDispatcherSvc` (see `services.md`) to signal UI after backend operations complete. Prefer this over calling UI methods directly from action/service code.

## Error Handling & Logging

- Catch specific exceptions (`UniqueViolationException`, `DioException`, etc.), not bare `Exception`.
- Log with `Logger.debug/info/warn/error()` — include a `tag:` for filtering:
  ```dart
  Logger.warn('Skipping duplicate', tag: 'ChatActions');
  ```
- Expected errors (e.g., duplicate inserts) should log a warning and continue, not rethrow.
- Propagate unexpected errors: `return Future.error(e, s)` with stack trace.

## Async Conventions

- Always `await` async calls — never fire-and-forget unless intentional background work.
- For intentional fire-and-forget, use `unawaited()` from `dart:async` to make intent explicit.
- Use `Completer<void>` to coordinate between HTTP response and socket event (send progress tracking pattern).
- Run DB queries off the main thread: `await runAsync(() => query.find())`.
