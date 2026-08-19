# Git — native

Follow [../../CONTRIBUTING.md](../../CONTRIBUTING.md): focused, descriptive commits; tests with
the behavior they protect; rustpush submodule commit first, then the parent pointer.

Follow the worktree, nested-push, and evidence-handoff sequence in
[../../docs/DEVELOPMENT.md](../../docs/DEVELOPMENT.md).

State which Gradle/cargo gates ran and which device checks remain. Never claim a push, login,
upgrade, or store publish without evidence.

This file used to claim there is no CI/CD. Native CI is `.github/workflows/native.yml`.
