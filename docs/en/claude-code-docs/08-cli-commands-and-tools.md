# 08 — CLI, `/` commands and tools

Official pages covered:

| Page | Link |
|--------|------|
| CLI reference | <https://code.claude.com/docs/en/cli-reference> |
| Commands | <https://code.claude.com/docs/en/commands> |
| Tools reference | <https://code.claude.com/docs/en/tools-reference> |
| Interactive mode | <https://code.claude.com/docs/en/interactive-mode> |
| Checkpointing | <https://code.claude.com/docs/en/checkpointing> |
| Manage sessions | <https://code.claude.com/docs/en/sessions> |
| Customize your status line | <https://code.claude.com/docs/en/statusline> |
| Customize keyboard shortcuts | <https://code.claude.com/docs/en/keybindings> |
| Configure your terminal | <https://code.claude.com/docs/en/terminal-config> |

---

## Shell commands

| Command | Use |
|---------|-----|
| `claude` | Interactive session |
| `claude "query"` | Interactive session with an initial prompt |
| `claude -p "query"` | Non-interactive (print) mode, exits when done |
| `cat file \| claude -p "query"` | Processes piped content |
| `claude -c` / `claude -c -p "query"` | Continues the most recent conversation in the directory |
| `claude -r "<session>" "query"` | Resumes a session by ID or name |
| `claude update` / `claude install [version]` | Updates / (re)installs the native binary |
| `claude auth login\|logout\|status` | Authentication (status exits 0 if logged in, 1 if not) |
| `claude doctor` | Read-only diagnosis of installation and settings |
| `claude mcp ...` | Manages MCP servers (`login`, `logout`, `list`, `get`, `add`, `remove`) |
| `claude plugin ...` | Manages plugins |
| `claude agents` / `attach` / `logs` / `stop` / `respawn` / `rm` | Background sessions (agent view) |
| `claude setup-token` | Long-lived OAuth token for CI |
| `claude project purge [path]` | Deletes all local state of the project |
| `claude ultrareview [target]` | Non-interactive multi-agent review |

## Most relevant flags

- **Session and context:** `--continue`, `--resume`, `--fork-session`, `--session-id`,
  `--name`, `--no-session-persistence`, `--add-dir`, `--worktree`, `--tmux`, `--teleport`,
  `--cloud`, `--autocompact`.
- **Permissions and tools:** `--permission-mode`, `--dangerously-skip-permissions`,
  `--allow-dangerously-skip-permissions`, `--allowedTools`, `--disallowedTools`, `--tools`,
  `--permission-prompt-tool`, `--permission-prompts`, `--restricted`, `--safe-mode`, `--bare`.
- **Configuration:** `--settings`, `--setting-sources`, `--mcp-config`, `--strict-mcp-config`,
  `--plugin-dir`, `--plugin-url`, `--agents`, `--agent`, `--disable-slash-commands`.
- **System prompt:** `--append-system-prompt[-file]`, `--system-prompt[-file]`,
  `--append-subagent-system-prompt[-file]`, `--exclude-dynamic-system-prompt-sections`.
- **Model:** `--model`, `--fallback-model`, `--effort`, `--advisor`.
- **Output (print mode):** `--output-format text|json|stream-json`, `--input-format`,
  `--include-partial-messages`, `--include-hook-events`, `--forward-subagent-text`,
  `--json-schema`, `--max-turns`, `--max-budget-usd`, `--verbose`.
- **Diagnostics:** `--debug[=categories]`, `--debug-file <path>`.

`claude --help` does not list every flag — a flag's absence there does not mean it does not
exist.

## `/` commands (selection)

Day-to-day flow:

| Command | Function |
|---------|-----------|
| `/init` | Generates an initial `CLAUDE.md` (`CLAUDE_CODE_NEW_INIT=1` for the interactive flow) |
| `/doctor` | Setup checkup, proposes fixes and cuts to `CLAUDE.md` |
| `/context [all]` | Shows context usage as a colored grid |
| `/compact [instructions]` | Summarizes the conversation to free context |
| `/clear [name]` | New conversation with empty context |
| `/rewind` (`Esc Esc`) | Restores conversation and/or code, or summarizes from a point |
| `/memory` | Edits `CLAUDE.md`, toggles auto memory |
| `/permissions` | allow/ask/deny rules (**Auto mode** tab when available) |
| `/hooks` | Lists hooks by event (read-only) |
| `/skills`, `/reload-skills`, `/skill-doctor` | List, reload and measure skills |
| `/plugin`, `/reload-plugins` | Manage plugins |
| `/mcp` | Status and authentication of MCP servers |
| `/model`, `/effort`, `/fast`, `/advisor` | Model, effort, fast mode, advisor |
| `/output-style` | Switch response style |
| `/config`, `/status`, `/usage` (`/cost`, `/stats`) | Configuration, status, cost and limits |
| `/diff`, `/code-review` (`/review`), `/security-review`, `/simplify` | Review |
| `/plan`, `/goal`, `/loop`, `/batch`, `/subtask`, `/tasks`, `/workflows` | Plan and parallelize |
| `/agents`, `/list-agents` | Subagents (the wizard was removed in v2.1.198) |
| `/resume`, `/rename`, `/branch`, `/export`, `/recap` | Session management |
| `/add-dir`, `/cd` | Working directories |
| `/btw` | Side question whose answer does **not** enter the history |
| `/verify`, `/run`, `/run-skill-generator` | Run and verify the application |
| `/bug`, `/feedback` | Report a problem / send feedback |

Entries marked **Skill** are bundled skills (a prompt handed to Claude); those marked
**Workflow** are dynamic workflows that run in the background (`/deep-research`). Not every
command shows up for every user — it depends on platform, plan and environment.

## Tool catalog

Canonical names used in permission rules and hook matchers:

- **Files:** `Read`, `Write`, `Edit`, `NotebookEdit`
- **Search:** `Glob`, `Grep` (absent by default on macOS/Linux/WSL, where Bash covers it),
  `LSP` (code intelligence, inactive until you install a language plugin)
- **Execution:** `Bash`, `PowerShell`, `Monitor` (runs a command in the background and
  returns each output line)
- **Web:** `WebFetch`, `WebSearch`
- **Agents and tasks:** `Agent`, `ListAgents`, `SendMessage`, `SubagentHandback`,
  `Workflow`, `TaskCreate`/`TaskGet`/`TaskList`/`TaskUpdate`/`TaskStop`/`TaskOutput`,
  `TodoWrite` (off by default)
- **Flow:** `EnterPlanMode`, `ExitPlanMode`, `EnterWorktree`, `ExitWorktree`,
  `AskUserQuestion`, `EndConversation`
- **Scheduling:** `CronCreate`, `CronDelete`, `CronList`, `ScheduleWakeup`, `RemoteTrigger`
- **MCP:** `ListMcpResourcesTool`, `ReadMcpResourceTool`, `WaitForMcpServers`, `ToolSearch`
- **Output to the user:** `Artifact`, `PushNotification`, `SendUserFile`, `ReportFindings`,
  `SendFeedback`, `ShareOnboardingGuide`
- **Skills:** `Skill`

Important behaviors: Bash has a default timeout of 2 minutes and automatically backgrounds
long commands; outputs above the inline ceiling become a file path plus a preview; the
`Edit` tool requires reading the file first in many cases; `WebFetch` has pre-approved
documentation domains; `WebSearch` has a per-session limit.

The label shown in the transcript may differ from the canonical name (`Stop Task` is
`TaskStop`) — rules and matchers use the **canonical name**.

## Interactive mode

- **Interrupt and steer:** `Esc` to stop; typing and pressing Enter queues the message,
  which Claude reads at the end of the tool calls in progress.
- **Rewind:** `Esc Esc` or `/rewind`; options to restore conversation, code, both, or
  summarize from/up to a point.
- **Shell mode:** the `!` prefix runs a command directly in the shell.
- **Background:** `Ctrl+B` sends the current task to the background.
- **Transcript:** `Ctrl+O`; `Ctrl+G` opens the plan in the editor; `Ctrl+R` reverse-searches
  history.
- Full **Vim mode** (NORMAL/INSERT, text objects, visual), spell check, emoji shortcodes,
  voice dictation, prompt suggestions and a built-in task list.

## Sessions and checkpoints

- Transcripts in `~/.claude/projects/` (JSONL); `cleanupPeriodDays` controls retention
  (auto memory files are left out of the sweep).
- `/resume` shows sessions of the current worktree by default, with shortcuts to widen the
  search.
- `/rename` names a session; treat sessions like working branches.
- Checkpoints cover **only** changes made through editing tools: they do not track changes
  made by Bash commands, external processes or background subagents, and do not restore
  symlinked paths. They do not replace git.

## Status line

`statusLine` in settings or `/statusline`. It receives data as JSON (model, directory, git,
context window and prompt cache fields, cost, duration, limits) and prints one or more
lines — useful for tracking context usage continuously.
