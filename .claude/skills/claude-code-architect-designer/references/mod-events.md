# Mods — events, render sites, calls, and the rules a mod here follows

Reference for the `claude-code-architect-designer` skill, Form 9. Loads only when it is
invoked, same discipline as `hook-events.md` and `mcp-fields.md`.

Source: `docs/en/claude-code-docs/16-mods.md` (snapshot of the official mods pages) and,
above both, the `.d.ts` Claude Code writes into a loaded mod's `.claude-plugin/types/`
(`claude --plugin-dir .claude/mods/<name>` writes it on load). **The `.d.ts` wins** when it
disagrees with this page or with the docs — the mods API changes between releases, and
this page gets corrected.

## Who owns this list

**The owner is the `mods` block of `.claude/schemas/extensions.json`**: `events`,
`gating_events`, `forbidden_calls`, `process_allow`, `deny_markers`, `min_version`,
`ci_version`. `ArchHook.java schema` reads it and blocks on it; this page only explains it.
An event added here and not there is still refused. When adding one: JSON first, then the
table, then `java .claude/hooks/ArchHook.java schema`.

Design and rationale: `@.claude/decisions/0131-mods-in-architect-designer.md`.

---

## Where a mod lives here

```
.claude/mods/
├── .claude-plugin/marketplace.json   name `nerviz-mods`; one entry per mod, source "./<name>"
└── <name>/
    ├── .claude-plugin/plugin.json     name = directory name, never `claude-…`
    ├── .claude-plugin/types/          written by the engine on load — gitignored by itself
    ├── tsconfig.json                  written by the engine: extends the types folder
    ├── hooks/hooks.json               { "modules": ["./register.ts"] } — exactly one
    ├── hooks/register.ts              export const register: Register = (on) => { … }
    └── tests/*.test.ts                claude plugin test
```

`.claude/settings.json` registers the marketplace and turns each mod on:

```json
"extraKnownMarketplaces": { "nerviz-mods": { "source": { "source": "directory", "path": "./.claude/mods" } } },
"enabledPlugins": { "<name>@nerviz-mods": true }
```

A relative `directory` path resolves against the repository's **main checkout**, and the
marketplace registers only after the folder's workspace trust is accepted. A mod edited in a
git worktree does not load from the worktree — run it with `claude --plugin-dir` there.
Edits to a mod loaded in place take effect at the next session or after `/reload-plugins`.

## Is it Form 9 at all

Decision matrix § 2.3 decides. The short version: a mod draws for the **person**, only where
it loads (terminal, Desktop — not `claude -p`, VS Code, a cloud session, `--safe-mode`), on
top of a guarantee that already decides. A rule that must hold, text the model needs, or a
single line at the moment of a refusal is Form 7 or 8.

## Events a mod here uses most

Full list: `mods.events`. Fields and result shapes: the `.d.ts`.

| Event | Fires when | What a mod here does with it |
|---|---|---|
| `session.start` | Once per load, before the first prompt; **not** after `/clear`, `/resume`, `/branch` | Register `/commands`, take a first reading |
| `classic.SessionStart` | The settings event; `e.source` is `startup`, `clear`, `resume`, `fork` | Reload state after `/clear` — `session.start` does not fire again |
| `classic.UserPromptSubmit` | Around the settings hooks of that event | Read what a `UserPromptSubmit` hook (`guard prompt`) just decided, after `await next(e)` |
| `turn.start` / `turn.complete` | A turn begins / ends (`e.turnId`, `e.agentId` in a subagent) | Hold the turn id for `$.turn.abort`; filter subagents out |
| `turn.step` | One request to the model — an **async generator**: `const r = yield* next(e)` | Read cost or usage after the request |
| `tool.call` | A tool is about to run; `await next(e)` runs the `PreToolUse` settings hooks and the tool | Read what the guard decided (`r.deny`) and hold it behind a dialog |
| `tool.check` | The permission decision, after rules and settings hooks | Rarely: a state-dependent `ask`. Never a loosening `allow` |
| `command.run` | A `/command` — `{ command: '<name>' }` matcher | Answer without spending a turn: `{}` after drawing, `{ text }` where nothing draws |
| `ui.render` | A render site is drawn — `{ component: '<Site>' }` matcher | Draw; see below |

### Gating events

`mods.gating_events`: a hook on one of these can change whether something runs. Every such
hook ends in `.catch(($, e, next) => next(e))` — `schema` and `claude plugin validate`
(`gating hook without .catch`) both fail without it. A hook that throws is otherwise
skipped; the handler makes the failure fall through to the settings hooks and `permissions`
beneath: it replays their decision once `next.called`, and runs them once when not. A mod
here holds no refusal of its own, so the handler never needs one.

## Render sites

| Site | What | Props a hook reads (`e.props`) |
|---|---|---|
| `AbovePrompt` | The band above the prompt, **shared** by every mod | `hasSurvey` (yield to it), `isWorking`, `maxRows`, `bodyColumns` |
| `Spinner` | The working line (terminal only) | `word`, `message`, `suffix`, `mode` — rewrite with `next({ ...e, props: { ...e.props, suffix } })` |
| `Pane` | A framed region a mod opened with `$.ui.open({ id })` | `requestId` is the id |
| `ToolUse`, `ToolResult`, `TurnDuration`, `PromptHint`, … | Native rows | See the `.d.ts` |

The permission prompt is **not** a render site. On `AbovePrompt`, a returned tree replaces
what later mods would draw: put `await next(e)` among the children to keep theirs. Elements
come from `$.ui.resolve(e)`, never from an import.

## Calls

`$` is always spelled in full at the call site (`$.process.run`, never `const p = $.process`)
and helpers that take it are top-level functions — the engine reads the source to list what
a mod calls, and refuses to load one it cannot read.

| Allowed here, typically | For |
|---|---|
| `$.process.run(['java', '-jar', '.claude/hooks/ArchHook.jar', <mode>], { cwd, stdin })` | Every answer about the repository's rules. `process_allow` is `java` only |
| `$.session.root`, `id`, `surfaces`, `usage` | Where to run, which session the guard state belongs to, whether anything draws, cost |
| `$.ui.resolve`, `invalidate`, `open`, `ask`, `toast` | Drawing, redrawing, a pane, a dialog |
| `$.command.register` | A `/command`, in `session.start`, inside `try` — a taken name throws |
| `$.turn.abort({ turnId })` | Stop the turn the person chose to stop |
| `$.state`, `$.store` | Session state; state across sessions (shared by every session, not atomic) |

`mods.forbidden_calls`, refused by `schema`: `$.http.fetch` (an MCP server is the network
answer), `$.model.complete`/`fork`/`classify` (spends the person's plan), `$.prompt.submit`
and `$.session.send` (speaks as the person), `$.env.set` (changes every later command),
`$.fs.write` (a write no tool-time guard sees), `$.process.spawn`.

## No rule in TypeScript

The mod shows what `ArchHook.jar` prints. A list, path or pattern the mod needs lives in
`extensions.json` and reaches the mod through a mode's output — `guard status` hands over
`mods.deny_markers` with the phase. A phase re-derived from events in the mod is a second
owner of `guard`'s rule (invariant 2): add a read-only mode instead (Form 7c), with its own
`.claude/.ci/<Mode>Test.java`.

## Tests

`claude plugin test .claude/mods/<name>` runs `tests/*.test.ts` against the engine's own
`$`, with no session, sign-in, network or process. Stubs registered with the test's `on`
answer beneath the mod:

| Stub | Returns |
|---|---|
| A mods API call (`process.run`, `session.id`, `command.register`) | `{ value }` |
| An event (`tool.call`, `session.start`) | The event's result: `{ deny }`, `{ result }`, `{ cwd }` |
| `ui.render` beneath a hook that calls `next(e)` | A plain element |
| `$.ui.ask` | Arrives as a `tool.call` of `AskUserQuestion`: answer `{ result: { questions, answers: { [question]: label } } }` |

Fire `$.session.start({ cwd, surface, isInteractive })` yourself — it does not run on its
own — and register every stub before the first call on `$`. `$.ui.mount({ plugin, surface,
component, props })` draws a site through the mod; `find`, `press`, `unmount` read and act on
it. Prove each test red once with the defect it guards against.

## Two things that bite

- **Below `mods.min_version` nothing loads, in silence.** `java .claude/hooks/ArchHook.java
  doctor` prints the `Mods` line; `npx @anthropic-ai/claude-code@<ci_version>` runs `plugin
  validate` and `plugin test` without touching the installed CLI.
- **A hook that throws is skipped, not reported.** In a `--plugin-dir` session the line
  lands in the transcript; for a mod loaded from the marketplace, only in
  `claude --debug-file`. That is why the tests and `ModsSchemaTest` exist.
