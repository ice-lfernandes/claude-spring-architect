# 0026 · Catalog of example use cases to exercise `/new-feature` + `java-spring-boot-developer`

- **Date:** 2026-09-09
- **Scenario:** wants sample use cases to test the `/new-feature` pipeline and the
  `java-spring-boot-developer` agent, covering already-created skills and, where
  applicable, `java-patterns`.
- **Decision:** Option 1 — `.claude/skills/java-patterns/references/example-use-cases.md`
- **State:** approved by Lucas Fernandes, on 2026-09-09

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Both (pipeline never run end-to-end + pattern coverage), at least 10 use cases, not exhaustive | Rules out a single-purpose fixture; catalog must double as regression input and pattern coverage |
| 2 — trigger | Reusable catalog, not a one-off manual run | Rules out "create nothing / throwaway" as sole answer |
| 3 — frequency | Every time the pipeline changes (regression) | Rules out Form 5 (`CLAUDE.md`, always-loaded) — this is on-demand reference, not a standing fact |
| 8 — destination | Both this repo and the generated project | Forces the owning file to live inside a skill that already copies via step 6.7; a decisions-only or meta-repo-only file would fail this axis |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `java-patterns/references/example-use-cases.md` | 8 | **Approved** |
| 2 | new skill `pipeline-regression` (Form 2, this repo only) | 6 | Rejected — optional add-on, not required to satisfy the interview; proposed separately below |
| 3 | create nothing, pick examples ad hoc each session | 3 | Rejected — contradicts axis 2 (reusable) and axis 3 (regression) |

### Option 1 — `java-patterns/references/example-use-cases.md` (score 8)

**Motivator:** axis 8 (both destinations) + existing precedent
(`references/pattern-catalog.md` already lives there and already copies to the
generated project per `project-bootstrap` step 6.7, table row `java-patterns`).

**Pros:** single owner (java-patterns already owns "which pattern for which symptom");
zero new piece, so zero new maintenance surface; context cost stays low — a reference
file only loads when java-patterns is invoked; propagates to the generated project for
free, no new step 6.7 row needed; each entry can cite its curated-table row or its
`pattern-catalog.md` row, so triage stays single-sourced.

**Cons:** none of `java-patterns`'s current invocation paths (standalone, or preloaded
inside the executor) actually *reads* this file automatically — it's addressed by a
human copying a use-case description into `/new-feature`, not invoked programmatically.
Documented as "how to use" in the file itself so it doesn't look like dead weight.

**Points cut in the rubric:** none — form fit, invariants, context cost, precedent all
clean. Not a 10 only because propagation is manual (a human, not a mechanism, decides
when to run the catalog).

### Option 2 — new skill `pipeline-regression` (score 6)

**Motivator:** axis 3 (every time the pipeline changes) read literally as "someone/something
should re-run all 10 automatically", not just "the catalog should still be valid".

**Pros:** turns regression into a repeatable command instead of tribal knowledge; Form 2
(`disable-model-invocation`) fits — it has a side effect (drives real `/new-feature` runs)
and is user-triggered, not chained.

**Cons:** no observed second occurrence yet — first time this need comes up
(anti-pattern 9, built on anticipation, `references/decision-matrix.md` § 7). Axis 8 for
*this* piece is "this repo only" (a generated project has no meta-pipeline to regress),
which splits it from Option 1's "both" — two different pieces, not one. Viable, but
should wait for a second real need before being written.

## References

| Claim | Source |
|---|---|
| `java-patterns` already copies to the generated project | `.claude/skills/project-bootstrap/SKILL.md` step 6.7 table, row `java-patterns` |
| `references/*.md` is the right container for triage/reference data inside a skill | `.claude/skills/java-patterns/references/pattern-catalog.md` (existing precedent) |
| A piece built on a single occurrence is an anti-pattern | `references/decision-matrix.md` § 7, row 9 |
| Axis 8 = "both" requires the owning file to already be on a step-6.7 path | `@CLAUDE.md` invariant 9 |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/java-patterns/references/example-use-cases.md` | Created: 12 use-case rows, each naming the symptom, the pattern, `curated`/`exemplar` status, and a slug for `/new-feature UC-<NNN>-<slug>` |
| `.claude/skills/java-patterns/SKILL.md` | `## Contract` § Reads: one sentence noting the new file is human-facing testing material, not read during normal invocation |

Goes to the generated project: **yes, automatically** — `references/**` is part of the
"entire directory" step 6.7 already copies for `java-patterns`. No table edit needed.
