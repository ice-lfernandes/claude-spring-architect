# Claude Code runtime pitfalls

Primary source: `@claude-help.md`, `.claude/schemas/extensions.json` (the `settings`,
`injections`, `arguments` and `types` blocks), and what
`java .claude/hooks/ArchHook.java schema` checks.

## Why this page exists

Every item below is a **runtime** trap, not a decision of this repository: it holds for any
project that uses skills, agents, hooks or MCP, and in all of them the failure is **silent** —
nothing errors, nothing warns, and the piece simply does not do what its author believes it
does.

They used to live in `@CLAUDE.md` § Known pitfalls, which is read in every session. A runtime
fact does not need to be permanently in context: it needs to be where whoever writes a skill,
a hook or a `.mcp.json` will look. `@CLAUDE.md` keeps the pitfalls of **this repository**
(write territories, spec freezing, who writes `pom.xml`, `audit` being off here) and points
here with one row of its routing table.

What is **not** here: the frontmatter fields the runtime recognizes, which are data with a
single owner in `.claude/schemas/extensions.json` and are described in
[01-file-types.md](01-file-types.md).

## Skills

**A skill cannot have the name of a native slash command.** The folder name becomes the
command, and `/doctor`, `/init`, `/context`, `/memory` already exist in the runtime. That is
why this repo's diagnostic skill is called `arch-doctor`. Shadowing a native command produces
no error — it runs the wrong command. `ArchHook.java schema` fails the folder by name, against
`types.skill.native_commands` in `.claude/schemas/extensions.json` — a list that has to grow
when the runtime gains a command.

**`$ARGUMENTS` in the body of a skill is interpolated at every occurrence**, not only under
`## Target`. A sentence that talks *about* the argument reaches the model with the real value
inside it: `/new-feature`'s "invoke `test-architect` with empty `$ARGUMENTS` (setup mode)"
arrived as "with empty `UC-003-initiate-kyc-verification` (setup mode)" — an order to use
setup mode, naming the argument that means design mode. Write "the argument" or "the target
above"; `ArchHook.java schema` rejects the literal, reading `arguments` from
`.claude/schemas/extensions.json`.

**`allowed-tools` with `Bash(command:*)` checks each segment of the pipe separately.** An
injection `` !`a | b | c` `` in the body needs a rule for `a`, `b`, and `c`; miss one and the
whole command is blocked before it runs. Write injections as a single command
(`ls .claude/skills`, not `find … | sed | sort`). This only affects skills that restrict Bash:
`allowed-tools: Bash` without a filter lets the whole pipeline through.

**A frontmatter `` !`…` `` injection runs in the session's persistent shell, at whatever cwd
it currently holds.** A `cd` into a skill directory in one `Bash` call contaminates every
injection of every skill invoked afterward, and a relative `test -f` then reports a file as
absent while it exists. Read a template with `Read` at an absolute path, never `cd` + `cat`.
Every injection in this repo resolves paths from `"${CLAUDE_PROJECT_DIR:-.}"`, and
`ArchHook.java schema` blocks one that doesn't — a genuinely cwd-independent injection needs a
regex in `injections.exempt_patterns`.

**Everything the model must obey lives in the body of the file**, never in frontmatter.
`metadata.*` was removed from skills and agents: ownership, `reads`, `handoff` and contracts
live in the body's `## Contract` section. Do not put `metadata:` back into a `SKILL.md` — it
costs tokens on every invocation and enforces nothing.

## Frontmatter and validation

**The runtime silently ignores unknown frontmatter.** An invented field is decoration, not
behavior. List of native fields in `@claude-help.md`.

**`claude plugin validate` does not validate fields.** It accepts `metadata:`, accepts
camelCase in a skill, and accepts an invented field, always with `✔ Validation passed`. It
does not look at `.claude/agents/`, `.claude/rules/`, or `.claude/settings.json`. It catches
malformed YAML, nothing else. What validates fields is
`java .claude/hooks/ArchHook.java schema`.

## Hooks

**`.claude/settings.json` is only read at session startup.** Editing hooks mid-session has no
effect — `claude` must be restarted.

**A hook fails silently in four ways, none an error.** Unknown event name — never fires.
`matcher` on an event that doesn't read one — filters nothing. Pipe or `&&` inside `"command"`
— part of the filename (exec form: `command` is the binary, `args` the arguments). A mode that
throws — exits 0 through `main`'s catch, looks like it passed. `schema` catches the first
three, from the `settings` block of `.claude/schemas/extensions.json`; the fourth only by
running the mode by hand.

**Only the hook-protocol modes of `ArchHook.java` read stdin** — `check`, `format`, `tests`,
`schema`, `audit`, `guard`. Invoking one of those by hand without `</dev/null` blocks until
something closes stdin, with no output: a command that looks hung, not failed. `export`,
`doctor` and `compose` are invoked by people and read nothing.

## `AskUserQuestion`

**It rejects a question with fewer than 2 options, and rejects the whole batch with it:**
`InputValidationError ... "too_small" ... path: ["questions",1,"options"]`. A question with one
option isn't a question — decide it, and record the decision where the answer would have gone.
The batch also has an upper bound of 4 questions per call. The generated project carries the
same pitfall in its own `CLAUDE.md`, from
`project-bootstrap/templates/root.CLAUDE.md.example`.

## MCP

**`.mcp.json` is only read at session startup**, same as `settings.json`. Adding or editing a
server mid-session has no effect until `claude` is restarted.

**A project-scoped server in `.mcp.json` needs one-time human approval** the first time it
loads (`claude mcp list` shows pending ones). That prompt is the trust boundary a cloned
repository can't skip — never work around it with `enableAllProjectMcpServers`.

**Server precedence is `local > project > user`, silently.** A personal server with the same
name as the team's `.mcp.json` one shadows it — no warning either way.

**An unset `${VAR}` in `.mcp.json` doesn't fail generation.** The server loads with the literal
`${VAR}` text and fails to connect at runtime instead. `claude mcp list` surfaces the
missing-variable warning; frontmatter schema validation does not.
