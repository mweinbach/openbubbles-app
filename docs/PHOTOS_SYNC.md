# iCloud Photos Sync Plan

Status snapshot: 2026-08-19. This document is the implementation anchor for personal iCloud
Photos work. It does not describe the existing Shared Albums feature, except where that code can
be reused.

## Product boundary

The first product target is an experimental, read-only iCloud Photos browser and downloader:

1. detect whether the signed-in account exposes the personal Photos library;
2. page recent asset metadata without downloading the whole library;
3. explicitly download a small preview, then an original image, video, or complete Live Photo
   pair;
4. persist metadata and transfer intent without treating missing local data as a cloud deletion;
5. prove incremental changes before adding background work.

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

The access/metadata slice and the host-side preview/catalog foundation are now implemented.
Original resources, Live Photo pair downloads, incremental changes, background work, and remote
mutations described below are not.

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

### Implemented first slice (host-verified only)

- `rustpush/src/photos.rs` opens the private `com.apple.photos.cloud` container as the native
  Photos client, probes `CheckIndexingState` in `PrimarySync`, and performs a metadata-only
  `CPLAssetAndMasterByAddedDate` query.
- Pages are capped at 100 photo pairs. Rust joins `CPLAsset` to `CPLMaster`, skips soft-deleted
  records, and returns only a small summary. Raw records, asset values, download URLs, location,
  captions, and encryption material stay behind the Rust boundary.
- `photos_access_state()` and `list_photos_page(cursor, limit)` are async UniFFI exports with
  regenerated committed Kotlin bindings.
- `core/photos/PhotosPort.kt` provides the fakeable port and a deduplicating pager. It has tests
  for indexing behavior, page continuation/deduplication, and the FFI page-size bound.
- Android Settings contains a calm `Photos (experimental)` entry and metadata screen. It states
  explicitly that the library is experimental and read-only.

### Implemented setup slice (awaiting live preview proof)

- Metadata queries now ask for the presence and size of `resJPEGThumbRes` for images and
  `resVidSmallRes` for videos while still using `NO_ASSETS`; listing never downloads media.
- `download_photo_preview(master_id, media_kind, destination, progress)` is an async UniFFI call.
  Rust fetches exactly one selected asset field, keeps CloudKit/MMCS authorization material behind
  the FFI boundary, streams byte progress, flushes, and fsyncs the staging file.
- `PhotoTransferCoordinator` creates deterministic app-owned preview paths, writes to `.part`,
  atomically promotes successful files, removes failures, coalesces same-asset requests, and
  persists queued/running/succeeded/failed state for clean retries.
- Android now owns a separate WAL-enabled `openbubbles-photos.db`. Metadata plus its next cursor
  commit in one transaction; transfer rows include direction, resource kind, local path, progress,
  attempts, error, and timestamps. Interrupted `Running` rows recover to `Queued` at startup. The
  v1 SQL signature is pinned by a unit test, and a version bump fails until an explicit migration
  is supplied.
- Cached metadata and completed preview state restore before a live refresh. The experimental
  Photos rows expose explicit preview downloads and progress; no automatic download is started.
- Upload source files can be validated and recorded as durable `Blocked` plans. There is no remote
  upload UniFFI method and no CPL write is attempted until a live device proves the required
  records, assets, change tags, and commit order.

The database is intentionally an Android implementation behind the Android-free `PhotosCatalog`
contract. It does not add entities to `db/objectbox-model.json` and cannot move or rewrite the
legacy message store.

Host evidence on 2026-08-19:

- full `rustpush` library suite after the preview slice: 26 passed, 2 manual-network tests ignored;
- UniFFI release build, committed Kotlin generation, and binding parity check: passed;
- `:db:test`, `:core:test`, `:app-native:testDebugUnitTest`, and ObjectBox model parity: passed;
- Android x86_64/arm64 Rust compilation and `:app-native:assembleDebug`: passed;
- the updated light/dark Photos screenshot goldens: passed and were inspected;
- debug APK SHA-256:
  `bec4f1890589a5c37f1bcfc5f7df14186f6c8e14ce4ad625dd335c7955d28fd5`.

The repository-wide screenshot task still reports 28 unrelated existing baseline mismatches under
this renderer. Only the two Photos cases were updated; unrelated reference images were left
untouched.

This does **not** prove that Apple's server accepts the inferred native Photos bundle/container
pair for an OpenBubbles account session. No live Apple account, normal/ADP comparison, preview or
original download, incremental change cursor, background worker, upload, or delete has been
validated. The experimental screen is the next device-test surface for that go/no-go proof.

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

The first two names are now committed; keep later additions narrow.

- `photos_access_state()` returns `Ready`, `Indexing`, or `Unavailable`, plus sanitized detail.
  Transport/login failures remain typed UniFFI errors rather than being collapsed into access.
- `list_photos_page(cursor, limit)` returns a bounded page and an opaque next cursor.
- A photo summary should initially contain only stable product fields: asset/master ID, filename,
  media kind, created/added times, dimensions, duration, favorite/hidden flags, and available
  resource kinds.
- `download_photo_preview(master_id, media_kind, destination, progress)` is committed for the
  small image/video display rendition. Kotlin owns atomic promotion and durable state.
- A later `download_photo_resource(asset_id, resource_kind, destination)` will cover originals and
  Live Photo pairs and return verified size/checksum information.

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

The host implementation of this slice is complete. Live container authorization and real-record
decoding remain the go/no-go gate. Do not add persistence or scheduled work merely to make the
experimental screen look like sync.

### Slice 2: explicit resource download

1. Download a small image/video preview to app-owned temporary storage. Host implementation is
   complete; live proof is pending.
2. Download an original still image and video.
3. Support and verify both components of a Live Photo.
4. Atomically promote completed files; clean up partial files on cancellation/failure. The shared
   coordinator is implemented for previews.
5. Add an explicit "Save to device" MediaStore export. Exported gallery files are user-owned
   copies, not the sync root.

### Slice 3: durable read-only mirror

1. Evolve the versioned separate photo catalog after real CPL fixtures establish stable
   identifiers and relationships. Schema v1 now holds bounded metadata snapshots and transfers.
2. Persist opaque cursors only after a page has been applied successfully. The v1 snapshot+cursor
   transaction implements this rule; incremental page application is still pending.
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

Only start remote execution after read-only sync has a durable device test record. Local upload
planning rows exist now so UI/process work can be restored, but they intentionally remain blocked:

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
