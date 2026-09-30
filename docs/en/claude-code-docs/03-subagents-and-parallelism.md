# 03 — Subagents and parallel work

Official pages covered:

| Page | Link |
|--------|------|
| Run agents in parallel | <https://code.claude.com/docs/en/agents> |
| Create custom subagents | <https://code.claude.com/docs/en/sub-agents> |
| Manage multiple agents with agent view | <https://code.claude.com/docs/en/agent-view> |
| Orchestrate teams of Claude Code sessions | <https://code.claude.com/docs/en/agent-teams> |
| Message your other Claude Code sessions | <https://code.claude.com/docs/en/cross-session-messaging> |
| Orchestrate subagents at scale with dynamic workflows | <https://code.claude.com/docs/en/workflows> |
| Run parallel sessions with worktrees | <https://code.claude.com/docs/en/worktrees> |
| Let Claude coordinate ongoing work with Projects | <https://code.claude.com/docs/en/claude-projects> |

---

## Five ways to work in parallel

| Approach | What it gives | Who coordinates |
|-----------|----------|---------------|
| Subagents | Delegated workers inside one session, with their own context, returning a summary | Claude, turn by turn |
| Agent view (`claude agents`) | One screen to dispatch and monitor background sessions (research preview) | You |
| Agent teams | Coordinated sessions with a shared task list and messaging (experimental, off by default) | A lead agent |
| Projects | One continuous conversation in claude.ai/code or desktop, with parallel threads | Claude |
| Dynamic workflows | A script that runs many subagents and cross-checks results | The script |

Supports that are not "ways to run agents": **worktrees** (a separate git checkout per
session), **cross-session messaging** (Claude delivers notes between your sessions) and
`/batch` (a skill that splits a large change into 5 to 30 subagents, each isolated in a
worktree).

> Running several sessions or subagents multiplies token usage.

## Subagents

Each subagent runs in **its own context window**, with a custom system prompt, specific tool
access and independent permissions. It makes its own requests, which count toward the same
usage limits.

They serve to: preserve context, restrict tools, reuse configurations, specialize behavior
and control cost (by routing to cheaper models).

> Subagent descriptions take up context. Above 15,000 tokens combined (not counting the
> built-in ones), Claude Code warns at startup. Trim the `description`s and move detail into
> the system prompt, which only loads when the subagent runs.

### Built-in

| Agent | Model | Tools | Use |
|--------|--------|-------|-----|
| `Explore` | Inherits from the conversation (at most Opus on the Claude API) | Read-only; Write/Edit denied | Codebase search and analysis |
| `Plan` | Inherits from the conversation | Read-only | Research during plan mode |
| `general-purpose` | `CLAUDE_CODE_SUBAGENT_MODEL` or the conversation's | All available to subagents | Complex multi-step tasks |
| `claude` | Follows the model order | All | Catch-all; default agent of background sessions |
| `statusline-setup` | Sonnet | — | `/statusline` |
| `claude-code-guide` | Haiku | — | Questions about Claude Code itself |

`Explore` and `Plan` **skip** `CLAUDE.md` and the git status snapshot to stay cheap. All the
others load both, unless `omitClaudeMd: true`.

To restrict: `permissions.deny` with the specific type, deny the whole `Agent` tool,
`CLAUDE_CODE_DISABLE_EXPLORE_PLAN_AGENTS=1`, or
`CLAUDE_AGENT_SDK_DISABLE_BUILTIN_AGENTS=1` in headless/SDK.

### Scope and precedence

| Location | Scope | Priority |
|-------|--------|-----------|
| Managed settings | Organization | 1 (highest) |
| `--agents` flag (JSON) | Current session | 2 |
| `.claude/agents/` | Project | 3 |
| `~/.claude/agents/` | All your projects | 4 |
| Plugin `agents/` | Where the plugin is active | 5 (lowest) |

Directories are scanned recursively; identity comes from the `name` field, not the path
(except in plugins, where subfolders become part of the `plugin:folder:name` identifier).
Duplicate names in the same directory load only one, chosen by filesystem read order —
`/doctor` reports it. Plugin subagents ignore `hooks`, `mcpServers` and `permissionMode` for
security.

### Subagent file

```markdown
---
name: code-reviewer
description: Reviews code for quality and best practices
tools: Read, Glob, Grep
model: sonnet
---

You are a code reviewer. When invoked, analyze the code and provide
specific, actionable feedback on quality, security, and best practices.
```

Only `name` and `description` are required. Multi-word fields use camelCase (`maxTurns`,
`disallowedTools`) and an unknown field is ignored without error.

Relevant fields: `tools`, `disallowedTools`, `model` (`sonnet|opus|haiku|fable|<id>|inherit`),
`permissionMode`, `maxTurns`, `skills` (preloads the **full content** of the skills),
`mcpServers`, `hooks`, `memory` (`user|project|local`), `background`, `omitClaudeMd`,
`effort`, `isolation: worktree`, `color`, `initialPrompt`, `experimental.cacheTtl`.

The Markdown body becomes the system prompt. The subagent receives **only** that prompt plus
basic environment details — not Claude Code's system prompt.

Claude Code watches `~/.claude/agents/` and `.claude/agents/` and applies changes without a
restart. Exceptions that require a restart: the first file in an `agents` directory that did
not exist, directories coming from `--add-dir`, and sessions with `--disable-slash-commands`.

### Foreground vs. background

- Foreground blocks the main conversation; permission prompts arrive directly.
- Background runs in parallel; prompts show up in the main session.
- With fork mode on (the default in interactive sessions), subagents go to background.
- Background uses a **smaller** set of built-in tools, except forks and subagents resumed in
  foreground.
- `Ctrl+B` sends a running task to background. `CLAUDE_CODE_DISABLE_BACKGROUND_TASKS=1`
  forces everything into foreground.
- Limit: 20 concurrent subagents per session (`Concurrent subagent limit reached`).

### Conversation fork (`/subtask`)

A fork is a subagent that **inherits the whole conversation**, the same system prompt, the
same tools and the same model — and shares the parent's prompt cache, which makes it cheap
to start. Cost: it loses input isolation.

| | Fork | Regular subagent |
|---|------|-----------------|
| Context | Full conversation | Fresh, only the prompt passed |
| System prompt and tools | Same as the session | From the definition, filtered in background |
| Model | Same as the session | `model` field |
| Prompt cache | Shared | Separate |

Fork mode is on by default in interactive sessions, off in `-p` and in the Agent SDK
(`CLAUDE_CODE_FORK_SUBAGENT=1|0` forces it). To keep fork mode on but stop Claude from
creating forks, deny `Agent(fork)`.

### Usage patterns

- Delegate research: *"use subagents to investigate how our auth handles token refresh"*.
- Adversarial review: a subagent reads only the diff and the criteria, without the reasoning
  that produced the change. Instruct it to report only correctness/requirement gaps,
  otherwise it "finds" problems by construction and leads to over-engineering.
- Combine with skills in both directions: a skill with `context: fork` (the skill is the
  task, the agent is the executor) or a subagent with the `skills` field (the skill is
  preloaded reference).

## Dynamic workflows

When the work goes beyond a few subagents, the plan leaves the model's head and becomes
**code**:

| | Subagents | Skills | Agent teams | Workflows |
|---|---|---|---|---|
| Who decides the next step | Claude, turn by turn | Claude | The lead | The script |
| Where intermediate results live | Claude's context | Context | Shared task list | Script variables |
| Scale | A few tasks per turn | Same | A few long-lived peers | Dozens to hundreds of agents |
| Interruption | Restarts the turn | Restarts the turn | Teammates continue | Resumable in the same session |

- Bundled workflow: `/deep-research <question>` — fan-out of searches, cross-check of
  sources, vote per claim and a cited report (unverified claims are marked as such).
- To create one: ask in the prompt ("write a workflow to..."), use the keyword `ultracode`,
  or `/workflow-authoring` to load the reference.
- `/workflows` opens the progress view: phases, agent count, tokens, time; keys `p` (pause),
  `x` (stop), `r` (restart agent), `s` (save the script as a command), `f` (filter by
  status).
- A saved run becomes its own command and can be distributed in a plugin.
- Typical cases: audit many files for the same problem, keep going until a check passes,
  migrate files in parallel, review every changed file and write a single summary, research
  a topic across many sources.

## Agent teams (experimental)

Sessions coordinated by a lead, with a shared task list and messages between teammates. Off
by default. Points of attention in the documentation: choice of display mode, team size,
task sizing, quality gates through hooks, file conflicts (teams do **not** isolate
teammates in worktrees) and orphaned tmux sessions.

## Worktrees

Each session works in a separate git checkout, so parallel edits do not collide.
`--worktree`, the `EnterWorktree` tool, or `isolation: worktree` on a subagent. The
`.worktreeinclude` file (project root, `.gitignore` syntax) lists gitignored files — such as
`.env` — to copy into each new worktree.

Claude Code prevents commands from an isolated subagent from writing to the main checkout:
it blocks git redirection outside the worktree and refuses commands whose shape does not
allow verifying where git runs.

## Checking work in progress

| What | Command |
|-------|---------|
| Background sessions | `claude agents` (agent view) |
| Anything in background in the session | `/tasks` |
| Workflow runs | `/workflows` |
| Agents addressable by message | `/list-agents` |
