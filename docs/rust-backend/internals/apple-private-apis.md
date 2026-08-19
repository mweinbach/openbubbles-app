# apple-private-apis: vendored Apple authentication crates

Part of the [Rust backend reference](../README.md). This is a nested submodule of
`rustpush` (`rustpush/apple-private-apis/`); see
[submodules](../foundations/submodules.md) for the tree. Consumers:
[login](../account/login.md) (every GSA step), [rustpush internals](rustpush.md).

`rustpush` depends on these via path dependencies; they never build standalone in the
app. `rust/` pins the `remote-anisette-v3` feature, which flows through both crates.

## Layout

| Crate / dir | What it is |
|---|---|
| `icloud-auth/` | Grand Slam (GSA) Apple ID login: `AppleAccount`, the `LoginState` machine, SRP-6a `login_email_pass`, 2FA (`verify_2fa`, SMS via `VerifyBody`), delegates (`LoginDelegate::IDS`/`MobileMe`), PET/token extraction, `TokenProvider` backing |
| `icloud-auth/rustcrypto-srp/` | Vendored fork of the RustCrypto SRP crate (upstream API didn't fit Apple's group/parameter choices) |
| `icloud-auth/src/apple_root.der` | Pinned Apple root certificate used to validate GSA TLS responses |
| `omnisette/` | Anisette header provider abstraction (`AnisetteProvider` trait + `AnisetteClient` caching wrapper) |
| `omnisette/src/aos_kit.rs` | Real macOS provider: dlopen's AOSKit/ADI from the system (only compiled on `target_os = "macos"`) |
| `omnisette/src/remote_anisette_v3.rs` | **The shipping provider**: remote anisette server over websocket (tokio-tungstenite), provisioning state cached on disk |
| `omnisette/src/anisette_clearadi.rs` | ClearADI-backed provider (only compiled with the `remote-clearadi` feature) |
| `omnisette/src/store_services_core/` | Platform state-store glue (`posix_macos.rs`, `posix_windows.rs`) for ADI state directories |
| `clearadi/` | Nested submodule: emulated Apple ADI library. **The checked-in checkout is currently a placeholder stub** (50 lines, no real functionality) — `remote-clearadi` builds against the stub, which is why `rust/` pins `remote-anisette-v3` instead |

## Feature selection

| Feature (set on `rustpush`/`rust/`) | `DefaultAnisetteProvider` becomes |
|---|---|
| `remote-anisette-v3` (active) | `RemoteAnisetteProviderV3` — remote server, websocket provisioning |
| `remote-clearadi` | `ClearADIClient` — local emulated ADI (currently stubbed, see above) |
| neither, on real macOS | `AOSKitAnisetteProvider` — the host's real ADI |

`icloud-auth` forwards the same features to `omnisette` so both switch together.
Anisette provisioning state persists under `<config dir>/anisette_test/` (see
[state files](../foundations/state.md#on-disk-the-config-dir-passed-to-startinit_native));
`reset_anisette` (used by `ULoginSession.set_new_identity`) deletes it to force a
clean re-provision.

## Where it surfaces in the app

- Login: `AppleAccount::login_email_pass` and the whole 2FA state machine (trusted
  device, SMS) — driven through `ULoginSession`
  ([login doc](../account/login.md#uloginsession--the-login-state-machine)).
- Every authenticated Apple request: anisette headers (`X-Apple-I-MD`,
  `X-Apple-I-MD-M`, `X-MMe-Client-Info`, …) plus the validation-data
  (`X-Mme-Nas-Qualify`) proof.
- Token lifetime: `TokenProvider.get_gsa_token`/`get_mme_token` wrap the logged-in
  account for CloudKit, escrow, Find My, and contacts headers.

The validation-data half of that proof is computed by
[open-absinthe](open-absinthe.md).
