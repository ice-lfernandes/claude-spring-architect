# 0025 · `java-patterns` reaches `/new-feature` preloaded into the executor, not as a pipeline step

- **Date:** 2026-09-09
- **Scenario:** "aqui há referências sobre o idempotencykey [...] qual o escopo e como
  essa skill [`java-patterns`] entra na orquestração feita pela new-feature? se não
  tiver, vamos desenhar e alinhar" — `java-patterns` had zero mentions in
  `new-feature/SKILL.md` and in `java-spring-boot-developer.md`, and carries
  `disable-model-invocation: true`, which structurally blocks the orchestrator or the
  executor from calling it as a separate turn.
- **Decision:** Option 2 — add `skills: java-patterns` to
  `.claude/agents/java-spring-boot-developer.md`'s frontmatter. The catalog is preloaded
  in full at the executor's startup and applied directly while writing code; it is never
  invoked as a separate turn. First use of the `skills:` agent field in this repository.
- **Status:** approved by Lucas Fernandes, on 2026-09-09

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Real gap, not anticipation: an existing skill (`java-patterns`) with a written catalog, structurally unreachable by the one agent (`java-spring-boot-developer`) that writes the code its catalog applies to | Confirms this isn't premature design work |
| 2 — Trigger | Mid-generation, when a step is about to write the second occurrence of a catalog symptom (a repeated `switch`/`if-else`, duplicated cross-cutting logic) — an agent-internal moment, not a `/command` or a runtime event | Rules out a new hook or a new skill step; the trigger lives inside an existing procedure |
| 5 — Nature | Reference material consulted mid-procedure, exactly the role `templates/*.example` already plays for the four design skills — not a new sequence of steps | Rules out modeling this as a new pipeline stage |
| 8 — Destination | Both. `java-spring-boot-developer.md` is copied verbatim into the generated project by `project-bootstrap` step 6.8, and `java-patterns` is already copied by step 6.7 — the new `skills:` field travels with the agent, no rewrite needed | No propagation gap to close |
| 9 — Integration | Reads `java-patterns/SKILL.md` in full; writes nothing separately. The executor remains the sole writer of `src/**` (invariant 2) in this mode — `java-patterns` keeps writing code itself only in its unrelated standalone mode (`/java-patterns`, human-invoked, on already-existing code) | No ownership conflict: one skill, two modes, one writer per mode |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | New `new-feature` consolidation step: mechanically grep the generated code for catalog symptoms and *propose* `/java-patterns`, same shape as the existing ArchUnit-detection step | 7 | Rejected — user's call. Correct and lower-risk, but leaves the gap open until the *next* feature's consolidation runs, one pipeline pass later than option 2 |
| 2 | `skills: java-patterns` on `java-spring-boot-developer` — preloaded, applied directly, mid-generation | 8 | **Approved** |
| 3 | Leave `java-patterns` standalone-only, no orchestration link | 4 | Rejected — doesn't close the gap the interview confirmed is real (axis 1) |

### Option 2 — preload into the executor (score 8)

**Motivator:** axis 9. The executor already reads five other skills' `templates/*.example`
as passive reference while writing; `java-patterns`'s catalog is the same kind of thing —
a lookup table with no interview state of its own (its own procedure never calls
`AskUserQuestion`), so preloading loses nothing that standalone invocation would have
provided.

**Pros:**
- Closes the gap at the earliest possible moment — the same run that creates the second
  occurrence of a symptom, not one pipeline pass later.
- No new frontmatter precedent risk beyond the field itself: `skills:` is already a
  schema-recognized agent field (`.claude/schemas/extensions.json`), just never
  exercised in this repo before now.
- `disable-model-invocation` on `java-patterns` keeps meaning what it always meant —
  only a human runs it standalone. Preloading doesn't route around that flag; it's a
  different loading mechanism entirely (`@claude-help.md` § Distinctions that confuse
  people — "a subagent can preload skills (the `skills:` field)").

**Cons:**
- First precedent for `skills:` in this repo — the next reviewer has no second example to
  compare against.
- The executor's context grows by one skill's worth of tokens on every run, even for a
  first feature where the catalog never matches anything.

**Points cut in the rubric:** § 5 maintenance cost — one more thing `java-spring-boot-developer.md`
depends on and could drift from if `java-patterns`'s catalog changes shape later.

### Option 1 — propose-after-consolidation (score 7)

Same shape as `new-feature`'s existing ArchUnit-detection step (mechanical grep,
propose, never auto-run). Rejected by the user in favor of option 2, not on a technical
flaw — it's the more conservative choice and remains available later if preloading turns
out to be the wrong call.

## References

| Claim | Source |
|---|---|
| `skills:` field is schema-recognized, "Skills preloaded in full at startup" | `.claude/schemas/extensions.json` · `@.claude/skills/claude-code-architect-designer/references/frontmatter-fields.md` |
| Preloading via `skills:` is a distinct mechanism from `/name` invocation | `@claude-help.md` § Distinctions that confuse people |
| `java-patterns` carries `disable-model-invocation: true` | `.claude/skills/java-patterns/SKILL.md` frontmatter |
| `java-spring-boot-developer.md` copies verbatim to the generated project | `project-bootstrap/SKILL.md` step 6.8 |
| `java-patterns` copies to the generated project | `project-bootstrap/SKILL.md` step 6.7 |
| Precedent for "detect mechanically, propose, don't auto-run" (option 1's shape) | `new-feature/SKILL.md`, consolidation step 3 (ArchUnit detection) |

## Propagation

| File | Change |
|---|---|
| `.claude/agents/java-spring-boot-developer.md` | `skills: java-patterns` added to frontmatter; new § Design patterns explaining when/how the catalog applies; Contract's Reads list and References updated |
| `.claude/skills/java-patterns/SKILL.md` | Contract clarifies the two invocation modes and that the executor stays sole writer in preloaded mode |
| `.claude/skills/new-feature/SKILL.md` | Note under "Integrates with" pointing to this record, so the orchestration diagram isn't silently missing a piece |

Goes to the generated project: **yes, via steps 6.7 (already copies `java-patterns`) and
6.8 (already copies `java-spring-boot-developer.md`) — no rewrite needed in either, since
`skills:` is a repo-relative reference that resolves the same way inside the generated
project's own `.claude/`.**
