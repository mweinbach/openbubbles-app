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
- [x] Manual native workflow packaging assembles the arm64 debug APK.
- [x] PR/push native CI runs Android lint; manual package dispatch compiles the release AAB.
- [x] Boot and package-replaced receiver can start the native push service.
- [x] Poll-mode intent is applied before Rust initialization.
- [x] OpenAbsinthe constructor, key establishment, and signing use the recovered
      source path without loading a compatibility backend.
- [x] Production native builds compile the Rust engine with Cargo and package no
      project-owned precompiled Android library.
- [x] SIM-chat attachments route through Android carrier MMS rather than MMCS.
- [x] The manifest qualifies for Android's default-SMS role and Settings exposes
      the role needed for carrier MMS download/ingest.
- [x] Opening a chat, notification reply, and notification mark-read clear the
      local unread state and emit correctly routed private/public Apple read
      receipts according to the user's privacy setting.
- [x] Incoming tapbacks/custom reactions post readable notifications and honor
      the reaction-notification preference.
- [x] Incoming Focus status updates preserve each direct recipient's
      notification-silenced state.

## Android device acceptance

Record each run using the device/artifact/scenario evidence fields in
[docs/DEVELOPMENT.md](../docs/DEVELOPMENT.md#6-capture-hardware-evidence). An unchecked item stays
unchecked until direct hardware evidence exists.

- [ ] The combined source-only OpenAbsinthe path completes account-free Apple
      validation and returns a 517-byte envelope on a 16 KB device build.
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

## Self-update acceptance (Update Ledger)

Publishing mechanics, signing, and the changelog-section notes convention live
in [docs/RELEASES.md](../docs/RELEASES.md). Version 3.4.7 was the final
GitHub Releases bridge; current versions publish exclusively to Update Ledger
and clients have no GitHub Releases fallback.

- [ ] Publish path: the `Self-update release` GitHub Action, triggered by a
      version-bump push to `main` or an explicit manual dispatch, first
      verifies that `Native (Kotlin+Rust)` succeeded for its exact source
      commit. It then publishes the production-signed APK and matching JSON /
      Sparkle appcast feeds to the Update Ledger `openbubbles` stable channel;
      a missing/failed exact-commit validation or non-increasing build aborts
      without publishing.
- [ ] Immutable release evidence: the workflow source commit, Update Ledger
      project/channel/version/build, artifact filename and byte count, public
      appcast URL, SHA-256, and downloaded APK all agree. The APK package,
      version, and production signing certificate match the installed app.
- [ ] Keystore continuity: `android/release.jks` + `android/key.properties` are backed up
      off-machine (they are gitignored; GitHub Actions secrets hold a copy). Losing the key
      breaks in-place updates for every installed device.
- [ ] CI secret hygiene: `KEYSTORE_*` and `UPDATE_LEDGER_API_KEY` secrets
      exist, deploy keys on the private submodules are read-only, the Ledger
      project key is confined to publication, and the repo's own release
      workflow cannot be triggered by fork PRs.
- [ ] Instant release notice: an installed client receives the stable-channel
      Firebase wake-up and performs an expedited Ledger appcast check. If
      Firebase delivery fails, use **Update Ledger → OpenBubbles → Manage →
      Notify devices**; the release and normal polling remain valid.
- [ ] First self-update on hardware: background check downloads and verifies the APK, the
      "Install unknown apps" grant flow works, the system install confirmation appears, and the
      update installs in place.
- [ ] Data survives a self-update: chats, attachments, and the ObjectBox store at
      `{dataDir}/app_flutter/objectbox` are intact; the push service restarts via
      `MY_PACKAGE_REPLACED`.
- [ ] Every later self-update also presents Android's installation confirmation;
      installer-of-record status never permits a silent install.
- [ ] A corrupted APK (mismatched SHA-256 in the feed) is refused and cleaned up.
- [ ] A malformed, oversized, or undownloadable advertised build cannot
      advance the persisted rollback floor; a fully verified newer APK does
      advance it and blocks subsequent lower-build advertisements.
- [ ] A feed versionCode at or below the installed one is ignored as "up to date";
      "Skip this version" hides the notification until the next release.

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
- [x] Add lint to PR/push CI and debug-signed release-variant artifacts to manual package dispatch.
- [ ] Measure and reduce release package size where practical.
- [ ] Complete store listing, privacy disclosure, signing backup, and rollback plan.

Do not merge or publish a release solely because the automated gates pass. The
unchecked device, migration, signing, and packaging items require direct evidence.
