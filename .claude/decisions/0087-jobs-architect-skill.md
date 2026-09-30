# 0087 · A design skill owns scheduled jobs: tool choice, trigger, coordination, and the outbox's own jobs

- **Date:** 2026-09-30
- **Scenario:** "quero criar uma nova skill (jobs-architect) que tenha a responsabilidade,
  conhecimento e templates de construcao de jobs" — prompted by a `/new-feature` run in
  `demo-clean-arch-single-module` (UC-003) that chose the transactional outbox and produced
  jobs with no piece owning their design.
- **Decision:** Option 1 — Form 1 `.claude/skills/jobs-architect/SKILL.md` (class `design`,
  partial `35-jobs.md`) + Form 4 `.claude/rules/scheduling.md`; the relay's schedule and the
  prune job move to it; `/new-feature` runs it after messaging and before persistence.
- **State:** approved by Lucas Fernandes, on 2026-09-30 — option 1, with the three follow-ups
  accepted: jobs before persistence, the relay behind an inbound port, and the `.scheduling`
  entry written into the eight blueprints in the same run

## What the context showed

Observed in `demo-clean-arch-single-module` after UC-003 (`c6f86f9`):

| Fact | Where |
|---|---|
| The relay is a bare `@Scheduled(fixedDelayString = "${app.outbox.poll-interval:PT1S}")` with `SchedulingConfig` next to it | `infrastructure/messaging/OutboxRelay.java`, `SchedulingConfig.java` |
| `claimPending` is an unlocked `SELECT` — single relay instance assumed, no lock, no replica count recorded anywhere | `infrastructure/persistence/outbox/OutboxEventStore.java` |
| The prune job does not exist; the migration carries a commented `DELETE` and `application.yml` says `prune-after` is deliberately absent "because the pruning job that would read it doesn't exist yet" | `V5__create_outbox_events.sql`, `application.yml:63` |
| Same defect already recorded once: 7-day retention decided, job given to nobody | `.claude/lessons-learned/lessons-learned-014.md` § 9 |

Who decides what about jobs in this repository today:

| Concern | Owner today |
|---|---|
| The relay's `@Scheduled` and `@EnableScheduling` | `messaging-architect/templates/OutboxRelayPublisher.java.example` (SCHEDULING block) |
| Claim strategy (unlocked / lease / `SKIP LOCKED`) and poll interval | `persistence-architect` step 4b |
| Prune job | nobody — `persistence-architect` step 4b routes it to a `## Deferred` row |
| Any job not born from the outbox (nightly reconciliation, report, bulk import) | nobody — `use-case-design` accepts "job, schedule" as a trigger and no layer skill details it |
| Which scheduling technology, and why | nobody |

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | The prune job ownerless in two runs (lessons-learned-014 § 9, demo UC-003); relay coordination across replicas decided only implicitly; no piece for a non-outbox job | Create nothing (as the whole answer) |
| 2 — trigger | Model decision inside `/new-feature`, and standalone by description | Form 2 |
| 5 — nature | Multi-step procedure: inherit, interview, decide tool, write partial | Forms 4 and 5 as the whole answer |
| 6 — isolation | None — the interview is the heart of the task | Form 3 |
| 7 — mandatoriness | Persuasion suffices; a hook only after a repeated observed failure | Forms 7 and 8 |
| 8 — destination | Both — a development skill, travels in `export.skills.include` | — |
| 9 — integration | Pipeline design piece with its own partial; **takes over** the relay's scheduling and the prune job from `messaging-architect` / `persistence-architect` | "Extend the two existing skills" as the whole answer |
| — replicas | Inherited from a partial, asked only when absent | — |
| — templates | Curated: `@Scheduled`, ShedLock, Quartz (JDBC clustered), Spring Batch, db-scheduler, JobRunr. The rest (Kubernetes CronJob, Spring Cloud Task, Modulith events, Debezium) only in the decision matrix | — |
| — norm | Job code norms become their own rule | Norms only in the skill's `references/` |
| — package | `infrastructure.scheduling` (a driving adapter: the trigger calls an input port) | Job next to whichever adapter it touches |

## Research — tools in the Java/Spring ecosystem (2026-09-30)

Condensed; the full matrix becomes `jobs-architect/references/tool-decision-matrix.md`.

| Tool | Cross-instance single run | Catch-up after downtime | Dynamic schedules | Restart from failure point | Retry/backoff | UI | Infra | License |
|---|---|---|---|---|---|---|---|---|
| `@Scheduled` | no | no | via `SchedulingConfigurer` | no | no | `/actuator/scheduledtasks` | none | Apache-2.0 |
| ShedLock + `@Scheduled` | yes (lock, best effort) | no | no | no | no | no | 1 table | Apache-2.0 |
| Quartz, JDBC clustered | yes | yes (misfire instructions) | yes | no | limited | no | `QRTZ_*` tables | Apache-2.0 |
| Spring Batch 6 | via `JobRepository` | yes (restart) | no | **yes** | skip/retry per item | no | `BATCH_*` tables, or resourceless | Apache-2.0 |
| db-scheduler | yes (row locks; `SKIP LOCKED` polling optional) | partial | yes | no | limited | no | 1 table | Apache-2.0 |
| JobRunr | yes | partial | yes | no | yes (exponential) | **dashboard** | tables | LGPL-3.0; Pro commercial; OSS capped at 100 recurring jobs |
| Kubernetes CronJob | `concurrencyPolicy: Forbid` | manual backfill | no | no | platform | platform | cluster | — |

Status facts that constrain templates (sources in the matrix file):

- Spring Batch 6 (GA 2025-11-19, with Boot 4): resourceless `JobRepository` by default,
  `JobBuilderFactory`/`StepBuilderFactory` removed, `JobOperator` extends `JobLauncher`.
- Spring Cloud Data Flow OSS ended (2.11.x last, April 2025) — no template. Spring Cloud Task
  itself is still maintained.
- Starter coordinates differ per Boot major (e.g. db-scheduler's `-spring-boot-4-starter`) —
  resolved at runtime from the project's Boot version, never written from memory (invariant 8).
- `@Scheduled` without virtual threads runs on a pool of **1** by default: two jobs serialize.
- Outbox relay alternatives: polling + `FOR UPDATE SKIP LOCKED` (default), Debezium CDC,
  Spring Modulith event publication registry. The relay *form* stays `messaging-architect`'s
  decision; only its schedule and coordination move.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `skills/jobs-architect/` (Form 1, class `design`) + `rules/scheduling.md` (Form 4), taking over the relay's schedule and the prune job | 8 | **Approved** |
| 2 | `skills/jobs-architect/` alone, job norms in its `references/` | 5 | Rejected — the executor never loads a design skill's `references/` |
| 3 | No new skill — extend `persistence-architect` (prune) and `messaging-architect` (relay lock) | 4 | Rejected — non-outbox jobs stay ownerless; coordination split over two skills |
| 4 | Create nothing | 2 | Rejected — the same ownerless job observed twice |

### Option 1 — `jobs-architect` + `rules/scheduling.md` (score 8)

**Motivator:** axes 1, 9 and the norm answer — two runs left the prune ownerless, and no piece
decides tool or coordination.

**Pros:** one owner for "when and where code runs on its own"; closes lessons-learned-014 § 9
at the root instead of with another `Deferred` row; non-outbox jobs get a design path; the
executor gets job norms by `paths`, not by remembering a skill's reference.

**Cons:** biggest propagation of the four — rewrites ownership lines in `messaging-architect`,
`persistence-architect`, `new-feature`, `rules/messaging.md` and two exemplars. The trigger in
`infrastructure.scheduling` must call an **input port**, so the relay becomes an application
service behind a port instead of a `@Scheduled` adapter class — a real refactor of
`OutboxRelayPublisher.java.example`. The package needs a `packages.map` entry in each blueprint
(data, invariant 7), which this designer's contract does not write — until then the rule's
`derived_paths` entry is `optional` and inert.

**Pipeline order (adjusted from the interview):** the replica count was to be inherited from
`20-persistencia.md` § 1, but tool tables (`shedlock`, `QRTZ_*`, `BATCH_*`) are schema, and a
schema requirement born after persistence runs forces a second persistence pass — the failure
`new-feature` already records (0049). So `jobs-architect` runs **after messaging, before
persistence**, like messaging does: it inherits the replica count from any earlier use case's
`35-jobs.md` or `20-persistencia.md` § 1 Claim strategy, asks only when none exists, and hands
the tables to persistence as requirements. Persistence then reads the replica count from
`35-jobs.md` when it picks the claim strategy.

**Points cut in the rubric:** 5 (maintenance: many files touched) · 7 (propagation depends on
blueprint edits outside this skill's contract).

### Option 2 — skill only, norms in `references/` (score 5)

Fewer files, but the executor never loads a design skill's `references/`, so the norms
(`fixedDelay`, gate property off in tests, idempotent body, lock bound) reach code only through
the partial — per use case, re-stated each time. Strains invariant 2's spirit: a norm owned by
a procedure file. Cut: 2, 4, 6.

### Option 3 — extend the two existing skills (score 4)

Gives the prune to `persistence-architect` and the lock to `messaging-architect`. Leaves every
non-outbox job without an owner, splits one concern (coordination) across two skills that
would each re-derive the replica answer, and puts a technology choice (Quartz vs ShedLock vs
Batch) into skills whose subject is tables and brokers. Cut: 1, 2 (invariant 2 pressure), 5, 6.

### Option 4 — create nothing (score 2)

Rejected: axis 1 has two observed runs with the same ownerless job.

## References

| Claim | Source |
|---|---|
| A pipeline design piece is Form 1, not Form 2 | `claude-code-architect-designer/references/decision-matrix.md` § 4 · `decisions/0007-pipeline-skills-invocation.md` |
| No agent: the interview is the task | decision matrix § 5 counter-test |
| Norm in one owner, cited by path | `@CLAUDE.md` invariants 1 and 2 |
| Boilerplate only in `templates/*.example` | `@CLAUDE.md` invariant 3 |
| Versions and starter coordinates resolved at runtime | `@CLAUDE.md` invariant 8 |
| Package territory is blueprint data | `@CLAUDE.md` invariant 7 · `export.derived_paths` in `schemas/extensions.json` |
| Development skill must be in `export.skills.include` and a class | `@CLAUDE.md` invariant 9 |
| A late schema requirement forces a second persistence pass | `decisions/0049-messaging-before-persistence.md` |
| The prune job left ownerless | `lessons-learned/lessons-learned-014.md` § 9 · demo `application.yml:63` |
| Unlocked claim assumes one relay | `persistence-architect/templates/OutboxEventStore.java.example` ("SINGLE RELAY INSTANCE IS ASSUMED") |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/jobs-architect/SKILL.md` | New — entry rule (schedule trigger, Form B, deferred job), inherit-first survey, interview, matrix-driven choice, nine-block partial |
| `.claude/skills/jobs-architect/references/tool-decision-matrix.md` | New — the research of this record, dated and sourced, plus the scenario × tool matrix |
| `.claude/skills/jobs-architect/templates/*.example` | New — `jobs-spec.md`, `ScheduledJob` (shared `SchedulingConfig` + `JobRunRecorder`), `ShedLockJob`, `QuartzJob`, `SpringBatchJob` (Batch 6 API), `DbSchedulerTask`, `JobRunrJob`, `OutboxRelayJob`, `OutboxPruneJob`, `application-jobs.yml` |
| `.claude/rules/scheduling.md` | New norm — boundary, triggers, execution guarantees, observability, retention, How to verify |
| `.claude/rules/00-index.md` | `scheduling.md` row |
| `.claude/rules/messaging.md` | `…messaging.outbox` no longer holds scheduling wiring; prune cites `scheduling.md` § Retention |
| `.claude/rules/observability.md` | `paths` gain the two scheduling globs |
| `.claude/schemas/extensions.json` | `skill_classes.design.skills`, `export.skills.include`, `derived_paths["scheduling.md"]` (optional, suffix `.scheduling`), `.scheduling` added to observability's suffixes |
| `.claude/blueprints/*/*.yaml` (8) | A `packages.map` key ending in `.scheduling`; layered also gains the `scheduler` glob and dependency rules; hexagonal and onion compile it into the composition-root module until a project needs build isolation |
| `.claude/skills/messaging-architect/SKILL.md` + templates | Relay split: `RelayOutboxEvents` + `RelayOutboxEventsService` + `OutboxEventSender`/`KafkaOutboxEventSender`; no `@Scheduled`/`SchedulingConfig` in the messaging adapter; boundary table and handoff name `jobs-architect` |
| `.claude/skills/persistence-architect/SKILL.md` + templates | Reads `35-jobs.md` § 3 (replica count → claim strategy) and § 6 (tool tables, claim requirement, prune operation); poll interval leaves its territory; prune no longer routed through `Deferred`; `OutboxEventStore` implements `OutboxRetentionGateway` with a bounded native delete |
| `.claude/skills/new-feature/SKILL.md` + `feature-spec.md.example` | Step 4b, order and its reason, precedence rows, declared-dependency gate on `35-jobs.md` § 7, spec block `3.6 Jobs` |
| `.claude/agents/java-spring-boot-developer.md` | Block J (J1-J3); Form B list rewritten for the split; block detection by heading text (`^## [0-9.]+ Messaging` / `Jobs`) — the old `^## 6` check matched no spec the template produces |
| `.claude/skills/test-architect/SKILL.md` | Reads `35-jobs.md`; jobs tested through their inbound port, plus a context test that no trigger runs under the test profile |
| `CLAUDE.md` | Routing row; the outbox pitfall rewritten for three owners |
| `README.md`, `docs/{en,pt-br}/00,02,03,09` | Pipeline order, partial list, precedence table, skill and norm lists |

Goes to the generated project: **yes** — `jobs-architect` via `export.skills.include`,
`scheduling.md` via `export.rules` with its glob derived from the blueprint's `.scheduling`
entry. Checked with `ArchHook.java export` into four blueprints (clean single-module,
hexagonal, layered, vertical-slice) and `schema` inside the result.

Not changed, on purpose: projects already generated keep their `@Scheduled` relay until they
pull this version with `/arch-adopt` and run a case that touches it — the refactor is an
exemplar change, not a migration of existing code.

## Template review — same day, before commit

Read side by side, `jobs-architect/templates` and `messaging-architect/templates` left eight
places where a run implementing messaging and jobs together would have had to guess:

| Doubt | Resolution |
|---|---|
| `app.outbox.*` shaped in both yml exemplars — two versions of the same keys, and a risk of a second `app:` root | One exemplar per key: the pass's `batch-size`/`max-attempts` in `application-kafka.yml.example`, everything else in `application-jobs.yml.example`; both say "one map, merged" |
| Relay and prune services built by hand in `@Configuration`, unlike every other use case (`@Service` in `domain-modeling`'s exemplar) | Both `@Service`; values arrive as application-owned `OutboxRelaySettings` / `OutboxPruneSettings`, produced by the adapter's config |
| Hexagonal names (`RelayOutboxEvents` + `…Service`) in a clean-architecture project | Vocabulary note in both exemplars: the blueprint's convention wins — one concrete `…UseCase` |
| Relay trigger always `@Scheduled` vs "one technology per project" | The relay stays on `@Scheduled` (claim-coordinated polling pass) — stated in the rule, the matrix § 3, the skill and the exemplar; the prune follows the project's technology |
| `@SchedulerLock` hard-coded on the prune | Marked conditional on `35-jobs.md` § 3; no ShedLock dependency by copying |
| A project with the pre-split relay (the demo) — reuse or migrate? | Never both shapes: impact row plus migrate-now or defer-with-owner, in the skill (step 2), the relay exemplar and executor Block J |
| `app.jobs.<job>.*` vs `app.outbox.*` for gates | Prefix convention written in `application-jobs.yml.example` |
| vertical-slice kept a slice's own job inside the slice, which the rule's grep rejects; the grep also rejected layered's `scheduler` package | Every trigger in `config.scheduling` for vertical-slice; the grep accepts `scheduling` and `scheduler` |
