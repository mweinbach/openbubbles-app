# Submodules: the full tree, cloning, and pointer workflow

Part of the [Rust backend reference](../README.md). Related:
[lifecycle](lifecycle.md), [changes.md](../changes.md), and the leaf-first push
sequence in [DEVELOPMENT.md](../../DEVELOPMENT.md).

The build needs every submodule checked out — an empty `rustpush/` directory is the
classic symptom of a plain `git clone`.

## The submodule tree

Six repositories across three nesting levels. Pointers pin exact SHAs; the tree is
what a correct recursive clone produces:

```text
openbubbles-app                     (this repo)
├── rustpush/         → mweinbach/rustpush-private          Apple protocols
│   ├── apple-private-apis/ → …apple-private-apis-private   GSA auth + anisette
│   │   └── clearadi/     → …clearadi-private               emulated ADI (stub)
│   └── open-absinthe/  → …openabsinthe-private             validation engine
└── telephony_plus/   → mweinbach/telephony_plus            legacy Flutter plugin
    └── android-smsmms/ → mweinbach/android-smsmms           Java MMS stack
```

All six remotes are private GitHub repositories (CI grants read-only deploy keys for
each). What each provides in the build: `rustpush/` is the entire protocol layer
(see [rustpush internals](../internals/rustpush.md)); `apple-private-apis/` is
vendored into it via path dependencies; `open-absinthe/` compiles into the Rust
library for on-device validation; `telephony_plus/android-smsmms` is the Java MMS
sender for SIM attachments.

## What's inside each submodule

### `rustpush/` — Apple protocols

The entire protocol layer; documented separately in
[rustpush internals](../internals/rustpush.md) (APS, IDS, iMessage, CloudKit, MMCS,
keychain/Octagon, PCS, Find My, FaceTime, StatusKit, passwords, shared streams). It
carries two path crates of its own — `cloudkit-proto/` (CloudKit protobuf
definitions) and `cloudkit-derive/` (the `CloudKitRecord` derive macro) — which live
in the rustpush repo itself, not in the nested submodule below.

### `rustpush/apple-private-apis/` — GSA auth + anisette

Vendored via path dependencies: `icloud-auth/` (Grand Slam Apple ID login, SRP, 2FA
state machine, `TokenProvider`), `omnisette/` (anisette header providers — the
shipping one is `remote-anisette-v3`), and a pinned Apple root certificate. Feature
flags select the anisette provider. Full details:
[apple-private-apis.md](../internals/apple-private-apis.md).

### `rustpush/apple-private-apis/clearadi/` — emulated ADI (currently a stub)

The `remote-clearadi` feature would use this local emulated-ADI provider instead of
the remote anisette server. **The checked-in checkout is a placeholder stub with no
real functionality** — which is why `rust/` pins `remote-anisette-v3` as the
anisette source. Nothing in the shipping build exercises it.

### `rustpush/open-absinthe/` — on-device validation engine

The source-recovered engine that produces the validation-data proof Apple requires
for the spoofed Mac identity (no official native library is packaged). Its own
top-level README ("mock placeholder") is stale cover text; `RECOVERY.md` is the real
documentation, and Android debug builds run a differential gate against a pinned
oracle. Full details: [open-absinthe.md](../internals/open-absinthe.md).

### `telephony_plus/` — legacy Flutter plugin (reference only)

The retired Flutter client's telephony plugin: Dart `lib/`, `android/` plugin
half, `example/`, `pubspec.yaml`. **The native app consumes exactly one path from
it** — `telephony_plus/android-smsmms/library`, included by
`native/settings.gradle` as the `:android-smsmms` Gradle module. Everything else in
this submodule is reference/migration material per the native build boundary (no
Dart in the build); do not add dependencies on it.

### `telephony_plus/android-smsmms/` — Java MMS stack

A fork of Google's classic Android MMS library (`library/` is the Gradle module;
`sample/` and the upgrade notes are upstream leftovers). It implements the SMS/MMS
send path for SIM (`isRpSms`) conversations — attachments on that path go through
Android MMS, never MMCS. Included in the build only as `:android-smsmms`.

## Cloning

```bash
git clone --recurse-submodules https://github.com/mweinbach/openbubbles-app.git
```

Already cloned without the flag? The submodule directories exist but are empty —
recover with:

```bash
git submodule update --init --recursive
```

Plain `git clone` cannot auto-include submodules: Git only reads `.gitmodules`
after the checkout exists, so the flag (or the follow-up init) is always required.
To stop forgetting it on every pull/checkout, set it once globally:

```bash
git config --global submodule.recurse true
```

**Access**: all six remotes are private GitHub repositories. Your SSH key
or token must be authorized on them or the clone/update fails partway with an
authentication error. CI uses read-only deploy keys for exactly this
(`SUBMODULE_KEY_RUSTPUSH`, `KEY_TELEPHONY`, `KEY_APIS`, `KEY_ABSINTHE`,
`KEY_CLEARADI`, `KEY_SMSMMS` in `.github/workflows/native.yml`, which checks out
with `submodules: recursive`).

## Everyday submodule mechanics

- After `git checkout <other-branch>` or `git pull`, submodules can lag behind the
  recorded pointers. `git submodule update --init --recursive` re-syncs (doing this
  is normal, not an error).
- `git submodule update` leaves each submodule in **detached HEAD** at the pinned
  SHA. That is expected: the parent repo pins SHAs, not branches. Work inside a
  submodule only after `git checkout main` (or a feature branch) there.
- All `.gitmodules` entries track `branch = main`, so
  `git submodule update --remote --recursive` moves a checkout to each remote's
  current `main` tip — the starting point when you intend to bump a pointer.

## Bumping a pointer (the leaf-first rule)

A submodule commit referenced only by the parent is invisible to anyone cloning the
parent until the submodule repo is pushed. Therefore, always, from the innermost
change outward:

1. Commit inside the submodule (e.g. inside `rustpush/`), on a named branch.
2. Push **that repository** first. If the change was inside a nested submodule
   (e.g. `apple-private-apis/`), push it, then commit+push `rustpush/`'s pointer,
   before touching the root.
3. In the root repo, `git add <submodule-path>` and commit the pointer move as its
   own commit (never folded into unrelated work), then push.

This matches [CONTRIBUTING.md](../../../CONTRIBUTING.md#submodules) and the
handoff sequence in [DEVELOPMENT.md](../../DEVELOPMENT.md). The `rustpush`
pointer commit in this repo is the audit trail for protocol changes — keep it
reviewable on its own.
