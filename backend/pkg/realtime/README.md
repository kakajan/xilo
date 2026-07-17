# Realtime delivery boundary

`Publisher` is the typed, post-commit boundary for WebSocket fanout. The current
gateway implementation publishes versioned envelopes through Redis Pub/Sub on
`ws:chat:*`, `ws:user:*`, and the backward-compatible `ws:post:*` namespace.

## NATS wiring gate

The existing `pkg/nats` client is not initialized by the API gateway and the
chat database does not yet have a transactional domain-event outbox. Publishing
`message.sent` directly after the PostgreSQL commit could lose events between
the commit and a NATS outage, while publishing before commit could announce
rolled-back messages. Production NATS delivery therefore remains gated on:

1. a PostgreSQL transactional outbox record written in the message transaction;
2. an idempotent relay to the `message.sent` subject; and
3. replay/dead-letter monitoring.

Redis Pub/Sub remains intentionally best-effort realtime transport. REST remains
the source of truth for reconnect reconciliation.

Mutation acknowledgements and channel events can both reach the initiating
user (for example when that user has multiple active connections). Clients
deduplicate envelopes by `event_id` and reconcile messages by the authoritative
message `id` plus `updated_at`; `sequence` is only an ordering hint.
