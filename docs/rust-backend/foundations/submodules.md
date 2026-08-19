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
├── rustpush/         → mweinbach/rustpush-private          [private] Apple protocols
│   ├── apple-private-apis/ → …apple-private-apis-private   [private] GSA auth, omnisette, CK proto
│   │   └── clearadi/     → …clearadi-private               [private] emulated ADI (feature remote-clearadi)
│   └── open-absinthe/  → …openabsinthe-private             [private] validation engine (default feature)
└── telephony_plus/   → mweinbach/telephony_plus            [private] Android telephony glue
    └── android-smsmms/ → mweinbach/android-smsmms          [public]  Java MMS stack (module :android-smsmms)
```

What each provides in the build: `rustpush/` is the entire protocol layer
(see [rustpush internals](../internals/rustpush.md)); `apple-private-apis/` is
vendored into it via path dependencies; `open-absinthe/` compiles into the Rust
library for on-device validation; `telephony_plus/android-smsmms` is the Java MMS
sender for SIM attachments.

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

**Access**: five of the six remotes are private GitHub repositories. Your SSH key
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
