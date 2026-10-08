# 00 — Getting started and how Claude Code works

Official pages covered:

| Page | Link |
|--------|------|
| Overview | <https://code.claude.com/docs/en/overview> |
| Quickstart | <https://code.claude.com/docs/en/quickstart> |
| Changelog | <https://code.claude.com/docs/en/changelog> |
| How Claude Code works | <https://code.claude.com/docs/en/how-claude-code-works> |
| Extend Claude Code | <https://code.claude.com/docs/en/features-overview> |
| Explore the `.claude` directory | <https://code.claude.com/docs/en/claude-directory> |
| Explore the context window | <https://code.claude.com/docs/en/context-window> |
| How Claude Code uses prompt caching | <https://code.claude.com/docs/en/prompt-caching> |
| Manage sessions | <https://code.claude.com/docs/en/sessions> |
| Checkpointing | <https://code.claude.com/docs/en/checkpointing> |

---

## What it is

Claude Code is an agentic coding tool: it reads the codebase, edits files, runs commands
and integrates with development tools. It runs in the terminal, IDE extensions (VS Code,
JetBrains), the desktop app and the web (claude.ai/code), plus CI/CD, Slack and Chrome. All
surfaces use the same engine, so the repo's `CLAUDE.md`, settings and MCP servers apply
everywhere.

### Installation (quick reference)

```bash
# macOS, Linux, WSL
curl -fsSL https://claude.ai/install.sh | bash
# Windows PowerShell
irm https://claude.ai/install.ps1 | iex
# Homebrew (does not auto-update)
brew install --cask claude-code
# WinGet
winget install Anthropic.ClaudeCode
```

Native installs update in the background; Homebrew and WinGet do not. On native Windows,
installing Git for Windows is recommended, otherwise the shell tool becomes PowerShell.
Then: `cd your-project && claude`.

## The agentic loop

Three phases that blend together: **gather context → take action → verify results**,
repeating until the task is done. The model decides the next step from the result of the
previous one, and you can interrupt at any time (`Esc`) or queue a correction by typing
while it works.

Two components hold up the loop:

- **Models** — Sonnet for most tasks, Opus for architectural reasoning. Switch with
  `/model` or `claude --model <name>`.
- **Tools** — without tools the model only answers text. Categories: file operations,
  search, execution, web, and code intelligence (LSP, via a language plugin).

The layer around the model that supplies the tools and manages the context is what the
documentation calls the *agentic harness*.

## What Claude accesses when you run `claude` in a directory

- Files in the directory and subdirectories (others, only with permission).
- The terminal: any command you could run.
- Git state: current branch, uncommitted changes, recent history.
- `CLAUDE.md` (or `AGENTS.md`) and auto memory (first 200 lines or 25 KB of `MEMORY.md`).
- Configured extensions: MCP servers, skills, subagents, Chrome.

## Execution environments

| Environment | Where the code runs | Use |
|----------|--------------------|-----|
| Local | Your machine | Default, full access |
| Cloud | Anthropic VMs or self-hosted | Offload, repos you don't have locally |
| Remote Control | Your machine, controlled from the browser | Web UI with local execution |

## Sessions

Every message, tool use and result is written as JSONL under `~/.claude/projects/`. Before
editing files, Claude snapshots them — that is what makes rewind possible.

- Sessions are **independent**: each new session starts with a clean context. What crosses
  sessions is `CLAUDE.md` and auto memory.
- `claude --continue` / `claude --resume` reopen the same session (same ID, messages
  appended). `--fork-session` or `/branch` copy the history to a new ID.
- Sessions are tied to the directory; to parallelize, use git worktrees.
- Switching branches changes the files Claude sees, but does not erase the conversation
  history.

## Context window

The window holds: conversation history, contents of files read, command output,
`CLAUDE.md`, auto memory, loaded skills and system instructions.

Practical rules:

- `/context` shows what is taking up space; `/context all` breaks down tokens per MCP tool.
- When it fills up, Claude Code clears old tool output first, then summarizes the
  conversation. Requests and key snippets survive; detailed instructions from the start may
  vanish.
- What must persist goes into `CLAUDE.md`, not the conversation.
- To control compaction: a "Compact Instructions" section in `CLAUDE.md`, or
  `/compact focus here`.
- If a single file or output is too large and the context refills after every summary,
  Claude Code stops auto-compacting after a few attempts and shows an error (thrashing).
- MCP tool definitions are **deferred** by default (tool search): only names and server
  instructions consume context until a tool is used.

### Context cost per feature

| Feature | When it loads | What it loads | Cost |
|---------|----------------|---------------|-------|
| `CLAUDE.md` | Session start | Full content | Every request |
| Output style | Start and on switch | Active style's instructions | Every request |
| Skills | Start + on use | Descriptions at start, body on use | Low |
| MCP servers | Start | Tool names; schemas on demand | Low until used |
| Code intelligence | After edits and on demand | Diagnostics and symbols | Low (reduces reads) |
| Subagents | On spawn | Separate window | Isolated |
| Hooks | On trigger | Nothing (runs outside) | Zero, unless it returns output |
| Mods | On event | Nothing, except `context` in `prompt.submit`, `tool.register`, or variable text in `prompt.section` (invalidates the cache) | Zero to low |

## Checkpoints and safety

- File edits are reversible: `Esc Esc` or `/rewind` restore conversation and/or code.
- Checkpoints are independent of git and survive resume; they cover **only** changes made
  by the editing tools (not changes via Bash or external processes) and do not restore
  symlinks/hard links.
- Actions on remote systems (DB, APIs, deploys) are not "checkpointable" — that is
  controlled with permission mode and permission rules.

## Permission modes (quick view)

`Shift+Tab` cycles through them:

- **Auto** — a classifier reviews most actions in the background and blocks risky ones.
  From v2.1.283 it is the default starting mode in interactive terminal and VS Code
  sessions (in earlier versions, only on the Pro, Max and Team plans).
- **Manual** (`default`) — asks before editing files and running commands.
- **Accept edits** — edits files and runs common filesystem commands without asking.
- **Plan** — explores and proposes a plan without editing source code.

## The `.claude/` directory

In the project:

```
your-project/
├── CLAUDE.md              # project instructions (or .claude/CLAUDE.md)
├── .mcp.json              # project MCP servers (root, not inside .claude/)
├── .worktreeinclude       # gitignored files to copy into new worktrees
└── .claude/
    ├── settings.json       # permissions, hooks, statusLine, model, env (committed)
    ├── settings.local.json # personal overrides (gitignored)
    ├── rules/              # per-topic instructions, optionally with paths:
    ├── skills/<name>/SKILL.md
    ├── commands/<name>.md  # legacy format, still works
    └── agents/<name>.md    # project subagents
```

In the home directory (`~/.claude/`): `CLAUDE.md`, `settings.json`, `skills/`, `agents/`,
`rules/`, `plugins/`, `projects/<project>/` (transcripts and `memory/`). `~/.claude.json` is
written by Claude Code itself (login, MCP, per-project state) and should not be edited by
hand.

## Prompt caching (what it preserves and what invalidates it)

Invalidate the cache: switching model, changing effort level, turning on fast mode,
connecting/removing an MCP server, enabling/disabling a plugin, denying a whole tool,
compacting the conversation, accumulating many images, updating Claude Code.

Preserve the cache: editing repository files, editing `CLAUDE.md` mid-session, switching
permission mode, switching output style, invoking skills and commands, `/recap`, rewind.

Default TTL is configurable (`5m` / `1h`); subagents can choose via the
`experimental.cacheTtl` frontmatter.

## Working well (from the guide itself)

- Ask Claude itself: "how do I set up hooks?", "how should I structure my CLAUDE.md?".
- `/init` generates an initial `CLAUDE.md`; `/doctor` runs a setup checkup and proposes fixes.
- It's a conversation, not a perfect prompt: start, correct, iterate.
- Delegate instead of dictating: give context and direction, not the list of files to read.
