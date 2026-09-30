# 0009 · `testing` becomes `test-architect`, and pipeline pieces follow `<territory>-architect`

- **Date:** 2026-09-08
- **Scenario:** "to standardize skill names, suggest recommendations for the name of the
  `.claude/skills/testing/SKILL.md` skill".
- **Decision:** rename to `test-architect`, and fix the naming rule for
  `/new-feature`-pipeline pieces. The two pipeline skills from another family stay as
  they are — see § What doesn't get renamed.
- **Status:** approved by Lucas Fernandes, on 2026-09-08

## The symptom

Ten skills, three name families, and one outside all of them:

| Family | Skills |
|---|---|
| activity | `use-case-design`, `domain-modeling` |
| role | `persistence-architect`, `rest-api-architect` |
| verb-object (tool) | `init-project`, `project-bootstrap`, `arch-doctor`, `java-patterns`, `claude-code-architect-designer` |
| **none** | **`testing`** |

`testing` was the only one-word name, the only one with no territory qualifier, and the
only one whose name didn't say it produces a partial. It also collided in reading with
`rules/testing.md` — the same name for a rule and a skill, which are different things.

## Constraints that eliminated options

| Constraint | Eliminates |
|---|---|
| Folder name = `name:` = slash command | Name with a space, uppercase, or slash |
| Mandatory kebab-case (`schemas/extensions.json`, `case: kebab`) | camelCase, snake_case |
| Don't shadow a native slash command (`/doctor`, `/init`, `/context`, `/memory`) | `/test` doesn't collide today, but is too generic to risk |
| Invariant 7 — no technology in the piece's name | `junit-testing`, `archunit-setup` |
| D17 — the `description` decides invocation, not the name | The name is human readability; renaming doesn't change behavior |

## Options evaluated

| # | Name | Score | Verdict |
|---|---|---|---|
| 1 | `test-architect` | 9 | **Approved** |
| 2 | `test-design` | 7 | Rejected — "design" covers only one of the two modes |
| 3 | `test-strategy` | 5 | Rejected — a noun, not an agent; and `testing-strategy` was already dropped in 0008 |
| 4 | keep `testing` | 6 | Rejected — zero cost, but keeps the outlier |

### Option 1 — `test-architect` (score 9)

Aligns with the two most recent naming decisions, `@0005` and `@0006`, which chose the
`-architect` suffix on purpose. Covers **both** modes of the piece: whoever architects
both designs the partial and installs the check. Explicit territory, zero technology.

**Cons:** `/testing` stops existing; whoever had it memorized types the wrong command
once. ~25 files touched.

**Points cut:** criterion 5 — the rename is pure maintenance, no functional gain. The
gain is readability and only pays off with the next piece.

### Option 2 — `test-design` (score 7)

Would align with `use-case-design`. Fails by describing only the design mode: the piece
also installs ArchUnit, and a name that hides half of what the piece does is the same
problem `testing` had, inverted.

### Option 4 — keep it (score 6)

Defensible: the name doesn't change behavior, and the rename cost is real. Loses because
the outlier would contaminate the next piece — without a written rule, the next name goes
back to being chosen by taste.

## The rule

> **A `/new-feature`-pipeline piece is named `<territory>-architect`.** Territory is the
> layer or artifact it designs, never the technology that implements it.

A piece that isn't in the pipeline keeps the verb-object family (`init-project`,
`arch-doctor`): they're tools, they don't design any partial.

## What doesn't get renamed

`use-case-design` and `domain-modeling` stay. Two reasons:

1. Cost — another ~15 files, for symmetry gain only.
2. `use-case-design` isn't a layer. It designs the boundary **before** any layer exists,
   and `-architect` fits it worse than it fits the others.

Left recorded as an open decision, not an oversight: whoever wants full symmetry writes a
new record.

`rules/testing.md` does **not** get renamed. A rule is named after its theme; that a
similarly-named skill exists is coincidence, not contract.

`.claude/decisions/` isn't rewritten — records 0002 and 0008 keep saying `testing`, which
was the name on that date. This record is what bridges the two.

## References

| Statement | Source |
|---|---|
| Folder name becomes the slash command; don't shadow a native one | `@CLAUDE.md` § Known pitfalls |
| Kebab-case and valid fields per file type | `.claude/schemas/extensions.json` |
| Technology in the piece's name violates "architectures are data" | `@CLAUDE.md` invariant 7 · `@.claude/decisions/0002-skill-use-case-design.md` box d |
| `-architect` suffix chosen on purpose | `@.claude/decisions/0005-persistence-rule-and-design.md` § Name |
| The name doesn't decide invocation; `description` does | D17 · `@.claude/decisions/0007-pipeline-skills-invocation.md` |
| The piece has two modes, and the name has to cover them | `@.claude/decisions/0008-testing-rule-and-design.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/testing/` → `.claude/skills/test-architect/` | `git mv`; `name:` and body title |
| `CLAUDE.md`, `README.md`, `CONTEXT.md` | routing, tree, inventory |
| `.claude/skills/project-bootstrap/SKILL.md` | step 6.7, `## Skill contract` list, report, final delivery |
| `.claude/skills/project-bootstrap/{references/dependency-catalog.md,templates/pom.parent.xml.example,templates/root.CLAUDE.md.example}` | citations |
| `.claude/skills/{use-case-design,rest-api-architect,persistence-architect}/**` | boundary tables and partials map |
| `.claude/agents/project-initializer.md` | citation |
| `.claude/blueprints/{hexagonal,clean-architecture-multi-module}/*.yaml` | `archunit: true` comment |
| `.claude/skills/claude-code-architect-designer/templates/SKILL.md.example` | cited exemplar |

Goes to the generated project: **yes** — the skill already went through step 6.7, now
with the new name.
