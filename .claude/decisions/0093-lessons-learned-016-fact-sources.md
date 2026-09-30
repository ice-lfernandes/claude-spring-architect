# 0093 · Remediation of lessons-learned-016 — where a project fact is read from

- **Date:** 2026-09-30
- **Scenario:** `@.claude/lessons-learned/lessons-learned-016.md` — the `/new-feature` run that
  redesigned `UC-003-initiate-kyc-verification` in an adopted, upgraded project. Twelve
  sections. The user scoped this run to the **fact sources**: §§ 2, 3 and 11, the places where
  a skill reads a project fact from a source that is absent, stale, or contradicts another.
- **Decision:** Options 2.1, 3.1, 11.1 and 11.4 — persuasion forms only, each fix in the file
  that already owns the fact: `arch-adopt` step 8 collects the bounded context (its territory
  gains the root `CLAUDE.md`); `export.body_transforms.replace` emptied and every skill or agent
  that named `CLAUDE.md` as the source of `packages.map` pointed back at the active blueprint;
  `rules/testing.md` makes the test profile active on every `@SpringBootTest`, found by
  `test-architect` step 2; `rules/persistence.md` § Boundary owns the adapter `@Transactional`
  exception, cited by `rules/architecture-ddd.md`. No hook, no new mode, no Java beyond one comment.
- **State:** approved by Lucas Fernandes, on 2026-09-30

## Scope of this run

In: §§ **2, 3, 11**.

Out — a later run takes them. Two mechanisms were **already chosen** by the user during this
interview:

| § | Deferred item | Mechanism already answered |
|---|---|---|
| 1 | `Partial status` line in `00-caso-de-uso.md` has no writer; lists 4 of 6 partials | **Remove the line** from `use-case-design/templates/use-case-spec.md.example` and from the 13 `examples/UC-1xx-*/00-caso-de-uso.md`. `status:` plus the files on disk already answer it |
| 8 | Audit trail dirties the tree after every `git-publish` | **Always-owned:** `.claude/audit-usage/**` never counts as pre-existing work, in `/new-feature`'s entry guardrail and in `git-publish`; it rides silently in the next commit. The trail stays versioned (`export.gitignore_lines` comment) |
| 4 | No documented `approved → draft` reopen | Not yet answered |
| 5 | `BACKLOG.md` in the pre-0069 shape has no migration | Not yet answered |
| 6 | Outbox exemplar has no lease/backoff column; step 4b contradicts itself; downstream partial adding an upstream requirement is unregulated | Not yet answered |
| 7 | `CHANGELOG.md` lines written at design time for code that may never exist | Not yet answered |
| 9 | "Everything dirty" keeps one commit with the wrong type; `sonarqube-setup` does not chain `git-publish` | Not yet answered |
| 10 | Audit does not see Bash writes; § Approval names no tool | Not yet answered |
| 12 | `audit ask`/`answer` record timestamps only | Not yet answered |

## Reproduced on disk before classifying

Checked against `a267e55`. Three of the lessons' claims do not hold as written, and are recorded
here so nobody re-opens them.

| § | Claim | On disk |
|---|---|---|
| 2 | "No skill, agent, template or hook produces [the bounded context]" | **Closed for new projects** by `0057`: `project-initializer.md:82` asks for it, `project-bootstrap/templates/root.CLAUDE.md.example:14` writes it. **Open for adopted projects and projects generated before `0057`**: `arch-adopt`'s territory is `.claude/**` + `.gitignore` (`extensions.json` `skill_classes.build.overrides`), no step asks. The citations to lessons-learned-001 § 1 and -003 do not hold — neither file mentions it; -012 § 5 does |
| 3 | `export.replace` rewrites the blueprint citation into `CLAUDE.md` | **Confirmed**, and its premise is gone: `export.blueprint_copy` ships `.claude/blueprints/{id}/{id}.yaml` into every project, so the citation it rewrites resolves. `ArchHook.java:1580` is a literal `String.replace`; the phrase is wrapped across two lines at `jobs-architect/SKILL.md:290`, `rest-api-architect/SKILL.md:290`, `new-feature/SKILL.md:336`, `rules/logging.md:123` and survives unrewritten — one skill, two sources. `root.CLAUDE.md.example` has no package table at all (`{{#each modules}}` only), so the rewrite pointed at a table the template never wrote. `use-case-design/SKILL.md:176-178` carries the same dual source in the meta-repo itself |
| 11 | `CLAUDE.md` says `application.usecase` is "the transaction boundary" and holds only `*UseCase` + `*Command` | "transaction boundary" is in no template or rule — the generated project's `CLAUDE.md` text was model-written. But the contradiction is real one level down: `rules/architecture-ddd.md:38` "The transaction opens and closes here. Never in the adapter" and `rules/persistence.md` § Boundary "Zero `@Transactional` in the adapter", while `persistence-architect/templates/OutboxEventStore.java.example:318-362` puts `@Transactional` on the three marks and the prune batch **in the adapter**, citing that same § Boundary |
| 11 | Test profile belongs once per project | **A defect, not only a model decision.** `rules/scheduling.md:48` and every jobs exemplar switch jobs off in `application-test.yml`, and nothing in the repo activates profile `test` — no `@ActiveProfiles`, no `spring.profiles.active`. The file never loads in a `*IT`; every job runs under test |
| 11 | `*Settings` has no legal home in `application.usecase` | Confirmed: `messaging-architect/templates/OutboxRelayPublisher.java.example:216` places `OutboxRelaySettings` in hexagonal's `application.service`; the clean-architecture naming comment (`clean-architecture-single-module.yaml:75`) names `Command` as the only non-`UseCase` class there |

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | §2: fourth record, stop at the 4th skill of a run; §3: `jobs-architect` step 2 and its Contract name two sources in the exported copy; §11: jobs run under test; relay rule vs. exemplar | create nothing, for §§ 2, 3 and the two §11 items taken |
| 2 — trigger | §2: adoption/upgrade, a human command; §3: `export`, unattended; §11: design of a test partial | — |
| 7 — mandatoriness | Persuasion is enough for all three: each failure is caught at the next step that reads the fact, and nothing ships silently wrong from it | Forms 7 and 8 |
| 8 — destination | Both: every file touched travels (`arch-adopt`, `use-case-design`, `test-architect`, `archunit-installer`, rules) | — |
| 9 — integration | §2: `arch-adopt` cannot write `CLAUDE.md`; the orchestrator neither. §11 profile: the executor writes `src/test/**`, `pom.xml` only for a declared dependency | options that write outside a class's territory without widening it |
| — §2 who collects | `arch-adopt` asks, when the line is missing | `/new-feature` pre-flight, doctor-only |
| — §3 how | Remove both `replace` pairs | hardening `replace`, regenerating `CLAUDE.md` |
| — §11 profile | `test-architect`, per test: every `@SpringBootTest` carries `@ActiveProfiles("test")` | executor J1 once, `project-bootstrap` |
| — §11 rest | Only the `@Transactional` exception | `*Settings` home, port-signature rule, version re-resolution |

## Options evaluated

### § 2 — bounded context in an adopted project

| # | Option | Score | Verdict |
|---|---|---|---|
| 2.1 | `arch-adopt` step 8 asks when the root `CLAUDE.md` has no `Bounded context:` line, writes the template's line | 8 | **Approved** |
| 2.2 | 2.1 + a `doctor` line (`doctor.bounded_context` data, check in the `doctor` mode) | 7 | Rejected |
| 2.3 | `/new-feature` pre-flight asks once and writes | 7 | Rejected |
| 2.4 | `doctor` line only | 5 | Rejected |
| 2.5 | `.claude/project.json` machine-readable, `CLAUDE.md` rendered from it (lessons' suggestion) | 3 | Rejected |
| 2.6 | create nothing | 2 | Rejected |

**2.1 — Motivator:** axis 9, `arch-adopt` is the one piece that runs in exactly the projects
`0057` did not reach. **Pros:** collected once per project, before any feature run, by the
piece that already asks the project-level question (blueprint, step 4); the line's wording is
read from the fetched `$SRC`'s `root.CLAUDE.md.example`, so it has one owner. **Cons:**
territory widens by one file (`CLAUDE.md`), against the "writes nothing but `.claude/`"
stance in the body, which has to be reworded. A project that is stuck today gets the question
only on the **second** `/arch-adopt` after this lands — the first runs the project's old copy
of the skill. **Cut:** enforcement (−1, persuasion, `doctor` available).

**2.2** — **Cut:** maintenance (−1, Java in `doctor` + a data block for a line whose absence
`messaging-architect` step 2 already names), cost of always running (−1 half). Kept as the
upgrade path if the fact goes missing again after 2.1.

**2.3** — the orchestrator's territory gains `CLAUDE.md`, a project-level file written from
inside a feature run — the shape `0057` moved away from. **Cut:** trust surface, form fit.

**2.4** — reports, closes nothing: the stop at the 4th skill stays reachable.

**2.5** — a second source of truth plus rendering machinery for one line. Cap: invariant 2
strained until `CLAUDE.md` is generated from it.

**2.6** — rejected: two observed occurrences in the same project after `0057`.

### § 3 — `export.replace`

| # | Option | Score | Verdict |
|---|---|---|---|
| 3.1 | Remove both `replace` pairs; fix the dual-source sentence in `use-case-design` step 5; drop the path parenthetical in `archunit-installer` so `residue_markers` stays quiet; correct the export header comment and `docs/*/10-arch-adopt.md` | 9 | **Approved** |
| 3.2 | 3.1 + `replace` made whitespace-tolerant or failing on a partial match | 6 | Rejected |
| 3.3 | Keep the rewrite; `arch-adopt` regenerates a package table in `CLAUDE.md` from `packages.map`, `doctor` compares | 3 | Rejected |

**3.1 — Motivator:** axis 1, one skill citing two sources in the exported copy. **Pros:** the
blueprint YAML is in every project already; removing the rewrite removes the drift instead of
policing it. No Java logic changes (a comment only). **Cons:** the `replace` list ends empty —
dead configuration until a next pair needs it. **Cut:** none material.

**3.2** — hardens a mechanism with nothing left to protect. Anti-pattern 9. **Cut:** form fit,
maintenance.

**3.3** — two sources for `packages.map`, kept in sync by a regeneration step. Violates
invariant 2 → capped at ≤ 4.

### § 11 — test profile never activated

| # | Option | Score | Verdict |
|---|---|---|---|
| 11.1 | `rules/testing.md`: the test profile is active on every `@SpringBootTest`; `test-architect` design step 2 lists every one missing `@ActiveProfiles("test")` as a row the executor adds | 8 | **Approved** |
| 11.2 | Executor J1 activates it once, at the project's first job | 7 | Rejected |
| 11.3 | `project-bootstrap` activates it at generation | 5 | Rejected |

**11.1 — Motivator:** axis 9, `test-architect` already owns the survey of every
`@SpringBootTest` (step 2's grep) and the one context test proving no trigger runs under the
profile (step 1). **Pros:** explicit per test, the Spring idiom; the norm lands in the rule
that already says "its own test profile". **Cons:** an annotation per context test; the case
that adds the first job edits earlier cases' tests. **Cut:** enforcement (−1).

**11.2** — a single activation point has no idiomatic file: surefire/failsafe
`systemPropertyVariables` is `pom.xml` config, which the executor may not write
(`agent_classes.executor`: dependencies only); `src/test/resources/config/application.yml`
works but is a classpath-precedence trick nobody reads as a profile switch. **Cut:** precedent,
trust surface.

**11.3** — does not reach adopted projects. **Cut:** form fit, propagation.

### § 11 — the rest

| # | Item | Score | Verdict |
|---|---|---|---|
| 11.4 | `rules/persistence.md` § Boundary names the one exception — a write no use case's transaction covers (a relay's mark, a prune batch) carries its own `@Transactional` on that adapter method, and nothing spans a broker call; `rules/architecture-ddd.md:38` cites it | 8 | **Approved** |
| 11.5 | `*Settings` home in the two clean-architecture blueprints' naming comment | — | Out of this skill's territory (`.claude/blueprints/**`, Contract). Recorded for the user to edit by hand |
| 11.6 | Rule on changing an approved case's inbound port return type | 3 | create nothing — one occurrence, recorded correctly in `CHANGELOG.md` |
| 11.7 | Executor re-resolves a version pinned in the spec | 3 | create nothing — rewrites an approved, immutable spec; a stale-but-published version still builds |
| 11.8 | Producer timeouts vs. lease | — | Belongs to § 6, deferred with it |

**11.4 — Motivator:** axis 1, two rules forbid what the exemplar does and cites them for.
**Pros:** the exemplar was right; the rule catches up with one sentence in its owning file and
a citation from the other (invariant 2). **Cons:** a rule edit that travels to every project.

## References

| Claim | Source |
|---|---|
| New projects already collect the bounded context | `.claude/decisions/0057-lessons-learned-012-inferences.md`; `project-initializer.md:82`; `root.CLAUDE.md.example:14` |
| `arch-adopt` writes `.claude/**` + `.gitignore` only | `extensions.json` `skill_classes.build.overrides.arch-adopt` |
| The active blueprint travels | `extensions.json` `export.blueprint_copy`; `ArchHook.java` `exportBlueprint` |
| `replace` is literal | `ArchHook.java:1580` |
| Residue scan flags `.claude/blueprints/` in any exported file | `extensions.json` `export.body_transforms.residue_markers` |
| The executor may write `pom.xml` for a declared dependency only | `extensions.json` `agent_classes.executor`; `.claude/decisions/0062-lessons-learned-013-ownership.md` |
| Jobs off in `application-test.yml` | `rules/scheduling.md:48`; `jobs-architect/templates/application-jobs.yml.example:50` |
| A rule never names a skill or template | `@CLAUDE.md` invariant 1 |
| A norm has one owning file | `@CLAUDE.md` invariant 2 |
| `claude-code-architect-designer` does not write blueprints | its `## Contract` |

## Found while writing

Two more readers named `CLAUDE.md` as the source of `packages.map` in their own body, not through
`replace`: `archunit-installer.md` **Reads** and `commons-logging-installer.md` **Reads**. Same
fact, same fix, folded into 3.1. The export dry run into an empty repository reported no residue
and no surviving `CLAUDE.md` pointer for `packages.map`.

## Propagation

| File | Change |
|---|---|
| `.claude/skills/arch-adopt/SKILL.md` | Step 8 · bounded context, when the root `CLAUDE.md` has none; intro "writes one line by itself"; step 7 wording; report line `Bounded ctx`; Contract territory and **Writes** |
| `.claude/schemas/extensions.json` | `skill_classes.build.overrides.arch-adopt.write_allow` += `CLAUDE.md`; `export.body_transforms.replace` = `[]` with `$comment_replace` |
| `.claude/hooks/ArchHook.java` (+ jar) | export header comment: only `decisions/` is cut; the active blueprint ships |
| `.claude/skills/use-case-design/SKILL.md` | step 5: one source, the active blueprint's `packages.map` |
| `.claude/agents/archunit-installer.md` | step 4 path parenthetical dropped; **Reads** points at the blueprint YAML |
| `.claude/agents/commons-logging-installer.md` | **Reads** points at `packages.map` |
| `.claude/rules/testing.md` | § Slices and context: test profile active on every `@SpringBootTest` |
| `.claude/skills/test-architect/SKILL.md` | step 2: grep for `@SpringBootTest` without `@ActiveProfiles("test")`, each one a change in the partial |
| `.claude/rules/persistence.md` | § Boundary: the adapter `@Transactional` exception, owned here |
| `.claude/rules/architecture-ddd.md` | cites that exception |
| `CLAUDE.md` | `arch-adopt` routing row names the bounded-context line |
| `docs/{pt-br,en}/10-arch-adopt.md` | `body_transforms` row; step 8 and territory |
| `docs/{pt-br,en}/11-pitfalls.md` | bounded-context paragraph: `arch-adopt` step 8, and the second-run caveat |

Goes to the generated project: **yes** — every skill, agent and rule above travels through
`export`; the `extensions.json` change to `replace` does not (the `export` block is dropped),
the territory change does.

Not done here, for the user: the `*Settings` home in the two clean-architecture blueprints'
naming comment (11.5) — `.claude/blueprints/**` is outside this skill's territory.
