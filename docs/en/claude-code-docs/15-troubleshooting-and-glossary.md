# 15 — Troubleshooting and glossary

Official pages covered:

| Page | Link |
|--------|------|
| Troubleshoot installation and login | <https://code.claude.com/docs/en/troubleshoot-install> |
| Troubleshooting | <https://code.claude.com/docs/en/troubleshooting> |
| Debug your configuration | <https://code.claude.com/docs/en/debug-your-config> |
| Error reference | <https://code.claude.com/docs/en/errors> |
| Glossary | <https://code.claude.com/docs/en/glossary> |
| Changelog | <https://code.claude.com/docs/en/changelog> |
| What's new | <https://code.claude.com/docs/en/whats-new/index> |

---

## Diagnostic flow

| Symptom | First command |
|---------|------------------|
| `claude: command not found` after installing | [Fix your PATH](https://code.claude.com/docs/en/troubleshoot-install#command-not-found-claude-after-installation) |
| Something in the configuration does not take effect | `claude doctor` (outside the session) or `/doctor` (inside) |
| Which settings loaded | `/status` → `Setting sources` line |
| Which `CLAUDE.md` files and rules loaded | `/context` → **Memory files** (or `/memory`) |
| Hooks do not fire | `/hooks` and `claude --debug-file /tmp/claude.log` |
| MCP does not connect | `/mcp`, `claude mcp list`, `claude mcp get <name>` |
| Skill does not trigger | `/skills`, `/skill-doctor`, and strengthen the `description` |
| Broken configuration, unknown cause | `claude --safe-mode` (turns off all customizations) or `--bare` |
| Specific error message | [Error reference](https://code.claude.com/docs/en/errors) |

## Frequent problems and cause

| Problem | Typical cause |
|----------|--------------|
| `CLAUDE.md` ignored | File too large, vague instruction, or conflict between files. If it must always hold, make it a hook |
| `AGENTS.md` does not load | A `CLAUDE.md`/`CLAUDE.local.md` exists on the path, version < v2.1.277, or **Project instructions** set to `claude-md`/`managed-only` |
| Instruction disappears after `/compact` | It only existed in the conversation, or it is a rule with `paths:` that has not matched yet |
| Auto-compact in a loop (thrashing) | A huge file or output refills the context after every summary |
| Permission prompt repeats after "don't ask again" | The allow saved in the local file does not beat a project/managed `ask` |
| Committed key does not apply to the team | The key is user/managed scope, or is waiting on workspace trust |
| Hook JSON has no effect | Something wrote to stdout first (echo in the profile), or field outside `hookSpecificOutput` |
| Stop hook blocking too much | Does not check `stop_hook_active`; cap of 8 consecutive blocks |
| New skill does not appear | Directory created after the session started → `/reload-skills` |
| New subagent does not appear | `agents/` directory created after startup → restart |
| Personal skill does not exist in the routine | Cloud sessions do not read `~/.claude/skills/` |
| Frontmatter field ignored | Wrong name: unknown fields are discarded **without error** |
| High CPU/memory usage, freezes | [Troubleshooting](https://code.claude.com/docs/en/troubleshooting) |

## Diagnostic flags

```bash
claude --debug                      # debug with optional filter: --debug='mcp,startup'
claude --debug-file /tmp/claude.log # writes the log to a known path
claude --safe-mode                  # no CLAUDE.md, skills, hooks, plugins, MCP
claude --bare                       # minimal startup, no auto-discovery
claude doctor                       # read-only diagnostic, without opening a session
claude project purge [path]         # deletes the project's local state
```

## Glossary (official terms)

- **Agentic coding** — workflow where the AI reads files, runs commands and makes changes
  autonomously while you watch, redirect or step away.
- **Agentic harness** — the tools, context management and execution environment that turn a
  language model into a coding agent. Claude Code is the harness; Claude is the model inside
  it.
- **Agentic loop** — gather context → take action → verify results, repeating.
- **AGENTS.md** — project instructions file for coding agents in general; read by Claude when
  there is no `CLAUDE.md`.
- **CLAUDE.md** — persistent instructions, loaded every session **as a user message after the
  system prompt**.
- **Auto memory** — notes Claude writes itself, per repository, in `~/.claude/projects/`.
- **Auto mode** — permission mode where a separate classifier reviews actions in the user's
  place.
- **Bare mode** (`--bare`) — starts without hooks, skills, commands, subagents, plugins, MCP,
  auto memory and `CLAUDE.md`.
- **Bundled skills** — prompt-based playbooks that ship with Claude Code (`/batch`,
  `/code-review`, `/debug`, `/loop`...), unlike built-in commands, which run fixed logic.
- **Channel** — MCP server that pushes events into a running session.
- **Checkpoint** — restore point created at every prompt that starts a turn.
- **`.claude` directory** — where Claude Code reads project configuration; the user
  equivalent is `~/.claude`.
- **Cloud session** — session that runs on cloud infrastructure and keeps going after you
  close the laptop.
- **Command** — reusable instruction invoked with `/name`. Not to be confused with CLI
  subcommands (`claude mcp add`) or the `command` field of a stdio MCP.
- **Compaction** — automatic summary of the conversation when the context window nears the
  limit.
- **Connector** — MCP server added to the claude.ai account instead of configured in Claude
  Code.
- **Context window** — the session's working memory.
- **Dispatch** — task router started from the phone that creates a session in the desktop
  app.
- **Effort level** — controls adaptive reasoning.
- **Extended thinking** — visible reasoning before the answer.
- **Frontmatter** — YAML block at the top of a Markdown file, between `---`; used by skills,
  subagents, output styles and rules.
- **Hook** — handler executed automatically at a lifecycle point; three levels: event,
  matcher and handler.
- **Managed settings** — settings imposed by the organization; user and project settings do
  not override them.
- **MCP / MCP server / MCP Tool Search** — protocol, server and on-demand loading mechanism
  for tool definitions.
- **Non-interactive mode** — `claude -p`, no interactive UI.
- **Output style** — instructions that define the role, tone and format of responses.
- **Subagent** — worker delegated within a session, with its own context, that returns a
  summary.
- **Surface** — each place Claude Code runs (terminal, IDE, desktop, web, Slack, CI).

Full glossary: <https://code.claude.com/docs/en/glossary>.

## Following changes

- [Changelog](https://code.claude.com/docs/en/changelog) — release notes per version.
- [What's new](https://code.claude.com/docs/en/whats-new/index) — weekly summaries.
- `/release-notes` inside the session opens the changelog with a version picker.

Many sentences in this documentation begin with "requires Claude Code v2.1.x or later".
Always check `claude --version` before concluding that a feature does not exist.
