# 0053 · Extend `claude-code-architect-designer` to cover hooks and `permissions`

- **Date:** 2026-09-26
- **Scenario:** `@CLAUDE.md` invariant 6 says that a rule which must always hold is a
  hook or `permissions.deny`, and no piece owned writing one. The designer skill ended
  that branch of its own interview (axis 7) with "propose and stop", so every scenario
  that classified as a guarantee left the skill with nothing produced. Meanwhile
  `.claude/schemas/extensions.json` already carried a `settings.hook_entry` block with
  no owner deciding when a hook should exist, and `ArchHook.java` validated a hook
  entry's fields while accepting an unknown event name in silence.
- **Decision:** Form 7 (new), split 7a (`hooks` in `.claude/settings.json`), 7b (`hooks:`
  in a skill's or agent's frontmatter) and 7c (a new mode inside `ArchHook.java`), plus
  Form 8 (new) for `permissions.allow`/`deny`. The skill writes all of them, after
  approval. Precedent and shape: `@.claude/decisions/0033-mcp-in-architect-designer.md`,
  which did the same for MCP.
- **State:** approved by Lucas Fernandes, on 2026-09-26

## Interview

Four explicit questions were put to the user — not the skill's own axis interview, since
this decision is about the skill's own scope, one level up. Same method as 0033.

| Axis | Answer | Options it eliminated |
|---|---|---|
| How far into infrastructure does the skill write? | Registration **and** the new mode in `ArchHook.java` | "Entry yes, Java no" (mode stays propose-and-stop); "one new hook file per hook" |
| Does `ArchHook.java schema` validate hooks now or later? | Now — event name **and** shape | Only the event enum; deferring, shipping Form 7 as persuasion |
| Does `permissions.deny` come in with hooks? | Yes | Hook only, leaving the skill writing a hook entry in the same file where it may not write a `deny` line |
| Rubric: fold the always-runs cost into the existing eight, or add one? | Add a 9th criterion | Doubling the weight of criteria 3 and 5 |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Forms 7a/7b/7c + Form 8, skill writes, validated by `ArchHook.java schema`, 9th rubric criterion | 9 | **Approved** |
| 2 | Forms 7a/7b only — registrations, no Java, no `permissions` | 6 | Rejected — a registration that has no mode to call is half an answer, and the `deny` line stays orphaned in the very file the skill now edits |
| 3 | Status quo: hooks stay out of scope | 4 | Rejected — leaves invariant 6 with no owner and `settings.hook_entry` with no designer, which is the gap this opened |

### Option 1 — Forms 7 and 8, skill writes (score 9)

**Motivator:** axis 7 of the skill's interview already classified scenarios as "must
always hold" and then had nowhere to send them; `extensions.json` already validated hook
entries with no one deciding what to write into them.

**Pros:** closes the guarantee side of § 1 of the decision matrix instead of half of it;
gives invariant 6 an owner; reuses the existing enforcement path (`ArchHook.java schema`
over a JSON file) rather than inventing machinery; folds the generated project's hooks
into validation for the first time, by making `settings.match` a list.

**Cons — two, both real:**

1. **An apparent cycle in the architecture diagram.** `hooks/ + settings.json` sits above
   `skills/` and verifies it; a skill that writes a hook points back. Resolved by naming
   the edge design-time, exactly like `project-bootstrap` writing `src/` — at runtime the
   direction is unchanged. Written into the diagram itself, not left implicit.
2. **The skill now edits 2741 lines of Java with no test suite of its own.** This was the
   original reason hooks were out of scope. Mitigated, not eliminated: a `templates/hook-mode.java.example`
   that fixes the shape, a mandatory decision record for every 7c, the obligation to read
   every list from `extensions.json` instead of hardcoding it, and a validation step that
   runs the new mode by hand — a mode that throws exits 0 through `main`'s top-level catch
   and looks like it passed.

**Points cut in the rubric:** one on criterion 5 (maintenance cost), for con 2.

### Option 2 — registrations only (score 6)

**Motivator:** smallest write surface; keeps Java out of the skill's reach.

**Cons:** the common case (reusing an existing mode) works, and every case that needs a
new check dead-ends in the same place the skill dead-ended before — one level deeper.
Leaves Form 8 out while the skill writes to `settings.json` anyway, which is a line drawn
by file type rather than by principle. Same objection 0033 raised against its own
option 2.

### Option 3 — status quo (score 4)

Caps at ≤ 4 on criterion 2: invariant 6 names a form that no piece owns, which is the
definition of a norm with no enforcement path.

## What the enforcement now catches

Five failures the runtime accepts without a word, all driven by data in
`extensions.json`'s `settings` block:

| Check | Data |
|---|---|
| Event name that exists nowhere — the hook never fires | `hook_events`, plus `hook_events_extra` as the escape hatch, since claude-help.md § 8 ends its list with "among others" |
| Group key other than `matcher`/`hooks` | `group_allowed` |
| `matcher` on an event that never reads one — looks like a filter, filters nothing | `matcher_events` |
| `type` this repo has no handler for | `entry_types` |
| Shell string where the executable goes | `command_forbidden_chars` |
| `timeout` zero or negative | — |

Plus a sixth, by extension rather than by new code: `settings.match` became a list, so
`project-bootstrap/templates/settings.json.example` — the generated project's hooks — is
validated for the first time.

## References

| Claim | Source |
|---|---|
| Events, exit codes, exec form, `if`, where a hook can be registered | `@claude-help.md` § 8 · <https://code.claude.com/docs/en/hooks> · <https://code.claude.com/docs/en/settings-reference> |
| "If the rule must always hold, make it a hook" | `@claude-help.md` § 1 and `@CLAUDE.md` invariant 6 |
| Precedent for extending this skill's scope to a new file type, with validation shipped in the same change | `@.claude/decisions/0033-mcp-in-architect-designer.md` |
| Lists the hook reads are data with a single owner | `@CLAUDE.md` invariant 10 |
| A hook, not a review item, when the failure mode is silent | `@.claude/decisions/0051-frontmatter-injections-cwd-independent.md` |
| Enforcement concentrated in one Java file, exec form, no shell | `@claude-help.md` § 13 and step 7 of `project-bootstrap/SKILL.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/schemas/extensions.json` | `settings` block: `$comment`, `match` as a list, `hook_events`, `hook_events_extra`, `matcher_events`, `group_allowed`, `entry_types`, `command_forbidden_chars` |
| `.claude/hooks/ArchHook.java` | `sweep()`/`checkOne()` read `settings.match` as a list; `checkSettings()` validates event, group keys, matcher placement; new `checkHookEntry()` for type, exec form and timeout; `doctor()` gained a `Hooks` line |
| `.claude/settings.json` | Four `if`-filtered triggers for `schema` on `.claude/settings.json` and on the bootstrap template, Write and Edit |
| `CLAUDE.md` | Design-time edge in the diagram; invariant 6 gained an owner; invariant 9 extended to hooks; invariant 10 extended to hook events and entry fields, and to lists inside the Java; two routing rows; `schema` command row; pitfall on the four silent failures |
| `.claude/skills/claude-code-architect-designer/SKILL.md` | Forms 7a/7b/7c and 8 in the table; axis 7 rewritten, axes 14-16 added; Phase 2 veto; Phase 3.5 mandatory record; Phase 4 steps 1, 2, 6 (new), 7, 8, 10; Phase 5 restart warning; § Out of scope rewritten; § Contract; `allowed-tools` gained `Bash(java:*)` |
| `.claude/skills/claude-code-architect-designer/references/decision-matrix.md` | § 1 consequence and the design-time edge; § 2 first row; § 2.2 (new sub-table); § 3 four new signals; anti-patterns 15-19; 9th rubric criterion; § 9 record levels |
| `.claude/skills/claude-code-architect-designer/references/hook-events.md` | New file — derived reference, same relationship `mcp-fields.md` has to `extensions.json` |
| `.claude/skills/claude-code-architect-designer/templates/hook-entry.json.example` | New file — Form 7a/7b and Form 8 shape |
| `.claude/skills/claude-code-architect-designer/templates/hook-mode.java.example` | New file — Form 7c shape: dispatch, method, and where its data lives |
| `.claude/skills/project-bootstrap/SKILL.md` | Step 7 part 2 names `settings` among the blocks whose `$comment` citation is cut, and says to leave its `match` list alone; part 3 says a hook designed for the project is already in the template |
| `.claude/decisions/README.md` | Fourth condition for a record: the approved form is 7 or 8 |

Goes to the generated project: **per hook, decided at design time (axis 8)** — the
registration through `project-bootstrap/templates/settings.json.example`, a mode through
the `ArchHook.java` copy in step 7. Not this record, and not the designer skill itself:
it is a creation skill, same exclusion as `project-bootstrap` and `init-project`.

## Known gap, accepted

`allowed-tools` in the designer skill did not include `Bash(java:*)` while Phase 4
already instructed it to run `java .claude/hooks/ArchHook.java schema`. Pre-existing, and
fixed here rather than in its own commit, because this change makes the skill depend on
that command three more times.
