# 0130 · The executor runs in three chained block groups, and a spec is implemented only in a clean session

- **Date:** 2026-10-07
- **Scenario:** "Issue #109, triaged at 2cfe145 — Uma run do executor, feita depois da opção 1 da 0117, passa dos dois limites de § Reopening option 2: USD 9,88 contra ~USD 5 e pico de contexto de 427.889 contra ~300k. A opção 2, que divide o java-spring-boot-developer por grupo de blocos, não está implementada."
- **Decision:** option 1 — `.claude/skills/new-feature/SKILL.md` § End of flow (a design run ends at § Approval; § Implement, entered only from input row 3, chains three executor groups) and `.claude/agents/java-spring-boot-developer.md` § Groups. No new piece
- **State:** approved by Lucas Fernandes, on 2026-10-08
- **Goes to the generated project:** yes — `new-feature` and `java-spring-boot-developer` are both in `export.include`; nothing to add to the manifest

## Reproduced on disk

From `issue-verifier`'s tables for `/triage-issue 109` (layer static, `2cfe145`, run twice —
the second after the reporter attached the audit reports). Confirmed rows only. The issue's
proposed fix — the split and four "guards" — was judged by the verifier and is not an input
here: this record designs from 0117 § Option 2 and its § Reopening option 2.

| # | Fact | Evidence |
|---|---|---|
| 1 | 0117 deferred option 2, not rejected, with the trigger "an executor run above ~USD 5, or a peak context above ~300k" after option 1 | `0117` options table, § Reopening option 2 |
| 2 | Option 1 is present from v0.14.8; the reporter's project is v0.14.10 or later (single hand-back, trail continues into `git-publish` — 0119 behaviour) | `git tag --contains 89a7a9c`; the attached report |
| 3 | Run `2026-10-07T09-43-52--new-feature`: executor USD 9.88, 531,362 billable tokens, peak context 427,889, 28m58s, `claude-sonnet-5` | attached report + its `nodes.jsonl` row; the aggregate closes (919,663 billable), cost reproducible at `audit-pricing.json` prices |
| 4 | Run `2026-10-07T21-10-55--new-feature`: executor USD 5.21, peak 267,557 | `nodes.jsonl` row only — marginal, not needed for the trigger |
| 5 | 0117 precondition 3 (the audit no longer closes at a hand-back) is met | `audit.harness_prompts` in `.claude/schemas/extensions.json:154`; 0119 |
| 6 | Option 2 is not implemented: no group input in the agent; § Executor offer delegates once | `java-spring-boot-developer.md`; `new-feature/SKILL.md:736` |
| 7 | The agent's § Resume treats a complete file on disk as done | `java-spring-boot-developer.md:225-238` |
| 8 | Agent territory is keyed on `agent_type`, not on the call count | `ArchHook.java:6025-6029`; `AgentTerritoryTest` |

Unproven and left out: the run 2 totals "USD 5.80" and "20.8M cache read" (no report behind
them); the exact size of the exported agent body.

### 0117's three preconditions, settled before proposing

1. **The `tests` Stop gate between two groups.** Read at `ArchHook.java:187-300`: the gate
   defers while any marker of an `executor: true` agent of this session exists, one marker per
   `agent_id`, written at `SubagentStart`, deleted at `SubagentStop`. A group's `SubagentStop`
   deletes only its own marker. If the main thread launches the next group in the turn that
   receives the previous group's hand-back, that group's `SubagentStart` writes its marker
   before the turn's `Stop`, and the gate stays deferred. It runs only if a turn ends between
   groups with no writer running — over code that compiles, since every block compiles before
   it closes: noise, not damage. Hence the interview's "chain directly".
2. **A/B with and without the split.** "With" cannot be measured before the split exists. The
   "without" baseline is facts 3 and 4. The user chose to write it and measure the next
   implement run, with a revert criterion — § Verification.
3. **The audit and several hand-backs.** Met by 0119 (fact 5): each group's
   `<agent-message>` matches `audit.harness_prompts` and leaves the run open.

## Interview

Axes 5, 7, 8 and 16 carry over from 0117 unchanged: procedure text in two existing pieces, a
cost discipline (not build/security/compliance), destination both, no hook mode involved.

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Facts 3–4: an executor run after option 1 above both 0117 thresholds | create nothing |
| Group granularity (user) | **Three groups** — [1–2] · [H, 3, S, M, J] · [4]. Estimated −47% executor cache read on run 1 (linear model calibrated on it: 195 calls, 31k → 428k, ≈45M measured 44.7M) | two groups (−32%, one boundary); one per block (−63% gross, six boundaries, rereads of earlier code eat part of it, no precedent) |
| Hand-off (user) | **Earlier groups' Blocks lines and findings** go into the next delegation, ≈USD 0.03 per run | disk only — a decision taken while fixing a block and not in the spec never reaches the tests group |
| Between groups (user) | **Chain directly**, no question | confirm per group — the turn ends with no writer, `tests` runs over the partial tree at each boundary |
| A/B (user) | **Write, then measure** the next implement run against facts 3–4, with a revert criterion | prototype by hand first (one extra implement run); write without a criterion |
| Clean session (user) | **A design run ends at approval.** `/clear` cannot be run by a skill, a tool or a hook, so the run commits the spec and prints `/clear` + `/new-feature UC-NNN-<slug>`. Input row 3 becomes the only door to implementation | two doors, with the main thread's implement tail at the design's 250–370k Opus context |
| 17 — CI | `validate › schema` covers both bodies; `TestsDeferTest` has no case for a second writer starting after the first stopped | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Three chained groups + hand-off + design run ends at approval — `new-feature/SKILL.md` § End of flow, `java-spring-boot-developer.md` | 8 | **Approved** |
| 2 | Three chained groups only, both implement doors kept | 7 | Not chosen — the implement tail rides the design's 250–370k Opus context |
| 3 | Design run ends at approval only, executor unchanged | 3 | Rejected — does not address the executor, which is the symptom |
| 4 | Create nothing | 2 | Rejected — the trigger 0117 set is met |

### Option 1 — three chained groups, and implementation only in a clean session (score 8)

**Motivator:** axis 1 — the trigger 0117 set is met — and the interview's granularity,
hand-off, chaining and clean-session answers.

**Mechanism.**

*`new-feature/SKILL.md`*
- § Approval: **Approve** → `status: approved` → `git-publish` with what *Not now* stages today
  (`docs/use-cases/UC-NNN-<slug>/` and `BACKLOG.md`) → the final report ends with the two lines
  `/clear` and `/new-feature UC-NNN-<slug>`. The *Implement now / Not now* question is gone.
- § Executor offer becomes § Implement — entered only from input row 3, after the existing
  pre-flight. Three delegations to `java-spring-boot-developer`, each with the spec path and
  its group:

  | Group | Blocks | Checklist steps |
  |---|---|---|
  | `domain` | 1, 2 | 1–12 |
  | `adapters` | H, 3, S, M, J — those the spec carries | 13–15 (blocks H, S, M and J have no step of their own in the fixed 19) |
  | `tests` | 4 | 16–19 — closes `status:` |

  The next group is launched **in the turn that receives the previous hand-back, with no
  question** (precondition 1). Its prompt carries the earlier groups' `Blocks` lines and
  findings. A group that fails stops the chain: report, no git. The chain starts at the
  first group with an open checklist step, so a rerun after a failure does not restart a
  finished group. That needs each group to tick its own steps when it ends green — until
  this record the executor ticked the whole checklist at Block 4's close. The guard already
  admits checklist toggles on an approved spec, and the `status:` line stays the last write. The final report merges every group's lines and the four mandated findings.
- § End of flow's entry table, input row 3's text and § Operational note follow (each group
  runs in the background under the same `caffeinate` advice).

*`java-spring-boot-developer.md`*
- Input: a `group` named in the delegation; it runs only that group's blocks. Earlier groups'
  lines arrive as context — the spec wins where they disagree, and a disagreement is a finding.
- Its one report is the summary of the last block of its group; only the `tests` group closes
  `status:` and reports totals and coverage.
- § Integration and § Invocation describe row 3 as the caller.

**Expected:** −47% executor cache read on a run shaped like fact 3 (≈ −USD 4.2 of 9.88),
less two cold starts (~USD 0.3). The main thread's implement turns run at ~40–60k instead of
250–370k Opus (≈ −USD 0.4–0.8 when the user implemented in the design run). Estimates —
§ Verification decides.

**Pros:** the largest lever measured, on the costliest scenario. One door to implementation
instead of two — 0067 already made row 3 complete. The `tests` gate needs no change.

**Cons:**
- A decision that is neither on disk nor in a group's report is still lost — the risk a
  resumed run already accepts, narrowed by the hand-off.
- Two more subagent starts per run: 8 more hook processes (`tests agent-start`,
  `context subagent`, `audit agent`, `tests agent-end`).
- Chaining in the same turn is persuasion. Its failure is the `tests` gate running over
  compiling code, not a broken run.
- Three commands where there was one (approve, `/clear`, `/new-feature UC-NNN`).

**Points cut in the rubric:** 5 (orchestration text: group table, hand-off, merge), 6 (no
multi-call executor precedent), rounded — 7/9 → 8.

**CI:** `validate › schema` covers both bodies (class sections, `export`). New case in
`.claude/.ci/TestsDeferTest.java` (`validate · hooks-cross-platform`): writer A starts, A
stops, writer B starts → deferred; B stops → Maven called — the gate between groups,
precondition 1. What the orchestrator types is not testable; § Verification measures it.

### Option 2 — three chained groups only (score 7)

Same split, both doors kept. The main thread's three hand-backs, three launches and
`git-publish` run at the design's 250–370k Opus context when the user implements in the
same run. Cut: 5 and 6 as option 1, plus 3 (the implement tail rides the design context).

### Option 3 — design run ends at approval only (score 3)

Saves the main-thread tail (≈ USD 0.4–0.8) and leaves the executor at USD 9.88 — the
symptom is the executor. Cut: 1 (does not address the trigger), 4, 7.

### Option 4 — create nothing (score 2)

0117 named the trigger, and fact 3 meets it on both thresholds.

## Verification

The next implement run with the split, on a spec with the same block set as fact 3 (or
close), is the "with" half of 0117's precondition 2. Compare the sum of the three groups'
`cost_usd` and `peak_context` in `nodes.jsonl` with fact 3. **Revert to one delegation** if the
executor's total does not fall by at least 20%, or if the `tests` group's red-rerun cycles grow
enough to cancel it — a sign the hand-off is too thin.

## References

| Claim | Source |
|---|---|
| Option 2's shape, cons and preconditions | `@.claude/decisions/0117-new-feature-cost-per-entry-scenario.md` § Option 2, § Reopening option 2 |
| `tests` defers per `agent_id` while an `executor: true` marker exists | `.claude/hooks/ArchHook.java:187-300`; `@.claude/decisions/0116-tests-defers-while-a-writer-subagent-runs.md` |
| Each hand-back leaves the audit run open; one report per agent | `@.claude/decisions/0119-audit-ignores-subagent-handback-executor-single-report.md` |
| Row 3 is a complete implement door | `@.claude/decisions/0067-implement-entry-row.md` |
| A subagent does not inherit the conversation | `@claude-help.md` § Subagents; decision matrix § 5 |
| Built-in commands are not invocable by the model | Skill tool contract — `/clear` is not a skill |
| Cost is turns × context | `0117` § Inside the executor |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/new-feature/SKILL.md` | § End of flow rewritten: design and implementation never share a session; § Approval → `git-publish` of the spec and the two lines `/clear` + `/new-feature UC-NNN-<slug>`; § Executor offer replaced by § Implement (pre-flight, the three-group table, where the chain starts, the hand-off, launch in the same turn, failure stops the chain). Input row 3, the pipeline list (step 8), § Contract's pre-flight mention, § Final report, § Operational note and the commons-logging reference follow |
| `.claude/agents/java-spring-boot-developer.md` | Input: the group and the earlier groups' lines (no group → all three in one run). § Integration names row 3 as the caller, three times. New § Groups under § Procedure: the table, run only the named group, earlier lines are context and the spec wins, tick at the group's green end, report shape. Block 4's checklist tick and final-summary `Blocks` line, and § Invocation |
| `.claude/.ci/TestsDeferTest.java` | Two cases: group 1 stopped and group 2 started → still deferred; three groups all stopped → Maven called |
| `.github/workflows/validate.yml` | Comment on the `TestsDeferTest` step naming 0130 |
| `docs/pt-br/07-ci-validate.md` · `docs/en/07-ci-validate.md` | The `TestsDeferTest` row names the chained-groups cases and 0130 |
| `docs/pt-br/03-new-feature.md` · `docs/en/03-new-feature.md` | Sequence diagram: approval commits the spec and prints the two commands; the implement run is a loop over the three groups. Pre-flight heading; § Cost discipline |
| `README.md` | The `/new-feature` section shows the two flows, create the spec and implement it, each with its diagram; the intro's demo paragraph names the second flow. Refused by `guard sweep` inside the designer's phase (outside `skill_classes.meta`), written after it closed |
| `docs/pt-br/03-new-feature.md` · `docs/en/03-new-feature.md` (second pass) | Title; a two-flow table in § What it does; the sequence diagram split into Flow 1 and Flow 2 |
| `docs/*/00-*.md` · `docs/*/01-*.md` · `docs/*/08-audit-usage.md` · `docs/*/09-*.md` · `docs/*/README.md` | The `/new-feature` command row names both flows; `git-publish`'s two calls; who delegates to the executor and when; the audit tail of the spec flow has no pre-flight; SDD line; doc index |
| `.claude/skills/git-publish/SKILL.md` | § Integration: the two `/new-feature` calls, one per flow |
| `.claude/decisions/0117-new-feature-cost-per-entry-scenario.md` | Note in § Reopening option 2 pointing here |
| `.claude/decisions/0067-implement-entry-row.md` | Note: row 3 is now the only door |

Goes to the generated project: **yes** — `new-feature` and `java-spring-boot-developer` are in
`export.include` and travel whole; `/arch-adopt` delivers them to an existing project. Nothing to
add to the manifest. `TestsDeferTest` stays here: nothing under `.claude/.ci/` travels.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · hooks-cross-platform › tests defers while a writer subagent of the session runs, and only then` | The `tests` gate stays deferred across a handover between two chained executor groups (precondition 1), and runs once every group stopped | Green on the tree, 9/9. Red with `testsAgentStart` writing a marker only when the session's state directory did not exist yet (one writer per session): `group 1 stopped, group 2 started — still deferred` fails by name, the other 8 pass. Source and jar restored from git; `build --verify` green |
| `validate › schema` (step `frontmatter schema`) | Both bodies: frontmatter, class sections, `export` entries | Green |

What the orchestrator types — the chaining in the same turn, the hand-off — is not testable in
CI. § Verification measures it on the next implement run.
