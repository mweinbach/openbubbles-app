# iCloud Photos Sync Plan

Status snapshot: 2026-08-19. This document is the implementation anchor for personal iCloud
Photos work. It does not describe the existing Shared Albums feature, except where that code can
be reused.

Agents changing or diagnosing this path must also load
[openbubbles-photos-sync](../.agents/skills/openbubbles-photos-sync/SKILL.md). The skill is the
operational runbook for ownership, safety, verification, and hardware evidence; this document
remains the product boundary, implementation status, protocol findings, and roadmap.

## Product boundary

The first product target is an experimental personal iCloud Photos browser with explicit,
user-initiated transfers:

1. detect whether the signed-in account exposes the personal Photos library;
2. page recent asset metadata without downloading the whole library;
3. download small previews only for visible gallery cells, then download one original image or
   video only after explicit selection (complete Live Photo pairs remain future work);
4. persist metadata and transfer intent without treating missing local data as a cloud deletion;
5. stage selected photos or manually scanned document-tree folders privately and require a
   separate explicit upload action;
6. offer one opt-in "Back up new camera photos" switch that stages and uploads images Android adds
   to `DCIM` after the switch is turned on, and nothing older.

Do not call this "Photos sync" in shipping UI until incremental reconciliation is proven. The JPEG
upload path and the opt-in camera backup do not imply device-gallery mirroring, remote deletion,
album mutation, edits, favorites, hidden state, video uploads, or Shared Photo Library writes.

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

The access/metadata slice, preview/original catalog foundation, adaptive foreground gallery, and
explicit JPEG upload path are implemented. Live Photo pair downloads, incremental changes,
enabled background work, and remote mutations other than that narrow upload path are not.

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
  `CPLAssetAndMasterByAssetDate` query, matching the index used by Apple's installed Photos
  client. CloudKit's opaque continuation marker, not an inferred record rank, advances each
  bounded page. The initial request includes the query and zone; continuation requests retain
  the zone but omit the original query, as required by the Photos CloudKit endpoint.
- Pages are capped at 100 photo pairs. Rust joins `CPLAsset` to `CPLMaster`, skips soft-deleted
  records, decrypts protected native capture dates, media types, and visibility flags with each
  record's own Photos PCS key, and returns only a small summary. Raw records, asset values,
  download URLs, location, captions, and encryption material stay behind the Rust boundary.
- `photos_access_state()` and `list_photos_page(cursor, limit)` are async UniFFI exports with
  regenerated committed Kotlin bindings.
- `core/photos/PhotosPort.kt` provides the fakeable port and a deduplicating pager. It has tests
  for indexing behavior, page continuation/deduplication, and the FFI page-size bound.
- Android Settings contains a calm `Photos (experimental)` entry and adaptive media grid. Listing
  is metadata-only; cells fetch only their small display rendition as they enter the lazy-grid
  viewport/prefetch window.

### Implemented setup slice (device-proven for image previews)

- Metadata queries now ask for the presence and size of `resJPEGThumbRes` for images and
  `resVidSmallRes` for videos while still using `NO_ASSETS`; listing never downloads media.
- `download_photo_preview(master_id, media_kind, destination, progress)` is an async UniFFI call.
  Rust fetches exactly one selected asset field, keeps CloudKit/MMCS authorization material behind
  the FFI boundary, streams byte progress, flushes, and fsyncs the staging file.
- `PhotoTransferCoordinator` creates deterministic app-owned preview paths, writes to `.part`,
  atomically promotes successful files, removes failures, coalesces same-asset requests, and
  persists queued/running/succeeded/failed state for clean retries. Cancellation durably restores
  the queued state even when the SQLite write suspends, so scrolled-away previews do not become
  permanently stuck in `Running`.
- Android now owns a separate WAL-enabled `openbubbles-photos.db`. Metadata plus its next cursor
  commit in one transaction; transfer rows include direction, resource kind, local path, progress,
  attempts, error, and timestamps. Interrupted `Running` rows recover to `Queued` at startup. The
  v1 SQL signature is pinned by a unit test, and a version bump fails until an explicit migration
  is supplied.
- Cached metadata and completed preview state restore before a live refresh. Preview requests are
  bounded to composed gallery cells and a four-request foreground concurrency limit.
- Upload rows share this durable transfer store and recover after process death or in-place APK
  replacement. Selection creates only a `Queued` row; it does not cross the Apple write boundary.

### Implemented explicit JPEG upload slice (device-proven for the original single-file path)

- Android's system photo picker accepts one JPEG. The app copies the original to private cache,
  reads EXIF orientation and capture time, and generates a software-decoded JPEG preview capped at
  414 pixels. The shared coordinator fsyncs content-addressed original/preview files plus a
  versioned metadata sidecar before inserting a restorable `Queued` transfer.
- A separate row tap invokes `upload_photo_jpeg(...)` through the fakeable `PhotosPort` and async
  UniFFI. Running, failure, retry count, success, and the returned remote master ID persist in the
  existing versioned Photos transfer table. Re-selecting content that already succeeded returns
  the prior completed row.
- Rust validates both JPEGs and their dimensions, prepares the original and preview in one MMCS
  v2 upload batch using Photos' 16-byte FORD key and wire version `0x03`, and keeps the clear
  resource keys in memory only. PCS RFC 3394-wraps each resource key directly with its new
  CPLMaster record key; protected metadata fields use the field name alone as authenticated data.
  No clear key crosses UniFFI or persistence.
- CloudKit's upload-token response is decoded as Apple's nested `CKDPAsset` plus wrapper token and
  expiration. The returned owner, URLs, requestor, record identifier, signatures, and other
  authorization fields are preserved rather than reconstructed. The wrapper token becomes the
  asset download token, and the clear asset key uses protobuf field 20.
- The CPL master record name uses the MMCS v2 FORD reference signature (`0x01`), while
  `resOriginalFingerprint` and `resJPEGThumbFingerprint` use their resources' total asset
  signatures (`0x04`). A deterministic CPLAsset UUID acts as the content retry anchor. CPLMaster
  and CPLAsset are submitted together with zone isolation.
  The asset-to-master reference is an owning CloudKit reference. Record fields preserve CPL's
  exact mixed acronym casing, including `assetHDRType`, `fullSizeJPEGSource`, the
  `resJPEGThumb*` family, and the exceptional lowercase-`d` `importGroupId`.
- The Apple write contract remains deliberately JPEG-only. It does not add video/Live Photo
  upload pairs, edits, albums, deletes, or automatic Android gallery observation.

### Implemented foreground gallery and manual source slice (host-verified only)

- The Photos screen is now an adaptive, stable-key thumbnail grid. A composed cell requests only
  its advertised small image/video rendition. Failed previews require an explicit retry.
- Selecting a cell is the only UI event that can call the new async
  `download_photo_original(master_id, media_kind, destination, progress)` method. Originals use a
  separate durable transfer ID and cache directory, are validated as supported image/ISO-BMFF
  media, and replace the preview in the full-screen viewer after atomic promotion.
- The Android picker accepts up to 50 images. JPEG input is preserved; other decodable image
  formats, including provider-supported HEIC, are normalized to a bounded JPEG original before
  they enter the existing JPEG-only Apple upload contract. Selection stages files only.
- Users can persist read access to chosen Android document-tree folders. `Scan now` recursively
  finds at most 500 images and stages them; choosing a folder never scans or uploads it.
- `PhotosBackgroundSyncWorker` then contained the future queued-upload and metadata-refresh pass
  behind a compile-time `false` with no scheduler or enable control. The capture-metadata and
  camera-backup slice below replaced that with the opt-in `DCIM` backup switch.

This slice has host coverage but no new hardware protocol claim. In particular, original images,
original videos, converted HEIC uploads, multi-file batches, and folder batches still require
device evidence before they can be described as live-proven.

### Implemented capture metadata and camera backup slice (host-verified only)

Status snapshot: 2026-08-21. Native Photos clients showed uploads at the wrong time, without a
location, and with "No camera information" because the CPL records carried none of it: the
master's `mediaMetaDataEnc` was a bare JFIF dictionary, the asset zone was hardcoded to UTC with a
zero offset, and `locationEnc` was never written. Apple clients read those record fields, not the
uploaded original, so the fix is record-side.

- `rustpush/src/photos_metadata.rs` reads the staged JPEG's EXIF (kamadak-exif, primary IFD only)
  and builds the ImageIO `CGImageProperties` dictionary a native import would store: top-level
  `PixelWidth`/`PixelHeight`/`Orientation`/`DPIWidth`/`DPIHeight`/`ColorModel`/`Depth`/
  `ProfileName`, `{TIFF}` (Make, Model, Software, DateTime, Orientation, resolution),
  `{Exif}` (DateTimeOriginal, OffsetTime*, Subsec*, ExposureTime, FNumber, ISOSpeedRatings,
  FocalLength, FocalLenIn35mmFilm, LensMake/LensModel/LensSpecification, ExifVersion, and the
  rest of the tag set, with ImageIO's spelling), `{GPS}` (decimal `Latitude`/`Longitude` with refs,
  `Altitude`, `TimeStamp`, `DateStamp`, speed, track, positioning error), and `{JFIF}` only when the
  file actually has an APP0 segment. Maker notes, user comments, IFD pointers, and strip/tile
  layout are never copied. `CPLMaster.mediaMetaDataType` is now `CGImageProperties`.
- `CPLAsset.assetDate` (and `CPLMaster.originalCreationDate`) come from `DateTimeOriginal` plus the
  EXIF UTC offset. `timeZoneOffset` is that offset in seconds and `timeZoneNameEnc` is the device's
  IANA zone when its offset agrees, otherwise Apple's fixed `GMT±HHMM` spelling. A camera clock
  without an offset is read in the device zone Kotlin resolved at that wall-clock time; a file with
  no date falls back to the caller's capture instant, then to now, with the historical `UTC`/0 pair
  only when nothing is known.
- `CPLAsset.locationEnc` is a CoreLocation-shaped binary plist (`lat`, `lon`, `alt`, `course`,
  `speed`, `horizontalAccuracy`, `verticalAccuracy`, `timestamp`) derived from the GPS IFD, with
  CoreLocation's own unknown sentinels, and omitted entirely when the file has no valid coordinate.
- `upload_photo_jpeg` gained an optional `UPhotoTimeZone { name, offset_seconds }` fallback and the
  committed bindings were regenerated. `PhotoTransferCoordinator` stores the zone in a version-2
  sidecar and still reads version-1 sidecars from earlier stagings.
- Android reads MediaStore items with `MediaStore.setRequireOriginal` when `ACCESS_MEDIA_LOCATION`
  is granted, so the GPS tags reach the staged JPEG instead of Android's redacted copy; a refused
  original falls back to the redacted stream. A HEIC/PNG/WebP source that has to be re-encoded has
  its EXIF copied onto the normalized JPEG (orientation reset to upright, pixel dimensions rewritten,
  maker notes and thumbnails dropped) so the uploaded original carries the capture's metadata too.
- `parseExifOriginalDateTime` no longer treats an offset-less camera clock as UTC; it reads it in
  the device zone, and the picker also records that zone's offset at the capture wall-clock time.
- The previously dormant `PhotosBackgroundSyncWorker` is now the opt-in "Back up new camera
  photos" switch in the uploads sheet. Enabling requests `READ_MEDIA_IMAGES` (storage on 12L and
  lower, "selected photos" on 14+) plus `ACCESS_MEDIA_LOCATION`, records the newest current
  `DCIM` MediaStore id as a watermark, and schedules an hourly periodic pass plus a MediaStore
  content-URI trigger that re-arms after every run. A pass stages at most 60 newer `DCIM` images
  through the same picker/staging path, uploads queued rows (failed rows retry up to five times
  automatically; a tap can still retry later), refreshes the cached library, and advances the
  watermark per item so a crash resumes instead of repeating. Photos that already existed when the
  switch was turned on are not uploaded; videos are skipped. Explicit uploads and the worker share
  one process-wide upload gate. Disabling cancels both work names; sign-out clears the switch,
  watermark, and schedule through `PhotosAccountCleanup`.
- The Photos app bar now owns the "Add to iCloud Photos" action; the floating button over the grid
  is gone and the density controls moved into the overflow menu.

Host evidence on 2026-08-21:

- `rustpush` library tests: 104 passed, 2 manual-network tests ignored, including the new
  `photos_metadata` suite (ImageIO shape, capture-time resolution, location plist, key renames);
- `:db:test`, `:core:test`, `:app-native:testDebugUnitTest`, `:db:checkModelParity`,
  `:app-native:checkUniffiBindings`, `:app-native:lintDebug`, and `:app-native:assembleDebug`
  passed;
- the arm64 debug APK is 132,911,778 bytes with SHA-256
  `f2d291093485b90228b6f467f7a98fc2aae3dc5601d172eb18fd97871bf0ea60`.

Native iPhone/Mac appearance of the corrected date, location, and camera details for a fresh
upload, and a real background backup pass on hardware, are the next device checkpoints and have not
been claimed here.

### Implemented timeline and viewer slice (host-verified only)

The library reads as a timeline instead of one flat grid. This is presentation only: no new UniFFI
call, no additional field, and no change to what the bounded listing requests.

- `ui/photos/PhotosTimeline.kt` is pure: it filters (All / Favorites / Videos), drops hidden assets,
  orders by capture time falling back to added time, groups into day/month/year sections, decides the
  pinch density step, and formats the info sheet. `PhotosTimelineTest` is the oracle for all of it.
- Grouping is client-side over the pages already loaded. The protocol follows Apple's asset-date
  index and its opaque continuation markers, so a section can still grow as later pages arrive;
  the grid must not be described as a complete incremental library until the change catalog of
  slice 3 exists.
- Hidden assets are now excluded from the grid. The flag was already stored; showing them contradicted
  what it is for.
- The viewer is a pager over the same timeline. Only the **settled** page is selected, which is what
  requests an original — flicking through the library does not start a download per page. Pinch-zoom
  and pan are claimed only when zoomed in or with two fingers, so paging and zooming never fight.
- The info sheet shows only listing metadata (name, date, dimensions, size, kind, favourite) and says
  in as many words that location, people, and captions are never requested.
- Density has app-bar buttons as well as a pinch, and adding to the library moved to a single explicit
  action button. Nothing about the timeline triggers a refresh, a transfer, or an upload.

The database is intentionally an Android implementation behind the Android-free `PhotosCatalog`
contract. It does not add entities to `db/objectbox-model.json` and cannot move or rewrite the
legacy message store.

Host evidence on 2026-08-19:

- full `rustpush` library suite after the PCS/MMCS/upload-schema fixes: 47 passed, 2 manual-network
  tests ignored;
- Rust facade regression coverage proves a staged preview can be rewound and read before atomic
  promotion;
- the combined `:db:test`, `:core:test`, `:app-native:testDebugUnitTest`,
  `:db:checkModelParity`, `:app-native:checkUniffiBindings`, and
  `:app-native:assembleDebug` gate passed;
- UniFFI parity and Android x86_64/arm64 Rust compilation passed as part of that gate;
- the updated light/dark Photos screenshot goldens: passed and were inspected;
- upload coordinator coverage proves durable original/preview/metadata promotion, same-content
  idempotence, JPEG-only validation, explicit execution, persisted remote ID, and retry state;
- the final upload-capable debug APK is 323,792,632 bytes with SHA-256
  `b3cbadcf8b35ebbd463976b9feab726b74afd6c649a1981db381db99ea968527`;
- the earlier preview-only debug APK had SHA-256
  `93afd838c32d830f36a3943eab6e5b14ee6350db3c83fbf52596534af7afda29`.

The latter hash is retained as the earlier preview-download device-evidence artifact; it is not
the upload-capable APK.

Current foreground-gallery host evidence on 2026-08-19:

- `rustpush` library tests: 49 passed and 2 manual-network tests ignored;
- the combined database, core, Android unit, ObjectBox parity, UniFFI parity, two-ABI Rust build,
  debug assembly, and Photos light/dark screenshot gate passed;
- coordinator coverage proves preview/original resource separation, independent cache paths,
  successful atomic original promotion, and rejection of an invalid original;
- the hard-disabled background invariant has a unit test, and no scheduler or UI enable control
  exists;
- the debug APK is 323,917,208 bytes with SHA-256
  `3fc3ca74ed80d21e60ad290250869bee2535a4a5c6bc96a692d4f9e215f14754`.

This is host evidence only for the new gallery/original/batch/folder behavior; the device evidence
below predates this slice.

Device evidence on 2026-08-19:

- Pixel 10 Pro Fold `58201FDCG003BG` first proved previews with OpenBubbles
  `3.4.0 (20002268)` and the earlier preview-only APK hash above. The current upload-capable APK
  installed in place as `3.4.2 (20002270)` while preserving `firstInstallTime`
  `2026-08-18 10:57:34` and the signed-in app state.
- The live private Photos container reported `Personal library metadata available`. The first
  bounded page persisted 60 image summaries plus an opaque continuation cursor in
  `openbubbles-photos.db`.
- The live encrypted-resource chain resolved the Photos PCS service key from the
  `ProtectedCloudStorage` keychain view, unwrapped the legacy 24-byte asset protection value,
  and prepared two non-persistent MMCS protection-key candidates. No key bytes crossed UniFFI or
  were logged.
- MMCS chunk identifiers now preserve their wire length. Prefix-`0x01`/17-byte identifiers use
  their direct AES-128-CFB key; prefix-`0x02`/25-byte identifiers use RFC 3394 unwrap and its
  integrity check to select the correct authenticated asset key. Malformed or unknown PCS values
  return errors instead of panicking.
- Three image previews completed as JPEGs at 31,153, 52,616, and 48,694 bytes. For the first
  fully inspected path, UI and SQLite agreed on `48,694 / 48,694`, the promoted file was a valid
  407-by-422 JFIF/Exif JPEG, and `last_error` was cleared. All three rows and
  `Preview downloaded` states restored after a force-stop and cold launch.
- One earlier failed row remained durable with its byte target and sanitized error, while a
  retried row reached `Succeeded` and retained its attempt count. This separately proves failure,
  retry, success, and cold-start persistence rather than inferring them from the UI alone.
- The upload-capable APK installed in place on the same Pixel and exposed `Upload to iCloud` only
  after the live 60-record metadata list. Selecting the authorized 1,600-by-1,200 disposable JPEG
  created a 43.7 KB `Queued` transfer and required a separate upload tap.
- Early authorized attempts isolated the CPL request requirements: `masterRef` must be owning;
  CPL preserves acronyms in `assetHDRType`, `fullSizeJPEGSource`, and `resJPEGThumb*`; the schema
  spells `importGroupId` with a lowercase `d`; protected metadata uses field-name-only PCS AAD;
  and Photos' MMCS v2 resources use 16-byte FORD keys rather than the generic 32-byte profile.
- Runtime inspection of macOS's `CloudKitDaemon` protobuf descriptors then exposed two wire
  mismatches. The asset upload response is a nested `CKDPAsset` with the token on its wrapper, not
  an `AssetUploadData` plus a base64-encoded inner protobuf; and `CKDPAsset.clearAssetKey` is field
  20. Preserving Apple's returned asset authorization fields stopped the Mac client from failing
  to unwrap the uploaded resources.
- The Mac Photos process next reported a resource fingerprint scheme mismatch: it received
  MMCSv2 where it expected MMCSv1. The master record name must use the `0x01` FORD reference
  signature, but the resource fingerprint fields must use the `0x04` total signatures. Four
  disposable records created by the earlier probes were repaired with their original FORD keys,
  corrected upload authorization, and corrected fingerprints; the subsequent native Mac change
  batch completed without PCS or partial-failure errors.
- A fresh upload of `218.jpg` then completed on Pixel 10 Pro Fold `58201FDCG003BG`.
  OpenBubbles reported `Uploaded to iCloud Photos` for all 43,716 staged bytes. The 1,600-by-1,200
  source JPEG was 34,386 bytes with SHA-256
  `4bc8da09ee6aa4765c11f8efba533862932ec596f0d8f5fd22355e9b040d4ed8`.
- Native macOS Photos found exactly one media item named `218.jpg`. Exporting its unmodified
  original produced a 34,386-byte, 1,600-by-1,200 JFIF JPEG with the identical SHA-256
  `4bc8da09ee6aa4765c11f8efba533862932ec596f0d8f5fd22355e9b040d4ed8`. This proves the full
  Android staging -> encrypted MMCS -> atomic CPL save -> native Apple Photos fetch/decrypt path
  byte-for-byte. Direct visual confirmation on an iPhone was not available in this run.
- The upload trace also revealed pre-existing PCS and MMCS debug statements containing key or raw
  receipt material. Those statements were removed. Upload errors exposed to Kotlin contain only
  fixed local diagnostics or Apple enum names, never raw records or response text.

The repository-wide screenshot task still reports 28 unrelated existing baseline mismatches under
this renderer. Only the two Photos cases were updated; unrelated reference images were left
untouched.

This proves one normal signed-in account's personal-container access, bounded metadata query,
separate catalog persistence, explicit image-preview download, retry, atomic cache promotion, and
cold-start restoration. The sampled 60 records contained no videos, so the small-video rendition
is still host-only. ADP behavior, originals, Live Photos, incremental change application,
background work and delete remain unvalidated. The narrow, explicit JPEG write path is implemented
and live-proven through MMCS, the atomic CPL save, a refreshed remote page, the persisted receipt,
and an identical original exported by native macOS Photos. This is not general two-way sync.

### Implemented gallery export slice (host-verified only)

Downloading a full-quality asset is what puts it on the phone: the promoted original is mirrored
into the `DCIM/iCloud` album, so an iCloud photo can be picked, sent, or shared by any Android app.
This is a one-way copy out; it adds no Apple call.

- `data/photos/PhotoLibraryExport.kt` is pure: given the promoted cache file, the iCloud filename,
  the media kind, and the capture time it produces the gallery display name, MIME type, and album
  path, or refuses. The cache file's extension decides the format, a name that disagrees with the
  media kind is refused rather than misfiled, and path separators and control characters are
  stripped so a record name cannot escape the album. Images and videos share one `DCIM/iCloud`
  bucket because that is where gallery apps expect camera-grade media. `PhotoLibraryExportTest` is
  the oracle.
- `ui/photos/PhotoGalleryExport.kt` copies those bytes verbatim through the shared scoped-storage
  writer with the capture time as `DATE_TAKEN`. A same-name, same-size entry already in the album is
  reported as `AlreadySaved` instead of inserted, so re-opening a photo does not accumulate copies.
  On API 26-28 a missing legacy storage grant returns `PermissionRequired` rather than failing
  silently; the viewer's save action then requests it.
- `PhotosViewModel` mirrors on the transition a download makes to `Succeeded`, through the fakeable
  `PhotoGalleryPort`, and records the per-asset outcome in UI state. A restored, already-validated
  cache file does not download or mirror again. `PhotosViewModelTest` proves one mirror per
  download and that the export never touches the Apple write path.
- The viewer also shares the cached original directly through the existing FileProvider grant, so
  sending does not depend on the gallery copy, and keeps an explicit save action for a download that
  predates this slice or whose mirror failed.
- The export is deliberately not a two-way link. Nothing observes the Android gallery, an exported
  copy carries no iCloud identity, and re-uploading it would still require the explicit picker or
  folder staging plus a separate upload tap. Editing or deleting the gallery copy does not change
  iCloud.

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
- `upload_photo_jpeg(original_path, preview_path, filename, captured_at_ms, orientation,
  fallback_time_zone)` is committed for an explicitly staged JPEG pair and returns the CPL
  master/asset identifiers. Rust derives the capture date, zone, location, and camera metadata
  from the file's EXIF; the caller's instant and `UPhotoTimeZone` only fill what the file omits.
- `download_photo_original(master_id, media_kind, destination, progress)` is committed for an
  explicitly selected original. A later resource method must still cover Live Photo motion pairs
  and return verified size/checksum information.

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

This slice is live-proven on the Pixel/account recorded above. Other account states and ADP still
need separate evidence. Do not add scheduled work merely to make the experimental screen look
like sync.

### Slice 2: explicit resource download

1. Download a small image/video preview to app-owned temporary storage. Image preview download is
   live-proven; the sampled page contained no videos, so small-video proof remains pending.
2. Download an original still image and video. The foreground path is implemented; live proof is
   still pending.
3. Support and verify both components of a Live Photo.
4. Atomically promote completed files; clean up partial files on cancellation/failure. The shared
   coordinator is implemented for previews and originals.
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

1. The opt-in camera backup worker is scheduled (hourly periodic plus a MediaStore content trigger)
   with connected-network, battery-not-low, and storage-not-low constraints and uploads only newer
   `DCIM` images. Incremental *download* reconciliation of remote changes remains future work.
2. APS notifications may mark Photos dirty and enqueue work; never perform a library sync inside
   the APS callback or message poll.
3. Use foreground transfer behavior for user-initiated long downloads where Android requires it.
4. Keep Photos failures isolated from iMessage receive and Messages-in-iCloud history sync.

### Slice 5: mutations

The explicit JPEG upload milestone is the only remote mutation currently exposed. It has durable
device evidence through MMCS, the atomic CPL record commit, refreshed metadata, the persisted
success receipt, and byte-identical export from native macOS Photos. Keep all broader mutation
work blocked:

- HEIC, video, and Live Photo uploads or automatic gallery observation;
- album creation/membership changes;
- favorite, hidden, edit, and metadata writes;
- local/remote deletion with explicit conflict policy;
- Shared Photo Library participation.

Do not expose mutation methods speculatively in the initial UniFFI API.

## Safety rules

- Read-only is the default except for the JPEG-only upload path: a separate tap in the uploads
  sheet, or the user's own "Back up new camera photos" switch, which only ever adds images.
- Missing local data is never evidence that a remote asset should be deleted.
- Deleting an app cache entry must never delete an iCloud original.
- Remote tombstones may remove catalog/cache state, but not user-exported MediaStore copies.
- Gallery export is one-way. The device gallery is never observed, and an exported copy must never
  become an implicit upload source.
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
./gradlew :core:test :app-native:testDebugUnitTest \
  :app-native:checkUniffiBindings :db:checkModelParity \
  :app-native:assembleDebug --console=plain
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
