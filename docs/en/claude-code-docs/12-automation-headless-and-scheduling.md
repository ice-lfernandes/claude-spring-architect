# 12 — Automation, headless and scheduling

Official pages covered:

| Page | Link |
|--------|------|
| Run Claude Code programmatically (headless) | <https://code.claude.com/docs/en/headless> |
| Automate actions with hooks | <https://code.claude.com/docs/en/hooks-guide> |
| Push events into a running session with channels | <https://code.claude.com/docs/en/channels> |
| Channels reference | <https://code.claude.com/docs/en/channels-reference> |
| Run prompts on a schedule (`/loop`, cron) | <https://code.claude.com/docs/en/scheduled-tasks> |
| Automate work with routines | <https://code.claude.com/docs/en/routines> |
| Schedule recurring tasks in Desktop | <https://code.claude.com/docs/en/desktop-scheduled-tasks> |
| Keep Claude working toward a goal | <https://code.claude.com/docs/en/goal> |
| Launch sessions from links | <https://code.claude.com/docs/en/deep-links> |
| Share session output as artifacts | <https://code.claude.com/docs/en/artifacts> |

---

## Non-interactive mode (`-p`)

```bash
claude -p "Explain what this project does"
claude -p "List all API endpoints" --output-format json
claude -p "Analyze this log file" --output-format stream-json --verbose
tail -200 app.log | claude -p "Slack me if you see any anomalies"
git diff main --name-only | claude -p "review these changed files for security issues"
```

- `text` prints plain text; `json` returns an object with a `result` field; `stream-json`
  prints one JSON object per line, starting with an init event.
- The run creates a resumable session, unless you pass `--no-session-persistence`.
- Useful flags: `--max-turns`, `--max-budget-usd`, `--json-schema` (validated output),
  `--include-partial-messages`, `--include-hook-events`, `--forward-subagent-text`,
  `--allowedTools`, `--permission-prompt-tool`, `--permission-prompts`.
- **Bare mode** (`--bare`) skips auto-discovery of hooks, skills, commands, subagents and
  plugins — faster startup and a predictable environment.
- `SIGTERM` interrupts the run; there is defined behavior for background tasks on exit and
  for a deleted working directory.
- In `-p`, fork mode is off by default, `PermissionRequest` only exists when the Agent SDK
  wrapper supplies the `canUseTool` callback, and `.mcp.json` servers load **without** an
  approval prompt.

Documented fan-out pattern: generate the list of targets, iterate calling `claude -p` with a
restricted `--allowedTools`, test on 2-3 items, and only then run over the whole set.

## Event-driven automation

- **Hooks** (see [04](04-hooks.md)) — the deterministic basis: format after an edit, block
  commands, log, notify, inject context, audit configuration changes.
- **Channels** — an MCP server pushes messages, alerts and webhooks into a running session:
  CI results, chat messages, monitoring events (`--channels`, research preview).
- **Deep links** — `claude-cli://` URLs open a session in the right repository with the
  right prompt; embeddable in runbooks, alerts and dashboards.

## Scheduling

| Mechanism | Where it runs | Use |
|-----------|-----------|-----|
| **Routines** (`/schedule`) | Cloud | Keeps running with your computer off; fires on a schedule, API call or GitHub event |
| **Desktop scheduled tasks** | Your machine | Direct access to local files and tools; loads `~/.claude/skills/` |
| **`/loop [interval] [prompt]`** | Current session | Repeats a prompt while the session is open; polling and reminders |
| **Cron tools** (`CronCreate`, `CronList`, `CronDelete`) | Current session | Scheduled tasks inside the session, restored on `--resume` |

Gotcha: each routine run is a fresh cloud session. A skill that exists only in
`~/.claude/skills/` is **not** found there — enable it on your claude.ai account or commit
it to `.claude/skills/`.

In dynamic `/loop` (no interval), Claude reschedules itself with `ScheduleWakeup`, choosing
the interval according to what it is waiting for.

## `/goal`: work until the condition is met

`/goal <condition>` sets a completion condition. A separate evaluator re-checks after every
turn and Claude keeps working until the condition is met, until a model judges it
impossible, or until an error you need to fix clears the goal. `/goal clear` removes it.

It is the middle step between asking for verification in the prompt and forcing it with a
`Stop` hook.

## Artifacts

They publish session output as a private, interactive web page on claude.ai — useful when
the result is better seen than read in the terminal (an incident timeline that updates while
Claude investigates, for example). Controlled by the `enableArtifact` / `disableArtifact`
keys (the restrictive value wins from any scope) and listable with `/artifacts`.

## Unattended usage pattern

Combination recommended by the documentation for running without you watching:

1. `--permission-mode auto` (or `dontAsk` with an allowlist in locked-down CI);
2. an executable check — `/goal` or a `Stop` hook;
3. `--max-turns` and/or `--max-budget-usd` as a safety stop;
4. structured output (`--output-format json` or `--json-schema`) for the next script;
5. adversarial review (subagent or `/code-review`) before treating it as done.
