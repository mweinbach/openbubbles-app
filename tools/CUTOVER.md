# Native cutover runbook (M4)

This is the checklist for retiring the Flutter app and making the native
(Kotlin+Rust) client the shipping OpenBubbles app. **Do not run this until
the native app has been daily-driven and verified on a real device with a
real Apple ID.** Every step is one commit so any of it is revertible.

## Preconditions
- [ ] Native app verified: login (incl. SMS 2FA + terms), send/receive text
      + attachments, notifications, group ops, typing, edits/unsend
- [ ] Battery-life check: foreground service running for 24h without
      excessive drain
- [ ] Desktop (Windows) build opens the same account and renders history

## Store compatibility (the critical invariant)
The native app opens the Flutter app's ObjectBox files **iff**
`db/objectbox-model.json` stays byte-identical to
`lib/generated/objectbox-model.json`. Guarded by `:db:checkModelParity`
(wired into native CI). If this ever breaks, stores must be migrated via
backup/restore instead of in-place open — do not discover this at cutover.

The Flutter app writes its store under its applicationId data dir
(`com.openbubbles.messaging` for the `prod` flavor). For the in-place
upgrade path the native app must ship with the SAME applicationId
(see step 2), and its store dir must point at the same location the
Flutter app used:
`context.filesDir/../` — the Flutter app used
`getApplicationDocumentsDirectory()` (= `/data/data/<pkg>/app_flutter` on
Android via path_provider) while `Db.build` currently takes
`getExternalFilesDir`. **Before cutover**, point the native store at the
Flutter location (copy-on-first-run is also acceptable: open fresh, then
copy the four `data.mdb/lock.mdb` files in if absent — ObjectBox handles
the rest) and TEST with a real backup from the Flutter app.

## Steps (one commit each, on the `m4-cutover` branch)
1. `git checkout -b m4-cutover`
2. app-native `defaultConfig`: `applicationId "com.openbubbles.messaging"`
   (drop the `.native` suffix), versionCode continuing the Flutter app's
   (`20002000 + N` scheme in android/app/build.gradle), versionName match.
   Keep `namespace app.openbubbles.nativeapp` (namespaces are independent).
3. Release signing: reuse `android/key.properties` +
   `android/keystore/openbubbles-release.jks` in app-native's release
   block (both gitignored; regenerate if lost — but then it is NOT an
   in-place upgrade for users signed with the old key).
4. Store-dir switch per the compatibility section above + migration test
   against a real device backup.
5. Launch cutover: make NativePushService start on boot
   (BOOT_COMPLETED receiver — port `BootReciever.kt` pattern), launcher
   activity already native.
6. The deletion commit: remove `lib/`, `android/` (Flutter host),
   `rust_builder/` (FRB plugin — keep `rust_builder/cargokit`! the native
   build shells into it), `.fvmrc`-adjacent flutter config from CI,
   `flutter_rust_bridge.yaml`, Flutter deps from pubspec (delete pubspec
   entirely once nothing references it), and the 82 MB
   `android/app/src/main/jniLibs/**/libnative_lib.so` — wait: those live
   under `android/` which step 6 deletes wholesale; the uniffi bindings
   file also lives under `android/app/src/main/kotlin/uniffi/` — MOVE it
   to `core/src/main/kotlin/uniffi/` (or a dedicated module) BEFORE
   deleting `android/`. Also move `tools/gen_db_entities.py`'s seed model
   copy somewhere stable (it reads lib/generated/objectbox-model.json —
   copy that file to db/seed first).
   Update: `native/settings.gradle` srcDir aliases, CI workflows
   (build.yml dies; native.yml becomes the only build), README.
7. Merge `m4-cutover` -> `main` only after device verification.

## Post-cutover cleanup backlog
- SMS/MMS telephony (or explicitly drop the feature)
- CloudKit history backfill on fresh installs
- FindMy / FaceTime / posters / passwords UI if not done by then
- Windows packaging polish (MSIX signing, Inno Setup)
