# lib/services/isolates/ — Background Isolate System

## Files
| File | Purpose |
|------|---------|
| `global_isolate.dart` | Base isolate manager + `IsolateRequestType` enum + `IsolateRequest`/`IsolateResponse` types |
| `incremental_sync_isolate.dart` | Lightweight sync-only subclass of `GlobalIsolate` |
| `isolate_actions.dart` | `IsolateActons.actions` — the `Map<IsolateRequestType, Function>` routing table |
| `isolate_event.dart` | `IsolateEvent` enum + `IsolateEventMessage` + `IsolateEventEmitter` |

## GlobalIsolate
Persistent background Dart isolate for all heavy work (DB reads/writes, sync, bulk processing).
- Spawned once at startup; registered with GetIt as `GetIt.I<GlobalIsolate>()`
- `idleTimeout: 5 minutes` — auto-kills after 5 min idle, restarts on next `send()`
- Communication: `send<T>(IsolateRequestType, input: data)` → returns `Future<T>`
- Fire-and-forget: `broadcast(IsolateRequestType, data)` — no response awaited

## IncrementalSyncIsolate
Subclass of `GlobalIsolate` with sync-specific service init (`StartupTasks.initSyncIsolateServices`).
- `idleTimeout: Duration.zero` — self-terminates immediately after work completes
- Registered with GetIt as `GetIt.I<IncrementalSyncIsolate>()`

## Adding a New Action
1. Add the method to the appropriate `*_actions.dart` file in `actions/`
2. Add the `IsolateRequestType` enum value in `global_isolate.dart`
3. Register it in `IsolateActons.actions` map in `isolate_actions.dart`
4. Call it via the matching `*_interface.dart` — never call `GlobalIsolate.send()` directly from business logic

## isIsolate Flag
Defined in `lib/env.dart`. When `isIsolate == true`, interface methods call the action directly instead of dispatching to `GlobalIsolate` — this prevents isolates from spawning sub-isolates.

## IsolateEvent (isolate → main thread push)
From within an action running in the isolate:
```dart
IsolateEventEmitter.emit(IsolateEvent.socketMessage, data);
```
From the main thread, subscribe:
```dart
GetIt.I<GlobalIsolate>().addEventListener(IsolateEvent.socketMessage, (data) { ... });
```
Currently only `IsolateEvent.socketMessage` exists.
