# 0002 · Designing a use case is a manual skill that writes a spec into `docs/use-cases/`

- **Date:** 2026-09-07
- **Scenario:** "I want to create a new skill use-case-design" — interview about scope,
  input and output of a use case, delimit the boundary so as not to mix two together, and
  emit an implementation plan with every file to create, without writing code.
- **Decision:** Form 2 — `.claude/skills/use-case-design/SKILL.md`, with a reduced
  contract: owner of the mother spec, not of the whole spec
- **Status:** approved by Lucas Fernandes, on 2026-09-07

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | `rest-api-architect/SKILL.md` declares "missing `use-case-design` (Phase 4, also unwritten), which is this skill's actual input." `testing` is in the same state, and `project-bootstrap` already names `use-case-design` as a planned skill. Not preemption: there are two pieces blocked in writing | "create nothing" |
| 2 — Trigger | Explicit invocation; the skill writes a file into the user's project | Form 1 |
| 4 — Territory | Writes `docs/use-cases/**`. No read territory to justify auto-loading | Form 4 |
| 5 — Nature | Procedure: interview → apply boundary test → propose split → save spec. Not a declarative fact | Forms 4 and 5 |
| 6 — Isolation | The interview with the user is the heart of the task; the output is a short file; the context needed fits in `references/` | Form 3 |
| 7 — Enforceability | Can fail; no build depends on it | hook |
| 8 — Destination | **Both** — designing a use case happens after the project exists. Requires propagation into step 6.7 of `project-bootstrap` | — |
| 9 — Integration | `docs/use-cases/**` has no owner. Doesn't touch `**/adapter/in/rest/**` (`rest-api-architect`) nor domain/application (`java-patterns`) | — |
| 10 — Cost of error | High: a wrong spec propagates through four code layers | weighed into the score |

Content decisions made during the interview, which fix the skill's body:

| Question | Answer chosen |
|---|---|
| Axes of the skill's own interview | Trigger + payload + response · Side effects (boundary test) · Invariants and errors · Idempotency, transaction and concurrency — all four |
| Spec format | Design sections per layer + implementation-order checklist derived at the end |
| Handoff | Stops at the spec. Names who implements, invokes no one |
| Spec scope (revised after Phase 3) | **Mother spec only.** Boundary, flow, canonical names and component table. Per-layer detail goes into partial specs, each with its own owner |
| Large scope | Proposes the split, shows the boundary that justifies it, and waits for the user's choice before saving |
| Business language | Refuses and asks for technical rephrasing, listing the expected vocabulary |
| Reading code | Yes — Glob/Grep before proposing; each component carries state NEW / CHANGE / REUSE |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `.claude/skills/use-case-design/SKILL.md` (Form 2) | 9 | **Approved** |
| 2 | `.claude/agents/use-case-designer.md` (Form 3) | 3 | Rejected — none of the agent's three reasons applies; invariant 5 |
| 3 | `.claude/rules/use-case-spec.md` (Form 4) | 3 | Rejected — it's procedure, and the rule would have to name skills; invariant 1 |
| 4 | Create nothing — absorb into `rest-api-architect` | 4 | Rejected — gives two owners to one skill and leaves `testing` and `java-patterns` without an entry point |

### Option 1 — `.claude/skills/use-case-design/SKILL.md` (score 9)

**Motivator:** axes 1 and 5. Two skills declare in writing that they depend on it, and the
task is a multi-step procedure with an interview.

**Pros:**
- Closes the blocker declared in `rest-api-architect/SKILL.md` and in `testing/SKILL.md`.
- `disable-model-invocation: true` is this repo's default for anything that writes files
  into the user's project; the seven existing skills do so.
- Clean ownership: `docs/use-cases/**` doesn't collide with any existing piece.
- Near-zero context cost until invoked — only the `description` stays loaded.
- The spec, versioned in git, survives `/compact` and is reviewable in a PR.

**Cons:**
- It's the first skill in the repo to write design markdown instead of code or
  structure — no exact precedent.
- Depends on the active blueprint's `packages.map` to derive real paths, and in the
  generated project that file doesn't exist: needs the same copy fix that `java-patterns`
  gets in step 6.7.
- Wide propagation: 6.7 + `## Skill contract` + `CLAUDE.md` routing + the
  `project-bootstrap` line that still lists it as nonexistent.

**Points cut on the rubric:** criterion 6 (repo precedent) — partial, for the reason
above.

### Option 2 — `.claude/agents/use-case-designer.md` (score 3)

Fails the § 5 counter-test of the matrix on all three points: the interview is the heart
of the task and the subagent doesn't see the conversation; the context it needs fits in a
`references/` folder of the skill itself; the final output is a short file. Invariant 5
violated → score capped at ≤ 4.

### Option 3 — `.claude/rules/use-case-spec.md` (score 3)

The spec's format as a rule. Two failures: it's procedure, not declarative fact
(matrix § 2); and the rule would have to name `rest-api-architect` and `java-patterns` as
consumers, which breaks invariant 1 — `rules/` is a leaf. The format belongs in
`templates/use-case-spec.md.example` inside the skill, per invariant 3.

### Scope revision — the `/new-feature` pipeline

Raised by the user after Phase 3, before approval: the destination is a manual
`/new-feature` orchestrator that chains the layer skills and ends in an executor
subagent. The original proposal had `use-case-design` **consolidate** the partial specs
of the other skills.

**Rejected — creates a cycle.** `use-case-design` would call `rest-api-architect` and
receive output back into the same file: two owners of `docs/use-cases/UC-NNN.md`,
invariant 2 broken, and the dependency graph stops being acyclic.

**Approved — the orchestrator consolidates, not the design skill.** One file per owner:

```
/new-feature                    ← orchestrator, owner of the consolidated index
   ├─1→ use-case-design         → UC-NNN/00-use-case.md   (boundary, flow, names)
   ├─2→ domain-modeling         → UC-NNN/10-domain.md
   ├─3→ persistence-adapter     → UC-NNN/20-persistence.md
   ├─4→ rest-api-architect      → UC-NNN/30-rest.md
   ├─5→ testing                 → UC-NNN/40-tests.md
   ├─6→ consolidates            → UC-NNN/README.md
   │        ↓ human approval
   └─7→ agent java-spring-boot-developer → writes code, runs ./mvnw test
```

Consequences fixed by this decision:

| # | Question | Resolution |
|---|---|---|
| a | Skill→skill invocation | Doesn't exist in the runtime. Steps 1-6 are the model following the orchestrator's list in the same thread; zero guarantee, but each step leaves a file on disk — visible, not silent, failure |
| b | Step 7 | `context: fork` + `agent:`. The only point in the pipeline with real isolation |
| c | Is the executor agent legitimate? | Yes, reason 1 (preserve context): generating 12 Java files fills the context and returns a short report. Passes the § 5 counter-test because **the interview already happened** — the written spec replaces the conversation. Same pattern as `init-project → project-initializer` |
| d | Names of the layer skills | `domain-modeling`, `persistence-adapter`, `messaging-adapter` — the ones already registered in `project-bootstrap/SKILL.md`. `domain-model-architect` and `jpa-persistence-architect` rejected: the second fixes technology in the piece's name, and the same use case with MongoDB would need another skill (invariant 7) |
| e | Build order | Phased. See § Phasing — six pieces at once is anti-pattern 9 |

### Approved phasing

| Phase | Builds | Unlocked when |
|---|---|---|
| 1 (done, 2026-09-07) | `use-case-design` | — |
| 2 | `domain-modeling` + `persistence-adapter`; unblocks `rest-api-architect` (requires P5) | Phase 1 used in ≥ 1 real use case |
| 3 | `agents/java-spring-boot-developer.md` | ≥ 2 complete specs exist for it to execute |
| 4 | `/new-feature` | Phases 2 and 3 ready — orchestrating nonexistent pieces isn't testable |

`agents/feature-builder.md`, planned in Phase 5 of `CONTEXT.md`, is this same piece under
another name. It keeps the name `java-spring-boot-developer`, which says what it
executes.

### Option 4 — create nothing, absorb into `rest-api-architect` (score 4)

`rest-api-architect` would end up owning both designing the use case and generating the
REST adapter — two responsibilities, and the first isn't REST: a use case triggered by a
Kafka event or by a job has no controller at all. `testing` and `java-patterns` would
remain without an entry point. Criteria 1, 5 and 7 of the rubric cut.

## References

| Statement | Source |
|---|---|
| Anything that writes files into the user's project is Form 2 | `references/decision-matrix.md` § 4; the repo's seven skills |
| Agent only for preserving context, restricting tools, or changing model | `@CLAUDE.md` invariant 5 · `references/decision-matrix.md` § 5 |
| A rule never mentions a skill | `@CLAUDE.md` invariant 1 |
| Spec format lives in `templates/*.example`, not in the body | `@CLAUDE.md` invariant 3 |
| The skill is blocked in writing | `.claude/skills/rest-api-architect/SKILL.md` § "What's missing before removing `disable-model-invocation`" |
| `use-case-design` is already named as planned | `.claude/skills/project-bootstrap/SKILL.md` step 6.7 |
| A copied skill loses citations to `blueprints/` | `.claude/skills/project-bootstrap/SKILL.md` step 6.7, fixes 1 and 2 |
| Only `description` stays in context until invoked | `references/decision-matrix.md` § 4 |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/use-case-design/SKILL.md` | Created |
| `.claude/skills/use-case-design/templates/use-case-spec.md.example` | Created |
| `.claude/skills/use-case-design/references/scope-boundary.md` | Created |
| `CLAUDE.md` | New line in the routing table |
| `.claude/skills/project-bootstrap/SKILL.md` | Line in the step 6.7 table · copy fix (`blueprints/`) · `## Skill contract` · removal of `use-case-design` from the list of nonexistent skills |
| `.claude/skills/rest-api-architect/SKILL.md` | Blocker removed; becomes owner of `30-rest.md`; P5 remains the only open item |
| `.claude/skills/testing/SKILL.md` | Becomes owner of `40-tests.md` |
| `CONTEXT.md` | Inventory (§ 4), decision D14 (§ 2), P6 (§ 6) and phases 4 and 5 roadmap (§ 7) |

Goes to the generated project: **yes, via step 6.7** — it's a development skill, not a
creation skill.
