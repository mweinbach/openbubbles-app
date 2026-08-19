# The Rust backend reference

Deep reference for everything below `:core`'s UniFFI Kotlin bindings: the `rust/`
application facade, the `rustpush/` Apple-protocol submodule, and the
lifecycle/contract the Kotlin app must uphold. Read [RUST_KOTLIN.md](../RUST_KOTLIN.md)
first for the one-page orientation; use this folder when you need the actual API
surface, state machines, file formats, or a change recipe. For module-level app
architecture see [ARCHITECTURE.md](../ARCHITECTURE.md).

Everything here is the shipping Kotlin+Rust stack. Flutter Rust Bridge (FRB) leftovers
compile but are dead surface ([FRB legacy](#frb-legacy)).

## Folder layout

| Doc | What it covers |
|---|---|
| [foundations/runtime.md](foundations/runtime.md) | Tokio runtime, sync-vs-async exports, delegates, logging |
| [foundations/lifecycle.md](foundations/lifecycle.md) | Boot order, live vs poll, teardown, reconnects |
| [foundations/submodules.md](foundations/submodules.md) | The submodule tree (rustpush, telephony_plus + nested), clone/init, pointer bumps |
| [foundations/state.md](foundations/state.md) | In-memory `SharedPushState` + every on-disk config file |
| [messaging/message-model.md](messaging/message-model.md) | `UMessage`/`UPart`/`UMessageInst` variant reference |
| [messaging/incoming.md](messaging/incoming.md) | Receive loop, pointer queue, durable journal, SendConfirm, re-auth |
| [messaging/outgoing.md](messaging/outgoing.md) | Full send surface, attachments, group-version invariant |
| [account/login.md](account/login.md) | Provisioning, `ULoginSession` state machine, 2FA, phone registration |
| [account/keystore.md](account/keystore.md) | Keystore backends, import wrapping, key-alias registry |
| [icloud/history-sync.md](icloud/history-sync.md) | Messages-in-iCloud pull/push, cursors, clique join |
| [icloud/services.md](icloud/services.md) | Passwords, Shared Albums, Find My, FaceTime, StatusKit, profiles |
| [internals/rustpush.md](internals/rustpush.md) | rustpush protocol internals |
| [internals/apple-private-apis.md](internals/apple-private-apis.md) | Vendored GSA/anisette crates + clearadi stub, feature-selected providers |
| [internals/open-absinthe.md](internals/open-absinthe.md) | On-device validation engine: module map, API, differential oracle |
| [changes.md](changes.md) | Change recipes by kind + invariant checklist — read before editing |

## Layer map

```text
app-native / desktopApp        Compose UI, services, Android Keystore impl
        │
      :core                     intake, repos, sync  (imports committed UniFFI Kotlin)
        │  JNA → librust_lib_bluebubbles.so
      rust/                     UniFFI facade: U* mirrors, boot, queue, keystore, engine
        │
      rustpush/                 Apple protocols (git submodule)
        ├── keystore/           abstract keystore traits + software/backup impls
        ├── open-absinthe/      on-device Apple validation circuit (source-built)
        └── apple-private-apis/ icloud-auth (GSA), omnisette (anisette), cloudkit-proto…
```

### The `rust/` crate (`rust_lib_bluebubbles`)

| File | Owns | Change rules |
|---|---|---|
| `src/lib.rs` | UniFFI scaffolding, the Tokio `RUNTIME` ([runtime](foundations/runtime.md)), logger init, `bbhwinfo` protobuf (OABS payload), debug NAC smoke-test `extern "C"` fns | Rarely touched; runtime sizing and logging policy live here |
| `src/native.rs` | Process boot (`start`, `init_native`), `NativePushState` object, the receive loop (`start_loop`), the durable queue (journal + pointer map, [incoming](messaging/incoming.md)), carrier lookup, legacy passwords/keychain callbacks, `MsgReceiver`/`KotlinFilePackager`/`HandleWifiNetworksCallback` foreign traits | Queue/receive-loop behavior and boot semantics only |
| `src/uniffi_ext.rs` | The bulk of the Kotlin-visible API: all `U*` mirrored types, sends, attachments, login (`ULoginSession`), CloudKit sync, vault/passwords, shared albums, Find My, FaceTime, posters/profiles, provisioning, SMS helpers | Any new Kotlin-visible type or method defaults to here |
| `src/api/api.rs` | The engine behind the facade: `SharedPushState`/`SharedICloudServices`, state restore, all `make_*` service constructors, `recv_wait` topic dispatch, `send`/SendConfirm, migration, login helpers, reset/teardown, CloudKit wrappers, FindMy/FaceTime/StatusKit wrappers, `GSAConfig` persistence | Restore/APS-watcher/journal internals and cross-service glue |
| `src/api/mirrors.rs` | Legacy FRB Dart mirrors of rustpush types | Do not extend; needed only so `frb_generated.rs` still compiles |
| `src/frb_generated*.rs` | Legacy FRB glue from the retired Dart client | Never hand-edit, never extend |
| `src/keystore.rs` | `NativeKeystore` foreign trait, `setup_keystore`, hardware/software selection, import key-wrapping (ASN.1 `KeyWrapper`), lock/unlock/recover | Only for keystore contract changes |
| `uniffi-bindgen.rs` + `build.rs` | UniFFI bindgen binary + scaffolding | — |

### The `rustpush/` submodule

| Module | Protocol |
|---|---|
| `src/auth.rs` | GSA delegate login, IDS cert provisioning (`authenticate_apple`/`_phone`/`_smsless`), `TokenProvider`, IDMS circle sessions (client + server), `IdmsAuthListener` |
| `src/aps.rs` | APNs socket (port 5223, rustls, packed/raw wire), `APSConnection` as a self-healing `ResourceManager`, topic `Filter`s, `send_message` + APNs-tunneled `SignedRequest`s |
| `src/ids/` | `IDSUser` registration (`register`), NGM identities + payload crypto (`pair-ec`), `IdentityManager`/`KeyCache` (per-handle delivery keys + send counters), the send pipeline (`SendJob`/`SendResult`/`MessageTarget`) |
| `src/imessage/` | `IMClient` (madrid orchestrator), message model (`messages.rs`), wire plists (`rawmessages.rs`, `include!`d), CloudKit message sync (`cloud_messages.rs`), name/photo sharing, contact posters (`posterkit.rs`) |
| `src/icloud/` | `CloudKitClient` (web CK), `mmcs.rs` attachment transport, `keychain.rs` (Octagon circles/escrow), `pcs.rs` (protected-cloud-service keys) |
| `src/findmy.rs`, `facetime.rs`, `statuskit.rs`, `passwords.rs`, `sharedstreams.rs` | Find My (devices/friends/items), FaceTime signaling, presence/status, iCloud Passwords, Shared Albums |
| `src/util.rs` | `ResourceManager`/`Resource` (the retry/backoff wrapper under APS + IDS), NSKeyedArchive coder helpers, `CompactECKey`, misc |
| `src/activation.rs`, `src/macos.rs`, `src/relay.rs` | Albert activation (push cert CSR + FairPlay), `MacOSConfig`/`HardwareConfig` + on-device validation data, hosted `RelayConfig` |
| `keystore/` | `Keystore` trait, `BackupKeystore` (hardware-backed + state file), `SoftwareKeystore` (desktop) |
| `cloudkit-proto/`, `cloudkit-derive/` | rustpush's own path crates: CloudKit protobuf definitions and the `CloudKitRecord` derive macro |
| `open-absinthe/` | Nested submodule: source-built Apple validation engine (see [open-absinthe](internals/open-absinthe.md)) |
| `apple-private-apis/` | Nested submodule: vendored `icloud-auth` (GSA/SRP), `omnisette` (anisette providers), `clearadi` stub (see [apple-private-apis](internals/apple-private-apis.md)) |

`rustpush` types never cross the FFI boundary directly (they cannot derive UniFFI traits).
Everything Kotlin sees is a `U*` record/enum from `uniffi_ext.rs`, or an opaque XML/plist
blob (attachments, MMCS files) that round-trips through persistence.

### The `U*` mirroring pattern

`uniffi_ext.rs` mirrors rustpush types field-by-field (`conv_*` functions in, `back_*`
functions out). Where a variant is rare or pre-MVP, the raw value is carried as
`serde_json` under a `*_json` field (or plist-XML under `*_xml` for transfer
descriptors) so nothing is lost while Kotlin stays typed. When you extend the API,
follow this pattern; do not invent a second convention.

### FRB legacy

`#[frb]` attributes, `frb_generated.rs`, and `api/mirrors.rs` exist only so the crate
still compiles during/after the Dart→Kotlin cutover. The Kotlin API is exclusively the
committed UniFFI bindings in `core/src/main/kotlin/uniffi/rust_lib_bluebubbles/`. Do
not add FRB exports; do not hand-edit the generated Kotlin.
