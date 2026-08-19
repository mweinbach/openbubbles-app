---
name: openbubbles-send-lifecycle
description: Diagnose or change the outgoing-message lifecycle when a sent message remains sending, fails incorrectly, duplicates, or reaches the UI in the wrong state. Do not use for a transport-wide receive outage or unrelated Compose rendering work.
---

# OpenBubbles Send Lifecycle

Trace one outgoing message across its durable identities and terminal events. Do not mask a missing transition with a UI timeout.

## Map one message end to end

Read the send section of [../../../docs/ARCHITECTURE.md](../../../docs/ARCHITECTURE.md) and [../../../docs/DEVELOPMENT.md](../../../docs/DEVELOPMENT.md). Follow the same message through:

1. `MessageRepo.stageOutgoingMessage*` and its local ObjectBox row;
2. temporary/staging guid and `sendingServiceId`;
3. Rust or carrier dispatch;
4. promotion to the transport guid;
5. local/outgoing echo ingestion;
6. `UPushMessage.SendConfirm` success or failure;
7. the persistent `MessageStatus` rendered by Compose.

Use durable identifiers and timestamps from the database/logs. A recipient seeing or reacting to a message proves transport success but does not prove the local row consumed its confirmation.

## Design for event ordering

Model terminal events as order-independent and idempotent. In particular, `SendConfirm` may arrive before Kotlin promotes the temporary row or ingests the outgoing echo. Retain a bounded unmatched confirmation until a valid row identity becomes visible; apply it once; keep an echo distinct from Apple's terminal confirmation.

Cover at least the failing order, the normal order, duplicate delivery, and both success/failure terminals with deterministic tests near `MessageIngestor` or the platform send helper. Do not send a real external message unless device validation was explicitly requested.

## Verify and hand off

Run the union of affected gates from [../../../docs/VERIFY.md](../../../docs/VERIFY.md), inspect the persistent row after the event sequence, and state which real-device send/receive scenarios remain unrun. If the fix changes a Kotlin-visible Rust event or type, also load [../openbubbles-uniffi-contract-change/SKILL.md](../openbubbles-uniffi-contract-change/SKILL.md).
