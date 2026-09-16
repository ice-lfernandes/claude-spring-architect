---
name: use-case-design
description: >
  Delimits a use case's boundary and emits the parent spec that the layer skills
  detail — trigger, payload, response, side effects, invariants, errors, and canonical
  names. Use when the request involves "new use case", "design before implementing",
  "implementation spec", a new endpoint/event/job, or when a request bundles several
  actions and it's necessary to know whether it's one use case or several. First piece
  of the `/new-feature` pipeline.
argument-hint: "[technical description of the use case]"
allowed-tools: Read, Write, Glob, Grep, Bash, AskUserQuestion
---

## Already-designed use cases

!`ls -1d docs/use-cases/UC-* 2>/dev/null | tail -5 || echo "(none — this will be UC-001)"`

## Request

$ARGUMENTS

---

# Use Case Design

Produces the use case's **parent spec**: the boundary, the flow, and the canonical
names every layer inherits. Writes no code. Chooses no technology.

**Entry rule: one use case, one side effect.** Before any question, apply the
boundary test from `references/scope-boundary.md`. A request with two side effects and
no shared transaction isn't one use case — it's two, and the right answer is to
propose the split, not design the hybrid.

**Vocabulary rule: technical input or nothing.** The request names the concrete
trigger (endpoint, topic, job, schedule) and the concrete destination (table, queue,
service). A request in business language — "when a user is created in the marketing
area" — doesn't get guessed at: stop and ask for a rewrite, listing the expected
vocabulary.

## How it's invoked

Two ways, and both matter: `/use-case-design` by hand, or chained by `/new-feature`
once that orchestrator exists. That's why it does **not** carry
`disable-model-invocation` — that field hides the skill from the model, and a skill the
model can't see is a skill the orchestrator can't call.

It's the first piece in the pipeline: it has no prior partial to require. The guard
against improper firing is the boundary test above — a vague request stops and asks
for a rewrite. Recorded in `@.claude/decisions/0007-pipeline-skills-invocation.md`.

## Why this isn't a subagent

It's a multi-step procedure whose heart is the interview with the user, and a
subagent doesn't see the conversation (axis 6 of the design interview). It writes
files in `docs/use-cases/**` — this repository's default for anything with a side
effect. The subagent lost by failing the counter-test's three questions; the full
record, with all four options and the notes, is in
`@.claude/decisions/0002-skill-use-case-design.md`.

## Out of scope — and it's someone else's scope, not a lesser scope

| Doesn't decide | Why | Who decides |
|---|---|---|
| REST or GraphQL, SQL or NoSQL, Kafka or SQS | Technology is an architecture choice, fixed earlier: the blueprint's `packages.map` and `features` | The user, in `/init-project` |
| Which design pattern to apply | A pattern is born from a symptom in the code, not from a spec | `java-patterns` |
| Writing classes, tests, or migrations | The spec describes what to create; creating is a different phase | The layer skills and, eventually, the executor agent |
| Per-layer detail (annotations, columns, exact HTTP status) | Each layer has its own rule and owner | The partial specs — see § Structure |
| **Path, verb, and HTTP status** | Fixing them here creates divergence: `api-rest.md` has URI and status rules this skill doesn't apply, and `30-rest.md` ends up correcting the parent spec's prose. Write the situation (`created`, `conflict with existing state`), not the number | `rest-api-architect`, in `30-rest.md` |

If the user asks for one of these, say which piece owns it and stop. Don't improvise
the decision.

## Procedure

1. **Validate the vocabulary.** Without a technical trigger and a technical
   destination in the request, stop and ask for a rewrite, with the examples from
   `references/scope-boundary.md`. Don't proceed by guessing.
2. **Count the side effects** using the boundary test. Two or more with no shared
   transaction — or a request naming several operations (create, read, update, delete)
   — is several use cases. **A split becomes backlog, not work:**

   1. Present the list in dependency order (which UC stays synchronous, which reacts to
      which event, which needs the aggregate another one creates) in **one**
      `AskUserQuestion`, asking which one to design now.
   2. Design only the chosen one.
   3. Append the others to `docs/use-cases/BACKLOG.md`, from
      `templates/backlog.md.example`: one line each, with the description ready to pass
      to the next `/new-feature`. **Don't reserve a number** — a number is given when the
      case is designed, so skipping or dropping an entry leaves no gap.

   Nothing is saved before the answer.
3. **Interview** with `AskUserQuestion`, four blocks, one per call when earlier
   answers change the next questions:

   | Block | Fixes |
   |---|---|
   | Trigger, payload, response | Who initiates, what data comes in, what goes out and in what shape |
   | Side effects | Writes, external calls, publications — the boundary already counted in step 2, now confirmed |
   | Invariants and errors | Rules the domain guarantees, and the `@.claude/rules/error-handling.md` exception born from each violation |
   | Idempotency, transaction, concurrency | Safe to re-run? Where does the transaction open and close? What key could collide? |

   Don't move on with a block unanswered. A missing answer becomes a silent assumption
   in the spec.
4. **Read the code before proposing.** `Glob`/`Grep` to know what already exists:
   aggregate, ports, repository, controller. Every spec component carries a **NEW**,
   **CHANGE**, or **REUSE** state. Proposing to create what already exists is this
   skill's most expensive failure mode.

   Read the approved specs too — every `UC-NNN-spec.md` whose `status:` is `approved`
   or `implemented`. They are a **read-only contract**: reuse the aggregate they already
   modeled, and never edit their files. A change this case needs in an approved case
   goes into this spec's `## Impact on approved use cases` section — which case, what
   changes, why. Decide what this case needs; don't defer or anticipate a decision for a
   future case.
5. **Derive the real paths** from the active blueprint's `packages.map` — in the
   generated project, from the packages documented in the root `CLAUDE.md` and the
   module `CLAUDE.md` files. Never write a generic path when the real one is knowable.
6. **Fix the canonical names** per `@.claude/rules/naming.md`: inbound port
   `<Verb><Noun>UseCase`, implementation `<Verb><Noun>Service`, aggregate as a noun,
   exceptions from the `@.claude/rules/error-handling.md` family. These names are the
   inherited contract — the partial specs detail them, never reinvent them.
7. **Fix the number and the slug.** This skill is their only owner — no caller passes
   them in. `NNN` is the highest existing `UC-NNN` plus one, read from the injection at
   the top (`UC-001` when there's none); the slug is kebab-case, from the trigger's verb
   and noun.
8. **Generate** from `templates/use-case-spec.md.example` into
   `docs/use-cases/UC-NNN-<slug>/00-caso-de-uso.md`. Create the folder; don't create the
   empty partials.
9. **Report and stop.** File path, the boundary applied, the backlog entries the split
   produced (if any), and the table of who details each partial. **Don't invoke any
   skill** — see § Handoff.

## Spec structure

One folder per use case. One file per owner — nobody edits another's file.

```
docs/use-cases/UC-001-create-user/
├── 00-caso-de-uso.md    ← this skill. Boundary, flow, canonical names
├── 10-dominio.md        ← domain-modeling
├── 20-persistencia.md   ← persistence-architect
├── 30-rest.md           ← rest-api-architect
├── 40-testes.md         ← test-architect
└── UC-001-spec.md        ← /new-feature: consolidated spec, carries `status:`
```

`docs/use-cases/BACKLOG.md` sits beside the folders: the use cases a split left for later.

While the partials don't exist yet, `00-caso-de-uso.md` stands on its own: the
component table already names every file to create and who details it. It's an
incomplete spec, not an invalid one — and the header says so.

## Handoff — declarative, never executable

This skill ends at the file. It doesn't call `rest-api-architect`, `java-patterns`, or
`test-architect`, for two reasons: each one exclusively owns its own paths, and
merging design with execution makes "writes no code" impossible to verify.

The link lives in three passive places: the `Detailed by` column of the component
table, this section, and the final report — which prints the command the user runs
next, with the spec's path as the argument.

## Contract

**Reads** `@.claude/rules/architecture-ddd.md` (Application section — where the
transaction opens, what doesn't go in the signatures), `@.claude/rules/naming.md`,
`@.claude/rules/error-handling.md`, the active blueprint's `packages.map`, and this
skill's `references/scope-boundary.md` before counting effects.

**Writes** `docs/use-cases/UC-NNN-<slug>/00-caso-de-uso.md` and appends to
`docs/use-cases/BACKLOG.md`. Only those. The `10-` through `40-` partials belong to the
layer skills; the consolidated `UC-NNN-spec.md` belongs to the `/new-feature`
orchestrator.

**Owns** the use case number and slug. No other piece assigns them.

**Does not** edit an approved spec (`status: approved` or `implemented`) — reads it as a
contract, and records the needed change in its own impact section.

**Does not** write code, tests, migrations, or OpenAPI, and never writes under `src/`. Doesn't touch the inbound REST
adapter — the package the blueprint's `packages.map` gives that role
(`rest-api-architect`) —, the domain or application (`java-patterns`,
`domain-modeling`), nor `.claude/rules/**`. Doesn't decide technology.

**Does not** reproduce rules. Cites by path; what the rule already says isn't repeated
in the spec.

**Also carries** `examples/` — twelve complete `00-caso-de-uso.md` fixtures for testing
`/new-feature` and `java-spring-boot-developer`, indexed in `examples/README.md`. Meta-repo
documentation, not this skill's runtime output: step 6.7 of `project-bootstrap` does not
copy this directory into a generated project.
