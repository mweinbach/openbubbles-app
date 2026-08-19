# State: what lives where

Part of the [Rust backend reference](../README.md). Related:
[lifecycle](lifecycle.md) (who builds this state), [keystore](../account/keystore.md)
(key storage), [changes.md](../changes.md) (durability rules for new writers).

## In-memory

`NativePushState` (UniFFI object) wraps `Arc<SharedPushState>` + the `APSWatcher`.
`SharedPushState` (`rust/src/api/api.rs`) holds:

| Field | Meaning |
|---|---|
| `os_config` | `JoinedOSConfig` — `MacOS` or `Relay`; the spoofed device identity |
| `conn` | `APSConnection` — the APNs `ResourceManager` |
| `anisette` | Anisette client (remote-v3 by default) |
| `client` | `Arc<IMClient>` — madrid messaging (IDS identity inside) |
| `ft_client`, `idms_client` | FaceTime, IDMS/2FA listeners |
| `icloud_services: Option<SharedICloudServices>` | Present only when an Apple account is signed in: `account` (AppleAccount), `token_provider`, and per-service clients — `cloudkit_client`, `keychain`, `passwords`, `profiles_client`, `fmfd` (Find My), `sharedstreams`, `cloud_messages_client`, `statuskit_client`. Several are `None` until iCloud Keychain (Octagon) is joined — that gates history sync, passwords, and Find My items |
| `local_broadcast` | mpsc sender that feeds local events (SendConfirm etc.) into the receive loop |
| `cancel_poll` | stops the loop (`stop_loop`) |
| `active_circle_sessions`, `client_session` | IDMS 2FA/circle approval state |
| `conf_dir` | the config directory below |

`NativePushState.get_state()` registers the state in a process registry and returns a
pointer id (used by the desktop daemon handoff; Android does not need it).

## On disk (the config dir passed to `start`/`initNative`)

Treat every file here as app-private. **Never commit any of them.**

| File | Contents | Writer | Secret |
|---|---|---|---|
| `hw_info.plist` | `SavedHardwareState`: APS push state, encrypted NGM identity, OS config | provisioning / `setup_push` (rewritten atomically whenever the push token rotates) | yes (identity) |
| `id.plist` | Registered `IDSUser`s (certs; private keys are keystore handles) | `register_ids` / IMClient `keys_updated` callback | yes |
| `id_cache.plist` | `KeyCache`: recipient delivery keys + NGM send counters | IdentityManager | yes |
| `gsa.plist` | `GSAConfig`: username, password encrypted under keystore key `gsa:password`, postdata flag | login | yes |
| `anisette_test/` | Anisette provisioning state (`state.plist`) | anisette provider | yes |
| `cloudkit.plist` | `CloudKitState` tokens | login/CloudKit | yes |
| `keychain.plist` | `KeychainClientState` (Octagon circle, cloud keys) | keychain sync | yes |
| `keychain_identity.plist` | Sidecar copy of the keychain peer identity so repairs re-adopt the *same* circle peer instead of registering ghosts | `make_keychain` | yes |
| `passwords.plist` | Passwords local cache | PasswordManager | yes |
| `findmy.plist` | `FindMyState` (encoded; includes keys) | FindMy client | yes |
| `facetime.plist`, `sharedstreams.plist`, `sync.plist`, `statuskit.plist` | FaceTime sessions/links, shared-stream tokens, sync-controller state, StatusKit key | respective clients | tokens, yes |
| `keystore.plist` / `keystore_s.plist` | Keystore state (hardware-backed vs software) | `setup_keystore` | **critical** |
| `messages.journal` | Durable incoming-message queue ([the journal](../messaging/incoming.md#the-durable-journal-messagesjournal)) | receive loop | contents are messages |
| `logs/rs_r*.log` | Rotating Rust log | logger | may contain payloads |
| `incident`, `incident_affected` | One-shot markers that the IDS key cache predates an incident and was rebuilt | `make_imclient` | — |

## Durability rules

Rules in `api.rs` that you must preserve when touching writers: state files are
written via `atomic_write_plist` (temp file + fsync + rename + parent fsync); a
state file that exists but fails to parse is *quarantined* to `<name>.corrupt-<ms>`
(`quarantine_corrupt_state`) — never silently regenerated, because regenerating
keystore/keychain secrets permanently orphans everything sealed by the old ones; the
login-time `migrate()` upgrades old formats (key material into keystore handles,
identity encoding, gsa password encryption) exactly once.
