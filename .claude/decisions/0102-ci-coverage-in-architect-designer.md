# 0102 · CI coverage is decided with the piece, not in a later review

- **Date:** 2026-10-01
- **Scenario:** "quero adicionar seções/passos no procedimento para que sempre que uma decisão
  sobre criação de skill, agents, rule, hook (todas as formas) seja também analisado como aplicar
  a validação/teste nas pipes do github do projeto"
- **Decision:** option 1 — edit `.claude/skills/claude-code-architect-designer/` (axis 17, Phase 3 item 7, Phase 4 step 9) plus `references/ci-coverage.md`
- **State:** approved by Lucas Fernandes, on 2026-10-01
- **Goes to the generated project:** no

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Observed twice, and both times CI arrived in a separate, later review. `0084`: the guard modes added by `0063`–`0077` (`guard bash`, `guard sweep`, `compose gate`, `context subagent`) ran in every session with zero CI coverage until a staleness review of PR #39. `0099`: two defects of one run (Checkstyle's default `format`, template compilation) were caught by hand, because no check could see them. The procedure never asks about CI: `.github/**` appears only in the `## Contract` territory | create nothing |
| 2 — Trigger | Every run of `claude-code-architect-designer`, whatever the form | — |
| 5 — Nature | A sequence of steps inside an existing procedure | Forms 4, 5 |
| — Forms covered (user) | All eight, including 6a/6b and 5. A form with nothing testable records "nothing to test, because …" — the answer is mandatory, a test is not | "only 7 and 8" |
| — Pipelines (user) | Both: this repo's `validate.yml` / `templates.yml` / `.claude/.ci/`, and the generated project's `project-bootstrap/templates/ci.yml.example` + `ci-gradle.yml.example` when axis 8 = "both" | — |
| — Depth (user) | Analyze in Phase 3 **and** write the test or workflow step in Phase 4, after approval, as part of propagation | analysis-only |
| 7 — Mandatoriness (user) | Procedure only. Nothing shows the model skipping a CI question — the procedure never asked one. A `schema` check on decision records would be a guarantee against a failure nobody observed (anti-pattern 16) | Forms 7, 8 |
| 8 — Destination | This repo only: the skill is a creation skill, in `export.skills.exclude`. What it writes into `ci.yml.example` travels, as that template already does | — |
| 9 — Integration | `skill_classes.meta.write_allow` already holds `.claude/**` and `.github/**`: `.claude/.ci/`, the workflows and the generated project's CI templates are inside the territory. No class change | new skill, class edit |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Edit `claude-code-architect-designer`: new axis, a CI item per option, a CI step in Phase 4, a CI coverage map in `references/ci-coverage.md`, a `## CI coverage` section in the decision template | 8 | **Approved** |
| 2 | Analysis only: the axis, the Phase 3 item and the decision section; no Phase 4 step | 6 | Rejected — the test is still written a review later, the shape of `0084` and `0099` |
| 3 | New skill `ci-architect`, chained by the designer in Phase 4 | 4 | Rejected — a second owner of `.github/**` |
| 4 | Option 1 plus a `schema` check failing a new decision record without `## CI coverage` | 4 | Rejected by the user at axis 7 — anti-pattern 16 |
| 5 | Create nothing | 2 | Rejected — symptom observed twice |

### Option 1 — edit the designer, with a coverage map (score 8)

**Motivator:** axis 1 (0084, 0099) and the user's depth answer.

**Mechanism.**

1. `SKILL.md`
   - Phase 1: axis 17 — *CI coverage*. Applies to every form. Answered by the designer from
     `references/ci-coverage.md` and the workflows, asked only when the answer depends on the
     user (e.g. a network-bound check: path-filtered or every PR).
   - Phase 3: a 7th item per option — **CI**: the existing job/step that already covers the
     piece, the new test or step it needs (file + workflow + job), or "nothing testable,
     because …".
   - Phase 4: a new step after propagation — write the test (`.claude/.ci/<Name>Test.java`,
     single-file Java, against the jar when it exercises a hook mode) and register it in the
     job the map names; for axis 8 = "both", the generated project's CI templates too, and only
     with what exists inside the generated project. Verify it green, and once red with the
     defect injected — `0099`'s "Verified before committing". Never delegated: choosing the
     cases is design.
   - Phase 4 validation step and Phase 5 report: run the new test; report the CI line.
   - `## Contract`: Writes names `.claude/.ci/**`, `.github/workflows/**` and the two CI
     templates explicitly; Delegates excludes the CI step.
2. `references/ci-coverage.md` — new. Form → what already checks it (job and step names) →
   when a new check is needed → where it goes (`hooks-cross-platform` for behavior of the jar,
   `design` / `schema` for a claim about files, `templates.yml` for anything that needs
   network). Kept out of `SKILL.md`: read only in Phases 3–4.
3. `references/decision-matrix.md` — rubric criterion 7 (*complete propagation*) loses a point
   when the CI answer is missing; anti-pattern 21, "CI answer is *later*". Nine criteria stay
   nine — no reweighting of past scores.
4. `templates/decision.md.example` — `## CI coverage` section.
5. `docs/pt-br/06-claude-code-architect-designer.md`, `docs/en/06-claude-code-architect-designer.md`
   — the new axis and step.

**Pros:** closes the gap where it opened — the same run that designs the piece designs its
check. Most pieces need no new test (`schema` already covers frontmatter, class, export
manifest, injections), so the common answer is one line naming the existing check. The map has
precedents for every row (`0084`, `0099`, `0092`).

**Cons:** the map names jobs and steps of `validate.yml`; renaming a step leaves it stale.
Phase 4 grows by one step.

**Points cut in the rubric:** maintenance (−1, the map duplicates job names that live in the
workflows), context cost (−0.5, one more axis read on every run).

**CI:** `validate · design › frontmatter schema` already covers the skill file; the rest is
prose — see § CI coverage.

### Option 2 — analysis only (score 6)

Same Phase 1/3 changes and decision section, no Phase 4 step. Cheaper, but the test is still
written by someone else later — the exact shape of `0084` and `0099`. The user asked for
writing. Cut: enforcement (−2), complete propagation (−1).

### Option 3 — new skill `ci-architect` (score 4)

A second skill writing `.github/**`, which `skill_classes.meta` already grants the designer:
two owners of one territory. Needs a class, an `export.skills.exclude` entry and a hand-off of
the interview it would not see. Cut: maintenance (−2), form fit (−1), precedent (−1),
propagation (−1).

### Option 4 — option 1 plus a `schema` check on records (score 4)

A Form 7c-like check against a failure nobody observed (anti-pattern 16), a rule to exempt the
101 records before it, a jar rebuild. Rejected at axis 7 by the user.

### Option 5 — create nothing (score 2)

The symptom happened twice and cost two separate PRs of catch-up.

## References

| Claim | Source |
|---|---|
| Guard modes shipped without CI until a later review | `.claude/decisions/0084-ci-covers-jar-and-post-0075-guards.md` |
| Template defects only caught by hand; path-filtered workflow for network-bound checks | `.claude/decisions/0099-ci-tests-for-verbatim-templates.md` |
| CI tests are single-file Java in `.claude/.ci/`, run against the jar | `0084`; `.claude/.ci/*Test.java` |
| The designer's territory already includes `.github/**` | `skill_classes.meta.write_allow` in `.claude/schemas/extensions.json` |
| Generated project CI runs only what exists in the project | header comment of `project-bootstrap/templates/ci.yml.example` |
| A guarantee is bought against an observed failure | `references/decision-matrix.md` § 7, anti-pattern 16; `@CLAUDE.md` invariant 6 |
| Only `java`, `git`, `curl` | `@CLAUDE.md` § Dependencies |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/claude-code-architect-designer/SKILL.md` | Axis 17 + its paragraph; Phase 3 item 7 (**CI**); Phase 3.5 keeps the CI item and leaves `## CI coverage` pending; Phase 4 **step 9** (CI coverage), old 9–10 renumbered 10–11, step 11 reruns the new test after the jar build; step 9 never delegated; Phase 5 CI line; `## Contract` Reads/Writes/Delegates |
| `.claude/skills/claude-code-architect-designer/references/ci-coverage.md` | **New** — the pipelines, form → existing check → when a new one is needed → where it goes, how to write and prove a new test |
| `.claude/skills/claude-code-architect-designer/references/decision-matrix.md` | Rubric criterion 7 names the CI answer; anti-pattern 21 |
| `.claude/skills/claude-code-architect-designer/templates/decision.md.example` | A **CI** line per option; `## CI coverage` section; HOW TO USE note |
| `docs/pt-br/06-claude-code-architect-designer.md`, `docs/en/06-claude-code-architect-designer.md` | Axis 17, item 7, rubric criterion 7, the CI step, report line, contract, references, sequence diagram. These pages number Phase 4 one step behind `SKILL.md` (no Forms 7/8 rules step), so the CI step is **step 8** there and **step 9** in the skill — a pre-existing drift, not widened here |

No `@CLAUDE.md` routing row: the existing "Creating a skill, agent, norm…" and "Creating a hook…"
rows already route to this skill, and CI coverage is now part of what it does. No
`extensions.json` change: `skill_classes.meta.write_allow` already covers `.claude/.ci/**` and
`.github/**`.

## CI coverage

Existing check covers it; nothing new written. `validate · design › frontmatter schema` proves
the edited `SKILL.md` keeps its frontmatter, its class (`meta`) and the class's required sections
(`## Procedure`, `## Out of scope`) — run locally on this tree: exit 0. `claude plugin validate
.claude/skills`: passed. Nothing else in the change is testable in CI: it is procedure prose and
a reference page, whose claims about job and step names are owned by the workflows themselves —
`ci-coverage.md` says so and defers to them. A drift check over those names (a step comparing the
page to the YAML) was considered and not written: it would be a guarantee against a failure not
yet observed, the same reasoning that rejected option 4.

Goes to the generated project: **no** — `claude-code-architect-designer` is in
`export.skills.exclude`; what it later writes into `ci.yml.example` travels with that template.
