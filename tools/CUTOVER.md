# Native release-readiness checklist

The Flutter application code has been removed and the Kotlin/Rust client owns
the shipping application ID. This file now tracks release evidence, not the
already-completed mechanical cutover.

## Automated gates

- [x] Native Gradle root builds with JDK 21.
- [x] Android application ID is `com.openbubbles.messaging`.
- [x] Android store path targets the legacy app data directory.
- [x] ObjectBox model parity is enforced by `:db:checkModelParity`.
- [x] Database, core, and Android JVM tests run in native CI.
- [x] Fixture-free `rustpush` library tests run in native CI.
- [x] Debug APK assembles for arm64 and x86_64.
- [x] Android lint and release-variant AAB compilation run in native CI.
- [x] Boot and package-replaced receiver can start the native push service.
- [x] Poll-mode intent is applied before Rust initialization.
- [x] OpenAbsinthe rejects missing or incompatible native backends explicitly.
- [x] Production native builds package the version-pinned on-device engine.
- [x] Account-free device smoke test completes Apple validation and returns a
      517-byte envelope without using the hosted relay.
- [x] SIM-chat attachments route through Android carrier MMS rather than MMCS.
- [x] The manifest qualifies for Android's default-SMS role and Settings exposes
      the role needed for carrier MMS download/ingest.
- [x] Opening a chat, notification reply, and notification mark-read clear the
      local unread state and emit correctly routed private/public Apple read
      receipts according to the user's privacy setting.
- [x] Incoming tapbacks/custom reactions post readable notifications and honor
      the reaction-notification preference.

## Android device acceptance

- [ ] Fresh self-hosted provisioning succeeds using an `OABS` Mac QR/payload.
- [ ] Apple ID password login succeeds on the release candidate.
- [ ] Trusted-device 2FA succeeds and registration writes usable account state.
- [ ] SMS 2FA fallback, phone selection, and code submission succeed.
- [ ] Account-update/terms and Apple-blocked registration paths are exercised.
- [ ] Text send/receive works for direct and group iMessage conversations.
- [ ] SMS and MMS send/receive works with required permissions.
- [ ] Attachment upload/download works for supported media types.
- [ ] Notification delivery, grouping, privacy, deep links, reply, and mark-read work.
- [ ] Reboot and package replacement restore background receiving.
- [ ] Live mode receives after the UI has been backgrounded for an extended period.
- [ ] Battery-saver mode polls, posts new messages, and tears down the service.
- [ ] A 24-hour live-mode battery sample shows acceptable drain.
- [ ] Upgrade from a real Flutter-era backup preserves chats and attachments.
- [ ] Sign-out and fresh sign-in clear/rebuild the correct native state.

## Product parity

- [x] Send reactions/tapbacks, threaded replies, edits, and unsend from Android UI.
- [x] Rename groups, edit group photos, and add/remove participants.
- [x] Pin, archive, delete, and mute conversation controls.
- [x] Add temporary mute presets with legacy-compatible expiry evaluation.
- [x] Open/share downloaded video, audio, and generic files with Android handlers.
- [ ] Add dedicated in-app audio/video playback controls.
- [ ] Instrumentation or journey coverage for login, service, worker, receiver,
      notification, and upgrade flows.

## Desktop release work

- [ ] Migrate existing desktop data into the native desktop data directory.
- [ ] Verify login and history rendering on Windows with a real account.
- [ ] Add contacts, new-chat, attachments, message actions, and group controls.
- [ ] Add desktop tests.
- [ ] Align desktop package version with Android.
- [ ] Produce and verify signed Windows packaging.

## Release artifacts

- [ ] Build and install the signed release APK/AAB using the production key.
- [ ] Verify version-code continuity and in-place upgrade signature.
- [x] Add release-variant artifact and lint gates to CI (CI AAB is debug-signed).
- [ ] Measure and reduce release package size where practical.
- [ ] Complete store listing, privacy disclosure, signing backup, and rollback plan.

Do not merge or publish a release solely because the automated gates pass. The
unchecked device, migration, signing, and packaging items require direct evidence.
