---
name: domain-modeling
description: >
  Details the domain and application layer of an already-designed use case — aggregate,
  value objects, invariants, ports, and events — into the `10-dominio.md` partial. Use
  when the request involves modeling the domain, defining an aggregate or value object,
  designing a use case's ports, or detailing a spec already created by `use-case-design`.
  Piece of the `/new-feature` pipeline: requires `00-caso-de-uso.md` in the given folder
  and stops without it.
argument-hint: "[path of the UC-NNN-<slug> folder]"
allowed-tools: Read, Write, Glob, Grep, Bash, AskUserQuestion
---

## Available specs

!`find "${CLAUDE_PROJECT_DIR:-.}/docs/use-cases" -mindepth 1 -maxdepth 1 -type d -name 'UC-*' 2>/dev/null | sort`

Empty above → none yet, run `/use-case-design` first. (`find`, not an `ls` glob: under zsh an unmatched glob
aborts the command before any fallback runs.)

## Target

$ARGUMENTS

---

# Domain Modeling

Details the **interior** of a use case: what the mother spec named, this skill gives
shape to. Signatures, types, invariants, and where each lives.

**Entry rule: without a mother spec, there's nothing to detail.** This skill reads
`docs/use-cases/UC-NNN-<slug>/00-caso-de-uso.md` and treats it as a contract. Without
that file, it stops and tells you to run `/use-case-design` — modeling a domain from a
loose request reinvents the boundary the mother spec exists to fix.

**Exit rule: it doesn't write code.** It emits `10-dominio.md`. The Java classes come
from the executor agent, which reads the partial and the `templates/` exemplars.

## How it's invoked

Two paths, and both matter: `/domain-modeling` by hand, or chained by `/new-feature` once
that orchestrator exists. That's why it does **not** carry
`disable-model-invocation` — that field hides the skill from the model, and a skill the
model can't see is a skill the orchestrator can't call.

The guard against out-of-order firing isn't the frontmatter: it's the **entry rule**
above. Without the previous partial, the skill stops and says what needs to run first.
Recorded in `@.claude/decisions/0007-pipeline-skills-invocation.md`.

## Why this isn't a subagent

It's a procedure whose step 3 goes back to the user to ask what the mother spec left
open — the exact type of each value object, which invariant lives in the constructor. A
subagent doesn't see the conversation. Decision recorded in
`@.claude/decisions/0003-skill-domain-modeling.md`.

## Boundary with neighboring skills

Three pieces touch domain and application. Overlapping would be an ownership bug, so the
division is by **moment**, not by folder:

| Piece | When it acts | What it produces |
|---|---|---|
| `use-case-design` | Before the domain exists | Canonical names and boundary — `00-caso-de-uso.md` |
| **this skill** | After the mother spec, before code exists | Signatures and invariants — `10-dominio.md` |
| `java-patterns` | After code exists, and only with a symptom | Refactor of real code (`file:line` that hurts) |

If the code already exists and the problem is a growing chain of `if`s, it isn't this
skill — it's `java-patterns`. If there's no mother spec, it also isn't this skill.

## Procedure

1. **Read the mother spec.** Without `00-caso-de-uso.md` in the given folder, stop and
   report. Extract: canonical names, trigger, effects, listed invariants, error table,
   and the component-table rows marked `Detailed by: domain-modeling`.
2. **Read the existing code.** `Glob`/`Grep` the project for the canonical names. The
   aggregate might already exist and the use case be just a new method. Confirm or
   correct the mother spec's NEW/CHANGE/REUSE state — and if you correct it, say so in
   the report: the mother spec is now stale and someone needs to review it.
3. **Ask only what's missing.** `AskUserQuestion` for what the mother spec didn't fix:

   | Typical gap | Why it matters |
   |---|---|
   | Type of each field — `String` or value object | `email: String` spreads validation everywhere; `Email` concentrates it in one place. The criterion isn't opinion: `@.claude/rules/value-objects.md` |
   | Invariant in the constructor or in a method | Constructor = always true. Method = only for that transition |
   | Does the aggregate emit an event? | Decides whether there's a sibling UC to consume it, and whether `messaging-architect` comes in |
   | …and can that event be lost without anyone noticing? | The `Durability` column of § 4 · Events. A property of the event — whether its absence is later detectable and recoverable — not of its transport. `messaging-architect` turns the answer into a publication form; deciding the form here is opining outside this skill's territory |
   | Aggregate boundary — what's inside and what's a reference by id | An aggregate that's too big is an unnecessary lock |
   | Command with a single field | Sometimes the record is ceremony; sometimes it's cheap extensibility |

   Don't ask what the mother spec already answered. Repeating an already-answered
   question is the sign you didn't read step 1.
4. **Fix the signatures.** Names from `@.claude/rules/naming.md` § Architecture vocabulary
   — they already come from the mother spec, don't change them. Exception names are
   this skill's: the mother spec only describes the situation and its kind. Zero framework types in signatures
   (`@.claude/rules/architecture-ddd.md`). Zero `null` crossing a boundary; `Optional`
   only on query return (`@.claude/rules/code-quality.md`). Every primitive field goes
   through the `@.claude/rules/value-objects.md` criterion before staying a primitive —
   a field with a formation rule that stays `String` is a decision to justify in the
   partial, not a default.

   **Flag which fields are sensitive.** The same pass that decides a field's value
   object (document, phone, email — `@.claude/rules/value-objects.md`'s catalog) is the
   natural signal for `@.claude/rules/logging.md`'s masking rule: a field with a
   formation rule that identifies a person (CPF/CNPJ, phone, email, a document number)
   is a candidate for `@MaskSensitiveData`. List these fields in the aggregate block —
   `rest-api-architect` reads this to decide which DTO fields implement `LogMask`; it
   doesn't re-derive sensitivity from field names on its own.
5. **Map each invariant to its exception.** Typed family from
   `@.claude/rules/error-handling.md` and `errorCode` in `UPPER_SNAKE_CASE`. An invariant
   without a named exception is an invariant nobody will implement.

   **The exception family belongs to this skill, and this is where it enters the
   project.** `project-bootstrap` doesn't write it — it has no invariant to tie it to
   (`@.claude/decisions/0011-bootstrap-without-business-code.md`). So: search for
   `DomainException` in the project with `Grep`. If it doesn't exist, the partial's
   invariants block opens with the line **"Exception family: NEW — five classes in the
   `domain.exception` package"**, and names the five. If it already exists, write
   **REUSE** and name only the missing ones. Once per project, not once per use case —
   the second spec finds them and reuses them.

   The form exemplars are `templates/DomainException.java.example` and the four typed
   ones. Don't invent categories beyond the four: the taxonomy belongs to the rule, not
   this skill.

   Default every invariant to the plain typed family + a string `errorCode`. Promote to
   a named subclass (`OrderAlreadyPaidException extends ConflictException`, no new
   state) only when the entry bar in `@.claude/rules/error-handling.md` § Shape of the
   base classes is met: the `errorCode` already repeats across ≥ 2 call sites, or a
   caller needs to `catch` it specifically rather than branch on `errorCode()`. A first
   occurrence never gets its own class — the same discipline `java-patterns` applies to
   when a symptom earns a design pattern.

   **A second call site found in a later use case** promotes the exception here, in this
   case's `10-dominio.md`, under `## Impact on approved use cases` — never by editing the
   earlier case's partial or spec, which are approved and immutable.
6. **Generate** from `templates/domain-spec.md.example` to
   `docs/use-cases/UC-NNN-<slug>/10-dominio.md`.
7. **Report and stop.** File path, divergences found against the mother spec, and what's
   missing for the folder to be complete (`20-persistencia.md`, `30-rest.md`,
   `40-testes.md`). Don't invoke anyone.

## What the partial contains

Four blocks, all mandatory. An empty block is written as "none" — deleting it hides a
question nobody asked.

| Block | Details | Form exemplar |
|---|---|---|
| Aggregate and value objects | Root, fields, types, which VOs exist and why, which fields are sensitive (masking candidates) | `Aggregate.java.example` · `ValueObject.java.example` · `ValueObjectCatalog.java.example` |
| Invariants | Each rule, where it's enforced, which exception it raises; and the state of the exception family (NEW or REUSE) | `DomainGuards.java.example` · `DomainException.java.example` and the four typed ones · `@.claude/rules/error-handling.md` |
| Ports | Input (`<Verb><Noun>UseCase`), command, output — complete signatures | `UseCasePort.java.example` · `Command.java.example` |
| Events | Which event, which payload, which UC consumes it | `DomainEvent.java.example` |

The exemplars in `templates/` are **reference for form**, not files to copy: they show a
record with a compact constructor, a static `of` factory, and total absence of
framework. It's the executor that reads them when generating code.

Twelve exemplars, and the five of the exception family (`DomainException`, `NotFound`,
`Validation`, `BusinessRuleViolation`, `Conflict`) arrived here when `project-bootstrap`
stopped emitting business code. This skill owns the shape of the domain, and the
exception is domain: transport-agnostic, no framework, with a stable `errorCode`.
Record: `@.claude/decisions/0011-bootstrap-without-business-code.md`.

## Contract

**Reads** `docs/use-cases/UC-NNN-<slug>/00-caso-de-uso.md` (mandatory — stops without
it), `@.claude/rules/architecture-ddd.md` (Domain and Application sections),
`@.claude/rules/value-objects.md` (criterion for which field becomes a value object),
`@.claude/rules/naming.md`, `@.claude/rules/error-handling.md`,
`@.claude/rules/code-quality.md`, `@.claude/rules/logging.md` (which fields to flag as
masking candidates), and the active blueprint's `packages.map`.

**Writes** `docs/use-cases/UC-NNN-<slug>/10-dominio.md`. Only that file.

**Does not write Java code.** The classes in `domain/**` and `application/**` come from
the executor agent — the exception family included: this skill owns the **shape** (the
`templates/` exemplars) and the **state** (NEW or REUSE, in the partial), not the
writing. Does not touch `00-caso-de-uso.md` (`use-case-design`), the other partials, or
`.claude/rules/**`.

**Does not collide with `java-patterns`**, which writes domain code from a symptom in
existing code. This skill produces a spec before code exists; that one refactors code
that already hurts. Different moments, different artifacts.

**Does not** decide persistence, mapping, or transport technology —
`20-persistencia.md` and `30-rest.md` have their own owners.
