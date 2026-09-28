# 0004 · The criterion for when a field becomes a value object is its own rule, not a section of `architecture-ddd.md`

- **Date:** 2026-09-07
- **Scenario:** "I want to create a file that applies to every class inside the
  domain/model package, domain module, that explains the concept of rich domain and
  avoids anti-patterns like `String email`, `String phone`, `String document`,
  `String cpf` — and reference templates to help the `/domain-modeling` skill"
- **Decision:** Form 4 — `.claude/rules/value-objects.md`, plus the exemplar
  `.claude/skills/domain-modeling/templates/ValueObjectCatalog.java.example`
- **Status:** approved by Lucas Fernandes, on 2026-09-07

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | `String email`, `String cpf`, `String phone`, `String document` appearing as aggregate fields. Types with formation rules treated as plain text | None — confirms it's not a preemptive piece (anti-pattern 9) |
| 2 — Trigger | Touching a file in `domain/model` | 1, 2, 3 (it's neither a procedure nor needs isolation) |
| 4 — Territory | `**/domain/model/**/*.java`, with a `**/domain/**/*.java` fallback for a blueprint that doesn't have a `model` sub-package | 5 (`CLAUDE.md` is the whole repo) |
| 5 — Nature | Declarative fact: decision criterion about types, plus catalog and counter-catalog | 1, 2, 3 |
| 7 — Enforceability | Rule only, verified by review. No hook, no ArchUnit in this iteration | Drops the hook (out of scope) |
| 8 — Destination | Both — this repo and the generated project | Requires propagation into step 6.6 |
| 9 — Integration | `architecture-ddd.md` § Domain owns "immutable value objects; validation in the constructor." The new rule owns **when** a field deserves to be a value object. Scope cut to primitive obsession only, to avoid duplication | Rejects the "full rich domain" scope |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `.claude/rules/value-objects.md` + `ValueObjectCatalog.java.example` | 9 | **Approved** |
| 2 | Only the exemplar in `domain-modeling/templates/`, no rule | 5 | Rejected — only loads when the skill is invoked; hand-written code never sees the criterion |
| 3 | New section in the root `CLAUDE.md` | 3 | Rejected — an identifiable territory exists; matrix § 2 mandates Form 4 |
| 4 | Create nothing — extend `architecture-ddd.md` | 3 | Rejected — mixes layer principle with typing criterion; the file stops having one theme |

### Option 1 — `.claude/rules/value-objects.md` (score 9)

**Motivator:** axes 4 and 5. Declarative fact with an identifiable file territory — the
exact row in the decision-matrix's § 2 table that points to Form 4.

**Pros:**
- Auto-loads by `paths` when touching `domain/model/**`. Doesn't depend on anyone
  invoking any skill.
- Sole owner of a theme with no owner today: `architecture-ddd.md` says value objects are
  immutable, but never says **when** to create one. The gap is real.
- The scope cut to primitive obsession keeps invariant 2 intact: cites
  `architecture-ddd.md`, `error-handling.md`, `naming.md` and `lombok.md` by path, never
  reproduces any of them.
- Narrow territory (`domain/model`) costs less context than `lombok.md` and `naming.md`,
  both `**/*.java`.
- The Java exemplar goes into `templates/` of the skill that emits it — invariant 3
  satisfied.

**Cons:**
- Literal glob `**/domain/model/**` doesn't match a blueprint that names the package
  differently (vertical slice, modular monolith). Mitigation: second glob
  `**/domain/**/*.java`. Falls short of `architecture-ddd.md`'s design, whose globs come
  from the active blueprint's `architecture_paths`.
- No automatic enforcement (axis 7). Depends on review.

**Points cut on the rubric:** criterion 4 (enforcement) — depends on persuasion, when
ArchUnit is available in the generated project. Criterion 6 partial — the glob fallback
has no exact precedent in the repo.

### Option 2 — only `ValueObjectCatalog.java.example` (score 5)

**Motivator:** axis 5 read only as "I want templates."

Delivers half the request. The exemplar is a form reference read by the executor when
`domain-modeling` runs; domain code written outside that flow never sees the criterion.
It's exactly the failure mode `paths` exists to close.

**Points cut:** criterion 1 (form fit) — matrix § 2 points to Form 4 and this option
ignores it.

### Option 3 — section in `CLAUDE.md` (score 3)

Costs every prompt of every session, for knowledge that only matters in `domain/model`.
`CLAUDE.md` is already close to the ~200-line target. Matrix anti-pattern 3.

### Option 4 — create nothing, extend `architecture-ddd.md` (score 3)

`architecture-ddd.md` fixes dependency direction and what each layer may import. A
catalog of primitive types and CPF formation rules is a different theme. Merging the two
makes the file lose its single theme, and it doesn't even have a glob — it's the only
rule without its own `paths`, by design.

## References

| Statement | Source |
|---|---|
| Declarative fact with territory → rule with `paths` | `references/decision-matrix.md` § 2, row 4 |
| A rule never mentions a skill or agent | `@CLAUDE.md` invariant 1 |
| A theme has one sole owner file; others cite by path | `@CLAUDE.md` invariant 2 |
| Code boilerplate lives in `templates/` of the skill that emits it, suffix `.example` | `@CLAUDE.md` invariant 3 |
| A new rule is only complete when the step that copies it is also updated | `@CLAUDE.md` invariant 9 |
| Rule with `paths: ["**/*.java"]` and a single principle — form precedent | `.claude/rules/lombok.md` |
| `architecture-ddd.md` is the only rule with no `paths`, by design | `.claude/rules/00-index.md` |
| Value object exemplar already exists, with `Email` and `UserId` | `.claude/skills/domain-modeling/templates/ValueObject.java.example` |
| Rules copied verbatim into the generated project | `.claude/skills/project-bootstrap/SKILL.md` step 6.6 |

## Propagation

| File | Change |
|---|---|
| `.claude/rules/value-objects.md` | New — `paths` with two globs, criterion, catalog, counter-catalog, mandatory form, admitted exception |
| `.claude/skills/domain-modeling/templates/ValueObjectCatalog.java.example` | New — `Cpf`, `Cnpj`, `Document` (`sealed`), `PhoneNumber`, `Money`, `Percentage` and three commented counter-examples |
| `.claude/rules/00-index.md` | Line in the written-rules table, "Verified by" column = Review |
| `.claude/skills/project-bootstrap/SKILL.md` | `value-objects.md` line in the step 6.6 table; `five exemplars` → `six exemplars` in the `domain-modeling` line of step 6.7; entry in the reading list of `## Skill contract` |
| `.claude/skills/domain-modeling/SKILL.md` | Rule cited in step 4 and in `## Contract`; new exemplar in the blocks table; pointer in the step-3 gap table |

Not changed, deliberately: `@CLAUDE.md`. The routing table lists skills, not rules —
rules are routed via `@.claude/rules/00-index.md`, which was updated.

Goes to the generated project: **yes, via step 6.6** — axis 8 answered "both." The Java
exemplar follows the `domain-modeling` skill, already copied by step 6.7.
