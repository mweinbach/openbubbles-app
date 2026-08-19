---
name: openbubbles-native-library-compat
description: Investigate or fix native Android library provenance and compatibility, including 16 KiB ELF or RELRO warnings, APK alignment, linked binary origins, or safe repacking. Do not use for ordinary Cargo or Gradle compilation failures.
---

# OpenBubbles Native Library Compatibility

Separate packaging evidence from binary evidence, then trace ownership before changing a library.

## Classify each failing artifact

Read [../../../docs/DEVELOPMENT.md](../../../docs/DEVELOPMENT.md) and the build section of [../../../docs/RUST_KOTLIN.md](../../../docs/RUST_KOTLIN.md). For every reported `.so`, record ABI, APK path, hash, and one owner:

- built from this repository by Cargo/NDK;
- supplied by a Maven/AAR dependency;
- checked-in or extracted vendored artifact.

APK ZIP alignment and ELF `PT_LOAD`/`GNU_RELRO` alignment are different checks. Verify both rather than treating one green result as proof of the other. A debuggable-device warning may be proactive even on a 4 KiB kernel; inspect the artifact instead of waiting for a crash.

Prefer rebuilding project-owned code with the pinned NDK and upgrading the owning dependency for third-party code. Do not patch an arbitrary binary until provenance and invariants are known.

## Vendored or recovered code

Capture the source release/path, SHA-256, wrapper or hard-coded offset coupling, toolchain, and functional oracle. Search recursive submodule refs, sibling clones, archives, lockfiles, and unreachable objects before reverse engineering.

For OpenAbsinthe provenance or source recovery, read [../../../rustpush/open-absinthe/RECOVERY.md](../../../rustpush/open-absinthe/RECOVERY.md). Recover behavior in bounded stages and prove each stage with fixtures or a differential oracle. Do not replace a white-box/custom circuit with familiar cryptography based only on superficial resemblance.

Use `tools/repack_elf_16k.py` only when its documented ELF invariants apply. Verify segment congruence, final APK ZIP alignment, both supported ABIs, and absence of unintended project-owned precompiled libraries. For OpenAbsinthe only, packaging/load success does not prove Apple validation; that requires the hardware oracle, currently the account-free 517-byte validation envelope.

Do not call a build source-only while any executed boundary still comes from an opaque project-owned artifact. If equivalence is not proved by a reproducible oracle and a fresh artifact inventory, state the unrecovered boundary and keep its provenance pinned.

Never commit identity material, replay traffic, certificates, or an oracle containing live account state. Report separately what packaging proves, what unit/differential tests prove, and what remains device-only.
