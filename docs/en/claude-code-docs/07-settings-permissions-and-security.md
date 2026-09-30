# 07 — Settings, permissions, sandbox and security

Official pages covered:

| Page | Link |
|--------|------|
| Settings files and precedence | <https://code.claude.com/docs/en/settings> |
| All settings | <https://code.claude.com/docs/en/settings-reference> |
| Example settings files | <https://code.claude.com/docs/en/settings-example> |
| Configure permissions | <https://code.claude.com/docs/en/permissions> |
| Choose a permission mode | <https://code.claude.com/docs/en/permission-modes> |
| Configure the sandboxed Bash tool | <https://code.claude.com/docs/en/sandboxing> |
| Choose a sandbox environment | <https://code.claude.com/docs/en/sandbox-environments> |
| Environment variables | <https://code.claude.com/docs/en/env-vars> |
| Security | <https://code.claude.com/docs/en/security> |
| Catch security issues as Claude writes code | <https://code.claude.com/docs/en/security-guidance> |
| Scan your codebase for vulnerabilities | <https://code.claude.com/docs/en/claude-security> |
| Configure auto mode | <https://code.claude.com/docs/en/auto-mode-config> |

---

## Settings files

| Scope | File | Affects | Use for |
|--------|---------|-------|----------|
| User | `~/.claude/settings.json` | You, all projects on the machine | Personal preferences, default model, your rules |
| Shared project | `.claude/settings.json` | Everyone on the project (committed) | Team permissions, hooks, plugins, env |
| Project local | `.claude/settings.local.json` | Only you, only this project | Personal overrides, testing before sharing |
| Managed | `managed-settings.json`, MDM, claude.ai console | Everyone in the organization | Security and compliance policy |

A fifth file, `~/.claude.json`, is written by Claude Code itself (login, MCP, per-project
state, global config keys) and should not be edited by hand.

Installing Claude Code does **not** create a settings file. Claude Code creates
`~/.claude/settings.json` on the first change through `/config`, and
`.claude/settings.local.json` on the first "Yes, and don't ask again" approval — adding
`**/.claude/settings.local.json` to git's global excludes file.

Settings are strict JSON: `//` comments and trailing commas are syntax errors. Add
`"$schema": "https://json.schemastore.org/claude-code-settings.json"` for autocomplete.

### Precedence

1. **Managed settings** (nothing of yours overrides them, except the exceptions below)
2. **Command-line arguments** (`--settings`, flags of one session)
3. **Project local** (`.claude/settings.local.json`)
4. **Shared project** (`.claude/settings.json`)
5. **User** (`~/.claude/settings.json`)

Derived rules:

- Lists (`permissions.allow`, etc.) **add up** across layers instead of replacing each
  other. Exceptions: `fallbackModel`, `modelPicker`, `availableModels` and `modelSettings`
  have their own rules.
- Environment variables are not a level of the stack: the relationship is decided pair by
  pair (`ANTHROPIC_MODEL` beats the `model` key of any file; `ANTHROPIC_DEFAULT_MODEL` only
  applies if no file sets `model`).
- Some keys **never** take effect from a committed file (scope "User, local, or managed",
  "Managed" or "Global config" in the reference), and others wait for workspace trust
  (`permissions.allow`, `permissions.additionalDirectories`, `extraKnownMarketplaces`, most
  of `env`). `deny` and `ask` apply immediately.
- `permissions.defaultMode` with the values `auto` and `bypassPermissions` does not take
  effect from project or local settings.

### Exceptions: the more restrictive value beats managed

`disableClaudeAiConnectors: true`, `enableArtifact: false`/`disableArtifact: true`,
`isolatePeerMachines: true`, `remoteControlAtStartup: false` (project/local), a stricter
`crossSessionInbound`, `useAutoModeDuringPlan: false`, `syncClaudeAiSkills: false`,
`syncClaudeAiPlugins: false`, a lower `maxEffortLevel`.

### When edits take effect

Claude Code watches the files and reloads most changes in the running session (including
`permissions`, `hooks`, `apiKeyHelper`), firing `ConfigChange`. Read only at startup:
`model` (use `/model`), `effortLevel` and `modelSettings` (use `/effort`), among
administrative keys such as `requiredMinimumVersion`.

Verification: `/status` → `Setting sources` line; `claude doctor` lists rejected entries.

### Cloud sessions

They read the committed `.claude/settings.json` (in a single-repository session) and only
server-managed settings. They do **not** read `~/.claude/settings.json` or
`.claude/settings.local.json`.

## Permission system

| Tool type | Approval in Manual mode | "Don't ask again" |
|--------------|--------------------------|-------------------|
| Read-only (Read, Grep) | No, inside the working directories | — |
| Bash commands | Yes, except a built-in read-only set | Permanent per repo and command |
| File modification | Yes | Until the end of the session |
| WebFetch | Yes, except pre-approved documentation domains | Permanent per repo and domain |
| WebSearch | Yes | Permanent per repository |

`/permissions` lists every rule and the file each one comes from.

**Evaluation order: deny → ask → allow.** The first one that matches decides; specificity
does not change the order. A broad `deny` (`Bash(aws *)`) blocks even with a more specific
allow — allow does not carve an exception out of deny.

A deny by **bare name** (`Bash`) removes the tool from Claude's context; a scoped deny
(`Bash(rm *)`) keeps the tool and blocks the calls that match. `EndConversation` is the
exception: it cannot be removed while any other tool exists.

> Permissions are enforced by Claude Code, not by the model. `CLAUDE.md` shapes what it
> tries; permissions decide what it can do.

### Rule syntax

- `Tool` — all uses. `Bash(*)` is equivalent to `Bash`.
- `Tool(specifier)` — `Bash(npm run build)`, `Read(./.env)`, `WebFetch(domain:example.com)`.
- `Tool(param:value)` in deny/ask — matches a top-level parameter:
  `Agent(model:opus)`, `Agent(isolation:worktree)`, `Bash(run_in_background:true)`.
  It does not work on the main field (`command`, `file_path`, `url`...).
- Globs in the tool name in deny/ask: `"*"`, `"mcp__*"`. In allow, only after the literal
  prefix `mcp__<server>__`.

### Wildcards in Bash

Put the `*` **after the subcommand**: `Bash(git log *)` allows only `git log`, `Bash(git *)`
allows everything git (including `-c`, which makes git run a program you name). Claude Code
warns at startup about an allow with `*` before the subcommand.

| You write | Matches | Does not match |
|--------------|----------|------|
| `Bash(npm run build)` | `npm run build` | `npm run build --watch` |
| `Bash(npm run *)` | `npm run build`, `npm run` | `npm install` |
| `Bash(ls *)` | `ls -la`, `ls` | `lsof` |
| `Bash(ls*)` | `ls -la`, `lsof` | — |

`Bash(ls:*)` is an equivalent form of `Bash(ls *)`, recognized only at the end of the
pattern.

### Compound commands, wrappers and limits

- Claude Code understands shell operators: `Bash(safe-cmd *)` does not authorize
  `safe-cmd && other-cmd`. Deny/ask apply to any subcommand, including in subshells,
  substitutions and `for` bodies.
- Fixed wrappers are stripped before matching (`timeout`, `xargs` without flags, known env
  assignments like `NODE_ENV=test`). Environment runners (`direnv exec`, `devbox run`,
  `mise exec`, `npx`, `docker exec`) are **not** stripped; exec wrappers such as `watch`,
  `setsid`, `flock` cannot be auto-approved by a prefix rule.
- What a Bash rule does **not** catch: an absolute path (`/usr/bin/curl`), invocation via
  `sh -c '...'`, and variations such as `git -C . push` or `git 'push'`. For enforcement
  that does not depend on the command text, use **sandboxing**; to inspect the command with
  your own logic, use a `PreToolUse` hook.
- Redirections (`>`, `>>`, `<`, `tee`) are checked against your file rules.

> Bash patterns that try to restrict arguments are fragile: `Bash(curl http://github.com/ *)`
> can be bypassed by a flag before the URL, another protocol, a redirect or a variable.
> Prefer denying `curl`/`wget` and using `WebFetch(domain:...)`, or a hook.

### Read and Edit

They use gitignore pattern syntax, with four anchor types:

| Pattern | Meaning |
|--------|-------------|
| `//path` | Absolute from the filesystem root |
| `~/path` | From the home directory |
| `/path` | Relative to the **settings source** (not the filesystem root) |
| `path` or `./path` | Relative to the current directory |

`Edit` applies to every built-in tool that edits files; a `Read` deny also blocks Edit and
Write on the same path, and applies to recognized file commands in Bash (`cat`, `head`,
`tail`, `sed`, `tee`). Path rules are only checked against `Edit(...)` and `Read(...)` —
writing them for `Write`, `Glob` or `NotebookEdit` does not work.

On Windows, paths are normalized to POSIX (`C:\Users\alice` → `/c/Users/alice`).

## Permission modes

| Mode | Runs without asking | Best for |
|------|--------------------|-------------|
| `default` (Manual) | Only reads | Reviewing each action, sensitive work |
| `acceptEdits` | Reads, edits and common filesystem commands | Iterating on code you are reviewing |
| `plan` | Reads (+ classifier-approved commands, with auto mode) | Exploring before changing |
| `auto` | Everything, with safety checks in the background | Long tasks, less prompt fatigue |
| `dontAsk` | Reads and pre-approved tools; the rest is **denied** | CI and locked-down scripts |
| `bypassPermissions` | Everything, except what no mode auto-approves | Only in an isolated container/VM |

`Shift+Tab` cycles. `permissions.disableBypassPermissionsMode` and
`permissions.disableAutoMode` (value `"disable"`) lock modes — useful in managed settings.

Auto mode uses a server-side classifier that blocks scope escalation, unknown infrastructure
and actions induced by hostile content; `/auto-mode-setup` and `autoMode.environment` tune
the behavior, and `/permissions` has an **Auto mode** tab.

Protected paths (`.git`, `.claude` and others) and critical paths get special treatment per
mode — see [Protected paths](https://code.claude.com/docs/en/permission-modes#protected-paths).

## Working directories

The launch directory is the primary one. `--add-dir` / `/add-dir` add others and also load
that directory's `.claude/skills/`, `.claude/commands/` and `.claude/agents/` — whereas the
`permissions.additionalDirectories` setting grants **file access only**, without loading
configuration. `/cd` moves the session.

## Sandboxing

Operating-system-level isolation for the Bash tool: it restricts filesystem and network,
letting Claude work more freely inside limits. Unlike permissions, it does not depend on the
command text.

Points from the documentation: sandbox modes, turning off filesystem isolation, credential
protection and masking, network isolation (with an approval prompt for requests), OS-level
enforcement, a custom proxy, and how the sandbox relates to permission rules and modes.
Organizations can enforce the sandbox via managed settings and stop developers from
loosening the policy. There are documented security limitations and platform compatibility
limits — read them before relying on it as a hard boundary.

## Security

- **Permission-based architecture**: write access restricted to the project directory and
  subdirectories by default; state-changing operations require approval.
- **Prompt injection** is the central risk: external content (pages, issues, tool outputs)
  can try to redirect the agent. Protections include permission review, context isolation
  and domain allowlists; final responsibility for reviewing proposed commands is the user's.
- **MCP**: trust the server before connecting; servers that fetch external content increase
  the injection surface.
- **Working with sensitive code**: use deny rules on credential files (`Read(./.env)`,
  `Read(./secrets/**)`), the sandbox and, for teams, managed settings.
- `/security-review` analyzes the branch's changes for vulnerabilities; the
  `security-guidance` plugin runs a review by a separate model and returns findings to the
  session; codebase scanning tools are in
  [claude-security](https://code.claude.com/docs/en/claude-security).
- Report vulnerabilities through the channels listed in
  [Security](https://code.claude.com/docs/en/security#reporting-security-issues).

## Example settings file

```json
{
  "$schema": "https://json.schemastore.org/claude-code-settings.json",
  "permissions": {
    "allow": ["Bash(npm run lint)", "Bash(npm run test *)"],
    "deny": ["Read(./.env)", "Read(./.env.*)", "Bash(git push *)"]
  },
  "hooks": {
    "PostToolUse": [
      { "matcher": "Edit|Write",
        "hooks": [{ "type": "command", "command": "jq -r '.tool_input.file_path' | xargs npx prettier --write" }] }
    ]
  }
}
```
