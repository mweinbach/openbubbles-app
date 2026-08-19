# Login, provisioning, and account lifecycle

Part of the [Rust backend reference](../README.md). Prerequisites:
[lifecycle](../foundations/lifecycle.md). Related: [keystore](keystore.md),
[incoming](../messaging/incoming.md) (runtime 2FA approvals).

## Provisioning (writes `hw_info.plist`)

- `provisionFromValidationData(dir, data, extra)` — 517-byte `0x02`-prefixed validation
  envelope extracted from a real Mac, plus `UHwExtra` (macOS version, protocol version
  1660, device id, iCloud/AOSKit UA strings). One-time per install.
- `provisionFromEncoded(dir, encoded)` — the `OABS` QR payload after the magic +
  sharing flag; carries the full config so no extras needed.
- `provisionFromRelay(dir, code, host, token?)` — hosted hardware-relay bridge
  (`hw.openbubbles.app`-style). Supported but **not the default path**; self-hosted
  OABS + on-device validation is.
- `hasHardwareConfig(dir)` gates the login UI's step. Provisioning = fresh NGM identity
  + `setup_push` (Albert activation → push cert/token) + persisted hardware state.
- `repairICloudServices(dir)` — deletes *only* iCloud service files (keychain,
  CloudKit, passwords, Find My, FaceTime, shared streams, StatusKit key) keeping the
  Apple session, IDS registration, and hardware identity. Recovery for service state
  corrupted before writes were atomic: stop the push service, call this, sign in again,
  re-join iCloud Keychain.

## `ULoginSession` — the login state machine

Create with `createLoginSession(dir, delegate)` after provisioning; it fails `NotReady`
if `hw_info.plist` is missing or the stored identity won't decode. All methods are
**synchronous** (`RUNTIME.block_on`) — call from a background thread
(`LoginViewModel` serializes them; `RustLoginHandle` drops the session after a failure
so a poisoned lock cannot brick retries). The delegate fires on the calling thread
before the method returns.

States (`ULoginState`): `LoggedIn`, `NeedsDevice2Fa`, `Needs2FaVerification`,
`NeedsSms2Fa`, `NeedsSms2FaVerification { phone_id, mode }`, `NeedsExtraStep { detail }`,
`NeedsLogin`. Stages (`ULoginStage`): Connecting → Authenticating → AwaitingDevice2Fa /
FetchingSmsOptions / SendingSmsCode → VerifyingCode → RegisteringIds → Finished.

The internal `pump` drives the machine until a state needing user input:

```
session.connect()                     // optional; login() auto-connects (APS + anisette)
session.login(user?, pass?)           // creds lowercase; nulls reuse saved gsa.plist
  → Needs2FaVerification              // trusted-device code path (default)
      session.submit2faCode("123456") // code shown on a trusted Apple device
  → NeedsSms2Fa                       // via requestSmsFallback() or automatic
      getSmsPhoneOptions() → chooseSmsPhone(id)   // single option auto-sends
      session.submit2faCode("987654") // SMS code
  → NeedsExtraStep                    // Apple terms / account update
      getUpdateAccountPage() → show HTML → completeUpdateAccount()
  → LoggedIn
session.register()                    // → Registered (id.plist written) | AppleBlocked{...}
initNative(dir, null, handler)        // rebuild the live state; reloadAfterLogin
```

Details that matter:

- `login` with no credentials resumes the saved session from `gsa.plist`
  (`savedLoginUsername`, `hasSavedUsers`).
- Device 2FA uses a proximity circle session — `on_circle_session(Some(sid))` tells
  Kotlin to advertise the BLE GATT service with that UUID (modern Apple devices refuse
  the join without it); `None` clears the surface.
- `register()` collects the Apple user + any phone users; `AppleBlocked` mirrors
  Apple's support-alert dialog (registration stops until acknowledged). On
  `Registered`, `id.plist` is written — the state must be rebuilt with `initNative`.
- `setNewIdentity()` rotates the NGM identity, resets anisette, and resets the session
  to `NeedsLogin`. `resetConnection()` re-dials APS with a fresh push token (required
  before SMS-gateway phone registration) while keeping account/login state.

## Phone (carrier SMS) registration

Two paths, both storing per-subscription `IDSUser`s in the session:

- **SMS-less (EAP-AKA)**: `getCarrier(handler, mccmnc)` resolves the carrier gateway,
  then `smsLessAuth(subscription, mccmnc, subscriber, imei, UEapAkaHandler)`. The
  handler answers carrier challenges from the Android telephony stack; returning an
  empty string aborts.
- **SMS gateway**: `authPhone(subscription, number, sig)` with the gateway response
  parts (`number|sig`, sig hex-decoded).

Cache phone users with `exportPhoneUsers()`/`importPhoneUser()` (validated against the
live connection; a stale cert returns `false` and the cached entry must be discarded).

## Keypad-style 2FA approvals (runtime)

When another device requests sign-in, the IDMS path in the
[receive loop](../messaging/incoming.md#the-receive-loop-start_loop--recv_wait) builds
an `ActiveCircleSession`; `getAuthCode(txnid)` returns the OTP the user must enter on
the requesting device (or falls back to the anisette 2FA code), `teardown2fa(action,
txnid)` aborts, and `TwoFaAuthEvent` reports the outcome.
