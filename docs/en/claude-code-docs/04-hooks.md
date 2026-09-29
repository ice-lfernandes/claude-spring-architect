# 04 — Hooks

Official pages covered:

| Page | Link |
|--------|------|
| Automate actions with hooks (guide) | <https://code.claude.com/docs/en/hooks-guide> |
| Hooks reference (full schemas) | <https://code.claude.com/docs/en/hooks> |
| Intercept and control agent behavior with hooks (SDK) | <https://code.claude.com/docs/en/agent-sdk/hooks> |
| Official example: bash command validator | <https://github.com/anthropics/claude-code/blob/main/examples/hooks/bash_command_validator_example.py> |

---

## Why hooks exist

Hooks are commands you define that Claude Code runs at points in its lifecycle. They are the
only **deterministic** layer of behavior: the action always happens, without depending on the
model deciding to run it.

The documentation's decision rule: *"Put guardrails in hooks."* An instruction like "never
edit `.env`" in CLAUDE.md or in a skill is a request, not a guarantee. A `PreToolUse` hook
that blocks the edit is enforcement.

| | Hook | Skill |
|---|------|-------|
| Runs | Command, HTTP, MCP tool, LLM prompt or subagent | Instructions Claude reads |
| Trigger | Lifecycle event | You typing `/name`, or the description matching |
| Determinism | Guaranteed | The model interprets |
| Context cost | Zero, unless it returns output | Description always; body on use |
| Good for | Lint after edit, blocking a command, logging, notification | Workflows that need reasoning, reference |

## Lifecycle events

| Event | When it fires |
|--------|----------------|
| `SessionStart` | Session starts or is resumed |
| `Setup` | `--init-only`, or `--init`/`--maintenance` in `-p` |
| `UserPromptSubmit` | You submit a prompt, before the model processes it |
| `UserPromptExpansion` | A typed command expands into a prompt (can block) |
| `PreToolUse` | Before a tool runs (can block) |
| `PermissionRequest` | When a tool needs a permission decision |
| `PermissionDenied` | When auto mode denies a call |
| `PostToolUse` | After a tool succeeds |
| `PostToolUseFailure` | After a tool fails |
| `PostToolBatch` | After a parallel batch resolves |
| `Notification` | Claude Code emits a notification |
| `MessageDisplay` | Assistant text is displayed |
| `SubagentStart` / `SubagentStop` | Subagent created / finished |
| `TaskCreated` / `TaskCompleted` | Task created / completed |
| `Stop` | Claude finishes responding |
| `StopFailure` | Turn ends because of an API error |
| `TeammateIdle` | An agent-team teammate is about to go idle |
| `InstructionsLoaded` | A `CLAUDE.md` or rule enters the context |
| `ConfigChange` | A configuration file changes during the session |
| `CwdChanged` | Working directory changes (useful with direnv) |
| `DirectoryAdded` | `/add-dir` or `register_repo_root` |
| `FileChanged` | A watched file changes on disk (`matcher` lists the names) |
| `WorktreeCreate` / `WorktreeRemove` | Worktree creation/removal (replaces the default git behavior) |
| `PreCompact` / `PostCompact` | Before / after compaction |
| `PreModelSwitch` / `PostModelSwitch` | Before (can block) / after switching model |
| `Elicitation` / `ElicitationResult` | An MCP server asks for user input |
| `SessionEnd` | Session ends |

## Hook types

- `"type": "command"` — runs a shell command (the normal case).
- `"type": "http"` — POSTs the event JSON to a URL; the response uses the same output
  format. Headers support `$VAR`, but only variables listed in `allowedEnvVars` are
  resolved. An HTTP status alone blocks nothing.
- `"type": "mcp_tool"` — calls a tool on a configured MCP server.
- `"type": "prompt"` — single-turn LLM evaluation (Haiku by default). Answers
  `{"ok": true|false, "reason": "..."}`. On `Stop`/`SubagentStop`, `ok:false` hands the
  `reason` back so Claude continues (`"impossible": true` releases the stop).
  On `PreToolUse`/`PostToolUse`, the turn ends by default; `continueOnBlock: true` returns
  the reason to the model and continues.
- `"type": "agent"` — **experimental**: a subagent that can read files and run commands
  before deciding. Default timeout 60s, up to 50 tool turns. It has no `impossible` or
  `continueOnBlock` (it behaves like `continueOnBlock: true`).

Use a prompt hook when the input JSON is enough to decide; an agent hook when you need to
verify the real state of the codebase.

## Input and output

The event arrives as JSON on **stdin**. Common fields: `session_id`, `cwd`,
`hook_event_name`, plus per-event fields (`tool_name`, `tool_input`, `prompt`, `source`...).

### Exit codes

- **0** — no objection. On `PreToolUse` this does **not approve** the call: the normal
  permission flow continues. On `UserPromptSubmit`, `UserPromptExpansion`, `SessionStart`
  and `PostModelSwitch`, stdout treated as plain text is added to the context.
- **2** — blocks the action; write the reason to **stderr**. Where the reason shows up
  depends on the event: some hand it to the model as feedback, others show it to the user,
  and some (like `ConfigChange` and `Elicitation`) show nothing. Non-blockable events, such
  as `SessionStart`, just show the stderr and carry on.
- **Any other** — if stdout is a JSON object valid against the schema, the JSON decides and
  the exit code is ignored; otherwise it is a non-blocking error and the action proceeds.

### Structured JSON output

Exit 0 and write JSON to stdout. `PreToolUse` example:

```json
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Use rg instead of grep for better performance"
  }
}
```

`permissionDecision` accepts `allow` (skips the prompt, but deny/ask and managed deny still
apply), `deny`, `ask` and `defer` (only in `-p`, for an Agent SDK wrapper to resume later).

For `UserPromptSubmit`, text is injected with `hookSpecificOutput.additionalContext` —
**nested**; at the root level it is silently ignored. `PostToolUse` and `Stop` use
`decision: "block"` at the top level; `PermissionRequest` uses
`hookSpecificOutput.decision.behavior`.

Don't mix: either exit 2 with stderr, or exit 0 with JSON.

## Matchers

Without a matcher, the hook fires on every occurrence of the event. `"Edit|Write"` (or
`"Edit, Write"`) limits by tool name. Matchers are **case-sensitive**.

| Event | The matcher filters | Examples |
|--------|------------------|----------|
| `PreToolUse`, `PostToolUse`, `PostToolUseFailure`, `PermissionRequest`, `PermissionDenied` | Tool name | `Bash`, `Edit\|Write`, `mcp__.*` |
| `SessionStart` | How the session started | `startup`, `resume`, `clear`, `compact`, `fork` |
| `SessionEnd` | Why it ended | `clear`, `resume`, `logout`, `prompt_input_exit`, `other` |
| `Notification` | Notification type | `permission_prompt`, `idle_prompt`, `agent_completed`, ... |
| `SubagentStart`/`SubagentStop` | Agent type | `Explore`, `Plan`, custom names |
| `PreCompact`/`PostCompact` | Origin | `manual`, `auto` |
| `ConfigChange` | Configuration source | `user_settings`, `project_settings`, `local_settings`, `policy_settings`, `skills` |
| `InstructionsLoaded` | Load reason | `session_start`, `nested_traversal`, `path_glob_match`, `include`, `compact` |
| `FileChanged` | Literal file names | `.envrc\|.env` |
| `UserPromptSubmit`, `PostToolBatch`, `Stop`, `CwdChanged`, `MessageDisplay`, ... | No matcher | Always fires |

### The `if` field

Filters by name **and arguments** using permission-rule syntax, avoiding a needless process
spawn:

```json
{ "type": "command", "if": "Bash(git *)", "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/check-git-policy.sh" }
```

Subcommands inside `&&`, `$()` and backticks are checked. When Claude Code cannot determine
what runs, it runs the hook anyway — the filter is *best-effort*, so hard enforcement
belongs in the permission system.

`if` only works on tool events; on any other event it prevents the hook from running.

## Where to configure

| Location | Scope | Shareable |
|-------|--------|----------------|
| `~/.claude/settings.json` | All your projects | No |
| `.claude/settings.json` | One project | Yes (committed) |
| `.claude/settings.local.json` | One project, only you | No |
| Managed settings | Organization | Yes (admin) |
| Plugin `hooks/hooks.json` | Where the plugin is active | Yes |
| Skill frontmatter | Rest of the session, after invocation | Yes |
| Subagent frontmatter | While the subagent runs | Yes |

`/hooks` lists everything grouped by event (read-only menu). `disableAllHooks: true` turns
them off — managed settings still apply unless they also set the key. Hooks **add up**
across sources: all registered for the event fire.

## Combining results

All hooks for the event run **in parallel to completion**; a `deny` does not stop its
siblings from running (nor their side effects). Then Claude Code combines: for permission
decisions on `PreToolUse` the most restrictive wins, in the order `deny` > `defer` > `ask` >
`allow`. `additionalContext` text from all of them is preserved.

## Hooks and permission modes

`PreToolUse` fires **before** any mode check, in every mode, including `dontAsk` and
`bypassPermissions` — so a hook can enforce policy the user cannot get around by switching
modes. The reverse does not hold: a hook's `allow` does not override a settings deny or the
prompt for MCP tools marked `requiresUserInteraction`.

## Limitations

- Command hooks communicate only through stdout/stderr/exit code; they do not trigger `/`
  commands or tool calls.
- Timeouts: `command`/`http`/`mcp_tool` 10 min (30s for `UserPromptSubmit` and model
  switch, 10s for `MessageDisplay`); `prompt` 30s; `agent` 60s; `SessionEnd` shares 1.5s.
- `PostToolUse` undoes nothing — the tool has already run.
- `Stop` fires whenever Claude finishes responding, not only on task completion; it does not
  fire on a user interrupt (an API error becomes `StopFailure`). Cap of 8 consecutive
  blocks; your script must check `stop_hook_active`
  (`CLAUDE_CODE_STOP_HOOK_BLOCK_CAP` adjusts it).
- If several `PreToolUse` hooks return `updatedInput`, the last one to finish wins — and the
  order is non-deterministic.

## Canonical examples

Format after edit:

```json
{"hooks":{"PostToolUse":[{"matcher":"Edit|Write","hooks":[
  {"type":"command","command":"jq -r '.tool_input.file_path' | xargs npx prettier --write"}]}]}}
```

Block protected files (`PreToolUse` + script with `exit 2`):

```bash
#!/bin/bash
INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')
FILE_PATH="${FILE_PATH//\\//}"
for pattern in ".env" "package-lock.json" ".git/"; do
  if [[ "$FILE_PATH" == *"$pattern"* ]]; then
    echo "Blocked: $FILE_PATH matches protected pattern '$pattern'" >&2
    exit 2
  fi
done
exit 0
```

Re-inject context after compaction: `SessionStart` with matcher `compact` and an `echo` (or
`git log --oneline -5`) whose stdout enters the context.

Reload environment with direnv: `SessionStart` + `CwdChanged` writing
`direnv export bash > "$CLAUDE_ENV_FILE"`.

Auto-approve a specific prompt: `PermissionRequest` with a narrow matcher returning
`{"hookSpecificOutput":{"hookEventName":"PermissionRequest","decision":{"behavior":"allow"}}}`.
Never use an empty matcher or `.*` here.

## Troubleshooting

- **Hook doesn't fire:** check `/hooks`, the exact matcher (case-sensitive) and the right
  event.
- **"command not found":** use an absolute path or `${CLAUDE_PROJECT_DIR}`; `"args": []`
  switches to exec form and avoids the shell.
- **Script doesn't run:** `chmod +x`.
- **JSON has no effect:** something wrote to stdout first (typically an unconditional `echo`
  in your `~/.bashrc`; guard it with `if [[ $- == *i* ]]`), or the field is at the wrong
  level (`permissionDecision` goes inside `hookSpecificOutput`). `claude --debug` shows
  `Hook JSON output had unrecognized keys`.
- **Full coverage of file changes:** Claude also changes files through Bash. For auditing,
  use a `Stop` hook that sweeps the tree once per turn, or also match `Bash|PowerShell` and
  list with `git status --porcelain`; to react to a specific file regardless of who wrote
  it, use `FileChanged`.
- **Logs:** `claude --debug-file /tmp/claude.log` and `tail -f`, or `/debug` mid-session.
