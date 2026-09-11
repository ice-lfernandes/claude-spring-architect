# Claude Code ecosystem guide

A practical reference to everything Claude Code offers for extending and controlling
the agent: memory, skills, commands, subagents, hooks, MCP, and plugins. Written with a
focus on software development.

Source: official documentation at <https://code.claude.com/docs/en/overview> (accessed
2026-09-04). Where the docs and this guide diverge, the official docs win.

---

## Summary

1. [Mental model: the pieces of the ecosystem](#1-mental-model-the-pieces-of-the-ecosystem)
2. [Which piece to use for what](#2-which-piece-to-use-for-what)
3. [CLAUDE.md and memory](#3-claudemd-and-memory)
4. [Rules (`.claude/rules/`)](#4-rules-claudrules)
5. [Skills](#5-skills)
6. [Commands (slash commands)](#6-commands-slash-commands)
7. [Subagents](#7-subagents)
8. [Hooks](#8-hooks)
9. [MCP](#9-mcp)
10. [Plugins and marketplaces](#10-plugins-and-marketplaces)
11. [Workflows that work](#11-workflows-that-work)
12. [Context management](#12-context-management)
13. [Anti-patterns](#13-anti-patterns)
14. [How this repository uses all of this](#14-how-this-repository-uses-all-of-this)
15. [Quick command reference](#15-quick-command-reference)

---

## 1. Mental model: the pieces of the ecosystem

Claude Code is an agent: it reads files, edits, runs commands, verifies the result, and
iterates. The pieces below are the extension layer — they change what it knows, what it
can reach, and what happens automatically.

| Piece | What it is | Where it lives |
|---|---|---|
| **CLAUDE.md** | Persistent context, loaded every session | `./CLAUDE.md`, `~/.claude/CLAUDE.md` |
| **Auto memory** | Notes Claude writes itself between sessions | `~/.claude/projects/<proj>/memory/` |
| **Rules** | Modular instructions, optionally scoped by path glob | `.claude/rules/*.md` |
| **Skills** | Knowledge and workflows loaded on demand; become `/name` | `.claude/skills/<name>/SKILL.md` |
| **Commands** | Older single-file skill format | `.claude/commands/<name>.md` |
| **Subagents** | Isolated workers with their own context | `.claude/agents/<name>.md` |
| **Hooks** | Scripts triggered on lifecycle events | `.claude/settings.json` |
| **MCP** | Protocol for connecting external services | `.mcp.json`, `claude mcp add` |
| **Plugins** | Packaging of everything above, distributable | directory with `.claude-plugin/plugin.json` |

One principle runs through everything else: **the context window is the scarce
resource**. It holds the entire conversation — every message, every file read, every
command output. Performance degrades as it fills up. Practically every good practice
below exists to spend less context, or to spend it better.

---

## 2. Which piece to use for what

### Decision table

| Goal | Use |
|---|---|
| Convention that always holds ("use pnpm, not npm") | CLAUDE.md |
| Rule that only applies to certain files (`src/api/**`) | Rule with `paths:` |
| Playbook you've pasted into chat for the third time | Skill |
| Action with side effects you want to trigger by hand (`/deploy`) | Skill with `disable-model-invocation: true` |
| Task that reads 40 files and you just want the summary | Subagent |
| Something that must happen **every time, no exceptions** | Hook |
| Data that lives in an external system (Jira, database, Figma) | MCP |
| Same setup in a second repository | Plugin |

### Triggers: when to add each thing

Don't configure everything up front. Each piece has a recognizable trigger:

| Trigger | Add |
|---|---|
| Claude makes the same mistake about a convention twice | A line in CLAUDE.md |
| You type the same prompt to start a task | An invocable skill |
| You paste the same N-step procedure for the third time | A skill |
| You copy data from a browser tab Claude can't see | An MCP server |
| Claude reads many files just to find where a symbol is defined | A code-intelligence plugin (LSP) |
| A side task floods the conversation with output you won't re-read | A subagent |
| You want something to happen always, without asking | A hook |
| A second repository needs the same setup | A plugin |

### Distinctions that confuse people

**Skill vs. Subagent** — a skill is *reusable content* that enters your context; a
subagent is an *isolated worker* with its own context that returns only a summary.
Skills cost context; subagents save it. They combine: a subagent can preload skills
(the `skills:` field), and a skill can run isolated (`context: fork`).

**CLAUDE.md vs. Skill** — CLAUDE.md loads *every session* (fixed cost); a skill loads
*on demand*. If it's "always do X," it goes in CLAUDE.md. If it's reference material
that only matters sometimes, it becomes a skill.

**Hook vs. Skill** — a hook is deterministic: it always fires on the event, without the
model deciding. A skill is interpreted: the model decides how to apply it. An
instruction like "never edit `.env`" in a CLAUDE.md is a *request*; a `PreToolUse` hook
that blocks the edit is *enforcement*. If the rule must always hold, make it a hook.

**MCP vs. Skill** — MCP provides the tools (the connection and authentication); a skill
provides the knowledge of how to use them well. They combine: MCP connects to the
database, the skill documents the schema and query patterns.

---

## 3. CLAUDE.md and memory

### File hierarchy

Loaded in order, from broadest scope to most specific. All are **additive**, not
replaced:

| Scope | Location | For |
|---|---|---|
| Managed policy | `/Library/Application Support/ClaudeCode/CLAUDE.md` (macOS), `/etc/claude-code/CLAUDE.md` (Linux) | Organization standards, via MDM |
| User | `~/.claude/CLAUDE.md` | Your preferences, all projects |
| Project | `./CLAUDE.md` or `./.claude/CLAUDE.md` | Team, versioned in git |
| Local | `./CLAUDE.local.md` | Personal to the project, in `.gitignore` |

Files in the current directory **and every directory above it** load at startup. Files
in subdirectories load on demand, when Claude reads something in there.

### Writing an effective CLAUDE.md

Target: **under 200 lines**. A bloated file makes Claude ignore half of it — the
important rules get lost in the noise. For every line, ask: *"would removing this make
Claude make a mistake?"* If not, cut it.

| ✅ Include | ❌ Exclude |
|---|---|
| Build commands it can't guess | Anything it can discover by reading the code |
| Style rules that diverge from the standard | Standard language conventions |
| Test instructions and the preferred runner | Detailed API documentation (link instead) |
| Repo etiquette (branches, PRs) | Information that changes frequently |
| Project-specific architectural decisions | Long explanations or tutorials |
| Environment quirks (required env vars) | File-by-file code description |
| Pitfalls and non-obvious behaviors | Obvious statements like "write clean code" |

Diagnostic signs:

- Claude repeats a mistake despite the rule existing → the file is too long, the rule
  got lost.
- Claude asks something that's already in CLAUDE.md → the phrasing is ambiguous.
- Need to emphasize? Use `IMPORTANT` **on a single line**. Emphasize ten things and
  none stand out.

Treat it like code: review when something goes wrong, review regularly, version it in
git.

### Imports

```markdown
See @README for an overview and @package.json for the npm commands.

# Additional instructions
- git workflow @docs/git-instructions.md
```

Relative paths resolve from the file that imports them. Maximum depth: 4 hops. To cite
a path **without** importing it, use backticks: `` `@README` ``.

Import doesn't reduce context — the imported file loads alongside it at startup. It's
for organization, not savings. To save context, use rules with `paths:` or skills.

### AGENTS.md

Claude Code reads `CLAUDE.md`, not `AGENTS.md`. If the repository already uses
`AGENTS.md` for other agents:

```markdown
@AGENTS.md

## Claude Code
Use plan mode for changes to `src/billing/`.
```

### Auto memory

Claude writes its own notes between sessions, in
`~/.claude/projects/<project>/memory/`. Four types: `user` (who you are), `feedback`
(corrections you've given), `project` (work in progress), `reference` (external links).
It skips whatever can be derived from the code.

`MEMORY.md` is the index — the first 200 lines (or 25KB) load every session. Topic
files load on demand.

Enabled by default. To disable: `/memory` → toggle, or `autoMemoryEnabled: false` in
settings, or `CLAUDE_CODE_DISABLE_AUTO_MEMORY=1`.

### Useful commands

- `/init` — generates an initial CLAUDE.md from the existing code.
- `/memory` — lists and opens the memory files.
- `/context` — shows what **actually** loaded in this session. Use it to diagnose.
- `/doctor` — checkup of the Claude Code setup: diagnoses and fixes configuration
  issues. Native; unrelated to this repo's `/arch-doctor`.

---

## 4. Rules (`.claude/rules/`)

For large projects, break instructions into topic files:

```
.claude/
├── CLAUDE.md
└── rules/
    ├── code-style.md
    ├── testing.md
    └── security.md
```

All `.md` files are discovered recursively. Rules **without** a `paths` frontmatter
load at startup, at the same priority as `.claude/CLAUDE.md`.

### Path-scoped rules

The real gain is here: the rule only enters context when Claude touches matching
files.

```markdown
---
paths:
  - "src/api/**/*.ts"
  - "lib/**/*.{ts,tsx}"
---

# API rules

- Every endpoint validates its input
- Use the standard error response format
```

| Pattern | Matches |
|---|---|
| `**/*.ts` | Every TypeScript file, any directory |
| `src/**/*` | Everything under `src/` |
| `*.md` | Markdown at the root |
| `src/components/*.tsx` | Components in a specific directory |

Rules can be symlinks — useful for sharing a set across projects:

```bash
ln -s ~/company-standards/security.md .claude/rules/security.md
```

Personal rules in `~/.claude/rules/` apply to every project and load **before** the
project's own (the project's take priority).

---

## 5. Skills

The most flexible extension. A markdown file with instructions that becomes part of
Claude's repertoire. It uses it when relevant, or you invoke it with
`/skill-name`.

Create a skill when you find yourself pasting the same checklist or procedure into
chat repeatedly, or when a CLAUDE.md section has turned into a procedure rather than a
fact. Unlike CLAUDE.md, the skill's body only loads when it's used — long reference
material costs almost nothing until it's needed.

### Structure

```
.claude/skills/deploy-staging/
├── SKILL.md          # required
├── reference.md      # loaded only when needed
├── examples.md
└── scripts/
    └── helper.py     # executed, not loaded into context
```

The directory name becomes the command: `/deploy-staging`. Keep `SKILL.md` under
**500 lines**; move detailed reference material to separate files and cite them:

```markdown
## Additional resources
- Full API details: [reference.md](reference.md)
- Usage examples: [examples.md](examples.md)
```

### Code boilerplate (scaffolders)

Example source code — the base class a skill emits, a reference `pom.xml`, an
exception handler — goes in `templates/`, **inside the skill's directory**. Never in
`rules/`, never pasted into the body of `SKILL.md`.

The reason is mechanical, not aesthetic:

| | `.claude/rules/` | `.claude/skills/<name>/` | `SKILL.md` body |
|---|---|---|---|
| What the runtime discovers there | Only `.md`, loaded **as instruction** | The whole directory, any file type | — |
| When it enters context | Without `paths`: every session. With `paths`: whenever a matching file is touched | Only when the skill reads the file | Every skill invocation |
| Nature | Norm — what must be true | Procedure + supporting material | Permanent task instruction |

A `.java` file inside `rules/` is dead code: it's not `.md`, nothing loads it.
Renaming it to `.md` and pasting the code inside is worse — it becomes a permanent
instruction every time the glob matches, for code that only matters at generation
time.

The resulting split has two distinct owners:

- **Rule** declares the norm: *"exceptions inherit from `DomainException`; never a
  bare `RuntimeException`"*. Applies when someone **writes or reviews** code.
- **Skill template** carries the exemplar: `DomainException.java.example`, real,
  compilable code. Applies when someone **generates** code.

The norm doesn't repeat the code; the exemplar doesn't repeat the norm. Each cites the
other by path.

| Convention | Why |
|---|---|
| `.example` (or `.template`) suffix in the name | The file isn't compiled or tested as part of this repository. Without the suffix, the build and linter try to process it |
| A single naming pattern across all exemplars | `X.java.example` and `pom.example.xml` side by side turn a precondition `ls` into a false negative |
| A real, compilable exemplar, not a mold with `${placeholder}` | Claude reads the shape and writes the adapted equivalent. Mechanical substitution breaks on the case the mold didn't anticipate — a `domain` module's `pom.xml` shouldn't get `spring-boot-starter-web` just because another module's exemplar shows it |
| One file per concept | Updating one exemplar doesn't force reviewing the others, and the precondition check points exactly at what's missing |
| Cited by `SKILL.md` via path | The skill body loads on every invocation; the exemplar only at the step that needs it |

A third case, outside skills: boilerplate that no skill generates and a human copies
by hand (archetype, module seed). It's neither skill nor rule — it's `templates/` at
the project root, versioned as regular code, cited in `CLAUDE.md` between backticks
(no `@`, so it doesn't get imported).

### Where they live

| Location | Path | Applies to |
|---|---|---|
| Personal | `~/.claude/skills/<name>/SKILL.md` | All your projects |
| Project | `.claude/skills/<name>/SKILL.md` | Only this project |
| Plugin | `<plugin>/skills/<name>/SKILL.md` | Wherever the plugin is active |

Name conflict resolution: managed > personal > project. Plugin skills are namespaced
(`/my-plugin:deploy`), so they never collide.

Skills also load from `.claude/skills/` nested below the working directory — useful in
a monorepo. They become available the first time Claude reads or edits a file in that
subdirectory.

**Live detection:** editing a `SKILL.md` is picked up in the current session, no
restart needed. Creating a `skills/` directory that didn't exist at session start
requires a restart.

### Frontmatter

All fields are optional; only `description` is recommended.

```yaml
---
name: deploy
description: Deploys the application to production. Use when the user asks for a deploy.
argument-hint: "[environment]"
disable-model-invocation: true
allowed-tools: Bash(git *) Read
context: fork
agent: general-purpose
paths: "src/**/*.java"
model: sonnet
---
```

Main fields:

| Field | Effect |
|---|---|
| `description` | How Claude decides to use the skill. **Put the main use case first** — the text is truncated at 1,536 characters in the listing |
| `when_to_use` | Extra context: trigger phrases, example requests |
| `argument-hint` | Autocomplete hint, e.g. `[issue-number]` |
| `arguments` | Positional names for `$name` substitution |
| `disable-model-invocation` | `true` = only you invoke it. Use for side effects (`/deploy`, `/commit`) |
| `user-invocable` | `false` = only Claude invokes it. Use for background knowledge |
| `allowed-tools` | Pre-approves tools **during the turn** that invokes the skill |
| `disallowed-tools` | Removes tools from the pool while the skill is active |
| `model` / `effort` | Overrides model/effort while the skill is active |
| `context: fork` | Runs the skill in an isolated subagent |
| `agent` | Which subagent type to use with `context: fork` |
| `background` | With `fork`, `false` = wait for the result in the same turn |
| `paths` | Globs that limit when the skill auto-activates |
| `hooks` | Hooks registered when the skill is invoked |

### Arguments

```markdown
---
name: fix-issue
description: Fixes a GitHub issue
disable-model-invocation: true
---

Fix issue $ARGUMENTS following our standards.
```

`/fix-issue 123` → Claude receives "Fix issue 123...".

| Variable | Meaning |
|---|---|
| `$ARGUMENTS` | All arguments, as typed |
| `$0`, `$1`, `$2` | Argument by position (equivalent to `$ARGUMENTS[N]`) |
| `$name` | Named argument, declared in `arguments:` |
| `${CLAUDE_SKILL_DIR}` | Directory of the `SKILL.md` — use for embedded scripts |
| `${CLAUDE_PROJECT_DIR}` | Project root |
| `${CLAUDE_SESSION_ID}` | Session ID, useful for logs |
| `${CLAUDE_EFFORT}` | Current effort level |

Skills can be stacked: `/write-tests /fix-issue 123` loads both and passes `123` as
the argument to each.

### Dynamic context injection

The `` !`command` `` syntax runs the command **before** the content reaches Claude and
substitutes its output. This is what lets the skill work on real data, not on what the
model imagines:

```markdown
---
name: summarize-changes
description: Summarizes uncommitted changes and flags risks.
---

## Current changes

!`git diff HEAD`

## Instructions

Summarize the above in 2-3 bullets and list risks: missing error handling,
hardcoded values, tests that need to change.
```

Multi-line block:

````markdown
```!
node --version
git status --short
```
````

Watch out for:

- A command that fails (exit ≠ 0) **aborts the entire skill invocation**. Append
  `|| true` to commands that legitimately exit non-zero.
- Default timeout: 2 minutes.
- The command never asks for permission. If the permission check doesn't return
  "allow," the invocation aborts — pre-approve with `allowed-tools`.
- `!` is only recognized at the start of a line or after a space.

### Running the skill isolated (`context: fork`)

```yaml
---
name: deep-research
description: Researches a topic in depth
context: fork
agent: Explore
---

Research $ARGUMENTS in depth:
1. Find files with Glob and Grep
2. Read and analyze the code
3. Summarize with file:line references
```

The skill's content becomes the subagent's prompt. It does **not** see the
conversation history. Runs in background by default; the result arrives when it's
done.

⚠️ `context: fork` only makes sense for skills with actionable instructions. A skill
that just says "use these API conventions" without a task gives the subagent nothing
to do.

### Content lifecycle

When invoked, the rendered `SKILL.md` enters the conversation as a message and **stays
there for subsequent turns**. Claude doesn't re-read the file afterward. So write
permanent instructions ("always do X throughout this task"), not one-time steps. And
keep the body lean — every line is a recurring cost.

Unlike the content, `allowed-tools` permission **expires** on your next message.

### Bundled skills

Ship with Claude Code, invoked like any skill:

| Skill | For |
|---|---|
| `/code-review [level] [--fix] [--comment] [pr#]` | Reviews the diff or PR: correctness bugs and simplifications |
| `/verify` | Builds and runs the app to confirm the change actually works |
| `/run` | Launches and drives the app to see the change working |
| `/run-skill-generator` | Teaches `/run` and `/verify` to build and launch this project |
| `/batch <instruction>` | Distributes a large change across 5-30 subagents, each with its own PR |
| `/loop [interval] [prompt]` | Repeats a prompt while the session is open |
| `/doctor` | Setup checkup: diagnoses and fixes configuration issues |
| `/fewer-permission-prompts` | Analyzes transcripts and proposes a permission allowlist |
| `/deep-research <question>` | Fan-out web searches with cross-verification and a cited report |

### Diagnostics

| Symptom | Likely cause / fix |
|---|---|
| Skill doesn't fire | `description` missing the words you actually use. Test with "what skills exist?" |
| Skill fires too often | `description` too generic, or missing `disable-model-invocation: true` |
| `/name` works but Claude never picks it | Malformed YAML: the body loads, the metadata doesn't. Run with `--debug` |
| Truncated descriptions | Too many skills competing for budget (1% of the window). Use `skillOverrides: "name-only"` |

Validation: `claude plugin validate .claude/skills`.

---

## 6. Commands (slash commands)

**Custom commands have been merged with skills.** `.claude/commands/deploy.md` and
`.claude/skills/deploy/SKILL.md` both create `/deploy` and work the same way. Files in
`.claude/commands/` keep working; skills add a directory for supporting files and
automatic invocation by the model.

Differences:

| | `.claude/commands/x.md` | `.claude/skills/x/SKILL.md` |
|---|---|---|
| Command name | File name | Directory name |
| Supporting files | No | Yes |
| Frontmatter | Same, except `name` and `paths` (ignored) | Full |
| Invoked by the model | Yes | Yes |

**Recommendation:** use `skills/` for new things. Keep `commands/` only for what
already exists as a simple file.

Behavior notes:

- Commands are only recognized **at the start** of the message.
- The text after the name becomes the arguments.
- Up to six skills can be chained in one message.

---

## 7. Subagents

Specialized assistants that run in an **isolated context window**, with their own
system prompt, their own tool access, and an optional model.

The core benefit: keeping exploration, logs, and file content **out** of your main
conversation. The subagent works independently and returns only a summary.

### Format

```markdown
---
name: security-reviewer
description: Reviews code for vulnerabilities. Use after changes to auth.
tools: Read, Grep, Glob, Bash
model: opus
---

You are a senior security engineer. Review the code looking for:
- Injection (SQL, XSS, command)
- Authentication and authorization flaws
- Secrets or credentials in the code

Give specific line references and suggested fixes.
```

The frontmatter defines the metadata; the markdown body becomes the **system prompt**.

### Full frontmatter

| Field | Required | Effect |
|---|---|---|
| `name` | ✅ | Identifier, lowercase and hyphens. Cannot contain `:` |
| `description` | ✅ | When to delegate. Short — all descriptions combined have a 15k-token ceiling |
| `tools` | ➖ | Comma-separated list. Without the field, inherits everything |
| `disallowedTools` | ➖ | Removes from the inherited list. Accepts `mcp__*` |
| `model` | ➖ | `sonnet`, `opus`, `haiku`, full ID, or `inherit` |
| `permissionMode` | ➖ | `default`, `acceptEdits`, `auto`, `dontAsk`, `bypassPermissions`, `plan` |
| `maxTurns` | ➖ | Maximum turns before stopping |
| `skills` | ➖ | Skills preloaded **in full** at startup |
| `mcpServers` | ➖ | MCP servers only for this subagent |
| `hooks` | ➖ | Hooks only for this subagent |
| `memory` | ➖ | Persistent memory: `user`, `project`, or `local` |
| `background` | ➖ | `true` keeps it in background even if Claude asks for foreground |
| `effort` | ➖ | `low`…`max` |
| `isolation` | ➖ | `worktree` = runs in an isolated git worktree |
| `color` | ➖ | Display color |

### Where they live (priority order)

1. Managed settings (organization)
2. `--agents` flag (current session only)
3. `.claude/agents/` (project — version it in git for the team)
4. `~/.claude/agents/` (personal, all projects)
5. Plugin `agents/` (namespaced: `my-plugin:agent`)

Both directories are scanned recursively; subdirectories don't affect the name.
Changes are detected within seconds, no restart needed — except when **creating** the
`agents/` directory for the first time.

### How to invoke

```text
# 1. Automatic delegation — Claude decides from the description
Use the code-improver agent to suggest improvements to this project

# 2. @-mention — guarantees execution
@agent-security-reviewer audit the auth changes

# 3. Entire session as the subagent
claude --agent code-reviewer

# 4. One-off definition
claude --agents '{"reviewer": {"description": "...", "prompt": "...", "tools": ["Read"]}}'
```

### What the subagent sees (and doesn't)

**Doesn't see:** conversation history, main session's auto memory, what you read
before.

**Sees:** its own system prompt, the CLAUDE.md hierarchy (except Explore/Plan), git
status (except Explore/Plan), the delegation message, preloaded skills.

### Forks

A *fork* is a subagent that **inherits the entire conversation** instead of starting
from scratch:

```text
/subtask write unit tests for the parser changes so far
```

Sees the full history, system prompt, tools, and model. Only the final result returns
to the main conversation — tool calls stay out of your context.

### Bundled subagents

| Name | For |
|---|---|
| `Explore` | Fast, read-only search and analysis. Skips CLAUDE.md and git status |
| `Plan` | Research for plan mode. Read-only |
| `general-purpose` | Complex multi-step tasks, all tools |
| `claude` | Catch-all with every tool |

### Limits

- **Depth:** 3 levels of nesting by default
  (`CLAUDE_CODE_MAX_SUBAGENT_SPAWN_DEPTH`).
- **Concurrency:** 20 simultaneous (`CLAUDE_CODE_MAX_CONCURRENT_SUBAGENTS`).
- **Descriptions:** combined ceiling of 15,000 tokens for custom subagents.

### Cost control

```json
{
  "env": {
    "CLAUDE_CODE_SUBAGENT_MODEL": "haiku",
    "CLAUDE_CODE_SUBAGENT_MODEL_FORCE": "1"
  }
}
```

Haiku for read-only work (search, reading, summarizing) is cheap and effective.
Reserve Opus for complex reasoning.

### Persistent memory

```yaml
---
name: code-reviewer
description: Reviews code
memory: project
---

While reviewing, update your memory with patterns, conventions, and recurring issues.
```

| Scope | Location |
|---|---|
| `user` | `~/.claude/agent-memory/<name>/` |
| `project` | `.claude/agent-memory/<name>/` — shareable via git |
| `local` | `.claude/agent-memory-local/<name>/` — don't commit |

### Files silently ignored

Claude Code **skips** a subagent file when: it has no `name` field (treated as
documentation); the opening `---` isn't on the first line; `name` starts with `-` or
contains `:`; it has `name` but no `description`; the YAML doesn't parse.

Check beforehand: `claude plugin validate ~/.claude/agents`.

---

## 8. Hooks

Scripts (or HTTP requests, MCP calls, prompts, subagents) triggered on lifecycle
events. **Deterministic**: they always happen on the event, regardless of what the
model decides. That's the difference between asking and guaranteeing.

### Events

- **Once per session:** `SessionStart`, `SessionEnd`
- **Once per turn:** `UserPromptSubmit`, `Stop`, `StopFailure`
- **On every tool call:** `PreToolUse`, `PostToolUse`, `PostToolUseFailure`,
  `PermissionRequest`, `PermissionDenied`
- **Others:** `SubagentStart`, `SubagentStop`, `PreCompact`, `PostCompact`,
  `Notification`, `FileChanged`, `InstructionsLoaded`, `TaskCreated`, `TaskCompleted`,
  `WorktreeCreate`, `ConfigChange`, `CwdChanged`, among others

### Configuration

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Write|Edit",
        "hooks": [
          {
            "type": "command",
            "command": "prettier",
            "args": ["--write", "${tool_input.file_path}"],
            "timeout": 30
          }
        ]
      }
    ]
  }
}
```

Matchers: `"*"` or omitted matches everything; plain text matches exactly or a list
(`Edit|Write`); other characters become a JavaScript regex (`^Notebook`, `mcp__.*`).

**Exec form** (with `args`): direct executable, no shell — portable across Linux,
macOS, and Windows, and doesn't need an execute bit. **Shell form** (without `args`):
string passed to the shell, with expansion and quoting.

### Exit codes

| Code | Behavior |
|---|---|
| `0` | Success. Output JSON is parsed if valid |
| `2` | **Blocks the action** (on events that support it). stderr becomes the reason |
| Other | Non-blocking error; the action proceeds |

Where `2` blocks: `PreToolUse` (blocks the call), `UserPromptSubmit` (rejects the
prompt), `Stop` (forces continuation), `PostToolBatch` (stops the loop). In
`PostToolUse` it doesn't block — the tool already ran — but stderr is shown to Claude.

### Structured JSON output

```json
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Blocked by policy",
    "additionalContext": "Extra context for Claude",
    "systemMessage": "Message visible to the user"
  }
}
```

### Example: blocking edits to protected files

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          {
            "type": "command",
            "if": "Edit(**/db/migration/*.sql)",
            "command": "bash",
            "args": ["-c", "echo 'Applied migrations are not edited' >&2; exit 2"]
          }
        ]
      }
    ]
  }
}
```

The `if` field filters by permission rule before running the hook — avoids spawning a
process for every edit.

### Where to configure

| Location | Scope | Shareable |
|---|---|---|
| `~/.claude/settings.json` | All projects | No |
| `.claude/settings.json` | This project | Yes (version it) |
| `.claude/settings.local.json` | This project | No (gitignore) |
| `<plugin>/hooks/hooks.json` | Wherever the plugin is active | Yes |
| Skill/subagent frontmatter | During invocation | Yes |

### Tips

- Ask Claude to write the hook: *"write a hook that runs eslint after every file
  edit"*.
- `/hooks` shows what's configured.
- Debug: `CLAUDE_CODE_DEBUG=hooks claude`.
- Path placeholders: `${CLAUDE_PROJECT_DIR}`, `${CLAUDE_PLUGIN_ROOT}`.

---

## 9. MCP

Model Context Protocol: an open standard that connects Claude to external tools,
databases, and APIs — without you copying and pasting data into the chat.

### Adding servers

```bash
# Remote HTTP
claude mcp add --transport http notion https://mcp.notion.com/mcp

# With an auth header
claude mcp add --transport http github https://api.githubcopilot.com/mcp/ \
  --header "Authorization: Bearer YOUR_TOKEN"

# Local process (stdio). The -- separates the option flags from the server command
claude mcp add --env API_KEY=value --transport stdio myserver \
  -- npx -y @example/mcp-server
```

### Scopes

| Scope | Location | Shared |
|---|---|---|
| `local` (default) | `~/.claude.json` | No |
| `project` | `.mcp.json` | Yes, via git |
| `user` | `~/.claude.json` | No, but applies to all projects |

Precedence: local > project > user.

```json
{
  "mcpServers": {
    "database": {
      "type": "stdio",
      "command": "python",
      "args": ["db-server.py"],
      "env": { "DB_URL": "${DB_URL:-localhost:5432}" }
    }
  }
}
```

Variable expansion (`${VAR}`, `${VAR:-default}`) works in `command`, `args`, `env`,
`url`, and `headers`. An unset variable with no default doesn't fail startup: the server
loads with the literal `${VAR}` text and fails to connect: `claude mcp list` surfaces the
warning.

### Credentials beyond a static header

- **`oauth`** — `clientId`, `callbackPort`, `authServerMetadataUrl`, `scopes`. Lets
  `claude mcp add`/`login` drive an OAuth 2.0 flow instead of a token pasted into
  `headers`.
- **`headersHelper`** — path to a script; its stdout (JSON) supplies headers at connect
  time. For Kerberos, SSO, or any credential that can't sit still in a file. For a
  `project`- or `local`-scope server this only runs after the workspace-trust prompt is
  accepted.
- **`alwaysLoad`** — `true` keeps a server's tools loaded even under tool search, instead
  of deferring them until first use.

### Managing

```bash
claude mcp list                    # servers and status, including pending approval
claude mcp get notion              # details
claude mcp remove notion
claude mcp login sentry            # OAuth
claude mcp reset-project-choices   # forget this project's per-server approve/reject choices
```

In-session: `/mcp` to view status, toggle, and authenticate.

### Project-scope approval

The first time Claude Code sees a server declared in a project's `.mcp.json`, it prompts
for approval — the trust boundary that stops a cloned repository from launching
processes on your machine without consent. Settings that affect this, in
`.claude/settings.json`:

| Key | For what |
|---|---|
| `enabledMcpjsonServers` | Array — pre-approves specific `.mcp.json` servers by name |
| `disabledMcpjsonServers` | Array — rejects specific `.mcp.json` servers by name |
| `enableAllProjectMcpServers` | Boolean — approves every `.mcp.json` server with no prompt at all. Removes the one human checkpoint the mechanism exists for |
| `allowedMcpServers` / `deniedMcpServers` | Organization-level allow/deny list (managed settings) |
| `managedMcpServers` | Organization-provided servers, alongside whatever the user adds |

### Tool names

```
mcp__<server>__<tool>     e.g.: mcp__github__create_pr
```

Use this pattern in permission rules and in `disallowedTools`.

### Cost and security

Tool search is on by default: only the **names** load at startup; full schemas are
deferred until use. `/context all` shows how many tokens each loaded MCP tool
consumes.

⚠️ An MCP server can read files, call APIs, and **inject prompts**. Only connect
servers you trust. Prefer OAuth over a token pasted into config.

### Cheaper alternative: CLI

Before connecting an MCP, consider whether a CLI solves it. `gh`, `aws`, `gcloud`,
`sentry-cli` are the **most context-efficient** way to talk to external services, and
Claude already knows how to use them. For a CLI it doesn't know:
*"use `foo-cli --help` to learn the tool, then solve A, B, C"*.

---

## 10. Plugins and marketplaces

Packaging: a plugin bundles skills, subagents, hooks, and MCP servers into a single
installable, versioned unit.

| Approach | Skill name | Best for |
|---|---|---|
| Standalone (`.claude/`) | `/deploy` | Personal workflow, customizing this project, quick experiment |
| Plugin | `/plugin:deploy` | Sharing with the team, distributing, versioning, reusing across repos |

Start standalone; convert to a plugin when a **second repository** needs the same
setup.

### Structure

```
my-plugin/
├── .claude-plugin/
│   └── plugin.json      # only the manifest goes here
├── skills/
│   └── review/SKILL.md
├── agents/
├── hooks/hooks.json
├── .mcp.json
├── .lsp.json            # language servers (code intelligence)
├── monitors/monitors.json
├── bin/                 # executables added to PATH
└── settings.json        # defaults applied when the plugin activates
```

⚠️ **Common mistake:** don't put `skills/`, `agents/`, or `hooks/` **inside**
`.claude-plugin/`. Only `plugin.json` goes there; everything else lives at the
plugin's root.

```json
{
  "name": "my-plugin",
  "description": "What this plugin does",
  "version": "1.0.0",
  "author": { "name": "Your Name" }
}
```

### Developing and testing

```bash
claude plugin init my-tool          # scaffold in ~/.claude/skills/my-tool/
claude --plugin-dir ./my-plugin     # load without installing
claude --plugin-dir ./p1 --plugin-dir ./p2
claude plugin validate ./my-plugin  # validate before distributing
```

`/reload-plugins` reloads without restarting the session.

### Installing

```
/plugin                                          # browse the marketplace
/plugin marketplace add anthropics/claude-plugins-official
/plugin install skill-creator@claude-plugins-official
/plugin uninstall <plugin>@<marketplace>
```

Two public marketplaces: `claude-plugins-official` (curated by Anthropic, registered
automatically) and `claude-community` (third-party submissions, after review).

For your team: host the marketplace in a **private repository**.

### A plugin useful for skill authors

`skill-creator` automates skill evaluation: generates test cases in
`evals/evals.json`, runs each one in an isolated subagent, A/B tests between versions,
measures the `description`'s hit rate, and produces an HTML report. Worth it for any
skill the team is going to depend on.

---

## 11. Workflows that work

### Give Claude a way to verify its own work

**The highest-impact practice.** Claude stops when the work *looks* done. Without a
check, "looks done" is the only signal available — and you become the verification
loop: every error waits for you to notice.

Give it something that returns pass/fail and the loop closes itself: a test suite, a
build exit code, a linter, a script that compares output to a fixture, a screenshot
compared against the design.

| Strategy | Before | After |
|---|---|---|
| Verification criterion | *"implement email validation"* | *"write validateEmail. cases: user@example.com true, invalid false, user@.com false. run the tests after implementing"* |
| Visual verification | *"make the dashboard better"* | *"[screenshot] implement this design. take a screenshot of the result, compare it to the original, list differences and fix them"* |
| Root cause | *"the build is broken"* | *"the build fails with: [error]. fix it and verify. attack the root cause, don't suppress the error"* |

Escalation by rigor:

1. **In the prompt** — ask it to run the check and iterate in the same message.
2. **In the session** — `/goal <condition>`: a separate evaluator re-checks every
   turn.
3. **As a deterministic gate** — a `Stop` hook that runs the script and blocks the end
   of the turn until it passes.
4. **Second opinion** — a verification subagent: whoever did the work isn't who checks
   the proof.

Ask for **evidence**, not a claim: the test output, the command run, and what it
returned. Reading evidence is faster than re-running the check yourself.

### Explore → plan → code → commit

Letting Claude jump straight into code produces code that solves the wrong problem.

1. **Explore** — `Shift+Tab` until `⏸ plan mode on`, or `claude --permission-mode plan`.
   *"read /src/auth and understand how we handle sessions and login"*.
2. **Plan** — *"I want to add Google OAuth. which files change? what's the session
   flow? create a plan."* `Ctrl+G` opens the plan in your editor to edit.
3. **Implement** — exit plan mode and execute, checking against the plan.
4. **Commit** — *"commit with a descriptive message and open a PR"*.

Plan mode has a cost. For a clear scope and a small fix (typo, log, rename), ask
directly. **If you can describe the diff in one sentence, skip the plan.**

### Let Claude interview you

For large features, starting with a minimal prompt and letting it ask questions
produces better specs than writing the spec yourself:

```text
I want to build [brief description]. Interview me in detail using the
AskUserQuestion tool.

Ask about technical implementation, UI/UX, edge cases, concerns, and trade-offs.
Don't ask obvious questions, dig into the hard parts I might not have considered.

Keep interviewing until we've covered everything, then write the full spec into SPEC.md.
```

Then **open a new session** to execute: clean context, focused only on the
implementation, with the spec written down to consult.

A good spec names the files and interfaces involved, states what's **out** of scope,
and ends with an end-to-end verification step.

### Specific context in the prompt

| Strategy | Before | After |
|---|---|---|
| Scope the task | *"add tests for foo.py"* | *"write a test for foo.py covering the logged-out user case. avoid mocks."* |
| Point to the source | *"why is this API weird?"* | *"look at ExecutionFactory's git history and summarize how the API got to this state"* |
| Reference patterns | *"add a calendar widget"* | *"see how the home page widgets are implemented. HotDogWidget.php is a good example. follow the pattern for..."* |
| Describe the symptom | *"fix the login bug"* | *"login fails after session timeout. look at src/auth/, especially token refresh. write a test that reproduces it, then fix it"* |

A vague prompt has its place when you're exploring: *"what would you improve in this
file?"* surfaces things you wouldn't have thought to ask.

### Writer/Reviewer pattern

Fresh context improves review — Claude isn't biased by code it just finished writing.

| Session A (Writer) | Session B (Reviewer) |
|---|---|
| `Implement rate limiting on the endpoints` | |
| | `Review @src/middleware/rateLimiter.ts. Look for edge cases, race conditions, and consistency with existing middleware.` |
| `Here's the feedback: [B's output]. Address it.` | |

Works the same way for tests: one session writes the tests, another writes the code
that passes them.

### Adversarial review before calling it done

```text
Use a subagent to review the rate limiter diff against PLAN.md. Verify that every
requirement was implemented, that the listed edge cases have tests, and that nothing
outside the scope changed. Report gaps, not style preferences.
```

⚠️ A reviewer instructed to find gaps will almost always find one, even in solid work
— it's what you asked for. Chasing every observation leads to over-engineering: extra
abstraction layers, defensive code, tests for impossible cases. Instruct the reviewer
to flag **only** what affects correctness or the stated requirements.

### Non-interactive mode and CI

```bash
claude -p "Explain what this project does"
claude -p "List all endpoints" --output-format json
claude -p "Analyze this log" --output-format stream-json --verbose

# Pipes
tail -200 app.log | claude -p "let me know if there are anomalies"
git diff main --name-only | claude -p "review these files for security issues"
```

### Fan-out across many files

For large migrations, in a git repository use `/batch <instruction>` — Claude
splits the change across 5 to 30 subagents, each in its own worktree opening a PR.

To drive by script:

```bash
for file in $(cat files.txt); do
  claude -p "Migrate $file from Python 2 to 3. Answer OK or FAIL." \
    --allowedTools "Edit,Bash(git commit *)"
done
```

Refine the prompt on the first 2-3 files before releasing it on the whole set.
`--allowedTools` limits the damage in an unsupervised run.

### Parallel sessions

- **Worktrees** — CLI sessions in isolated git checkouts, no edit collisions.
- **Desktop app** — multiple local sessions, each in its own worktree, visually.
- **Claude Code on the web** — cloud sessions, for long-running tasks.
- **Cross-session messaging** — sessions pass findings to each other.

---

## 12. Context management

The window holds the entire conversation. A single debugging session can consume tens
of thousands of tokens, and performance drops as it fills up.

### Tools

| Command | Effect |
|---|---|
| `/clear` | Resets the context. Use **between unrelated tasks** |
| `/compact [instructions]` | Summarizes the conversation to free up space. `/compact focus on the API changes` |
| `/context` | Shows current usage as a colored grid |
| `/context all` | Breaks down cost per loaded MCP tool |
| `Esc` | Stops Claude mid-action, preserving context |
| `Esc Esc` or `/rewind` | Rewinds conversation and/or code to a checkpoint |
| `/btw <question>` | Side question whose answer does **not** enter the history |
| `/usage` | Token statistics |

### Practical rules

- **`/clear` between unrelated tasks.** Junk-drawer session is the most common
  anti-pattern.
- **After two failed fixes at the same spot, `/clear`.** The context is polluted with
  the approaches that didn't work. A clean session with a better prompt almost always
  beats a long session with accumulated fixes.
- **Delegate investigation to subagents.** *"use subagents to investigate how token
  refresh works"* — they read dozens of files in their own context, you get the
  summary.
- **Customize compaction** through CLAUDE.md: *"when compacting, always preserve the
  full list of modified files and the test commands"*.

### Context cost per piece

| Piece | When it loads | Cost |
|---|---|---|
| CLAUDE.md | Session start | **Every request** |
| Rules without `paths` | Session start | Every request |
| Rules with `paths` | When matching files are touched | Only when relevant |
| Skills | Descriptions at start; content when used | Low |
| Skills with `disable-model-invocation` | Only when you invoke it | **Zero** until invoked |
| MCP | Names at start; schemas on demand | Low |
| Subagents | When created | **Isolated** from the session |
| Hooks | On the event | **Zero**, unless they return output |

### Checkpoints

Every prompt creates a checkpoint. `Esc Esc` or `/rewind` restores conversation, code,
or both. This changes the posture: instead of planning every move, tell it to try
something risky — if it doesn't work, roll back.

⚠️ Checkpoints only track changes made through Claude's own editing tools. Changes via
Bash or external processes are **not** captured. It doesn't replace git.

---

## 13. Anti-patterns

| Pattern | Symptom | Fix |
|---|---|---|
| **Junk-drawer session** | Started with one task, asked about something else, came back. Context full of irrelevant stuff | `/clear` between tasks |
| **Fixing without stopping** | Fixed it, still wrong, fixed it again. Context polluted with failed attempts | After 2 fixes, `/clear` and rewrite the initial prompt incorporating what you learned |
| **Bloated CLAUDE.md** | Important rules get lost in the noise; Claude ignores half of it | Prune freely. If it already gets it right without the instruction, delete it or turn it into a hook |
| **Trusting without verifying** | Plausible implementation that doesn't handle edge cases | Always provide a way to verify. If you can't verify it, don't merge it |
| **Infinite exploration** | "Investigate X" with no scope; it reads hundreds of files | Bound the investigation or delegate to a subagent |

---

## 14. How this repository uses all of this

`claude-spring-architect` is a live example of the ecosystem applied to Spring Boot project
scaffolding. Mapping between the theory above and the files here:

```
CLAUDE.md                          # always-on context: invariants + routing
CONTEXT.md                         # continuity log, not loaded by the runtime
.claude/
├── rules/                         # modular norms, loaded by paths
│   ├── 00-index.md                # index: which norm covers what
│   ├── architecture-ddd.md        # no paths: master content, copied on init
│   ├── naming.md                  # paths: **/*.java
│   ├── code-quality.md
│   ├── error-handling.md
│   ├── api-rest.md                # paths: **/adapter/in/rest/**
│   ├── lombok.md
│   ├── value-objects.md
│   ├── persistence.md
│   ├── testing.md
│   ├── observability.md
│   └── logging.md
├── skills/
│   ├── init-project/SKILL.md              # /init-project — disable-model-invocation
│   ├── arch-doctor/SKILL.md               # /arch-doctor  — disable-model-invocation
│   ├── claude-code-architect-designer/    # designs this .claude/ itself
│   ├── project-bootstrap/                 # SKILL.md + templates/ + references/
│   ├── use-case-design/                   # pipeline 1/5
│   ├── domain-modeling/                   # pipeline 2/5
│   ├── persistence-architect/             # pipeline 3/5
│   ├── rest-api-architect/                # pipeline 4/5
│   ├── test-architect/                    # pipeline 5/5
│   ├── new-feature/                       # orchestrates the 5 above
│   ├── java-patterns/                     # preloaded into the executor agent
│   └── docker-architect/                  # extends docker-compose after bootstrap
├── agents/
│   ├── project-initializer.md     # subagent: interviews, validates, delegates
│   ├── java-spring-boot-developer.md  # /new-feature's executor
│   └── archunit-installer.md      # test-architect's setup mode, isolated context
├── hooks/
│   └── ArchHook.java              # boundary enforcement, exec form
├── blueprints/                    # declarative data, not instructions
│   ├── _schema.md
│   ├── hexagonal/hexagonal.yaml
│   ├── clean-architecture-multi-module/
│   ├── clean-architecture-single-module/
│   ├── layered/layered.yaml
│   ├── modular-monolith/modular-monolith.yaml
│   ├── onion/onion.yaml
│   ├── custom-template/
│   └── vertical-slice/  # folder only, .yaml not written yet
├── decisions/                     # history — why each piece is shaped as it is
└── settings.json                  # hook registration
```

There's no `.claude/commands/`: slash commands and skills were merged, and keeping
both would duplicate the concept. `/init-project` and `/arch-doctor` are skills with
`disable-model-invocation: true` — same `/name`, plus a support folder.

Decisions in this repository that illustrate the guide's principles well:

- **CLAUDE.md as a router, not an encyclopedia.** The "when X, read Y" table points to
  skills; the norms live in `.claude/rules/`, loaded by `paths` when the matching file
  is touched. CLAUDE.md doesn't repeat any norm.
- **One norm, one owning file.** Skills and agents **cite** it by path
  (`@.claude/rules/x.md`) and never reproduce the content. A norm written in two
  places has diverged — that's a bug.
- **Enforcement in a hook, not in a prompt.** The prohibition on importing Spring in
  the `domain` module isn't a request in markdown: it's `ArchHook.java` reading
  `.claude/forbidden-imports.txt` and blocking. Matches the principle "if the rule must
  always hold, make it a hook."
- **Hook in exec form, no shell.** `command: java` + `args`, a Java file in
  single-file source mode — identical on Linux, macOS, and Windows, no `chmod`.
- **`Stop` hook as a verification gate.** `ArchHook.java tests` runs on the `Stop`
  event: the turn doesn't end without the tests passing. It's level 3 of the escalation
  described in
  [Give Claude a way to verify its own work](#give-claude-a-way-to-verify-its-own-work).
  The `format` and `check` modes run on `PostToolUse` with matcher `Write|Edit`.
- **Secrets denied by permission, not by convention.** `permissions.deny` blocks
  `Read(./**/*.env)`, `Read(./**/secrets/**)`, and `Bash(git push --force:*)` — the
  CLAUDE.md invariant "secrets never in versioned files" has a real gate behind it.
- **Data outside the instructions.** The blueprints are declarative YAML. Adding a new
  architecture doesn't require touching any skill, agent, or command — if it did, the
  design would be broken.
- **Command with dynamic context.** `skills/init-project/SKILL.md` uses
  `` !`find .claude/blueprints -mindepth 2 -maxdepth 2 -name '*.yaml'` `` to list the
  available architectures at invocation time, instead of a fixed list that goes stale.
- **Skill with exemplars, not molds.** `project-bootstrap/templates/` holds real,
  compilable files; `SKILL.md` says to read the shape and write the equivalent, not
  mechanically substitute placeholders.
- **The exemplar lives in the skill; the norm, in `rules/`.** `rules/error-handling.md`
  declares the typed family of exceptions in prose and auto-loads on every `**/*.java`;
  `skills/domain-modeling/templates/DomainException.java.example` is the code, read
  only when that skill runs. Swapping the two around would put Java code into context
  on every file touched — see [Code boilerplate](#code-boilerplate-scaffolders).
  `references/` in the same skill directory holds what's reading material for the
  model (`blueprint-selection.md`, `dependency-catalog.md`), not code to emit.
- **Versions resolved at runtime.** Spring Initializr is the oracle for versions; the
  skill explicitly forbids writing versions from memory. The model's knowledge is
  out of date by construction.

---

## 15. Quick command reference

### Session and context

| Command | Effect |
|---|---|
| `/clear [name]` | New conversation, empty context |
| `/compact [instructions]` | Summarizes to free up context |
| `/context [all]` | Context usage as a grid |
| `/rewind` | Rewinds code and conversation to a checkpoint |
| `/resume` | Resumes a previous conversation |
| `/branch [name]` | Branches the conversation to try another direction |
| `/btw [question]` | Side question, outside the history |
| `/export [file]` | Exports the conversation |
| `/usage` | Token statistics |

### Configuration

| Command | Effect |
|---|---|
| `/init` | Generates a CLAUDE.md from the project |
| `/memory` | Edits CLAUDE.md and toggles auto memory |
| `/skills` | Manages skills; `Space` cycles visibility, `Esc` saves |
| `/agents` | Manages subagents |
| `/hooks` | Views configured hooks |
| `/plugin` | Manages plugins |
| `/mcp` | Status, authentication, and toggling of MCP servers |
| `/permissions` | Allow/ask/deny rules |
| `/config` | Settings interface |
| `/doctor` | Setup checkup, diagnoses and fixes |

### Work

| Command | Effect |
|---|---|
| `/plan [description]` | Enters plan mode directly from the prompt |
| `/goal <condition>` | Claude works until the condition is met |
| `/code-review` | Reviews the diff in a fresh subagent |
| `/security-review` | Checks the diff for vulnerabilities |
| `/verify` | Builds and runs the app to confirm the change |
| `/run` | Launches and drives the app |
| `/batch <instruction>` | Fans out a large change across subagents |
| `/diff` | Reviews the working tree |
| `/tasks` | Session's background work |
| `/list-agents` | Subagents and sessions Claude can message |

### Keyboard

| Shortcut | Effect |
|---|---|
| `Shift+Tab` | Cycles permission modes (includes plan mode) |
| `Esc` | Interrupts Claude, preserving context |
| `Esc Esc` | Rewind menu |
| `Ctrl+G` | Opens the plan in a text editor |
| `@` | References a file or subagent |
| `!` | Runs a shell command directly in the session |
| `#` | Tells Claude to memorize something |

### CLI

```bash
claude                                  # interactive session
claude -p "prompt"                      # non-interactive
claude --continue                       # resumes the last session
claude --resume                         # choose from a list
claude --permission-mode plan           # starts in plan mode
claude --agent code-reviewer            # entire session as a subagent
claude --add-dir ../shared              # access to an extra directory
claude --plugin-dir ./my-plugin         # loads a local plugin
claude --allowedTools "Edit,Bash(git commit *)"
claude --debug                          # load diagnostics
claude mcp add|list|get|remove|login
claude plugin init|validate|marketplace
```

---

## Further reading

- Overview — <https://code.claude.com/docs/en/overview>
- Best practices — <https://code.claude.com/docs/en/best-practices>
- Extending Claude Code (which piece to use) — <https://code.claude.com/docs/en/features-overview>
- Skills — <https://code.claude.com/docs/en/skills>
- Subagents — <https://code.claude.com/docs/en/sub-agents>
- Hooks — <https://code.claude.com/docs/en/hooks>
- MCP — <https://code.claude.com/docs/en/mcp>
- Plugins — <https://code.claude.com/docs/en/plugins>
- Memory and CLAUDE.md — <https://code.claude.com/docs/en/memory>
- Commands and bundled skills — <https://code.claude.com/docs/en/commands>
- Full documentation index — <https://code.claude.com/docs/llms.txt>
