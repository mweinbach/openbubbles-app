# Documentation routing and domain vocabulary

OpenBubbles does not use a root `CONTEXT.md`, `CONTEXT-MAP.md`, or `docs/adr/` tree today. Do not
invent those files as a prerequisite or search historical Flutter documents for current decisions.
The current documentation map is [../README.md](../README.md), with repository-wide instructions in
[../../AGENTS.md](../../AGENTS.md).

## Read by owning boundary

| Work | Source of truth |
|---|---|
| Evidence-first loop, shared checkout, findings, handoff | [../DEVELOPMENT.md](../DEVELOPMENT.md) |
| Modules, login, receive/send, background modes | [../ARCHITECTURE.md](../ARCHITECTURE.md) |
| Compose, Navigation3, visual evidence | [../UI.md](../UI.md) |
| Account work, caches, files, retries, cleanup | [../DATA_LIFECYCLE.md](../DATA_LIFECYCLE.md) |
| ObjectBox compatibility and ownership | [../PERSISTENCE.md](../PERSISTENCE.md) |
| UniFFI and Rust/Kotlin boundary | [../RUST_KOTLIN.md](../RUST_KOTLIN.md) |
| Rust backend internals | [../rust-backend/README.md](../rust-backend/README.md) |
| Personal iCloud Photos | [../PHOTOS_SYNC.md](../PHOTOS_SYNC.md) |
| Tests and evidence tiers | [../VERIFY.md](../VERIFY.md) |
| Releases and Update Ledger | [../RELEASES.md](../RELEASES.md) |
| Issue intake and durable specs | [issue-tracker.md](issue-tracker.md) |

Read only the owning documents and directly linked specialist references for the task. When a
cross-layer change touches several contracts, name those contracts explicitly rather than moving
behavior into a convenient module.

## Stable product vocabulary

Use repository terms consistently in issues, tests, comments, and handoffs:

- **OpenBubbles** — the direct Apple messaging client; not a BlueBubbles-over-Mac-server client.
- **Android app / native app** — `app-native/`, Kotlin + Compose + Android lifecycle.
- **core** — Android-free JVM product behavior in `core/`; not a resurrected `:shared` KMP module.
- **database / message store** — the compatibility-sensitive ObjectBox store at
  `{dataDir}/app_flutter/objectbox` on Android.
- **Rust facade** — `rust/`, the application-facing UniFFI boundary.
- **Apple protocols / rustpush** — the `rustpush/` submodule that owns Apple wire behavior.
- **live receive** — APS/IDS-driven receive while the foreground service is alive.
- **poll/history sync** — bounded CloudKit/CKKS work; it is not proof of live IDS registration.
- **staged send** — the durable local outgoing row before transport GUID promotion and echo ingest.
- **host evidence** — deterministic JVM/Rust/build proof without a physical Android/Apple oracle.
- **device evidence** — a recorded artifact, device, OS, scenario, time window, and redacted outcome.
- **release evidence** — immutable workflow/source/artifact/feed/signing facts; separate from current
  `main` and from device installation acceptance.

Do not collapse APNs transport, IDS registration, Cloud history, database ingest, UI projection,
notification display, and recipient delivery into a generic “connected” or “sent” state. The first
missing transition is the useful domain fact.

## Adding or changing documentation

- Update the owning document and add a route in `docs/README.md` or `AGENTS.md` when agents need to
  discover it. Avoid duplicating the same operational rule across several instruction files.
- Link to current source symbols or commit-pinned GitHub lines. Do not make transient line numbers
  the only explanation of a contract.
- Separate current guarantees, historical evidence, proposed work, and hardware-not-yet-proven
  claims. Dates, versions, devices, hashes, and commits belong with evidence snapshots, not timeless
  architecture rules.
- If a future ADR is added, surface conflicts explicitly rather than silently overriding it. Until
  then, the documents above and current tested code/history are the decision sources.
- `legacy/flutter/docs/` is historical reference only. It may explain a compatibility boundary but
  never overrides the Kotlin/Rust architecture or current tests.
