# Services — native

Android lifecycle, notifications, SMS, and WorkManager live in `app-native/`. Shared messaging
behavior lives in `core/`. Follow [../../docs/ARCHITECTURE.md](../../docs/ARCHITECTURE.md).

For a device-observed login, receive, or delivery mismatch, load
`../../.agents/skills/openbubbles-live-flow-triage/SKILL.md`. Treat account/2FA, APNs transport,
IDS registration, Rust journal, Kotlin ingest, persistence, and UI as separate observable states.

This file used to describe GetX services and Dart isolates. Those rules are void.
