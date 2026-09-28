# 0008 · `testing.md` rule + `testing` rewritten with two modes, and a JaCoCo gate in bootstrap

- **Date:** 2026-09-08
- **Scenario:** "let's finish the `.claude/skills/testing` stage" — the last piece of
  stage 2 of the `/new-feature` pipeline, today a stub.
- **Decision:** Option 1 — Form 4 (`.claude/rules/testing.md`) + Form 1
  (`.claude/skills/testing/SKILL.md`, two modes, without `disable-model-invocation`)
- **Status:** approved by Lucas Fernandes, on 2026-09-08

## Interview

Only the axes that eliminated forms.

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | The `40-tests.md` partial has no real owner: the skill is a stub and `rules/testing.md` doesn't exist. `CONTEXT.md` § 7 stage 2 item 11 requires the rule **before** the skill | "create nothing" |
| 2 — Trigger | Two paths: chained by `/new-feature` (design, per use case) and `/testing` by hand (setup, once per project) | Pure Form 2 — the pipeline needs to call it (D17) |
| 4 — Territory | `**/src/test/**` — already listed in the planned-rules table of `00-index.md` | Reinforces Form 4 for the declarative part |
| 5 — Nature | Mixed: pyramid, slices, names, data, coverage and DB engine are declarative fact; reading the partials, interviewing and emitting `40-tests.md` is procedure | No single form is enough — hence Option 1 being a pair |
| 6 — Isolation | No. The interview decides what's worth testing, and the output is a short partial | Form 3 |
| 8 — Destination | Both. Tests are written inside the generated project | Requires steps 6.6 and 6.7 |
| 9 — Integration | `40-tests.md` is unassigned. Collides with `rest-api-architect` at one, and only one, point: the HTTP contract **cases** are theirs (D16); the **strategy** is this rule's |
| — Output | Spec only. The skill emits `40-tests.md`; the executor writes the test code | Closes the same inconsistency with D15 that P7 closed in `rest-api-architect` |
| — ArchUnit | A setup mode inside the skill itself | Separate `arch-guard` skill |
| — Coverage | **Build gate**: 80% lines + 70% branches, JaCoCo, excluding only config and the `main` class | Qualitative criterion without a number |
| — Gate owner | `project-bootstrap` — already owns the POM, and a coverage gate doesn't fail on empty | `testing`'s setup mode |
| — Integration-test engine | Testcontainers mandatory | H2 |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `rules/testing.md` + `skills/testing/` rewritten with two modes | 9 | **Approved** |
| 2 | `rules/testing.md` + `skills/testing/` (design) + `skills/arch-guard/` (setup) | 5 | Rejected — one piece too many, no symptom |
| 3 | just rewrite the skill, rules in the body | 4 | Rejected — invariant 2 |
| 4 | create nothing | 2 | Rejected — the partial stays orphaned and stage 2 doesn't close |

### Option 1 — `rules/testing.md` + `skills/testing/` with two modes (score 9)

**Motivator:** axis 5. Mixed answer, and this repository splits the two halves: what's
always true becomes a leaf rule with `paths`; a sequence of steps becomes a skill. Direct
and recent precedent: `persistence.md` + `persistence-architect`, `api-rest.md` +
`rest-api-architect`.

**Shape of the two modes**, declared in the skill's body:

| Mode | When | Produces |
|---|---|---|
| **setup** | Once per project, once business classes already exist | ArchUnit installed: version resolved at runtime, `ArchitectureTest.java` with the translated packages |
| **design** | Per use case, after the three prior partials | `docs/use-cases/UC-NNN-<slug>/40-tests.md` |

Not two skills in disguise: they share the rule, the vocabulary, and the exemplars. The
mode is chosen by argument — a `UC-NNN-*` folder means design, no argument means setup.

**Pros:**

- The rule auto-loads when touching `**/src/test/**`, even without anyone invoking the
  skill.
- Closes stage 2 in the order written in `CONTEXT.md`: rule before skill.
- Without `disable-model-invocation`, like the other four in the pipeline (D17):
  `/new-feature` can chain it.
- Consistent with D15/D16 — the skill emits spec, the executor writes `src/test/**`. A
  single owner for test code.
- The ArchUnit procedure already written in the stub is reused whole; nothing thrown
  away.

**Cons:**

- A skill with two modes is harder to describe than one with a single mode. Mitigation:
  the argument decides, and the body opens with the table above.
- The rule ends up fixing a number (80/70). Numbers in rules age; the qualitative
  alternative was explicitly rejected by the user.

**Points cut on the rubric:** criterion 5 loses half a point — two modes in one piece is
more maintenance than one; criterion 6 loses half a point — the rule+skill pair has
precedent, but a skill with two modes is unprecedented in this repository.

### Option 2 — `testing` + `arch-guard` separate (score 5)

One piece per mode, each with a single purpose. Fails axis 1: there's no symptom that
justifies `arch-guard`. `CONTEXT.md:206` already lists it as a hypothesis to reassess and
concludes that `testing` and `arch-doctor` probably cover it. Two pieces sharing the same
rule and the same exemplars, to separate two procedures no one confuses. Anti-pattern 9
(preemptive piece). Viable, hence 5, not 4.

### Option 3 — rules in the skill body (score 4)

Ships faster. `testing.md` is in the `00-index.md` planned-rules table and will be
written; on the day it is, the pyramid and coverage exist in two places and diverge on
the first update. Invariant 2 violated — caps at ≤ 4. It's exactly what item 11 of
`CONTEXT.md` says in writing to avoid.

### Option 4 — create nothing (score 2)

`40-tests.md` stays without an owner, stage 2 doesn't close, and its acceptance
criterion ("two complete specs in `docs/use-cases/`, with every partial filled in")
becomes unreachable. Axis 1 has a symptom written in two files.

## What the rule fixes, and what stays out

Boundary against invariant 2, verified piece by piece:

| Theme | Owner |
|---|---|
| Pyramid, slices, test names, test data, coverage, DB engine | `rules/testing.md` — **new** |
| HTTP contract cases (status, `errorCode`, body shape) | `rules/api-rest.md` + `30-rest.md` partial (D16) |
| Rules ArchUnit verifies | `architecture-ddd.md`, `naming.md`, `code-quality.md` — the testing rule **cites**, doesn't repeat |
| Which query and which index to test against the real engine | `rules/persistence.md` |
| JaCoCo plugin and the POM gate | `project-bootstrap` |

## References

| Statement | Source |
|---|---|
| Rule before skill, on pain of duplicated rules | `CONTEXT.md` § 7, stage 2, item 11 |
| A rule never mentions a skill; `rules/` is a leaf | `@CLAUDE.md` invariant 1 |
| A theme with a single owner, cited by path | `@CLAUDE.md` invariant 2 |
| Boilerplate lives in `templates/*.example` of the skill that emits it | `@CLAUDE.md` invariant 3 |
| Agent only for preserving context, restricting tools, or changing model | `@CLAUDE.md` invariant 5 · `references/decision-matrix.md` § 5 |
| A rule that must always hold is a gate, not prose | `@CLAUDE.md` invariant 6 |
| Versions never from memory; resolved at runtime | `@CLAUDE.md` invariant 8 · ArchUnit procedure already in the stub |
| New rules and skills have to reach the generated project | `@CLAUDE.md` invariant 9 · `project-bootstrap` steps 6.6 and 6.7 |
| Layer skills emit spec, not code | D15 · `@.claude/decisions/0003-skill-domain-modeling.md` |
| Contract test cases belong to `rest-api-architect` | D16 · `@.claude/decisions/0006-rest-api-architect-design.md` |
| Pipeline skill carries no `disable-model-invocation` | D17 · `@.claude/decisions/0007-pipeline-skills-invocation.md` |
| Bootstrap doesn't install ArchUnit: a rule over zero classes fails on empty | D3 · `CONTEXT.md:48` · `testing/SKILL.md` § "Why bootstrap doesn't generate ArchUnit" |
| `arch-guard` was already listed as a hypothesis to reassess | `CONTEXT.md:206` |
| Testcontainers against the real engine, not H2 | `persistence-architect/references/sql-tuning.md` · `best-practices-links.md` |
| `testing.md` is unwritten and its territory is already planned | `@.claude/rules/00-index.md`, planned-rules table |

## Propagation

| File | Change |
|---|---|
| `.claude/rules/testing.md` | NEW |
| `.claude/rules/00-index.md` | moves from "planned" to "written" |
| `.claude/skills/testing/SKILL.md` | rewritten — two modes, without `disable-model-invocation` |
| `.claude/skills/testing/templates/test-spec.md.example` | NEW — shape of the partial |
| `.claude/skills/testing/templates/{TestFixtures,DomainTest,UseCaseTest,PersistenceIT}.java.example` | NEW — form exemplars per level |
| `.claude/skills/project-bootstrap/templates/pom.parent.xml.example` | JaCoCo gate 80/70, `check` in the `verify` phase |
| `.claude/skills/claude-code-architect-designer/references/decision-matrix.md` | § 4 rewritten: Form 1 for a pipeline piece; `testing` stops being the stub precedent |
| `.claude/skills/claude-code-architect-designer/templates/{SKILL.md,SKILL.command.md}.example` | stub references removed |
| `.claude/skills/rest-api-architect/references/best-practices-links.md` | version oracle corrected to `repo1.maven.org` |
| `monetization-roadmap.md` | no more stub skills; missing-rules count |
| `.claude/skills/project-bootstrap/SKILL.md` | step 6.6 (rule), step 6.7 (skill), POM step (JaCoCo) |
| `CLAUDE.md` | routing table line loses the "(stub)" |
| `.claude/skills/use-case-design/SKILL.md` | partials map loses the "(stub)" |
| `CONTEXT.md` | D18, inventory, stage 2 item 11 |

Goes to the generated project: **yes** — rule via step 6.6, skill via step 6.7.
