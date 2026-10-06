# 0126 · A `Skill` call made by a classed agent is judged by the agent's class, not the caller's phase

- **Date:** 2026-10-06
- **Scenario:** Issue #99, triaged at `803af10`: `project-initializer`'s `Skill` calls to
  `build`-class skills (`transport-security-setup`, `sonarqube-setup`) exit 2, because
  `guardCall`/`guardRefuseBuildCall` judge them against the main session's open `init-project`
  phase (class `orchestrator`) and never read `agent_type`.
- **Decision:** Option A. A `Skill` call carrying the `agent_type` of a classed agent returns at once from `guardCall` in `.claude/hooks/ArchHook.java`: no refusal, and the phase stays unchanged.
- **State:** approved by the maintainer, on 2026-10-06

## Reproduced on disk

These rows are the confirmed claims from the `issue-verifier` table, checked at `803af10`. No
commit since then touches any cited file.

| # | Fact | Evidence |
|---|---|---|
| 1 | A `/init-project` prompt opens the phase at `UserPromptSubmit` | `ArchHook.java:5800-5801`; reproduced with `guard prompt` |
| 2 | `orchestrator` has `design_phase: true` and lists `new-feature` and `init-project` | `extensions.json:194-200` |
| 3 | `transport-security-setup` and `sonarqube-setup` are class `build`, and that class has `blocked_during_design: true` | `extensions.json:207,211` |
| 4 | The phase file is keyed on `session_id` only | `ArchHook.java:5779,5790-5794` |
| 6 | `guardCall` and `guardRefuseBuildCall` never read `agent_type` | `ArchHook.java:5843-5869,5877-5893` |
| 7 | The write path judges a classed agent by its own class first, and never reads the phase for it | `ArchHook.java:5971-5985` |
| 8 | `project-initializer` is class `driver`, with `executor: true` and `write_allow: ["**"]` | `extensions.json:278-284` |
| 9 | `guard call` exits 2 for both chained skills when the payload carries `agent_type: project-initializer` | reproduced |
| 10 | `project-bootstrap` chains both skills through `Skill` (steps 7.5 and 8.4). `project-initializer` is the only agent that holds `Skill` | `project-bootstrap/SKILL.md:309,331`; `agents/*.md` `tools:` lines |

The verifier found one more fact the issue did not claim. Admitting the call without other
changes is not enough: `guardCall:5865` (`guardOpen`) would then replace the main thread's
`init-project` phase with the callee's name. The main thread would keep the callee's
territory for the rest of the turn.

Not inputs to this record: claim 11 (refuted — the defect predates 0123, since 0059 kept the
phase open across `Agent`), claim 12 (unproven), and the issue's proposed fix.

## Interview

The axes come from the triaged table. Only axes that eliminated a form are listed.

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Observed in a real `/init-project` run and reproduced with the hook mode: exit 2 on both chained skills. The agent then ran both procedures by hand | create nothing (it is not anticipation) |
| 2, 14 — trigger | `PreToolUse` `Skill`, already registered to `guard call` (`settings.json:20-25`) | 1, 2, 4, 5, 6 |
| 7, 15 — mandatoriness | The refusal is a guarantee that already exists. The defect is in which caller it judges, not in its absence | persuasion forms as the fix |
| 16 — existing mode | `guard call` already runs the check. The change goes in its method, with no new mode and no new registration | 7a, 7b (a new registration), and a new 7c mode |
| 8 — destination | Both: `ArchHook.java` and the jar travel through `export`. In a generated project no classed agent holds `Skill` today, so behavior there does not change | — |
| 9 — integration | `guardViolations` already gives priority to `agent_type`, and `guardCall` must not contradict it. Decision 0058 requires that `/new-feature` stays unable to reach `build` skills | option B (see below) |
| 10 — cost of an error | A false refusal blocks project generation, as observed. A false admission would let a design run reach a `build` skill | — |
| 17 — CI | `validate` · `hooks-cross-platform` › `SkillTerritoryTest` already exercises `guard call` | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| A | `ArchHook.java` `guardCall`: a `Skill` call whose `agent_type` resolves to any `agent_classes` entry returns at once. No refusal, and the phase stays unchanged | 9 | **Approved** |
| A′ | Same, but only for agents in an `executor: true` class (the issue's shape) | 7 | Rejected — a non-executor agent would still replace the caller's phase, and the refusal protects nothing the write territory does not already cover |
| C | No hook change: `project-bootstrap` 7.5 and 8.4 tell `project-initializer` to follow both skills by `Read`, as it already does for `project-bootstrap` | 4 | Rejected — persuasion where the guarantee already exists, and the phase-replacement defect stays latent |
| B | Remove `design_phase` from `orchestrator` | 3 | Rejected — contradicts decision 0058: `/new-feature` would reach `build` skills again |

### Option A — classed agent skips `guardCall` entirely (score 9)

**Motivator:** axes 9 and 16. Since decision 0059, the boundary for a classed agent is its
`write_allow`, keyed on `agent_type`. The caller's phase does not apply to that agent.
`guardCall` is the last place where the phase still judges the agent.

**Pros:**
- The fix is one early return in an existing method. It needs no new data and no new mode.
- It aligns `call` with `write`. One sentence covers both: a classed agent's actions never
  read or change the caller's phase.
- The refusal adds nothing for a classed agent. Whatever the callee writes from inside the
  agent carries the same `agent_type`, so the agent's `write_allow` judges it. Example:
  `java-spring-boot-developer` cannot write `docker-compose.yml` through `docker-architect`
  for the same reason it cannot write that file directly.
- It also fixes the phase-replacement defect the verifier found. A subagent's `Skill` call
  no longer overwrites the main thread's phase.
- An unclassed agent keeps the current fallback (refusal, then phase change). This matches the
  accepted gap already documented in the `guard` comment.

**Cons:** A future classed agent that holds `Skill` could reach `build` skills during
`/new-feature`. Its own `write_allow` still bounds what those skills can write, and
`AgentTerritoryTest` tests each agent's `write_allow` separately.

**Points cut in the rubric:** criterion 6 (precedent) — partial. The write path is the
precedent, but no call-time rule judged by agent existed before.

**CI:** extend `.claude/.ci/SkillTerritoryTest.java` (job `validate` ·
`hooks-cross-platform`, already registered). New cases, with a `/init-project` phase open:
- A `Skill(transport-security-setup)` call with `agent_type: project-initializer` exits 0.
- After that call, a main-thread write to `CLAUDE.md` still exits 2. This proves the phase was
  not replaced: the territory of `transport-security-setup` would admit `CLAUDE.md`.
- The same call without `agent_type` still exits 2, so the refusal stays for the main thread.
- The same call with an unclassed `agent_type` still exits 2, so the fallback is unchanged.

### Option A′ — only `executor: true` classes (score 7)

The fix has the same shape as A, gated on the class's `executor` flag. This option loses on
criterion 1 and criterion 5:
- A non-executor class (`verifier`) writes nothing, so the refusal protects nothing for it.
- A `Skill` call from a non-executor agent would still replace the main thread's phase. The
  verifier's extra finding stays open for every class that is not an executor.

Its only gain is a narrower surface, and option A's write territory already provides it.
**CI:** same cases as A, plus one with `agent_type: issue-verifier`.

### Option C — follow by `Read` instead of `Skill` (score 4)

This is prose in `project-bootstrap`: under `project-initializer`, read
`transport-security-setup` and `sonarqube-setup` and follow them, as the agent already does for
`project-bootstrap` itself.
- The run would lose each skill's own `allowed-tools` and `model`.
- Two callers would invoke the same skill in two different ways.
- The phase-replacement defect would stay latent for any future `Skill` call by an agent.
- Persuasion is used where a guarantee was already in place (criterion 4). The workaround
  from the reported run is this option, improvised.

**CI:** nothing testable — a model-followed instruction.

### Option B — remove `design_phase` from `orchestrator` (score 3, capped)

This option contradicts decision 0058 (`0058…md:114-117`): `/new-feature` is also class
`orchestrator`, with territory `docs/**`. Without `design_phase`, `/new-feature` could reach
`docker-architect` mid-design. That reach is the failure (lessons-learned-012 §§ 12-13) that
the refusal was built to stop. Only `init-project`'s override has an empty territory, so the
issue's premise holds for that skill alone. **CI:** `SkillTerritoryTest`'s existing
`build-class Skill call refused mid-design` case would fail, correctly.

## References

| Claim | Source |
|---|---|
| A subagent's `PreToolUse` payload carries `session_id` and `agent_type` | `.claude/decisions/0116-*.md:123`, citing the runtime hooks documentation |
| A classed agent's territory holds no matter which phase the caller left open | `@CLAUDE.md` invariant 9 (agents paragraph); `.claude/decisions/0059-agent-classes-territory-schema.md` |
| The refusal exists so that `/new-feature` stays docs-only | `.claude/decisions/0058-skill-classes-territory-schema.md:114-117` |
| A same-class chain joins the phase, while a call of another class replaces it | `ArchHook.java:5850-5866`; `.claude/decisions/0092-*.md` |
| An unclassed agent falls back to the phase, as an accepted gap | `ArchHook.java:5771-5772` |
| `guard call` behavior as documented to readers | `docs/pt-br/08-audit-usage.md:324`, `docs/en/08-audit-usage.md:322` |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | Early return at the top of `guardCall` when `agent_type` resolves to an `agent_classes` entry. The method gets a Javadoc with the form, the reason and the rejected alternative. The `guard` header comment now names the `call` behavior |
| `.claude/hooks/ArchHook.jar` | Rebuilt under JDK 21; `build --verify` is green |
| `.claude/.ci/SkillTerritoryTest.java` | Five cases under `/init-project` (see below). The case count goes from 45 to 50 |
| `docs/pt-br/08-audit-usage.md`, `docs/en/08-audit-usage.md` | The `guard call` row names the classed-agent skip |
| `docs/pt-br/07-ci-validate.md`, `docs/en/07-ci-validate.md` | The `SkillTerritoryTest` row: 50 cases, the new behavior, and the stale "39" corrected |

Goes to the generated project: **yes, through `export`, with nothing to wire.** `ArchHook.java`
and the jar are copied whole. Behavior there does not change today, because no exported agent
holds `Skill`.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · hooks-cross-platform › guard keeps each skill inside its class's territory` (`SkillTerritoryTest`) | With `/init-project` open: a `Skill(transport-security-setup)` call with `agent_type: project-initializer` exits 0. A later main-thread write to `CLAUDE.md` exits 2, so the phase was not replaced. A main-thread `Skill(sonarqube-setup)` exits 2. The same call from an unclassed `agent_type` (`general-purpose`) exits 2 | Green on the tree, 50/50. **Red 1:** with the early return removed, the case `classed agent's build-class Skill call admitted mid-orchestrator` fails with the original #99 message. **Red 2:** with only the refusal skipped, so the callee's phase still opens, three cases fail by name, including `the main thread keeps init-project's empty territory — not the callee's` |
| `validate · hooks-cross-platform › committed ArchHook.jar is what the source compiles to` | The jar matches the source | `build --verify` green after the restore |

No step was added to the workflow: the test step was already registered.
