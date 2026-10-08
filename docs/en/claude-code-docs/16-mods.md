# 16 — Mods

Portuguese version: [`docs/pt-br/claude-code-docs/16-mods.md`](../../pt-br/claude-code-docs/16-mods.md).

Official pages covered:

| Page | Link |
|------|------|
| Mods overview | <https://code.claude.com/docs/en/plugins/mods/overview> |
| Create a mod | <https://code.claude.com/docs/en/plugins/mods/create> |
| React to events | <https://code.claude.com/docs/en/plugins/mods/events> |
| Draw in the interface | <https://code.claude.com/docs/en/plugins/mods/interface> |
| Use the mods API | <https://code.claude.com/docs/en/plugins/mods/api> |
| Test a mod | <https://code.claude.com/docs/en/plugins/mods/test> |
| Troubleshoot a mod | <https://code.claude.com/docs/en/plugins/mods/troubleshoot> |
| Manage mods for your organization | <https://code.claude.com/docs/en/plugins/mods/admin> |
| Mods reference | <https://code.claude.com/docs/en/plugins/mods/reference> |
| Interface gallery | <https://code.claude.com/docs/en/plugins/mods/gallery> |
| TypeScript declarations (canonical source) | <https://github.com/anthropics/claude-code/blob/main/mods/types/claude-code.d.ts> |
| Sample mods | <https://github.com/anthropics/claude-code-playground/tree/main/claude-code/mods> |
| Built-in mods (source) | <https://github.com/anthropics/claude-code/tree/main/mods> |

Snapshot of this page: 2026-10-08, reference "as of v2.1.290". Mods exist since **v2.1.287**
(terminal) and **v2.1.286** (Desktop). The API changes between releases; the source that wins
is the `.d.ts` Claude Code writes into the mod's own `.claude-plugin/types/`
(see [Types for your version](#types-for-your-version)).

---

## What a mod is

A mod is a **plugin whose code runs inside the Claude Code process**. It is a JavaScript or
TypeScript file (the *hooks module*) that exports `register(on, options)` and registers
*handlers* for events: a tool about to run, a submitted prompt, a part of the interface about
to be drawn. Each handler can **observe**, **rewrite** or **answer** the event.

Documentation vocabulary: on these pages, "hook" means a mod's handler; the `settings.json`
hook is called a **settings hook**. Both keep existing, nothing is deprecated.

Some native features are already mods: `/diff`, `AGENTS.md` loading, the `sec-default` policy
guard, telemetry and the `plugin-authoring` skill.

### Mod vs settings hook vs skill vs MCP

| | Mod | Settings hook | Skill | MCP server |
|---|-----|---------------|-------|------------|
| What it is | Functions in a plugin, called in the Claude Code process | Shell command, HTTP or prompt on a lifecycle event | `SKILL.md` Claude reads | External process giving tools |
| What it changes | Tool calls, prompts, commands, turns and what the interface draws | Whether a tool call or prompt goes ahead, its arguments/result, added context | What Claude knows and does | Which tools Claude has |
| Draws in the interface | **Yes** | No | No | No |
| Written in | JS/TS | Script + `settings.json` | Markdown | Any language |
| Pick it when | You want a pane, a band above the prompt, your own `/command`, or to rewrite an event | You want to block/allow/log with a script you already have | You keep pasting the same instructions | Claude needs to reach an external system |

One plugin can hold all of them: mod + skill + MCP server in the same package.

### Where it runs and where it draws

| Where | Hooks run | Drawing appears |
|-------|-----------|-----------------|
| `claude` in a terminal (including IDE integrated terminal and JetBrains plugin) | Yes | Yes |
| Code tab of the Desktop app (except WSL session) | Yes | Yes, minus terminal-only elements |
| WSL session in Desktop | No (plugins unavailable) | No |
| VS Code extension chat panel | Yes | **No** |
| `claude -p` and Agent SDK | Yes | **No** |
| Remote Control (claude.ai / mobile) | Yes, in the machine's session | In the machine's terminal |
| Cloud session | Yes, for a plugin that reaches it | **No** |

Consequence: a mod that draws must check `e.surface` and fall back to text (`$.ui.log`, or
a command's `text`) where nothing draws.

---

## Anatomy

```
first-mod/
├── .claude-plugin/
│   └── plugin.json          # ordinary plugin manifest; mods add no required field
├── hooks/
│   ├── hooks.json           # { "modules": ["./register.js"] } — this is what makes the plugin a mod
│   └── register.js          # hooks module: export function register(on, options)
├── types/index.d.ts         # only when using $.state or adding a namespace; named by "types" in the manifest
└── tests/*.test.ts          # claude plugin test
```

- `hooks.json` takes **one** path in `modules`, relative to the file itself. It can also hold
  settings hooks under `hooks` in the same file.
- Accepted extensions: `.js .mjs .cjs .jsx .ts .mts .cts .tsx`. **ES module** required
  (`import`, never `require`). No Node.js, no bundler, no build step.
- `options` carries the `userConfig` values the manifest declares, with defaults.
- Inside the module there are **no** Node APIs and no `setTimeout`: everything that leaves the
  module goes through the mods API (`$`). That is what lets `claude plugin validate` list what
  a mod does without running it.

### Development loop

```
claude --plugin-dir ./first-mod           # load for one session, reload on save
claude plugin validate ./first-mod        # manifest + static analysis: hooks: and calls: lines
claude plugin test                        # runs *.test.ts, no session, sign-in or network
claude -p "/tally" --plugin-dir ./first-mod   # try a /command without an interactive session
claude --debug-file ./mod.log --plugin-dir ./first-mod   # load, skip and refusal log
```

- Every save reloads the module: `register` runs again and module-level variables reset.
- Develop **always** against `--plugin-dir`, never against the installed copy: Claude Code
  caches an installed plugin by version; edits only land after bumping `version` and
  reinstalling.
- `CLAUDE_CODE_PLUGIN_DIRS` (env, or `env` in settings) equals `--plugin-dir` for apps without
  a flag; `CLAUDE_CODE_PLUGIN_DIR_WATCH=1` makes a long `-p` run reload on save.

### Ask Claude to write the mod

The built-in `plugin-authoring` skill (`/plugin-authoring`) knows where to write and which
events your version has. The mod is born in `~/.claude/dev-mods/<session-id>/<name>/`; on the
first saved file Claude Code asks whether to enable *hot reload* for the session. That
directory is deleted after `cleanupPeriodDays`: to keep it, copy it somewhere of your own and
load with `--plugin-dir`, or publish it to a marketplace. It doesn't load in `claude -p`,
`dontAsk`, an untrusted workspace, `--safe-mode`, `--bare`, `disableAllHooks`, or under policy.

### Types for your version

On every load via `--plugin-dir`, Claude Code writes into the mod's `.claude-plugin/types/`:
`claude-code/index.d.ts` (events, methods, elements), `claude-code-tools/index.d.ts`
(built-in tool inputs, narrowing by `e.tool`), `claude-code-mcp/index.d.ts` (connected MCP
tools) and a `tsconfig.json`. **These files win over any documentation page** when they
disagree.

---

## The hook function

```javascript
on('tool.call', { tool: 'Bash' }, async ($, e, next) => { ... })
```

| Argument | What it is |
|----------|------------|
| `$` | The mods API, by namespace: `$.ui`, `$.fs`, `$.process`… Always written in full (`$.fs.read`), never assigned to a variable nor destructured — static analysis depends on it |
| `e` | The event, **deeply frozen**. To change it, pass a copy to `next` |
| `next(e)` | The next handler in the chain (another mod, or Claude Code's own behavior). Resolves to the result |
| `next.signal` | `AbortSignal` that fires when the event is abandoned (user interrupted) |
| `next.origin` | `{ plugin, tier }` of whoever fired the event; Claude Code is `{ plugin: 'engine', tier: 'core' }` |
| `next.budget` | `.ms` and `.remainingMs` of the hook's time limit |
| `next.to(e, tier)` | Skips to a later tier (`append`, `builtin`, `core`); only mods in `prependPlugins`/`appendPlugins` |
| `next.error`, `next.called` | In the `.catch` handler only: `kind` is `throw` or `timeout` |

### Three modes

| Mode | How | Effect |
|------|-----|--------|
| **Observe** | Do something and `return next(e)`; or `await next(e)`, do something, return the result | Nothing changes for Claude |
| **Rewrite** | `return next({ ...e, text: e.text.trim() })`; or change the result after `await next(e)` | Later handlers and Claude Code see the new version |
| **Answer** | Return a result **without calling `next`** | Short-circuit: later mods and native behavior don't run |

Each event has its own result shape (`{ deny }`, `{ result }`, `{ text }`, `{ decision }`…).

### Matchers

Second argument of `on`. An object whose fields are compared with the event's; a single
value, an array of values, or a regex:

```javascript
on('tool.call', { tool: 'Bash' }, hook)
on('tool.call', { tool: ['Edit', 'Write'] }, hook)
on('tool.call', { tool: /^mcp__github__/ }, hook)
```

- `'classic.*'` matches every settings hook event; `'*'` matches everything **except**
  telemetry.
- Registering the same event twice **without a matcher** fails the load:
  `on("session.start") is registered twice without a matcher`.
- The event name must be a **string literal** — a variable or a loop fails validation.

### Hook failure

A hook that throws, times out or returns the wrong shape is **skipped**, and the session
continues:

- Failed **before** calling `next`: the next handler runs in its place.
- Failed **after** `next` resolved: that result stands, nothing runs again.

A line `my-mod: tool.call hook skipped: threw Error: boom` goes to the transcript
(`--plugin-dir` session) or only to the debug log (installed mod). **A blocking hook must fail
closed** with `.catch`:

```javascript
on('tool.call', { tool: 'Bash' }, guard).catch(async ($, e, next) => {
  return { deny: 'The command guard failed, so this command was not run: ' + next.error.kind }
})
```

Without the `.catch`, a guard that timed out lets the command through.

---

## Events

Full list and exact fields: reference + `.d.ts`. Hooks on `turn.step` and `process.spawn`
are *async generators*; the rest are async functions.

### Tools

| Event | Fires when | Return |
|-------|------------|--------|
| `tool.call` | A tool is about to run, including from subagents and MCP. `e.tool` + arguments as fields (`e.command`, `e.file_path`) | `next(e)`, `{ deny: reason }`, `{ result }` |
| `tool.check` | Claude Code decides whether the call may run, **after** permission rules and settings hooks. `next(e)` resolves to their decision; `e.input` holds the arguments | `{ decision: 'allow' \| 'ask' \| 'deny', reason }` |
| `tool.describe` | Once per tool, when its description goes to Claude | `{ description, isDeferred? }` |

Patterns:

- **Hold and ask**: `await $.ui.ask('Run this command? ' + e.command, ['Run it', 'Refuse'])`
  inside `tool.call`. Time spent inside a mods API call **doesn't count** against the limit.
  In `claude -p` or if the user dismisses, `ask` rejects: start from the safe answer.
- **Post-execution**: `const r = await next(e)`; a refusal comes as `{ deny }`, a failure
  with `isError`.
- **Retry**: call `next(e)` again after `isError`.
- **Write `deny`** as an actionable instruction: Claude reads that text as the tool's result.
- `tool.check` is for a **state-dependent** decision (current branch, a value another hook
  recorded); for a fixed command or path, a permission rule is cheaper.

### Prompts and what Claude reads

| Event | Fires when | Return |
|-------|------------|--------|
| `prompt.submit` | A prompt is submitted. `e.text` | `next({ ...e, text })` (rewrite, transcript shows the new text), `next({ ...e, context: [...(e.context ?? []), extra] })` (text only Claude reads), `{ drop: reason }` |
| `prompt.fill`, `prompt.suggest` | Text is about to enter the prompt box as a draft / suggestion | `next(e)` with changed text |
| `prompt.edit` | The user edits the box (**50 ms** limit) | `next(e)` |
| `prompt.compose` | Claude Code renders the system prompt | `{ sections: [{ id, text, scope }] }` |
| `prompt.section` | Once per named section of the system prompt (`e.name`) | `{ text }` or `{ text: null }` to omit |
| `prompt.context` | Context of the conversation's first message | `{ blocks }` |
| `prompt.attachment` | Claude Code's own message (reminder). `e.type`, `e.detail` | `{ text }` or `{ text: null }` |
| `prompt.mention` | An @-mentioned file is about to be read (≥ 2.1.290) | `next({ ...e, path })` or `{ deny }` |
| `skill.prompt` | A skill's text is expanded | `{ text }` |
| `attribution.text` | Commit/PR attribution text | `{ text }` |

Text that **changes between requests** in `prompt.section`/`prompt.context`/`skill.prompt`
**invalidates the prompt cache**.

### Commands and configuration

| Event | Fires when | Return |
|-------|------------|--------|
| `command.run` | A command is about to run. `e.args` is the text after the name | `{ text }` (printed, and Claude reads it), `{}` (nothing), `next(e)` |
| `command.describe` | The command list | `{ description, argumentHint, isHidden }` |
| `config.set` | A `/config` row is about to change | `next({ ...e, value })` or `{ deny }` |
| `config.describe` | Each `/config` row | `{ label, description, isHidden }` |

### Turns

| Event | Fires when | Return |
|-------|------------|--------|
| `turn.start` | A turn begins. `e.turnId` | `next(e)` |
| `turn.step` | **One request** to the model (a turn with tools has several). `e.agentId` in a subagent. **Async generator**: `const r = yield* next(e)`; `r.usage` has `input_tokens`, `output_tokens`, `cache_read_input_tokens`, `cache_creation_input_tokens`, `model` | `yield* next(e)`, `next({ ...e, model })`, `next({ ...e, effort })`, or answer without calling the model |
| `turn.complete` | The turn ended (including aborted: `e.isAborted`). `e.answer`, `e.durationMs`, `e.usage` (totals), `e.agentId` | `next(e)` or `{ text }` for a line under the answer |

### Session

| Event | Fires when | Return |
|-------|------------|--------|
| `session.start` | Once per loaded mod, before the first prompt, and on every reload. **Not** after `/clear`, `/resume`, `/branch` | `next(e)` |
| `session.end` | Session ends or `/clear`/`/resume`/`/branch`. `e.reason`: `clear`, `resume`, `logout`, `prompt_input_exit`, `other` | `next(e)` |
| `session.compact` | Compaction imminent | `{ skip: reason }` |
| `session.receive` / `session.send` | A message arrives from / is about to go to another session or subagent. `e.text`, `e.origin.kind` (`peer`, `peer-send-message`, `task-notification`, `scheduled-trigger`; in `send`: `model` or `plugin`) | `{ consumed: reason }` / `{ isDelivered: false, reason }` |
| `session.append` | Each row the conversation keeps, before it is stored | `next({ ...e, message })` |
| `session.attach` / `session.detach` | Another app connects/disconnects | `next(e)` |
| `session.measure` | After each turn and when a plan limit changes | `next(e)` |

### Subagents

| Event | Fires when | Return |
|-------|------------|--------|
| `agent.offer` | A subagent type is offered to Claude | `{ isOffered: false }` to withhold |
| `agent.spawn` | A subagent or teammate is about to start (`e.isTeammate`) | `next({ ...e, model })` or `{ deny }` |

### Interface

| Event | Fires when |
|-------|------------|
| `ui.render` | A render site is about to be drawn |
| `ui.resolve` | On load, per app × site × mod; the result is the element table `$.ui.resolve(e)` reads |
| `ui.press`, `ui.input`, `ui.select` | A control drawn by **a mod** was used (`e.element` = `key`). Another mod sees it before the owner's callback and can change or answer in its place |
| `ui.focus`, `ui.scroll` | Focus or scroll of a pane/band is about to change |
| `ui.close` | A pane is about to close. `e.id`, `e.origin.kind`: `plugin`, `person`, `unload` |
| `ui.message` | A `Client` posted data to its mod |
| `ui.fault` | A `Client` of yours failed (`e.phase`: `load`/`render`/`run`; ≥ 2.1.289) |

### Other mods and telemetry

| Event | Fires when | Return |
|-------|------------|--------|
| `plugin.register` | A hooks module is about to load. `e.tier`, `e.uses` (events, calls, env, state — what `validate` prints) | `{ refuse: reason }` |
| `engine.create` | The mods API is being built for this mod | A changed API (add a namespace; tiers outside `user` may withhold one) |
| `telemetry.log`, `telemetry.mark` | A telemetry record. An installed mod **needs** the `{ to: 'collector' }` filter or fails `validate`; `*` doesn't match | `next(e)` or `{ deny }` |

### Settings hook events

Every `settings.json` event exists as `classic.<Event>` (`classic.Stop`,
`classic.PostToolUse`, `classic.SessionStart`…). `e` is the JSON the settings hook would get
on stdin, including `transcript_path`. `classic.SessionStart` with `e.source` in
`clear`/`resume`/`fork` is how a mod reloads state after `/clear`, since `session.start`
doesn't fire again.

### Mods API calls as events

Every `$.ns.method` call is also an event `ns.method` (`fs.read`, `model.complete`,
`ui.open`). A mod **earlier in the chain** observes, rewrites or refuses (`{ deny }`,
`{ value }`) the calls of later ones. That is how an organization restricts what mods reach.

### Chain order

1. `sec-default@builtin` (built-in guard, `cc-plugin-sec-default` in `/plugin`), mods in
   `prependPlugins`, other organization mods outside `appendPlugins`
2. Mods **you** installed (a mod runs before the ones it lists in `dependencies`)
3. Mods in `appendPlugins`
4. Other built-in mods

The first is outermost: it sees the event first and the result last, and decides whether the
others run. Within one module, hooks run in the order `register` called `on`.

**Where `PreToolUse` settings hooks sit:**

- From **managed settings**: before the first mod; a block is final, no mod sees the call.
- From any other settings file or a plugin's `hooks/hooks.json`: **after** the last mod calls
  `next`, as part of native behavior. A mod that answers `tool.call` without `next` keeps them
  from running. `tool.check` comes after all of that, so a mod can **approve** a call that a
  non-managed `PreToolUse` blocked.

---

## Mods API (`$`)

| Namespace | Methods | Notes |
|-----------|---------|-------|
| `$.plugin` | `name`, `root` | |
| `$.ui` | `resolve`, `invalidate`, `open`, `close`, `panes`, `focus`, `scroll`, `toast`, `status`, `log`, `notice`, `ask`, `copy`, `selection`, `blit` | `log(text)` = dim transcript line Claude **doesn't** read; `log(text, { to: 'debug' })` goes to the debug log. `status` = line under the prompt with `⚠ mod:`. `toast` = 4 s |
| `$.command` | `register({ name, description, argumentHint, immediate })`, `run`, `list` | Register in `session.start`. A taken native name **throws** (`"/focus" refused: it is the built-in /focus`), so register last or in `try/catch`. `immediate: true` runs during a turn |
| `$.tool` | `register({ name, description, inputSchema, isDeferred })`, `call`, `check`, `list` | Claude sees `mcp__<plugin>__<name>`; handle the call in a `tool.call` filtered on that name. `isDeferred: false` (≥ 2.1.293) exempts it from tool search |
| `$.agent` | `register`, `spawn`, `list` | |
| `$.model` | `complete({ model, system, prompt, maxTokens, timeoutMs, effort })`, `fork({ prompt })`, `classify` | `complete` has no history; check `r.isAnswered`, read `r.reason`. `fork` asks **over the current conversation**, cache-friendly. Spends the user's plan/key |
| `$.prompt` | `submit({ text, asUser })`, `read`, `fill`, `suggest`, `compose` | `submit` waits for idle and starts a turn; **don't** `await` it inside a hook that runs while Claude works |
| `$.turn` | `abort` | |
| `$.session` | `messages` (newest 4,096), `cwd`, `root`, `model`, `turns`, `id`, `repo`, `surfaces`, `usage`, `version`, `compact`, `send`, `append`, `authorize` | `usage()` → `{ startedAt, context: { tokens, window, percent }, rateLimits: [{ kind, percentUsed, resetsAt }], cost }` |
| `$.config` | `list`, `set` | |
| `$.settings` | `read` | Settings files + managed policy |
| `$.env` | `get`, `set` | Name as a string literal; shows in `env reads:`/`env writes:` of `validate` |
| `$.fs` | `read`, `write`, `list`, `exists`, `stat`, `ancestors` | Relative path resolves against the session cwd. `list` isn't recursive. `write` **isn't atomic**. 4 MiB per file |
| `$.store` | `get`, `set`, `delete`, `keys` | JSON under `~/.claude/plugins/store/`, **shared by every session on the machine**, 4 MiB. `get`+`set` isn't atomic: re-read before writing |
| `$.state` | `get`, `set` + helpers `atom`, `read`, `update`, `derive`, `memberOf` from `'claude-code'` | Reactive: a `ui.render` that reads a value redraws when it's written. Lasts the session; **resets on `/clear`, `/resume`, `/branch`**. Needs `types/index.d.ts` with `PluginState` |
| `$.clock` | `now`, `sleep`, `after`, `every` | Replace `setTimeout`/`setInterval`. Timers stop on reload. `sleep` **counts** against the time limit |
| `$.http` | `fetch(url, init)` → `{ status, ok, headers, text }` | Subject to the organization's network policy |
| `$.process` | `run(argv, { cwd, timeoutMs })` → `{ exitCode, stdout, stderr }`, `spawn` | Argument list, **no shell**. Rejects if it can't start or times out (30 s default, 10 min max). The process runs **outside the sandbox** |
| `$.mcp` | `call`, `connect` | `connect` only for a server the mod's own manifest lists |
| `$.audio` | `play`, `speak` | |
| `$.telemetry` | `log`, `mark` | Only sent when Claude Code or a built-in mod calls it |

### Where to keep state

| Keep it in | Lasts until | For |
|------------|-------------|-----|
| Module-level variable | Reload (every save in dev) | Disposable values |
| `$.state` | Session end, `/clear`, `/resume`, `/branch` | Values a drawing depends on that should survive a reload |
| `$.store` | The mod deletes it, or `cleanupPeriodDays` unused | Settings, history, what the user expects to find next time |

Pattern: copy `$.store` → `$.state` in `session.start` **and** in `classic.SessionStart`
with `{ source: ['clear', 'resume', 'fork'] }`. Without the second, after `/clear` the drawing
shows the default and the next callback **writes the default over** what was stored.

---

## Interface

### Render sites

`ui.render` fires for every site; filter with `{ component: '<Site>' }`. `e.surface` is
`terminal` or `desktop`; `e.props` holds the site's data; `e.viewport` has `columns`, `rows`,
`isFullscreen`.

| Site | What it is | `e.props` | `e.requestId` | Where |
|------|------------|-----------|---------------|-------|
| `Pane` | Sidebar on the right (wide fullscreen terminal) or framed region above the prompt. Empty until a mod opens it with `$.ui.open` | `title`, `isFocused`, `bodyColumns`, `placement` (`dock`/`inline`), `scroll.bodyRows`, `view` | The `open` `id` | Terminal, Desktop |
| `AbovePrompt` | Band above the prompt, always there, **shared** by every mod | `hasSurvey`, `isWorking`, `maxRows`, `bodyColumns`, `scroll`, `view` | One instance | Terminal, Desktop |
| `UserMessage`, `AssistantMessage` | Transcript messages | text, origin | message id | Terminal, Desktop |
| `ToolUse`, `ToolResult`, `ToolGroup` | Tool call row, result, collapsed group | name, input, result | call id | Terminal, Desktop |
| `CommandOutput` | The row a command printed | `command`, `text` | id | Terminal, Desktop |
| `AskUserQuestion` | Claude's question dialog. The tree **must hold the native reference exactly once**, with your elements above it | question, options | call id | Terminal, Desktop |
| `Spinner` | Animated line while working | `word`, `message`, `suffix`, `mode` | agent id | Terminal, Desktop |
| `ToolProgress` | A tool's live progress | `kind` | call id | Terminal |
| `TurnDuration` | The line that closes a turn | `word`, `durationMs` | id | Terminal |
| `InfoNotice`, `SessionMode`, `PromptHint` | Notices under the logo, footer modes, hint under the prompt | | | Terminal / both / both |

**The permission prompt is not a render site**: no mod changes what it shows.

In a `--plugin-dir` session an invalid tree produces
`ui.render (Pane) refused: <reason>; the engine drew its own` in the transcript, and Claude
Code draws the native version. Otherwise only the debug log records it. Empty pane = look for
that line.

### Three attitudes at a native site

```javascript
// Change a detail: keep the native drawing and change props
return next({ ...e, props: { ...e.props, suffix: ' · tool calls: ' + calls + '…' } })

// Replace: return a tree without calling next
return Text({ children: ['Claude has made ' + calls + ' tool calls'] })

// Leave it alone
return next(e)

// Compose: the native reference { type: 'engine', ref } beside your elements
const theirs = await next(e)
return Box({ flexDirection: 'column', children: [theirs, Text({ children: ['under the spinner'] })] })
```

On `AbovePrompt`, returning a tree **replaces** what later mods would draw; to keep theirs,
put `await next(e)` among the `children` of a `Box`.

### Elements

Obtained with `const { Box, Text, Button } = $.ui.resolve(e)`. In `.tsx`/`.jsx` you can use JSX.

| Element | Main props | Terminal | Desktop |
|---------|------------|:--------:|:-------:|
| `Box` | `key`, flex (`flexDirection`, `columnGap`…), `gap`, `padding`, `margin`, `width`, `height`, `borderStyle`, `backgroundColor`, `position`, `hover` | ✓ | ✓ |
| `Text` | `color` (theme key or color), `backgroundColor`, `bold`, `italic`, `underline`, `dimColor`, `inverse`, `wrap` (`wrap`, `truncate`, `truncate-start/middle/end`) | ✓ | ✓ |
| `Button` | `key`, `label`, `onPress(e)`, `hotkey` (one digit or lowercase letter), `plain`, `dimColor`, `autoFocus`, `action` (native keybinding) | ✓ | ✓ |
| `Link`, `Code`, `Markdown` | `href`/`label`; code; `text` (**not** `children`), `key` if `onLinkPress` | ✓ | ✓ |
| `Input` | `key`, `label`, `placeholder`, `value`, `submitLabel`, `onSubmit(value)`, `onInput(value)`, `autoFocus` | ✓ | ✓ |
| `Select` | `key`, `label`, `options: [{ value, label }]`, `value`, `onSelect(value)`, `autoFocus` | ✓ | ✓ |
| `Svg` | SVG document up to 131,072 chars | | ✓ |
| `Client` | `module`, `key` — a region drawn by a second file of yours (animation, pointer); no mods API, talks via `ui.message` | ✓ | ✓ |
| `Raster` | `key`, `columns` ≤ 512, `rows` ≤ 256, `cells` (base64 of code point/color/background triples in `Uint32`; `0x01000000` = default color). Animate with `$.ui.blit` without re-render | ✓ | |
| `Image` | PNG/RGBA up to 2 MiB or a path | ✓ | |

`borderStyle`: `single`, `double`, `round`, `bold`, `singleDouble`, `doubleSingle`,
`classic`, `arrow`, `dashed`, `quote`. An invalid name (`rounded`) = no border, no error.
`autoFocus`, `focus`, `closeOnEscape`, `holdToasts` accept **only `true`**; `false`
throws — add the field conditionally.

### Pane: open, close, focus

```javascript
await $.ui.open({ id: 'hello-tabs', title: 'Hello tabs', focus: true, closeOnEscape: true, rows, columns })
await $.ui.close({ id: 'hello-tabs' })
```

- A pane opened by **user action** (command, button) appears at any width. Opened **by the
  mod alone** (timer, `turn.start`) it appears only in a terminal ≥ **144 columns** (110 after
  the user opened it once). `open` resolves `{ isPlaced, reason }`. To announce without
  opening: `$.ui.toast`.
- Focus: `focus: true` is granted only with an empty prompt and nothing else focused;
  `Ctrl+X Tab`; click. Keys while focused: Tab (next control), ↑↓ (controls or scroll), Enter,
  hotkey, PgUp/PgDn/Home/End, `Ctrl+X` + arrow (resize), `Ctrl+X X` (close), Esc (back to the
  prompt). Tab and arrows are **not** rebindable — a game steers with `w a s d`.
- A digit hotkey on the **band** also fires when typed alone into an empty prompt.
- For a `/command` to open a pane **during** a turn: `immediate: true` at registration.

### Redraw

A drawing is a snapshot of the last `ui.render`. Claude Code redraws on its own when props or
width change; it does **not** redraw on a timer or when a module variable changes. Ask with
`$.ui.invalidate('ui.render')` (throttled to 10/s; 30/s in the terminal for the visible pane,
the band and the hint). `$.state` redraws by itself on write. Timer:
`$.clock.every(1000, () => $.ui.invalidate('ui.render'))` in `session.start`.

---

## Tests

`claude plugin test [dir]` runs every `*.test.ts`/`*.test.tsx`, with no session, sign-in or
network. Exit 1 on failure. Kit in `'claude-code/testing'`: `test`, `expect`, `mock`, `tier`.

```typescript
import { expect, mock, test } from 'claude-code/testing'

test('/tally reports the tool calls the mod has seen', async ($, on) => {
  on('tool.call', () => ({ result: 'ok' }))             // stub: answers in Claude Code's place
  await $.tool.call({ tool: 'Bash', command: 'ls' })    // fires the event through the mod's hooks
  await $.tool.call({ tool: 'Read', file_path: 'README.md' })
  const answer = await $.command.run({ command: 'tally', args: '' })
  expect(answer.text).toBe('Claude has made 2 tool calls since this mod loaded')
})
```

Kit rules:

- The test's `$` **acts as Claude Code**: each method fires the event of the same name through
  the mod's hooks. It is not the mods API.
- `on` registers **stubs**. A stub for a mods API call returns `{ value }`
  (`on('store.get', ($, e) => ({ value: saved.get(e.key) }))`); a stub for an event returns
  the event's result (`{ result }`, `{ text }`). `{ deny: reason }` makes the call reject.
  Errors: `returned neither { value } nor { deny }`, `no implementation for <name>`.
- **Every stub before the first call on `$`.**
- `session.start` **doesn't run by itself**: fire `await $.session.start({...})` with stubs
  for `session.start` and `command.register`.
- A hook returning `next(e)` in `ui.render` needs a stub returning a plain element.
- A `turn.step` stub is an async generator; read the stream to `done`.
- `$.ui.ask` reaches the test as a `tool.call` of `AskUserQuestion`.
- Namespace mocks: `mock.clock(on)` (`advance`, `set`, `settle`, `sleep`, `now`),
  `mock.store(on, { count: 7 })`, `mock.env(on, { CI: 'true' })`. The kit answers
  `$.ui.invalidate` and `$.state` on its own.
- Drawing: `const ui = await $.ui.mount({ plugin, component, requestId, surface, viewport, props })`
  → `ui.press({ key })`, `ui.input({ key, text, kind? })`, `ui.select({ key, value })`,
  `ui.find({ key } | { type, text })`, `ui.unmount()`. Tests the tree and its validity per
  app, **not** the painting.
- After `/clear`: don't fire `session.start`, fire `$.classic.SessionStart({ source: 'clear' })`
  and mount.
- Policy mod: `tier('prepend')` at the top of the file; `test(name, { plugins: [inline] }, fn)`
  loads inline mods to be admitted or refused. A refusal throws on the first call on `$`.
- Limit per test: 5 s, unless `timeoutMs`.

`claude plugin test` in a directory **without** a mod doubles as a diagnostic:
`no hooks module to load` (mods can load), `hooks modules are turned off here`
(`disableAllHooks` or policy), `hooks modules are turned off in this process` (turned off
remotely by Anthropic).

---

## Limits

| Limit | Value |
|-------|-------|
| A hook's own execution time per event (not counting `next` or mods API calls, except `$.clock.sleep`) | 10 s; 50 ms for `prompt.edit` |
| `.catch` handler | 1 s |
| All `session.end` hooks together | The `SessionEnd` budget (1.5 s default), after the settings hooks |
| `$.process.run` | 30 s default, 10 min max |
| `$.model.complete` `maxTokens` | 1,024 default, up to 64,000 or the model's limit |
| `$.fs.read`/`write` | 4 MiB per file |
| Text in one tree | First 100,000 chars |
| `$.store` | 4 MiB JSON total |
| `$.session.messages()` | Newest 4,096 entries |
| Redraw | 10/s; 30/s in the terminal for the visible pane, expanded band and hint |
| Toast | 4 s unless `{ timeoutMs }` |
| Pane opened without the user asking | ≥ 144 columns; 110 after opening once |
| Command, tool, agent, pane names | letters, digits, `_`, `-`, up to 64 |
| One test | 5 s unless `timeoutMs` |

A hook that exceeds its time is **skipped**; a call that exceeds a size is **rejected**.

---

## Security and governance

### What a mod reaches

Runs **with the user's permissions, no sandbox**. It can: read and write any file the user
can, start programs, reach the network; read env vars and settings (API keys included); see
every prompt and tool call; rewrite a prompt/tool call, submit a prompt as the user, message
another session; **approve a tool call before the permission prompt**; spend the plan.
Sandboxing isolates Claude's Bash, not a process the mod starts. It can't change the
permission prompt.

With `Read(.env)` denied, a mod still reads the file with `$.fs.read`. To limit that: don't
load the mod, or handle the call in a policy mod.

### Review before installing

```
claude plugin validate ./some-mod
  ❯ ./register.js hooks: session.start, tool.call, ui.render{component=Pane}
  ❯ ./register.js calls: $.fs.read, $.http.fetch, $.store.set, $.ui.open
```

| In `calls:` | Means |
|-------------|-------|
| `$.fs.read`, `$.fs.write` | Reads/writes files wherever the user can |
| `$.process.run`, `$.process.spawn` | Starts programs as the user |
| `$.http.fetch` | Network |
| `$.env.get`, `$.settings.read` | Env vars and settings, which can hold keys (`env reads:` names each) |
| `$.env.set` | Changes env for Claude Code and every command/MCP started afterwards (`env writes:`) |
| `$.mcp.call` | Calls an MCP tool under the session's rules |
| `$.model.complete` | Spends plan/key |
| `$.prompt.submit` | Submits a prompt, including as the user |
| `$.session.send` | A message another session reads |

In `hooks:`: `tool.call` and `prompt.submit` = sees and changes everything; `session.append` =
rewrites history before storing; `ui.render{component=AskUserQuestion}` = redraws the
question dialog; `tool.check` = approves/denies before the permission prompt.

Claude Code **refuses to load** a mod whose use of the mods API static analysis can't read.
`validate` also fails a name that looks like Anthropic's (`claude-` prefix).

### Turning off

| Scope | How |
|-------|-----|
| One mod | Disable/uninstall in `/plugin` → **Installed** |
| Every installed one, one session | `claude --safe-mode` (also disables your other customizations) |
| Every one you installed, always | `"disableAllHooks": true` in `~/.claude/settings.json` (also stops settings hooks and status line; what the organization manages keeps running) |

`disableAllHooks` and `allowManagedModsOnly` stop the mod and **leave the rest of the plugin**
(skills, agents, MCP) loading. `--bare`, `--safe-mode` and `disableAllHooks` do **not** stop
built-in mods; each has its own switch in `/plugin`. `CLAUDE_CODE_ENABLE_FUNCTION_HOOKS`
(early access) is ignored since 2.1.287 — `0` turns nothing off.

### Managed settings

| I want | Settings |
|--------|----------|
| No installed mods, hooks untouched | `pluginConfigs["cc-plugin-sec-default@builtin"].options.allowManagedModsOnly: true`, no mods of your own |
| No mods and no hooks, managed included | `disableAllHooks: true` |
| Only the organization's mods | `allowManagedModsOnly` + install the mods **so they count as the organization's** |
| Any mod from approved marketplaces | Marketplace restrictions + `disableSideloadFlags: true` (rejects `--plugin-dir`, `--plugin-url`, `--agents`, `--mcp-config`; and mods Claude writes in a session) |
| Any mod, with yours checking the others | Install yours and list it with `sec-default@builtin` in `prependPlugins` |

A mod **counts as the organization's** only when: managed `enabledPlugins` enables it,
managed settings name the marketplace as a **local directory by absolute path**
(`extraKnownMarketplaces` with `source: "directory"`), and the marketplace lists it by
**relative path** (loaded *in place*). A plugin copied into the cache (GitHub, git, URL, npm)
counts as the user's even with managed `enabledPlugins`. The directory must be writable by an
administrator only.

Built-in guard `sec-default@builtin`: loads when the machine has managed settings **or** the
user is signed in with a Team/Enterprise plan. Protects what is managed (managed hooks, the
system prompt, managed `CLAUDE.md`, settings, managed MCP tools). Deny rules and managed
`PreToolUse` win over mods (`allowModsToOverrideDenyRules` relaxes that). Fails **closed**:
unable to read managed settings, it refuses every user mod. Options are read only from
managed settings, under the key `cc-plugin-sec-default@builtin` (in `prependPlugins`,
`sec-default@builtin` is accepted). Setting `prependPlugins` **replaces the default**: name the
guard in the list or it won't load.

`prependPlugins`/`appendPlugins` in user settings only count on a machine without managed
settings and a user outside Team/Enterprise. A repository **never** sets those two.

### Policy mod

A mod in `prependPlugins` handles `plugin.register` (refuses by `e.tier === 'user'` and
`e.uses.calls`) and intercepts mods API calls by name (`on('fs.write', …)`). Fail closed with
a `.catch` that returns `{ refuse }` for `tier === 'user'`. Log via
`$.ui.log(msg, { to: 'debug' })` or `$.http.fetch`. Limits: the session runs without your mod
if the hooks worker crashes 3 times (`/reload-plugins` reloads) or under `--safe-mode`.

---

## Troubleshooting

First `claude plugin validate`. Then the line Claude Code writes when it skips something:
transcript (`--plugin-dir` or hot reload session), debug log (`claude --debug` /
`--debug-file`), or stderr (`claude -p` with `--plugin-dir`).

| Symptom / message | Cause | Action |
|-------------------|-------|--------|
| `/plugin` doesn't list the mod in the `N mod active` line | Module didn't load | Look for `hooks module <name> not loaded: <reason>` in the debug log |
| `not loaded: disableAllHooks in managed settings` / `only managed plugins and built-in plugins run` / `installed plugins ... (--bare)` / `another plugin of that name loads first` | Policy, `--bare`, duplicate name | Per the reason |
| `refused by cc-plugin-sec-default: mods are limited to your organization's by policy (allowManagedModsOnly)` | Organization guard | Admin |
| `tried to lift a deny rule in your settings` | `tool.check` approved a call a deny rule refuses; the call stays denied | Admin: `allowModsToOverrideDenyRules` |
| `validate` passes with no `hooks:` line | `hooks.json` without `modules` | Add `"modules": ["./register.js"]` |
| `hooks module did not load: <file:line>` | Module top-level threw | Fix |
| `options do not fit plugin.json userConfig` | `pluginConfigs` outside the schema | Fix the value |
| Nothing loads in a new directory | Trust prompt unanswered | Open interactive `claude` and accept |
| `<mod>: <event> hook skipped: threw/timeout/...` | Hook threw, timed out or returned the wrong shape | Fix; `.catch` if it blocks |
| `<mod> registered /x but no command.run hook answered it` | No hook, wrong matcher, hook returned `next(e)`, or hook skipped (`focus: false` in `open` is one path) | See `hook skipped` |
| `<mod> was unloaded: it crashed the hooks worker` | Hook blocked the thread (loop without await) | Fix |
| `mods that run in the hooks worker are off for this session: it crashed 3 times` | Worker crashed 3× with no culprit | `/reload-plugins` |
| `a hook changed this call's input after the model wrote it` (auto mode) | A mod/hook changed the input after the classifier | Re-issue; if it repeats, turn off the mod or leave auto mode |
| Empty pane or native content | Invalid tree | `ui.render (Pane) refused: <reason>` |
| `$.ui.open` runs and nothing appears | Narrow terminal, not user-initiated | Open from a command/button; read `isPlaced` |
| Dead hotkeys | Pane without focus | `Ctrl+X Tab`, click, or `focus: true` |
| Edits don't take effect | Editing the installed copy | `--plugin-dir` |
| Value resets on reload / after `/clear` | Module variable / `$.state` | `$.store` + reload in `classic.SessionStart` |

Debug log: `claude --debug-file ./mod-debug.log --plugin-dir ./first-mod`, then
`tail -f ./mod-debug.log | grep first-mod`. A loaded mod shows as
`hooks module first-mod@inline loaded (worker, environment 2, tier user); events: …`.
Broken reload: `reload failed, the previous version stays loaded: <reason>`, and the previous
version keeps running.

---

## Practical rules

1. **A rule that must always hold is still a settings hook or `permissions`.** A mod doesn't
   draw in VS Code, `-p`, cloud or mobile, and `--safe-mode` turns it off. A mod that blocks
   is a UX layer over a guarantee, not the guarantee.
2. **A mod guard fails closed** with `.catch` → `{ deny }`. Without it, timeout = the command
   goes through.
3. **`deny` is text Claude reads**: write the way out, not just the refusal.
4. **Check `e.surface` and `e.agentId`.** Fall back to text where nothing draws; filter
   subagents in `turn.step`/`turn.complete` when you only want the main conversation.
5. **`$.store` is shared across sessions and not atomic**: one key per item, re-read before
   writing.
6. **Variable text in `prompt.section`/`prompt.context`/`skill.prompt` invalidates the cache.**
7. **`claude plugin validate` is the security review**: read `hooks:` and `calls:` of every
   third-party mod before installing. Mods **are not sandboxed**.
8. **Develop with `--plugin-dir`**, test with `claude plugin test`, and state the tested Claude
   Code version in the README — the API changes between releases.
