# 0068 · The declared-dependency list is gated where every run passes, not where two steps do

- **Date:** 2026-09-28
- **Scenario:** lessons-learned-014 § 6 — both partials of `UC-003` shipped without their
  declared-dependency section, the spec reached `approved` anyway, and the executor added
  `spring-boot-starter-kafka` to `pom.xml` on the strength of a checklist item and a
  configuration block.
- **Decision:** No new piece. A gate in `/new-feature`'s consolidation, next to the
  `Satisfied by` one, plus the matching refusal in
  `.claude/agents/java-spring-boot-developer.md`.
- **State:** approved by Lucas Fernandes, on 2026-09-28.

## The mechanism, which is the part worth keeping

`/new-feature` defines both sections as validated blocks and states that each is "the only list
the executor may act on when it writes `pom.xml`" — `25-mensageria.md` § 7 and
`20-persistencia.md` § 6. The validation lives in **step 4 and step 5**. `UC-003`'s partials were
written and consolidated in earlier runs, so those steps never ran over them, and the spec
reached `approved` with both lists absent.

**A validation that lives in a step is skipped by every path that does not take that step.**
Consolidation is the one block every run passes, and it is where `status: draft` is first
written — the moment a pile of partials becomes a spec. The `Satisfied by` gate already sits
there for exactly this reason.

The executor's half is the same failure seen from the other end: with no list present, it
inferred one that was defensible and still the design skill's decision to make. That inference
is what `lessons-learned-002 § 10` recorded as `pom.xml` having no owner, and the territory
granted in `agent_classes` cannot express "dependency blocks only" — only the agent's own body
can.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Two partials without the section, a spec `approved` regardless, `pom.xml` written by inference | 9 (create nothing) |
| 7 — mandatoriness | The check must run on every path, which is not the same as "cannot fail": consolidation is a block the model always executes, and the failure mode was a *conditional* step, not a disobeyed instruction | Forms 7 · 8 as the primary answer |
| 9 — integration | The `Satisfied by` gate is already there, same shape, same "stop and ask" | A second location |
| — executor | Yes: a spec with no list is a spec defect it reports, never a `pom.xml` it infers | Leaving the executor's half open |
| 8 — destination | Both — `new-feature` and the executor are in `export`'s include lists | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Gate at consolidation + the executor's refusal | 8 | **Approved** |
| 2 | A hook mode validating the consolidated spec's partials | 6 | Rejected — a guarantee bought against a *missing step*, which prose at a step every run takes already fixes; it runs after the spec is written, and the `Stop` sweep already pays a JVM per turn |
| 3 | Move the check earlier, into each design skill | 5 | Rejected — that is where it already is, in steps 4 and 5; it is the path that skips them that failed |
| 4 | Create nothing | 3 | Rejected — the spec is `approved` today with both lists absent, and nothing would catch the next one |

### Option 1 (score 8)

**Motivator:** axis 9. The gate has a precedent in the same block, doing the same thing, for the
same reason.

**Shape.**

- Consolidation, in the same bullet list as `Satisfied by`: a spec cannot be consolidated while a
  partial that applies is missing its declared-dependency section. `none` is a valid value —
  most cases need no new dependency — but **absence is not `none`**: it is the difference
  between "the design skill decided nothing is needed" and "nobody looked".
- Which partials apply is already decided by the run: `20-persistencia.md` § 6 always,
  `25-mensageria.md` § 7 only when step 4 ran.
- The executor: a spec whose applicable block carries no declared-dependency list is a **spec
  defect** — report and stop, never infer from a configuration block, a checklist item, or a
  property name. It already refuses an incomplete spec; this names the case that got through.

**Pros:** one sentence in the block every run executes, and the failure becomes impossible to
reach by the path that produced it. No new piece, no class, no registration.

**Cons:** it is persuasion. A model that skips consolidation's bullet list skips this with it —
though a run that skips consolidation writes no spec at all, which bounds the damage.

**Points cut in the rubric:** § 8 criterion 4 (persuasion where a hook was available).

### Option 2 — a hook over the consolidated spec (score 6)

A mode reading `UC-NNN-spec.md` on `PostToolUse` of `Write(docs/use-cases/**/UC-*-spec.md)`,
opening each partial it references and failing by name on a missing section.

Rejected, and the reason is worth stating because it differs from §§ 1 and 13, where the hook
*was* the answer. There the failure was a rule the model could bypass — a tool with no matcher,
a command nobody typed. Here the failure is a validation placed in a **conditional step**, and
moving it to an unconditional one removes the bypass entirely. A hook would be a process per
spec write to confirm what a correctly-placed instruction already guarantees — anti-pattern 16.

Worth revisiting if a spec ever reaches `approved` without its lists *after* this gate exists.

## References

| Claim | Source |
|---|---|
| Both sections are mandatory blocks of their partials | `.claude/skills/persistence-architect/SKILL.md` § blocks · `.claude/skills/messaging-architect/SKILL.md` step 7 |
| Each is the only list entitling the executor to touch `pom.xml` | `.claude/skills/new-feature/SKILL.md` step 5 |
| The `Satisfied by` gate is enforced at consolidation, not in a step | `.claude/skills/new-feature/SKILL.md` § Consolidation, step 2 |
| `pom.xml` has exactly one writer in a feature run, and the bound is the agent's body | `@CLAUDE.md` § Known pitfalls · `agent_classes` `$comment` |
| A hook against a failure the right instruction placement removes is a process per event | `references/decision-matrix.md` § 7 anti-pattern 16 |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/new-feature/SKILL.md` | the gate in consolidation step 2, next to `Satisfied by`; steps 4 and 5 point at it instead of owning it alone |
| `.claude/agents/java-spring-boot-developer.md` | the input guardrail names the missing list as a spec defect, and § Does not write says `pom.xml` is never written from an inference |

Goes to the generated project: **yes** — both are in `export`'s include lists.

**Restart warning:** none.

## What shipped

- **Consolidation step 2** carries the gate, immediately before the `CHANGELOG.md` bullet and
  right after `Satisfied by`: a partial that applies must carry its section, `none` is a value
  and absence is not one, with the two applicability rules stated (persistence always, messaging
  when step 4 ran).
- **Steps 4 and 5** now say they are not the only check, naming the path that skipped them.
- **The executor's input guardrail** gains item 3b, and § Does not write names the three
  inferences that have to be refused by name — a `spring.kafka.*` configuration block, a
  checklist item naming a transport, a property prefix implying a starter. Listing them matters
  more than the rule: the inference that got through was the plausible kind, not a careless one.

## Verification

`claude plugin validate .claude/skills` and `java .claude/hooks/ArchHook.java schema` both pass.
Nothing here is executable — the gate is prose in a block the model runs, and the check on it is
that consolidation now states the rule in the same shape, and the same bullet list, as the
`Satisfied by` gate it is modelled on.
