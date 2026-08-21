# Data and background lifecycle

OpenBubbles handles account-private databases, Apple protocol sessions, Android provider state,
media caches, staged uploads, downloaded attachments, background workers, and process-lifetime
callbacks. Correct bytes are not enough: every feature needs an explicit owner, lifetime, failure
boundary, and cleanup policy.

Use this guide for Photos, contacts, avatars, profiles, transcript backgrounds, attachments,
CloudKit history, update downloads, drafts, and any new cache or worker. Feature-specific documents
may narrow these rules but must not weaken them silently.

## Define the owner before starting work

Name all four parts of the lifecycle contract:

| Question | Required answer |
|---|---|
| Who owns the work? | App process, Android service, navigation entry/ViewModel, WorkManager run, account session, or one user action. |
| What identity scopes it? | Apple account/session, chat, message/attachment, photo master, provider account, release build, or another stable product key. |
| When does it end? | Completion, cancellation, navigation pop, sign-out, account replacement, service generation change, process death, or store shutdown. |
| What survives? | Durable row, validated canonical file, retry marker, opaque cursor, or nothing. |

Process lifetime is not account lifetime. A singleton client, retained ViewModel, static cache, or
WorkManager job must not keep using credentials or paths after account replacement merely because
the process remains alive.

## Account and generation scoping

Every account-bound client, cache, callback, and unit of work must carry enough identity to reject
late results. Prefer an immutable account/session key plus a monotonically changing generation.

Required transition order for sign-out or account replacement:

1. prevent new account-bound work from starting;
2. advance/invalidate the generation so late callbacks fail closed;
3. cancel and **join** foreground coroutines, transfers, and retained ViewModels;
4. cancel unique WorkManager work and wait at the owning boundary where Android permits it;
5. close account clients, subscriptions, cursors, and stores;
6. delete only explicitly allowlisted account-local state;
7. initialize the next account and publish its new generation.

Cancellation alone is insufficient. A job can already be past a cancellation check or completing a
filesystem/database write. Every publication callback must compare its captured identity with the
current identity before mutating durable state or UI.

Tests must exercise an old job completing after sign-out and after a new account starts. The old job
must not recreate deleted directories, repopulate a database, publish UI state, or clear the new
account's progress/error state.

## Durable file publication

Never expose a canonical path while bytes are still arriving. Use the following transaction:

```text
reserve unique sibling staging path
  -> stream with a hard byte ceiling
  -> flush and fsync
  -> validate declared size, actual size, type/signature, and decoded constraints
  -> atomically replace/promote
  -> fsync the containing directory when durability requires it
  -> commit durable success state
```

Rules:

- Use a unique `.part`/staging file in the destination directory so atomic promotion stays on the
  same filesystem. Do not let two attempts share a partial path.
- Enforce limits while streaming. A missing or dishonest `Content-Length` is not permission for an
  unbounded write.
- Validate non-empty content, declared/actual byte count, expected media/container signature, and
  relevant dimensions or structure before promotion.
- Preserve the prior valid canonical file until its replacement is fully validated. A failed retry
  must not destroy last-known-good content.
- Mark database state successful only after promotion. On cancellation/failure, remove the owned
  partial and leave a retryable or explicitly failed durable state.
- On startup, remove only recognized abandoned staging names under an allowlisted root. Never follow
  symlinks or recursively delete an unresolved broad path.

User-exported MediaStore files are user-owned copies. Cache eviction, remote tombstones, sign-out,
or account cleanup must not delete them unless the user explicitly requested that deletion.

## Bound memory, caches, and decoding

Entry-count limits do not bound memory when values vary from tiny icons to multi-megabyte images.
Define independent limits for:

- encoded input bytes;
- retained cache bytes;
- decoded width/height and pixel count;
- concurrent decodes/transfers;
- per-batch operation count and estimated provider/database payload bytes;
- total files/entries per reconciliation pass.

Use access-ordered byte accounting for in-memory media caches. An oversize item may be returned to
the current operation after bounded validation, but it must not be retained when it exceeds the
cache budget. Replacement and eviction must update byte accounting exactly once.

Sampling during decode reduces peak allocation but does not prove the output contract. Recheck the
decoded dimensions, scale to the explicit product maximum, and separately enforce the final encoded
byte ceiling. Reject malformed or unsupported input as a recoverable feature error rather than
letting it become an OOM or panic.

Tests should assert structural bounds—retained bytes, entries, dimensions, concurrency, and batch
sizes—rather than flaky JVM heap deltas.

## Treat archives and protocol payloads as hostile

For ZIPs, posters, layered backgrounds, protobufs, plists, MMCS metadata, and similar inputs, bound
the work before allocation or extraction:

- compressed input bytes and streamed bytes;
- expanded total bytes and per-entry bytes;
- entry count, nesting/path depth, and normalized path length;
- path traversal, absolute paths, duplicate/ambiguous entries, and symlinks;
- layers, gradients, stops, dimensions, and decoded pixel count;
- integer conversions, indexes, offsets, lengths, and short reads/writes.

Malformed values return typed/recoverable errors. Do not use unchecked indexing, `unwrap`, generic
recursive extraction, or allocation based only on a remote length. Cryptographic verification and
protocol integrity checks remain mandatory even when accepting a compatibility representation.

## Scheduling, retries, and idempotence

Keep three outcomes distinct:

1. **durably scheduled** — a persistent worker/retry owner exists;
2. **best-effort immediate** — an inline refresh/mirror improves latency but may fail;
3. **completed** — durable state and canonical files reflect success.

Schedule durable recovery before an optional immediate mirror when eventual work is required. A
best-effort failure may be logged in sanitized form, but must not erase or masquerade as the durable
retry. Conversely, a required provider/database failure must propagate to WorkManager's retry/fail
decision rather than being caught and reported as success.

Record attempt identity and make page/batch application idempotent. Commit continuation cursors only
after the page is fully applied. Replaying a page, callback, terminal send event, or transfer receipt
must not duplicate rows or regress a terminal state.

## Database and callback shutdown

Transactions, lazy relation reads, cursors, and their owning dispatcher/thread form one lifetime.
Do not open a lazy reader on one thread and close its store from another while work is active.

Subscriptions and invalidation observers require one explicit owner. At shutdown or store
replacement:

1. stop accepting new callbacks;
2. cancel subscriptions and join reader work;
3. make any late callback observe the closed/generation state and return without touching data;
4. close the store/client only after those owners are released.

See [PERSISTENCE.md](PERSISTENCE.md) for ObjectBox-specific constraints.

## Privacy, logs, and cleanup

Logs and telemetry must not contain message text, handles, contacts, raw records, credential/key
material, push tokens, signed URLs, request/response bodies, decrypted protocol values, or private
file contents. Prefer fixed event names, bounded counts, durations, enum/error classes, and redacted
identifiers.

Persisted diagnostics need a severity floor, a byte/rotation cap, and an exact deletion policy.
Remove old sensitive logs by allowlisted basename under the known log root; do not implement a broad
recursive cleanup. Tests should scan source/logger calls for prohibited sinks and test the cleanup
allowlist without reading private payloads.

## Verification matrix

Every new lifecycle boundary needs deterministic coverage for the relevant rows:

| Boundary | Minimum proof |
|---|---|
| Cancellation | Work stops and cannot publish after owner/generation ends. |
| Account switch | Old result cannot mutate new account state or recreate old state. |
| Partial write | Canonical file remains absent or last-known-good; partial is cleaned. |
| Wrong/unknown size | Streaming ceiling and post-write validation both reject it. |
| Process restart | Running work becomes queued/retryable or is safely abandoned. |
| Duplicate/reordered event | Application is idempotent and terminal state does not regress. |
| Cache pressure | Retained bytes/dimensions/concurrency stay within declared budgets. |
| Provider/database failure | Required failure reaches the owning retry/fail decision. |
| Shutdown | Subscriptions/readers release before store/client close; late callbacks are inert. |
| Cleanup | Only allowlisted roots/names are removed; symlink targets and user exports survive. |

Host tests prove policy and ownership. A device pass separately proves Android provider behavior,
WorkManager/service lifetime, filesystem semantics on the target device, account transitions, and
live Apple behavior. State exactly which tier passed.

## Handoff

Report the owner, scope key/generation, cancellation boundary, durable state, file publication
strategy, all byte/dimension/concurrency limits, retry/idempotence behavior, cleanup allowlist, tests,
and any device scenario not run. If one of those has no answer, the lifecycle contract is incomplete.
