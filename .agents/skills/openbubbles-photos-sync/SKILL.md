---
name: openbubbles-photos-sync
description: Extend or diagnose personal iCloud Photos metadata, protected preview/resource transfers, explicit JPEG upload, the separate Photos catalog, or live-device proof. Use for the experimental personal Photos path; not Shared Albums or ordinary message attachments.
---

# OpenBubbles Personal Photos

Read [../../../docs/PHOTOS_SYNC.md](../../../docs/PHOTOS_SYNC.md),
[../../../docs/DEVELOPMENT.md](../../../docs/DEVELOPMENT.md), and the CloudKit/PCS sections of
[../../../docs/RUST_KOTLIN.md](../../../docs/RUST_KOTLIN.md) before editing. Preserve the exact
scope: this is an experimental personal iCloud Photos browser with controlled foreground
transfers, not Shared Albums and not full bidirectional sync. Writes remain explicit JPEG uploads;
the dormant background worker does not authorize background mirroring or any other mutation.

## Follow the ownership boundaries

- `rustpush/src/photos.rs` owns the personal Photos container, CPL record interpretation,
  bounded metadata query, resource selection, and protected-asset handling.
- `rustpush/src/icloud/cloudkit.rs`, `pcs.rs`, and `mmcs.rs` own CloudKit authorization, PCS key
  resolution/unwrapping, and encrypted MMCS transfer. Changes here affect other Apple services;
  keep them generic and add focused regression coverage.
- `rust/src/uniffi_ext.rs` owns the Kotlin-visible async facade, staged file handle, media-header
  verification, and sanitized error mapping. Raw records, signed URLs, tokens, and key material
  must remain in Rust.
- `core/src/main/kotlin/app/openbubbles/core/photos/` owns the Android-free port, pager, transfer
  coordinator, deterministic cache paths, retry policy, and catalog contract.
- `app-native/.../data/photos/` owns the separate WAL SQLite catalog. `app-native/.../ui/photos/`
  and the Settings wiring own the Android screen and lifecycle.

Do not add Photos entities to `db/objectbox-model.json`, move the message store, expose Android
types from `core/`, or add a Flutter Rust Bridge API.

## Preserve the current safe flow

The metadata query is bounded and uses `NO_ASSETS`; listing must never download the library. A
composed lazy-grid cell may fetch one small rendition, write it to an app-owned `.part` file,
verify that the decrypted bytes match the expected JPEG or ISO-BMFF header, then atomically
promote the file. Only explicit selection may fetch an original, using a separate transfer ID and
cache directory.
Both Rust and Kotlin reject mismatched bytes. A corrupt completed cache entry must be downloaded
again rather than reported as successful.

The separate `openbubbles-photos.db` stores metadata snapshots, the opaque next cursor, and
transfer intent/state. Apply metadata plus its cursor in one transaction. Recover interrupted
`Running` rows to `Queued`; keep failed rows retryable; do not infer a cloud deletion from a
missing local cache file.

Picker selections and manually scanned document-tree folders normalize decodable images into the
JPEG original/preview upload contract, copy them into content-addressed private staging, and record
`Queued` rows. Selection and folder scan must never upload. A separate explicit upload action
invokes async UniFFI, persists attempt/failure/success state, and records the remote master ID.
Folder access alone must never scan. Background scheduling stays absent and the compile-time
background flag stays false until the product and protocol gates change. Video/Live Photo uploads,
albums, edits, deletes, and automatic Android gallery observation remain blocked.

Protected assets can require PCS decryption even when the enclosing CloudKit field omits its
encrypted flag. Keep clear asset keys in memory only, never persist or log them, and rely on RFC
3394 integrity rather than key-shape guesses when selecting unwrap candidates. Legacy MMCS keys
are 16 bytes, the Photos MMCS v2 upload profile uses a 16-byte FORD key with wire version `0x03`,
and the generic MMCS v2 profile uses a 32-byte FORD key with wire version `0x04`; RFC 3394 wraps
the clear keys to 24 and 40 bytes respectively. CPL uses the FORD reference signature (`0x01`)
for the master record name, while resource fingerprint fields use the total asset signature
(`0x04`). Error messages crossing
UniFFI must be fixed byte-free categories, safe Apple enum names, or a narrow allowlist of local
diagnostics. Never log a PCS key, MMCS receipt, raw CloudKit response, record, or signed URL.

For uploads, `CPLAsset.masterRef` is an owning CloudKit reference. Preserve the installed CPL
schema's exact field spelling: `assetHDRType`, `fullSizeJPEGSource`, `resJPEGThumb*`, and
`importGroupId` (lowercase `d`, despite the surrounding acronym-preserving names). The read-only
`CPLSchema-com.apple.photos*.plist` resources installed with macOS Photos are a safe field-name
oracle; log only names and wire types when live schema comparison is unavoidable.

Photos encrypts protected record fields with the field name alone as PCS AAD, including when the
record uses a custom per-record key and the zone has no default record key. The upload-token
response carries a nested `CKDPAsset` plus a wrapper token and expiration; preserve the nested
owner, URLs, requestor, record identifier, signatures, and authorization fields, then map the
wrapper token into the asset download token. `CKDPAsset.clearAssetKey` is protobuf field 20.

## Route the change correctly

- For a Kotlin-visible Rust record, enum, callback, or method change, also load
  [../openbubbles-uniffi-contract-change/SKILL.md](../openbubbles-uniffi-contract-change/SKILL.md)
  and regenerate committed bindings. Rust-internal fixes do not require a surface change.
- For any Photos Compose or navigation change, load
  [../m3-expressive/SKILL.md](../m3-expressive/SKILL.md) and the specialist it selects. Keep the
  experimental screen under Settings; do not invent a top-level destination.
- For a catalog schema change, bump the Photos schema version, supply an explicit migration, and
  extend the pinned SQL-signature/upgrade tests. Do not weaken ObjectBox parity.
- For authorized hardware interaction, also load
  [../android-cli/SKILL.md](../android-cli/SKILL.md). A code or diagnosis request alone does not
  authorize an APK install, account change, upload, deletion, or clearing app data.
- Before any remote write, name the disposable asset and exact operation, get explicit user
  authorization, and preserve its hash/size. Selection and local staging are distinct from the
  remote-write authorization. After a failed authorized attempt, ask before another retry unless
  the user has clearly granted continuous retry authorization for that same named asset and
  operation.

## Prove the right evidence tier

Keep host and device evidence separate. Host coverage should include the affected protocol unit
tests, pager/coordinator/catalog tests, UniFFI parity when relevant, model parity, and Android
assembly:

```bash
cargo test --manifest-path rustpush/Cargo.toml --lib --locked
(cd native && ./gradlew :db:test :core:test :app-native:testDebugUnitTest \
  :db:checkModelParity :app-native:checkUniffiBindings \
  :app-native:assembleDebug --console=plain)
```

Add the screenshot gate when UI changed. If the UniFFI surface changed, run
`(cd rust && ./build-uniffi.sh)` first and inspect the generated Kotlin diff.

For live proof, record the device serial, app version/code, APK hash, account mode when known, and
time window. Verify boundaries independently:

1. the personal Photos container reports metadata availability;
2. a bounded page and opaque cursor persist without downloading assets;
3. a visible-cell preview reaches the expected byte count;
4. the promoted cache file has the expected media header and nonzero size;
5. SQLite records success with a blank error, while failure/retry state remains durable;
6. the downloaded state restores after force-stop/cold launch;
7. for upload, picker/folder staging first restores as `Queued` without a write;
8. MMCS accepts both original and preview, PCS wraps the clear 16-byte Photos FORD keys, and the atomic
   CPLMaster+CPLAsset save returns success;
9. the returned master appears in a refreshed OpenBubbles page and on an Apple Photos device.

Treat those upload stages independently. MMCS success without CPL save is not a library upload;
a successful CPL response without cross-device appearance is not two-way sync. Capture stage-only
diagnostics. Do not dump the whole app-process log because unrelated messaging logs can contain
private message/account data.

Normal and Advanced Data Protection accounts are separate hardware targets. A green host test,
visible `Preview downloaded`, valid cached file, durable DB row, and cold-start restoration are
different facts; report exactly which passed. Do not call the feature full Photos sync until
incremental reconciliation, originals/videos/Live Photos, and the relevant account modes are
device-proven.

Commit `rustpush/` changes and push that submodule branch before committing the parent pointer.
End the handoff with implemented and remaining resource types, read/write boundary, tests, device
identity, first failure if any, and the exact commits pushed.
