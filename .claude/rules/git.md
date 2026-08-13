# Git Rules — Commit Conventions

## Format

```
<type>: <message>
```

- Lowercase type and message.
- No scope suffix — use `feat:` not `feat(chat):`.
- No trailing period.
- Imperative or present tense preferred.

## Types

| Type | When to use |
|------|-------------|
| `feat:` | New user-visible feature or capability |
| `fix:` | Bug fix |
| `chore:` | Refactor, cleanup, dependency update, tooling, no behavior change |
| `wip:` | Incomplete work being committed mid-task (squash before merging) |

## Examples

```
feat: add scheduled message support for macOS
fix: prevent duplicate tiles in conversation list
chore: remove unused fetchNetworkContacts method
wip: sync animation and chat list update timing
```

## Branch & PR Workflow

- Branch off `master`; PRs target `master`.
- No CI/CD — build and test locally before opening a PR.
- Squash `wip:` commits before merging.

## What Not to Do

- Don't use `feat(scope):` parenthetical scopes.
- Don't capitalize the first word of the message.
- Don't add a period at the end.
- Don't mix multiple unrelated changes in one commit.
