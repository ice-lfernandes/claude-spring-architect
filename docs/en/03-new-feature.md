# `/new-feature` — feature design pipeline

Primary source: `.claude/skills/new-feature/SKILL.md`,
`.claude/agents/java-spring-boot-developer.md`, the design skills (`use-case-design`,
`domain-modeling`, `rest-api-architect`, `messaging-architect`, `jobs-architect`,
`persistence-architect`, `test-architect`), and the `guard` mode of
`.claude/hooks/ArchHook.java`.

## What it does

Designs **one use case per run** and consolidates it into a single `UC-NNN-spec.md` —
ready for the executor agent to implement once the user approves it. **This command
only makes sense inside an already-generated project**, created by `/init-project`: it
reads `pom.xml`, `.claude/forbidden-imports.txt`, and discovers the domain package in the
real project.

These boundaries hold on every run, and none of them is prose — the `guard` hook blocks
with `exit 2`, `permissions.ask` prompts, or consolidation stops:

- **A run writes only under `docs/`.** Territory is an allowlist, deny by default: each
  pipeline skill writes only its class's `write_allow` (`skill_classes` in
  `.claude/schemas/extensions.json`). Migration SQL lives inside `20-persistencia.md`;
  every file under `src/` comes from the executor.
- **A design run materializes no file outside `docs/`.** `guard` refuses the *call* itself
  to a `build`-class skill — `docker-architect` included — while the run is open. A missing
  compose service is **recorded** in the partial, with the command that creates it.
- **Git goes only through `git-publish`**, behind its two confirmations — on every end
  of the flow, including the one without the executor. `git push` is always `ask`.
- **An approved spec is immutable.** A later case records the change it needs in its
  own `## Impact on approved use cases` section, plus one line in the `CHANGELOG.md` of
  every altered case's folder. `guard` freezes the files — and exempts exactly that
  `CHANGELOG.md`, so the log a frozen folder is supposed to receive is the one write it
  still accepts (`guard.frozen_exempt_basenames`), besides the spec's own `status:` close
  and checklist toggles.
- **An impact row that adds a precondition names its satisfier.** A `Satisfied by` column:
  an approved `UC-NNN`, this case, or a **named** backlog case — and in that last situation the
  spec and the final report both state that the earlier case is unreachable end to end until
  that one ships. With no satisfier, consolidation stops. A fixture that fabricates the state
  is not a satisfier.
- **The final report carries four findings nobody should have to reconstruct:** an approved
  case this run left unreachable, every item a partial deferred with the owner it left it to,
  a dedupe guarantee delegated to an external consumer with no contract, and personal data
  crossing a boundary in clear, with its receiver.
- **Design patterns are decided in the design, not by the executor.** Every design partial
  carries a `## Design patterns` section — `none` when nothing applies, and absence stops
  consolidation — naming the spec line or `file:line` that forces each pattern and the
  classes it creates.

## Why it's a manual skill, not an agent

`new-feature/SKILL.md` documents the decision itself: axis 2 (manual trigger) + axis 5
(procedural) + axis 8 (both). The "agent" form was rejected — none of the three valid
reasons applies (context, tools, model). `disable-model-invocation: true` means the model
can't call it: the user types the command.

## Input — a closed table

The argument is classified by command (`grep -E`, `test -d`, `grep '^status:'`), never
interpreted. First matching row wins; anything else is an error.

| Input | Result |
|---|---|
| empty | lists the use cases with their `status` |
| `UC-NNN-slug`, folder exists, spec `draft` or missing | resumes: only the missing partials, then consolidation |
| `UC-NNN-slug`, spec `approved` | implements it: straight to the executor delegation, skipping the design steps and consolidation |
| `UC-NNN-slug`, spec `implemented` or `implemented-blocked` | ❌ already implemented — describe the change as a new feature |
| no folder, but the argument's `UC-NNN` has exactly one folder on disk | ❌ names the real slug and status, plus the exact command — the case exists, the argument named it wrongly |
| `UC-NNN-slug`, no folder, number matching zero or several | ❌ not found — describe the feature to create one |
| contains `UC-` + digit but isn't exact | ❌ ambiguous argument |
| free text, some case still open | ❌ resume or approve it first |
| free text, no open case | new use case — the text goes as is to `use-case-design` |

An error prints the reason and the usage, and ends the run: no side effect after it — no
skill call, no write, no question. Reading is allowed, which is what lets a near miss answer
with the real slug instead of "not found".

## Sequence diagram

```mermaid
sequenceDiagram
    actor U as User
    participant NF as skill: new-feature
    participant UC as skill: use-case-design
    participant DOM as skill: domain-modeling
    participant REST as skill: rest-api-architect
    participant MSG as skill: messaging-architect
    participant JOBS as skill: jobs-architect
    participant PER as skill: persistence-architect
    participant TST as skill: test-architect
    participant LOG as agent: commons-logging-installer
    participant DEV as agent: java-spring-boot-developer
    participant GIT as skill: git-publish

    U->>NF: /new-feature REST endpoint that cancels a confirmed order in the orders table
    NF->>NF: input table → new case · worktree decided · project checks
    NF->>UC: description, as is
    UC-->>U: several use cases? one question: which one now
    UC-->>NF: 00-caso-de-uso.md (others → BACKLOG.md)
    alt short path (4 criteria met)
        NF->>NF: UC-NNN-spec.md from feature-spec-short
    else full path
        NF->>DOM: 10-dominio.md
        NF->>REST: 30-rest.md (+ schema requirements)
        opt external event delivery
            NF->>MSG: 25-mensageria.md
        end
        opt scheduled trigger, outbox relay (Form B), or a deferred job
            NF->>JOBS: 35-jobs.md
        end
        NF->>PER: 20-persistencia.md (reads schema requirements, SQL inside)
        NF->>TST: 40-testes.md
        NF->>NF: resolve divergences, consolidate by reference (status: draft)
    end
    NF-->>U: Approve spec?
    alt keep as draft
        NF-->>U: resume command, no git
    else approve (status: approved)
        NF-->>U: Implement now?
        alt implement now
            opt pre-flight — once per project, each gap behind a question
                NF->>TST: Skill, no argument (setup mode → archunit-installer)
                NF->>LOG: Agent commons-logging-installer, in a separate turn
            end
            NF->>DEV: spec path
            DEV-->>NF: green build, status: implemented (or implemented-blocked)
            NF->>GIT: feat(UC-NNN-slug) — two gates
        else not now
            NF->>GIT: docs of the approved spec only — two gates
        end
    end
    NF-->>U: final report + recommend /clear
```

## Pieces and what each writes

| Order | Skill/Agent | Depends on | File it produces |
|---|---|---|---|
| 1 | `use-case-design` | — | `00-caso-de-uso.md`, `BACKLOG.md` entries — owns number and slug |
| 2 | `domain-modeling` | 1 | `10-dominio.md` — owns exception names |
| 3 | `rest-api-architect` | 1, 2 | `30-rest.md` — including the schema requirements transport creates |
| optional | `messaging-architect` | 2, if external delivery | `25-mensageria.md` |
| optional | `jobs-architect` | 1 (schedule trigger), or messaging's Form B, or a deferred job | `35-jobs.md` — tool, trigger/cadence, cluster coordination, overlap/misfire, gate property, job metrics; owns the outbox relay's schedule and the prune job |
| 4 | `persistence-architect` | 1, 2, 3, and messaging/jobs when they ran | `20-persistencia.md` — migration SQL inside it; reads `35-jobs.md`'s tool tables (`shedlock`, `QRTZ_*`, `BATCH_*`) as schema requirements and its replica count for the outbox claim strategy, when jobs-architect ran |
| 5 | `test-architect` | 1–4 | `40-testes.md` |
| — | `new-feature` (consolidation) | all above | `UC-NNN-spec.md` |
| 6 | `java-spring-boot-developer` (agent) | `approved` spec | code and migrations in `src/**` |
| — | `git-publish` | end of flow | commit + push, behind two gates |

**Why REST before persistence.** REST generates schema requirements (`Idempotency-Key`
needs the shared key table); persistence generates none for REST. In the old order the
table was discovered after the persistence partial was written, and persistence ran
twice.

`docker-architect` is **not** chained by the pipeline. A design run is docs-only: when
`persistence-architect`, `messaging-architect` or `test-architect` finds a service missing
from `docker-compose.yml`, it records the pending service in the partial and reports the
`/docker-architect` command, which the user runs afterwards in a prompt of its own.
`ArchHook.java`'s `guard` mode refuses the call while a design phase is open —
`.claude/decisions/0058-skill-classes-territory-schema.md`.

**Design patterns are decided inside that phase too.** `domain-modeling`,
`persistence-architect`, `rest-api-architect`, `messaging-architect` and `jobs-architect`
each read `gof-design-patterns` § Design-time use — read, never invoked: it is a `build`
skill — and write a `## Design patterns` section into their own partial: the spec line or
`file:line` that forces the pattern, the pattern, the classes and interfaces it creates, the
"When not" checked. Consolidation collects every row into the spec's own `## Design
patterns`, so the classes a pattern creates are in what the user approves. The executor
implements those rows, and adopts one on its own only for a symptom already on disk; the
catalog reaches it at `SubagentStart`
(`.claude/decisions/0089-design-patterns-decided-at-design-time.md`).

## Pre-flight — infrastructure installed once, on the first "implement now"

Before delegating to the executor, `new-feature` detects (by command, never by
assumption) two gaps that only matter when the first `.java` is about to be written
under `src/`:

| Gap | How it's detected | Installed via |
|---|---|---|
| ArchUnit missing | `grep -rl "ArchRule\|ArchTest" src/test/` is empty | `Skill(test-architect)` with no argument — setup mode, which delegates to `archunit-installer` (ArchUnit + JaCoCo gate) |
| Logging/masking classes missing | no `commons` folder, or only `package-info.java` in it | `Agent(commons-logging-installer)` — thirteen exemplars from `new-feature/templates/commons/` translated into the real package, plus `AutoConfiguration.imports` and the AOP dependencies |

Each gap found becomes an `AskUserQuestion` (**Install now** / **Skip for this run**);
no gap, no question. The two may fire in the same turn — and could not before:
`Skill(test-architect)` opened a design phase in the `guard` hook and
`Agent(commons-logging-installer)` closed one, with no ordering guarantee between the two
`PreToolUse` hooks, and the loser blocked every write under `src/` by the other agent (it
happened: 138k tokens, zero files written). `agent_classes` retired it: a subagent's write is
judged by its own `agent_type`, so no phase reaches it and nothing closes a phase on an
`Agent` call any more.

## Spec lifecycle

| `status:` | Written by | When |
|---|---|---|
| `draft` | `new-feature` | at consolidation |
| `approved` | `new-feature` | only after explicit user approval |
| `implemented` | `java-spring-boot-developer` | with `./mvnw verify` green |
| `implemented-blocked` | `java-spring-boot-developer` | with a green build, when the run left an approved case unreachable end to end — the `Satisfied by` the spec named isn't there yet |

Either closed state can revert to `approved` — the exit a run that closed by mistake needs
(`.claude/decisions/0066-spec-state-machine.md`).

A spec defect found by the executor in an approved spec: the user sets `status: draft`
by hand, then `/new-feature UC-NNN-slug` resumes and re-approves.

**An approved spec is never edited to record that another case changed its behavior.** The
case that alters already-approved cases — including the one that creates no use-case class
at all, and still gets its own `UC-NNN` — writes one line in the `CHANGELOG.md` **of every
altered case's folder**: the date, the `UC-NNN` that changed it, and what changed. Without
it, immutability protects the text and loses the history: whoever opens
`UC-001-register-customer/` has no way to learn its behavior changed elsewhere. `guard`
freezes the folder; the changelog is the separate file that records the change without
breaking the freeze.

## Short path

When the case reuses an approved aggregate, needs no new table, column, or migration,
no new exception, and no question to the user, `new-feature` skips the partials and
writes one spec where every row names its source — an approved spec or a rule.

## Precedence rule during consolidation

| Fact | Who wins |
|---|---|
| HTTP path, verb, status, body shape | `30-rest.md` |
| Table, column, key, index, migration | `20-persistencia.md` |
| Topic, serialization, delivery guarantee, consumer retry/DLQ | `25-mensageria.md` |
| The outbox table, its columns, the claim query, attempt ceiling/backoff, and retention window | `20-persistencia.md` — messaging **declares** that the case needs an outbox and which guarantee the relay must honour, and never a column name. A § 6 row naming columns is a divergence, and a column decision that would drop the declared guarantee **stops** the pipeline instead of being settled by precedence |
| Scheduling tool, trigger and cadence, cluster coordination, overlap/misfire handling, job metrics, the prune job | `35-jobs.md` — including the outbox relay's own poll interval and coordination, which no longer lives in `20-persistencia.md` |
| Aggregate name, value object, port, event | `10-dominio.md` |
| Exception class and `errorCode` | `10-dominio.md` |
| Use case boundary, invariants, error situations | `00-caso-de-uso.md` |
| Name and level of each test | `40-testes.md` |
| A design pattern and the classes it creates | The partial of the layer those classes live in — two partials adopting different patterns for the same class **stops** the pipeline |

The spec is consolidated **by reference**: each block holds the final decisions and
the partial's path, never a copy of its tables. Discarded values go to
`## Resolved divergences`. `## Impact on approved use cases` and `## Design patterns` collect
every row of the same section of each partial.

## Cost discipline

A real run cost ~USD 15 for a two-field aggregate, 70% of it cache reads: the lever is
turns × context size. So: no progress messages between steps, independent writes in one
turn, versions read from `pom.xml`, one use case per run, and `/clear` between runs.

## Operational note — long-running background work

An executor running in the background **doesn't survive the machine sleeping**. Before
delegating in the background, `new-feature/SKILL.md` says to offer keeping the machine
awake (`caffeinate -i` on macOS) or running on the main thread, which is resumable.

## Entry into generated projects

`/new-feature` travels into every project generated by `/init-project`
(`project-bootstrap` step 6.6, which writes skills, agents and hooks through
`ArchHook.java export`), with the `guard` hook wired. Once `pedidos-api`
exists, `/new-feature <description>` runs **inside** it, without
`claude-spring-architect` on the machine.

Every run leaves a report under `.claude/audit-usage/` — active duration, time waiting
on the user, tokens per chained skill and for the executor, files touched, failures.
That's how the cost discipline above stops being an estimate: `/audit-usage` shows
which piece is eating the budget. See [08-audit-usage.md](08-audit-usage.md).
