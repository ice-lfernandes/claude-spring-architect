# 0095 · Records are exempt from ArchUnit role rules; the outbox `*Settings` become per-call `*Command`s

- **Date:** 2026-09-30
- **Scenario:** the item `.claude/decisions/0093-lessons-learned-016-fact-sources.md` left to the user
  (11.5): lessons-learned-016 § 11 — `RelayOutboxEventsUseCase.Settings` nested by the model
  because the clean-architecture convention names `Command` as the only non-`UseCase` class of
  `application.usecase`, and the outbox exemplars place a `*Settings` record there.
- **Decision:** Option 1 — the two package-based ArchUnit role rules exempt records; the outbox's
  `OutboxRelaySettings`/`OutboxPruneSettings` become `RelayOutboxEventsCommand`/`PruneOutboxEventsCommand`,
  the argument of each port call, built by the job that triggers the pass. No blueprint edited.
- **State:** approved by Lucas Fernandes, on 2026-09-30

## The problem, reproduced

The exemplars write two application-layer records that carry a use case's configuration,
injected at construction:

| Exemplar | Record | Package in the exemplar |
|---|---|---|
| `messaging-architect/templates/OutboxRelayPublisher.java.example:216` | `OutboxRelaySettings(int batchSize, int maxAttempts)` | `application.service` |
| `jobs-architect/templates/OutboxPruneJob.java.example:71` | `OutboxPruneSettings(Duration retention, int batchSize)` | `application.service` |

No blueprint's naming convention names that role. `clean-architecture-single-module.yaml:73-77`
and `-multi-module.yaml:95-99` say `Command` is "the one class in `application.usecase` that
doesn't end in `UseCase`"; hexagonal, layered, onion, modular-monolith and vertical-slice say
nothing. Each run picks a home.

**Checked with ArchUnit 1.4.1** (resolved from Maven Central, compiled against the two role rules
of `test-architect/templates/ArchitectureTest.java.example:97-111`) — four failures:

| Class | Rule it fails |
|---|---|
| `OutboxRelaySettings` (record in `application.service`) | `use_case_implementations` — suffix `Service` |
| `RelayOutboxEventsService$Settings` (the model's nesting) | the same — ArchUnit imports nested classes |
| `OutboxEventRecord` (record in `application.port.out`) | `outbound_ports_are_interfaces` |
| `OutboxEventSender$SendResult` (record nested in the port) | the same |

So the outbox exemplar fails the test exemplar **in its own vocabulary**, hexagonal, with no
translation involved, and nesting — the model's answer — fails too. The Settings question is
one symptom of a wider one: the role rules treat a value carrier as a role.

With `.and().areNotAssignableTo(Record.class)` both rules pass every record, nested or not, and
still fail a plain class out of pattern (`BadHelper` in `application.service`) — same run.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | § 11 nesting; four ArchUnit failures reproduced | create nothing |
| — ArchUnit root | Records exempt from role rules | per-name suffix exceptions (a list that grows per exemplar); moving records out of port packages |
| — Settings | **Rename to `Command`, and make it the input of each call** — the trigger builds it every pass; the `@Bean` that produced the settings disappears | name-only rename (a `Command` injected as a bean contradicts "the use case's input"); `application.shared`; nesting |
| — Blueprints | No edit: `Command` is already in the clean-architecture convention, co-located with its `UseCase` | authorizing this skill to write `.claude/blueprints/**` |
| — Projects already generated | The executor fixes an old `ArchitectureTest` role rule that fails on a record, never renames or nests the record | create nothing |
| 8 — destination | Both | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Records exempt in the two role rules + installer text; `OutboxRelaySettings` → `RelayOutboxEventsCommand`, `OutboxPruneSettings` → `PruneOutboxEventsCommand`, both arguments of the port method, built by the trigger; executor legacy sentence | 8 | **Approved** |
| 2 | Name the `Settings` role in every blueprint convention + installer suffix exception `Settings` | 5 | Rejected |
| 3 | Nest `Settings` inside the use case, made official | 2 | Rejected |
| 4 | Rename to `Command`, still a constructor-injected bean | 4 | Rejected |

**1 — Motivator:** the reproduced ArchUnit failures, and the convention that already exists.
**Pros:** a record is a value carrier, never a role — one predicate covers `Command`, the outbox
records and every future record, and the installer's `Command`-specific exception becomes a
special case of it; the clean-architecture convention ("Command, co-located with its UseCase, one
per use case") is met exactly, so no blueprint changes; the numbers travel with the call that uses
them. **Cons:** the relay's `batch-size`/`max-attempts` binding moves from the messaging adapter
to the scheduling adapter (the trigger), so `OutboxProperties` and `OutboxRelayConfig` move or go;
two exemplars, one job exemplar, the jobs partial template and the executor's Block M change.
**Cut:** maintenance (one ripple across three skills).

**2** — seven YAML edits outside this skill's territory, an exception list that grows, and the
hexagonal port failures stay. **Cut:** form fit, maintenance, precedent.

**3** — fails ArchUnit (reproduced), and the adapter imports `XUseCase.Settings`. Rejected.

**4** — a `Command` that is not the input of a call contradicts the convention it borrows the name
from. **Cut:** form fit, invariant 2 (two meanings for one name).

## References

| Claim | Source |
|---|---|
| ArchUnit imports nested classes; the record predicate works | reproduced in this session, ArchUnit 1.4.1 |
| `Command` is co-located with its `UseCase`, one per use case | `clean-architecture-*.yaml` naming comment |
| The installer adds a suffix exception only for a documented second role | `.claude/agents/archunit-installer.md` step 4 |
| A sibling adapter's class may not be imported | `.claude/rules/architecture-ddd.md` § Adapters; `OutboxPruneJob.java.example` note |
| `claude-code-architect-designer` does not write blueprints | its `## Contract` |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/test-architect/templates/ArchitectureTest.java.example` | `.and().areNotAssignableTo(Record.class)` on `outbound_ports_are_interfaces` and `use_case_implementations`, with the reason |
| `.claude/agents/archunit-installer.md` | step 4: records exempt from every package-based role rule; a non-record second role still needs the explicit exception |
| `.claude/skills/messaging-architect/templates/OutboxRelayPublisher.java.example` | `relayPending(RelayOutboxEventsCommand)`; the command in `port.in`, validating its two numbers; service constructor without settings; `OutboxProperties` and `OutboxRelayConfig` removed from the messaging adapter; WIRING note |
| `.claude/skills/jobs-architect/templates/OutboxRelayJob.java.example` | binds `OutboxProperties` (moved here, same defaults) and builds the command once; CADENCE and THE PASS'S INPUT notes |
| `.claude/skills/jobs-architect/templates/OutboxPruneJob.java.example` | `prunePublished(PruneOutboxEventsCommand)`; the trigger binds `prune-after` and `prune.batch-size` with `@Value` and builds the command; `OutboxPruneConfig` removed |
| `.claude/skills/jobs-architect/templates/jobs-spec.md.example` | the two § 1 rows name the port method with its command |
| `.claude/agents/java-spring-boot-developer.md` | Block M: command, not settings bean; binding lives in Block J. § Failure mode: an old `ArchitectureTest` failing on a record gets the exemption, never a renamed or nested record |

Goes to the generated project: **yes** — every file above travels through `export`. A project
already generated keeps its own `ArchitectureTest.java` (`src/test`, never touched by `export`);
the executor's failure-mode entry is what fixes it the first time a record trips it. No new
import in any exemplar: the CI import check covers the same FQCNs as before.
