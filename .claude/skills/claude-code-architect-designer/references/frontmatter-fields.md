# Frontmatter — fields the runtime recognizes

Reference for the `claude-code-architect-designer` skill. Loads only when it is invoked.

**The runtime silently ignores unknown frontmatter.** No error, no warning: an invented
field looks like behavior and is decoration. Before writing a field, confirm it's in one
of the tables below.

Source: `@claude-help.md` § 4, 5, and 7. Primary source: <https://code.claude.com/docs/en/skills>,
`/sub-agents`, `/memory`. Where they diverge, the official docs win — and this file gets
corrected.

## Who owns this list

**The owner is `.claude/schemas/extensions.json`**, because that's what
`ArchHook.java schema` reads and what it blocks on. A field added only here doesn't
become accepted — it still returns exit 2.

The tables below are **derived**. They exist because the JSON stores names, not meaning:
the "For what" column is the reason this file continues to exist. The names column has
to match the JSON exactly — diverging is invariant 2 breaking.

When adding a field: JSON first, then the table. Confirm with
`java .claude/hooks/ArchHook.java schema`.

Design, exit codes, and commit order:
`@.claude/decisions/0001-schema-frontmatter-extensions.md`.

---

## Skill (`.claude/skills/<name>/SKILL.md`)

| Field | For what |
|---|---|
| `name` | Identifier. Lowercase and hyphens |
| `description` | **The field that decides whether the skill is invoked.** Concrete use case first, trigger phrases after. Truncated to 1536 characters in the listing |
| `when_to_use` | Extra trigger phrases |
| `argument-hint` | Autocomplete hint, e.g. `"[usecase-name]"` |
| `arguments` | Positional names, for `$name` substitution |
| `disable-model-invocation` | `true` = only the user invokes, via `/name`. For side effects |
| `user-invocable` | `false` = only the model invokes; hidden from the `/` menu |
| `allowed-tools` | Pre-approves tools **during the turn** that invokes the skill |
| `disallowed-tools` | Removes tools from the pool while the skill is active |
| `model` · `effort` | Model/effort override while the skill is active |
| `paths` | Globs that limit automatic activation |
| `context: fork` | Runs the skill in an isolated subagent |
| `agent` | Which subagent type to use with `context: fork` |
| `background` | With `fork`, `false` = waits for the result in the same turn |
| `hooks` | Hooks registered when invoking the skill |

The **folder name** becomes the command: `.claude/skills/arch-doctor/` → `/arch-doctor`.
The frontmatter `name` follows the folder; diverging is guaranteed confusion during
diagnosis.

### Substitutions in the body

`$ARGUMENTS` · `$0`, `$1`, `$2` · `$name` (declared in `arguments:`) ·
`${CLAUDE_SKILL_DIR}` · `${CLAUDE_PROJECT_DIR}` · `${CLAUDE_SESSION_ID}` ·
`${CLAUDE_EFFORT}`.

Work in the markdown body and inside `allowed-tools` rules.

### Dynamic context injection

A `` !`command` `` line in the body is executed by the runtime **before** the model reads
it, and replaced by the output. It's used to put real state into context instead of
making the model discover it. Precedents in the repo: `arch-doctor/SKILL.md` and
`init-project/SKILL.md`.

Keep the commands cheap and deterministic: they run on every invocation, even when the
user only wanted to read the skill.

---

## Subagent (`.claude/agents/<name>.md`)

| Field | Required | For what |
|---|---|---|
| `name` | ✅ | Lowercase and hyphens. Cannot contain `:` |
| `description` | ✅ | When to delegate. Short — the sum of descriptions has a 15k-token ceiling |
| `tools` | ➖ | Comma-separated list. Without the field, inherits everything |
| `disallowedTools` | ➖ | Removes from the inherited list. Accepts `mcp__*` |
| `model` | ➖ | `sonnet`, `opus`, `haiku`, full ID, or `inherit` |
| `permissionMode` | ➖ | `default`, `acceptEdits`, `auto`, `dontAsk`, `bypassPermissions`, `plan` |
| `maxTurns` | ➖ | Maximum turns before stopping |
| `skills` | ➖ | Skills preloaded **in full** at startup |
| `mcpServers` | ➖ | MCP servers only for this subagent |
| `hooks` | ➖ | Hooks only while the subagent runs |
| `memory` | ➖ | `user`, `project`, or `local` |
| `background` | ➖ | `true` keeps it in the background |
| `effort` | ➖ | `low` … `max` |
| `isolation` | ➖ | `worktree` = runs in an isolated git worktree |
| `color` | ➖ | Display color |

Watch the **camelCase** here (`disallowedTools`, `permissionMode`, `maxTurns`) against
the **kebab-case** of skills (`disallowed-tools`, `disable-model-invocation`). Swapping
the conventions produces a silently ignored field — the most expensive failure mode on
this list.

Restricting which agents an agent can invoke: `tools: Agent(worker, researcher), Read, Bash`.

---

## Rule (`.claude/rules/<name>.md`)

| Field | For what |
|---|---|
| `paths` | Globs that make the rule auto-load when the matching files are touched |
| `status` | `active` applies now · `draft` is a proposal, don't apply · `deprecated` only for reading old code |

Supported glob patterns: `**/*.java`, `src/**/*`, `src/**/*.{ts,tsx}`,
`src/components/*.tsx`.

A rule without `paths` only enters context through explicit citation
(`@.claude/rules/<file>.md`). Declare `paths` whenever there's identifiable territory —
the automatic path doesn't depend on anyone remembering.

Precedent in this repo: `rules/naming.md` uses `paths: ["**/*.java"]`;
`rules/architecture-ddd.md` is the only one without its own `paths`, by design — the
globs come from the active blueprint's `architecture_paths` and are written into the
copy `project-bootstrap` makes for the generated project.

---

## `CLAUDE.md`

No frontmatter. Content only.

Imports another file with `@path/file.md` — but the import loads at startup, so it
**doesn't save context**: it's organization, not optimization. Use it to split a large
`CLAUDE.md` into readable files, never to pretend it shrank.

---

## What never goes in frontmatter

**`metadata:`** and everything it used to carry — ownership, `reads`, handoff,
contracts. It's not a native field: it costs tokens on every invocation and obligates
nothing. In this repo that content lives in the `## Contract` section of the file's
**body**, where the model reads it as an instruction. General rule: **everything the
model must obey lives in the body.**

---

## Diagnosis

| Symptom | Likely cause |
|---|---|
| `/name` works but the model never picks the skill | Malformed YAML: the body loads, the metadata doesn't. Run with `--debug` |
| The skill doesn't fire | `description` too abstract. Concrete use case first |
| The skill fires too much | Missing `paths`, or it should be `disable-model-invocation: true` |
| The field does nothing | Not native, or in the wrong convention (camelCase vs kebab-case) |
| The hook doesn't run | `.claude/settings.json` is only read at startup — restart `claude` |

Batch validation: `claude plugin validate .claude/skills`. **Does not replace the
schema.** Tested: it accepts `metadata:`, accepts `disallowedTools` in a skill (camelCase
where it should be kebab-case), and accepts an invented field, all with
`✔ Validation passed`. It also doesn't look at `.claude/agents/`, `.claude/rules/`, or
`.claude/settings.json`. It's for malformed YAML, not for wrong fields.
