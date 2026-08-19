---
name: openbubbles-live-flow-triage
description: Diagnose device-observed mismatches between displayed Apple messaging state and the actual account, APNs, IDS registration, receive journal, persistence, and UI flow. Use for login, 2FA, incoming receive, or connection-state reports; not outgoing-message status, CloudSync-only, or ordinary Compose work.
---

# OpenBubbles Live Flow Triage

Find the first missing state transition before changing code. A visible status is a projection, not proof that the downstream path works.

## Start with a stable anchor

Read [../../../docs/DEVELOPMENT.md](../../../docs/DEVELOPMENT.md), [../../../docs/ARCHITECTURE.md](../../../docs/ARCHITECTURE.md), and the boot/queue section of [../../../docs/RUST_KOTLIN.md](../../../docs/RUST_KOTLIN.md). Record:

- exact user-visible text or symptom and its time;
- device serial, Android version, package version name/code, and current screen when hardware is in scope;
- root and recursive-submodule worktree state;
- what is observed versus inferred.

For authorized device interaction, also load [../android-cli/SKILL.md](../android-cli/SKILL.md). A diagnosis request alone does not authorize installing an APK, clearing data, signing in, sending messages, or changing settings.

## Trace the live state ladder

Treat every boundary as a separate fact:

1. Apple account/session state, including a pending 2FA or account-update response.
2. APNs transport is connected and the foreground service is alive.
3. IDS registration is current and has usable handles.
4. Rust receives/decrypts the event and journals a pointer.
5. Kotlin receives the callback while the service scope is alive.
6. `MessageIngestor` persists the event and only then calls `completeMessage`.
7. ObjectBox flows drive the chat projection and notification.

Correlate the UI, focused Android/service logcat, persisted Rust diagnostics, and journal retry behavior over the same time window. Locate the first transition without positive evidence. Do not infer full registration from “Connected to Apple push,” message delivery from a running process, or ingestion from a decrypted Rust event.

If a registration refresh fails while APS remains usable, evaluate whether the service can stay degraded-but-live long enough to drain messages. Stopping the service on an account warning can turn a recoverable registration issue into a receive outage.

Keep Messages in iCloud/CKKS recovery separate from live messaging. A CloudSync `Bad message` can coexist with healthy APNs, IDS, and new-message delivery; diagnose it directly without routing through this skill unless the live path is also affected.

## Preserve state and evidence

- Prefer `android layout` before a screenshot; visually inspect any captured image.
- Preserve app data by default. Never uninstall, clear data, discard identity files, or replace the installed build without explicit scope.
- If installing is requested, confirm serial, artifact path, version/signature compatibility, and use an in-place replacement.
- Treat provisioning files, account material, replay traffic, and persisted logs as sensitive. Do not commit them.

## Exit with a falsifiable result

Report the first failing boundary, evidence that adjacent boundaries passed or remain unknown, affected and unaffected product paths, device/build identity, files changed (normally none for diagnosis), and the smallest justified fix plus its remaining hardware check.
