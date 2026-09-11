---
name: claude-code-architect-designer
description: >
  Decides which Claude Code extension resolves a scenario — auto-invocable skill,
  manually-invoked skill, subagent, rule in `rules/`, or `CLAUDE.md` section — and writes
  the files only after approval. Interviews first, proposes scored options with sources,
  writes afterward. Explicit invocation only.
argument-hint: "[scenario, use case, or problem in one sentence]"
disable-model-invocation: true
allowed-tools: Read, Write, Edit, Glob, Grep, AskUserQuestion, Bash(find:*), Bash(ls:*), Bash(claude plugin validate:*)
---

## Current inventory

Skills: !`ls .claude/skills`

Agents: !`ls .claude/agents`

Rules: !`ls .claude/rules`

Decision records (the latest is the most recent): !`ls .claude/decisions`

## Scenario

$ARGUMENTS

---

# Claude Code Architect Designer

Decides **which of the five forms** of extension resolves the scenario, and writes it.
The five:

| # | Form | File |
|---|---|---|
| 1 | Auto-invocable skill | `.claude/skills/<name>/SKILL.md` |
| 2 | Manually-invoked skill (`/name`) | same, with `disable-model-invocation: true` |
| 3 | Subagent | `.claude/agents/<name>.md` |
| 4 | Rule | `.claude/rules/<name>.md` |
| 5 | `CLAUDE.md` section | root `CLAUDE.md` |

A legitimate sixth answer, and the cheapest one: **create nothing**. A piece that already
covers the scenario exists, or the problem is a compliance issue and belongs in a
hook/`permissions.deny` — which this skill does not write, see § Out of scope.

**Entry rule: no proposal without an interview.** Classifying from one sentence produces
the wrong piece, and the wrong piece costs more than no piece at all — it stays in
context every session, or it never fires.

## Why this is a skill and not a rule

This is a multi-step procedure — interview, classify, propose, write — hence a skill. As
a rule it would break invariant 1 of `@CLAUDE.md`: a rule about when to create skills and
agents would have to mention skills and agents, and `rules/` is a leaf.

## Procedure

### Phase 1 · Interview

Read `references/decision-matrix.md` before asking. Use `AskUserQuestion` — at most 4
questions per call, so 2 to 3 calls. **Don't proceed with a missing answer**: each axis
below eliminates candidate forms, and an unanswered axis leaves the decision guessing.

| # | Axis | What it decides |
|---|---|---|
| 1 | Concrete symptom — what error repeats, what prompt gets pasted again | Whether there's a case, or it's anticipation |
| 2 | Trigger — `/command`, model decision, touching a file, runtime event | Forms 1 · 2 · 4 · out of scope |
| 3 | Frequency — every session, weekly, rare | Always loaded vs on demand |
| 4 | Territory — which file globs, or none | `paths` in form 4; `paths` in form 1 |
| 5 | Nature — declarative fact or sequence of steps | Forms 4/5 vs 1/2/3 |
| 6 | Isolation — verbose output, tools to restrict, different model | Form 3, and only it |
| 7 | Mandatoriness — can it fail sometimes, or is it build/security/compliance | Out of scope (hook) |
| 8 | Destination — this repo only, also the generated project, or both | Steps 6.6/6.7/7 of `project-bootstrap` |
| 9 | Integration — what it reads, what it writes, which existing piece it collides with | Ownership conflict |
| 10 | Cost of getting it wrong | minutes or days | Weight in the score |

Axis 9 is checked against the inventory injected at the top, not from memory. Two pieces
writing to the same paths is an ownership bug, not a style decision.

### Phase 2 · Classify

Apply the decision table in `references/decision-matrix.md`. Then run the nine
invariants of `@CLAUDE.md` as a veto — the most commonly violated are 1 (a rule that
mentions a skill) and 5 (an agent without one of the three reasons). A proposal that
fails an invariant **is not presented as viable**: it appears with the score it deserves
and the reason for rejection.

### Phase 3 · Propose — write nothing

Two to four options, ordered by score, always including the "create nothing" hypothesis
when it is defensible. Each in this form, in this order:

1. **Title** — proposed filename, in kebab-case
2. **Motivator** — the interview axis that justifies it
3. **Pros**
4. **Cons** — includes the invariant it strains, if any
5. **Score 0-10** — rubric in `references/decision-matrix.md`, § Rubric
6. **Visual** — file tree or ASCII graph of who calls whom

And at the end, a **references** table: for each decision, the concrete source
(`@claude-help.md` § N, `@CLAUDE.md` invariant N, or the repo file that serves as
precedent). A claim about the runtime without a source is decoration — cut it.

### Phase 3.5 · Save the decision draft

What Phase 3 produced — options, scores, rejected alternatives, the references table —
evaporates at the end of the session. Six months from now nobody knows why the piece is
a skill and not an agent, and the ten-axis interview starts over from scratch.

**When to save a file.** Only if at least one of these is true:

- Two or more options scored ≥ 5 — there was a real choice.
- The top-scoring option strains an invariant of `@CLAUDE.md`.
- Axis 8 answered "both" — the piece also goes to the generated project.

None of these → don't save any file. The justification lives in the `## Why this is
<form>` section of the file Phase 4 creates, and that's enough. A record for a trivial
decision is ceremony, not memory.

**How.** Generate from `templates/decision.md.example` to
`.claude/decisions/NNNN-<slug>.md`, where `NNNN` is the highest existing plus one — read
it from the inventory injected at the top, not from memory. Save with **all** options and
without the `State` line: the decision hasn't been made yet. Directory rules in
`@.claude/decisions/README.md`.

**Stop here.** Wait for explicit approval. "Looks good" is not approval of which option.

If the user rejects everything, close the draft with
`State: rejected — no option approved` and stop. Don't delete it: its value is avoiding
the same interview again.

### Phase 4 · Write — only after approval

1. Generate from the `templates/` exemplar matching the approved form.
2. Frontmatter: native fields only, list in `references/frontmatter-fields.md`. An
   invented field is silently ignored by the runtime — it looks like behavior, it's
   decoration.
3. No `metadata:` in frontmatter. Ownership, `reads`, and handoff go in the `## Contract`
   section of the body.
4. Code boilerplate goes to `templates/<name>.example` inside the skill that emits it,
   never pasted in the body — invariant 3.
5. **A `## Why this is <form>` section in the body of the created file, always.** Three
   sentences: the form chosen, the interview axis that motivated it, and the closest
   rejected form with the reason. It's the only record that travels with the file — it
   survives whoever never read `.claude/decisions/`, and the copy into the generated
   project. Precedent: `.claude/agents/project-initializer.md`, section "Why this is an
   agent and not a skill". For Form 5 there's no body to put it in: the justification
   lives only in the decision record, and if there's no record, in the commit message.
6. **Propagate.** A new file that nobody routes to isn't found:

   | You created | Also update |
   |---|---|
   | Skill | `@CLAUDE.md` routing table |
   | Development skill (valid inside the generated project) | Table in step 6.7 of `project-bootstrap/SKILL.md` and the `## Skill contract` list |
   | Rule | `@.claude/rules/00-index.md` (written-rules table; remove from planned) **and** the table in step 6.6 of `project-bootstrap/SKILL.md` |
   | Agent | `@CLAUDE.md` routing table, if it's invocable by name |
   | `CLAUDE.md` section | Nothing else — but confirm the total stays under ~200 lines |

   A creation skill (only useful before the project exists) stays **outside** step 6.7,
   like `project-bootstrap` and `init-project`. State this explicitly in the report.

   **Delegation, and only in this case.** If axis 8 answered "both," propagation grows —
   steps 6.6/6.7/7 of `project-bootstrap`, plus its `templates/`. There, delegate **this
   step 6 and no other** to the generic agent with `model: sonnet`, passing the path of
   the Phase 3.5 record and the exact list of files to touch. These are mechanical table
   edits with a destination fixed in writing. Without a saved record, don't delegate: the
   subagent doesn't see the conversation, and the interview is what justifies each line.

   Steps 1 through 5 **never** get delegated. Writing the `description` decides whether
   the skill fires, and the `## Contract` decides ownership — that's design, not
   transcription.

7. If you saved a draft in Phase 3.5, promote it: fill in `Decision`, `State` (approved
   by whom, on what date), and the `Propagation` table with the files step 6 touched.

8. Run `claude plugin validate .claude/skills` and report the output without rewriting
   it.

### Phase 5 · Report

Files created, files changed, the decision record path (or the sentence explaining why
there wasn't one), the `validate` output, and the restart warning if you touched
`settings.json` — it's only read at session startup.

## Out of scope

**Hooks and `permissions.deny`.** If the interview concludes on axis 7 that the rule must
always hold, the answer is a hook — and this skill's correct response is to say so and
stop, not to write the hook. Reason: this repo's enforcement is concentrated in
`ArchHook.java`, and adding a subcommand to it is infrastructure change, with its own
test and its own commit. Propose, don't execute.

**`.claude/commands/`.** Never. Invariant 4: write a skill and control invocation with
`disable-model-invocation`.

**Blueprints.** Architecture is data, not extension — `@.claude/blueprints/_schema.md`.

**The `skill-creator` plugin** is disabled in this project (`enabledPlugins` in
`.claude/settings.json`) on purpose: it creates generic skills, with no knowledge of this
repo's invariants. Don't reintroduce it to work around this skill.

## Contract

**Reads** `@claude-help.md`, `@CLAUDE.md` (the nine invariants),
`@.claude/rules/00-index.md`, `@.claude/blueprints/_schema.md` when the decision touches
blueprints, and the inventory injected at the top. Reads this skill's `references/`
before classifying — the matrix is deliberately not in the body.

**Writes** `.claude/skills/**`, `.claude/agents/**`, `.claude/rules/**`, and the root
`CLAUDE.md` of **this repository**. Only after explicit approval.

**Also writes** `.claude/decisions/NNNN-<slug>.md` — and this is the only path it touches
*before* approval, as the Phase 3.5 draft. It is the exclusive owner of the directory: no
other piece writes there, and nothing inside it is a rule.

**Does not write** `.claude/settings.json`, `.claude/hooks/**`, `.claude/blueprints/**`,
nor project Java code. Does not create `.claude/commands/`.

**Does not** reproduce rules. A new rule is a file in `rules/` with a single owner — never
prose inside a `SKILL.md`.

**Delegates** at most step 6 of Phase 4 (propagation), and only when axis 8 is "both" and
a decision record has been saved. Classifying, proposing, and writing the body always
stay in this thread — the subagent doesn't receive the conversation, and the interview is
the heart of the task.

**Stays out of the generated project.** It's a creation skill, like `project-bootstrap`
and `init-project`: whoever clones an already-generated project has no extensions to
design. The same applies to `.claude/decisions/` — it records decisions about this
meta-repository.
