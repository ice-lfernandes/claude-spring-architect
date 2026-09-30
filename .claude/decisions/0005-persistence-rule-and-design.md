# 0005 · Persistence enters as a leaf rule + manual design skill, named `persistence-architect`

- **Date:** 2026-09-07
- **Scenario:** "create the skill that produces a spec and implementation plan for JPA
  components, DAO, SQL, database modeling decisions, tuning; and defines the convention
  properties of the Spring datasource. It doesn't write the final code. It's one of the
  skills in the `/new-feature` pipeline. It should bring reference templates and links to
  best practices."
- **Decision:** Option 1 — Form 4 (`.claude/rules/persistence.md`) + Form 2
  (`.claude/skills/persistence-architect/SKILL.md`, `disable-model-invocation: true`)
- **Status:** approved by Lucas Fernandes, on 2026-09-07

## Interview

Only the axes that eliminated forms.

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | The `/new-feature` pipeline's `20-persistence.md` partial has no owner. No piece covers mapping, migrations, indexes, transactions or N+1 | "create nothing" |
| 2 — Trigger | `/persistence-architect`, invoked by hand within the pipeline, right after `domain-modeling` | Form 1 (auto-invocable) |
| 4 — Territory | `**/adapter/out/persistence/**` and `**/db/migration/**` — territories already listed in the planned-rules table of `00-index.md` | Reinforces Form 4 for the declarative part |
| 5 — Nature | Mixed: the JPA/migrations/transactions rules are declarative fact; reading the specs, interviewing and emitting the partial is procedure | No single form is enough — hence Option 1 being a pair |
| 6 — Isolation | No. The interview (which aggregate maps to which table, which index, which locking strategy) is the heart of the task and the output is a short file | Form 3 |
| 8 — Destination | Both. It applies inside the generated project — that's where persistence gets written | Requires steps 6.6 and 6.7 |
| 9 — Integration | No collision: `domain-modeling` stops at `10-domain.md` and is framework-free by rule; `20-persistence.md` is unassigned | — |
| — Name | The user chose `persistence-architect`, against `persistence-adapter` fixed in D14(d) | See § Name |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `rules/persistence.md` + `skills/persistence-architect/` | 9 | **Approved** |
| 2 | only `skills/persistence-architect/`, with the rules in the body | 4 | Rejected — the rules get two owners as soon as the rule file is written (invariant 2) |
| 3 | `agents/persistence-architect.md` | 3 | Rejected — none of invariant 5's three reasons applies |
| 4 | create nothing, absorb into `domain-modeling` | 2 | Rejected — the domain is framework-free by rule; persistence is an adapter |

### Option 1 — `rules/persistence.md` + `skills/persistence-architect/` (score 9)

**Motivator:** axis 5. The answer is mixed, and this repository's design splits the two
halves: what's always true becomes a leaf rule with `paths`; what's a sequence of steps
becomes a skill.

**Pros:**

- The rule auto-loads when touching `adapter/out/persistence/**` or `db/migration/**`,
  even when no one invokes the skill.
- Closes stage 2, item 9 of `CONTEXT.md` in the order written there: rule before skill.
- The skill is left with what belongs to it — procedure, form exemplars, external
  links — and cites the rule by path.
- Direct precedent: `api-rest.md` + `rest-api-architect`, the same pair already in use.

**Cons:**

- Two pieces instead of one; the rule has to exist before the skill makes sense.
- No invariant tensioned, except the name (see § Name).

**Points cut on the rubric:** criterion 6 loses half a point — the rule+skill pair has
precedent, but the persistence rule is the first with territory in two disjoint globs
(adapter and migrations).

### Option 2 — skill only, rules in the body (score 4)

Faster to ship and looks equivalent today. Fails tomorrow: `persistence.md` is in the
`00-index.md` planned-rules table and will be written. On the day it is, the rules exist
in two places and diverge on the first update — exactly the failure mode invariant 2
exists to stop. Caps at ≤ 4 for this.

### Option 3 — subagent (score 3)

Fails the counter-test: the interview is the heart of the task and the subagent doesn't
see the conversation; the output is a short partial, not a bulky report; no tool needs
restricting and no different model is needed. Invariant 5 violated.

### Option 4 — absorb into `domain-modeling` (score 2)

`architecture-ddd.md` bans `jakarta.*` and framework code in the domain. Putting JPA
mapping in the skill that details the domain invites exactly the leak the rule
prohibits. And it gives one piece two responsibilities, with two output artifacts.

## Name — supersedes D14(d) in part

D14 (`@.claude/decisions/0002-skill-use-case-design.md`, box d) fixed
`persistence-adapter` and rejected `jpa-persistence-architect` because technology in the
piece's name violates invariant 7: the same use case on MongoDB would need another
skill.

The user chose **`persistence-architect`**. D14's principle stands — no technology in the
name. Only the suffix changes, to align with `rest-api-architect`. JPA and Hibernate
enter as the default implementation **inside** the skill, not in its name.

This record supersedes D14(d) only regarding the suffix. `messaging-adapter` stays as it
is until someone decides otherwise.

## References

| Statement | Source |
|---|---|
| A rule never mentions a skill; `rules/` is a leaf | `@CLAUDE.md` invariant 1 |
| A theme with a single owner, cited by path | `@CLAUDE.md` invariant 2 |
| Boilerplate lives in `templates/*.example` of the skill that emits it | `@CLAUDE.md` invariant 3 |
| Agent only for preserving context, restricting tools, or changing model | `@CLAUDE.md` invariant 5 · `references/decision-matrix.md` § 5 |
| Technology in the piece's name violates "architectures are data" | `@CLAUDE.md` invariant 7 · `@.claude/decisions/0002-skill-use-case-design.md` box d |
| A new rule has to reach the generated project | `@CLAUDE.md` invariant 9 · `project-bootstrap/SKILL.md` steps 6.6 and 6.7 |
| Layer skills emit spec, not code | D15 · `@.claude/decisions/0003-skill-domain-modeling.md` |
| `persistence.md` is unwritten and its territory is already planned | `@.claude/rules/00-index.md`, planned-rules table |
| Write the rule before the skill | `CONTEXT.md` § 7, stage 2, item 9 |
| Rule + skill pair already in use | `@.claude/rules/api-rest.md` + `.claude/skills/rest-api-architect/` |
| External links live in `references/`, not in `rules/` | `claude-code-architect-designer/references/decision-matrix.md:6` · no rule in the repo has a URL |

## Propagation

| File | Change |
|---|---|
| `.claude/rules/persistence.md` | NEW |
| `.claude/rules/00-index.md` | moves from "planned" to "written" |
| `.claude/skills/persistence-architect/**` | NEW |
| `CLAUDE.md` | line in the routing table |
| `.claude/skills/project-bootstrap/SKILL.md` | step 6.6 (rule), step 6.7 (skill), `## Skill contract` |
| `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example` | rename `persistence-adapter` |
| `.claude/skills/use-case-design/SKILL.md` | rename `persistence-adapter` |
| `.claude/skills/use-case-design/templates/use-case-spec.md.example` | rename `persistence-adapter` (4 lines) |
| `CONTEXT.md` | piece status, stage 2 item 9, name note |

Goes to the generated project: **yes** — rule via step 6.6, skill via step 6.7.
