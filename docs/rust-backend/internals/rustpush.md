# rustpush internals

Part of the [Rust backend reference](../README.md). Orientation for protocol changes;
everything here is internal to the submodule and **not** Kotlin-visible until you mirror
it (see [changes.md](../changes.md)).

- **`OSConfig`** (`src/lib.rs`) abstracts the spoofed device: `MacOSConfig`
  (`macos.rs`, `HardwareConfig` + version strings) and `RelayConfig` (`relay.rs`).
  Notable methods: `generate_validation_data()` (absinthe circuit vs relay call),
  `build_activation_info(csr)`, `get_gsa_config(push, require_mac)`, `get_private_data()`.
- **`ResourceManager<T>`/`Resource`** (`util.rs`): generate-with-backoff wrapper.
  `APSConnection` and the IDS `IdentityManager` are both ResourceManagers — call
  `ensure_ready`/`refresh_now`; subscribe `generated_signal` for reconnect events.
  Errors: `ResourceTimeout`/`ResourceGenTimeout`/`ResourceStalled`/`ResourceFailure`.
- **APS** (`aps.rs`): connect = optional Albert `activate()` → signed `Connect` →
  `ConnectResponse`; 60 s ping/pong keepalive; auto-ack notifications; topic
  `Filter`s are refcounted via `APSInterestToken` (drop = unsubscribe);
  `send_message(topic, data, id)` rides a Notification + Ack; `SignedRequest::send_apns`
  tunnels IDS HTTP over APNs. Wire is the packed format when ALPN negotiates it.
- **IDS** (`ids/`): `register()` posts per-user registrations (auth cert per user,
  NGM prekeys, capabilities via `IDSService.client_data` — `MADRID_SERVICE` is the
  iMessage capability set); `IdentityManager` re-registers on heartbeat/expiry and
  keeps `KeyCache` (delivery keys per service/handle, invalidated by
  `sha1(id_cert‖push_token)` change; per-peer NGM send counters; single-flight batched
  lookups, 18 handles/query). Sending (`send_message`) encrypts per-target
  (`pair-ec` NGM or legacy `pair`), bundles ≤5000 B, retries stragglers up to 5 rounds
  (`SendResult::Sent | APSError(code) | TimedOut`).
- **IMClient** (`imessage/aps_client.rs`): `handle(APSMessage)` → decrypt →
  `process_msg` dispatch (receipts, typing, errors incl. 802 key invalidation, SMS
  activation auto-reply, full `from_raw` decode). `send(MessageInst)` picks the
  sms-relay or madrid topic, `prepare_send`s the envelope, pre-fetches keys, and
  hands to `IdentityManager`; queued/scheduled messages first sync a copy to our own
  devices only.
- **Wire format** (`rawmessages.rs`): binary plists, gzipped unless the body contains
  XML (`x`/`ix`); the keys that surface in the `U*` projection are listed in
  [message-model](../messaging/message-model.md). New IDS command values must be
  reflected in the inbound raw parser list.
- **MMCS** (`icloud/mmcs.rs`): uploads/downloads are negotiated over madrid commands
  c=150/c=151 via APNs; files are chunked at 5 MB with per-chunk signatures; iMessage
  transfers wrap content in a streaming AES-256-CTR `IMessageContainer` (zero nonce,
  first key byte discarded); CloudKit assets use the v2 "FORD" path (HKDF-derived
  per-chunk keys + AES-256-SIV-protected key metadata that becomes the record's
  protection info). `MMCSFile::prepare_put` mints the key/size/signature.
  `MMCSMatcher` streams chunks source→target with progress callbacks.
- **CloudKit web API** (`icloud/cloudkit.rs`): `CloudKitClient` (dsid + token provider)
  → `CloudKitContainer::init` → `CloudKitOpenContainer` (session, zone-key cache).
  Operations are protobuf `RequestOperation`s (ULEB128-delimited, gzipped) against
  `gateway.icloud.com`: record save/fetch/delete/query, `FetchRecordChangesOperation`
  (the `/record/sync` engine under all `sync_*_page` exports — it loops until every
  zone's status is 3, threading the continuation token), assets, zones, shares,
  functions (Cuttlefish). Encrypted zones get a `PCSZoneConfig`; record crypto goes
  through `PCSEncryptor` (AES-128-GCM under HKDF-derived `PCSKey`s). 401 → mme token
  refresh; 429 → `TooManyRequests`.
- **Keychain / Octagon** (`icloud/keychain.rs`): `KeychainClient` over the cuttlefish
  container (`FunctionInvokeOperation`: fetchChanges / updateTrust / joinWithVoucher /
  establish / reset). `sync_keychain(zones)` pulls CKKS items — the zones include
  `Passwords`, `WiFi`, `CreditCards`, `Engram` (messages), `ProtectedCloudStorage`,
  `Photos`, and more; each item is AES-SIV-wrapped under keystore-backed access keys,
  so the state file never holds plaintext secrets. IDS keys do **not** live here; the
  P-384 peer identity is the circle identity, and `ProtectedCloudStorage` items are
  the trust root for every other dataclass's PCS service keys — which is why history
  sync, passwords, and Find My items all gate on the clique being joined.
- **PCS** (`icloud/pcs.rs`): service-key hierarchy (master key → per-service keys →
  per-record `PCSKey`s) and share protection; modules touch it only through
  `get_zone_encryption_config` / `prepare_put_v2` boundary keys.
- **The service-client shape**: every long-lived rustpush client follows one pattern —
  `state: DebugRwLock<State>` + an `update_state` persistence callback (the host
  writes it atomically), a held `APSInterestToken` for its topics, and a
  `handle(APSMessage)` dispatcher fed by the shared APNs connection. Copy this shape
  for any new push-driven service.
- **Errors** (`error.rs`): `PushError` groups as GSA/login, IDS, sending, APS,
  activation/anisette/relay, resource, crypto/encoding, circle/keychain/escrow,
  domain. Callers mostly string-render them (`UError.reason`).

Features (`rustpush/Cargo.toml`): `macos-validation-data` (default; source-builds
open-absinthe for on-device validation), `remote-anisette-v3` (**active** in `rust/`'s
dependency line — remote anisette provisioning), `remote-clearadi` (self-contained
emulated ADI, alternate provider). The two remote features are provider selections;
on real macOS `AOSKitAnisetteProvider` wins. `rust/` pins
`features = ["macos-validation-data", "remote-anisette-v3"]`.

## open-absinthe (on-device validation)

`rustpush/open-absinthe/` is a nested submodule: the source-recovered Apple
FairPlay/"absinthe" validation engine that produces the proof Apple accepts for the
spoofed Mac identity. Full details — module map, API, differential oracle, recovery
rules — are in [open-absinthe.md](open-absinthe.md).
