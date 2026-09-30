# 0038 · The execution trail covers every project skill and agent, not only orchestrators

- **Date:** 2026-09-16
- **Scenario:** "qualquer `.claude/skills` e `.claude/agents` que seja executado pelo modelo
  ou chamado pelo usuário tenha seu report de audit-usage — determinístico, não opcional,
  pesando custo de tokens."
- **Decision:** Out of the six forms — `audit` mode of `.claude/hooks/ArchHook.java` extended
  to every project skill and agent, plus a read-only `audit summary` subcommand that
  `.claude/skills/audit-usage/SKILL.md` injects instead of the raw ledger.
- **State:** approved by Lucas Fernandes, on 2026-09-16 — option 1

## Interview

Only the axes that eliminated forms.

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — concrete symptom | A model-invoked skill (e.g. `git-publish`, `java-patterns`) left no report; an agent spawned from a plain prompt left no report | — (observed, not anticipation) |
| 2 — trigger | Lifecycle event: a `/command`, a `Skill` tool call, an `Agent`/`Task` tool call | 4, 5, 6 |
| 4 — territory | Names that resolve to `.claude/skills/<n>/SKILL.md` or `.claude/agents/<n>.md`. Plugin skills (`caveman:*`) and runtime agents (`Explore`, `general-purpose`) are out | — |
| 6 — isolation | None | 3 |
| 7 — mandatoriness | **Always** — "determinístico, não é opcional" | **1, 2** — and with them all six forms |
| 8 — destination | Generated project only. The meta-repo stays unwired, as `0035` decided | — |
| granularity | One `.md` per top-level invocation (user or model); nested skills/agents become a section in the parent report **and** their own ledger row carrying `parent` | — |
| 11-13 — MCP | Not applicable | 6a, 6b |

Axis 7 decides it the same way it decided `0035`: the first row of decision matrix § 2
matches before any form is considered. This skill proposes and stops; the change is to
`ArchHook.java`.

## The gap, measured in the code

`auditPrompt` opens a run only when the prompt is `/<name>` **and** that skill carries
`disable-model-invocation: true` (`isOrchestrator`). `append` drops every event when no
run is open. So today:

| Invocation | Recorded? |
|---|---|
| `/new-feature` (orchestrator) | Yes — its own report |
| `Skill`/`Agent` call inside an open run | Only as a node; no ledger row of its own |
| `/java-patterns` typed by the user (auto-invocable skill) | **No** — not an orchestrator |
| Model calls `Skill(git-publish)` after a plain prompt | **No** — no open run |
| Model spawns `java-spring-boot-developer` after a plain prompt | **No** — no open run |
| `java-patterns` preloaded via `skills:` in the executor's frontmatter | **No** — no tool call exists |

A second, silent gap: `auditTokens` reads only the main transcript. A subagent's usage is
written to its own transcript, so agent spend is under-counted even inside an open run.
Verified during implementation — see § What implementation changed.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Extend `ArchHook.java audit` — implicit runs + per-node ledger rows + `audit summary` pre-aggregation for `/audit-usage` | 9 | **Approved** |
| 2 | Option 1 without `audit summary` — `/audit-usage` keeps injecting the raw ledger tail | 7 | Rejected — raw node rows raise `/audit-usage` tokens; sums stay in the model |
| 3 | Reconstruct the chain from the transcript at `Stop`/`SessionEnd` only | 5 | Rejected — undocumented transcript format as the only source; silent break |
| 4 | Self-report: a closing instruction in every `SKILL.md` and agent body | 2 | Rejected — persuasion, tokens per invocation, nothing on crash |
| 5 | Create nothing — orchestrators only | 3 | Rejected — leaves both observed gaps open |

### Option 1 — extend `audit` mode + `audit summary` (score 9)

**Motivator:** axis 7, plus the explicit token-cost constraint.

**Mechanism — no new hook event is wired.** `UserPromptSubmit`, `PreToolUse
Skill|Task|Agent`, `SubagentStop`, `Stop` and `SessionEnd` are already in
`settings.json.example`; the JVM count per turn does not change.

1. **Audited set = resolves to a project file.** `isOrchestrator` becomes `isAudited`:
   `.claude/skills/<n>/SKILL.md` or `.claude/agents/<n>.md` exists, and `<n>` is not in
   `audit.exclude_skills`. Still data, not a hardcoded list — invariant 7. A plugin name
   (`caveman:x`) has no file and falls out by construction.
2. **`auditPrompt`** opens a run for any `/<n>` that `isAudited`, with `origin: user`.
3. **`auditCall` opens an implicit run** when no run is open and the called skill/agent
   `isAudited`: `run_start` with `origin: model`, `kind: skill|agent`. Inside an open run
   it stays a node, as today. The implicit run closes at the next prompt, like any run.
4. **`SubagentStop`** records `agent_transcript_path` (if the payload carries it) on
   `agent_end`, so an agent node's tokens come from its own transcript. Skill nodes in
   the main thread keep the timestamp-window attribution already stated in `0035`.
5. **Preloaded skills** (`skills:` in an agent's frontmatter) are listed under that agent
   node as `pré-carregada`, derived from frontmatter at render — no event exists for
   them, and none is needed.
6. **Ledger:** the root row stays one line per run (now with `origin`, `kind`,
   `tokens_total`). Each node writes a line to a **separate** `nodes.jsonl` with `run`,
   `parent`, `kind`, `name`, `tokens_self`, `duration_ms`. `history.jsonl` does not grow
   faster, so its readers do not pay for the finer grain.
7. **`ArchHook.java audit summary`** — new read-only subcommand. Aggregates
   `history.jsonl` + `nodes.jsonl` in the JVM and prints a compact block: totals, spend
   per skill/agent (roots and nodes, self-tokens, never double-counted), failure rate,
   last 15 runs. `/audit-usage` injects that block instead of `tail -n 60` of raw JSON.

**Token cost:**

| Where | Today | Option 1 |
|---|---|---|
| Model tokens to *produce* the trail | 0 | 0 — hook only |
| Model tokens per skill/agent invocation | 0 | 0 — no instruction added to any body |
| `/audit-usage` injection | ~60 raw JSON lines, grows with fields | Pre-aggregated table, bounded; estimated at a fraction of the raw tail, to be measured |
| `/audit-usage` arithmetic | Done by the model — error-prone | Done by the JVM; the model renders |
| Runtime latency | JVM on `Stop` renders only inside orchestrator runs | Renders whenever an audited piece ran in the turn — more `Stop` renders, each reads the transcript |

**Pros:** deterministic for every tool-mediated invocation; zero permanent context; no
new event wiring; extends the existing design instead of adding a second mechanism;
fixes agent under-counting; lowers `/audit-usage` tokens instead of raising them.

**Cons:** `ArchHook.java` grows again (already 1613 lines). More versioned `.md` files per
session — every model-initiated skill makes a report. `SubagentStop`'s transcript field is
not documented in `@claude-help.md` § 8 (only the event name is) — best-effort, same caveat
`0035` took for `PermissionRequest`. `/audit-usage` changes its injection, so its body's
§ 2 parsing instructions change with it.

**Not covered, by nature:** a model that reads `SKILL.md` with `Read` instead of invoking
it (not an invocation); hooks disabled or `.claude/audit-usage/` absent (the switch);
a run killed before `Stop` (keeps the last flush, `⏳ em andamento`, as today).

**Points cut in the rubric:** undocumented payload field for agent transcripts.

### Option 2 — Option 1 without `audit summary` (score 7)

Same capture. `/audit-usage` keeps `tail -n 60 history.jsonl` and adds a tail of
`nodes.jsonl` to get per-node spend. Rejected on the token constraint: node rows multiply
the raw JSON injected per read, and the aggregation stays in the model, where it already
risks arithmetic errors. Smaller Java diff is its only advantage.

### Option 3 — transcript reconstruction at `Stop`/`SessionEnd` (score 5)

Parse `tool_use` blocks named `Skill`/`Agent` and `<command-name>` markers out of the
transcript. Pros: no dependency on `PreToolUse`, catches anything the transcript holds.
Cons: the transcript's line format is undocumented and changes between runtime versions —
a silent break produces no report at all, the exact failure axis 7 forbids; nested
subagent transcripts are separate files anyway. The events already wired give the same
data with a documented contract.

### Option 4 — self-report in every body (score 2)

A closing line in each `SKILL.md`/agent telling the model to record itself. Persuasion
where a guarantee exists — anti-pattern 2; costs tokens on every invocation (instruction
plus a tool call); a crashed run records nothing. Contradicts axis 7 head-on.

### Option 5 — create nothing (score 3)

Keeps the two observed gaps of axis 1 open, and the agent under-count.

## References

| Claim | Source |
|---|---|
| "Always" resolves to a hook before any of the six forms | `references/decision-matrix.md` § 1, § 2 row 1 · `@CLAUDE.md` invariant 6 |
| `PreToolUse`, `SubagentStop`, `Stop`, `SessionEnd` exist | `@claude-help.md` § 8 "Events" |
| Only orchestrators open a run; events outside a run are dropped | `.claude/hooks/ArchHook.java` `auditPrompt`, `isOrchestrator`, `append` |
| Tokens read from the main transcript only | `.claude/hooks/ArchHook.java` `auditTokens` |
| `Skill|Task|Agent` and `SubagentStop` already wired | `.claude/skills/project-bootstrap/templates/settings.json.example` |
| Preloaded skill with no tool call | `.claude/agents/java-spring-boot-developer.md` `skills: java-patterns` · `0025` |
| Audited set and exclusions are data | `@CLAUDE.md` invariants 7, 10 · `.claude/schemas/extensions.json` `audit.exclude_skills` |
| Meta-repo stays unwired | `0035` Propagation · `@CLAUDE.md` pitfall "The `audit` mode is off in this repository" |

## What implementation changed from the proposal

Written after the code, so nobody reads the option above as the shipped design.

- **Agent tokens do not come from `SubagentStop`.** The documented payload carries
  `agent_id` and `agent_type`, not a transcript path. What exists on disk, observed and
  not documented: `<session>.jsonl` sits next to `<session>/subagents/agent-<agentId>.jsonl`,
  and a sibling `.meta.json` carries `toolUseId` — the same `tool_use_id` PreToolUse
  delivers. The call event records `tool_use_id`; the render maps it to the file. A
  changed layout reads as zero tokens for that agent, never as a crash.
- **The existing token count double-counted.** The runtime writes one transcript line per
  content block, each repeating the message's `usage` — measured: 20 usage lines for 9
  messages. Turns are now deduplicated by `message.id`. Every report written before this
  change over-states tokens and cost.
- **Timestamps are parsed, not compared as text.** `…12Z` sorts after `…12.5Z` as a
  string; the old lexicographic filter could drop or admit a turn at the boundary.
- **The deciding turn counts.** A model-opened run starts at PreToolUse, after the message
  that decided the call was already written. The last plain prompt is kept in
  `.state/<session>.prompt.json` (gitignored, redacted, overwritten each turn) and its
  timestamp opens the token window, so that message is not lost between runs. The same
  file gives the report the user's words that led to the call.
- **Attribution rule.** An agent's tokens = its own transcript. A main-thread turn belongs
  to the last main-thread piece started before it when that piece is a skill; after an
  agent call, or before any piece, it is the root's orchestration. A skill called inside
  a subagent has no tokens of its own — they are its agent's. Stated in the report.
- **Cost is per model.** A subagent on another model is priced at its own rate; any model
  with usage and no price makes the amount unknown, not a partial sum.
- **Ledger fields.** `history.jsonl` gains `kind`, `origin`, `tokens_self`, `cost_usd`,
  `cost_self_usd` (dot decimal, summable); `tokens_billable` keeps meaning the run total.
  `nodes.jsonl` lines: `run`, `parent`, `kind`, `skill`, `origin` (`nested`/`preloaded`),
  `tokens_self`, `cost_usd`, `duration_ms` — usage fields absent, never zero, when the
  tokens are the agent's. Lines written before the change still aggregate: no `kind`
  reads as skill, no `tokens_self` reads as the whole run, `cost` is parsed.
- **`audit summary` ignores non-report Markdown** (`GENESIS.md`, from `project-bootstrap`
  step 8.6) when listing runs without a ledger line.
- **`/audit-usage` injection shrank from three blocks to one.** The `ls` listing and the
  `pricing.json` dump went too: the listing grows with every report, and the summary
  already says whether prices are set. The skill runs `ls` or `grep` only when an argument
  needs a filename.
- **Verified** by simulating the event sequence in a scratch project: model-opened agent
  run, a skill inside the subagent, a main-thread skill after it, a runtime agent
  (`Explore`) as an unrecorded node, a plugin skill ignored, an observer closing the run,
  a user `/command` run with a tool failure, legacy ledger lines, and an absent directory
  (summary prints nothing). Not yet exercised in a live generated project.

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | `isAuditedSkill`/`isAuditedAgent` replace `isOrchestrator`; `auditCall` opens model-initiated runs; `openRun`, prompt file; `Usage` per model replaces `Tokens`; `usageTurns` dedup + parsed timestamps; `subagentTranscripts`; per-piece section in the report; `nodes.jsonl`; `preloadedSkills`; `audit summary`; `doctor` no longer probes `auditCost` |
| `.claude/schemas/extensions.json` | `audit.$comment`: the audited set, and this record |
| `.claude/skills/audit-usage/SKILL.md` | Single `audit summary` injection; §§ empty-state, routing, consolidated view, contract rewritten around it |
| `.claude/skills/project-bootstrap/SKILL.md` | Step 7 part 5 (`nodes.jsonl` versioned), "What the trail records", output line, step 6.7 row |
| `.claude/skills/project-bootstrap/templates/settings.json.example` | `_comment` — no wiring change |
| `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example` | Command row and routing row |
| `CLAUDE.md` | Routing rows for `audit` and `audit-usage` |

Goes to the generated project: **yes** — `ArchHook.java`, `extensions.json` (step 7) and
`audit-usage` (step 6.7) already travel. This meta-repo stays unwired.
