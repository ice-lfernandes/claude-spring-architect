# 0010 · The executor and `/new-feature` stay unwritten until a real spec exists; the three parameters that define them get fixed today

- **Date:** 2026-09-08
- **Scenario:** "we already have the skills invoked by the model: domain-modeling,
  rest-api-architect, persistence-architect, test-architect and use-case-design. Next
  steps: 1. new user-invocable skill `/new-feature` (orchestrator); 2. new subagent
  `java-spring-boot-engineer`, which receives the complete spec and writes the code,
  probably with a model other than opus."
- **Decision:** **Create nothing this cycle.** The matrix's sixth answer. What gets
  recorded here are the two pieces' parameters, so the interview doesn't repeat when the
  gate opens.
- **Status:** approved by Lucas Fernandes, on 2026-09-08

## What motivated the record

This is the first record where the answer is "create nothing" and a file still gets
saved. The reason is in § 3.5 of the skill: axis 8 answered **both** — the two pieces go
to the generated project —, and that alone requires saving. On top of that, three
parameters were fixed by the user's explicit answer, and losing them costs the whole
interview again six months from now.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Concrete symptom | None. `docs/use-cases/` is empty: none of the five layer skills has run against a real use case | All, this cycle. No symptom is anti-pattern 9 |
| 6 — Isolation | The executor generates N Java files and returns a short report; the interview already happened, the spec replaces the conversation | Confirms Form 3 for the executor, once written |
| 8 — Destination | **Both** — the two pieces operate on a project that already exists | Requires saving this record; requires step 6.7 and a new step for agents |
| — Model | `sonnet` for the executor | Fixes reason 3 of matrix § 5 as the agent's third justification |
| — Name | `java-spring-boot-developer`, the one already recorded | Closes `java-spring-boot-engineer` and `spring-boot-implementer` |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | create nothing — run a real spec first | 9 | **Approved** |
| 2 | only `agents/java-spring-boot-developer.md` now | 6 | Rejected — respects D-0002's order but still has no spec to execute |
| 3 | both pieces now | 4 | Rejected — anti-pattern 9 doubled, and neither's acceptance criterion would be verifiable |
| 4 | only `skills/new-feature/SKILL.md` now | 3 | Rejected — inverts D-0002 and step 7 of the pipeline would point to a nonexistent agent |

### Option 1 — create nothing (score 9)

**Motivator:** axis 1. Five layer skills were written in two days and **none has run
against a real use case**. The acceptance criteria of `persistence-architect`,
`rest-api-architect` and `test-architect` are declared as *pending* in `CONTEXT.md`
(items 9, 10 and 11). Building the sixth and seventh piece on top of five unverified ones
multiplies the debt instead of paying it.

**Pros:**

- D-0002 § Phasing had already written the gate: Phase 3 unlocks with "≥ 2 complete specs
  exist for it to execute," and Phase 4 with "Phases 2 and 3 ready." This decision doesn't
  invent the rule — it honors the one already there.
- Running the chain end to end is the only way to find out whether the spec is enough for
  an executor to work without asking anything. If a question is needed, the defect is in
  the spec, and it gets fixed in one of the five skills — not in the agent.
- The real partials will say which fields are missing from
  `templates/*-spec.md.example`. Writing the executor before that fixes the reading
  contract against a format that's still speculative.

**Cons:**

- Postpones two pieces the `README.md` already promises (line 110, "Packaged pipeline")
  and that `monetization-roadmap.md` § 191 already records as an unfulfilled promise. The
  documentation debt stays open one more cycle.

**Points cut on the rubric:** criterion 5 (maintenance cost) — nothing lost; criterion 7
(propagation) — loses the fraction of not closing the routing the `README.md` promises.

### Option 2 — `agents/java-spring-boot-developer.md` now (score 6)

**Motivator:** axis 6, and D-0002's order, which puts the executor before the
orchestrator.

Legitimate and violates no invariant: all three reasons in § 5 apply (preserve context,
restrict tools, `model: sonnet`). Lost for one reason, the mirror of Option 1's: without a
complete spec on disk, the acceptance criterion declared in `CONTEXT.md` item 13 —
"implements a spec end to end with a green build, without asking the user a single
question" — isn't verifiable. The seventh piece would be left with an unmet criterion.

If the gate opens and there's only time for one piece, **it's this one**.

### Option 3 — both pieces now (score 4)

Anti-pattern 9 applied twice. Adds a problem the others don't have: `/new-feature`
delegates to the executor, so the contract between the two can only be validated by
running them, and running requires the spec that doesn't exist. Two new pieces, zero
verification.

### Option 4 — only `/new-feature` now (score 3)

Inverts D-0002's phasing without presenting a new reason. The orchestrator's step 7 would
delegate to `java-spring-boot-developer`, which doesn't exist — a dead path inside the
repository's most visible piece.

## Parameters fixed, for when the gate opens

These are this record's useful product. They don't get asked again.

| Parameter | Value | Reason |
|---|---|---|
| Agent name | `java-spring-boot-developer` | Already recorded in D-0002 § Phasing, `CONTEXT.md` items 13 and 14, and `0003` § Motivator. `engineer` and `implementer` were considered and rejected: renaming costs propagation across four files and fixes no defect. Contrasts with D19, where the rename fixed a name outside the `<territory>-architect` pattern — here the agents' pattern is different, and `developer` doesn't break it |
| Agent `model` | `sonnet` | The spec closes the decisions before the agent starts; what's left is translating spec into code while citing rules. Satisfies reason 3 of matrix § 5 — the third of the three agent justifications, alongside preserving context and restricting tools. `haiku` rejected: generating an aggregate with invariants, a manual mapper and a migration isn't triage |
| Destination (axis 8) | **Both** — this repository and the generated project | Both pieces read `docs/use-cases/` and write `src/`. They only work **inside** a project that already exists, unlike `project-bootstrap`, `init-project` and `claude-code-architect-designer`, which serve before it |
| Build order | Executor first, `/new-feature` after | D-0002 § Phasing, Phases 3 and 4. The original request had the reverse order |

## The gate — what unlocks the two pieces

One condition, and it isn't negotiable for convenience: **two complete specs in
`docs/use-cases/`**, each with the five partials filled in by skills actually running,
and no skill having edited another's file. It's stage 2's acceptance criterion, written
in `CONTEXT.md` item 12.

**Obstacle to state, because it isn't obvious:** this repository isn't a Java
application — no `pom.xml`, no `src/`, and `CLAUDE.md` says so in its first line. The
five layer skills read the active blueprint's packages and write about code that
exists. So the real spec **cannot run here**. It has to run in a project generated by
`/init-project`, and that project is what produces the gate's two specs.

This adds a step to the critical path that no prior record had written:

```
/init-project  →  real Spring project
     ↓
/use-case-design  →  UC-001/00-use-case.md
     ↓
/domain-modeling · /persistence-architect · /rest-api-architect · /test-architect
     ↓
UC-001 complete   +   repeat for UC-002
     ↓
GATE OPEN  →  agents/java-spring-boot-developer.md  →  skills/new-feature/
```

The pending acceptance criteria of items 9, 10 and 11 of `CONTEXT.md` all close in this
same run — it's not extra work, it's the same work.

## Propagation gap found, and still to be fixed

Axis 8 answered "both," and checking how a piece gets copied to the generated project
turned this up: **step 6.7 of `project-bootstrap` copies skills and nothing else**. The
table has one row per skill; there's no step that copies `.claude/agents/**`. Step 7
copies `ArchHook.java` and `extensions.json`, not agents.

Until that's fixed, invariant 9 — "the generated project is autonomous" — breaks the day
the executor is written: the copied `/new-feature` would delegate to an agent that wasn't
copied with it.

**Not fixed now**, deliberately: adding an empty step 6.8, to copy an agent that doesn't
exist yet, is the same preemption this decision rejected. It's written here and in
`CONTEXT.md` so whoever writes the executor finds it.

## References

| Statement | Source |
|---|---|
| The executor's gate is "≥ 2 complete specs"; the order is executor → orchestrator | `@.claude/decisions/0002-skill-use-case-design.md` § Phasing, Phases 3 and 4 |
| The executor agent is legitimate by reason 1, and passes the counter-test because the interview already happened | `@.claude/decisions/0002-skill-use-case-design.md` § Scope revision, line c |
| The name `java-spring-boot-developer` supersedes `feature-builder` | `@.claude/decisions/0002-skill-use-case-design.md` § Phasing, last row |
| Layer skills emit spec; code comes from the executor | D15 · `@.claude/decisions/0003-skill-domain-modeling.md` |
| An agent needs one of three reasons, and a different `model:` is the third | `references/decision-matrix.md` § 5 |
| A piece with no observed symptom is anti-pattern 9 | `references/decision-matrix.md` § 7 |
| The `model`, `tools` and `skills` fields of an agent are camelCase-sensitive and native | `references/frontmatter-fields.md` § Subagent |
| Stage 2's acceptance criterion is two complete specs with no skill editing another's file | `CONTEXT.md` item 12 |
| The acceptance criteria of items 9, 10 and 11 remain unmet | `CONTEXT.md` items 9, 10, 11 |
| This repository isn't a Java application | `@CLAUDE.md`, first section |
| The generated project has to be autonomous | `@CLAUDE.md`, invariant 9 |
| Step 6.7 copies skills and doesn't copy agents | `@.claude/skills/project-bootstrap/SKILL.md` § 6.7 and § 7 |

## Propagation

| File | Change |
|---|---|
| `CONTEXT.md` § 2, decisions table | Row **D20** — the decision to postpone, the four fixed parameters, and the reason |
| `CONTEXT.md` § inventory | Row for record `0010`; **D20** note on the `agents/java-spring-boot-developer.md` and `skills/new-feature/` rows, both still `❌ missing` |
| `CONTEXT.md` item 13 | Fixes `model: sonnet` and confirms the name `java-spring-boot-developer`; adds the step 6.7 gap as work that comes with the piece |
| `CONTEXT.md` item 14 | Fixes the "both" destination and points the gate to this record |
| `CONTEXT.md` § stage 2 acceptance criterion | Writes the obstacle: the real spec requires a generated project, because this repository doesn't compile Java |

No file under `.claude/skills/**` or `.claude/agents/**` was created or changed — that's
what the decision mandates. The `@CLAUDE.md` routing table doesn't change: there's no new
piece to route. Step 6.7 of `project-bootstrap` doesn't either, and the agents gap stays
recorded in `CONTEXT.md` item 13 instead of fixed — fixing it now would mean creating a
step to copy a file that doesn't exist.

Goes to the generated project: **no** — it's a decision record about this
meta-repository, invariant 9. The **pieces** it describes will go, once they exist.
