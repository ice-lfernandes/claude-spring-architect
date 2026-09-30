# 0003 · Layer skills emit spec, not code; `domain-modeling` is the first

- **Date:** 2026-09-07
- **Scenario:** "let's follow the next steps to build the new-feature skill; let's create
  the domain-modeling skill" — stage 2 of the pipeline fixed in
  `@.claude/decisions/0002-skill-use-case-design.md`.
- **Decision:** Form 2 — `.claude/skills/domain-modeling/SKILL.md`, **spec-only**
- **Status:** approved by Lucas Fernandes, on 2026-09-07

The classification (manual skill, name `domain-modeling`, owner of `10-domain.md`) had
already been fixed in `0002` § D14. This record exists because of the decision that
**wasn't** fixed: who writes the Java code.

## The conflict that motivated the record

`java-patterns/SKILL.md` declares, in its `## Contract`:

> **Writes** domain and application code in the generated project.

A `domain-modeling` that generated domain classes would create two owners for
`domain/**` and `application/**` — invariant 2 broken, and the failure mode is silent:
two skills generate different shapes of the same class and whichever ran last wins.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | **Spec-only** — only `10-domain.md`; code comes from the executor | 8 | **Approved** |
| 2 | Spec + code for its own layer | 6 | Rejected — would force amputating `java-patterns`'s contract and making the executor optional |
| 3 | Spec in the pipeline, code outside it | 3 | Rejected — behavior depends on invocation context; not verifiable |

### Option 1 — spec-only (score 8)

**Motivator:** the pipeline from `0002` already has an executor
(`java-spring-boot-developer`, stage 3) whose sole reason to exist is writing code from a
spec. Duplicating that capability in the layer skills empties it out.

**Pros:**
- Ownership without overlap: `domain-modeling` produces markdown, `java-patterns`
  produces Java. The split becomes about **timing** (before code exists / after, with a
  symptom), not about folder — and that's verifiable.
- The executor receives the four partials and writes everything at once, in compile
  order. Four skills each writing code in turn leaves the build broken midway.
- The spec is reviewable in a PR before a single line of Java exists.

**Cons:**
- **Declared inconsistency with `rest-api-architect`**, whose contract says it writes
  `**/adapter/in/rest/**` *and* `30-rest.md`. Left unresolved until that skill moves past
  stub; recorded as P7 in `@CONTEXT.md`.
- Without an executor, stage 2 produces specs nobody implements automatically. Accepted:
  stage 3 comes next, and a good spec is implementable by hand.

**Points cut on the rubric:** criterion 7 (complete propagation) — the inconsistency with
`rest-api-architect` is deliberately left open, not closed.

### Option 2 — spec + code (score 6)

Consistent with `rest-api-architect` as it stands today, and makes each skill usable on
its own. But requires rewriting `java-patterns`'s contract to "refactors existing code,
doesn't create it," and reduces the executor to a batch mode. Viable; lost because it
undoes stage 3's reason to exist, which was already approved.

### Option 3 — context-dependent behavior (score 3)

"Only spec inside `/new-feature`; also code by hand." The skill has no reliable way of
knowing how it was invoked, and a contract that changes with the caller can't be tested.

## Content decisions

| Question | Answer |
|---|---|
| `10-domain.md` blocks | The four: aggregate and value objects · invariants and where they're guaranteed · ports · events |
| Input | Derived from `00-use-case.md`; `AskUserQuestion` only for what the mother spec didn't fix. Without a mother spec, it stops |
| Exemplars | The five: `domain-spec.md.example` + `ValueObject` · `Aggregate` · `DomainEvent` · `UseCasePort` · `Command` |
| New rule | None. `architecture-ddd.md`, `naming.md`, `error-handling.md` and `code-quality.md` cover the territory |

## Tensions resolved inside the exemplars

Two rules collide with Java idiom. Both resolved in writing in the exemplar's header, not
silently:

| Tension | Rule | Resolution |
|---|---|---|
| `naming.md` asks for "never a public constructor" for a VO with invariants; in a public `record` the canonical constructor **cannot** be less accessible than the type | `naming.md` § Methods | Validation in the compact constructor — no construction path bypasses it. The `of` factory stays, to name the intent and normalize before validating |
| `code-quality.md` bans `Optional` in a field; the department is optional | `code-quality.md` § Clean Code | Null Object: `DepartmentId.unassigned()`. Required field, no `null`, no `Optional` |

## Verification performed

Not assumed. The five exemplars were split by the `// --- <File>.java` markers, compiled
with `project-bootstrap`'s typed exceptions and `@Service`/`@Transactional` stubs:

```
javac -d out $(find src -name '*.java')   → exit 0
```

And run, to prove the invariants actually trigger:

```
normalized email: lucas@example.com      (Email.of trims and lowercases)
trimmed name: 'Lucas'
rejected 'no-at-sign' -> EMAIL_INVALID
rejected 'a@b'         -> EMAIL_INVALID
rejected ''             -> EMAIL_REQUIRED
rejected short name     -> USER_NAME_LENGTH
after assignTo, isAssigned: true / original: false   (immutability)
```

## References

| Statement | Source |
|---|---|
| Pipeline, phasing and existence of the executor | `@.claude/decisions/0002-skill-use-case-design.md` § D14 |
| Two owners for the same path is a bug | `@CLAUDE.md` invariant 2 |
| The transaction opens and closes at the application layer | `@.claude/rules/architecture-ddd.md` § Application |
| Typed family and `errorCode` | `@.claude/rules/error-handling.md` |
| `Optional` forbidden as a field | `@.claude/rules/code-quality.md` § Clean Code |
| Static factory on a VO with invariants | `@.claude/rules/naming.md` § Methods |
| A skill that writes files is Form 2 | `references/decision-matrix.md` § 4 |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/domain-modeling/SKILL.md` | Created |
| `.claude/skills/domain-modeling/templates/domain-spec.md.example` | Created |
| `.claude/skills/domain-modeling/templates/{ValueObject,Aggregate,DomainEvent,UseCasePort,Command}.java.example` | Created; compile and run |
| `.claude/skills/java-patterns/SKILL.md` | `## Contract` clarifies the split by timing |
| `.claude/skills/use-case-design/SKILL.md` | `10-domain.md` stops being "(unwritten)" |
| `CLAUDE.md` | Line in the routing table |
| `.claude/skills/project-bootstrap/SKILL.md` | Step 6.7, `packages.map` fix, `## Skill contract` |
| `CONTEXT.md` | Inventory, P7 (`rest-api-architect` inconsistency), stage 2 roadmap |

Goes to the generated project: **yes, via step 6.7** — it's a development skill.
