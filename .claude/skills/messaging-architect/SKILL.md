---
name: messaging-architect
description: >
  Designs the Kafka producer/consumer adapter for an already-modeled domain event —
  topic, partition key, serialization, consumer group, delivery semantics, retries and
  DLQ — into the `25-mensageria.md` partial. Use when the request involves publishing a
  domain event to Kafka, consuming a Kafka topic, designing a producer or consumer
  adapter, deciding topic/partition/serialization, or wiring retry/DLQ for a message
  listener. Piece of the `/new-feature` pipeline: requires `10-dominio.md` in the given
  folder with an Events block that names external delivery, and stops without it.
argument-hint: "[path of the UC-NNN-<slug> folder]"
allowed-tools: Read, Write, Glob, Grep, Bash, AskUserQuestion
---

## Available specs

!`find docs/use-cases -mindepth 1 -maxdepth 1 -type d -name 'UC-*' 2>/dev/null | sort`

Empty above → none yet, run `/use-case-design` first. (`find`, not an `ls` glob: under zsh an unmatched glob
aborts the command before any fallback runs.)

## Target

$ARGUMENTS

---

# Messaging Architect

Designs **how a domain event leaves the process and how another one gets consumed**: topic,
key, serialization, delivery semantics, retry and DLQ, and the two adapters that carry it.
What `domain-modeling` already named as an event with a consumer, this skill gives a broker
transport to.

**Entry rule: without `10-dominio.md` naming an event for external delivery, there's nothing
to transport.** This skill reads `docs/use-cases/UC-NNN-<slug>/00-caso-de-uso.md` and
`10-dominio.md` and treats them as a contract. Without the domain partial, it stops and tells
you to run `/domain-modeling`. If the domain partial's Events block is "none," or names only
a sibling use case in the same process, it also stops: an in-process call has no broker to
design — designing Kafka config for it would be scope no one asked for.

**Exit rule: it doesn't write code.** It emits `25-mensageria.md`. The Java classes come from
the executor agent, which reads the partial and the `templates/` exemplars.

**Rule rule: the rules don't live here.** Idempotent consumer, `acks=all`, topic naming,
retry/DLQ, and the rest are `@.claude/rules/messaging.md`. This skill applies them and cites
them; it doesn't reproduce them.

## How it's invoked

Two paths, and both matter: `/messaging-architect` by hand, or chained by `/new-feature` once
that orchestrator's pipeline reaches this point. That's why it does **not** carry
`disable-model-invocation` — a skill the model can't see is a skill the orchestrator can't
call.

The guard against out-of-order or unnecessary firing isn't the frontmatter: it's the **entry
rule** above. Recorded in `@.claude/decisions/0007-pipeline-skills-invocation.md`.

## Why this is a skill and not a subagent

Form 1, motivated by axis 2 (chained by `/new-feature`, also invocable by hand) and axis 9
(`domain-modeling` already owns the event; this skill only owns its transport). The closest
rejected form was a fourth option in the same decision — a dedicated "domain events" skill —
which failed invariant 2: `domain-modeling` already asks whether the aggregate emits an event
and already writes the Events block of `10-dominio.md`. A subagent was never viable either: it
fails the § 5 counter-test in `claude-code-architect-designer`'s decision matrix on all three
points — the interview over topic/partitioning/DLQ is the heart of the task, the reference
content fits in `templates/`, and the partial it produces is short.

## Boundary with neighboring skills

The division is by **moment and artifact**, not technology:

| Piece | When it acts | What it produces |
|---|---|---|
| `use-case-design` | Before the domain exists | `00-caso-de-uso.md` — boundary and canonical names |
| `domain-modeling` | After the mother spec | `10-dominio.md` — aggregate, invariants, ports, **and the event itself** |
| `rest-api-architect` | After the domain partial | `30-rest.md` — transport, no schema, plus the schema requirements it creates |
| **this skill** | After the domain partial, only when an event needs external delivery — and **before** `persistence-architect` | `25-mensageria.md`, § 6 included |
| `persistence-architect` | After this skill | `20-persistencia.md` + migration — builds what § 6 asked for |
| `test-architect` | After all of them | `40-testes.md` |

`domain-modeling` decides **whether** an event exists and **who consumes it**. This skill
never redefines the event or its payload's business meaning — it only decides how it travels.
If the consuming use case is a sibling in the same process, there is no broker to design and
this skill doesn't run at all (see Entry rule).

**Out of scope, on purpose: in-process dispatch.** Spring's own `ApplicationEventPublisher`/
`@EventListener` is a same-JVM transport for the same kind of domain event this skill handles
for Kafka. No use case in this repo has needed it yet, so it isn't built — anti-pattern 9
(anticipation) in `claude-code-architect-designer`'s decision matrix. If one does, it's one
more transport option inside this skill (another `templates/` pair and a section in
`@.claude/rules/messaging.md`), not a new skill: the boundary above already put event
*definition* in `domain-modeling` and event *transport* here, and in-process dispatch is
still transport.

## Procedure

1. **Read the specs.** `00-caso-de-uso.md` and `10-dominio.md` from the folder in
   `$ARGUMENTS`. Without the second, stop. Extract: the event's name, payload fields, the
   consuming use case, and whether that consumer is external (another service, another
   deployable) — that's what makes this skill apply at all.

2. **Survey what already exists.** A topic or consumer group already wired gets reused, not
   duplicated.

   ```bash
   grep -rln "@KafkaListener\|KafkaTemplate" --include='*.java' src/ 2>/dev/null
   grep -rn "group-id\|bootstrap-servers" src/main/resources/ 2>/dev/null
   grep -rl "processed_events\|ProcessedEventStore" --include='*.java' src/ 2>/dev/null
   ```

3. **Interview — only what the specs don't fix.** `AskUserQuestion`, at most 4 questions per
   call. Don't re-ask what `00-caso-de-uso.md` or `10-dominio.md` already answered.

   | Axis | Decides |
   |---|---|
   | **Publication timing** — must the event survive a broker outage between the commit and the publish? | Form A (publish after commit) or Form B (transactional outbox + relay), per `@.claude/rules/messaging.md` § Publication timing. Read the `Durability` column of `10-dominio.md` § Events **first**: when it already answers, record the form and don't ask again |
   | Partition key candidate (which field must stay ordered) | Whether the aggregate id is enough, or a composite key is needed |
   | Consumer group id, new or existing | Reuse vs. a fresh subscription with its own offset |
   | `auto-offset-reset` tolerance (losing vs. reprocessing on redeploy) | `earliest` or `latest` |
   | Ordering requirement across different aggregates | Whether one topic is enough or the event needs to fan out differently |
   | Existing `processed_events`-style dedupe table in this project | Reuse vs. ask `persistence-architect` to model one |

   Every axis but the first is consumer-side. A use case that only **produces** still has the
   publication timing to settle, so "no axis applies, no `AskUserQuestion` this pass" is never
   the right conclusion for a producing use case: either the domain partial's `Durability`
   column already fixed the form and the partial records which, or this step asks.

4. **Design the producer adapter.** Implements the outbound port `domain-modeling` already
   declared — never a new interface. Payload is the minimum the consumer needs, mapped
   explicitly from the domain event; the event itself never serializes directly. The
   publication form from step 3 decides the shape, and only the shape — the port's signature
   is the same either way:

   | Form | What implements the port | Extra pieces | Exemplar |
   |---|---|---|---|
   | A — publish after commit (default) | The adapter sends to the broker directly | none | `templates/KafkaProducerAdapter.java.example` |
   | B — transactional outbox + relay | The adapter writes an outbox row inside the caller's transaction | relay component, `OutboxRelayGateway` port, shared `outbox_events` table | `templates/OutboxRelayPublisher.java.example` |

   **4a. Form B's schema, when step 2 found no outbox.** One table for the whole project, not
   one per event — same nature as `idempotency_keys`. This skill doesn't design tables: name
   the requirement as a row of the partial's **§ 6 · Schema requirements** and flag that
   `persistence-architect` has to model it, with the columns the relay needs (claim, attempts,
   failure reason, dead-letter flag) listed as requirements, not as DDL. Exemplar's header
   comment carries the same list. § 2 explains the form and why; § 6 is the list the next
   skill reads — a requirement described only in § 2's prose is one nobody has to find.

   The receiving end is that skill's **step 4b**, which models the table once from
   `persistence-architect/templates/OutboxEventTable.sql.example` and
   `templates/OutboxEventStore.java.example` — the mirror of its own step 4a for
   `idempotency_keys`. Name the step in the partial, so the requirement has an addressee
   instead of a hope. The port stays declared here: that pair implements
   `OutboxRelayGateway`, it doesn't re-declare it.

5. **Design the consumer adapter.** Translates the inbound payload into a call on the target
   use case's inbound port. Dedupe on the event's own identity before calling it; manual
   acknowledgment, offset commits only after the use case returns.
   Shape: `templates/KafkaConsumerAdapter.java.example`.

   **5a. Dedupe table, when step 2 found none yet.** Not per-consumer: one table
   (`processed_events` or equivalent), shared by every listener in the project, modeled once.
   If missing, add it as a second row of **§ 6 · Schema requirements** and flag that
   `persistence-architect` needs to model it — this skill doesn't design tables,
   `20-persistencia.md` does.

6. **Fix retry and DLQ.** Backoff attempts and the DLQ topic name, per
   `@.claude/rules/messaging.md` § Retry and DLQ. A business rejection (typed domain
   exception from the consumed use case) skips retry and goes straight to the DLQ.

7. **Fix the configuration.** Bootstrap servers, producer `acks`/idempotence, consumer group
   and offset reset, ack mode — from `templates/application-kafka.yml.example`. Mandatory
   values are the rule; what this skill decides is the per-use-case sizing (group id, offset
   reset tolerance) from step 3.

8. **Write the partial.** `docs/use-cases/UC-NNN-<slug>/25-mensageria.md`, from
   `templates/messaging-spec.md.example`. Six blocks, all mandatory — § 6 included, written
   as `none` when steps 4a and 5a both found nothing to ask for.

9. **Check Kafka has a container.** `grep -A2 "^services:" docker-compose.yml` for a
   `kafka` service. Missing → invoke `docker-architect` with this UC's folder, so the
   dev-time broker matches the topic just designed. Don't edit `docker-compose.yml` here —
   that skill is its single owner.

10. **Report and stop.** Path of the file written, **the content of § 6** (each schema
    requirement handed to `persistence-architect`, or "none"), whether `docker-architect`
    ran, and what's missing for the folder to be complete (`20-persistencia.md`,
    `40-testes.md`). Don't invoke anyone else — `persistence-architect` runs next in the
    pipeline and reads § 6 in its first pass.

## What the partial contains

Six blocks. An empty block is written as "none" — deleting it hides a question nobody
asked.

| Block | Fixes | Form exemplar |
|---|---|---|
| Topic and delivery | Topic name, partition key, serialization, delivery semantics | `@.claude/rules/messaging.md` § Topics and serialization |
| Producer adapter | The port from `10-dominio.md`, **the publication form (A or B) and why**, the adapter, the payload shape, and — Form B only — the outbox schema requirement for `persistence-architect` | `KafkaProducerAdapter.java.example` (A) · `OutboxRelayPublisher.java.example` (B) |
| Consumer adapter and idempotency | The consuming use case, the listener, the dedupe key and table | `KafkaConsumerAdapter.java.example` |
| Retry and DLQ | Backoff, DLQ topic, which failures skip retry | `@.claude/rules/messaging.md` § Retry and DLQ |
| Configuration | Group id, offset reset, ack mode, with the decided value and why | `application-kafka.yml.example` |
| Schema requirements | Every table or column this transport needs that the domain didn't model — the shared `outbox_events` under Form B (step 4a), the shared dedupe table (step 5a) — one row each with the columns as requirements, never DDL. `none` when there are none | `@.claude/rules/messaging.md` § Publication timing · § Retry and DLQ |

The exemplars in `templates/` are **reference for form**, not files to copy. It's the
executor agent that reads them when generating code.

## Contract

**Reads** `docs/use-cases/UC-NNN-<slug>/00-caso-de-uso.md` and `10-dominio.md` (mandatory —
stops without the second, or without its Events block naming external delivery),
`@.claude/rules/messaging.md`, `@.claude/rules/architecture-ddd.md` (Adapters section),
`@.claude/rules/naming.md`, `@.claude/rules/error-handling.md`, `@.claude/rules/lombok.md`,
and the active blueprint's `packages.map`.

**Writes** `docs/use-cases/UC-NNN-<slug>/25-mensageria.md`. Nothing else.

**Does not write Java code.** The publisher, listener, and payload classes come from the
executor agent.

**Does not edit `docker-compose.yml`.** When step 9 finds no `kafka` service, it invokes
`docker-architect` instead of writing the service block itself — single owner, see that
skill's Contract.

**Does not model the dedupe table, nor the outbox table.** When step 5a or step 4a finds
none, it names the need as a row of § 6 for `persistence-architect` to pick up — this skill
doesn't design schema. That block is the contract between the two: this skill runs **before**
`persistence-architect` in `/new-feature`, so § 6 is input to that skill's first pass, not a
correction to a partial already written. Form B's relay reads that state through the application-layer
`OutboxRelayGateway`, never through the persistence adapter's entity or repository
(`@.claude/rules/architecture-ddd.md` § Adapters).

**Owns the publication form** (after-commit vs. outbox + relay), from step 3's first axis.
`domain-modeling` records whether the event tolerates being lost — the `Durability` column of
`10-dominio.md` § Events; this skill turns that property into a transport decision. A form
chosen upstream of the `Durability` column is a divergence to report, not to adopt silently.

**Does not decide** the use case boundary (`00-caso-de-uso.md`), whether an event exists or
its payload's business meaning (`10-dominio.md`, `domain-modeling`'s call), the transport for
synchronous HTTP (`30-rest.md`), or the tests (`40-testes.md`). Doesn't touch
`.claude/rules/**`.

**Does not cover in-process Spring events** (`ApplicationEventPublisher`/`@EventListener`) —
see § Boundary with neighboring skills. No symptom for it yet in this repo.

**Does not collide with `domain-modeling`**: that one declares the event and the outbound
port, this one says how the port is served over Kafka. The event's payload meaning belongs to
the other; if it needs to change, report the divergence instead of rewriting it.
