# 0001 · Schema of required fields for Claude Code extension files

- **Date:** 2026-09-05
- **Scenario:** "I want to create a schema of required fields for each type of Claude
  extension: skill, subagent, rules, hooks. On every new creation and edit of those .md
  files this should be mandatory and blocking. Deterministic, not interpreted."
- **Decision:** Option A and Option B, both executed on 2026-09-05, in four separate
  commits on branch `feat/schema-extensoes-claude`.
- **Status:** approved by Lucas Fernandes, on 2026-09-05

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | An invented field passed **and** a required field was missing, repeatedly | Eliminates "don't create anything preemptively" |
| 2 — Trigger | Writing or editing an extension file | — |
| 7 — Enforceability | Blocking, deterministic, not interpreted | **Eliminates all five forms.** Hook |
| 8 — Destination | Both — this repo and the generated project | Requires step 7 of `project-bootstrap` |
| 9 — Integration (data) | Schema in JSON, not hardcoded | Aligns with invariant 7 |
| 9 — Integration (code) | New subcommand in `ArchHook.java`, not a new Java file | Avoids 2 JVM starts per `.md` edit |
| 9 — Targets | 3 `.md` types + `hooks` block of `settings.json` | "Hooks" has no `.md` file of its own |
| Rigor | Missing required fields **and** unknown fields, both block | Closes the runtime's failure mode |

## Facts verified in this session

1. **`claude plugin validate` catches none of this.** A test skill in scratchpad with
   `metadata:`, `disallowedTools` (camelCase in a skill) and `campo-que-nao-existe: 42`
   returned `✔ Validation passed`, exit 0. It doesn't look at `.claude/agents/`, nor at
   `.claude/rules/`, nor at `settings.json`.
2. **4 of the 6 rules lack `status`** — `architecture-ddd.md`, `code-quality.md`,
   `error-handling.md`, `naming.md`. Only `00-index.md` and `api-rest.md` declare it.
   A strict validator would block the repository on the first `Stop` without cleanup
   before wiring the hook.
3. **`api-rest.md` and `00-index.md` have YAML comments inside the frontmatter.** The
   parser has to tolerate them, not treat them as keys.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| A | `ArchHook.java schema` + `.claude/schemas/extensions.json` + wiring | 10 | **Approved — executed on 2026-09-05** |
| B | Preparation within this skill's scope | 8 | **Approved — executed on 2026-09-05** |
| C | Rule `.claude/rules/claude-extensions.md` | 4 | Rejected — vetoed by invariant 1 |
| D | Create nothing, trust `claude plugin validate` | 3 | Rejected — killed by fact 1 |

### Option A — hook + schema as data (score 10)

**Motivator:** axis 7. First row of the decision table in `references/decision-matrix.md`
§ 2: what has to happen always, without depending on the model's judgment, is a hook.

**Pros:** guarantee instead of persuasion. Schema as data satisfies invariant 7 — a new
field is one line of JSON, without touching Java. Strong precedent: `ArchHook` already has
subcommands `check`, `format`, `tests` and `doctor`, and is already registered in
`PostToolUse` with matcher `Write|Edit`.

**Cons:** `.claude/settings.json` is only read at session start — wiring the hook requires
restarting `claude`. Strict rigor means a new native field in official documentation
blocks until it enters the JSON. Requires the cleanup commit from fact 2 first.

**Out of scope for the skill that produced this record.** Its Contract says "Does not
write `.claude/settings.json`, `.claude/hooks/**`." Reason stated in § Out of scope: a new
subcommand in `ArchHook` is infrastructure change, with its own test and commit.

### Option B — preparation within scope (score 8)

**Motivator:** axes 1 and 9. The schema already exists today as prose, inside
`.claude/skills/claude-code-architect-designer/references/frontmatter-fields.md`. If
Option A creates the JSON with nothing else, there would be two owners of the same rule —
invariant 2 broken, and they diverge on the first new field.

**Pros:** closes invariant 2 before it breaks. Leaves the infrastructure commit
mechanical: this record carries the complete JSON, the exit codes, and the cleanup list.

**Cons:** blocks nothing by itself. Without Option A it's documentation.

**Points cut on the rubric:** criterion 4 (enforcement) — depends on persuasion, by
design. The guarantee is Option A.

### Option C — rule under `rules/` (score 4)

Vetoed by invariant 1: a rule about skill and agent fields would have to name them, and
`rules/` is a leaf. Second cut on criterion 4 of the rubric: delivers persuasion where the
request was for a guarantee. Recorded so no one reproposes it without reading this.

### Option D — create nothing (score 3)

Killed by fact 1.

## Specification of Option A

Written here so the infrastructure commit is mechanical and doesn't repeat the interview.

### `.claude/schemas/extensions.json`

```json
{
  "$comment": "Sole owner of recognized frontmatter fields. Source: official Claude Code docs. Add a field here, never in Java.",
  "types": {
    "skill": {
      "match": ".claude/skills/*/SKILL.md",
      "case": "kebab",
      "required": ["name", "description"],
      "allowed": [
        "name", "description", "when_to_use", "argument-hint", "arguments",
        "disable-model-invocation", "user-invocable", "allowed-tools",
        "disallowed-tools", "model", "effort", "paths", "context", "agent",
        "background", "hooks"
      ]
    },
    "agent": {
      "match": ".claude/agents/*.md",
      "case": "camel",
      "required": ["name", "description"],
      "allowed": [
        "name", "description", "tools", "disallowedTools", "model",
        "permissionMode", "maxTurns", "skills", "mcpServers", "hooks", "memory",
        "background", "effort", "isolation", "color"
      ]
    },
    "rule": {
      "match": ".claude/rules/*.md",
      "required": ["status"],
      "allowed": ["paths", "status"],
      "enum": { "status": ["active", "draft", "deprecated"] }
    }
  },
  "settings": {
    "match": ".claude/settings.json",
    "hook_entry": {
      "required": ["type", "command"],
      "allowed": ["type", "command", "args", "timeout", "statusMessage", "if"]
    }
  },
  "forbidden_everywhere": ["metadata"]
}
```

`forbidden_everywhere` exists because `metadata:` is the invented field this repository
already used and removed. Blocking it by name gives a better error message than "unknown
field."

### `ArchHook.java schema` subcommand

Reuses `filePath(stdin)`, `relative(abs)` and `err(...)`, which already exist.

| Input | Behavior |
|---|---|
| Path doesn't match any `match` | exit 0, silent |
| File without a `---` block at the top | exit 2, "missing frontmatter" |
| Field from `required` absent | exit 2, names the field and the type |
| Key outside `allowed` | exit 2, names the key and suggests the right convention when the same key exists in the other `case` |
| Key in `forbidden_everywhere` | exit 2, own message |
| Value outside the `enum` | exit 2, lists valid values |

Parser: only the block between the first and second `---` line. A key is what's before
the first `:` on a non-indented line. Lines starting with `#` are comments and are
ignored — fact 3. No dependencies: no YAML library.

No-file-argument mode (`schema` called from `Stop`): scans all `match` patterns and
reports all errors at once, not just the first. There are ~14 files.

Also add to `doctor()`: a line saying whether the schema exists and how many files pass.

### `.claude/settings.json`

```
PreToolUse   matcher Write   if Write(.claude/**/*.md)   → schema (validates tool_input.content)
PostToolUse  matcher Edit    if Edit(.claude/**/*.md)    → schema (validates the file on disk)
Stop                                                     → schema (full sweep)
```

`Write` validates beforehand because `tool_input` carries the whole `content`. `Edit`
doesn't: it carries `old_string`/`new_string`, and reconstructing the patch inside the
hook is fragile. Hence the `PostToolUse` + `Stop` pair — the file may reach disk invalid,
but the turn doesn't close with it there. Source: `@claude-help.md` § 8, "Exit codes."

**Resolved.** The `Stop` cycle brake already had precedent in the repository itself: the
`tests` mode guards itself with `"stop_hook_active": true` on stdin since it exists
(`ArchHook.java`, `tests` mode). The `schema` mode uses the same guard. The field is still
not documented in `@claude-help.md` — worth adding it there.

### Mandatory commit order

1. **Cleanup.** `status: active` on the 4 rules from fact 2. Without this, step 3 blocks
   the repository.
2. **Data + validator.** `.claude/schemas/extensions.json` and the `schema` subcommand.
   Testable by hand: `java .claude/hooks/ArchHook.java schema` without wiring any hook.
3. **Wiring.** The three blocks in `.claude/settings.json`. Requires restarting `claude`.
4. **Propagation.** Step 7 of `project-bootstrap/SKILL.md` — the generated project
   receives `ArchHook`, so it also has to receive `extensions.json` and the
   `settings.json` blocks. Without this, the copied hook fails to open the schema.

## Propagation

### Done on 2026-09-05 — branch `feat/schema-extensoes-claude`

| File | Change |
|---|---|
| `.claude/rules/architecture-ddd.md` | `status: active` — cleanup, step 1 |
| `.claude/rules/code-quality.md` | `status: active` — cleanup, step 1 |
| `.claude/rules/error-handling.md` | `status: active` — cleanup, step 1 |
| `.claude/rules/naming.md` | `status: active` — cleanup, step 1 |
| `.claude/skills/claude-code-architect-designer/references/frontmatter-fields.md` | "Who owns this list" section: owner today, owner starting from the validator's commit. Correction about what `claude plugin validate` doesn't catch |
| `CLAUDE.md` | Invariant 10, warning about `validate`, routing line |

| `.claude/schemas/extensions.json` | Created with the content of the "Specification" section |
| `.claude/hooks/ArchHook.java` | `schema` mode, JSON parser, line in `doctor()`. 260 → 647 lines |
| `.claude/settings.json` | `PreToolUse`/Write, `PostToolUse`/Edit, `Stop` |
| `.claude/skills/project-bootstrap/SKILL.md` | Step 7 becomes three parts; the autonomy check runs `schema`; output contract |
| `.claude/skills/project-bootstrap/templates/settings.json.example` | Same three triggers, for the generated project |
| `references/frontmatter-fields.md` | Becomes derived; the JSON is the owner |

### Divergences from the specification

None in the contract. Two in detail:

1. **JSON parser, not regex.** The specification didn't say how to read stdin or
   `settings.json`. Regex over JSON breaks on escaped quotes and nesting, and these are
   two places where breaking silently is worse than not validating. About 50 lines, no
   new dependencies.
2. **`doctor` runs the full sweep.** The specification asked for "a line saying whether
   the schema exists and how many files pass." It distinguishes three states: no schema,
   schema present and everything passes, schema present and N files invalid.

### Verification performed

Test repository in scratchpad with 9 planted violations — `metadata:`, `disallowedTools`
in a skill, an invented field, an agent without `description`, `max-turns` in an agent,
`status: activo`, a rule without frontmatter, a hook entry without `command`, an invented
field in a hook entry. All 9 caught, exit 2, each with the right message and with the
convention suggestion where applicable.

Pass cases confirmed: valid `Write`, file outside territory, second `Stop` pass, missing
schema (warns and exits 0). Invalid `Write` blocks with the file still absent from disk;
`Edit` falls through to disk as designed.

### To do

| What | Why |
|---|---|
| Restart `claude` | `.claude/settings.json` is only read at startup; until then the new hooks don't run |
| Add `stop_hook_active` to `@claude-help.md` § 8 | Used by two `ArchHook` modes and undocumented |
| Run an `/init-project` end to end | Step 7 changed and the autonomy check gained a command; never exercised with the schema |

Goes to the generated project: **yes**, via step 7 of `project-bootstrap`. The
`extensions.json` and the `settings.json` blocks travel with `ArchHook`; without them the
copied hook fails to open.
