# 02 — Skills

Official pages covered:

| Page | Link |
|--------|------|
| Extend Claude with skills | <https://code.claude.com/docs/en/skills> |
| Commands (includes bundled skills) | <https://code.claude.com/docs/en/commands> |
| Extend agents with skills (SDK) | <https://code.claude.com/docs/en/agent-sdk/skills> |
| Agent Skills (open standard) | <https://agentskills.io> |

---

## What it is and when to create one

A skill is a `SKILL.md` with instructions that Claude adds to its toolkit. You invoke it
with `/skill-name`, or Claude loads it on its own when the `description` matches the task.

Create a skill when:

- you paste the same instructions, checklist or procedure into the chat repeatedly;
- a section of `CLAUDE.md` turned into a procedure instead of a fact.

Essential difference from `CLAUDE.md`: the skill body **only loads when used**, so long
reference material costs almost nothing until it is needed.

Custom commands were merged with skills: `.claude/commands/deploy.md` and
`.claude/skills/deploy/SKILL.md` both create `/deploy`. The skill format adds a directory
for supporting files, invocation-control frontmatter and automatic loading by the model.

Claude Code follows the open [Agent Skills](https://agentskills.io) standard and extends it.

## Where skills live

| Location | Path | Loads in |
|-------|------|-----------|
| Enterprise | `.claude/skills/<name>/SKILL.md` in the managed settings directory | All machines in the organization |
| Personal | `~/.claude/skills/<name>/SKILL.md` | All your projects (not in Cowork/cloud) |
| Project | `.claude/skills/<name>/SKILL.md` | Sessions in this repository (commit it) |
| Nested | `<subdir>/.claude/skills/<name>/SKILL.md` | Sessions started at or below `<subdir>` |
| `--add-dir` | `.claude/skills/` in the added directory | That session |
| Plugin | `<plugin>/skills/<name>/SKILL.md` | Wherever the plugin is enabled, as `/plugin:skill` |
| claude.ai | Skills enabled on the account | Cowork, cloud and the terminal logged in with the account |

Name resolution rules: enterprise > personal > project; a skill beats a file in
`.claude/commands/`; a project skill replaces a bundled command (but not its aliases);
plugin skills are namespaced and coexist; a synced skill loses the short name to any other
command with the same name and runs as `/anthropic-skills:<name>`.

Reserved names: `synced` (the synced-skills folder) and `anthropic-skills` (the namespace
of the claude.ai account skills).

In monorepos, skills load from the directory where you started up to the repo root. Skills
in subdirectories below only load when Claude touches a file in that folder (or after
`/add-dir`). If a name collides, `/deploy` runs the root one and `/apps/web:deploy` runs the
nested one.

## Anatomy

```yaml
---
name: my-skill
description: What the skill does and when to use it
disable-model-invocation: true
allowed-tools: Read Grep
---

Markdown instructions here...
```

- All fields are optional; only `description` is recommended — it is what decides when
  Claude loads the skill on its own.
- The frontmatter is only read if the opening `---` is the **first line** of the file.
- An unknown field is silently ignored (no error) — beware of typos.
- `description` + `when_to_use` are truncated at 1,536 characters in the listing: put the
  main use case first.
- Booleans accept `yes/no/on/off/1/0` in addition to `true/false` (v2.1.218+).

### Frontmatter fields (main ones)

| Field | Effect |
|-------|--------|
| `name` | Command name in the `/` menu; defaults to the directory name |
| `description` | The trigger: what it does and when to use it |
| `when_to_use` | Additional trigger phrases, appended to the description |
| `argument-hint` | Autocomplete hint, e.g. `[issue-number]` |
| `arguments` | Named positional arguments for `$name` substitution |
| `disable-model-invocation` | `true` = only you invoke it; removes the description from the context |
| `user-invocable` | `false` = only Claude invokes it; disappears from the `/` menu |
| `allowed-tools` | Pre-approves tools **during the turn** that invoked the skill |
| `disallowed-tools` | Removes tools from the pool while the skill is active |
| `model` / `effort` | Overrides model and effort level during the turn |
| `context: fork` | Runs in an isolated subagent |
| `agent` | Which subagent type to use with `context: fork` |
| `background` | `false` makes the fork block the turn until it finishes |
| `hooks` | Registers hooks when the skill is invoked, valid for the rest of the session |
| `paths` | Globs that limit when Claude activates the skill automatically |
| `shell` | `bash` (default) or `powershell` for injected commands |
| `metadata`, `license`, `compatibility` | Metadata; Claude Code accepts it but does not act on it |

Outside Claude Code (upload to claude.ai, Skills API, `package_skill.py`) only the six spec
fields are valid: `name`, `description`, `license`, `compatibility`, `metadata`,
`allowed-tools`. An extra field causes a hard upload error.

## Invocation control

| Frontmatter | You invoke | Claude invokes | Context |
|-------------|-------------|---------------|----------|
| (default) | Yes | Yes | Description always in context; body on invocation |
| `disable-model-invocation: true` | Yes | No | Description out of context |
| `user-invocable: false` | No | Yes | Description always in context |

Use `disable-model-invocation: true` for anything with side effects (`/commit`, `/deploy`,
`/send-slack-message`). Use `user-invocable: false` for background knowledge that is not an
action (e.g. `legacy-system-context`).

## Content lifecycle

- On invocation, the rendered `SKILL.md` enters the conversation as **one message and stays
  in later turns**. The file is not re-read — write standing instructions, not one-off
  steps.
- Re-invoking with identical content adds only an "already loaded" note; if it changed (new
  arguments or dynamic context), the full content is appended again.
- Auto-compaction re-attaches the most recent invocation of each skill, keeping the first
  5,000 tokens of each, with a combined budget of 25,000 tokens (from most recent to
  oldest).
- If the skill "stopped influencing", the content is usually still there and the model is
  choosing another path: strengthen the `description`, or use hooks for enforcement.
- Keep `SKILL.md` under ~500 lines; extensive material goes into supporting files
  referenced from it.

## Arguments and substitutions

| Variable | Meaning |
|----------|-------------|
| `$ARGUMENTS` | Everything that came after the skill name |
| `$ARGUMENTS[N]` / `$N` | Argument by position (0-based) |
| `$name` | Named argument declared in `arguments` |
| `${CLAUDE_SESSION_ID}` | Session ID |
| `${CLAUDE_EFFORT}` | Current effort level |
| `${CLAUDE_SKILL_DIR}` | Directory of the `SKILL.md` |
| `${CLAUDE_PROJECT_DIR}` | Project root |
| `${CLAUDE_PLUGIN_ROOT}` / `${CLAUDE_PLUGIN_DATA}` | Only in plugin skills |

`${CLAUDE_SKILL_DIR}` and `${CLAUDE_PROJECT_DIR}` are also substituted inside Bash rules in
`allowed-tools` — that is how a skill runs its own script without a permission prompt:

```yaml
---
name: render-chart
description: Render a chart from a CSV file
allowed-tools: Bash(${CLAUDE_SKILL_DIR}/scripts/render.sh *)
---
Run `${CLAUDE_SKILL_DIR}/scripts/render.sh <csv-file>` to render the chart.
```

You can stack skills in one message: `/write-tests /fix-issue 123` loads both and passes
`123` as an argument to both (up to 6 in total; expansion stops at the first one that is not
an inline skill).

## Dynamic context (`!` commands)

`` !`command` `` runs the command **before** the content reaches the model and replaces the
line with the output. For multiple lines, use a fenced block opened with ` ```! `.

Important rules:

- Only recognized when `!` is at the start of the line or after whitespace.
- The substitution runs once; the output is not rescanned.
- Working directory is the session shell's; `stderr` is merged into `stdout` in bash;
  default timeout of 2 minutes from the Bash tool.
- **A failure aborts the entire invocation** (`Shell command failed for pattern "..."`), not
  just the placeholder. Exit code 1 from search/comparison commands is tolerated; use
  `|| true` for the rest.
- Injected commands never prompt for permission: they are checked against the rules first.
  Deny aborts; outside auto mode, any result other than "allow" also aborts.
- `disableSkillShellExecution: true` in settings turns this off for user, project, plugin
  and `--add-dir` skills (bundled and managed ones are not affected).
- Skills synced from claude.ai never run these commands on your machine.

## Running in a subagent (`context: fork`)

```yaml
---
name: deep-research
description: Research a topic thoroughly
context: fork
agent: Explore
---
Research $ARGUMENTS thoroughly: ...
```

- Creates a subagent of the type in `agent` (default `general-purpose`) and hands it the
  skill content as the prompt. The subagent **does not see the conversation history** — the
  instructions must stand on their own.
- Runs in the background by default (v2.1.218+); `background: false` blocks the turn. It
  also blocks in `-p`, with `CLAUDE_CODE_DISABLE_BACKGROUND_TASKS=1`, when an invocation is
  already running, and in scheduled tasks.
- A background fork uses the reduced tool set of background subagents, and its edits fall
  outside checkpoints (`/rewind` does not undo them; use git).
- Only makes sense for skills with an explicit task; skills that are just guidelines return
  empty.

## Restricting Claude's access to skills

- Denying the whole `Skill` tool turns them all off.
- Permission rules: `Skill(commit)` (exact), `Skill(review-pr *)` (prefix), `Skill(deploy *)`
  in deny. Deny also blocks by alias and by unqualified name.
- `Skill(anthropic-skills:pdf)` approves one specific synced skill.
- `skillOverrides` in settings changes the visibility of skills you did not write
  (`"off"`, `"user-invocable-only"`).

## Bundled skills

They ship with Claude Code and are prompt-based: `/doctor`, `/code-review`, `/batch`,
`/debug`, `/loop`, `/verify`, `/run`, `/run-skill-generator`, `/simplify`, `/design`,
`/dataviz`, `/claude-api`, `/update-config`, `/fewer-permission-prompts`,
`/workflow-authoring`, among others. `disableBundledSkills: true` turns the set off.

Run/verify trio:

| Skill | Function |
|-------|--------|
| `/run` | Starts and drives the application to see the change working |
| `/verify` | Builds and runs the app to confirm the change, without falling back to tests/type-check |
| `/run-skill-generator` | Records the build and launch recipe as a skill in `.claude/skills/run-<name>/` |

## Editing during the session

Claude Code watches the skills directories and applies changes without a restart (except in
bare mode). If you create a skills directory that did not exist at the start of the session,
run `/reload-skills`. Changes to a plugin's `hooks/`, `.mcp.json`, `agents/` require
`/reload-plugins`.
