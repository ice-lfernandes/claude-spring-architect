---
paths:
  - "**/adapter/out/messaging/**"
  - "**/adapter/in/messaging/**"
  - "**/infrastructure/messaging/**"
status: active
---

# Messaging — broker adapters, delivery, and resilience

A domain event is a plain fact the domain already emits (`@.claude/rules/architecture-ddd.md`
§ Domain: past-tense name, zero framework). This rule covers what happens **after** that:
the adapter that puts the event on a broker, and the adapter that reads one back. Today the
only implemented broker is Kafka; the shape below is written for it, and a second broker adds
a new section here, not a new file — single owner, `@CLAUDE.md` invariant 2.

## Boundary

- The domain event never crosses into the adapter unchanged: the producer adapter maps it to
  a message payload, explicit, both directions. No `DomainEvent` implementation serialized
  directly onto the wire
- Zero business logic in the adapter. A consumer that branches on payload content beyond
  "which use case do I call" has smuggled a domain decision into the transport layer
- The producer adapter implements the outbound port `domain-modeling` already declared for
  the event (`10-dominio.md` § Ports) — it doesn't invent its own interface
- The consumer adapter translates an inbound message into a call on the target use case's
  inbound port. It never touches the target aggregate directly
- No Kafka type (`ProducerRecord`, `ConsumerRecord`, `@KafkaListener`) in a domain or
  application signature

## Delivery semantics

- At-least-once, always. Exactly-once delivery isn't a Spring Kafka default and isn't assumed
  anywhere in this rule
- Every consumer is idempotent: dedupe on the event's own identity (its id, or aggregate id +
  event type + occurred-at), not on offset. A redelivered message must produce the same
  end state as the first delivery, not a duplicate side effect
- Producer: `acks=all` and `enable.idempotence=true`. Without idempotence, a producer retry
  after a transient broker error can duplicate the message before it even reaches the
  consumer
- Ordering only within a partition. If the use case needs order between two events of the
  same aggregate, they share a partition key — the aggregate id, never a random key

## Topics and serialization

- One topic per event type. A shared topic for unrelated event types forces every consumer
  to filter, and couples their deployment
- Topic name: `<bounded-context>.<aggregate>.<event-in-past-tense>` , kebab-case
  (`orders.order.confirmed`). Stable once in use — renaming a topic is a migration, not a
  refactor
- Payload: JSON by default. A schema registry (Avro/Protobuf) is a deliberate upgrade for
  when two teams need a contract they can validate at build time — not a default, and not
  assumed until a use case actually needs it
- The payload's fields are the minimum the consumer needs to act. It is not the full domain
  event object serialized as-is: a payload that leaks internal fields couples the consumer to
  the producer's domain model

## Retry and DLQ

- Retry with backoff on a transient failure (connection reset, timeout). A fixed number of
  attempts, not infinite
- After retries are exhausted, the message goes to `<topic>.dlq` — never dropped silently
- A business error (the use case rejects the message on a domain invariant) does not retry:
  retrying doesn't fix a validation failure, it just delays the DLQ. Route it straight there
- The DLQ is monitored, not archived: a message with no consumer is a production incident,
  not a log line

## Configuration

- Consumer group id: one per logical consumer, stable across deployments — changing it resets
  the committed offset and can cause reprocessing or gaps
- `auto-offset-reset`: `earliest` in an environment where losing unprocessed messages is worse
  than reprocessing old ones; `latest` only when a gap on redeploy is acceptable. Decide per
  use case, not as a global default
- Manual acknowledgment (`AckMode.MANUAL` or `RECORD`) when the consumer must guarantee the
  use case ran before the offset commits. `AckMode.BATCH`'s auto-commit-before-processing is
  a data-loss window under a crash mid-batch
- Zero credentials in a versioned `application.yml`. Same as `@.claude/rules/persistence.md`
  § Configuration — bootstrap servers with SASL/TLS come from the environment in any
  non-local profile

## How to verify

```bash
# No Kafka type outside the messaging adapter. Zero lines is the expected result.
grep -rn "org.apache.kafka\|org.springframework.kafka" --include=*.java . | grep -v "/messaging/"

# Producer idempotence and acks are set explicitly, not left to the client default.
grep -rn "enable.idempotence\|acks" src/main/resources/

# Consumer idempotency and DLQ routing are integration-test territory, not grep territory.
./mvnw -q test
```
