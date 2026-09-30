# 0086 · Which pieces the audit trail skips is decided by their class, not by a list

- **Date:** 2026-09-30
- **Scenario:** "verifique quem é responsável por dizer qual skill ou agent deve gerar o
  audit-usage" — and, once the answer was on the table, "faça a opção a e depois a b".
- **Decision:** `audited: false` on the `observer` class of `skill_classes` (readable on any
  class of `agent_classes` too), read by `ArchHook.java isAuditExcluded`; `audit.exclude_skills`
  removed. Preceded by option a: stale comments corrected.
- **State:** approved by Lucas Fernandes, on 2026-09-30 — options a then b

## What the verification found

Who decides that a piece leaves a report, traced in the code:

| Decision | Owner before this record |
|---|---|
| Which pieces are audited | No list: `isAuditedSkill` / `isAuditedAgent` — the name resolves to `.claude/skills/<n>/SKILL.md` or `.claude/agents/<n>.md`. Plugin skills and runtime agents fall out by construction (0038) |
| Which pieces are skipped | `audit.exclude_skills` in `extensions.json` = `["audit-usage", "arch-doctor"]` |
| Own report or node of its parent | `auditPrompt` (a `/command`) and `auditCall` (a `Skill`/`Agent` call with no run open) open a run; inside an open run the call is a node |
| On or off | The directory `.claude/audit-usage/` plus the registration in `project-bootstrap/templates/settings.json.example` |

Three defects in that picture:

1. **The same two names in two lists.** `skill_classes.observer.skills` already said which
   skills observe instead of producing work; `audit.exclude_skills` repeated them.
   Invariant 2 — a norm written in two places has diverged — with the silent failure mode
   invariant 10 warns about: a third observer registered in its class and not in the list
   would write a report about reading reports, and `schema` checks neither against the other.
2. **Stale comments.** The `audit` mode's header in `ArchHook.java` still described the
   trail as "orchestrator skills — the ones carrying `disable-model-invocation: true`", and
   the `audit` block's `$comment` justified `exclude_skills` with the same flag. Both were
   true before 0038 and false since: any skill or agent of the project is audited.
3. **A misleading key.** `exclude_skills` also excluded agents — `auditCall` passed an agent
   name to the same check.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| a | Correct the stale comments; keep both lists | 5 | **Approved, as a first step** |
| b | The class owns the exclusion: `audited: false` on `observer`, `exclude_skills` removed | 8 | **Approved** |
| c | Keep both lists; `schema` fails when they disagree | 5 | Rejected — a check that keeps a duplicate alive instead of removing it |

### Option b — the class owns it (score 8)

**Motivator:** invariant 2. The class is already the single owner of what a skill *is* —
territory, body shape, allowed models; whether it is recorded is one more property of the
same thing. Precedent for a boolean on a class read by one hook mode: `design_phase` and
`blocked_during_design` on `skill_classes`, `pattern_catalog` on `agent_classes` (0077).

**How it reads:** `isAuditExcluded(kind, name)` resolves the piece's class through
`skillClassOf` / `agentClassOf` — the lookups `guard` and `schema` already use — and returns
true only when that class declares `audited: false`. Absent means audited, so no other class
changes. A piece in no class is audited; `schema` already fails it by name.

**Pros:** one list instead of two; a new observer is excluded by joining its class, the step
invariant 9 already makes mandatory; agents get the same switch by the same spelling.

**Cons:** the exclusion is now one indirection away — reading `extensions.json` for "what
does the trail skip" means finding the class with the flag, not a list under `audit`. The
`audit.$comment` says where to look. A generated project updated to this `ArchHook.java`
with an old `extensions.json` would audit its observers — impossible through `export`, which
writes both files together.

**Points cut in the rubric:** criterion 5 (one indirection); criterion 6 held by precedent.

### Option c — a consistency check (score 5)

Cheapest diff after a, and it closes the silent drift. It also makes the duplicate
permanent: every new observer is two edits, and the check exists only to police a list that
has no reason to exist.

## References

| Claim | Source |
|---|---|
| Any project skill or agent is audited, not only orchestrators | `@.claude/decisions/0038-audit-trail-every-skill-and-agent.md` |
| Why observers are skipped (feedback loop, closing the open run) | `@.claude/decisions/0036-skill-audit-usage.md` |
| A norm in two places has diverged | `@CLAUDE.md` invariant 2 |
| Lists the hook reads live in `extensions.json`, not in the Java | `@CLAUDE.md` invariant 10 |
| Class booleans read by a hook mode | `extensions.json` `skill_classes.classes.design.design_phase`, `agent_classes.classes.*.pattern_catalog` · `@.claude/decisions/0077-pattern-catalog-injected-at-subagent-start.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | Option a: `audit` mode header rewritten. Option b: `isAuditExcluded(kind, name)` reads the class flag; both call sites pass the kind |
| `.claude/hooks/ArchHook.jar` | Rebuilt |
| `.claude/schemas/extensions.json` | `audit.exclude_skills` removed; `audit.$comment` points at the class; `skill_classes.classes.observer` gains `audited: false` and says why in its `$comment` |
| `.claude/.ci/AuditRenderTest.java` | An observer typed mid-run closes the run and leaves no report; a model call to one with no run open opens none. Mutation-checked: removing the flag fails both |
| `.claude/skills/arch-doctor/SKILL.md`, `.claude/skills/audit-usage/SKILL.md` | Name the class flag instead of the list |
| `claude-help.md`, `docs/en/08-audit-usage.md`, `docs/pt-br/08-audit-usage.md` | Same |

Goes to the generated project: **yes** — `ArchHook.java`, the jar, `extensions.json` and both
skills already travel through `export`, together.
