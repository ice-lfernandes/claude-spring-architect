# 0119 · The audit ignores a subagent's hand-back, and the executor reports once

- **Date:** 2026-10-03
- **Scenario:** "Issue #84, triaged at 4be8813" — plus the user's question of whether it relates to 0117 and 0118
- **Decision:** option 1 — `audit.harness_prompts` in `.claude/schemas/extensions.json`, read by `auditPrompt` in `.claude/hooks/ArchHook.java` (Form 7c edit of the `audit` mode); `.claude/agents/java-spring-boot-developer.md` reports once, at the end; notes in 0023, 0117 and 0118
- **State:** approved by Lucas Fernandes, on 2026-10-03
- **Goes to the generated project:** yes, for every option that edits a piece — `ArchHook.java`, the jar and `extensions.json` travel through `export`, and `java-spring-boot-developer` is in `export.agents.include`

## Reproduced on disk

The `issue-verifier` table of `/triage-issue 84` (layer 1, static, at `4be8813`) confirmed
claims 1–7, 11 and 12, and left 8–10 unproven. Those three were checked here against
`demo-clean-arch-single-module` — its `.claude/audit-usage/` and the session transcripts —
before any option was written. The issue's proposed fix is not an input.

| Fact | Evidence | Result |
|---|---|---|
| The harness delivers a background subagent's report as a prompt that starts with `<agent-message from="…">`. It runs `UserPromptSubmit`, so `auditPrompt` receives it | Five `.state/<session>.prompt.json` files hold it, as written by `auditPrompt` itself. It is the observer file `auditPrompt` writes *after* the close branch. The `Another Claude session sent a message:` preamble is display only and is not in the prompt | **Confirmed** |
| `auditPrompt` closes the open run on it | `history.jsonl`: four of four implement runs end at the second of the hand-back. UC-004 22:35:45, UC-005 13:26:21, UC-007 01:11:00, UC-008 21:02:47 (UTC) | **Confirmed, and wider than the issue.** Every background executor run closes at its *final* hand-back, not only when an intermediate one is sent |
| The work after the hand-back is in no report | Transcripts: `Skill(git-publish)` 7–10 s after each hand-back, then two `AskUserQuestion`s, a commit and a push. `git-publish` is class `ops`, `audited: false` (the demo's `audited.json` also sets it `false`). It reaches a report only as a node of a run that is still open | **Confirmed.** The tail is not misattributed. It is missing |
| Claim 8: `SubagentHandback` delivers one report | Subagent transcript `agent-a456d0d1f3f5c0efa` (UC-008), 21:23:44: `{"success":false,"message":"Nothing was sent: your report was already delivered (SubagentHandback delivers one report). Use SendMessage for anything further, then stop."}` | **Confirmed** |
| Claim 9: the executor spent that one report on Block 1 progress | Same transcript, 21:02:47: `SubagentHandback("PROGRESS UPDATE (not final — continuing): Block 1 (Domain, steps 1-7) is complete…")` | **Confirmed** |
| Consequence beyond the audit: the final report never reached the caller | Main thread, after the 21:02:47 hand-back: it waits, then at the 21:24:04 `<task-notification>` it runs `./mvnw -q clean verify` itself, reads `git diff --stat`, and only then chains `git-publish`. The executor's final report, with the four mandated findings `/new-feature` relies on, is lost | **Confirmed.** The main thread recovered on its own, 1 of 1 |
| Claim 10: report end earlier than peak context, 9 files audited of 52 committed | The UC-008 run: `20:57:25 → 21:02:47`, `files 9` | **Confirmed** — the close at the progress hand-back |
| The triage's "no test harness exercises `auditPrompt`" | `.claude/.ci/AuditRenderTest.java` runs `audit prompt` on the jar in `validate · hooks-cross-platform` | **Refuted.** A case can be added there |
| Regression from 0117? | 0117's diff adds no reporting or feedback lines. `:139` dates from the initial commit. UC-004, UC-005 and UC-007, all before 0117, sent one final report each | **No.** The executor's instruction was ambiguous from the start, and UC-008 is the first run that read it that way |

Nothing changed in the cited files between `4be8813` and this record.

## Relation to 0117 and 0118

- **0118, finding 2b.** It refuted "post-run work lands on `git-publish`" because "the audit
  closes the run". The effect is right, but for implement runs the cause is this bug: the
  close came from the executor's hand-back, not from a user prompt. The USD 0.29–0.54
  `git-publish` that 0118 saw comes from design-only runs, which have no background agent.
  In implement runs the tail is in no report: an under-count, not a misattribution.
- **0117, row 3 (implement an approved spec).** The executor's USD 3.34–14.82 holds, because
  the executor finishes before its hand-back. The run total leaves out the main thread's tail:
  `git-publish` with its two confirmations, at 250–370k context, about USD 0.3–0.5 (0118's
  own measure).
- **0117, option 2 (deferred).** Splitting the executor into 2–3 delegations would make
  each group's hand-back close the run, so the report would hold group 1 only. This
  record is a precondition for reopening it.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Measured: 4 of 4 background implement runs closed early; 1 of 4 executors lost its final report | Create nothing |
| 5 — Nature | Two defects: a check in an existing hook mode, and an ambiguous instruction in an existing agent | A new skill, agent, rule or `CLAUDE.md` section |
| 7 — Mandatoriness | The audit is already a hook, and the defect is in its check. The executor's "report once" can't be enforced exactly: the runtime refuses a second hand-back on its own, and a hook can't tell progress from a final report | A new hook for the executor |
| 8 — Destination | Both | — |
| 16 — Existing mode | `audit` (`auditPrompt`) | A new mode |
| Harness prompt shapes (user) | **A list in `extensions.json`** — invariant 10. The `TASK_NOTIFICATION` constant becomes data | A wider Java regex |
| Intermediate feedback (user) | **Option A:** each block's lines go into the final report, which is delivered once | Plain text in the transcript, which keeps the turns and the ambiguity. Removing the templates, which loses the per-block account |
| `/new-feature` third state (user) | **Create nothing.** With A the cause is gone, and the orchestrator already recovered on its own | A line in § Executor offer |
| Records (user) | **Notes in 0117 and 0118** pointing here | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `audit.harness_prompts` (7c edit) + executor single report + notes in 0117/0118 | 8 | **Approved** |
| 2 | Only the audit fix | 6 | Rejected — leaves the lost final report in place |
| 3 | Only the executor fix | 4 | Rejected — every background run still closes at its final hand-back |
| 4 | Ignore every prompt while a background agent is open (the issue's F1b) | 2 | Rejected — contradicts 0041 |
| 5 | A `PreToolUse` hook on `SubagentHandback` refusing a non-final report | 2 | Rejected — see below |
| 6 | Create nothing | 1 | Rejected — measured in 4 of 4 runs |

### Option 1 — the audit skips the hand-back, the executor reports once (score 8)

**Motivator:** axes 1 and 16. One prompt shape the hook never learned, and one instruction
that asks for several deliveries over a channel that carries one.

**Mechanism.**

- **`extensions.json`:** a new `audit.harness_prompts` list. It is an array of regexes, each
  matched with `find()` against the prompt: `^\s*<task-notification>` and
  `^\s*<agent-message\b`. A `$comment` says each one is a shape the harness injects as a
  prompt. Neither is user intent, so `auditPrompt` neither closes, nor opens, nor overwrites
  the observer file.
- **`ArchHook.java`:** `auditPrompt` reads the list from `extensions.json`. The
  `TASK_NOTIFICATION` constant goes away. The comment above the check names both shapes and
  cites 0041 and this record. The jar is rebuilt.
- **`java-spring-boot-developer.md`:**
  - `:139` becomes "Returns: one final report, delivered once at the end".
  - Each block's `**Intermediate feedback:**` becomes a `**Block line in the final report:**`
    template. It still holds the per-block files and errors, and the pattern findings
    `:308-313` require.
  - § Final summary gains a `Blocks:` section that collects them.
  - One sentence states the channel: the report is delivered once, when the run ends, and
    nothing is reported between blocks. That is the same discipline as `/new-feature`'s
    "No progress messages" (`SKILL.md:373`).
  - The usage example at `:904-905` drops "(4 intermediate feedback messages)".
- **0117 and 0118:** one note each pointing here. 0118 § 2b gets the cause for implement runs.
  0117 § Reopening option 2 gets this fix as a precondition.

**Pros:** removes both causes. The pattern list becomes data. The final report reaches the
caller again. The `git-publish` tail returns to the run as a node, just as in design runs.

**Cons:**
- The `<agent-message` shape is an undocumented runtime format, the same fragility as
  `<task-notification>`. If the harness renames it, runs close early again, silently.
- The executor half is persuasion. Nothing stops the model from handing back early, but no
  instruction asks for it any more.
- After the fix, main-thread turns between the hand-back and `git-publish` land on the root
  `new-feature` node. Turns after `git-publish` starts land on `git-publish`, per the
  attribution rule 0118 documented.

**Points cut in the rubric:** 1 for the fragile prompt format, 1 for the persuasion half.

**CI:**
- `validate · hooks-cross-platform › AuditRenderTest` gains a case. The test opens a run with
  `/demo-skill`, sends an `audit prompt` whose prompt starts with `<agent-message from="x">`,
  and checks that `history.jsonl` gains no line and that the observer file is untouched.
  Then a plain prompt closes the run.
- The existing `<task-notification>` path gets the same case. Today nothing tests it.
- `validate › schema` covers the agent body. What the agent types is not testable.

### Option 2 — the audit fix only (score 6)

The same `extensions.json` and `ArchHook.java` change. The executor body is left as is.
The trail becomes correct, but an executor that reads `:139` the way UC-008's did still
spends its one hand-back on progress, and the caller still loses the final report.

### Option 3 — the executor fix only (score 4)

It fixes the lost report and leaves the audit wrong in the common case: every background
run, progress or not, still closes at its final hand-back (4 of 4 measured).

### Option 4 — ignore every prompt while a background agent is open (score 2)

Contradicts 0041: a real plain-text prompt must still close the run. It also relies on
`openAgents` being resolved, which 0041 shows is unreliable: empty-name `agent_end` events,
and `agent_id`s that resolve to nothing. An executor that dies without `agent_end` (machine
sleep, `new-feature/SKILL.md:836`) would hold the run open for good.

### Option 5 — a hook refusing a non-final `SubagentHandback` (score 2)

A `PreToolUse` matcher on a runtime tool the agent does not declare. The hook would judge
"final" from free text, and the failure was seen once. This is the mirror of invariant 6:
the runtime already refuses the second call, and option 1 removes the instruction that led
to the first.

### Option 6 — create nothing (score 1)

The early close is in every background implement run of the demo.

## References

| Claim | Source |
|---|---|
| `auditPrompt` exempts only `^\s*<task-notification>`; any other prompt closes the run | `.claude/hooks/ArchHook.java` `auditPrompt` and `TASK_NOTIFICATION`, at `4be8813` |
| Why a harness-injected prompt must not close a run | `@.claude/decisions/0041-audit-background-subagent-tracking.md` option 1, item 2 |
| A mode reads its lists from `extensions.json` | `@CLAUDE.md` invariant 10 |
| `SubagentHandback` delivers one report | UC-008 executor transcript, the tool result quoted above |
| `/new-feature` allows no progress messages, only a final report | `.claude/skills/new-feature/SKILL.md:373` |
| Executor asks for intermediate messages | `.claude/agents/java-spring-boot-developer.md:139`, `:365`–`:777`, `:904-905` |
| `git-publish` is `audited: false` | `skill_classes.ops` in `.claude/schemas/extensions.json`; demo `audited.json` |
| Attribution of main-thread turns to the last started skill | `@.claude/decisions/0118-skill-model-pin-audit-tail-and-bsd-sed.md` finding 2 |
| `AuditRenderTest` already drives `audit prompt` on the jar | `.claude/.ci/AuditRenderTest.java`; `.github/workflows/validate.yml` |

## Propagation

| File | Change |
|---|---|
| `.claude/schemas/extensions.json` | `audit.harness_prompts` — `^\s*<task-notification>`, `^\s*<agent-message\b` — and its `$comment_harness_prompts` |
| `.claude/hooks/ArchHook.java` + `ArchHook.jar` | `auditPrompt` calls `isHarnessPrompt`, which reads the list (a pattern that does not compile matches nothing). The `TASK_NOTIFICATION` constant is gone. The comment names both shapes and cites 0041 and 0119 |
| `.claude/agents/java-spring-boot-developer.md` | § Contract `Returns:` reads one report, delivered once. § Procedure says a block ends with its line for the final report, not with a message. The six `**Intermediate feedback:**` templates become `**Line in the final report:**`, without their `Next:` lines. Block 4's template becomes the final summary with a `🧱 Blocks:` section. The two pattern-finding sentences of § Design patterns point to the block's line. § Invocation drops the intermediate messages |
| `.claude/.ci/AuditRenderTest.java` | Session `s11`: an `<agent-message>` hand-back and a `<task-notification>` leave the run open and the observer file absent, and the user's next prompt closes it. Header and failure message updated |
| `docs/{pt-br,en}/07-ci-validate.md` | `AuditRenderTest` row names the harness prompts and 0119 |
| `.claude/decisions/0023-design-java-spring-boot-developer-executor.md` | § 4 intermediate feedbacks: superseded note |
| `.claude/decisions/0117-new-feature-cost-per-entry-scenario.md` | Row 3 note on the missing tail; § Reopening option 2 gains precondition 3 |
| `.claude/decisions/0118-skill-model-pin-audit-tail-and-bsd-sed.md` | Correction under § Reproduced on disk: 2b's close, for implement runs, was this bug |

`/new-feature` is left as is (user: create nothing). Its § Final report and "No progress
messages" already match the executor's new contract.

Goes to the generated project: **yes** — `export` copies `ArchHook.java`, the jar and
`extensions.json` whole, and the agent is in `export.agents.include`. Nothing to add to the
manifest. The test and the records stay here.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · hooks-cross-platform › AuditRenderTest` | `audit prompt` on the jar leaves a run open on an `<agent-message>` hand-back and on a `<task-notification>`, writes no observer file for either, and closes on the user's next prompt | Green on the tree. Red with `^\s*<agent-message\b` removed from `audit.harness_prompts`: `an <agent-message> hand-back leaves the run open (0119)` fails by name, along with the two checks after it, since the run is already closed |
| `validate › schema` | The agent's frontmatter, class sections and `export` entry, and `extensions.json` | Green (exit 0) |
| `validate › build --verify` | The committed jar is the source's bytes | Green |

What the executor sends, and when, is not testable in CI: it is what the model types. It is
checked on the next background implement run in the demo: one `SubagentHandback` in the
executor's transcript, and the `history.jsonl` run ending at the user's next prompt, not at
the hand-back.
