# 0041 · Audit trail loses background subagents — four fixes in `ArchHook.java`

- **Date:** 2026-09-16
- **Scenario:** ran `/new-feature` and `/test-architect setup` in the demo project.
  Both reports (`.claude/audit-usage/2026-09-16T20-26-28--new-feature.md` and
  `2026-09-16T20-43-27--test-architect.md`) froze right after the skill launched its
  subagent (`java-spring-boot-developer`, `archunit-installer`) and stayed stamped
  `⏳ em andamento` forever, with the subagent's own work invisible — no files, no
  duration past the launch.
- **Decision:** fix all four causes found in `ArchHook.java`, in one change — they are
  one bug for the user (the trail loses the subagent) even though they live in three
  different functions. No new skill, agent, or rule.
- **State:** approved by Lucas Fernandes, on 2026-09-16

## Context — root cause

Both subagents run **in background** (`archunit-installer`'s own
`.state/<session>/subagents/*.meta.json` records `"requestShape":"background"`). The
tool call returns immediately ("Async agent launched successfully"), the main turn's
`Stop` hook fires and calls `audit flush` while the subagent is still working, and the
subagent's result comes back later as a synthetic `<task-notification>` prompt. That
shape alone should have just meant "the first flush is incomplete, later ones catch up"
— and it would have, except for a real crash:

`auditRules` (called from `auditRender`, called from every `flush`/`close`) built its
map key as `f.getFileName() + " " + g` — except the separator between filename and glob
was a **literal NUL byte** (`\x00`), invisible in an editor, confirmed with `od -c`.
`auditRender` then did `e.getKey().split(" ", 2)` and read `parts[1]`. `split(" ", 2)`
on a string containing no real space returns a one-element array —
`ArrayIndexOutOfBoundsException`. `main`'s top-level `catch (Exception e)` swallows it
and exits 0, so the hook prints a warning to stderr and the session sees nothing.

Reproduced by copying the demo project's real `.state/<session>.ndjson` and transcript
into a scratch checkout and replaying `audit flush`/`audit prompt` by hand:

- Every render before any `src/**`-matching file was touched succeeded (that's the 17s /
  5s "launched, then frozen" snapshot actually captured in the two reports).
- The instant a touched file matched a rule's `paths` (`code-quality.md`'s `**/*.java`,
  reached the moment the subagent edited a `.java` file), every subsequent `flush` threw
  and the report stopped updating — permanently, since nothing later matches fewer
  files.
- Independently of the crash: replaying the exact `<task-notification>` prompt the demo
  session received against an **open, uncrashed** run showed `auditPrompt` closing it —
  "any prompt ends the run in progress" treated the harness's own background-result
  delivery as user intent to end the session's work. Confirmed by inspecting `history.jsonl`
  gaining a premature `run` line at the moment of the synthetic prompt, well before the
  subagent's remaining edits (and the `./mvnw verify` that followed) ever happened.
- A third, latent problem in the same code path: `auditRender`'s node-depth tracking was
  a bare counter (`depth++` on `agent`, `depth = max(1, depth-1)` on `agent_end`). The
  demo's real log has **nine** `agent_end` events with an empty `name`, interleaved with
  the real `archunit-installer` subagent's own file edits — internal/ephemeral agents the
  background-agent runtime emits `SubagentStop` for, with no `Skill`/`Task`/`Agent`
  PreToolUse of ours and no transcript in `subagentTranscripts()`. Each one still
  decremented the shared counter, so nesting and per-node duration were wrong the moment
  more than one `agent_end` arrived before the tracked one.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Fix all four causes in `ArchHook.java`, one change | 9 | **Approved** |
| 2 | Fix only the crash (cause 1), leave the rest | 5 | Rejected — the premature-close bug (cause 2) still mis-attributes every background run's tail to no run at all, crash or not |
| 3 | Force subagents to run in foreground from the calling skill | 3 | Rejected — no documented way to require foreground from a `Skill`/`Task`/`Agent` call; the hook has to tolerate background execution regardless |
| 4 | Rewrite the audit trail as a proper event-sourced format instead of NDJSON + re-render | 2 | Rejected — the actual defects are three bugs in ~40 lines, not a format problem; NDJSON append-only is exactly what survives `kill -9` per the hook's own design comment |

### Option 1 — fix all four (score 9)

**Motivator:** all four live in the same function family (`audit` → `auditPrompt` /
`auditRender` / `auditRules`), touched by the same investigation, and shipping only part
of them leaves the trail wrong in a different way.

1. **Crash (`auditRules` key).** Replaced the joined-string key with a
   `record RuleHit(String rule, String glob, List<String> files)` and a
   `List<RuleHit>` return — no separator, nothing to `split`, the whole bug class is
   structurally gone rather than patched. Added a `doctor` self-check that calls
   `auditRules(Set.of(".../Sample.java"))` and reports pass/fail, so a future edit to
   this function that reintroduces a throw is visible in `/arch-doctor` instead of
   silent.
2. **Premature close (`auditPrompt`).** A prompt matching `^\s*<task-notification>` now
   returns immediately — no close, no open, no overwrite of the observer's
   `.prompt.json`. The run stays open and keeps absorbing the subagent's remaining
   `file`/`agent_end` events until a real prompt (a second `/command`, or plain text)
   ends it.
3. **Depth tracking (`auditRender`).** Replaced the counter with a
   `Deque<String> openAgents` of tool-use-ids. `agent` pushes its own; `agent_end`
   resolves its `agent_id` back to a tool-use-id via `toolUseIdOfAgentId` (built from
   `subagentTranscripts()`, moved before the event loop so it's available during it) and
   pops that specific entry — an `agent_id` that resolves to nothing closes none of ours.
   Depth for a new node is `openAgents.size() + 1`.
4. **Status label.** When a `flush` (not `close`) finds `openAgents` non-empty, the
   status line now reads `⏳ aguardando subagent em background` instead of the
   indistinguishable `⏳ em andamento` — a mid-run snapshot now says what it's actually
   waiting for.

**Pros:** the crash fix eliminates a whole class of "invisible string surgery" bug, not
just this instance; the close fix is the one that actually restores tracking end-to-end
(the crash fix alone still would have closed early on the real notification, just
without throwing); no new file, no propagation beyond the hook itself.

**Cons:** widens `ArchHook.java` (already 2114 lines) by ~40 net lines. Accepted —
splitting the hook into multiple files is a larger, unrelated change (this repo ships
`ArchHook.java` as a single file on purpose, per its own header, to stay a same-directory
JDK 21 single-file program with zero build step).

## Verification

Replayed the demo project's real `.state/0a55aed3-…ndjson` and transcript against the
fixed hook in a scratch checkout, in sequence:

1. `flush` mid-background (agent launched, not yet finished) → renders, status
   `⏳ aguardando subagent em background`.
2. The real `<task-notification>` prompt → `history.jsonl` still absent, `.ndjson` still
   open, no `.prompt.json` written.
3. Remaining real events appended (agent's file edits, `agent_end`) → `flush` renders
   all 4 touched files, correct one-level tree, no exception.
4. A real next prompt → closes with `✅ sucesso`, one `history.jsonl` line.

`javac .claude/hooks/ArchHook.java` compiles clean. `doctor` against the demo project
(temporarily copying the fixed hook there and reverting after) shows the new
`Audit rule inference` check passing.

## References

| Claim | Source |
|---|---|
| Async agent tool result, background execution shape | demo project's `.claude/audit-usage/.state/<session>/subagents/*.meta.json` (`requestShape: "background"`) |
| A hook must never crash the session | `.claude/hooks/ArchHook.java`'s own `main`'s comment, same file |
| "A run closes at the first user prompt after it opened" | `.claude/hooks/ArchHook.java` phase-table comment, above `audit()` |
| NDJSON append-only survives a kill -9 | same comment block |
| Single-file, no build step, by design | `.claude/hooks/ArchHook.java`'s file header |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | `auditRules` returns `List<RuleHit>` (was `Map<String,List<String>>` keyed by a NUL-joined string); `auditRender`'s render call site updated to match; `auditPrompt` early-returns on `<task-notification>`; `auditRender`'s depth tracking replaced by an `agent_id`-resolved `Deque<String> openAgents`; status line adds the background-wait case; `doctor()` gains an `Audit rule inference` self-check |

Goes to the generated project: **yes**, automatically — `ArchHook.java` is copied
verbatim by `project-bootstrap` (invariant 9); no template or `SKILL.md` references its
internals, so no other file needed a change. This record stays here (invariant 9).
