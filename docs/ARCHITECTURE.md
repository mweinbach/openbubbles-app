# Native architecture

OpenBubbles is a Kotlin application stack over a Rust protocol/runtime layer.
Android and desktop share persistence and core messaging behavior but own their
platform UI and lifecycle integrations.

```text
Android Compose UI                 Compose Desktop UI
        |                                  |
        +----------- app/core APIs --------+
                           |
                  core/ repositories
                  intake, sync, backup
                           |
                 db/ ObjectBox entities
                           |
                UniFFI Kotlin bindings
                           |
               rust/ application facade
                           |
              rustpush/ Apple protocols
```

## Modules

### `app-native`

The Android application owns Compose navigation, provisioning/login screens,
permissions, contacts import, SMS/MMS receivers, WorkManager polling,
`NativePushService`, notifications, FaceTime surfaces, and Android Keystore
integration.

### `core`

Plain Kotlin/JVM business logic shared by Android and desktop. It contains
message ingestion, chat/message repositories, attachment transfer, contacts,
CloudKit orchestration, and backup/restore. Framework-specific APIs should not
cross into this module.

### `db`

ObjectBox entities and the stable model contract. Android stores data under the
legacy application data location so an in-place upgrade can open the existing
store. `:db:checkModelParity` prevents accidental UID/model drift.

### `shared`

Kotlin Multiplatform contracts and small cross-platform utilities. Shared
product behavior generally belongs in `core`; `shared` is for code that must be
compiled for both Android and desktop targets directly.

### `desktopApp`

Compose Desktop shell and lifecycle. It shares `core`, `db`, and UniFFI but owns
desktop data paths, packaging, and window behavior. Desktop migration and
feature parity remain active work.

### `rust` and `rustpush`

`rust` exposes the Kotlin-facing UniFFI API and coordinates native state.
`rustpush` implements Apple authentication, APS, IDS, CloudKit, attachment,
Find My, FaceTime, and related protocol behavior.

## Login and provisioning

Provisioning writes `hw_info.plist`. `ULoginSession` then drives a state machine
for credentials, trusted-device 2FA, SMS fallback/phone selection, account terms,
and IDS registration. Successful stages persist `gsa.plist` and `id.plist`.
Kotlin's `LoginViewModel` serializes actions and renders each required user step.

The remote-anisette-v3 provider is used for login. Authentication failures drop
the current session so a Rust panic or poisoned lock cannot break every retry.

## Message receive flow

1. Android starts `NativePushService` as a foreground service.
2. The start intent selects persistent APNs mode or a one-shot poll before Rust
   initialization begins.
3. Rust restores native state and invokes `nativeReady`.
4. Persistent mode starts the APS loop. Poll mode performs one incremental
   CloudKit sync and stops.
5. Incoming Rust messages are decoded, ingested by `core`, persisted to
   ObjectBox, and posted through Android notifications.
6. A queue entry is completed only after successful handling so Rust can retry
   transient ingestion failures.

## Message send flow

Compose view models call send contracts implemented in `app-native`/`core`.
Optimistic rows are persisted for immediate UI feedback, then the UniFFI state
sends text or attachments through Rust. Delivery updates reconcile the stored
message state.

## Background modes

- Live mode owns a persistent foreground APS connection and returns
  `START_STICKY` for process recovery.
- Battery-saver mode schedules constrained 15-minute WorkManager polls. Each
  poll starts the service with `POLL_ONCE`, runs one sync, and returns
  `START_NOT_STICKY`.

Device acceptance must cover reboot, package replacement, force-stop recovery,
notification actions, delayed polling, and a 24-hour battery sample.

## Testing boundaries

- `db` verifies entities and model compatibility.
- `core` tests ingestion, attachments, contacts, CloudKit, and backup behavior.
- `app-native` tests Android-independent UI state machines and platform policy.
- `rustpush` runs fixture-free unit tests; APNs proxy/replay tools are manual.
- Instrumentation/journey coverage is still required for framework and device
  behavior that JVM tests cannot prove.
