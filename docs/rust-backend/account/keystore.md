# Keystore

Part of the [Rust backend reference](../README.md). Related:
[state files](../foundations/state.md) (durability/quarantine),
[changes.md](../changes.md#change-the-keystore-contract).

Rust keys never live in plaintext on disk except inside the Android Keystore / desktop
software keystore state.

- `setupKeystore(dir, NativeKeystore)` picks the backend: if `keystore.plist` exists →
  hardware-backed `BackupKeystore`; else if `keystore_s.plist` exists → software; else
  it *probes* the hardware implementation (`supports_import`: RSA import + P-384
  ECDH derive) and initializes accordingly. An unparsable state file is quarantined,
  never regenerated (see [state files](../foundations/state.md#durability-rules)).
- The foreign trait `NativeKeystore` (implemented by `AndroidNativeKeystore` in
  `app-native/`, package `com.bluebubbles.messaging.services.rustpush`) exposes
  create/destroy/list/import/sign/verify/public-key/derive/encrypt/decrypt. Rust-side
  `keystore.rs` wraps imports in an ASN.1 `KeyWrapper` (RSA-OAEP-wrapped AES-GCM
  transport key + keymaster `AuthorizationList`) so private keys transit as ciphertext.
- Locking: `isLocked()`, `finishUnlock()`, `doLock()`, `recoverKeychain()`; the Android
  side gates unlock behind biometrics and `RustBoot.unlockKeystore` bridges the prompt.
- Key alias registry (what lives where):

| Alias | Type | Purpose |
|---|---|---|
| `activation:{serial}` | RSA-1024 (SHA-1/PKCS1) | Albert push certificate |
| `ids:{user_id}` | RSA-2048 | IDS auth CSR / registration keypair |
| `keychain:signing:{mid}`, `keychain:encryption:{mid}` | EC P-384 | Octagon peer identity |
| `keychain:cloudkey-access-key:{dsid}` | secret (64 B) | unwraps synced CloudKeys |
| `gsa:password` | AES-256-GCM | `gsa.plist` password at rest |
| `ids:identity-storage-key:{tag}` | AES | NGM identity serialization |

`rustpush/keystore/` defines the traits; `backup.rs` is the hybrid
hardware-keystore + state-file implementation; `software.rs` the desktop one (whose
`SoftwareEncryptor` key is a fixed literal — desktop is best-effort, Android is the
hardened path).
