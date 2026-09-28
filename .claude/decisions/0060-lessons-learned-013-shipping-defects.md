# 0060 · Remediation of lessons-learned-013 — the shipping defects

- **Date:** 2026-09-28
- **Scenario:** `@.claude/lessons-learned/lessons-learned-013.md` — the `UC-003-initiate-kyc-verification`
  implementation run in `demo-clean-arch-single-module`, the first run to exercise the outbox's
  Form B end to end. Eleven sections plus a header note. The user scoped this run to the
  **shipping defects**: the items that leave a broken or missing artifact in a project generated
  today, not the ownership arbitrations.
- **Decision:** Option 2 — the shipping defects fixed in the files that already own them,
  with `@Scheduled` + `@ConditionalOnProperty("app.outbox.enabled")` as the relay's scheduler.
  No new piece, no new hook mode, no new dependency in this meta-repository: `ArchHook.java`
  (`guard`'s third frozen-folder exemption, `compose`'s fourth question), `extensions.json`
  (`guard.frozen_exempt_basenames`, a new `compose` block, `agent_classes.executor` gains
  `pom.xml`, `export.derived_paths.observability.md` gains `.messaging`), one agent, four
  skills, one rule, four templates, `@CLAUDE.md` and four `docs/` pages.
- **State:** approved by Lucas Fernandes, on 2026-09-28

## Scope of this run

In: §§ **1, 2, 3, 6, 8, 11**, plus the trailing note (`OutboxRelayPublisher.java.example`
instructs `catch (RuntimeException e)`, which the generated project's own Checkstyle
`IllegalCatch` rejects). Item **10** is pulled forward from Run B because Option 1 adds a
dependency the exemplar declares, which is exactly the hole § 10 names.

Out, and deliberately so — a second run takes them, with three mechanisms **already chosen**
by the user during this interview:

| § | Deferred item | Mechanism already answered |
|---|---|---|
| 4 | `claimPending` does not claim; dedupe delegated to an external team | `persistence-architect`'s outbox step gains an explicit axis — `FOR UPDATE SKIP LOCKED`, a lease column, or a documented single-instance constraint — written into `20-persistencia.md` § Claim strategy. The exemplar keeps the unlocked read as the documented single-instance case. `messaging-architect` must name who guarantees dedupe when the consumer is not in this project |
| 7 | PII (CPF) at rest in `outbox_events.payload` and in transit on the topic | Write `.claude/rules/security.md` now — promoted out of `00-index.md`'s "planned" table, PII at rest and in transit as its first scope, no `paths` (always cited) — **and** an explicit serialization question in `messaging-architect` step 3 |
| 5 | An impact row left `UC-002` unreachable end-to-end | Not yet answered |
| 9 | `CLAUDE.md` module table vs. `messaging.md`'s grep over adapter-local `@Configuration` | Not yet answered |

§ 10's answer, applied in **this** run: widen `agent_classes.executor.write_allow` with
`pom.xml` / `**/pom.xml`, and the executor may add only a dependency the spec's § 3 or § 6
names as a declared requirement. The guard cannot express "only dependency blocks" — the
widening is file-level, and that is the cost.

## Reproduced on disk before classifying

| § | Reproduction |
|---|---|
| 1 | `ArchHook.java:3965` — the frozen-folder check exempts `isStatusClose \|\| isChecklistToggle` and nothing else. No `CHANGELOG.md` path, and the error message's advice (`set status: draft`) is wrong for a changelog append |
| 2 | `java-spring-boot-developer.md:360-408` — Block M generates `[Event]Publisher` with a direct `KafkaTemplate` send, a `@KafkaListener`, `ProcessedEventStore` and `DefaultErrorHandler`. Zero lines on a relay, a scheduler, or outbox state. Four of its five validation items are consumer-side |
| 4 | `persistence-architect/templates/OutboxEventStore.java.example:232` — `claimPending` is a plain `findByPublishedAtIsNull…`; the same file's Javadoc (lines 41-50) states "SINGLE RELAY INSTANCE IS ASSUMED" and names the lease as a schema change belonging in the partial. Neither partial template asks for the decision |
| 6 | Block M cites `messaging.md`, `architecture-ddd.md`, `naming.md`, `code-quality.md`, `lombok.md`, `error-handling.md`, `logging.md` — not `observability.md`, which is cited by Block 3 only |
| 8.2 | `OutboxEventStore.java.example:245-255` — reload-before-write is the exemplar's documented shape, not an executor invention |
| 10 | `agent_classes.executor.write_allow` = `src/**`, `**/src/**`, `docs/use-cases/UC-*/UC-*-spec.md`. The only `pom.xml` writers are the two `installer` agents |
| 11 | `docker-architect/templates/kafka-service.yml.example` — `ports: "9092:9092"` together with `KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092`. The contradiction is in the exemplar verbatim |
| note | `messaging-architect/templates/OutboxRelayPublisher.java.example:135` — `catch (RuntimeException e)` |

## JobRunr — verified 2026-09-28, before it entered any option

| Fact | Source |
|---|---|
| Latest stable `org.jobrunr:jobrunr-spring-boot-3-starter` = **7.5.1**; `8.0.0-beta.1` is published and is a beta | Maven Central `solrsearch`, group `org.jobrunr` |
| No `jobrunr-spring-boot-4-starter` exists | same query |
| 7.5.1 and 8.0.0-beta.1 both declare `spring-boot-starter:3.4.4`, scope `provided` | the published POMs |
| Dual license: LGPL v3 or later, **or** commercial | the POM's `<licenses>` block |
| Needs a `StorageProvider` and creates its own tables; embedded dashboard over HTTP; `@Recurring(cron=…)`; distributed execution "once and only once"; retries built in | <https://www.jobrunr.io/en/blog/2023-02-13-java-scheduler/> |

**Unverified, and it gates Option 1:** the generated project is Spring Boot 4, and the starter
compiles against Boot 3.4.4. `provided` scope means the application's own Boot supplies it, so
it may work — but Boot 4 moved autoconfiguration packages, which is the same fact this run
discovered for Kafka (`spring-boot-autoconfigure` carries no `kafka` package). Compatibility
cannot be settled by reading; it needs a compile and a context start.

## Interview

| Axis | Answer | Forms/options it eliminated |
|---|---|---|
| 1 — symptom | Eleven sections from one real run; eight of them reproduced on this machine today, two of which (§ 1, § 11) are live in every project generated now | "create nothing" for the whole run |
| 5 — nature | Every Run A fix lands on a file that already owns the subject: `ArchHook.java`, one agent, three skills' `templates/`, one rule, `extensions.json` | Forms 1, 3, 4 (except `observability.md`'s line), 6, 7b |
| 7 — mandatoriness | § 1 and § 11 are defects of a program and of an exemplar, not of judgment; both recur silently — the § 11 healthcheck passes with the listener misconfigured | Prose-only fixes for § 1 and § 11 |
| 8 — destination | **Both.** `ArchHook.java`, `extensions.json`, the agent, the three skills and `observability.md` are all in `export`'s payload | — |
| 16 — existing mode | `guard` already runs on every write and `compose` already parses the compose file and extracts published host ports (`ArchHook.java:481,599`) | A new Java mode (Form 7c as a *new* mode) |
| Scope | **Split.** Shipping defects now (§§ 1, 2, 3, 6, 8, 11); ownership arbitrations (§§ 4, 5, 7, 9, 10) in a second run | One eleven-section approval |
| § 1 — exemption breadth | **Basename match, any write.** A path whose basename is exactly `CHANGELOG.md` directly under a frozen `UC-NNN` folder is exempt regardless of tool or phase, and the error message is corrected | Append-only detection (the guard would have to reimplement `Edit`'s semantics to know an append is an append) |
| § 2 — where Form B's shape lives | **Agent body + new exemplars**, with **JobRunr** as the relay's scheduler instead of `@Scheduled`/`@EnableScheduling` | Prose-only Block M; exemplars with no validation list |
| § 3 — relay must not run in the ITs | **Property guard on the component.** Under JobRunr this is superseded by `org.jobrunr.background-job-server.enabled: false`, a framework property instead of project code | Test-profile-only (each new IT has to remember) |
| § 6 — dead-lettering is silent | **Counter + pending-age gauge, and a rule line** in `observability.md`, so the norm holds when Block M is not the writer | Counter alone; a validation item with no norm behind it |
| § 8.3 — duplicated carriers | **Merge in the exemplar** — one carrier record, both directions | A `naming.md` line keeping them separate by direction |
| § 8.5 — `spring.kafka.*` squatted by `@Value` reads | **Adopt the starter, with the exemplar stating why** — `KafkaProducerConfig` exists only for what the starter cannot express, and its Javadoc names the Boot 4 fact the run discovered | Moving the hand-wired properties to `app.kafka.*` |
| § 11 — the `bootstrap-servers` default | **`messaging-architect` owns it**, written when the producer is first wired | The blueprint's `application.yml`; both files with a verification row |
| § 11 — what catches the class next time | **Textual check in the existing `compose` mode**: a service that publishes a port to the host and declares an advertised-address env key must advertise at least one host-reachable address. Deterministic, no Docker, folded into `doctor` | A host-side client run as a `docker-architect` step; both |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Run A with JobRunr as the relay's scheduler — the user's first § 2 answer | 8 | Rejected by the user once the verification showed what it costs: no Boot 4 starter, LGPL-or-commercial in every generated project, its own tables, its own HTTP dashboard |
| 2 | The same Run A with `@Scheduled` + `@ConditionalOnProperty("app.outbox.enabled")` | 9 | **Approved** |
| 3 | Trim Run A to § 1 + § 11 | 6 | Rejected — Block M would stay Form A-only and the next Form B run infers the same five classes again |
| 4 | Create nothing | 2 | Rejected — § 1 blocks a write the pipeline's own design requires, and § 11 ships a broker no host client can reach |

### Option 1 — Run A with JobRunr (score 8)

**Motivator:** § 2 axis 1 — the executor inferred `SchedulingConfig`, `OutboxProperties` and
`KafkaProducerConfig` in full, and a missing `@EnableScheduling` makes the whole of Form B
silently inert; the user's answer names JobRunr as the mechanism.

**Pros.** Three inferred classes collapse into one starter and a `@Recurring` method. The test
switch becomes a documented framework property rather than project code an IT author must
remember (§ 3). "Once and only once" across instances turns § 4's duplication from an
undesigned race into a configuration fact. The dashboard gives the failure path somewhere to be
looked at, on top of § 6's counter and gauge. Adopting the Kafka starter (§ 8.5) returns
`spring.kafka.*` to its owner, so the serializer declarations that are inert today become live.

**Cons.** Boot 4 compatibility is unverified and cannot be settled by reading. The LGPL
v3-or-commercial dual license enters every generated project. JobRunr writes its own tables into
the application database, which no persistence partial has had to account for, and the dashboard
is an HTTP endpoint the generated project did not previously expose. No precedent: this repo has
never chosen a third-party runtime framework outside Spring, Kafka and Testcontainers.

**Points cut in the rubric:** criterion 6 — no precedent for a third-party runtime framework
chosen by this repo; criterion 8 — the dashboard endpoint plus the license surface are capability
the task did not ask for.

**Gate, written into the exemplar's step:** resolve the version from Maven Central at write time
(invariant 8 — never from memory), then `./mvnw -q test-compile` plus a context-start smoke run.
If it fails on Boot 4, the scheduler half falls back to Option 2's shape and every other item of
this option stands unchanged.

### Option 2 — the same Run A, `@Scheduled` kept (score 9)

`SchedulingConfig` and `OutboxProperties` ship as exemplars;
`@ConditionalOnProperty(name = "app.outbox.enabled", matchIfMissing = true)` guards the relay and
the config, the test profile sets it false, and a mocked `KafkaTemplate` stands in where a test
needs one. Identical to Option 1 in every other item, § 8.5's starter adoption included.

**Pros.** No dependency, no license question, no Boot 4 unknown, no extra tables, no new HTTP
endpoint. Highest rubric score: the same fixes at the smallest surface.

**Cons.** § 4's multi-instance duplication stays entirely open for Run B. Relay enablement is
hand-rolled project code, so an IT that forgets the property reintroduces § 3. No dashboard —
the counter and the gauge are all the observability there is.

**Points cut in the rubric:** criterion 4 — enablement by convention where a framework property
was available.

**What the approval settled about JobRunr.** Not a fallback and not a deferral: the scheduler
question is closed with `@Scheduled`. Reopening it needs a new symptom — the relay failing to
run where the property says it should, or a second recurring job with real distribution needs —
and, before any exemplar names it, a Boot 4 compile-and-boot check, because
`jobrunr-spring-boot-3-starter` (7.5.1 stable, 8.0.0-beta.1 beta) compiles against
`spring-boot-starter:3.4.4` and no Boot 4 starter is published.

### Option 3 — trim Run A to § 1 + § 11 (score 6)

Only the two items that ship a broken artifact today. § 11's fix reaches every project generated
tomorrow, and the approval is small. Rejected on cost: Block M stays Form A-only, so the next
Form B run infers the same five classes again — and that run already exists, because §§ 4 and 5
are deferred.

**Points cut:** criterion 7 — propagation left half-done by design; criterion 5 — Block M is
opened twice for one subject.

### Option 4 — create nothing (score 2)

Recorded only to name what it would mean: `CHANGELOG.md` stays unwritable under a frozen folder,
with an error message recommending a fix (`status: draft`) that is worse than the block, and
every project generated today inherits a Kafka broker no host-side client can reach.

## Two items that need no change

- § 11's trailing note — the generated `docker-compose.yml` comment citing
  `UC-003-request-customer-kyc-validation`, a folder that never existed — is already covered by
  `doctor.uc_references`, added in 0056. It reports and does not block, which is the right level
  for a stale citation.
- § 8.6 — `app.outbox.prune-after: P7D` has no reader. Under Option 1 or 2 the property becomes
  the `OutboxProperties` exemplar's problem, and an exemplar can carry a property that is read or
  no property at all. Proposed: delete it and move the retention figure to the index comment that
  assumes it, unless the pruning job comes into scope.

## References

| Claim | Source |
|---|---|
| The new check belongs to a mode that already exists, not to a new one | `references/decision-matrix.md` § 2.2 row 4; § 7 anti-pattern 17 |
| A blocking hook must name the way out — § 1's message currently misnames it | `references/decision-matrix.md` § 7 anti-pattern 18 |
| Lists a mode reads are data in `extensions.json`, never constants in the Java | `@CLAUDE.md` invariant 10 |
| Boilerplate lives in `templates/` with the `.example` suffix, never in a body | `@CLAUDE.md` invariant 3 |
| Versions are never written from memory | `@CLAUDE.md` invariant 8; precedent `0057` item 13 (image tag verified in the registry before the block is written) |
| `compose` already parses the compose file and already extracts each service's published host ports | `.claude/hooks/ArchHook.java:481,599` |
| The frozen-folder guard exempts exactly two write shapes | `.claude/hooks/ArchHook.java:3965,3986,4002` |
| The outbox's single-instance assumption is the exemplar's own documented choice, and the lease is named there as a schema change | `.claude/skills/persistence-architect/templates/OutboxEventStore.java.example:41-50` |
| Persistence owns the outbox table, its columns and the relay's pacing; messaging owns the broker side and the delivery guarantee | `.claude/decisions/0057-lessons-learned-012-inferences.md`, item 9 |
| Executor territory is `src/**` plus the spec's `status:` line | `agent_classes.executor.write_allow` in `.claude/schemas/extensions.json` |
| A subagent's write is judged by its own class, not by the caller's phase | `.claude/decisions/0059-agent-classes-territory-schema.md` |
| JobRunr's versions, license, starter naming and Boot 3.4.4 compile target | Maven Central `solrsearch` and the published POMs, queried 2026-09-28 |
| JobRunr's feature set (`@Recurring`, dashboard, distributed once-and-only-once, built-in retries, `StorageProvider`) | <https://www.jobrunr.io/en/blog/2023-02-13-java-scheduler/> |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | `guard`: third frozen-folder exemption (`isFrozenExempt`, basename directly under the folder) and a rewritten block message that names what stays writable. `compose`: a fourth question — `advertisedAddressIssues` + `composeServiceEnv`, both file-only, folded into the report that survives a dead daemon. The duplicate `unquote` was dropped in favour of the one already there |
| `.claude/schemas/extensions.json` | `guard.frozen_exempt_basenames: ["CHANGELOG.md"]`; new top-level `compose` block (`advertised_env_suffixes`, `host_addresses`); `agent_classes.executor.write_allow` gains `pom.xml` / `**/pom.xml` with the bound written into its `$comment`; `export.derived_paths.observability.md` gains the `.messaging` suffix |
| `.claude/agents/java-spring-boot-developer.md` | Block M split by form: the existing list is Form A + consumer, and a new **Form B** sub-block names every class the run had to infer (relay, `SchedulingConfig`, `OutboxProperties`, the § 7 dependency, the test property) with a validation list of its own — six items, none `n/a` under Form B. `observability.md` added to the reads for Block M. Block 4 gains the relay unit test. `pom.xml` enters Writes, bounded to spec-declared dependencies; Does-not-write and the Contract line updated |
| `.claude/rules/observability.md` | § Metrics gains the durability bullet — a deferred delivery declared must-not-be-lost carries a give-up counter and a pending-age gauge, both owned by the sweeping component. `paths` gains the two messaging globs. Written without naming any skill or agent — invariant 1 |
| `.claude/skills/messaging-architect/templates/OutboxRelayPublisher.java.example` | `OutboxEventRecord` replaces `PendingOutboxEvent` as the single carrier for both directions; `markDeadLettered` added and `markFailed` now returns whether it dead-lettered; `oldestPendingOccurredAt` added for the gauge; `SchedulingConfig` and `OutboxProperties` blocks added; relay gated by `@ConditionalOnProperty`, metered with a counter and a gauge, and catching `CompletionException` instead of `RuntimeException` (Checkstyle `IllegalCatch`); constants replaced by the bound properties |
| `.claude/skills/messaging-architect/templates/application-kafka.yml.example` | `spring-boot-starter-kafka` named as the dependency that makes the file do anything (Boot 4 moved the autoconfiguration out of `spring-boot-autoconfigure`); `bootstrap-servers` default now host-first (`localhost:29092`); `app.outbox.enabled` added; `prune-after` kept with the instruction to ship it only with its reader |
| `.claude/skills/messaging-architect/templates/messaging-spec.md.example` | new **§ 7 · Declared dependencies** block, the list the executor is allowed to act on; `bootstrap-servers` row in § 5; implementation order starts with the dependencies |
| `.claude/skills/messaging-architect/SKILL.md` | step 7 gains the host-first default and the dependency declaration; step 8 counts seven blocks; step 10 reports § 7 |
| `.claude/skills/new-feature/SKILL.md` | the messaging validation reads seven blocks, and § 7's role as the executor's only `pom.xml` licence is stated |
| `.claude/skills/persistence-architect/templates/OutboxEventStore.java.example` | the three marks are single `@Modifying` statements (`markPublished`, `deadLetterIfExhausted` + `recordAttempt`, `markDeadLettered`) plus a scalar `oldestPendingOccurredAt`; the entity's mutators are gone; `@Transactional` on the marks only, with the reason; carrier renamed |
| `.claude/skills/persistence-architect/SKILL.md` | carrier renamed in both places; the retention property ships only with the job that reads it |
| `.claude/skills/docker-architect/templates/kafka-service.yml.example` | two listeners, `PLAINTEXT` for the compose network and `EXTERNAL://localhost:29092` for the host, with `KAFKA_INTER_BROKER_LISTENER_NAME` and the protocol map extended; a header explaining why one listener cannot serve both and why nothing runtime catches it |
| `.claude/skills/docker-architect/SKILL.md` | step 7's verification command now covers the advertised-address check, with what it means when it fires and the note that it answers with the stack down |
| `CLAUDE.md` | three pitfalls: the three writes a frozen folder admits, a healthcheck proving nothing about host reachability, and `pom.xml`'s single writer inside a feature run. The `compose` rows in § Commands and § Routing updated |
| `docs/04-arch-doctor.md` · `docs/en/04-arch-doctor.md` | the `Compose` row and the intro describe the fourth check |
| `docs/03-new-feature.md` · `docs/en/03-new-feature.md` | the immutability bullet names the `CHANGELOG.md` exemption |

**Not done, and deliberately:** no `testing.md` line for the relay-in-tests rule. The user's
§ 3 answer was the component property alone, and the property is what the exemplar and Block M
enforce; a norm on top of a mechanism that already holds is invariant 6's mirror.

**Verified by running it, not by reading it:**

- `guard` — a `CHANGELOG.md` write under an `implemented` folder exits 0; a partial in the same
  folder and a nested `notes/CHANGELOG.md` still exit 2, with the new message.
- `compose` — the old Kafka block reports the defect by name; the two-listener block reports
  nothing; `localhost:29092` advertised without publishing 29092 is caught too; a service with
  no advertised key is left alone; list-form `environment:` parses.
- `schema` (full sweep) and `claude plugin validate .claude/skills` pass; `doctor` reports
  `Schema ✅` and 15 registrations across 4 events, unchanged.
- `export` for `clean-architecture-multi-module` derives
  `**/infrastructure/messaging/**` into `observability.md`'s `paths`; `vertical-slice` keeps its
  single `**/config/**` glob, as before this change.

Goes to the generated project: **yes** — `ArchHook.java`, `schemas/extensions.json`,
`rules/observability.md` and the three development skills' `templates/` are all in `export`'s
payload; `java-spring-boot-developer.md` travels with `export.agents`. The
`docker-architect` Kafka exemplar reaches a project through the skill itself, which is a
`build`-class skill that travels.
