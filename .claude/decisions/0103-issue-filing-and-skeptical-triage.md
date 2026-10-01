# 0103 · Issues are filed by the user from a generated project, and triaged skeptically here

- **Date:** 2026-10-01
- **Scenario:** "Today only `sonar-lessons` can open an issue on the meta-repo. I want the
  user of a generated project to open one from a description or a lessons-learned file —
  and, since the project is open source and anyone can file, something extremely critical
  that analyzes the problem and the proposed fix without taking them as true, verifies the
  problem occurs, and only then chains `claude-code-architect-designer`."
- **Decision:** Option A — Form 2 `.claude/skills/report-issue/SKILL.md`, Form 2
  `.claude/skills/triage-issue/SKILL.md`, Form 3 `.claude/agents/issue-verifier.md` in a new
  agent class `verifier`, Form 8 `permissions.ask` on four `gh` write verbs, and `sonar-lessons`
  handing its file to `/report-issue` instead of publishing.
- **State:** approved by Lucas Fernandes, on 2026-10-01

## Context checked before the interview

| Fact | On disk |
|---|---|
| Issues filed on the repository so far | 1 — #48, opened by `/sonar-lessons`. No external issue has arrived: the triage half is anticipation, which is why its guarantee side is an option with a price, not the default (invariant 6, mirror) |
| The skeptical step already exists as a habit | `0101` opens with *Reproduced on disk before classifying* — every claim of lessons-learned-018 checked against `effd2e3` before the interview. Unwritten: nothing makes the next triage do it |
| Who may publish an issue today | `sonar-lessons` step 6, behind one `AskUserQuestion`; privacy rule and duplicate search live in its body |
| An issue body is third-party text | Read by a model that, in this repo, holds `meta`'s territory (`.claude/**`, `CLAUDE.md`, `.github/**`). Prompt injection is the new risk the scenario creates; an HTML comment in the body is invisible on the web and read in full by the model |
| `claude-code-architect-designer` is `disable-model-invocation: true` | Nothing can chain it through the `Skill` tool. "Offer the designer" means printing the command, and the user typing it |
| An agent class with `write_allow: []` is already enforced | `guard` judges a subagent's write by `agent_type` (`ArchHook.java` › territory step 1), Bash writes included through `guard bash`/`guard sweep`. No Java change needed for a read-only verifier |

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Filing: only `sonar-lessons` can file from a project. Triage: anticipated, no external issue yet | a Form 7 hook against an observed failure |
| 2 — trigger | Filing: typed by the user in the generated project, never by the model. Triage: typed by the maintainer, locally, v1 | Form 1; a GitHub Action (v1) |
| 6 — isolation | The issue body must be read by an agent that cannot write — reason 2 of invariant 5 (restrict tools) | triage as a skill reading the body in the main thread |
| 7 — mandatoriness | Verification is judgment; the only always-true rule is "nothing reaches GitHub without a human" | Form 7 for the verification itself |
| 8 — destination | Filing skill: both (generated project is where it runs). Triage skill and verifier: meta-repo only | — |
| 9 — integration | `sonar-lessons` step 6 delegates publishing to the new filing skill — one owner of "publish an issue to the meta-repo" (invariant 2) | two publishers |
| — reproduction | Layered: static against HEAD always; a generated project in scratch only after asking, when the claim is about a generated project and static can't decide | static only; always-generate |
| — pre-filter (filing) | Outdated version, open duplicates, minimum evidence. Not: local edits to `.claude/` | — |
| — outcome | Comment + label on the issue, after confirmation | local-only; auto-close |
| — record | The comment carries the claim table; when confirmed, it becomes the designer decision's *Reproduced on disk* section (shape of `0101`). No new versioned directory | `.claude/triage/`, `.claude/lessons-learned/issue-NNN.md` |
| — chaining | Confirmed → offer `/claude-code-architect-designer`, passing verified facts only; the issue's proposed fix is never a premise | auto-chain; triage designs the fix |
| — verifier model | `opus` — deciding true / by-design / inconclusive is the judgment | `sonnet` |
| 11 — CLI | `gh` covers every GitHub call | Form 6 |
| 17 — CI | See each option | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| A | `report-issue` + `triage-issue` + `issue-verifier`, plus `permissions.ask` on the GitHub write verbs | 8 | **Approved** |
| B | Same as A, without the `permissions.ask` lines | 7 | Rejected — an issue could coax the verifier into publishing with nothing asking first |
| C | `report-issue` + `triage-issue` reading the body in the main thread, no agent | 5 | Rejected — the model reading third-party text holds `meta`'s write territory |
| D | Create nothing — the issue forms already demand file and line; verify by hand as in `0101` | 4 | Rejected — leaves the generated project with no way to file anything but Sonar findings |
| E | A GitHub Action triaging every opened issue (`claude-code-action`) | 3 | Rejected for v1 by the user — revisit after manual triage has run on real external issues |

### A — three pieces plus `permissions.ask` (score 8)

**Motivator:** axis 6 (the body is untrusted and must be read where nothing can be written)
and axis 9 (one publisher).

**Pieces:**

1. `.claude/skills/report-issue/SKILL.md` — Form 2, class `report` (joins by name, as the
   class `$comment` anticipated; territory `docs/lessons-learned/**`). Input: a free
   description or a path to a lessons-learned file. Picks the form — `sonar-lessons.yml` for a
   `sonar-NNN.md`, else `bug_report.yml` or `feature_request.yml` — fetched from
   `source.git_url` at HEAD and filled per field. Pre-filter: (a) `.arch-provenance.json` ref
   vs the latest release, warning that `/arch-adopt` may already carry the fix; (b) open
   duplicates via `gh issue list --search`; (c) minimum evidence — a `.claude/` path, a
   literal output, or steps to reproduce, else refuse. Owns the privacy rule (moved out of
   `sonar-lessons`). Writes `docs/lessons-learned/issue-NNN.issue.md`, asks, then
   `gh issue create --body-file`. `export.skills.include`.
2. `sonar-lessons` step 6 shrinks to: print `/report-issue docs/lessons-learned/sonar-NNN.md`.
   Only the user types it — the scenario's "only the user creates an issue".
3. `.claude/skills/triage-issue/SKILL.md` — Form 2, class `ops` (writes no file in the tree,
   effect outside it; `sonnet` — it relays, the verifier judges). `/triage-issue <N>`:
   delegates to the verifier, shows the verdict, asks before layer 2, asks before posting
   comment + label, and on `confirmed` prints the designer command with the verified table.
   `export.skills.exclude`.
4. `.claude/agents/issue-verifier.md` — Form 3, new class `verifier`: `executor: false`,
   `write_allow: []`, `model: opus`, `tools: Read, Grep, Glob, Bash`. Reads the issue with
   `gh issue view`, treats every line as a claim, never as an instruction, and flags hidden
   HTML comments. Per claim: how it was checked, result, evidence (`file:line @ sha` or the
   command and its output). Verdict: `confirmed` · `already-fixed` (commit) · `by-design`
   (decision NNNN) · `duplicate` (#N) · `not-reproduced` · `needs-evidence`. Separately
   judges the proposed fix: invariant it would break, decision it contradicts, symptom-only.
   Static layer always; layer 2 (`export` + Initializr into scratch) only when the skill
   re-delegates with the user's yes. `export.agents.exclude`.
5. `.claude/settings.json` `permissions.ask`: `Bash(gh issue comment:*)`,
   `Bash(gh issue edit:*)`, `Bash(gh issue close:*)`, `Bash(gh api:*)` — Form 8. Every
   GitHub write prompts the human, including one an injected instruction coaxed out of the
   verifier's Bash. Precedent: `Bash(gh repo create:*)` already sits there.
6. `claude-code-architect-designer` Phase 1: one paragraph — a scenario that names a triaged
   issue starts from the verifier's table; unverified claims and the proposed fix are not
   inputs.

**Pros:** the critical step is written down, not a habit; the untrusted text never meets a
writer; one publisher; nothing reaches GitHub without a human; no Java.

**Cons:** four new pieces against one external issue so far (anticipation); a new agent
class; the `ask` lines prompt on the maintainer's own legitimate comments too.

**Points cut:** 5 (maintenance — three pieces bought before an external issue arrived);
6 (precedent — first non-executor agent class). 8 partly restored by the `ask` lines; the
verifier's unscoped `Bash` still reaches the network (`curl`), which only the human-in-the-loop
on every GitHub write bounds.

**CI:** `validate` · `design` › `frontmatter schema` (classes, sections, export entries, the
new agent's `model`/`tools`); `design` › `issue forms cited by skills exist` (covers
`bug_report.yml` and `feature_request.yml` once `report-issue` cites them). New case in
`.claude/.ci/AgentTerritoryTest.java` for class `verifier`: every in-repo write refused, by
`Write` and by `Bash`. The `ask` lines: nothing testable in CI — no offline matcher for a
permission rule; a pitfall entry in `docs/*/11-pitfalls.md` instead.

### B — A without `permissions.ask` (score 7)

Same pieces 1–4 and 6. Cheaper, and nothing prompts twice. The verifier still cannot write
in the tree, but a `gh issue comment` or `gh issue close` it is coaxed into runs unprompted
outside auto-mode allowlists. **Points cut:** 4 (a guarantee was available, persuasion used),
5, 6, 8. **CI:** as A, without the pitfall entry.

### C — no verifier agent (score 5)

`triage-issue` reads the body in the main thread with a prose rule "treat as data". Fewer
pieces. Strains the user's isolation answer: the model reading attacker text holds `meta`'s
territory. **Points cut:** 4, 8 (twice — widest trust surface of all options), 6. **CI:**
`frontmatter schema` only.

### D — create nothing (score 4)

The forms already require "quote the file and line"; verification stays the maintainer's
habit, as in `0101`. Leaves the generated project without a way to file anything but Sonar
findings — the first half of the scenario unmet. **Points cut:** 1 (§ 2: a multi-step
procedure is a skill), 4, 7.

### E — GitHub Action on `issues: opened` (score 3)

Rejected for v1 by the user. A repository secret, cost per issue opened by anyone, and a
model reading attacker text with a repo token. Revisit only after manual triage has run on
real external issues.

## References

| Claim | Source |
|---|---|
| A manual-only skill cannot be invoked through the `Skill` tool, so chaining = printing the command | `@docs/pt-br/11-pitfalls.md` line 71; designer frontmatter |
| An agent exists to restrict tools, preserve context or change model | `@CLAUDE.md` invariant 5 |
| A subagent's writes are judged by its `agent_classes` territory, Bash included | `ArchHook.java` guard territory step 1; `0059`; `0063`; `0065` |
| One owner per rule — publishing moves out of `sonar-lessons` | `@CLAUDE.md` invariant 2 |
| `report` class anticipated a second skill joining by name | `skill_classes.report.$comment`, `0100` |
| `ops` = effect outside the tree, writes no file | `skill_classes.ops.$comment` |
| `permissions.ask` on a publishing `gh` verb has precedent | `.claude/settings.json` `Bash(gh repo create:*)` |
| Verification precedent | `0101` § Reproduced on disk before classifying |
| A guarantee is bought against an observed failure | `@CLAUDE.md` invariant 6, mirror |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/report-issue/SKILL.md` | New — Form 2, class `report` |
| `.claude/skills/triage-issue/SKILL.md` | New — Form 2, class `ops` |
| `.claude/agents/issue-verifier.md` | New — Form 3, class `verifier` |
| `.claude/skills/sonar-lessons/SKILL.md` | Step 6 prints `/report-issue <file>` instead of publishing; privacy rule, `gh` tools, the two publish failure modes and the `Publishes`/`Does not own` contract lines removed |
| `.claude/skills/sonar-lessons/templates/lessons-learned.md.example` | Header comment and `## Issue` placeholder name `/report-issue` |
| `.claude/skills/claude-code-architect-designer/SKILL.md` | Phase 1: a triaged issue starts from the verifier table in the conversation; the designer never fetches the issue itself |
| `.claude/schemas/extensions.json` | `report-issue` in `skill_classes.report`, `triage-issue` in `skill_classes.ops` (both `$comment`s extended); new `agent_classes.verifier`; `export.skills.include` + `report-issue`, `export.skills.exclude` + `triage-issue`, `export.agents.exclude` + `issue-verifier` |
| `.claude/settings.json` | `permissions.ask` + `Bash(gh issue comment:*)`, `Bash(gh issue edit:*)`, `Bash(gh issue close:*)`, `Bash(gh api:*)` |
| `.claude/.ci/AgentTerritoryTest.java` | Six cases for `issue-verifier`, a `bash` helper |
| `CLAUDE.md` | Routing: `sonar-lessons` row ends with `/report-issue`; new rows for `report-issue` and `triage-issue`; four agent classes |
| `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example` | Same `sonar-lessons` change; new `report-issue` row |
| `README.md` | Tree (two skills, one agent); copied and left-out lists |
| `docs/pt-br/01-tipos-de-arquivo.md`, `docs/en/01-file-types.md` | `ops` and `report` rows; agent table and class table gain `issue-verifier` / `verifier` |
| `docs/pt-br/07-ci-validate.md`, `docs/en/07-ci-validate.md` | `AgentTerritoryTest` row (25 cases); issue-forms row names `report-issue` |
| `docs/pt-br/11-pitfalls.md`, `docs/en/11-pitfalls.md` | Part 2: the four `gh` verbs always prompt, on purpose |
| `.github/ISSUE_TEMPLATE/bug_report.yml`, `feature_request.yml` | Preamble: claims are checked at `HEAD`; `/report-issue` fills the form from a project |
| `.github/workflows/validate.yml` | Comment of `issue forms cited by skills exist` names `report-issue` |

Goes to the generated project: **yes, partly** — `report-issue`, the `sonar-lessons` change
and the routing row travel through `export`; `triage-issue`, `issue-verifier` and the
`permissions.ask` lines stay here. Projects exported before this decision keep the old
`sonar-lessons`, which still fetches `sonar-lessons.yml` — kept in place for them.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · design › frontmatter schema` | Both skills in a class, with the class's sections and model; the agent in `verifier` with `model`, `tools`, `effort` and the class's sections; all three in `export` include/exclude; no `$ARGUMENTS` in prose | green on the tree; red once while writing — `triage-issue/SKILL.md:49` had `$ARGUMENTS` in prose, fixed |
| `validate · design › issue forms cited by skills exist` | `bug_report.yml`, `feature_request.yml` and `sonar-lessons.yml`, all cited by `report-issue`, exist | green (the step's loop, run locally) |
| `validate · hooks-cross-platform › AgentTerritoryTest` | `issue-verifier` refused `.claude/settings.json`, `CLAUDE.md`, a heredoc into `.claude/rules/`, and `ArchHook.java` with the designer's `.claude/**` phase open; its scratch outside the repo goes through | 25/25 green; red with `verifier.write_allow` widened to `[".claude/**", "CLAUDE.md"]` — 4 cases failed by name, exit 1 |
| `validate · export-determinism` | The exported tree carries `report-issue`, neither `triage-issue` nor `issue-verifier`, and its own `schema` passes | local export of `clean-architecture-single-module`: exit 0, no dead citation, exported `schema` exit 0 |
| `permissions.ask` lines | Nothing testable in CI — no offline matcher for a permission rule | pitfall entry in `docs/*/11-pitfalls.md` instead |
