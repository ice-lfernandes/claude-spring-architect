# 0034 · Add `git-publish` skill — offer git init/commit and gh create+push after generation or a feature

- **Date:** 2026-09-11
- **Scenario:** at the end of a successful `/init-project` run, and at the end of a
  successful `java-spring-boot-developer` run, present the user the option to create a
  git repo, commit, and push the project.
- **Decision:** Form 1 (auto-invocable skill, chained by name) —
  `.claude/skills/git-publish/SKILL.md`.
- **State:** approved by Lucas Fernandes, on 2026-09-11

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| Where the git action runs, given future orchestrators will also need it | User asked for the recommendation given reuse by future skills/orchestrators → single shared piece, not inline duplication | Inline duplication in each caller (invariant 2) |
| Scope of the git action | init + commit + push via `gh` CLI (create the remote too) | An MCP GitHub server — `gh` already covers it, § 2.1 |
| Confirmation before push | Two separate `AskUserQuestion` gates: one for local init/commit, one for remote create+push | A single combined confirmation |
| Destination | Must also exist inside the generated project — it's the project that gets created and receives future commits | Meta-repo-only tooling |
| Which form, once chaining was required | `disable-model-invocation: true` (Form 2) blocks the model from invoking it via the `Skill` tool at all, even explicitly by name — same restriction `java-spring-boot-developer.md` documents for why it can't re-invoke `java-patterns`. `project-initializer` (agent) and `/new-feature` (skill) both need to chain into this by name | Form 2 |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `git-publish` — Form 1, no `disable-model-invocation`, protected by two `AskUserQuestion` gates in the body instead of the flag | 9 | **Approved** |
| 2 | Duplicate the git procedure inline in `project-initializer.md` and in `java-spring-boot-developer.md`/`new-feature/SKILL.md` | 5 | Rejected — breaks invariant 2 the moment a third future caller needs the same procedure, which the user already named as the reason to ask |
| 3 | Form 3 (subagent) for the git procedure | ≤4 | Rejected — fails all three counter-test questions in `references/decision-matrix.md` § 5: the confirmation dialogue is the heart of the task, the context fits in one file, the output is short |

### Option 1 — `git-publish`, Form 1 (score 9)

**Motivator:** two different pieces (`project-initializer` agent, `/new-feature` skill)
need to invoke this by name after they finish successfully — the D17 precedent for why
the five `/new-feature` pipeline skills also stay without `disable-model-invocation`.

**Pros:** single owner reused by every current and future caller; no new tool granted to
`java-spring-boot-developer` (its restricted tool set, the reason it's an agent at all,
stays intact — `/new-feature` invokes `git-publish` after the agent returns, not the
agent itself); `gh` CLI is already the cheaper alternative to an MCP server (§ 2.1); two
independent confirmation gates match the repo's own git-safety default (never push
without asking) and the invariant-11-style discipline of never acting on a destructive/
visible operation silently.

**Cons:** being Form 1 (no `disable-model-invocation`) means the skill *could* fire from
a description match outside the two intended callers — mitigated, not eliminated, by the
two gates: no git side effect runs without an explicit yes regardless of what triggered
the invocation. Same trade-off the five pipeline skills already accept.

**Points cut in the rubric:** none against § 8's criteria — no invariant strained, `gh`
already the enforcement-appropriate tool, single owner, complete propagation below.

### Option 2 — inline duplication (score 5)

**Motivator:** avoids adding a new file; keeps `java-spring-boot-developer`'s tool list
untouched by never calling anything from within it.

**Cons:** the user explicitly named more future callers ("outras skills ou
orquestradores") — duplicating the same git init/commit/push/confirm logic across three
or more files is exactly the failure invariant 2 exists to prevent; the first divergence
(one caller's push flow drifts from another's) is a when, not an if.

### Option 3 — Form 3 subagent (score ≤4)

**Motivator:** none offered — considered only because `project-initializer` and
`java-spring-boot-developer` are themselves agents.

**Cons:** none of the three legitimate reasons apply (no verbose output to hide, no tool
restriction beyond what the calling context already has, no model/cost reason); the
interview *is* the task, ruled out by the § 5 counter-test on all three points at once.

## References

| Claim | Source |
|---|---|
| `disable-model-invocation: true` blocks explicit `Skill`-tool invocation, not just spontaneous firing | `.claude/agents/java-spring-boot-developer.md` § Design patterns — "its `disable-model-invocation: true` means the model can't call it that way regardless" |
| Pipeline skills stay without `disable-model-invocation` so the orchestrator can call them; the entry guard in the body replaces the flag | `@.claude/decisions/0007-pipeline-skills-invocation.md` (D17) |
| `gh` CLI is the cheaper alternative to a new MCP server | `references/decision-matrix.md` § 2.1 |
| Agent exists only for one of three reasons; counter-test | `references/decision-matrix.md` § 5 |
| Never push without explicit confirmation; review staged files for secrets before committing | this session's system instructions (git safety protocol) |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/git-publish/SKILL.md` | New — the skill itself |
| `.claude/agents/project-initializer.md` | `tools:` gains `Skill`; new final step invoking `git-publish` after a green build |
| `.claude/agents/java-spring-boot-developer.md` | Final summary and failure/invocation examples point to `git-publish` instead of raw `git` commands; Contract notes it doesn't invoke it itself |
| `.claude/skills/new-feature/SKILL.md` | Consolidation step 4 invokes `git-publish` after the executor reports success |
| `.claude/skills/project-bootstrap/SKILL.md` | Step 6.7 table gains a `git-publish` row; `## Skill contract` writes list includes it |
| `CLAUDE.md` (this repo) | Routing table row |
| `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example` | Routing table row (reaches the generated project's own `CLAUDE.md`) |

Goes to the generated project: **yes, via step 6.7** — axis "destination" answered
"both": the skill is used at generation time by `project-initializer` (this repo) and
must still exist inside the generated project for every future `/new-feature` run there.
