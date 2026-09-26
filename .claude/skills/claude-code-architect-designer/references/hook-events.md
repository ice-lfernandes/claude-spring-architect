# Hooks — events, entry fields, and exit codes

Reference for the `claude-code-architect-designer` skill. Loads only when it is invoked,
same discipline as `frontmatter-fields.md` and `mcp-fields.md`.

Source: `@claude-help.md` § 8. Primary source:
<https://code.claude.com/docs/en/hooks>,
<https://code.claude.com/docs/en/settings-reference>. Where they diverge, the official
docs win — and this file gets corrected.

## Who owns this list

**The owner is `.claude/schemas/extensions.json`'s `settings` block**, because that's what
`ArchHook.java schema` reads and what it blocks on. An event added only here doesn't
become accepted — it still returns exit 2. Same relationship `mcp-fields.md` has to the
`mcp` block of the same file.

When adding an event: JSON first, then the table. Confirm with
`java .claude/hooks/ArchHook.java schema`.

Design and rationale: `@.claude/decisions/0053-hooks-in-architect-designer.md`.

---

## Where a hook can be registered

| Location | Scope | Written by this skill |
|---|---|---|
| `.claude/settings.json` | This project, every session | ✅ Form 7a |
| `hooks:` in a skill's or agent's frontmatter | Only while that piece runs | ✅ Form 7b |
| `.claude/settings.local.json` | This machine, not versioned | ❌ out of scope |
| `~/.claude/settings.json` | Every project of one person | ❌ out of scope |
| `<plugin>/hooks/hooks.json` | Wherever the plugin is active | ❌ this repo ships no plugin |

## Events

`matcher` column: ✅ means the event reads a `matcher` and filters by it. On a ➖ event a
`matcher` is dead configuration that reads as a filter — `ArchHook.java schema` rejects
it.

| Event | Fires | `matcher` |
|---|---|---|
| `SessionStart` | Once, when the session opens | ➖ |
| `SessionEnd` | Once, when it closes | ➖ |
| `UserPromptSubmit` | Every prompt, before the model sees it | ➖ |
| `Stop` | End of every turn | ➖ |
| `StopFailure` | End of a turn that errored | ➖ |
| `PreToolUse` | Before each tool call | ✅ tool name |
| `PostToolUse` | After each tool call | ✅ tool name |
| `PostToolUseFailure` | After a tool call that failed | ✅ tool name |
| `PostToolBatch` | After a batch of tool calls | ✅ |
| `PermissionRequest` | When a permission prompt is about to be shown | ✅ |
| `PermissionDenied` | When one is refused | ✅ |
| `SubagentStart` / `SubagentStop` | Around a subagent's run | ✅ |
| `PreCompact` / `PostCompact` | Around a context compaction | ✅ |
| `Notification` | On a notification | ✅ |
| `FileChanged` | A watched file changed on disk | ➖ |
| `InstructionsLoaded` | `CLAUDE.md` and rules loaded | ➖ |
| `TaskCreated` / `TaskCompleted` | Around a background task | ➖ |
| `WorktreeCreate` | A worktree is created | ➖ |
| `ConfigChange` | Configuration changed | ➖ |
| `CwdChanged` | Working directory changed | ➖ |

`@claude-help.md` § 8 ends this list with "among others". An event the runtime supports
and this table doesn't have is **not** invented into `hook_events`: it goes into
`hook_events_extra` in `.claude/schemas/extensions.json`, with the reason in the commit,
and then into this table.

## Entry fields

| Field | Required | For what |
|---|---|---|
| `type` | ✅ | `command`. The runtime also serves http/mcp/prompt/agent hooks; this repo runs commands only, and `entry_types` in the schema says so |
| `command` | ✅ | The **bare executable**, e.g. `java`. A pipe, `&&`, or redirect here is a shell string in the executable's slot — rejected |
| `args` | ➖ | Arguments, one per array entry. This is exec form: no shell, no quoting, no execute bit, portable to Windows |
| `if` | ➖ | A permission rule (`Edit(.claude/**/*.md)`) evaluated **before** the process is spawned. The single cheapest thing in a hook |
| `timeout` | ➖ | Seconds, positive. Past it the hook is killed |
| `statusMessage` | ➖ | What the user sees while it runs |

Group level, above the entries: `matcher` and `hooks`. Nothing else.

## Exit codes

| Code | Behavior |
|---|---|
| `0` | Success. Valid JSON on stdout is parsed |
| `2` | **Blocks**, on the events that support it. stderr becomes the reason shown |
| other | Non-blocking error; the action proceeds |

`2` blocks on `PreToolUse` (the call), `UserPromptSubmit` (the prompt), `Stop` (forces
continuation), and `PostToolBatch` (stops the loop). On `PostToolUse` it does not block —
the tool already ran — but stderr reaches the model.

**stderr is the whole user interface of a blocking hook.** It is all the person who just
got stopped will read. Name the rule, the file, and what to do instead.

## Structured output

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

## This repo's modes

Before proposing Form 7c, check whether one of these already runs the check — anti-pattern
17. `java .claude/hooks/ArchHook.java doctor` reports what is registered.

| Mode | Does |
|---|---|
| `check` | Dependency-direction boundaries, from the blueprint's rules |
| `format` | Formats what was just written |
| `schema` | Frontmatter, `.mcp.json`, `settings.json` hooks, and frontmatter injections |
| `tests` | Tests of the modules a turn touched |
| `guard` | Blocks a design skill from writing `src/`, and freezes approved specs |
| `audit` | Execution trail — off in this repo, on in the generated project |
| `compose` | Compose services up, port conflicts, image tag vs. Testcontainers |
| `doctor` | Everything above, as a report |

## Two things that bite

- **`.claude/settings.json` is read only at session startup.** A hook written now does
  nothing until `claude` is restarted. Always say so when reporting.
- **A mode that throws exits 0.** The top-level catch in `main` swallows it, and the hook
  looks like it passed. Run a new mode by hand once before calling it done.
