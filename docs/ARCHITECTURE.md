# Native architecture

Kotlin application stack over a Rust protocol/runtime. Android and desktop share persistence and
core messaging; each owns UI and lifecycle.

```text
Android Compose UI                 Compose Desktop UI
        |                                  |
        +----------- core APIs ------------+
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

There is no `:shared` KMP module. It was removed (AGP 9). Put shared behavior in `:core`.

## Modules

| Module | Owns |
|---|---|
| `app-native/` | Compose, login/provision, SMS/MMS, `NativePushService`, notifications, FaceTime, Android Keystore. Composition root: `CoreGraph`. |
| `core/` | JVM ingest, repos, attachments, contacts, CloudKit, backup. No `android.*`. |
| `db/` | ObjectBox Java entities + model contract. Callers pass the store directory. |
| `desktopApp/` | Compose Desktop shell. Same `:core`/`:db`; data in `~/.openbubbles-natives`. Feature-incomplete. |
| `rust/` | UniFFI API Kotlin calls. |
| `rustpush/` | Apple protocols (submodule). |
| `native/` | Gradle root. JDK 21+ required; Android Studio's bundled JBR is recommended. |
| `:android-smsmms` | Java MMS stack from the `telephony_plus` submodule. |

Details: [UI.md](UI.md), [RUST_KOTLIN.md](RUST_KOTLIN.md), [PERSISTENCE.md](PERSISTENCE.md).

## Login and provisioning

Provisioning writes `hw_info.plist` from a one-time `OABS` Mac payload. `ULoginSession` then
runs credentials, trusted-device 2FA or SMS fallback, terms, and IDS registration (`gsa.plist`,
`id.plist`). `LoginViewModel` serializes the steps. Failed sessions are dropped so a poisoned
lock cannot break retries. Remote-anisette-v3 is used for login. Hosted hardware relay is not
the default path.

## Message receive

1. `NativePushService` starts; poll vs live is applied **before** Rust init.
2. `RustBoot` + `initNative` restore state and invoke `nativeReady` (hop off the Tokio thread).
3. Live mode starts the APS loop. Poll mode runs one incremental CloudKit sync and stops.
4. Incoming pointers decode → `MessageIngestor` → ObjectBox → UI flows / notifications.
5. `completeMessage` only after successful ingest so Rust can retry.

Code: `NativePushService.kt`, `core/.../intake/MessageIngestor.kt`.

## Message send

ViewModels call `AppGraph` ports. Text, iMessage attachment, SMS, and MMS senders first stage a
complete local ObjectBox row and return its id; the composer clears only after that boundary.
App-lifetime background work then calls `NativePushState` or the Android modem, promotes the row
to the transport guid, and ingests receipts/echoes. Edit, tapback, unsend, and sticker actions use
tokenized UI overlays until the corresponding database state arrives, rolling back on failure.

Code: `ChatViewModel.kt`, `CoreGraph.kt` (`CoreSender`, `CoreAttachmentSender`),
`MmsManagerSender.kt`, `MessageRepo.stageOutgoingMessage*`.

## Background modes

- Live: persistent foreground APS, `START_STICKY`.
- Battery-saver: 15-minute WorkManager `POLL_ONCE`, then tear down (`START_NOT_STICKY`).

The activity may dispose Compose after 60s in the background; the service keeps running.

## Testing

See [VERIFY.md](VERIFY.md). JVM tests cover policy and ingest. Device/login/battery/upgrade
remain [CUTOVER.md](../tools/CUTOVER.md).
For investigation order and cross-layer evidence, start with [DEVELOPMENT.md](DEVELOPMENT.md)
rather than inferring runtime health from the last visible UI state.
