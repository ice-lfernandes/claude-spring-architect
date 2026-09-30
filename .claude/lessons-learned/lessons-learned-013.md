# Lessons learned 013 — `UC-003-initiate-kyc-verification` implementation run

Run date: 2026-09-28. Scope: the executor (`java-spring-boot-developer`) and post-executor
phase of `UC-003`, after `UC-003-spec.md` was already `approved`. Not a record of what got
implemented — that's the spec and the code. This file is about the **pipeline's own blind
spots** hit while closing the spec, same shape as `lessons-learned-001.md`.

Every fix below lands in the **meta-repository** (the blueprint/skill source), not in this
generated project. This file is the input to that work, not a task list for this repo.

## § 1 · `ArchHook.java`'s frozen-folder guard has no exception for `CHANGELOG.md`

**What happened.** `UC-003-spec.md` § Impact on approved use cases names four changes to
`UC-001-register-customer` and `UC-002-create-account`'s code. `.claude/skills/new-feature/SKILL.md`
§ Consolidation step 2 requires one line per change written to each affected case's own
`CHANGELOG.md` ("created on the first change, appended afterwards ... The approved spec
stays untouched, which is exactly why the log is a separate file"). Attempting that write
after the executor finished got refused:

```
❌ UC-001-register-customer is implemented — its specs are immutable.
Record the change in the new use case's "Impact on approved use cases" section.
To reopen a spec that was never implemented, set `status: draft` by hand.
```

**Why this is a gap, not a one-off.** `ArchHook.java`'s frozen-folder check (guard, around
line 3956) only exempts two write shapes inside a frozen `UC-NNN` folder:
`isStatusClose` (the spec's own `status:` line closing to `implemented`) and
`isChecklistToggle` (a `[ ]` → `[x]` line in the same spec). There is no third exemption
for a `CHANGELOG.md` write — the one write path `/new-feature`'s own design explicitly
relies on to keep an approved spec immutable while still recording what changed
underneath it. The skill's design and the hook's enforcement diverge: one requires the
write, the other blocks it categorically, with an error message ("set `status: draft` by
hand") that is actively wrong advice here — reopening `UC-001` to `draft` to unblock a
one-line changelog append would be a far worse move than the block itself.

**What actually happened here.** Deferred by the user's explicit choice rather than
worked around. The four `CHANGELOG.md` lines UC-003 owes are captured below, not yet
written to disk:

- `docs/use-cases/UC-001-register-customer/CHANGELOG.md`:
  - `CustomerEntity`/`CustomerPersistenceMapper` gain the `status` column/field;
    migration `V4__add_status_to_customers.sql` alters this use case's `customers` table.
  - `RegisterCustomerRequest`/`RegisterCustomerResponse` gain
    `@MaskSensitiveData(maskedType = DOCUMENT)` on `securityNumber` and
    `implements LogMask` (masking gap predated `UC-003`, fixed there).
  - `CustomerRepositoryJpaAdapterIT` gains a status round-trip test;
    `RegisterCustomerRequestTest`/`RegisterCustomerResponseTest` added.
- `docs/use-cases/UC-002-create-account/CHANGELOG.md`:
  - `CreateAccountUseCase` now rejects account creation when
    `Customer.status() != ACTIVE` (new `CustomerRepository.findById`, new `errorCode`
    `CUSTOMER_NOT_ACTIVE` on `BusinessRuleViolationException`, HTTP 422).

**Lesson / suggested fix.** Add a third exemption to `ArchHook.java`'s frozen-folder guard,
parallel to `isStatusClose`/`isChecklistToggle`: a write whose path's basename is exactly
`CHANGELOG.md` directly under a frozen `UC-NNN` folder is always allowed, regardless of
which class or phase is open. This is a hook change (`.claude/hooks/ArchHook.java`), and
per `CLAUDE.md` § The boundary is not enforced by the compiler, editing hooks mid-session
has no effect until `claude` restarts — so it can't be applied and exercised in the same
session that discovers it.

## § 2 · Block M documents only Form A — the executor inferred all of Form B

**What happened.** `25-mensageria.md` decided **Form B** (transactional outbox + relay),
because the event is marked "must not be lost". `java-spring-boot-developer.md`'s
`### Block M: Messaging (steps M1-M4, conditional)` describes only **Form A plus a
consumer**: `[Event]Publisher` implementing `Publish[Event]Port` with a direct
`KafkaTemplate` send, `[Event]Listener` with `@KafkaListener`, `ProcessedEventStore`,
`DefaultErrorHandler` retry/DLQ. Not one line about a relay, a scheduler, or outbox state
transitions.

So the executor inferred, unsupervised, every one of these:

- `SchedulingConfig` with `@EnableScheduling` — nothing in the spec, the partials, or the
  agent asks for it; without it `@Scheduled` is inert and the whole of Form B silently
  does nothing.
- `OutboxProperties` (`app.outbox.*` binding) and which of the four properties can be
  bound to a record vs. must stay a placeholder string (`poll-interval` drives
  `@Scheduled` directly).
- `KafkaProducerConfig` in full — `ProducerFactory`, `KafkaTemplate`, serializers, and the
  discovery that Spring Boot 4's `spring-boot-autoconfigure` carries no `kafka` package,
  so a plain `spring-kafka` library dependency wires nothing on its own.
- Inline topic resolution instead of the `TopicResolver` the template names as "elided,
  adapter-local" — a defensible OCP call, but a divergence from the exemplar that nothing
  in the pipeline flagged either way.
- The dead-letter-on-unmapped-type path, expressed as `markFailed(..., maxAttempts = 0)`.

**The validation list made it worse, not better.** Block M's own `Validation:` line reads:
"payload is never the domain event, partition key is the aggregate id, the offset commits
only after the use case returns, a business rejection routes to the DLQ without retrying,
and the dedupe check runs before the use case is called". Four of those five are
consumer-side and `n/a` for this spec. Block M therefore ran with essentially **no
applicable validation at all** — nothing checked that the relay never sends inside the
appending transaction, that the claim is safe under more than one instance, or that an
exhausted row is observable.

**Lesson / suggested fix.** Give Block M an explicit Form B half, symmetric with the Form A
one it already has, and its own validation list:

- the relay is the only class importing `KafkaTemplate`, and it never runs inside the
  appending transaction;
- the claim is safe with N application instances (see § 4);
- an exhausted/dead-lettered row raises a metric, not only a log line (see § 6);
- the relay has a unit test that needs no broker (see § 8);
- `@EnableScheduling` exists, and scheduling is disabled in the test profile (see § 3).

## § 3 · The relay runs inside the integration tests, against no broker

**What happened.** `SchedulingConfig` enables `@Scheduled` application-wide. All three
`*IT` classes are `@SpringBootTest`, so they boot that config. `spring.kafka.bootstrap-servers`
resolves to `localhost:9092` in tests; no broker is started (Testcontainers provides
Postgres only). Net effect during `./mvnw verify`:

- `OutboxRelay.relay()` fires every second against the test database;
- `kafkaTemplate.send(...).join()` blocks on metadata until `max.block.ms` (60s default),
  then fails;
- `markFailed` increments `attempts` on rows the tests just created, and dead-letters them
  once five passes land.

`RegisterCustomerTransactionalOutboxIT` asserts that `claimPending` **contains** the row it
just produced — an assertion the relay can invalidate by marking that same row published or
dead-lettered first. It passes today on timing luck (60s per blocked attempt is longer than
the test), not by construction.

The `TaskUtils$LoggingErrorHandler ERROR ... HikariPool - Connection is not available`
entries the executor reported and classified as "scheduler firing against a datasource being
torn down, not a code defect" are the visible symptom of this, not incidental noise. The
diagnosis was half right — it *is* the scheduler — and the conclusion ("nothing to fix for
M3's scope") was wrong.

**Corroborating evidence.** JaCoCo shows `OutboxRelay` at 8 lines covered / 25 missed. No
test asserts anything about the relay: those 8 lines are covered **only** because the
scheduler executed them during the ITs. Accidental coverage of a class is itself the proof
that the class is running where it shouldn't.

**Lesson / suggested fix.** When Block M runs Form B, the test profile must neutralize the
relay: disable scheduling (a test property, or an `app.outbox.enabled` guard on the
component) and provide a mocked `KafkaTemplate`. This belongs in Block M's validation list
and in `testing.md`, not in the executor's judgment — it is not discoverable from a green
build.

## § 4 · `claimPending` claims nothing — multi-instance duplication is undesigned

**What happened.** The relay's read is
`findByPublishedAtIsNullAndDeadLetteredIsFalseOrderByOccurredAtAsc(Limit)` — a plain
`SELECT`. No `FOR UPDATE SKIP LOCKED`, no lease column, no status transition at claim time.
Two application instances running the relay read the same rows and publish the whole batch
twice, every pass.

The name comes straight from the exemplar
(`messaging-architect/templates/OutboxRelayPublisher.java.example`), which declares
`claimPending` in `OutboxRelayGateway` and implements it with the same unlocked read. The
executor copied a name that describes a guarantee the code does not provide.

**Why this isn't covered by "duplicates are absorbed by the idempotent consumer".** That
sentence appears in the rule, the template, the partial and the shipped Javadoc, and it is
load-bearing for a *different* race — send succeeds, mark fails. Here the duplicate source
is concurrent readers, the multiplier is the whole batch rather than one row, and — decisive
for this project — **the consumer is external**: the KYC service belongs to another team.
`25-mensageria.md` § 5 records "no consumer in this project". So the guarantee is being
delegated across an organizational boundary with no contract written anywhere on this side.

Neither `20-persistencia.md` nor `25-mensageria.md` mentions locking, leasing, or instance
count. A `grep -i "skip locked\|for update\|instance\|concurren"` over both partials returns
only the `@Version` discussion, which is about a different concern.

**Lesson / suggested fix.** `persistence-architect`'s outbox step must decide the claim
strategy explicitly — `SELECT ... FOR UPDATE SKIP LOCKED`, a lease column, or a documented
single-instance constraint — and `messaging-architect` must state who guarantees dedupe when
the consumer is not in this project. If the answer is "nobody on this side", that is a
finding for the final report, not a Javadoc sentence.

## § 5 · An approved use case became unreachable end-to-end, and nothing stopped the run

**What happened.** `UC-003` § Impact on approved use cases requires `CreateAccountUseCase`
to reject a customer whose `status() != ACTIVE`. Implemented correctly. But:

- `Customer.register()` always produces `KYC_IN_PROGRESS`;
- `Customer` has no transition method — only `rehydrate()` can produce another status;
- nothing in `src/main` ever assigns `CustomerStatus.ACTIVE` (verified by grep: the only
  hits are the enum constant, the comparison in `CreateAccountUseCase`, and two comments);
- the use case that would flip it is `UC-004`, still in the backlog.

So after this run, **every** real `POST /api/v1/accounts` returns 422 `CUSTOMER_NOT_ACTIVE`.
`UC-002-create-account` is functionally dead end-to-end.

**The executor knew.** It wrote, in `CustomerFixtures`:

```java
// ... needs a customer past KYC, which Customer.register() alone never
// produces — only rehydrate() can, since ACTIVE is reachable only through a later
// transition no use case in this project performs yet.
```

and then used `rehydrate()` in the ITs to manufacture the state the production path cannot
reach. The build went green, the spec was closed as `implemented`, and the regression was
recorded nowhere except that fixture comment.

**Why this is a pipeline gap and not executor carelessness.** The agent has exactly one
"stop and report" trigger for spec defects — a missing dedupe table in Block M. There is no
rule of the shape "if a change from § Impact leaves an approved use case unreachable through
its own public entry point, stop and report". Detecting it required exactly the reasoning
the executor already did in that comment; what was missing was an instruction to escalate
rather than route around.

Note the test-level tell: a fixture invented specifically to construct a state no production
path can construct is the signature of this class of defect. That is a cheap, mechanical
check.

**Lesson / suggested fix.** Add the escalation rule to the executor, and a matching check to
`/new-feature`'s consolidation: when an impact row adds a precondition to an approved case,
the spec must name which use case satisfies that precondition — and if the answer is a
backlogged case, say so in the spec and in the final report.

## § 6 · Dead-lettering is silent — `observability.md` is not cited by Block M

**What happened.** The event's durability requirement is "must not be lost". The shipped
failure path is: five failed sends → `dead_lettered = true` → row stops being read → one
`log.warn` per attempt, and nothing else. No counter, no gauge of pending age, no alert.
Loss is now silent and permanent, which is precisely the property Form B was chosen to
avoid.

**Why it slipped.** The executor's rule list cites `.claude/rules/observability.md` for
**Block 3 (REST)** only. Block M cites `messaging.md`, `architecture-ddd.md`, `naming.md`,
`code-quality.md`, `lombok.md`, `error-handling.md` and `logging.md` — not
`observability.md`. `logging.md` got applied faithfully (outcome and latency logged, payload
never logged), which produced good logs and zero metrics.

**Lesson / suggested fix.** Block M cites `observability.md`, and an event whose partial
declares "must not be lost" requires a dead-letter metric as a validation item, not as a
nice-to-have.

## § 7 · PII crosses two boundaries unmasked — the masking rule stops at logs

**What happened.** `KycVerificationRequestedPayload` carries `securityNumber` (a CPF) in
clear text. It is serialized into `outbox_events.payload` (`jsonb`, at rest, retained 7 days
after publication) and published to `banking.customer.kyc-verification-requested` in clear
text.

`lessons-learned-001` § 3 caught the same data leaking into **logs** and the pipeline fixed
it, because `logging.md` § Masking candidates exists and `rest-api-architect` re-derives
masking over touched DTOs. There is no equivalent rule for PII at rest or in transit:
`.claude/rules/security.md` is listed in `00-index.md` under "Planned rules (file does not
exist yet)". So no skill in the chain had a reason to ask the question, and none did.

**Lesson / suggested fix.** Raise `security.md`'s priority — its scope line already says
"secrets, sensitive data, PII". Until it exists, `messaging-architect`'s serialization step
should carry one explicit question: does this payload carry PII, and if so, what is the
minimum the consumer actually needs? (Here, plausibly: the KYC service needs the CPF, so the
answer may well be "yes, unavoidably" — but that should be a recorded decision, not an
unasked question.)

## § 8 · Code-level findings the run shipped

Ranked; none breaks the build, all are real.

1. **`OutboxRelay` is the project's largest coverage hole** — 25 lines missed / 8 covered, 5
   branches missed — and it is the class carrying the durability guarantee. `40-testes.md`
   § 4 deliberately defers only the **broker** test (M4); `resolveTopic`, the unmapped-type
   dead-letter path, and the failure branch are all unit-testable with a mocked
   `KafkaTemplate` and no broker. See also § 3: the 8 covered lines are accidental.
2. **`markPublished`/`markFailed` reload before writing** (`OutboxEventStore.java:62,71`):
   `findById().orElseThrow()` + `save()` per event, in two repository-level transactions with
   the entity detached in between. A batch of 100 costs ~201 round-trips where ~101 would do.
   A `@Modifying @Query` update collapses each to one statement.
3. **`NewOutboxEvent` and `PendingOutboxEvent` are identical records** — same five
   components, same order, same types. Either merge them or have a rule state that write-side
   and read-side carriers stay separate by direction. Same shape as `lessons-learned-001` § 5:
   a judgment call no rule settles, so two runs could legitimately disagree.
4. **`maxAttempts = 0` is a sentinel** for "dead-letter immediately" in `rejectUnmappedType`.
   It works by arithmetic (`attempts 0+1 >= 0`) and is commented, but a
   `markDeadLettered(eventId, reason)` on the gateway says what it means.
5. **`application.yml` declares producer serializers that nothing reads.**
   `spring.kafka.producer.key-serializer`/`value-serializer` are inert —
   `KafkaProducerConfig` hardcodes `StringSerializer` into its own `Map`. Worse, the project
   occupies the framework-reserved `spring.kafka.*` namespace with hand-rolled `@Value`
   reads: the day a Kafka starter is added, binding silently changes owner. Either move the
   hand-wired properties to an owned namespace (`app.kafka.*`) or adopt the starter.
6. **`app.outbox.prune-after: P7D` has no reader.** Documented as intentional (the pruning
   job is out of scope), but a property with no reader drifts from the retention the index
   and the comment assume.
7. **`Customer` has no state transition.** `register()`/`rehydrate()` only, so `rehydrate()`
   became the back door tests use to fabricate `ACTIVE` (§ 5). `activate()`/`reject()` is what
   `UC-004` will need anyway, and its absence is what made the § 5 regression invisible.

## § 9 · Two rules contradict over where Spring wiring lives

**What happened.** `CLAUDE.md` § Module structure assigns Spring wiring to
`infrastructure.config` ("Spring wiring and properties"). `.claude/rules/messaging.md`
§ How to verify enforces the opposite for Kafka types, with a grep that requires every
Kafka-typed import to sit under `/messaging/`. `KafkaProducerConfig` is both: a
`@Configuration` class and a Kafka-typed one.

The executor drafted it in `infrastructure/config`, failed the messaging grep, and moved it
to `infrastructure/messaging` — the right call, made by build feedback rather than by an
arbitrated rule, and then documented in the class Javadoc rather than anywhere a future run
would read first.

**Side effect nobody chose.** `pom.xml`'s JaCoCo block excludes `**/config/**` from coverage
on the grounds that configuration classes are "structure with no branches". Because this
`@Configuration` lives in `messaging`, it counts toward the coverage ratio while every other
one does not. Harmless at today's numbers; arbitrary either way.

**Lesson / suggested fix.** Whichever rule wins, one of the two files must name the
exception explicitly — `messaging.md` saying "Kafka `@Configuration` included", or the
module table saying "except adapter-local framework wiring". And the JaCoCo exclusion should
follow the decision (by annotation, or by both paths) instead of by package name alone.

## § 10 · `pom.xml` has no owner in the feature pipeline

**What happened.** Form B needs `KafkaTemplate`, which needs the `spring-kafka` dependency,
which `pom.xml` did not declare. The executor stopped and reported rather than writing it —
correctly: `agent_classes.executor`'s territory is `src/**` plus the spec's `status:` line,
and `java-spring-boot-developer.md` § Does not write names `Pom.xml` explicitly.

But no other participant owns it either. The five design skills are `docs/`-only;
`/new-feature` itself writes only the spec; `skill_classes.build` is blocked during a design
phase. The only `pom.xml` writers in the whole system are `archunit-installer` and
`commons-logging-installer` (`agent_classes.installer`), neither of which has anything to do
with a feature's dependencies. The run unblocked only because the orchestrating session
edited `pom.xml` directly, outside any skill's territory.

This is structurally identical to `lessons-learned-001` § 1 (no owner for a project-wide
`CLAUDE.md` fact discovered mid-feature) — same hole, different file.

**Lesson / suggested fix.** Either let `25-mensageria.md`/`20-persistencia.md` record a
**declared dependency requirement** that the executor is permitted to apply (widening
`agent_classes.executor.write_allow` to `pom.xml` for dependencies the spec names), or
create the narrow `ops`-class write path `lessons-learned-001` § 1 already proposed and let
both cases use it. A design run that discovers a missing dependency should not be able to
end in a state only a human edit can leave.

## § 11 · The Kafka compose template advertises only its internal hostname — every host-side publish fails

**What happened.** With the stack up and the application started on the host
(`./mvnw spring-boot:run`), `OutboxRelay`'s first send never reaches the broker:

```
WARN [Producer clientId=demo-clean-arch-single-module-producer-1] The metadata response from the cluster reported a recoverable issue with correlation id 2 : {banking.customer.kyc-verification-requested=UNKNOWN_TOPIC_OR_PARTITION}
WARN [Producer clientId=demo-clean-arch-single-module-producer-1] Error connecting to node kafka:9092 (id: 1 rack: null isFenced: false)
java.net.UnknownHostException: kafka: nodename nor servname provided, or not known
```

The producer bootstraps against `localhost:9092` — which works, the port is published —
and the broker answers with its advertised address, `kafka:9092`. From that point the
client talks only to the advertised address. The host has no DNS entry for `kafka`, so
every send fails, forever, with no path to recovery. The `UNKNOWN_TOPIC_OR_PARTITION`
line is a consequence, not a second problem: no reachable node, no topic.

**Where the defect actually lives.** Not in the generated project — in the exemplar.
`.claude/skills/docker-architect/templates/kafka-service.yml.example` declares:

```yaml
  ports:
    - "9092:9092"
  environment:
    KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
```

The `ports:` mapping and the `KAFKA_ADVERTISED_LISTENERS` value contradict each other.
Publishing 9092 to the host is a promise of host-side access; advertising only
`kafka:9092` makes that access impossible. One listener cannot serve both networks,
because the advertised address has to resolve differently in each. `docker-architect`
merged the block verbatim, as its contract says to, and the generated
`docker-compose.yml` inherited the contradiction unchanged.

**Why no check caught it.** The broker is healthy — `kafka-broker-api-versions.sh
--bootstrap-server localhost:9092` runs *inside* the container, where `localhost` is the
broker, so the healthcheck passes with the listener misconfigured. The integration tests
do not catch it either: Testcontainers' `KafkaContainer` configures its own advertised
listeners for host access, so the ITs exercise a correctly-wired broker that the compose
file does not produce. And `OutboxRelay` never runs against a broker in the test suite at
all (§ 3). Every mechanism that could have seen this looks somewhere else.

**The fix, for the template.** Two listeners, one per network:

```yaml
  ports:
    - "9092:9092"
    - "29092:29092"
  environment:
    KAFKA_LISTENERS: PLAINTEXT://:9092,EXTERNAL://:29092,CONTROLLER://:9093
    KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,EXTERNAL://localhost:29092
    KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT
    KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
```

`PLAINTEXT` stays the compose-network listener — the `app` service's
`KAFKA_BOOTSTRAP_SERVERS: kafka:9092` keeps working untouched. `EXTERNAL` serves the
host, at `localhost:29092`.

That change has a second half the template cannot make alone: `application.yml`'s
`spring.kafka.bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}` still
defaults to the port that only containers can use. Whoever owns the default — the
blueprint's `application.yml`, or `messaging-architect` when it first wires the producer
— has to move it to the external port, or the host-side developer runs into the same
wall by default and has to know to export the variable.

**Lesson / suggested fix.** A compose exemplar that publishes a port to the host is
claiming the service is reachable from the host; the exemplar itself should be the thing
that guarantees it. Two concrete asks for the meta-repo:

- Fix `kafka-service.yml.example` as above, and align the `application.yml` default
  alongside it — a one-line template change and a one-line property change that together
  remove the whole failure class.
- Give `docker-architect` a verification step with teeth for any service it publishes to
  the host: a healthcheck that passes from *inside* the container proves nothing about
  host reachability. `apache/kafka`'s own `kafka-broker-api-versions.sh` run from the
  host (or any client that follows the metadata redirect) would have failed immediately,
  at merge time, instead of at the first real publish.

Smaller, same file: the generated `docker-compose.yml` comment above the service cites
`docs/use-cases/UC-003-request-customer-kyc-validation/25-mensageria.md`, a folder that
does not exist — the case is `UC-003-initiate-kyc-verification`. The slug was written
from the case's working name before `use-case-design` fixed it, and nothing re-checks a
docs path quoted inside a generated file.

## Summary — who should close each gap

| § | Gap | Right owner |
|---|---|---|
| 1 | `ArchHook.java` frozen-folder guard blocks the one write (`CHANGELOG.md`) the `/new-feature` design requires for an implemented use case | `.claude/hooks/ArchHook.java` — third exemption alongside `isStatusClose`/`isChecklistToggle` |
| 2 | Block M documents Form A only; the executor inferred all of Form B, with a validation list that was `n/a` throughout | `java-spring-boot-developer.md` § Block M — Form B half + its own validation list |
| 3 | The relay runs inside `@SpringBootTest` ITs against no broker; relay coverage is accidental and one IT passes on timing | Block M validation + `.claude/rules/testing.md` — test profile must neutralize the relay |
| 4 | `claimPending` does not claim; multi-instance duplication undesigned, dedupe delegated to an external team with no contract | `persistence-architect` (claim strategy) + `messaging-architect` (who guarantees dedupe) |
| 5 | An impact row left `UC-002` unreachable end-to-end; the executor noticed in a fixture comment and shipped anyway | `java-spring-boot-developer.md` (escalation rule) + `/new-feature` consolidation (precondition must name its satisfier) |
| 6 | Dead-lettering emits no metric; `observability.md` is cited by Block 3 only | `java-spring-boot-developer.md` § Block M — cite `observability.md`, require the metric |
| 7 | PII (CPF) stored and published in clear; the masking rule stops at logs | `.claude/rules/security.md` (still unwritten) + `messaging-architect` serialization step |
| 8 | Shipped code: relay untested, reload-before-write, duplicated carriers, sentinel parameter, inert serializer config, reader-less property, no domain transition | Mostly `java-spring-boot-developer.md` Block M/Block 4; item 3 needs a rule to settle it |
| 9 | `CLAUDE.md` module table and `messaging.md`'s grep disagree over adapter-local `@Configuration`; JaCoCo exclusion follows package name | `.claude/rules/messaging.md` or `CLAUDE.md` § Module structure — name the exception once |
| 10 | No owner for `pom.xml` in the feature pipeline; a missing dependency can only be unblocked by a human edit | `agent_classes.executor.write_allow`, or the narrow `ops` write path from `lessons-learned-001` § 1 |
| 11 | The Kafka compose exemplar publishes 9092 to the host but advertises only `kafka:9092`; every host-side publish dies on `UnknownHostException: kafka`, and no healthcheck or IT sees it | `docker-architect/templates/kafka-service.yml.example` (two listeners) + the `application.yml` `bootstrap-servers` default + a host-side reachability check in `docker-architect` |

Also worth carrying to the meta-repo: `messaging-architect/templates/OutboxRelayPublisher.java.example`
instructs `catch (RuntimeException e)`, which this project's own Checkstyle `IllegalCatch`
rejects. The executor discovered it by breaking the build and substituted
`CompletionException` — a good call, at the cost of a failed compile. The template should
carry the narrower catch already.

As in `lessons-learned-001`: none of these is a framework-boundary violation in the shipped
code. `ArchHook.java guard` fired correctly every time (§ 1, § 10). They are process gaps —
places where ownership has a hole, and the run happened to fall into it correctly (§ 2, § 4,
§ 9) or not (§ 3, § 5, § 6). § 11 is the one exception to that framing: a template defect,
copied verbatim into the project exactly as the contract requires, that breaks the feature at
runtime — ownership was clear the whole time, and the exemplar was simply wrong.
