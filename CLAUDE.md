# OpenBubbles repository guidance

The shipping rewrite is Kotlin + Rust. Start with:

- `README.md` for setup and verified commands.
- `docs/ARCHITECTURE.md` for module and runtime boundaries.
- `CONTRIBUTING.md` for persistence, submodule, and verification rules.
- `tools/CUTOVER.md` for release gates that still require a device.

Use JDK 21. Keep ObjectBox model parity green. Do not commit credentials,
provisioning state, signing keys, build outputs, or private APNs fixtures.
Historical Flutter documents are reference material only and must not be used as
the implementation architecture.
