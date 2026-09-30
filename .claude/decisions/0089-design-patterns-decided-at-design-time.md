# 0089 · Design patterns are decided in the partial of the layer they shape, not by the executor after approval

- **Date:** 2026-09-30
- **Scenario:** "Tendo em vista que essa skill: gof-design-patterns envolve em criar possiveis
  classes e interfaces que mudam bastante o desenho geral da spec de implementacao [...] a
  skill orquestradora new-feature deve carrega-la e considera-la para desenho de
  implementacao?" — follow-up: "mesmo nas camadas de infra, rest, jobs, em tese é possivel
  fazer uso de design patterns como template-method (exemplo: sistema tem 5 listeners que
  fazem tudo igual e so uma parte especifica)".
- **Decision:** Option 1 — each design skill decides the patterns of its own layer through
  `gof-design-patterns` § Design-time use, and writes them into its partial's `## Design
  patterns`; consolidation carries them into the spec; the executor implements them and adopts
  one alone only for a symptom on disk. Supersedes the executor-side "force named in the spec"
  trigger of 0077; the `SubagentStart` injection itself stays.
- **State:** approved by Lucas Fernandes, on 2026-09-30 — option 1

## What the context showed

| Fact | Where |
|---|---|
| The catalog's entry rule admits "a force the approved spec already names" | `.claude/skills/gof-design-patterns/SKILL.md` § Why this is a skill |
| Whoever applies that rule is the executor, **after** approval, before Block 1/3/M/J | `.claude/agents/java-spring-boot-developer.md` § Design patterns |
| No partial template has a section for patterns — the classes a pattern creates never reach `## 5 · Components to create` before approval | `domain-modeling/templates/domain-spec.md.example` and the other four `*-spec.md.example` |
| `domain-modeling`'s boundary table still says the catalog acts "after code exists, and only with a symptom" — stale since 0077 moved the trigger to design time | `.claude/skills/domain-modeling/SKILL.md:70` |
| `new-feature` says the catalog is "not a pipeline step" and never invoked | `.claude/skills/new-feature/SKILL.md:124` |
| The full catalog already triages Template Method, and prefers composition when one step varies | `gof-design-patterns/references/pattern-catalog.md:64` |
| A `build`-class Skill call is blocked while a design phase is open — no design skill, `new-feature` included, can *invoke* the catalog; reading its file is not restricted | `skill_classes.*.blocked_during_design` in `.claude/schemas/extensions.json` |

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | None observed; the gap is structural: the approved spec cannot show the shape a pattern will give the code | Nothing on its own — weighs against any new piece |
| 5 — Nature | A procedure step inside design skills that already exist | Form 4 (a rule would have to name skills — invariant 1), Form 5 |
| 7 — Mandatoriness | A template section, always present, "none" when empty — persuasion, no hook | Forms 7, 8 |
| 8 — Destination | Both — every piece touched travels in `export.skills.include` | — |
| 9 — Integration | Patterns appear in every layer (the user's 5-listener Template Method example is messaging); each layer already has one owner | A single owner for all layers |
| Executor | Implements what the spec decided; on its own only for a symptom already on disk, citing `file:line` | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Each design skill decides the patterns of **its own** layer, through one procedure owned by `gof-design-patterns` and cited by all | 8 | **Approved** |
| 2 | `domain-modeling` alone decides patterns | 6 | Rejected — leaves infra patterns (the listener example) to the executor after approval |
| 3 | A new pipeline step with its own partial (`15-padroes.md`) | 4 | Rejected — one more piece, re-reads every partial after the components are fixed |
| 4 | `new-feature` loads the catalog in its own context | 2 | Rejected — the orchestrator does not design, and records the decision nowhere |
| 5 | Create nothing | 3 | Rejected — the approved spec keeps under-describing the code |

### Option 1 — layer owners, one cited procedure (score 8)

**Motivator:** axis 9 — a pattern shapes the classes of one layer, and each layer already
has exactly one design owner. The listener example is decided by the skill that sees the
listeners.

**Mechanism.**

1. `gof-design-patterns/SKILL.md` gains `## Design-time use`: how a design skill checks the
   forces of the spec **and** the symptoms in its layer's existing code against § Catalog
   (and `references/pattern-catalog.md` when no row matches), and the shape of the
   `Design patterns` table it writes. Single owner — invariant 2; the five skills cite it.
2. Each of `domain-modeling`, `persistence-architect`, `rest-api-architect`,
   `messaging-architect`, `jobs-architect` gets one step citing that section, and its
   partial template gets a `## Design patterns` section: force (spec line or `file:line`) ·
   pattern · classes and interfaces created · "When not" checked. Always present; "None —
   no force in the spec, no symptom on disk" when empty. The classes go into the partial's
   components table as well.
3. `new-feature`'s consolidation carries every partial's rows into one `## Design patterns`
   section of the spec, naming the partial that decided each (`feature-spec.md.example`,
   `feature-spec-short.md.example`) — one section, so the executor reads one list; a missing
   section or a row without a force stops consolidation. § "not a pipeline step" is rewritten
   to say where patterns are now decided.
4. Refactoring code of an already-implemented case (the five existing listeners) is an
   impact row + `CHANGELOG.md`, the path `/new-feature` already has — no new mechanism.
5. Executor: implements the pattern rows of the spec; on its own only for a symptom on disk
   (`file:line` in the block feedback); the "force named in the spec" trigger leaves its
   body, since the spec now arrives decided. The `SubagentStart` injection (0077) stays.
6. `domain-modeling:70` boundary row and `rest-api-architect:88` collision paragraph are
   corrected.

**Pros:** the approved spec shows the final shape; no new piece; one owner of the procedure;
covers every layer; reuses the impact/CHANGELOG path for refactors.

**Cons:** touches ~12 files; design-time patterns invite speculation — held only by the entry
rule (spec line or `file:line`) and the "When not" column; no observed failure (axis 1).

### Option 2 — `domain-modeling` only (score 6)

Covers most cases with three files, but leaves the user's own example (listeners, adapters,
jobs) to the executor after approval — the gap this record exists to close.

### Option 3 — new pipeline step (score 4)

A pattern decision needs the layer's knowledge that the layer skill already has; a separate
step would re-read every partial and run last, after the components are already fixed. One
more piece, one more partial, no gain.

### Option 4 — `new-feature` loads the catalog (score 2)

The orchestrator does not design; loading the catalog there costs context every run and
writes the decision nowhere. It cannot invoke the skill during design anyway.

### Option 5 — create nothing (score 3)

The executor keeps choosing shape after approval; the spec keeps under-describing the code.

## References

| Claim | Source |
|---|---|
| A rule may not name a skill | `@CLAUDE.md` invariant 1 |
| One owner per norm, others cite | `@CLAUDE.md` invariant 2 |
| Catalog injected at `SubagentStart`, entry rule at design time | `.claude/decisions/0077-pattern-catalog-injected-at-subagent-start.md` |
| Design runs are docs-only, `build` unreachable | `.claude/decisions/0058-skill-classes-territory-schema.md` |
| Impact rows and `CHANGELOG.md` for changes to implemented cases | `@CLAUDE.md` § Known pitfalls — frozen folders |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/gof-design-patterns/SKILL.md` | New `## Design-time use` (the single owner of the procedure); contract rewritten to three uses; the `domain-modeling` collision paragraph widened to the five design skills |
| `.claude/skills/domain-modeling/SKILL.md` | Step 5b; boundary table and collision paragraph corrected; `Design patterns` row in § What the partial contains |
| `.claude/skills/persistence-architect/SKILL.md` | Step 7b; contents row |
| `.claude/skills/rest-api-architect/SKILL.md` | Step 8b; collision paragraph corrected; contents row |
| `.claude/skills/messaging-architect/SKILL.md` | Step 7b (the listener-skeleton case); contents row |
| `.claude/skills/jobs-architect/SKILL.md` | Step 8b; contents row |
| `.claude/skills/{domain-modeling,persistence-architect,rest-api-architect,messaging-architect,jobs-architect}/templates/*-spec.md.example` | `## Design patterns` section before `## Impact on approved use cases` |
| `.claude/skills/use-case-design/SKILL.md` | Out-of-scope row: patterns belong to each layer's partial |
| `.claude/skills/new-feature/SKILL.md` | "Not a pipeline step" paragraph rewritten; precedence row (pattern → layer partial, conflict stops); consolidation bullet (section required, force required) |
| `.claude/skills/new-feature/templates/feature-spec{,-short}.md.example` | `## Design patterns` section |
| `.claude/agents/java-spring-boot-developer.md` | § Design patterns rewritten: implement the spec's rows, adopt alone only on a disk symptom, a spec force left undecided is a finding; reads list; summary line; 0089 in references |
| `.claude/schemas/extensions.json` | `subagent_context.header` — the text the executor receives |
| `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example` | Routing row |
| `CLAUDE.md` | Routing row |
| `docs/{en,pt-br}/{03-new-feature,01-file-types|01-tipos-de-arquivo,09-differentiators|09-diferenciais}.md` | Pattern flow; the stale `skills:` preload claims (pre-0077) corrected in passing |

Not touched: `test-architect` — a test layer pattern (a shared fixture builder) is a test shape
question it already owns through its exemplars, and no force in a spec asks for one.

Goes to the generated project: **yes** — every skill and agent above is in
`export.skills.include` or travels as an agent, and `root.CLAUDE.md.example` is the generated
project's own routing table.
