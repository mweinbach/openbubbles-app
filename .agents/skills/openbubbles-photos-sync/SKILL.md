---
name: openbubbles-photos-sync
description: Extend or diagnose personal iCloud Photos metadata, protected preview/resource downloads, the separate Photos catalog, or live-device proof. Use for the experimental personal Photos path; not Shared Albums or ordinary message attachments.
---

# OpenBubbles Personal Photos

Read [../../../docs/PHOTOS_SYNC.md](../../../docs/PHOTOS_SYNC.md),
[../../../docs/DEVELOPMENT.md](../../../docs/DEVELOPMENT.md), and the CloudKit/PCS sections of
[../../../docs/RUST_KOTLIN.md](../../../docs/RUST_KOTLIN.md) before editing. Preserve the exact
scope: this is an experimental read-only personal iCloud Photos browser/downloader, not Shared
Albums and not full bidirectional sync.

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
user tap fetches one small rendition, writes it to an app-owned `.part` file, verifies that the
decrypted bytes match the expected JPEG or ISO-BMFF header, then atomically promotes the file.
Both Rust and Kotlin reject mismatched bytes. A corrupt completed cache entry must be downloaded
again rather than reported as successful.

The separate `openbubbles-photos.db` stores metadata snapshots, the opaque next cursor, and
transfer intent/state. Apply metadata plus its cursor in one transaction. Recover interrupted
`Running` rows to `Queued`; keep failed rows retryable; do not infer a cloud deletion from a
missing local cache file. Upload rows may be planned durably as `Blocked`, but there is no remote
upload/delete API yet and agents must not add or exercise one speculatively.

Protected assets can require PCS decryption even when the enclosing CloudKit field omits its
encrypted flag. Keep clear asset keys in memory only, never persist or log them, and rely on RFC
3394 integrity rather than key-shape guesses when selecting unwrap candidates. Error messages
crossing UniFFI must be fixed byte-free categories or a narrow allowlist of safe diagnostics.

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
3. an explicit preview reaches the expected byte count;
4. the promoted cache file has the expected media header and nonzero size;
5. SQLite records success with a blank error, while failure/retry state remains durable;
6. the downloaded state restores after force-stop/cold launch.

Normal and Advanced Data Protection accounts are separate hardware targets. A green host test,
visible `Preview downloaded`, valid cached file, durable DB row, and cold-start restoration are
different facts; report exactly which passed. Do not call the feature full Photos sync until
incremental reconciliation, originals/videos/Live Photos, and the relevant account modes are
device-proven.

Commit `rustpush/` changes and push that submodule branch before committing the parent pointer.
End the handoff with implemented and remaining resource types, read/write boundary, tests, device
identity, first failure if any, and the exact commits pushed.
