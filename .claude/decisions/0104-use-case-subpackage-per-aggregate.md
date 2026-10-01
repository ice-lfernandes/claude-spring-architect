# 0104 · Use cases are grouped in one subpackage per aggregate, and `arch-adopt` hands over a migration prompt for every convention change

- **Date:** 2026-10-01
- **Scenario:** Issue #53, triaged at `3334519`: both clean-architecture blueprints place every
  `*UseCase` and its `*Command` flat in `application.usecase`, with no grouping convention, so
  the package grows by one or two files per use case.
- **Decision:** Option 1 — the grouping convention in the naming comment of `clean-architecture-single-module.yaml`, `clean-architecture-multi-module.yaml` and `hexagonal.yaml`; the exemplars aligned; a `migrations` block in `.claude/schemas/extensions.json` checked by `schema` and shown by `arch-adopt` step 9; `claude-code-architect-designer` may write `.claude/blueprints/**`
- **State:** approved by the user, on 2026-10-01, with `schema` validation of the `migrations` block included

## Reproduced on disk

From the `issue-verifier` verdict of `/triage-issue 53` (layer `static`, `HEAD` = `3334519` =
`v0.12.0`). Only the confirmed rows; the issue's proposed fix is not an input.

| Claim | Evidence |
|---|---|
| Both clean-architecture blueprints put `*UseCase` and `*Command` in `application.usecase` | `clean-architecture-single-module.yaml:53,73-78` · `clean-architecture-multi-module.yaml:70,95-99` |
| Nothing in `.claude/` describes grouping inside that package | Only subpackage norm is `persistence.md` § Boundary (decision 0045) |
| 0045 fixed the same symptom for the persistence adapter | `0045-persistence-subpackage-per-aggregate.md:16` |
| Nesting the command inside the use case was rejected | `0095-…:78` |
| Neither 0095 nor 0045 decides against subpackaging | 0095 cites co-location as an existing fact; none of its options touches package grouping |

Three claims of the issue were refuted, and they shape the scope: the convention's owner is the
blueprint YAML, not `naming.md` (an anchor here, filled by `export`); the `.java.example`
exemplars it listed write hexagonal-shaped packages translated through `packages.map`; the
ArchUnit "`Command` exception" is already a `Record` exemption since 0095. One surface the
triage did not list does write `application/usecase/XCommand.java` literally: the
`use-case-design` examples and `use-case-spec.md.example`.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — concrete symptom | Real: issue #53, confirmed at `3334519`; same shape 0045 fixed for persistence | Create nothing |
| 4 — territory | The use-case packages of `clean-architecture-single-module`, `clean-architecture-multi-module` **and** `hexagonal` (`application.port.in`, `application.service`), which grow the same way. `port.out` is a capability, not a use case, and stays flat | — |
| 5 — nature | Declarative fact about package layout, owned by blueprint data | Forms 1, 2, 3 for the norm itself |
| 7 — mandatoriness | Norm + exemplars, no ArchUnit rule — same call as 0045 | Forms 7, 8 for the layout |
| 8 — destination | Both: the active blueprint travels (`export.blueprint_copy`) and its convention is rendered into the project's `naming.md` | — (forces this record) |
| 9 — integration | `claude-code-architect-designer`'s contract excluded `.claude/blueprints/**` (0087, 0095 recorded edits outside it). The user widened it: the designer may write anything under `.claude/blueprints/` | — |
| 8 — already-generated projects | Migration notes plus a short prompt to paste into Claude Code inside the project; never run automatically, the user decides. Fixed behavior of `arch-adopt` from now on, for every convention change, not only this one | Any automatic rewrite of project code |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | One subpackage per aggregate + `migrations` block read by `arch-adopt` | 8 | **Approved** |
| 2 | One subpackage per use case + the same migration mechanism | 6 | Rejected — one package per class in hexagonal, no precedent outside vertical-slice |
| 3 | Sibling `application.command` package | 3 | Rejected — its stated gain already holds through 0095's `Record` exemption, and it removes the co-location 0095 option 1 builds on |
| 4 | Only fix the stale "must name `Command` as an explicit exception" wording | 3 | Rejected — the symptom is confirmed and repeats on every `/new-feature` |

### Option 1 — subpackage per aggregate (score 8)

**Motivator:** axes 1, 4 and 9. Same answer 0045 gave the persistence adapter, with the same
segment: one `<aggregate>` name across layers.

- Clean: `application.usecase.<aggregate>` holds each `*UseCase` and its `*Command`.
- Hexagonal: `application.port.in.<aggregate>` (input port + `*Command`) and
  `application.service.<aggregate>` (`*Service`).
- Placement: a use case lives with the aggregate it writes; a query with the aggregate it
  reads; the outbox relay and prune use cases in `outbox`. One that touches no aggregate goes with
  the aggregate its trigger concerns (a reaction to `OrderConfirmed`: `order`), else under the
  noun of its own name (`RequestShippingQuote`: `shippingquote`) — a gap the propagation
  found: three `use-case-design` examples and three job input ports had no aggregate, and the
  rule as approved left them nowhere. Always — including with a single
  aggregate, the same rule 0045 set against the "flat until the second arrives" variant.
- The `test/` tree mirrors the subpackages.

**Migration (shared with option 2).** A `migrations` block in `.claude/schemas/extensions.json`
— `id`, `blueprints`, `note`, `prompt` per entry — travels with the file. `arch-adopt` reads the
ids in the project's copy **before** `export` overwrites it, compares them with the fetched
list, and after verify prints every new entry whose `blueprints` names the active blueprint:
the note and the prompt, never executed. A project generated after the entry was written
already carries its id and never sees it. Install (no stamp) skips the step: there is no
earlier convention to migrate from.

**Pros:** precedent in the repo (`persistence.md:38-45`); the hexagonal layout stays readable
(a package per aggregate, not per class); one segment name for persistence, application and
tests; the migration mechanism is data, so the next convention change adds an entry and edits
no skill.

**Cons:** persuasion only, by the user's choice on axis 7; large but mechanical propagation —
three blueprints, `use-case-spec.md.example` and thirteen examples, the `domain-modeling`
templates, and the `package`/`import` lines of the jobs, messaging, rest and test exemplars.
A use case that writes two aggregates has to pick one; the placement rule above decides it.

**Points cut in the rubric:** 4 (enforcement — ArchUnit available, not used); 5 (maintenance —
many files touched plus a new data block and an `arch-adopt` step).

**CI:** `validate · design › frontmatter schema` and `exemplar-imports` already cover the
exemplars. `schema` is extended to check the `migrations` block — unique ids, required fields,
and `blueprints` that exist when the blueprint catalog is present — with a new
`.claude/.ci/MigrationsSchemaTest.java` in `hooks-cross-platform`. The `arch-adopt` step itself
is procedure: nothing testable in CI.

### Option 2 — subpackage per use case (score 6)

The issue's own option A: `application.usecase.<usecase>` (e.g. `confirmorder`). Never
ambiguous about placement, but in hexagonal it becomes one package per class
(`application.service.confirmorder` holding only `ConfirmOrderService`), it has no precedent
outside the vertical-slice blueprint (a different style by design), and two-file packages grow
at the same rate as the use cases. Same migration mechanism and CI as option 1.

**Points cut:** 1 (form fit for hexagonal), 4, 5, 6.

## References

| Claim | Source |
|---|---|
| Subpackage-per-aggregate precedent | `.claude/rules/persistence.md:38-45` · `0045-persistence-subpackage-per-aggregate.md` |
| `Record` exemption already in the ArchUnit template | `test-architect/templates/ArchitectureTest.java.example:97-102` · `0095-…` |
| The naming convention is blueprint data | `@CLAUDE.md` invariant 7 · `extensions.json` `export.rewrite` op `blueprint_vocabulary` |
| The active blueprint travels into the project | `@CLAUDE.md` invariant 9 · `export.blueprint_copy` |
| A new top-level block of `extensions.json` travels; only `export` is dropped | `extensions.json` `export.rewrite` op `drop_block` |
| The designer did not write blueprints until this record | its `## Contract` before this change · 0087 Option 1 cons · 0095 References |
| A claim about data in `extensions.json` is checked by `schema`, not by a YAML step | `claude-code-architect-designer/references/ci-coverage.md` · 0084 |

## Propagation

| File | Change |
|---|---|
| `.claude/blueprints/clean-architecture-single-module/clean-architecture-single-module.yaml` · `clean-architecture-multi-module/…yaml` | `Grouping` entry in the naming comment; stale "must name `Command` as an explicit exception" replaced by the record exemption |
| `.claude/blueprints/hexagonal/hexagonal.yaml` | `Command` and `Grouping` entries in the naming comment |
| `.claude/schemas/extensions.json` | `migrations` block with its first entry, `use-case-subpackage-per-aggregate` |
| `.claude/hooks/ArchHook.java` + `ArchHook.jar` | `checkMigrations` in `schema` |
| `.claude/skills/arch-adopt/SKILL.md` | step 5 keeps the ids already shown; step 9 prints pending migrations, never runs them; report line; `Reads` |
| `.claude/skills/claude-code-architect-designer/SKILL.md` | `Blueprints` paragraph and `## Contract`: writes `.claude/blueprints/**` after approval |
| `.claude/skills/use-case-design/templates/use-case-spec.md.example` + 13 `examples/UC-1*` | use-case paths carry the aggregate segment; note on when they do |
| `.claude/skills/domain-modeling/templates/{Command,UseCasePort}.java.example`, `domain-spec.md.example` | `port.in.user`, `service.user` |
| `.claude/skills/jobs-architect/templates/*.java.example` (8) | input-port imports and outbox `package` lines grouped |
| `.claude/skills/messaging-architect/templates/{KafkaConsumerAdapter,OutboxRelayPublisher}.java.example` | `stock`, `outbox` |
| `.claude/skills/rest-api-architect/templates/{Controller,RestMapper}.java.example` | `port.in.user`; `PageCriteria` stays at root |
| `.claude/skills/test-architect/templates/{TestFixtures,ControllerTest,UseCaseTest}.java.example` | `port.in.user`, `service.user` |
| `.claude/agents/archunit-installer.md` | the `Command` example no longer names a flat `application.usecase` |
| `CLAUDE.md` | `arch-adopt` routing row names the migration step |
| `docs/{en,pt-br}/10-arch-adopt.md` | migrations paragraph and report example |
| `docs/{en,pt-br}/06-claude-code-architect-designer.md` | `.claude/blueprints/**` dropped from "does not write" |
| `docs/{en,pt-br}/07-ci-validate.md` · `.github/workflows/validate.yml` | the new test step |

Goes to the generated project: **yes** — the active blueprint through `export.blueprint_copy`,
its convention rendered into `naming.md` by `blueprint_vocabulary` (checked by exporting
`clean-architecture-single-module` and `hexagonal` to a scratch directory), the exemplars with
their skills, and the `migrations` block inside `extensions.json`, which `drop_block` leaves
in place. Already-generated projects get the migration prompt on their next `/arch-adopt`.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · hooks-cross-platform › schema blocks a migrations entry no project would see` | `schema` exits 2 naming the entry for a misspelled blueprint, a missing field, a reused id and an id out of pattern; exits 0 on the shipped block and on a project's copy (no blueprint catalog) | green on the tree (6/6) · red with the `checkMigrations` call commented out: 4 cases failed by name |
| `validate · design › frontmatter schema` | every edited skill and agent still passes its class, sections and export entry | green |
| `validate · exemplar-imports` | the edited `.java.example` imports still resolve | not run locally (network); project-internal imports only changed |
| — | `arch-adopt` step 9 is procedure run by the model inside a project | nothing testable in CI |
