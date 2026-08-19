# API — native

There is no BlueBubbles HTTP/socket client. Apple traffic goes through UniFFI (`rust/` +
`rustpush/`). Follow [../../docs/RUST_KOTLIN.md](../../docs/RUST_KOTLIN.md).

For a Kotlin-visible Rust API, type, event, callback, or sync/async semantic change, load
`../../.agents/skills/openbubbles-uniffi-contract-change/SKILL.md` and regenerate bindings; never
hand-edit the generated Kotlin.

This file used to describe Dio + Dart isolate Interface→Action. Those rules are void.
