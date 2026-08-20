---
name: openbubbles-apple-native-parity
description: Compare a native macOS or iOS Apple client's installed schemas, runtime protocol descriptors, focused behavior, and exported artifacts with OpenBubbles, then implement the smallest Kotlin/Rust parity slice. Use for private Apple-service compatibility work; not ordinary Android UI changes or public Android API lookup.
---

# OpenBubbles Apple Native Parity

Use Apple's native client as an evidence oracle, not as an implementation to copy. Read
[../../../docs/DEVELOPMENT.md](../../../docs/DEVELOPMENT.md) and
[../../../docs/RUST_KOTLIN.md](../../../docs/RUST_KOTLIN.md) before editing, plus the feature's
current implementation document. For personal Photos, also load
[../openbubbles-photos-sync/SKILL.md](../openbubbles-photos-sync/SKILL.md) and read
[references/photos-upload-case-study.md](references/photos-upload-case-study.md).

This skill does not authorize device interaction, account changes, remote writes, retries, or
collection of private Apple data. Keep the user's scope and the feature skill's write boundary.

## Define one parity question

Anchor a single native behavior before inspecting or editing:

- the exact Apple app, macOS/iOS version, service, action, and resulting artifact;
- the OpenBubbles branch, root commit, recursive submodule commits, device/app version, and time
  window;
- the first disagreement, such as a field name, protobuf shape, protection profile, state
  transition, error category, file hash, or cross-device visibility result;
- what is observed, inferred, and unknown.

Do not start with “match the native app.” Choose one bounded transition, such as “a newly saved
record can be fetched and decrypted by native Photos.” Prefer a read-only probe. For a remote
write, name a disposable asset and exact operation, preserve its hash/size, and obtain explicit
authorization immediately before the write.

## Build the native evidence ladder

Use the least invasive oracle that can settle the question:

1. **Installed declarative resources.** Inspect schemas, plists, entitlements, protobuf
   descriptors, and public symbol metadata already installed on the Mac. Record the OS build and
   resource hash because private contracts can drift between releases.
2. **Existing OpenBubbles code.** Locate reusable CloudKit, PCS, MMCS, APS, account, and service
   lifecycle behavior before introducing a new transport or crypto path.
3. **Focused native runtime signal.** Reproduce one action and capture only the relevant native
   process/subsystem/category and a short time window. Stop when the first precise mismatch is
   visible.
4. **Resulting native artifact.** Query the native client for exactly one disposable item and,
   where possible, export the unmodified original. Compare name, size, dimensions, media header,
   and SHA-256 with the source.

For installed plist schemas, use read-only inspection such as:

```bash
find /System/Applications/Photos.app /System/Library/PrivateFrameworks \
  -name 'CPLSchema-com.apple.photos*.plist' -print 2>/dev/null
plutil -p /absolute/path/to/schema.plist
shasum -a 256 /absolute/path/to/schema.plist
```

Search and report only the names/types needed for the parity question. Do not commit or publish
Apple binaries, installed private-framework resources, extracted descriptors, account records,
or decompiled implementation text. A native resource is evidence about the contract, not a repo
dependency.

Focused unified logs may still contain filenames, identifiers, URLs, receipts, or key material.
Use a process plus subsystem/category/message filter and a short `--last` interval; never dump all
Photos, `cloudd`, or app-process logs into the repository or handoff. Redact identifiers and
discard the raw capture after extracting a byte-free finding. Stop immediately if credentials,
keys, message content, signed URLs, or unrelated personal data appear.

## Translate evidence into an ownership matrix

Map each native fact to the narrowest OpenBubbles owner before changing code:

| Native fact | OpenBubbles owner | Required proof |
|---|---|---|
| Record types, field spelling, references, query semantics | `rustpush/src/<service>.rs` | focused record/query unit tests |
| Protobuf field numbers or wrapper/nesting shape | `rustpush/*-proto/` and protocol client | encode/decode fixture tests |
| CloudKit request/response and authorization preservation | `rustpush/src/icloud/cloudkit.rs` | request/response contract tests |
| PCS AAD, key wrapping, or MMCS profile/signature | `pcs.rs` / `mmcs.rs` | crypto integrity and profile tests |
| Kotlin-visible async operation or record | `rust/src/uniffi_ext.rs` | regenerated bindings and parity gate |
| Paging, retry, idempotence, cache, and durable intent | `core/` | fake-port state-machine tests |
| Android picker, filesystem, MediaStore, worker, or UI | `app-native/` | Android unit/package/screenshot proof |

Keep protocol facts in Rust. Raw native records, tokens, URLs, change tags, descriptors, and key
material do not cross UniFFI. Keep Android types out of `core/`; do not touch the compatibility
ObjectBox model merely to make a protocol probe persistent.

If evidence affects multiple services, make the generic transport/crypto change at its real owner
and add regression coverage for existing consumers. Do not hide a Photos-only rule inside generic
CloudKit or MMCS code without an explicit profile/type.

## Implement one vertical slice

Work from the protocol boundary outward:

1. Encode the smallest Rust-side request/record needed to test the observed native fact.
2. Preserve server-returned authorization and nested asset structures instead of reconstructing
   them from assumptions.
3. Add deterministic protocol tests using synthetic or sanitized fixtures; never commit live
   records or replay traffic.
4. Add or adjust the async UniFFI method only when Kotlin must observe or initiate the behavior,
   then regenerate committed bindings.
5. Put cancellation, retry, cursor commit order, idempotence, and durable intent behind a fakeable
   `core/` port.
6. Add Android staging/UI only after the lower contract is testable. Separate local selection or
   staging from the explicit remote action.

Keep stage markers distinct:

```text
local selection/staging
  -> transport accepted bytes
  -> protection/authorization encoded
  -> atomic cloud record save
  -> native client change batch fetched
  -> native client decrypted/rendered the resource
  -> unmodified native export matched the source
```

Success at one stage does not prove the next. Patch the first disagreement and rerun the same
single-item oracle before widening the feature. If a failed write leaves disposable records, do
not silently delete or repair them outside the user's authorization.

## Verify the implementation and the oracle separately

Run the affected host gates from the repository root:

```bash
cargo test --manifest-path rustpush/Cargo.toml --lib --locked
(cd rust && ./build-uniffi.sh)  # only when the Kotlin-visible contract changed
./gradlew :db:test :core:test :app-native:testDebugUnitTest \
  :db:checkModelParity :app-native:checkUniffiBindings \
  :app-native:assembleDebug --console=plain
```

Add `./gradlew :app-native:validateDebugScreenshotTest --console=plain` for Compose changes and
classify unrelated renderer differences rather than updating their goldens.

Native-client evidence is a separate tier. Record the Apple OS/app build, Android device serial,
OpenBubbles version/code and APK hash, exact disposable input hash/size, time window, stage
reached, first native error, and output hash/size. A green host gate cannot prove native-client
acceptance; native UI appearance alone cannot prove byte integrity.

## Stop conditions

Stop and narrow the work when:

- the installed schema/runtime evidence differs across Apple OS builds and the active contract is
  not established;
- the first native error is downstream of an unproven earlier stage;
- a retry would create, mutate, repair, or delete another remote record without authorization;
- useful diagnostics require exposing credentials, keys, signed URLs, receipts, raw records, or
  unrelated personal data;
- the requested slice would silently expand into background sync, deletion, or broad mutation.

Commit and push changed leaf submodules first, then update the root pointer. In the handoff,
separate native observations, inferred protocol rules, code changes, host gates, native/device
proof, remaining unknowns, and exact root/submodule commits.
