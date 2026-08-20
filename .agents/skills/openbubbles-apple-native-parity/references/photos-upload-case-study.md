# Photos upload native-parity case study

Read this when reproducing or extending the 2026-08-19 personal iCloud Photos work. It records the
reasoning path and evidence hierarchy; it is not current device proof and does not authorize a new
upload.

## 1. Establish the real service boundary

OpenBubbles already supported Shared Albums through Shared Streams, but the personal library used
the private `com.apple.photos.cloud` CloudKit container, `PrimarySync`, and CPL records. The team
therefore treated personal Photos as a separate engine and started with bounded metadata and
explicit preview downloads rather than reusing Shared Albums' folder reconciler or deletion
semantics.

The first read-only slice established reusable ownership:

```text
macOS Photos / CPL behavior             native oracle
CloudKit + PCS + MMCS + CPL records     rustpush/
async Kotlin-visible facade             rust/src/uniffi_ext.rs
paging, staging, retry, persistence     core/
SQLite catalog, picker, gallery, UI     app-native/
```

## 2. Use installed macOS resources for static contract facts

The installed Photos private framework contained these read-only schema resources on the test Mac:

```text
/System/Library/PrivateFrameworks/CloudPhotoLibrary.framework/Versions/A/Resources/
  CPLSchema-com.apple.photos.asc.e2ee.plist
  CPLSchema-com.apple.photos.asc.e2ee.secure.plist
```

`plutil -p` inspection supplied exact schema spelling and encryption mappings. It corrected
assumptions that ordinary case conversion would have produced:

- `assetHDRType`, not a lowercased `Hdr` variant;
- `fullSizeJPEGSource` and the `resJPEGThumb*` family preserve acronyms;
- `importGroupId` is the exceptional lowercase-`d` spelling;
- the asset-to-master relationship is an owning CloudKit reference.

Only field names and wire/schema types were carried into the implementation. The installed plists
were not copied into the repository.

## 3. Build the smallest explicit write

The first upload implementation accepted a separately authorized JPEG plus a generated preview.
Android selection only staged content-addressed, fsynced files and a metadata sidecar; a later tap
crossed the remote-write boundary.

Rust then:

1. validated the original/preview media and dimensions;
2. uploaded both resources in one MMCS v2 batch using the Photos 16-byte FORD profile and wire
   version `0x03`;
3. RFC 3394-wrapped each resource key with its new CPLMaster record protection;
4. encrypted protected metadata using the field name alone as PCS AAD;
5. submitted `CPLMaster` and `CPLAsset` together with zone isolation.

The initial implementation landed as rustpush `8345086` and app `864263c`. Host tests proved its
local contracts, but that did not prove that Apple's native Photos client could consume it.

## 4. Let the native client identify the first disagreement

Early authorized probes reached MMCS and CloudKit but native Photos could not unwrap/render the
uploaded resources. The comparison used three independent native facts rather than guessing from
the Android success state.

### Runtime protobuf shape

Runtime inspection of macOS CloudKitDaemon protobuf descriptors showed that the asset-upload
response was a nested `CKDPAsset` plus a wrapper token and expiration. It was not an
`AssetUploadData` containing a base64-encoded inner protobuf. The same descriptor established
`CKDPAsset.clearAssetKey` as field 20.

The repair preserved Apple's returned nested owner, URLs, requestor, record identifier,
signatures, and authorization fields, then mapped the wrapper token into the asset download token.
Reconstructing only the fields OpenBubbles thought it needed had discarded authorization state
required by the native client.

### Resource fingerprints

Focused native Photos diagnostics then reported a fingerprint scheme mismatch: the client received
MMCSv2 where it expected MMCSv1. This did not mean the transfer profile itself should be downgraded.
It exposed two different signatures with different jobs:

- the CPLMaster record name uses the FORD reference signature, prefix `0x01`;
- `resOriginalFingerprint` and `resJPEGThumbFingerprint` use each resource's total asset
  signature, prefix `0x04`.

The corrected protocol implementation landed as rustpush `d075908`; the parent documentation and
pointer repair landed as app `e0023c3`.

### Security cleanup during observation

The focused trace exposed pre-existing PCS/MMCS debug statements that included key or raw receipt
material. Those logs were removed. Kotlin-facing errors were reduced to fixed local diagnostics or
safe Apple enum names. Raw native captures, records, receipts, and protection material were not
committed.

## 5. Prove the whole chain with an artifact oracle

After the protocol repair, one fresh disposable `218.jpg` upload on Pixel 10 Pro Fold
`58201FDCG003BG` reached all stages:

- OpenBubbles staged/uploaded 43,716 bytes across the original and preview path;
- the source original was a 34,386-byte, 1,600-by-1,200 JFIF JPEG;
- source SHA-256 was
  `4bc8da09ee6aa4765c11f8efba533862932ec596f0d8f5fd22355e9b040d4ed8`;
- native macOS Photos found exactly one item named `218.jpg`;
- “Export Unmodified Original” produced the same byte count, dimensions, media type, and SHA-256.

That byte-for-byte export proved this chain:

```text
Android private staging
  -> encrypted MMCS resources
  -> protected atomic CPLMaster + CPLAsset save
  -> native Mac change fetch and PCS/MMCS decrypt
  -> unmodified original export identical to the source
```

It did not prove general two-way sync, background work, deletion, Live Photos, videos, albums,
ADP-account compatibility, or iPhone appearance. Those remained separate evidence targets.

## 6. Extend the product only after protocol acceptance

Once the native client accepted the narrow JPEG contract, the app added protected original
downloads and the foreground gallery/manual-source slice. Those landed as rustpush `ec2de41` and
app `d948d69`. Background worker code remained compile-time disabled, selection/folder scan only
staged files, and every remote upload still required a separate explicit action.

The durable lesson is to preserve the order:

1. static native schema/descriptors establish names and wire shapes;
2. focused native behavior supplies the first mismatch;
3. Rust fixes the owning protocol contract;
4. host tests prove deterministic implementation behavior;
5. one disposable native artifact proves cross-client acceptance;
6. only then should Kotlin persistence and UI widen the supported flow.

See [../../../../docs/PHOTOS_SYNC.md](../../../../docs/PHOTOS_SYNC.md) for current product status,
remaining scope, and later release evidence. Historical hashes and device IDs in this case study
must never be reused as proof for a new build or account.
