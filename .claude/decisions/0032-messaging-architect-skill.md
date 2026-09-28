# 0032 · Skill `messaging-architect` for the Kafka producer/consumer adapter

- **Date:** 2026-09-11
- **Scenario:** "Finalize the skill documented to be created (`messaging-adapter`) — messaging
  protocols are plural (Kafka, RabbitMQ, SNS, SQS, JMS); decide whether to build one generic
  skill dispatching to per-tech skills, or one skill per technology; and clarify how
  broker-backed messaging relates to Spring's own native events. MVP priority: a complete
  skill and rule for Kafka, consumer and publisher."
- **Decision:** Form 1 — `.claude/skills/messaging-architect/SKILL.md`
- **State:** approved by Lucas Fernandes, on 2026-09-11

Supersedes the naming only: `.claude/decisions/0002-skill-use-case-design.md` (§ d) and
`.claude/decisions/0005-persistence-rule-and-design.md` (closing note) both fixed the name
`messaging-adapter` for this future piece. Renamed to `messaging-architect` at approval time
to match the `<layer>-architect` convention already used by `persistence-architect`,
`rest-api-architect`, and `docker-architect` — none of which use an `-adapter` suffix. Those
two records are left untouched; they're history, not current state.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Concrete symptom | No skill exists for publishing/consuming Kafka messages from an already-modeled use case; MVP needs producer + consumer, complete | — |
| 2 — Trigger | Chained by `/new-feature`, same as `persistence-architect`/`rest-api-architect` | Form 3 (subagent) — the interview with the user over topic/partitioning/DLQ is the heart of the task |
| 3 — Frequency | Per use case whose domain event needs external delivery, not every session | Form 5 (`CLAUDE.md`) |
| 4 — Territory | `**/adapter/out/messaging/**`, `**/adapter/in/messaging/**` (package role not yet declared by any blueprint) | Confirms Form 4 for the declarative half (`rules/messaging.md`), separate from the skill |
| 8 — Destination | Both — needed after project creation, whenever a new use case adds a Kafka producer/consumer | Fixes propagation to `project-bootstrap` steps 6.6/6.7 |
| 9 — Integration | `domain-modeling` already owns domain-event *definition* (`10-dominio.md` § Events, its own step 3 already names `messaging-adapter` as the piece that "comes in" after). `docker-architect` already anticipates "the database **or messaging service**" in its own description. `.claude/rules/00-index.md` already plans `messaging.md`, generic, not per-broker | Rules out a separate "domain events" skill — would duplicate `domain-modeling`'s territory (invariant 2) |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Single tech-agnostic skill `messaging-architect`, Kafka in the body now | 9 | **Approved** |
| 2 | Generic dispatcher skill + one child skill per broker (`kafka-adapter`, `rabbit-adapter`, …) | 4 | Rejected — no precedent for skill-invokes-skill dispatch in this repo; doubles propagation for 4 unbuilt brokers |
| 3 | One skill per technology from day one, no umbrella (`kafka-adapter` alone) | 5 | Rejected — its name would diverge from the already-planned generic `rules/messaging.md`, creating an ownership question the moment a second broker arrives |
| 4 | Separate "domain events" skill built first, `messaging-architect` after | 3 | Rejected — `domain-modeling` already owns event definition (see axis 9); this would violate invariant 2 |
| 5 | create nothing | 1 | Rejected — concrete MVP need (Kafka consumer + publisher), no existing piece covers it |

### Option 1 — `messaging-architect` (score 9)

**Motivator:** axis 9 — strong precedent (`persistence-architect`, `docker-architect`) for one
skill per layer, technology-agnostic in name, with tech-specific knowledge in
`references/`/`templates/`.

**Pros:** one pipeline entry, one partial (`25-mensageria.md`), one propagation path. Adding
RabbitMQ/SQS later means more `templates/`+rule content inside the same skill, not a new
skill — matches how `persistence-architect` doesn't fork per database engine.

**Cons:** none identified against the invariants. Body may grow if/when multiple brokers are
implemented; accepted per `decision-matrix.md` § 4 ("a long body isn't an argument against
the skill").

**Points cut in the rubric:** none — full score on form fit, invariants, precedent, and
propagation. One point held back (not a perfect 10) only because propagation touches five
files outside the new skill itself (`domain-modeling`, `docker-architect`,
`project-bootstrap`, `00-index.md`, root `CLAUDE.md`), raising maintenance surface slightly
above the median extension.

### Option 2 — dispatcher + per-broker skills (score 4)

Rejected mainly on § 8 criterion 5 (maintenance cost) and 6 (precedent): the only chaining
pattern this repo uses is the `/new-feature` pipeline's entry-rule-on-missing-partial, never
skill-invokes-skill as a routing layer. Building it for one implemented broker (Kafka) and
four unbuilt ones is anti-pattern 9 (anticipation).

### Option 3 — `kafka-adapter` alone (score 5)

Viable but strains naming coherence: `rules/messaging.md` was already planned as the single
generic owner of "Kafka/SQS, idempotency, retries, DLQ" (`00-index.md`, written before this
decision). A skill named after one broker, reading a rule named after the whole topic,
reads as two different scopes the day a second broker shows up.

### Option 4 — domain-events skill first (score 3)

Rejected on invariant 2: `domain-modeling/SKILL.md` step 3 already asks "Does the aggregate
emit an event?" and its `10-dominio.md` § Events block already records "which event, which
payload, which UC consumes it," with its own form exemplar
(`domain-modeling/templates/DomainEvent.java.example`). A new skill for the same artifact
would be a duplicated owner. What has no owner yet is only the **transport** — publishing
that already-defined event to Kafka — which is `messaging-architect`'s job, the same relation
`persistence-architect` has to `domain-modeling`'s output ports.

## References

| Claim | Source |
|---|---|
| `rules/messaging.md` was already planned, generic, not per-broker | `@.claude/rules/00-index.md` § Planned rules |
| `domain-modeling` already owns event definition and already names `messaging-adapter` as the next piece | `@.claude/skills/domain-modeling/SKILL.md` line 84, and `templates/DomainEvent.java.example` |
| `docker-architect` already anticipates a messaging service, not just a database | `@.claude/skills/docker-architect/SKILL.md` description |
| `<layer>-architect` naming precedent, no `-adapter` suffix among layer skills | `@.claude/skills/persistence-architect/SKILL.md`, `@.claude/skills/rest-api-architect/SKILL.md`, `@.claude/skills/docker-architect/SKILL.md` |
| No repo precedent for skill-invokes-skill dispatch as a routing mechanism | `@.claude/decisions/0007-pipeline-skills-invocation.md` (chaining is by entry-rule, not direct invocation) |
| Messaging consumer/producer naming convention already exists | `@.claude/rules/naming.md` § Classes: `<Event>Listener` / `<Event>Publisher` |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/messaging-architect/SKILL.md` (new) | The skill itself |
| `.claude/skills/messaging-architect/templates/*.example` (new) | `KafkaProducerAdapter`, `KafkaConsumerAdapter`, `application-kafka.yml`, `messaging-spec.md` |
| `.claude/rules/messaging.md` (new) | Moved from "planned" to "written" |
| `.claude/rules/00-index.md` | Row moved from Planned to Written |
| `CLAUDE.md` (this repo's root) | Routing table row added |
| `.claude/skills/domain-modeling/SKILL.md` | Renamed `messaging-adapter` → `messaging-architect` (line 84) |
| `.claude/skills/domain-modeling/templates/domain-spec.md.example` | Same rename |
| `.claude/skills/domain-modeling/templates/DomainEvent.java.example` | Same rename |
| `.claude/skills/docker-architect/SKILL.md` | Added Kafka to service catalog, boundary table row, `25-mensageria.md` to Reads |
| `.claude/skills/docker-architect/templates/kafka-service.yml.example` (new) | Compose block for the docker-architect step that serves `messaging-architect`'s need |
| `.claude/skills/project-bootstrap/SKILL.md` | Step 6.6 rule-copy table, § Rules with a territory, step 6.7 skill-copy table + both grep-exhaustiveness lists, § Skill contract read/write lists, stale `messaging-adapter` mention at line 589 |
| `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example` | Added routing row, removed stale "doesn't exist yet" mention |

Goes to the generated project: **yes, via step 6.6 (`messaging.md`) and step 6.7
(`messaging-architect`)** — axis 8 answered "both."

### Follow-up propagation (2026-09-11, same session, after `git status` sync)

The Phase 4 write above hadn't wired the skill into the orchestrator that this same
decision's interview table says it's chained by (row "2 — Trigger"). Fixed:

| File | Change |
|---|---|
| `.claude/skills/new-feature/SKILL.md` | Added `messaging-architect` as conditional step 4 (Contract's Writes/Integrates-with lists, Procedure, consolidation precedence table, template block list, References). Declared gap: `java-spring-boot-developer`'s fixed 19-step checklist doesn't consume the messaging block yet — same pattern as D23's "passo 6.8 falta" |
| `.claude/blueprints/hexagonal/hexagonal.yaml` | Added `adapter-out-messaging` module + `adapter.out.messaging` packages.map key. The blueprint already had `adapter-in-messaging` (consumer) but no producer home — asymmetry surfaced now that the skill designs both directions |
| `docs/03-new-feature.md` + `docs/en/03-new-feature.md` | Mermaid diagram + prose, both languages: messaging-architect chained after domain-modeling, conditional on external delivery, may itself chain `docker-architect` |
| `CONTEXT.md` | Inventory rows updated (`messaging.md`, `messaging-architect` skill no longer "❌ falta") |

Not touched, on purpose: `.claude/skills/new-feature/templates/feature-spec.md.example`
(worked example stays 5-block; a 6th worked block needs a real UC exercising Kafka to be
honest, not invented).
