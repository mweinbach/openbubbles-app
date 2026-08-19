# iCloud Photos Sync Plan

Status snapshot: 2026-08-19. This document is the implementation anchor for personal iCloud
Photos work. It does not describe the existing Shared Albums feature, except where that code can
be reused.

## Product boundary

The first product target is an experimental, read-only iCloud Photos browser and downloader:

1. detect whether the signed-in account exposes the personal Photos library;
2. page recent asset metadata without downloading the whole library;
3. explicitly download an original image, video, or complete Live Photo pair;
4. prove incremental changes before adding a durable catalog or background work.

Do not call this "Photos sync" in shipping UI until incremental reconciliation is proven. Upload,
remote deletion, album mutation, edits, favorites, hidden state, and Shared Photo Library writes
are later phases.

## What exists today

### Shared Albums

`rustpush/src/sharedstreams.rs` implements Apple's Shared Streams service, not the personal iCloud
Photos library. It currently provides:

- MobileMe Shared Streams authentication and the `com.apple.sharedstreams` APS topic;
- album discovery/invitations through `getchanges`, `albumsummary`, and `getassets`;
- MMCS asset download/upload;
- a folder reconciler backed by `sync.plist` and an asset-guid-to-filename map;
- Kotlin-visible Shared Albums operations in `rust/src/uniffi_ext.rs`;
- native Settings/UI and MediaStore gallery export in `app-native/`.

This is useful reference code, but its folder reconciliation is not the model for personal Photos.
In particular, deleting a local Shared Album file can propagate a remote deletion, Android file
watching is disabled, downloads select one principal rendition, and downloaded gallery items are
copies.

### Reusable infrastructure

The repository already has most lower-level primitives needed for a Photos protocol spike:

- Apple account, MobileMe tokens, anisette, and APS in `rustpush/`;
- generic private/shared CloudKit protobuf operations in
  `rustpush/src/icloud/cloudkit.rs`;
- MMCS upload/download support;
- PCS and Secure iCloud Keychain support, including the `Photos` keychain view;
- process-owned `NativePushState` service lifetime;
- committed async UniFFI bindings from `rust/` to `:core`;
- page/cursor/apply patterns in `core/.../sync/CloudSyncManager.kt` and
  `CloudSyncStateStore.kt`.

The personal Photos protocol is not implemented. A repository search found no client or record
model for the `com.apple.photos.cloud` container, `PrimarySync`, `CPLMaster`, `CPLAsset`, or CPL
album/resource relations.

## Investigation completed

The 2026-08-19 static audit established that:

- Shared Albums and personal iCloud Photos are separate Apple services and data models.
- The personal library is represented by a larger CloudKit/CPL record graph with multiple resource
  renditions, sidecars, Live Photo pairs, album relations, tombstones, and edit state.
- Existing CloudKit, MMCS, PCS, account, and UniFFI code should reduce transport work, but the CPL
  schema, authorization details, change semantics, and Advanced Data Protection behavior still
  require live-account proof.
- A read-only implementation is a reasonable first project. Full bidirectional Photos parity is a
  separate multi-month effort.

No personal Photos code, live container probe, sanitized protocol fixture, durable photo catalog,
background worker, upload, or delete operation has been implemented or validated yet.

## Ownership and proposed architecture

```text
Settings / experimental Photos screen       app-native/
                  |
       Photos wiring + platform export       app-native/
                  |
    PhotosPort + paging/apply policy         core/
                  |
        committed async UniFFI API           rust/
                  |
  CPL records, CloudKit, PCS, MMCS, APS      rustpush/
```

- Apple protocol behavior and CPL types belong in a new `rustpush/src/photos.rs` module (or a
  `photos/` submodule if it grows).
- `rust/src/api/api.rs` may own construction/lifetime of an optional Photos client alongside the
  other `SharedICloudServices`.
- Kotlin-visible records and methods belong in `rust/src/uniffi_ext.rs`. Network calls must use
  async UniFFI; do not add Flutter Rust Bridge exports or synchronous `RUNTIME.block_on` network
  methods.
- Paging, cancellation, cursor commit order, and user-facing progress belong in a fakeable
  `core/photos` manager/port boundary.
- Android MediaStore export, WorkManager constraints, and foreground-transfer behavior stay in
  `app-native/`.
- Do not add photo entities to the legacy `db/objectbox-model.json` during the protocol spike. A
  production catalog should use a separate store/model so it cannot endanger Flutter-era message
  store compatibility or inflate message backups.

## Initial UniFFI shape

Names are provisional; keep the first contract narrow.

- `photos_access_state()` returns a structured state such as `NeedsLogin`, `ServiceUnavailable`,
  `NeedsKeychain`, `Indexing`, or `Available`, plus sanitized diagnostic data.
- `list_photos_page(cursor, limit)` returns a bounded page and an opaque next cursor.
- A photo summary should initially contain only stable product fields: asset/master ID, filename,
  media kind, created/added times, dimensions, duration, favorite/hidden flags, and available
  resource kinds.
- A later `download_photo_resource(asset_id, resource_kind, destination)` performs an explicit
  atomic download and returns verified size/checksum information.

CloudKit authentication tokens, signed download URLs, change tags, encryption material, raw
records, and server response bodies must remain in Rust and must not be logged or cross UniFFI.

## Implementation slices

### Slice 1: access probe and first metadata page

1. Add the internal Photos client and container initialization.
2. Probe the private library and indexing state without modifying records.
3. Query and decode at most the newest 100 asset/master pairs.
4. Add async UniFFI access/page records and regenerate committed Kotlin bindings.
5. Add a fakeable `PhotosPort` plus deterministic paging/cancellation tests in `:core`.
6. Add an experimental iCloud Settings entry that reports capability and displays metadata only.

This is the first go/no-go slice. Do not add persistence or scheduled work merely to make a demo
look like sync.

### Slice 2: explicit resource download

1. Download an original still image and video to app-owned temporary storage.
2. Support and verify both components of a Live Photo.
3. Atomically promote completed files; clean up partial files on cancellation/failure.
4. Add an explicit "Save to device" MediaStore export. Exported gallery files are user-owned
   copies, not the sync root.

### Slice 3: durable read-only mirror

1. Finalize a separate photo catalog schema after real CPL fixtures establish stable identifiers
   and relationships.
2. Persist opaque cursors only after a page has been applied successfully.
3. Make record application idempotent and model tombstones separately from local cache eviction.
4. Add thumbnail/original caches with quotas, resumable transfers, and storage-pressure behavior.
5. Preserve gallery exports when cloud records disappear unless the user explicitly requests
   cleanup.

### Slice 4: background reconciliation

1. Add dedicated WorkManager scheduling with network, charging, battery, and storage constraints.
2. APS notifications may mark Photos dirty and enqueue work; never perform a library sync inside
   the APS callback or message poll.
3. Use foreground transfer behavior for user-initiated long downloads where Android requires it.
4. Keep Photos failures isolated from iMessage receive and Messages-in-iCloud history sync.

### Slice 5: mutations

Only start after read-only sync has a durable device test record:

- uploads and resource packaging;
- album creation/membership changes;
- favorite, hidden, edit, and metadata writes;
- local/remote deletion with explicit conflict policy;
- Shared Photo Library participation.

Do not expose mutation methods speculatively in the initial UniFFI API.

## Safety rules

- Read-only is the default until a later reviewed milestone explicitly changes it.
- Missing local data is never evidence that a remote asset should be deleted.
- Deleting an app cache entry must never delete an iCloud original.
- Remote tombstones may remove catalog/cache state, but not user-exported MediaStore copies.
- Apply a page before committing its continuation cursor; a crash may replay a page, so upserts and
  tombstones must be idempotent.
- Never log photo metadata, raw CloudKit records, signed URLs, credentials, keys, or full server
  responses. Sanitized protocol fixtures must contain no account or asset-identifying data.
- Upload/delete tests must use disposable assets and require explicit user authorization.
- Advanced Data Protection and non-ADP accounts are separate hardware evidence targets.

## First hardware go/no-go

Record the device/account mode, app commit, time window, and focused redacted logs. The first
milestone passes only when all of these are observed:

1. the Photos container opens with the existing OpenBubbles account session;
2. the newest bounded page decodes without a whole-library scan;
3. its continuation state resumes correctly;
4. adding one photo on an Apple device appears through an incremental change;
5. an original still and a full Live Photo pair download and verify;
6. normal and ADP-enabled accounts either work or return a well-defined capability state;
7. no Apple account, message history, Shared Albums, or push behavior regresses.

If container access, change tracking, or ADP cannot be made reliable, stop before building the
catalog, gallery, or worker layers.

## Verification for implementation changes

Run the union of the affected gates, keeping host evidence separate from live Apple evidence:

```bash
cargo test --manifest-path rustpush/Cargo.toml --lib --locked
(cd rust && ./build-uniffi.sh)
(cd native && ./gradlew :core:test :app-native:testDebugUnitTest \
  :app-native:checkUniffiBindings :db:checkModelParity \
  :app-native:assembleDebug --console=plain)
```

Compose work also requires the Material 3 Expressive routing skill and the relevant screenshot
gate. A new persistence module/store needs its own deterministic compatibility and upgrade tests;
do not weaken or regenerate the existing ObjectBox model.

## Rough sizing

- Access probe plus first bounded metadata page: about 1–2 weeks.
- Production read-only library, explicit originals, and incremental catalog: about 6–10 weeks.
- Uploads and safe library mutations: an additional 3–5 months.
- Near-full Apple Photos parity: roughly 9–18 months with ongoing protocol maintenance risk.

Treat these as planning ranges until the first live container and ADP probes produce evidence.
