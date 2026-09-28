# Decision records

Each file records **one** design decision about this repository's own `.claude/`: what
extension form was created, what alternatives were rejected and with what score.
Produced by the `claude-code-architect-designer` skill, never by hand.

Name: `NNNN-<kebab-case-slug>.md`, `NNNN` sequential with four digits.

## When a file exists here

Not every creation gets a record. Only when at least one of these holds:

- Two or more Phase 3 options scored ≥ 5 — there was a real choice.
- The approved option tensions one of `@CLAUDE.md`'s invariants.
- The piece also matters inside the generated project (interview axis 8 = "both").
- The approved form is a hook or a `permissions` rule (form 7 or 8) — always. Both
  execute, and both have almost nowhere to explain themselves: a `deny` line has nowhere
  at all. Without a record, whoever it blocks later deletes it.

A decision meeting none of these conditions stays recorded only in the
`## Why this is <form>` section of the file that was created. A record for a trivial
decision is ceremony, not memory.

## What this is not

- **Not a rule.** It doesn't enter `@.claude/rules/00-index.md`, has no `paths`, isn't
  auto-loaded. It's history, read when someone asks "why."
- **Doesn't go to the generated project.** It records decisions about this
  meta-repository's extensions. See `@CLAUDE.md` invariant 9.
- **Not the source of truth for current state.** It records what was decided on that
  date, not what holds today. If the file that was created later changed, the record
  still stands as history — don't rewrite it, write another that supersedes it and link
  the two.
