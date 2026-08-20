# Retired Flutter client reference

This directory contains documentation for the retired Flutter/GetX client. It is historical
reference only and must not be used as the implementation architecture for the shipping app.

The active Android and desktop clients are Kotlin + Rust. Open `native/` in Android Studio and use
the current documentation under `docs/` when changing product behavior.

## Contents

- `docs/DECISIONS.md` — retired Flutter architecture decisions.
- `docs/COMMON_TASKS.md` — retired GetX/Dart implementation recipes.
- `docs/MESSAGE_RECEIVE_FLOW.md` and `docs/MESSAGE_SEND_FLOW.md` — retired server-client flows.
- `docs/models.md` — retired Dart/ObjectBox model notes.

The original Flutter application source and Cargokit build tooling remain available through Git
history rather than the working tree. The `telephony_plus` submodule is the deliberate exception:
the native Gradle build consumes only `telephony_plus/android-smsmms/library` for Android MMS.
