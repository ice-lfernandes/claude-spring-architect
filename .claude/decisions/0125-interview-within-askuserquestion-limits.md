# 0125 · `/init-project`'s interview fits `AskUserQuestion`'s limits — an ordered list sent in batches of four, features asked only where the blueprint says `false`

- **Date:** 2026-10-06
- **Scenario:** "faca o ajuste do bug encontrado" — the `/init-project` run of this session failed
  its first `AskUserQuestion` with `InputValidationError ... "too_big", "maximum": 4, path:
  ["questions",0,"options"]`: step 2 asks *Features* as one multi-select of seven options.
- **Decision:** A6 — A1's ordered list and batching, with A2's single source for features: a feature the blueprint sets `true` is confirmed, only the `false` ones are asked, in one multi-select. Edits to `.claude/skills/init-project/SKILL.md`, `.claude/skills/jobs-architect/SKILL.md`, both `11-pitfalls.md`, `root.CLAUDE.md.example`, both `02-init-project.md`. No new piece.
- **State:** approved by Lucas Fernandes, on 2026-10-06

## Reproduced on disk before classifying

Checked against `b7baeb3`.

| Claim | On disk |
|---|---|
| Step 2's first call asks *Features* with seven options | Confirmed — `init-project/SKILL.md` step 2: "REST, JPA + Flyway, Kafka, SQS, OpenAPI, Testcontainers, Actuator (multi-select)". The tool schema caps `options` at 4 (`maxItems: 4`); this session's call was rejected whole |
| Step 2's second call carries six questions | Confirmed — groupId, project name, bounded context, the transport topology, and the two questions of `sonarqube-setup` § 2. The tool caps `questions` at 4 per call |
| The pitfall page names the floor of 2 options and the ceiling of 4 questions, not the ceiling of 4 options | Confirmed — `docs/pt-br/11-pitfalls.md` § `AskUserQuestion`, `docs/en/11-pitfalls.md` same section; `root.CLAUDE.md.example` names only the one-option case |
| `jobs-architect` step 3 lists an axis with five values | Confirmed — *Kind*: polling pass, calendar-anchored job, bulk run, fire-and-forget background task, schedule created at runtime |
| The runtime already refuses an oversized call | Confirmed in this session — the call never reaches the user; nothing to guard |

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Observed in this session, first call of the interview | create nothing |
| 2 — Trigger | `/init-project`, typed | 1 |
| 7 — Mandatoriness | The runtime already rejects the call with `InputValidationError`; a hook would re-check what the tool schema enforces | 7, 8 (§ 2.2, last row) |
| 8 — Destination | `init-project` stays here; the pitfall line in `root.CLAUDE.md.example` and `jobs-architect` travel | — |
| Features shape (user) | First answer: two multi-selects. Revised at approval: every feature the blueprint sets `true` is already confirmed; ask only the `false` ones | A1's two multi-selects, A2's fixed messaging question |
| Batching (user) | One ordered list, sent in batches of at most four, answered questions skipped | three calls fixed by hand |
| Pitfall scope (user) | Both `11-pitfalls.md` pages and `root.CLAUDE.md.example` | docs only |
| Scope (user) | `jobs-architect`'s *Kind* axis in the same change | — |
| 17 — CI | Nothing testable: how many questions a skill sends is model behaviour; `schema` covers the frontmatter of the edited skills | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| A6 | A1, with features asked only where the chosen blueprint sets them `false`, in one multi-select | 9 | **Approved** |
| A1 | Ordered question list + batches of ≤ 4, features in two multi-selects, *Kind* capped at four, pitfall in three files | 8 | Superseded by A6 at approval — re-asks what the blueprint already decided |
| A2 | Same, features reduced to one fixed messaging question, the rest from the blueprint's `features:` | 7 | Superseded by A6 — hardcodes which features are open instead of reading the blueprint |
| A3 | Same as A1, the three calls redistributed by hand instead of a batching rule | 5 | Rejected — overflows the first time a question is added |
| A4 | create nothing — the model retries after the error | 3 | Rejected — the skill keeps instructing an impossible call |
| A5 | `PreToolUse` hook on `AskUserQuestion` counting options and questions | 2 | Rejected — the tool schema already validates; mirror of invariant 6 |

### A6 — A1 with features read from the blueprint (score 9)

**Motivator:** the user, at approval — a feature the blueprint turns on was already chosen when
the blueprint was; asking it again is a second source for the same fact (invariant 7).

**Change:** A1's ordered list and batching rule, except *Features*: one multi-select over the
features the chosen blueprint sets `false`, among the seven `/init-project` offers; none `false`
→ no question. It depends on the blueprint answer, so the batching rule moves it to the call
after *Architecture* when the blueprint is not an argument. `features[]` passed to
`project-initializer` is the blueprint's `true` set plus the ones checked — the agent's input
contract is unchanged.

**Fits one question:** every blueprint on disk leaves Kafka and SQS `false` (two options);
`custom.template.yaml` leaves four of the seven (JPA + Flyway, Kafka, SQS, OpenAPI) — still
within the ceiling.

**Points cut:** 4 Enforcement half — prose shaping a call the runtime already validates.

**CI:** nothing testable — model behaviour. `validate · design › frontmatter schema` covers both
skills.

### A1 — ordered list + batches (score 8)

**Motivator:** axis 1 — the skill's text tells the model to send a call the tool refuses, in
every run that reaches *Features*.

**Change:**
- `init-project/SKILL.md` step 2: the three hand-written calls become one ordered list —
  architecture, build, features A (REST, JPA + Flyway, OpenAPI, Actuator), features B (Kafka,
  SQS, Testcontainers), artifactId, groupId, project name, bounded context, transport
  topology, Sonar server, Sonar authentication, then the follow-ups. Skip what the arguments
  answer; send the rest in order, at most four questions per call and two to four options per
  question; a question whose default or options depend on an answer not yet given goes to the
  next call. Each feature option names the blueprint's default; an unchecked option is off.
  A free-text field offers its default and one variant derived from an answer already given,
  the value itself typed under Other.
- `jobs-architect/SKILL.md` step 3: an axis with more than four values (*Kind* has five) offers
  the four the specs leave plausible, the fifth typed under Other — the same move
  `init-project` already makes for blueprints.
- `docs/{pt-br,en}/11-pitfalls.md` § `AskUserQuestion`: the ceiling of four options per question,
  with this session's error. `root.CLAUDE.md.example`: the same line, so generated projects
  carry it.

**Pros:** the count of calls follows from the arguments instead of being fixed; adding a question
later cannot overflow a call; every feature stays asked, as the user wanted.

**Cons:** up to four calls instead of three when no argument is given and the transport answer
needs two follow-ups; two feature questions spend half of one call.

**Points cut:** 4 Enforcement — the batching is prose, but the runtime enforces the limit
itself, so the prose only shapes a call that is already guarded.

**CI:** nothing testable — model behaviour. `validate · design › frontmatter schema` covers both
edited skills' frontmatter, class and sections.

### A2 — messaging only (score 7)

One question (none / Kafka / SQS / both), the rest from `features:` in the blueprint — a single
source (invariant 7) and one question saved. Rejected by the user: every feature stays a choice
at generation time.

### A3 — calls fixed by hand (score 5)

Three explicit calls of at most four questions. Works today, overflows the first time a
question is added. Cut: 5 Maintenance, 6 Precedent.

### A4 — create nothing (score 3)

The model reads the error and retries, but the skill keeps instructing an impossible call; this
session's run stopped on it. Cut: 1, 4, 5.

### A5 — a hook on `AskUserQuestion` (score 2)

The tool schema already validates the input before the call reaches the user. A hook would be a
process per question confirming what the runtime enforces — the mirror of invariant 6. Cut: 1,
5, 9.

## References

| Claim | Source |
|---|---|
| 2–4 options per question, 1–4 questions per call | The `AskUserQuestion` tool schema; the `InputValidationError` of this session |
| A guarantee only against an unguarded, observed failure | `@CLAUDE.md` invariant 6; `references/decision-matrix.md` § 2.2, last row |
| "Up to four as options, the rest typed under Other" | `init-project/SKILL.md` step 2, the architecture question — precedent for *Kind* |
| The interview lives in `/init-project` | `@.claude/decisions/0123-lessons-learned-020-init-project-run.md`, D1 |
| A pitfall goes to `docs/*/11-pitfalls.md`, never `CLAUDE.md` § Known pitfalls | `@.claude/decisions/0090-repo-pitfalls-leave-claude-md.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/init-project/SKILL.md` | Step 2: three hand-written calls replaced by an ordered list of ten questions; features only where the blueprint says `false`; ≤ 4 questions per call, 2–4 options per question, dependent questions to the next call; free-text fields offer default + derived variant. Step 3: what `features` carries |
| `.claude/skills/jobs-architect/SKILL.md` | Step 3: never more than 4 options — *Kind* offers four, the fifth under Other |
| `docs/pt-br/11-pitfalls.md`, `docs/en/11-pitfalls.md` | § `AskUserQuestion`: the 4-option ceiling, with this session's error |
| `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example` | Known-pitfalls line: more than 4 options or questions fails too |
| `docs/pt-br/02-init-project.md`, `docs/en/02-init-project.md` | Example interview: features asked only where the blueprint says `false`; bounded context its own question |

Goes to the generated project: **yes, through step 6.6's `export`** — `jobs-architect` and the
root `CLAUDE.md` line. `init-project` stays here (`export.skills.exclude`).

## CI coverage

Nothing testable in CI: how many questions and options a skill sends is model behaviour, and
the runtime itself rejects an oversized call. Ran before committing: `ArchHook.java schema`
(exit 0), `claude plugin validate .claude/skills` (passed), `TemplateCommentsTest` (green).
