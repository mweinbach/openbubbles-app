# Unconfirmed audit — Apple services parity (iMessage · iCloud · Find My · Photos)

**Status: UNCONFIRMED.** This is a static-analysis review, not a defect list. Per
[AGENTS.md](../../AGENTS.md) and [docs/DEVELOPMENT.md](../DEVELOPMENT.md), a finding here is a
lead tied to the commits below — not proof of a current defect. Reproduce against current `HEAD`
before editing code. Host-testable items can be confirmed with unit tests alone; items marked
"device" additionally need live-account or hardware evidence before they count as proven.

| | |
|---|---|
| Audit date | 2026-08-21 |
| Root HEAD audited | `6841abab35ce` |
| rustpush submodule audited | `f0a830717b05` |
| Method | Four parallel read-only audits over `rustpush/src/{imessage,icloud,ids}`, `aps.rs`, `auth.rs`, `findmy.rs`, `photos.rs`, `sharedstreams.rs`, plus Kotlin consumers in `core/` and `app-native/`. Native oracle used as contract reference only (`imagent`, `cloudd`, `searchpartyd`/`fmd`, `bird`). |

## Finding status legend

- **✅** — re-verified present at the commits above on 2026-08-21 (line numbers exact).
- **✔️** — already addressed by rustpush `07c55ef` ("Harden Apple service protocol boundaries")
  during this review; kept for context.
- Unmarked — reported by the audit pass, not individually re-verified; line numbers are
  approximate and may have drifted.

Severity assumes the threat model "malformed/hostile payload from Apple infrastructure or a
peer". Nothing in this document should be read as authorization to run remote writes; any
live-account confirmation follows the write boundary in
[openbubbles-apple-native-parity](../../.agents/skills/openbubbles-apple-native-parity/SKILL.md).

---

## Cross-cutting themes

### 1. Remote-triggered panics (dominant pattern)

Every service treats server/peer-controlled bytes as trusted somewhere, while the native daemons
isolate per-record failures and continue. Verified sites at `f0a83071`:

| Site | Trigger | Blast radius |
|---|---|---|
| ✅ `icloud/cloudkit.rs:26` `read_exact(...).expect("Failed to unlimit response")` inside `undelimit_response`; also trusts a server uleb128 length for an unbounded allocation | Non-protobuf gateway body (401 HTML page, 500 JSON) or truncated response | Every CloudKit consumer: Messages-in-iCloud, Photos, Find My, passwords |
| ✅ `icloud/pcs.rs:387` `panic!("Unimplemented encryption version ...")` in `get_ciphertext_key`; unchecked slicing of server-supplied key ids | Future PCS version byte (ADP-relevant) or short protection blob | All protected records |
| ✅ `auth.rs:1325,1364-1365` `CircleServerSession` `.expect(...)` chain on DER decode/SRP step | Out-of-order, truncated, or hostile circle PAKE step relayed via `com.apple.idmsauth` | Login / 2FA-assist path. The client-side `CircleClientSession` was already hardened to typed errors; the server-side session was missed |
| ✅ `imessage/messages.rs:2840` `data[1..]` on incoming MMS `eK`; `:2925` `guididx ... .unwrap()` on `tg:` replies; `:593` effect-type `.expect(...)` | Malformed incoming push fields | Receive loop stalls until restart |
| ✅ `ids/identity_manager.rs:285,373` `panic!("No handle cache ...")` reachable from `c=130` peer-cache invalidation with an unknown target handle | Peer-controlled envelope field | Send/receive path |
| ✅ `ids/user.rs:984` `query.chunks(query.len() / 2)` → `chunks(0)` panic when retrying a one-handle lookup after IDS 5206 | Transient server condition | Send path |
| ✅ `findmy.rs` — 51 `unwrap()`/`expect()` sites, e.g. `key_map.remove(&payload.id).expect("Not found in key map!")`, AES-GCM decrypt unwrap on report blobs, `pairing_date.unwrap()` | Stale server cache, rotated keys, short/truncated reports | Items screen dead until process restart |

Improvement direction: convert these to typed `PushError`s and add crafted-input unit tests per
site (the `07c55ef` test style is the template). Native behavior to mirror: drop the bad record,
log a content-free diagnostic, keep processing.

### 2. Log hygiene

The `logging_policy_tests` allowlist (`rustpush/src/lib.rs:121-131`) covers messages, aps_client,
identity_manager, cloudkit, mmcs, passwords — but **not** `aps.rs` or `ids/user.rs`, which is
exactly where leaks live:

- ✅ `aps.rs:1544,1549` hex-dump entire outgoing APS frames at debug level (session tokens,
  push tokens, handles, receipts). `:1549` contains the long-standing typo `Sendin2g`.
- ✅ `ids/user.rs:807` logs the full alias-provisioning response body at info level.
- `findmy.rs:894` logs beacon records that derive `Debug` including private key material;
  `:1678/:1681` log FMF import tokens; `:1635` dumps raw share plists; `:1327/:1349` log raw
  coordinates.
- `icloud/mmcs.rs:1300-1304` hex-dumps decrypted FORD plaintext (chunk-key material) into an
  error string on one failure path.
- `keychain.rs:1352` uses `println!` for an SRP step body; `sharedstreams.rs:814` likewise.

Improvement direction: extend the policy-test `SOURCES` list to `aps.rs`, `ids/user.rs`,
`findmy.rs`, `pcs.rs`, `auth.rs`, `keychain.rs`; remove or redact the listed statements.

### 3. Clock-step panics

`duration_since(UNIX_EPOCH).unwrap()` / `.expect("Time went backwards")` in hot paths:
`auth.rs:198` (MMe token staleness), `ids/identity_manager.rs:40-44` (key-cache validity),
`util.rs:562-566` (epoch helper), `findmy.rs:477`. An NTP step backwards — common on Android —
panics during routine operations. Ordering also mixes clocks: outgoing timestamps use device wall
clock (`messages.rs:2034` area) while incoming use Apple's `e` nanos field.

### 4. Transport resilience

No replay-on-401, no backoff/Retry-After handling, no resumable MMCS transfers, no signed-URL
expiry checks anywhere in the shared stack (details under iCloud). Affects every consumer under
flaky networks and long transfers.

---

## iMessage (`rustpush/src/imessage/`, `aps.rs`, `ids/`)

### High

- **H1 — Panic cluster on malformed incoming payloads.** See cross-cutting table above for the
  verified sites. Additional reported sites (approximate lines): `messages.rs:383-391`
  (SMS/MMS part-list lookups), `:650`, `:736` (gzip balloon blobs),
  `name_photo_sharing.rs:234` + `util.rs:510-512` (base64 record-key decode),
  `cloud_messages.rs:230-236` (ungzip expect), `messages.rs:2650,2857` (pre-1970 plist dates).
  ✔️ One site already fixed: `aps_client.rs` incoming-UUID `try_into().unwrap()` is now a typed
  `PushError::BadMsg` with a regression test (`07c55ef`). Proof: host tests feeding crafted
  `plist::Value` shapes into `MessageInst::from_raw` / `IDSRecvMessage::to_message`.
- **✅ H2 — APS layer logs tokens; policy test exempted it.** See cross-cutting theme 2.
  Proof: extend `SOURCES`, assert no payload-rendering fragments; device check via `adb logcat`.
- **H3 — NGM pair-ec send counter persisted apart from its key; receivers never validate
  counters.** Sender counter lives in the plaintext `KeyCache` binary plist
  (`ids/user.rs:533-544`) while prekeys live encrypted in Keystore; cache deletion resets
  counters under a still-published prekey. Receiver logs and ignores the counter
  (`user.rs:520`); no replay/window enforcement in `decrypt_payload`. Native tracks NGM counters
  for replay ordering. Needs a recorded protocol decision before changing receiver behavior.
  Proof: host crypto test for keystream reuse; device evidence for peer behavior.
- **✅ H4 — `APSConnectionResource::send_message` waits for *any* Ack, not *its* Ack**
  (`aps.rs:1486-1489`, `for_id` deliberately ignored). Concurrent operations can consume each
  other's acks; every other waiter matches precisely. Proof: host test against the existing mock
  TLS APS harness firing two concurrent sends with swapped acks.

### Medium

| ID | Finding | Site (approx.) | Why it matters vs native | Proof |
|---|---|---|---|---|
| M1 | Any 15 s wait timeout force-reconnects the shared APS socket (`do_reload`), and the auto-ack task ACKs notifications before handlers run | `aps.rs:1501-1512`, `:1352-1363` | apsd keeps the socket and retries the op; early-ack weakens APNs redelivery to at-most-once on crash | Device (reconnect-storm capture); host for reload trigger |
| M2 | Keep-receipts advertised (`supports-keep-receipts=true`) but command 110 never handled | `aps_client.rs:38`, no branch in `process_msg`/`from_raw` | Senders never learn a voice memo was "Kept" | Device capture of inbound `c=110`, then host parse test |
| M3 | MiC sync ignores zones carrying unsend tombstones and scheduled messages; `MessageFlags` uses `from_bits_truncate` so unknown future bits are stripped on re-upload | `cloud_messages.rs:654-697,839-849,363-374` | Offline unsends diverge permanently; scheduled messages never arrive; flag loss mirrors the fixed `backgroundProperties` bug | Device two-device matrix; host round-trip flag test |
| M4 | Delivered receipts requested only in 1:1 chats, and oddly *only* for reactions in groups | `messages.rs:1784-1791` | Modern iOS shows Delivered in group iMessage too; asymmetric React rule has no known native analogue | Device only |
| M5 | Unbounded gunzip on network-supplied blobs (balloons, `ati`, typing icons); PosterKit already has the right limits to reuse | `util.rs:555-560`, callers `messages.rs:647,723,736,2605` | Gzip-bomb DoS from any peer | Host limit tests mirroring `posterkit.rs` |
| M6 | Outgoing XML builder disables escaping, hand-escapes text only — attribute values (attachment name, mention uri) written raw | `messages.rs:202-204,216-227,284-297` | A filename containing `"`/`=` forges attributes in outgoing payloads; Apple clients mis-parse | Host test with hostile filename |
| M7 | Edit history exists only in the MiC summary path; live edits replace in place; `ec/ep/otr` unmodeled; no client-side gate on the 15-min edit/unsend windows | `rawmessages.rs:153-164`, `cloud_messages.rs:146-168`, `messages.rs:2455-2475` | "Show Edits" parity lost for live edits; unsend/edit of old messages attempted anyway | Inspect Kotlin accumulation; device capture of multi-edit sequence |
| M8 | Shared-profile update deletes the old public record before uploading the new one | `name_photo_sharing.rs:397-402` | Failure between delete and save leaves no shared name/photo | Host fault-injection |
| M9 | `KeyCache::invalidate` panics on unknown handle — reachable from network input | `identity_manager.rs:285,373` | Compounds H1 | Host test with foreign `tP` |
| M10 | IDS query split `chunks(query.len()/2)` panics on single-URI 5206 retry | `user.rs:984` | Kills send path on transient condition | Host test |
| M11 | Clock-skew handling inconsistent; panics in hot paths; mixed time bases for ordering | see theme 3 | NTP steps crash; bubbles sort wrongly under skew | Host mocked-clock test; device sort check |
| M12 | Legacy-payload decryption proceeds without sender verification when key lookup fails (`verification_failed` set but decryption side effects still run) | `identity_manager.rs:822-835`, `user.rs:259-267,485-508` | Native discards unverifiable payloads *before* decryption | Host test; audit Kotlin consumers of `verification_failed` |
| M13 | IDS key cache stores contact session/push tokens as plaintext binary plist | `identity_manager.rs:188-196` | Native keeps equivalents in keychain; file rides in backups unless excluded | Confirm backup exclusion config |
| M14 | Reconnect flush-cache probe fires `c=160` whenever no `NoStorage` arrives within 500 ms — i.e., on virtually every reconnect | `aps_client.rs:165-185` | Plausible duplicate-notification source, amplified by M1 reconnects | Device capture, duplicate-GUID counts |

### Low / code quality

Giant twin decode/encode functions (`from_raw` ≈390 ln, `to_raw` ≈420 ln) requiring synchronized
edits across six places per new message type — consider a per-type table;
inverted `is_writer` naming (`IMessageContainer::new(..., is_writer=true)` selects *decrypt*);
per-reaction regex recompilation (`messages.rs:2739`); unreachable-arm `panic!("no")`;
GUID casing inconsistency; MMCS domain derivation by string replace-all (`messages.rs:1441`);
hardcoded MMS attachment size 0 (`:2809`); `report_spam` hardcodes `message_length: 5`;
info-level noise and stray `debug!("a"/"b"/...)` breadcrumbs; `test.rs:433 // TODO DO NOT COMMIT`
marker in a feature-gated manual harness; `register()` parses server responses with ~20 unwraps
while holding the users write-lock across network I/O; `add_prefix`/`remove_prefix` email
heuristics misroute exotic handles; capability advertisement exceeds implementation
(`supports-polls`, `supports-gti`, etc. advertised with no payload types — peers may send them
and get silent drops).

### Already solid (do not churn)

PosterKit hardening (decompression bombs, traversal, image probes — bounded and tested);
MMCS transfer-metadata validation suite; send-state honesty (own token dropped from targets,
`NoValidTargets` surfaced); certified delivery both directions incl. BadMsg paths; KeyCache
atomic persistence with counter-preserving recovery; group-admin payload fidelity
(`gv="8"`, `pv`, `ngp`); read-receipt fan-out to own handles; content-free `kind()` diagnostics;
MiC background-properties preservation with tests; defensive pagination in
`query_messages_of_type`.

---

## iCloud transport (`rustpush/src/icloud/`, `auth.rs`, `activation.rs`)

### High

- **H1 — Non-protobuf/empty gateway response panics the stack.** Second half ✔️ fixed:
  missing operation responses/results now return `PushError::BadMsg` via `operation_response`
  with tests (`07c55ef`). First half ✅ still present: `undelimit_response`
  (`icloud/cloudkit.rs:26`) expects `read_exact` and trusts a server uleb128 length for an
  unbounded allocation. Proof: host unit test feeding HTML/JSON/truncated bodies through the
  parse path.
- **H2 — 401 refreshes the token but never replays the request** (`icloud/cloudkit.rs:2163-2165`
  in `perform_operations`; same shape in `CloudKitContainer::init` ~`:1447-1451`). Execution
  falls through to parse the failed body. 429 maps to `TooManyRequests` but Retry-After/throttle
  hints are dropped; no backoff policy exists in this layer. Native `ckd` re-authenticates and
  replays once. Proof: host mocked 401→200 sequence asserting single replay; device for real
  throttle behavior.
- **H3 — `CircleServerSession` panics on malformed PAKE payloads** (`auth.rs:1325`,
  `:1364-1365` ✅, plus step-5/channel unwraps ~`:1406-1437`, `decrypt_from_server`
  `.expect("Circle Decrypt Failed")` ~`:973`). Reachable from relayed circle messages.
  The client-side session is the hardened template to port. Proof: host malformed-step tests;
  device 2FA-assist flow afterwards.
- **✅ H4 — PCS ciphertext parsing panics on short/malformed buffers and unsupported versions**
  (`icloud/pcs.rs:387` version panic; unchecked header/tag slicing ~`:537-548`; mismatched-key-id
  branch also logs ciphertext bytes). Directly ADP-relevant. Proof: host tests over
  `get_ciphertext_key`/`PCSKey::decrypt` with empty/truncated/version-4 inputs.
- **H5 — Escrow SRP parsing does unchecked arithmetic on server bytes** (`keychain.rs:542-553`
  `msg_from_bin`; call sites ~`:2461-2506` incl. `verifier.verify_server(...).unwrap()`).
  A failed server-proof should be a typed auth error, not a crash. Proof: host crafted-blob
  tests; device recovery flow afterwards.
- **✅ H6 — `pcs_keys_for_record` slices a server-controlled key id**
  (`icloud/cloudkit.rs:159-160`): oversized `record.pcs_key` panics on
  `&id[..pcskey.len()]`; missing key panics instead of returning the existing
  `PCSRecordKeyMissing`. Hot path for every encrypted record fetch. Proof: host test with
  oversized/empty `pcs_key`.

### Medium

| ID | Finding | Site (approx.) | Why it matters vs native | Proof |
|---|---|---|---|---|
| M1 | Zone encryption-key cache keyed by zone *name* only, ignoring owner/environment | `cloudkit.rs:1557,1579,1689` | Same-named shared zones from different owners collide → wrong PCS keys | Host two-owner test; live multi-share account |
| M2 | Incremental-sync loops lack termination guards; single zone error aborts whole batch; whole pass buffered in memory; `max_changes` left None | `cloudkit.rs:806-828,865-877,797` | Server echoing a continuation spins forever; first sync of a large zone buffers unboundedly | Host mock non-advancing tokens; live long-poll fidelity |
| M3 | MMe refresh: clock-step panic and double-refresh race (no single-flight) | `auth.rs:198-199` | Concurrent stale checks perform duplicate GSA PET logins | Host concurrency + backwards-clock test |
| M4 | `login_apple_delegates` panics instead of typed errors on logged-out/malformed state | `auth.rs:369-370,417,427` | Refresh path for every CloudKit/MMCS flow crashes instead of surfacing UnauthorizedAccountError | Host malformed-plist tests; device logout-while-syncing |
| M5 | Keychain/circle peer messages can panic or wedge state (peer add without hash, permanent-info expects, IESCiphertext short-cipher underflow, bottle unwraps, non-terminating `sync_changes` loop) | `keychain.rs:1548,799-802,456-458,1869-1996,1347-1381` | One malicious/buggy peer device could crash the client repeatedly | Host crafted-peer tests; live two-device circle |
| M6 | `PCSShareProtection::decode` panics on peer protection info (decode key, self-sig, HMAC, truncated key id asserts) | `pcs.rs:1179-1237` | Bypasses the existing `RemovedFromShare` typing one level up | Host wrong-key/bad-HMAC decode tests |
| M7 | FORD metadata failure path hex-dumps decrypted chunk-key bytes into the error string | `mmcs.rs:1300-1304` | Violates the repo's no-key-material-in-errors rule | Host assertion on error contents |
| M8 | MMCS: no retry/backoff, no signed-URL expiry handling (`download_token_expiration` stored but never consulted), no Range/resume, no output-size verification for v1/plain chunks | `mmcs.rs:583-667,1368-1568` | Large originals fail wholesale on expired URLs/transient 5xx | Host mock 403→re-authorize; device large-transfer |
| M9 | Duplicate-chunk catch-up in `write_chunk` re-caches the wrong pair | `mmcs.rs:980-993` | Upload-data corruption class on repeated chunk ids | Host crafted duplicate-chunk stream |
| M10 | State write locks held across network calls in KeychainClient | `keychain.rs:1404-1442,1600-1667,2053-2065` | Serializes all keychain ops; future nested acquisition deadlocks | Host concurrency test |
| M11 | `PCSPrivateKey::from_dict` verification logic inverted: definitive bad signature panics, verification *error* proceeds with unverified key | `pcs.rs:321-329` | Silently using unverifiable key material undermines the trust check | Host both branches |
| M12 | Change-fetch requests omit `max_changes` and always send flow-control budget 0 | `cloudkit.rs:796-797,2084-2087` | Fidelity/throttling liability vs native budget accounting | Live comparison; host encoding test |

### Low

Panics on locally-generated-but-user-influenced inputs (`cloudkit.rs:55,73,392,592,1740-1741,
2026,2339`; `keychain.rs:1892,2242`; `auth.rs:1498,818-828`); anisette header-conversion unwraps
(`cloudkit.rs:1397`, `auth.rs:397`, `keychain.rs:2313`); `println!` production logging
(`keychain.rs:1352,1676,1691`; `sharedstreams.rs:814`); blocking std file reads up to 5 MB inside
async (`mmcs.rs:486`); per-call `CloudKitSession` so related ops don't share an op-group
(`cloudkit.rs:1236-1243`). `activation.rs` reviewed solid — pinned cert set with random
selection, typed parse errors; no changes needed.

### Already solid (do not churn)

CKDPAsset clearAssetKey (field 20) handling and upload inverse, host-tested; FORD 16/32-byte
scheme with HKDF/CmacSiv roundtrips; chunk identifiers 0x01/0x02 with conflicting-key detection
and no-leak assertions; v2 chunk HMAC integrity; PCS AAD rules both ways (full tag vs
field-name-only); RFC 3394 wrap correctness; keychain warn-and-skip on malformed records;
Cuttlefish v1/v2 AAD/SIV typed errors; TLK-share dual-encoding compatibility; escrow graceful
degradation; FullResetNeeded handling; client-side circle session error style; change-tag
persistence only after full pagination; Photos decrypt-stage logging discipline; error-taxonomy
foundation carrying raw CloudKit results.

---

## Find My (`rustpush/src/findmy.rs`, UniFFI surface, Kotlin consumers)

### Priority findings

1. **✅ Epoch-unit mismatch at the UniFFI boundary — friend/device "last seen" wrong everywhere.**
   Rust documents and sends Apple-epoch ms (`rust/src/uniffi_ext.rs:4571-4572`,
   `findmy.rs:1751-1752`); Kotlin's normalizer
   (`app-native/.../ui/findmy/FindMyModels.kt:138-149`) passes values ≥ 1e11 through unchanged as
   Unix ms — today's Apple-epoch-ms values (~7.9e11) sail through, so fresh locations render as
   mid-1990s dates and everything shows the absolute-stale branch. The sub-threshold branch never
   adds the required +978307200000 offset either. `asEpochMs` is untested.
   Proof: host test pinning one known Apple-epoch-ms input (fails today); one live capture to
   confirm server units before fixing.
2. **✅ Process-global `FMI_PHONE` cache survives account replacement**
   (`rust/src/uniffi_ext.rs:4823-4865`): rebuild gated only on config-dir equality;
   `reset_state`/`reset_icloud_services` never clear it. Logout/login into the same dir serves
   the previous account's dsid, tokens, and cached device list from memory — violates
   [DATA_LIFECYCLE.md](../DATA_LIFECYCLE.md) account-switch rules. The `fmfd` client is rebuilt
   correctly; only the phone client lags. Proof: extract rebuild predicate, host-test
   generation-change rebuild; device end-to-end switch.
3. **Panics on malformed/server-controlled data throughout parsing** — see cross-cutting table
   (51 sites). Worst: fetch-response id lookup (`findmy.rs:1276`), AES-GCM unwrap (`:1304`),
   length-heuristic slicing (`:1283-1286`), Option-date unwraps (`:750,:731`), sentinel-owner
   expect (`:1439`), literal `panic!()`s in `accept_item_share` (`:1069,:1072`) and JSON
   handling (`:1983-1984`). Proof: host tests feeding truncated/garbage reports and records.
4. **✅ Secondary-ratchet backward seek roots from the wrong secret**
   (`findmy.rs:766`; seeding at `:682`): backward seeks reseed from the primary
   `shared_secret` instead of `shared_secret_2`/`secure_locations_shared_secret`, so
   secure-location reports silently stop decrypting until forward convergence. One-line fix +
   regression test distinguishing the two roots.
5. **Pull-only refresh model**: only IDS alloy topics are registered (`findmy.rs:813`); FMIP
   data-sync pushes (`push:true` + apsToken in clientContext, `:1843-1856`) fall through —
   nothing triggers `refreshClient`. Native refreshes on push with cooldowns. Also
   `select_background_friend` exists but isn't UniFFI-exported, and incoming `BeaconShared`
   pushes have no Kotlin consumer while `acceptBeaconShare`/`deleteBeaconShare`/
   `updateBeaconName` have zero callers — pending invites are unactionable.
   Proof: device idle-push observation; host surface check.
6. **Sensitive material in logs** — beacon private keys, import tokens, raw share plists, raw
   coordinates (see theme 2). Proof: host log-capture assertions.
7. **Protocol coverage gaps vs native**: play sound, lost mode, erase, notify-when-found don't
   exist (`uniffi_ext.rs:4542` admits it); owner-side sharing flows absent; item `status` byte
   passed through raw (charging/low-battery bits undecoded). Feature work, each action needing
   device evidence.
8. **searchparty requests ignore HTTP status** (`findmy.rs:1104-1137`): empty error bodies
   return `Default::default()` (looks like "no items"); expired `searchPartyToken` masquerades as
   an empty result; no 401 handling unlike the FMF path (`:1989-1991` detects but still parses
   the failed body without retry). Proof: host mocked-transport typed-error tests.
9. **Long-held async locks across network I/O**: `sync_items` holds the state mutex across delta
   sync and nested HTTP (`:848-858,:973`); `with_fmi_phone` holds the global mutex across
   constructor network I/O — UI cache reads block behind them. Proof: timing test with delayed
   transport.
10. **`delete_shared_item` partial-failure inconsistency** (`:1389-1419`): leave-message sent and
    CloudKit deletes queued before the local-circle lookup, whose miss discards the queued ops —
    peers told you left, record orphaned, local state untouched. Owner-initiated removal triggers
    a harmless-but-nonnative reply-leave. Proof: host ordering test; device removal path.
11. **Hardcoded identity/locale spoofing in fmfd clientContext** (`:1927-1949`): a leftover
    personal email sent as `signedInAs` on every friends request, fixed `"countryCode": "CA"` /
    `"timezone": "EST, -18000"`, randomized `processId`. Privacy/fingerprint divergence.
    Proof: device request-body capture.
12. **Stale-location/accuracy semantics pass-through only**: `is_old`/`is_inaccurate` ignored by
    UI; no recency floor on newest-report selection; no reverse geocoding for items;
    `horizontal_accuracy` saturates at 255 with no ">255 m" hint. UX fidelity.
13. **Assorted robustness**: pre-1970 date panic (`:477`); unchecked key-length slicing in
    `derive_ps_key` (`:701-711`); unbounded ratchet seek toward corrupt indices (`:601-618`);
    rotated `shared_secret` leaves stale seeded ratchets (`:896-898`); anisette header-name
    unwraps (`:1106-1107,:193`); Kotlin battery `-1` sentinel coerced to "0%"
    (`FindMyModels.kt:200`).

### Already solid (do not churn)

Beacon cryptography matches OpenHaystack/native wire format (15-min primary slots with
720-slot lookback + 12 h lookahead, 04:00-daily secondary rotation, P-224 PS-key
diversification, ECDH→SHA-256→AES-128-GCM report decryption, Apple-epoch u32 BE timestamps,
×10⁷ lat/long scaling); BeaconStore protected saves with protection-info tags; `should_reset`
token wipe; `FindMyState` AES-256-GCM at rest with keystore key, atomic writes, legacy-plaintext
migration, deletion on reset; IDS plumbing (app-ack replies, correlation gating, topic-filtered
dispatch contract, interest tokens); Kotlin seam (`FindMyPort` + fake, typed adapter, parallel
refresh with offline retention, `runInterruptible` around JNI).

---

## Photos (`rustpush/src/photos.rs`, `sharedstreams.rs`, Kotlin photos stack)

Boundary doc: [PHOTOS_SYNC.md](../PHOTOS_SYNC.md). All recommendations stay inside the documented
write boundary; the sole permitted mutation remains the explicit JPEG upload path.

### High

- **✅ H1 — Upload retry strands CPLMasters.** Master id derives from a random per-upload FORD
  key (`photos.rs:506` ← `mmcs.rs:136 rand::random()`); only the CPLAsset UUID is
  content-deterministic. A retry after the previous attempt reached the CPL save (crash between
  commit and receipt persist) uploads fresh MMCS bytes and saves a second CPLMaster; the asset
  repoints and the old master strands invisibly against quota. Within-app duplicate rows are
  already prevented client-side (`PhotoTransferCoordinator.kt:246-250`), but that doesn't cover
  the crash window or cross-source duplicates. Improvement: fingerprint-existence probe before
  save, or persist/reuse the prepared put keyed by staged SHA-256. Proof: live disposable-asset
  probe (force-fail post-MMCS, retry, inspect for orphan); host tests for identifier derivation.
- **H2 — Pagination can permanently drop assets.** The 2×limit window can cut between a
  CPLAsset and its CPLMaster (`photos.rs:877,949-957,980-983`): the orphaned half is
  `continue`-dropped after the cursor advanced, and a short page is indistinguishable from
  end-of-library. With no change fetch (H3) such drops are permanent until full refresh.
  Improvement: carry unmatched pairs into the next parse; signal short pages distinctly.
  Proof: host synthetic fixtures; live evidence whether real pages split pairs.
- **H3 — No change token retained anywhere; rank-based cursors drift.** Nothing stores
  `mostRecentChangedAssetComment`/change tags; every refresh re-queries rank 0. Building
  incremental fetch needs (a) opaque token persistence in Rust, (b) a CPLAssetChanges-style
  raw-record operation with tombstone decoding, (c) idempotent upsert/tombstone application in
  `PhotosCatalog` (schema v1 has no tombstone representation). Roadmap-critical readiness item,
  documented non-goal today. Proof: live delta observation; host token/tombstone tests.

### Medium

| ID | Finding | Site | Why it matters vs native | Proof |
|---|---|---|---|---|
| M1 | Video duration never decoded although the documented summary contract includes it | `photos.rs:78-92,879-894` | Videos show no length; Live Photo timing underivable later | Host fixture extension; rides with pending small-video device proof |
| M2 | Non-`public.*` UTIs (RAW/vendor formats) classify as `Unknown`, blocking previews even when `resJPEGThumbRes` exists | `photos.rs:1068-1076` (+ `preview_field` `:824`) | RAW masters show failed cells where native shows JPEG thumbnails | Host fixture; one live RAW-library check |
| M3 | Naive EXIF timestamps interpreted as UTC; uploads hardcode `timeZoneNameEnc="UTC"` / offset 0 | `PhotoUploadPicker.kt:229-259`, `photos.rs:40,232,574` | Assets land in the wrong day/position in Photos.app — most user-visible metadata defect in the otherwise byte-perfect upload path | Device disposable JPEG with known non-UTC naive timestamp |
| M4 | ✔️ Malformed CloudKit responses panicked in `perform_operations` | was `cloudkit.rs:2179-2180` | Fixed by `operation_response` typed errors + tests in `07c55ef` | Done (host-covered) |
| M5 | Shared Albums startup watcher `expect` can abort construction of the whole iCloud service graph, including the personal Photos client | `sharedstreams.rs:693` ← `api.rs:940,849-874` | Rare trigger, wide blast radius | Host failing-watcher factory injection |

### Low

Boolean CPL fields type-sensitive (`photos.rs:1043-1047`); `live_photo` detection by bare field
presence (`:967`); unknown `CheckIndexingState` values collapse into `Indexing` (`:344-348`);
per-chunk info-level byte-count logging (`mmcs.rs:1063`); no resume for interrupted transfers
(coordinator deletes partials; MMCS chunk offsets would make chunk-level resume feasible);
`pcs.rs:967` DER-encode expect. Shared Albums reference-code risks verified still present:
local-deletion propagates remote deletes on rescan (`sharedstreams.rs:995-997`), single
principal rendition per asset (`:1024`), swallowed `delete_asset` failures (`:539-544`),
assorted unwraps and full-plist response logging.

### Already solid (do not churn)

Query discipline (metadata-only `NO_ASSETS`, page cap, field allowlist, cursor range-check);
soft-deleted handling both record types with tests; filename safety chain (Rust sanitizer +
SHA-256-derived Kotlin destinations — traversal impossible); streamed downloads with per-chunk
progress, magic-byte validation, fsync + atomic promote, cancellation; upload crypto/schema
hygiene all pinned by unit tests (FORD 16-byte, wire 0x03, field-20 clear key memory-only,
RFC 3394 wrapping, field-name-only AAD, owning masterRef, acronym-exact casing, lowercase-`d`
`importGroupId`); deterministic CPLAsset anchor correct at asset level; sanitized error
redaction whitelist; exactly one mutation export; background worker compile-time disabled with
unit-tested invariant.

---

## Suggested improvement tiers

**Tier 1 — host-testable hardening, no protocol decisions needed**

1. Panic→typed-error sweep starting with `undelimit_response` (`cloudkit.rs:26`), then findmy
   decode paths, remaining iMessage sites, PCS/auth/keychain — each with crafted-input tests
   (`07c55ef` style).
2. Logging-policy extension to `aps.rs`/`ids/user.rs`/`findmy.rs` + remove/redact the listed
   statements (including the `Sendin2g` typo line).
3. Find My epoch fix + regression test; `FMI_PHONE` account-generation key; secondary-ratchet
   root fix.
4. Ack correlation (`for_id`) + timeout-vs-reconnect policy in `aps.rs`.
5. Clock-step tolerance in timestamp helpers.

**Tier 2 — needs device evidence or a recorded protocol decision first**

401 single-replay/backoff semantics; keep-receipts (`c=110`) and MiC tombstone/scheduled-zone
capture; Find My push-triggered refresh + invite UI wiring; Photos pagination join-state;
upload orphan prevention (live disposable-asset probe); MMCS resume/expiry design.

**Tier 3 — feature parity roadmap**

Find My actions (play sound/lost mode/erase) and owner-side sharing; Photos incremental change
tokens → durable mirror; Live Photo pairs; video originals; HEIC/video upload.

## How to confirm findings

- Host tier: `cargo test --manifest-path rustpush/Cargo.toml --lib --locked` from the repo root;
  add per-site crafted-input tests first (they double as regression coverage for the fixes).
- UniFFI-visible changes (e.g., epoch fix, `select_background_friend` export):
  `(cd rust && ./build-uniffi.sh)` then the Gradle union per
  [VERIFY.md](../VERIFY.md).
- Device tier: follow the evidence rules in
  [openbubbles-apple-native-parity](../../.agents/skills/openbubbles-apple-native-parity/SKILL.md)
  — one bounded transition, disposable assets for any write, redacted captures, stage markers
  recorded separately from host gates.
