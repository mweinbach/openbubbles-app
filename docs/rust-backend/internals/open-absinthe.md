# open-absinthe: the on-device Apple validation engine

Part of the [Rust backend reference](../README.md). This is a nested submodule of
`rustpush` (`rustpush/open-absinthe/`); see
[submodules](../foundations/submodules.md) for the tree. It is the reason the app can
prove a spoofed Mac identity to Apple **without packaging any official native
library**: constructor, key establishment, signing framing, and the proof circuit are
all source-built.

> The submodule's own top-level `README.md` ("closed source, mock placeholder") is
> stale cover text from an earlier state of the repo. The checked-in source is the
> real recovered engine; `RECOVERY.md` inside the submodule is the authoritative
> documentation.

## What it does

Apple's IDS registration and GSA flows require a one-shot *validation data* blob that
only genuine Apple tooling can normally produce. `MacOSConfig::generate_validation_data()`
(`rustpush/src/macos.rs`, feature `macos-validation-data` — default) fetches the
validation certificate chain from the IDS bag, then drives this crate:

```rust
// rustpush/src/macos.rs (abridged)
let mut ctx = open_absinthe::nac::ValidationCtx::new(&certs, &mut session_info_request, &hardware)?;
// POST session_info_request to the id-initialize-validation bag key
ctx.key_establishment(&session_info_response)?;
let validation_data = ctx.sign()?;   // → X-Mme-Nas-Qualify / id-register validation-data
```

## Module map

| File | Role |
|---|---|
| `nac.rs` | Public API. `HardwareConfig` (the spoofed Mac identity fields) with `from_validation_data(&[u8])` parsing the 517-byte `0x02` envelope produced by a real Mac; `ValidationCtx::{new, key_establishment, sign}` |
| `pear.rs` | PEAR framing transform (384-byte frames) used by the hardware payload and handshake |
| `pear_aes.rs` | The encoded-AES tables used in session decryption (reduced to an affine basis + S-boxes rather than 8 KB of opaque tables) |
| `sign.rs` | Randomized 480-byte signing message → 517-byte outer validation envelope framing |
| `proof_vm.rs` | Architecture-neutral p-code interpreter that executes the recovered signing circuit; `proof_program.b64` holds the op stream + lookup-table ranges |
| `RECOVERY.md` | The recovery map: which historical binary (`libopenbubbles_official.so`, SHA-256 pinned) the code was reconstructed from, address tables, and the rule that partially recovered code stays disconnected until it has an end-to-end oracle |

`HardwareConfig` fields (`product_name`, `platform_serial_number(_enc)`,
`platform_uuid(_enc)`, `root_disk_uuid(_enc)`, `rom(_enc)`, `mlb(_enc)`, …) are exactly
what the [provisioning flow](../account/login.md#provisioning-writes-hw_infoplist)
captures into `hw_info.plist`.

## Errors

`AbsintheError` codes (negative i32): −2 invalid validation data, −3 response tag,
−4 ciphertext, −5/−6 signing state, −8 signing input, −9 certificate, −10 constructor
crypto, −11 proof recovery; −7 `DIFFERENTIAL_MISMATCH` exists only on Android debug
builds.

## Verification hooks

- **Differential gate**: on Android debug builds, `sign()` compares the computed proof
  against the pinned official-library oracle (`dlopen("libopenbubbles_official.so")`,
  symbol anchored by offset); a mismatch fails with `DIFFERENTIAL_MISMATCH`. The
  pinned `.so` is *not* packaged — it exists only as the recovery oracle.
- **Device smoke tests**: `rust/src/lib.rs` exports debug-only
  `openbubbles_debug_nac_round_trip` (built-in fixture hardware config) and
  `openbubbles_debug_nac_round_trip_saved(path)` (this install's saved
  `hw_info.plist`); both return the generated envelope length or a negative error
  code, invoked from ADB receivers — the device evidence tier for validation.
- **Cloud/CI caveat**: compiling `rustpush` on a bare cloud image requires the
  gitignored FairPlay placeholders first — see
  [DEVELOPMENT.md](../../DEVELOPMENT.md#cloudci-fixture-setup).

## Changing this code

Read `RECOVERY.md` first. Every production replacement needs both a readable
implementation **and** an exhaustive/fixture comparison against the pinned oracle; keep
the Android-debug differential gate green. `rust/`'s
`openbubbles_debug_nac_round_trip_saved` result on a real device is the acceptance
evidence for any change here.
