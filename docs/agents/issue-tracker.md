# Issue tracker: GitHub

Issues and product specs for this repository live in GitHub Issues. Prefer the installed GitHub
MCP/plugin for repository, issue, comment, label, and workflow operations when it exposes the needed
operation. Use the authenticated `gh` CLI or GitHub REST/GraphQL fallback when the connector lacks
the operation. Do not mix tools in a way that loses comments, labels, issue state, or concurrency
checks.

Resolve the canonical repository from `git remote -v` before writing. Do not rely on a remembered
owner, a fork name, or the current issue number range.

## Read before write

Before creating, rewriting, closing, or splitting an issue:

1. search open **and closed** issues for the product concept, visible symptom, feature name, and
   likely synonyms;
2. resolve nearby pull requests because GitHub shares one number space across issues and PRs;
3. read the full body, labels, assignees, comments, linked issues/PRs, and current state for every
   plausible overlap;
4. compare the issue against current `HEAD` and recent focused history—an open issue may already be
   implemented, partially implemented, or invalidated by a newer architecture;
5. decide whether to create, enrich, narrow, reopen, close, or leave a verified comment.

Do not duplicate an issue merely because the tester used different wording. Preserve one canonical
product outcome and link distinct symptoms or follow-ups to it.

### Concurrent issue batches

Another task or person may create issues while a batch is in progress. Snapshot the initial search,
create one issue at a time, and re-list/re-search after each small wave. Never infer how many issues
were created from numeric gaps. If a collision appears, consolidate before creating more: keep the
issue with the clearer history, link the duplicate, move unique context, and close only when that
state change is authorized.

For a multi-agent enrichment pass, assign exactly one issue per agent and make the mutation boundary
explicit: issue body/comment/labels only, no code or worktree edits. The coordinating agent verifies
the final issue state after every agent response instead of trusting a prose handoff.

## Tool routes

Use the GitHub MCP/plugin for normalized read/write operations when available. CLI fallbacks:

- **Create**: `gh issue create --title "..." --body-file <file>`
- **Read**: `gh issue view <number> --comments --json number,title,body,state,labels,assignees,comments`
- **Search/list**: `gh issue list --state all --search "<terms>" --json number,title,state,labels,url`
- **Comment**: `gh issue comment <number> --body-file <file>`
- **Labels/assignees**: `gh issue edit <number> --add-label ... --add-assignee ...`
- **Close/reopen**: `gh issue close <number> --comment ...` / `gh issue reopen <number>`

Use a temporary body file or safe API field for multiline content; do not expose credentials or let
shell interpolation execute pasted issue text. Confirm the normalized result after every mutation.

## Durable issue bodies

An issue body is a durable product contract, not a transcript dump or a one-time code review. Use
this structure when applicable:

1. **Outcome** — the user-visible result or defect to eliminate.
2. **Source context** — tester/request origin, date, and the user's product direction. Paraphrase
   private conversations; include no private handles, message content, coordinates, credentials, or
   unredacted screenshots.
3. **Confirmed current behavior** — current `HEAD`/snapshot examined, what exists, the first missing
   boundary, and what remains unknown.
4. **Scope and ownership** — owning modules/contracts and neighboring behavior that must remain
   unchanged.
5. **Acceptance criteria** — observable, testable outcomes including failure/cancellation states.
6. **Verification** — focused deterministic tests, affected repository gates, and separate device or
   live Apple evidence.
7. **Out of scope / related work** — explicit exclusions and links to canonical issues.

Keep the body readable and stable:

- Cite the snapshot commit when code behavior matters. Prefer a small set of owning symbols/files
  over exhaustive line-by-line tracing; line links must be commit-pinned if retained.
- Put long investigation logs, alternative designs, and exhaustive code maps in a linked design
  document or follow-up comment. Do not turn every issue into a brittle repository dump.
- Mark missing screenshot/attachment contents and uncertain causes as unverified. Never invent the
  visual detail or declare one root cause from surrounding text alone.
- Preserve the reporter's intent while separating observed behavior, inference, and proposal.
- Do not prescribe a database migration, new dependency, navigation structure, or protocol change
  merely because it is one possible implementation. State the owning contract and acceptance result.
- For privacy/security issues, define protected data, entry points, failure-closed behavior, and the
  evidence boundary without copying private data into GitHub.

If current code already satisfies the original request, do not leave a misleading open issue that
describes the feature as missing. Close it with evidence when authorized, or retitle/narrow it to the
specific residual gap and make that residual scope the first thing in the body.

## Feedback-to-backlog workflow

When converting Messages, email, screenshots, or tester notes into issues:

1. read enough surrounding context to capture the request and the user's response/product decision;
2. list each distinct outcome and map it to existing issues before creating anything;
3. keep separate issues for independently shippable bugs/features, but avoid splitting one lifecycle
   contract into dozens of implementation tickets;
4. create/enrich the canonical issues with concise context and acceptance criteria;
5. run a repo-grounded enrichment pass that checks current code/history and records only confirmed
   boundaries;
6. re-read the final backlog for duplicates, implemented items left open, conflicting requirements,
   missing links, and accidentally exposed private content;
7. report issue URLs/numbers, created vs updated vs closed state, and any unverified attachment detail.

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature requests; `/triage` reads this flag.)_

When set to `yes`, PRs run through the same labels and states as issues, using the `gh pr` equivalents:

- **Read a PR**: `gh pr view <number> --comments` and `gh pr diff <number>` for the diff.
- **List external PRs for triage**:
  `gh pr list --state open --json number,title,body,labels,author,authorAssociation,comments`, then
  keep only `authorAssociation` of `CONTRIBUTOR`, `FIRST_TIME_CONTRIBUTOR`, or `NONE` (drop
  `OWNER`/`MEMBER`/`COLLABORATOR`).
- **Comment / label / close**: `gh pr comment`, `gh pr edit --add-label`/`--remove-label`, `gh pr close`.

GitHub shares one number space across issues and PRs, so a bare `#42` may be either. Resolve the
object type through the connector or `gh pr view 42` with an issue fallback.

## When a skill says "publish to the issue tracker"

Create a GitHub issue.

## When a skill says "fetch the relevant ticket"

Fetch the issue plus comments, labels, assignees, links, and state through the GitHub connector or
`gh issue view <number> --comments`.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single issue with **child** issues as tickets.
Use the GitHub connector for supported issue/sub-issue/dependency operations; commands below are
CLI/REST fallbacks and the same read-before-write/concurrency rules still apply.

- **Map**: a single issue labelled `wayfinder:map`, holding the Notes / Decisions-so-far / Fog body. `gh issue create --label wayfinder:map`.
- **Child ticket**: an issue linked to the map as a GitHub sub-issue (`gh api` on the sub-issues endpoint). Where sub-issues aren't enabled, add the child to a task list in the map body and put `Part of #<map>` at the top of the child body. Labels: `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`). Once claimed, the ticket is assigned to the driving dev.
- **Blocking**: GitHub's **native issue dependencies** — the canonical, UI-visible representation. Add an edge with `gh api --method POST repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`, where `<blocker-db-id>` is the blocker's numeric **database id** (`gh api repos/<owner>/<repo>/issues/<n> --jq .id`, _not_ the `#number` or `node_id`). GitHub reports `issue_dependencies_summary.blocked_by` (open blockers only — the live gate). Where dependencies aren't available, fall back to a `Blocked by: #<n>, #<n>` line at the top of the child body. A ticket is unblocked when every blocker is closed.
- **Frontier query**: list the map's open children (`gh issue list --state open`, scoped to the map's sub-issues / task list), drop any with an open blocker (`issue_dependencies_summary.blocked_by > 0`, or an open issue in the `Blocked by` line) or an assignee; first in map order wins.
- **Claim**: `gh issue edit <n> --add-assignee @me` — the session's first write.
- **Resolve**: `gh issue comment <n> --body "<answer>"`, then `gh issue close <n>`, then append a context pointer (gist + link) to the map's Decisions-so-far.
