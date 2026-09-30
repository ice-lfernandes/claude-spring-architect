# 0007 · The four pipeline skills lose `disable-model-invocation`

- **Date:** 2026-09-07
- **Scenario:** the user asked why `use-case-design`, `domain-modeling`,
  `persistence-architect` and `rest-api-architect` were all set with
  `disable-model-invocation: true`, and proposed that the invocable skill be the
  orchestrator, with the others only auto-invocable by the model.
- **Decision:** remove `disable-model-invocation` from the four. They become invocable
  both ways — `/name` and the Skill tool.
- **Status:** approved by Lucas Fernandes, on 2026-09-07

## The problem the question uncovered

D14 fixed that `/new-feature` **chains** the layer skills. Chaining means the model
calling the Skill tool. But `disable-model-invocation: true` hides the skill from the
model.

**As they were, the orchestrator planned in D14 wouldn't be able to invoke them.** No one
noticed because `/new-feature` doesn't exist yet.

## Verification

Empirical, in this session, not deduced from documentation. The skill listing handed to
the model contained **one** entry — `project-bootstrap` — and that's exactly the only one
of the repository's ten skills without `disable-model-invocation: true`. The nine with
the field don't appear in the listing. A skill the model doesn't see is a skill the model
doesn't call.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Remove the field from the four — invocable both ways | 8 | **Approved** |
| 2 | `user-invocable: false` on the four — only the model invokes | 4 | Rejected |
| 3 | Leave as is | 2 | Rejected — contradicts D14 |

### Option 1 (score 8)

The orchestrator calls; the user also calls. Keeps the real use case of re-running
**one** layer: the domain changed, only `20-persistence.md` is redone, without running
the whole pipeline.

**Cons:** the four can now fire by `description` similarity. Mitigated in two places,
neither in the frontmatter:

- Each one's **entry rule**: without the prior partial, it stops and says what to run
  first. Firing out of order costs a stop, not a wrong artifact.
- Each one's `description` now says it's a pipeline piece and which partial it requires.

**Points cut:** unwarranted firing is still possible on `persistence-architect` — a
casual question about a slow query hits the description. That's the scenario step 6 of
the skill itself already anticipates (diagnostics without a new use case), so it's not a
failure.

### Option 2 — `user-invocable: false` (score 4)

Looks like the symmetric inverse of 1 and isn't. `disable-model-invocation` turns off an
**explicit call**; `user-invocable: false` leaves only **fuzzy firing by description**.
It trades the reliable mechanism for the approximate one, and eliminates
`/persistence-architect` for re-running a single layer. Fails what the user wanted —
control over order — because it isn't a call, it's text matching.

### Option 3 — don't touch it (score 2)

Postpones the collision to the day `/new-feature` is written, and at that point the
symptom shows up as "the skill doesn't exist" instead of "the field is wrong."

## What doesn't change

`arch-doctor`, `init-project`, `project-bootstrap`, `java-patterns`, `testing` and
`claude-code-architect-designer` keep what they have. They aren't pieces chained by
`/new-feature`; for those, `disable-model-invocation` remains the right choice — a side
effect triggered by hand (`@claude-help.md` § when to add each thing).

## References

| Statement | Source |
|---|---|
| `disable-model-invocation: true` = only the user invokes | `@claude-help.md:366` |
| `user-invocable: false` = only the model invokes | `@claude-help.md:367` |
| `/new-feature` chains the layer skills | D14 · `@.claude/decisions/0002-skill-use-case-design.md` |
| Layer skills emit spec, not code | D15 · `@.claude/decisions/0003-skill-domain-modeling.md` |
| Invocation is controlled with `disable-model-invocation` | `@CLAUDE.md` invariant 4 |
| The reason a subagent doesn't fit still holds | `@.claude/decisions/{0002,0003,0005}` , axis 6 |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/{use-case-design,domain-modeling,persistence-architect,rest-api-architect}/SKILL.md` | field removed; `description` says it's a pipeline piece; the "Why this is a manual skill and not a subagent" section split into "How it's invoked" + "Why this isn't a subagent" |
| `.claude/decisions/0006-rest-api-architect-design.md` | § `disable-model-invocation` stays — superseded |
| `CONTEXT.md` | D17, inventory of the four skills |

Goes to the generated project: **yes** — the four already went through step 6.7.
