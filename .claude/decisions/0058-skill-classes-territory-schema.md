# 0058 · Skill classes as data: body structure validated by `schema`, write territory enforced by `guard`

- **Date:** 2026-09-27
- **Scenario:** "the skills have little enforcement and standardization between them in
  terms of structure (at least nothing deterministic) — I want a schema for every skill in
  the project so that any creation or edit, even a manual one, is validated by a hook";
  plus the observed fact that `/new-feature` and the skills it orchestrates should only
  emit specs under `docs/`, and in the last run on `demo-clean-arch-single-module` they
  wrote `docker-compose.yml` and other files.
- **Decision:** Form 7c — a `skill_classes` block in `@.claude/schemas/extensions.json`,
  read by two existing modes of `.claude/hooks/ArchHook.java` (`schema` for structure,
  `guard` for territory), plus Form 7a registrations in `.claude/settings.json` and in
  `project-bootstrap/templates/settings.json.example`.
- **State:** approved by Lucas Fernandes, on 2026-09-27 — Option 1, with one amendment:
  `java-patterns` goes into `build` instead of getting a class of its own. So **six**
  classes, not seven, and `java-patterns` carries an `overrides.java-patterns.write_allow`
  of `src/**` — the narrow territory the `advisory` class existed to give it, obtained with
  the mechanism `build` already needed for its other three skills.

## Interview

| Axis | Answer | Forms/options it eliminated |
|---|---|---|
| 1 — symptom | Two observed, both in one run: the pipeline wrote `docker-compose.yml` and a `schema-registry` service block while every design skill says in prose it writes only `docs/`; and the bodies of the 16 skills have no common shape — `## Contract` in nine, `## Skill contract` in `project-bootstrap`, no contract section at all in `arch-doctor` and `init-project` | "create nothing" |
| 2 — trigger | A file is written (`Write`/`Edit`), and a skill file is edited — including by hand, with no skill running | Forms 1-5: none of them run at the moment of the write |
| 5 — nature | Declarative facts (which class a skill is, what it may write, which sections its body carries) consumed by a procedure that already exists | Form 3; a new skill |
| 7 — mandatoriness | Cannot be allowed to fail: prose already said "design writes only under `docs/`" and the run wrote compose anyway | Everything above the line in decision-matrix § 1 |
| 8 — destination | **Both**, with different scope. `schema` validates authorship, which happens here and in a generated project that edits its own skills; `guard` protects the project where the leak happened, and here it starts protecting `.claude/**` from the `meta` class. This repository has **no** `guard` registration today | Generated-project-only (the status quo), meta-repo-only |
| 14 — lifecycle event | `PreToolUse` on `Write`/`Edit`/`MultiEdit` for territory (a write that already happened is a diff to revert); `PreToolUse`/`PostToolUse` on edits under `.claude/` for structure, where `schema` is already registered | `PostToolUse` for territory |
| 15 — reaction | Block, exit 2 | Form 8 (`permissions.deny` is static and cannot know a design phase is open); a warning-only allowlist |
| 16 — existing mode | Both checks land on modes that already exist: `schema` already validates frontmatter per `types.*`, `guard` already opens/closes a design phase and blocks `src/**` | Form 7c as a *new* mode; a second hook file |
| Taxonomy | 6 classes, with `java-patterns` left open | A 3-class and a 4-class split, both of which give `docker-architect` and `project-bootstrap` the same territory |
| Territory model | **Allowlist, deny by default.** Each class declares `write_allow`; anything else during an open phase is exit 2 | The denylist extension of `design_forbidden_paths` — which is exactly the failure mode observed: `docker-compose.yml` is not under `src/**` |
| Compose during the pipeline | **No.** The pipeline is docs-only, `docker-architect` included | Keeping `new-feature/SKILL.md:46`'s authorization; swapping the active class while `docker-architect` runs |
| Retrofit | All 16 skills conform in the same change | A `schema_exempt` grandfather list; a warn-then-block two-phase rollout |
| Body structure | `required_sections` per class, presence only, no order | Canonical order (it would make a future reorder a blocking change, and `docker-architect` carries a `## Service catalog` mid-body) |
| Owner of the territory data | `extensions.json` owns the globs. The body carries `**Class:** <c>` and prose without globs; `schema` checks the class exists and lists that skill | Repeating the globs in the body under an exact-match check (invariant 2); a frontmatter field (silently ignored by the runtime) |
| Guard surface | `Write`/`Edit`/`MultiEdit`/`NotebookEdit` | A `Bash` write-detector (heuristic command parsing, false positives); stripping generic `Bash` from the design skills' `allowed-tools` |

### The open point, and how it was settled

`java-patterns` was left open by the taxonomy answer ("a 7th class, or it falls into
`build`"). The draft proposed its own class, **`advisory`**, with `write_allow: ["src/**"]`,
on the grounds that `build`'s territory (`pom.xml`, `docker-compose.yml`, `.claude/**`) is
three times wider than "implement a pattern in a Java class" — criterion 8 of the rubric.

**Settled the other way, and the reason it costs nothing:** `build` has no class-wide
territory at all. Its default is the empty list and each of its skills carries its own
`overrides.<skill>.write_allow`, because `project-bootstrap` writes a whole tree and
`docker-architect` writes one file. So `java-patterns` in `build` with an override of
`src/**` has exactly the territory `advisory` would have given it, and the repo carries one
class fewer. What the fold does cost is stated plainly: the class no longer predicts the
territory for any of its four members, and a reader who assumes it does will be wrong —
which was already true of `build` before this amendment.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `skill_classes` in `extensions.json`, read by `schema` (structure) and `guard` (allowlist territory), + registrations in both `settings.json` files, + retrofit of all 16 skills | 9 | Proposed |
| 2 | The same block and the same `schema` validation, but `guard` keeps the denylist model with `design_forbidden_paths` extended | 6 | Rejected by the territory answer — and by the symptom: the leaked file was one nobody had listed |
| 3 | A separate `.claude/schemas/skill-classes.json` plus a new `classes` mode in `ArchHook.java` | 4 | Rejected — invariant 10 makes `extensions.json` the single owner of every list the hook reads, and rubric criterion 17 rejects a new mode where `schema` and `guard` already run |
| 4 | Create nothing: keep the territory in each `## Contract` in prose and standardize the bodies by review | 1 | Rejected — that is the status quo that produced the symptom |

### Option 1 — `skill_classes` + the two existing modes (score 9)

**Motivator:** axis 7 (prose already failed) and axis 16 (both checks land on modes that
already exist, so Form 7c is an extension, not a new executable).

#### The data

One new top-level block in `@.claude/schemas/extensions.json`. Six classes; the `write_allow`
of a class is the default, and a skill may narrow or widen it with its own `overrides` entry,
because `build` holds four skills whose real territories differ by an order of magnitude.

| Class | Skills | `write_allow` | `required_sections` beyond the universal |
|---|---|---|---|
| `design` | `use-case-design`, `domain-modeling`, `persistence-architect`, `rest-api-architect`, `messaging-architect`, `test-architect` | `docs/use-cases/UC-*/**`, `docs/use-cases/BACKLOG.md` | `## Target`, `## How it's invoked`, `## Procedure` |
| `orchestrator` | `new-feature`, `init-project` | `docs/use-cases/**`, `docs/lessons-learned/**` (class default); `init-project` overrides with `[]` — it writes nothing itself, it delegates to `project-initializer` | `## Procedure` |
| `build` | `project-bootstrap`, `docker-architect`, `arch-adopt`, `java-patterns` | class default `[]`, one override per skill: `project-bootstrap` the whole tree; `docker-architect` `docker-compose*.yml` + `docker/**`; `arch-adopt` `.claude/**` + `.gitignore`; `java-patterns` `src/**` | `## Procedure` |
| `observer` | `arch-doctor`, `audit-usage` | `[]` — the reports are written by the `audit` hook, not by the skill | — |
| `meta` | `claude-code-architect-designer` | `.claude/**`, `CLAUDE.md`, `.mcp.json`, `MCP-SETUP.md`, `docs/**`, `.github/**` | `## Procedure`, `## Out of scope` |
| `ops` | `git-publish` | `[]` — its effect is `git` over `Bash`, which the guard does not cover | `## Procedure`, `## Failure modes` |

A section is matched as a **prefix** of an H2 line, which is what lets
`## Procedure — design mode` satisfy `## Procedure` without `test-architect` growing a second
heading it does not need.

Universal, every class: `## Contract`, and a `## Why this is <form>` heading (matched by
regex, since the form's name varies).

#### What `schema` gains

Five checks, all of them failing by name, on the files that mode already visits:

1. Every `.claude/skills/*/SKILL.md` on disk appears in exactly one class's `skills` list.
   Same discipline as `export.skills.include`/`exclude`: not listing a skill **is** the bug.
2. The body carries a `**Class:** <c>` line, `<c>` exists, and that class lists this skill.
3. Every `required_sections` entry for that class is present, plus the two universal ones.
4. Every class in the block has a `write_allow` key (an empty list is a decision; a missing
   key is an omission).
5. `guard`'s existing `design_skills` list is derived, not parallel: it becomes the union of
   the classes marked `design_phase: true` (`design` and `orchestrator`), and a name in the
   old list that no class claims is an error.

#### What `guard` gains

The phase file already exists (one file per session, OS temp dir, holding the skill name).
Three changes, no new state:

- `write` resolves the active skill's class and requires the path to match its
  `write_allow`. Anything else: exit 2, naming the class, the path, and the allow list. The
  `executor_agents` bypass and the frozen-spec check stay exactly as they are.
- `call` refuses a `Skill` call to a `build`-class skill while a `design` or `orchestrator`
  phase is open — exit 2. This is what makes the pipeline docs-only in the mechanism rather
  than in prose: `docker-architect` cannot be reached from inside a `/new-feature` run at
  all, so there is no class to swap and no phase to restore.
- `project-initializer` joins `executor_agents` (and declares `**Executor:** yes` in its own
  body, which `schema` already cross-checks), because `/init-project`'s whole job is
  delegated to it and its writes are the generated project.

`meta`'s territory grew two entries while this very change was being written: `docs/**` and
`.github/**`. The propagation step of the `meta` class edits both — a reference page in
`docs/` describes each hook, and a new check gets a workflow step — so leaving them out
would have had this class block its own step 8 the next time.

Two rules the draft did not have, both found while exercising the mode:

- **A phase is never replaced by a skill of its own class.** `project-bootstrap` chains
  `docker-architect` in its step 4.10 — both `build`. Replacing the phase there would leave
  `docker-architect`'s one-file territory active for the rest of `project-bootstrap`'s
  generation and block every file it writes afterwards. Two skills of one class share a
  territory, so narrowing to the callee buys nothing and costs that.
- **"Listed but absent" is an error only in this repository.** Three creation skills and
  `project-initializer` deliberately do not travel (`export.skills.exclude`,
  `export.agents.exclude`), so a generated project would otherwise report four phantom gaps.
  The gate is `isSourceRepo`: the `export` block is present at all — the exported copy drops
  it — **and** `export.source_marker` still resolves here. The `export` block alone is what
  the marker could no longer do by itself, since `.claude/blueprints/` now travels with the
  active blueprint. Coverage the other way (a skill on disk that no class lists) is checked
  everywhere: a project that writes a skill of its own needs a class for it too. This also
  fixes the same latent gap in `checkExecutorAgents`, which `project-initializer` exposed.

#### What changes in the pipeline's prose, as a consequence

`new-feature/SKILL.md` § Contract (`:46`) and § End of flow (`:506`, `:510`, `:525`) drop
`docker-compose.yml` and `docker/init/**` from what a design-only run may touch.
`persistence-architect` step 9 and `messaging-architect` step 9 stop invoking
`docker-architect` and instead **record** the missing service in the partial's § 6 and in
the consolidated spec, so the user runs `/docker-architect` afterwards.
`test-architect`'s setup mode does the same with the Testcontainers tag mismatch it
currently hands straight to `docker-architect`.

**Pros.** The allowlist catches the file nobody predicted, which is the whole shape of the
observed failure. Both checks reuse a mode, so the generated project receives them for free
(`export` copies `ArchHook.java` and `extensions.json` whole). The class is one line in each
body, so the skill the model reads at runtime states its own class without carrying globs
that could drift. `arch-doctor`/`audit-usage` and `git-publish` get an empty allow, which is
a real statement — an observer that writes is a bug.

**Cons.** Four, all real.

1. The `build` class needs per-skill overrides on day one, so the class is a grouping for
   *structure* and only a default for *territory*. A reader who assumes class ⇒ territory
   will be wrong about `build`.
2. Blocking a `Skill(build)` call during a design phase removes a convenience that worked:
   the compose service used to appear in the same run. The cost is a second, manual step,
   and the benefit is that a design run's diff is reviewable as docs.
3. The guard sees `Write`/`Edit` only. A design skill that shells out through `Bash` still
   escapes — the interview accepted this: it has never been the path used, and a command
   parser would block `grep x > /dev/null`.
4. The retrofit touches all 16 skills in one commit, and two of them
   (`arch-doctor`, `init-project`) get their first contract section — text written now, not
   derived from an earlier decision.

**Points cut in the rubric:** criterion 9 — `guard write` now spawns a JVM for every
`Write`/`Edit` while a phase is open, not only for `src/**` paths, and it blocks on a
judgment the data can be wrong about (a path legitimately missing from an allow list stops
a run until someone edits `extensions.json`); criterion 5 — the per-skill override inside
a class block is one indirection more than "class ⇒ territory".

### Option 2 — same data, denylist territory (score 6)

Keeps `design_forbidden_paths` and grows it with `docker-compose*.yml`, `docker/**`,
`pom.xml`, `.gitignore`. Cheaper diff, and it would have caught this specific run. It does
not catch the next unlisted file, which is what the symptom is: the list is written after
the leak, every time.

### Option 3 — separate schema file + new `classes` mode (score 4)

Cleaner on paper (skill classes are not "frontmatter extensions"). It splits ownership of
the lists the hook reads across two files — invariant 10 — and adds a third mode to keep in
sync with `schema` and `guard`, both of which already walk the same files. Capped by
criterion 17.

### Option 4 — create nothing (score 1)

Recorded for what it costs: the next `/new-feature` writes whatever file the model judges
necessary, and `claude plugin validate` keeps printing `✔ Validation passed` over a skill
with no contract section.

## References

| Claim | Source |
|---|---|
| `guard` already opens a design phase, blocks `src/**`, and freezes approved specs | `.claude/hooks/ArchHook.java:3447-3552`; `guard` block of `@.claude/schemas/extensions.json` |
| `guard` is registered only in the generated project | `.claude/skills/project-bootstrap/templates/settings.json.example` lines 17, 49, 72-93; `ArchHook.java:3462` ("No `guard` block, no guard") |
| The pipeline is authorized in writing to write compose | `.claude/skills/new-feature/SKILL.md:46`, `:506`, `:510`, `:525` |
| The design skills invoke `docker-architect` mid-run | `.claude/skills/persistence-architect/SKILL.md:243`; `.claude/skills/messaging-architect/SKILL.md:241`; `.claude/skills/test-architect/SKILL.md:190` |
| A `schema-registry` block was hand-written into compose during a design run | `.claude/lessons-learned/lessons-learned-012.md` §§ 4, 12, 13, 14 |
| Bodies have no common shape today | `## Contract` in 9 skills; `## Skill contract` + `## Output contract` in `project-bootstrap`; neither in `arch-doctor` and `init-project` |
| An invented frontmatter field is silently ignored | `@CLAUDE.md` § Known pitfalls; `@claude-help.md` |
| Lists a hook reads live in `extensions.json`, never in the Java | `@CLAUDE.md` invariant 10 |
| A rule that must always hold is a hook, not prose | `@CLAUDE.md` invariant 6 |
| One owner per norm; the body cites, never duplicates | `@CLAUDE.md` invariant 2 |
| `schema` already fails by name on a skill listed in neither `include` nor `exclude` | `export.$comment` of `@.claude/schemas/extensions.json`; `@CLAUDE.md` invariant 9 |
| `executor_agents` is cross-checked against each agent's `**Executor:** yes` | `.claude/hooks/ArchHook.java:1425-1463` |
| `/init-project` delegates everything to `project-initializer` | `.claude/skills/init-project/SKILL.md` § Arguments |
| `test-architect` setup mode delegates everything to `archunit-installer` | `.claude/skills/test-architect/SKILL.md:181-187` |
| A new mode is rejected when an existing one covers the check | `references/decision-matrix.md` § anti-patterns, criterion 17 |
| `settings.json` is read only at session startup | `@CLAUDE.md` § Known pitfalls |

## Propagation

| File | Change |
|---|---|
| `@.claude/schemas/extensions.json` | ✅ New `skill_classes` block — 6 classes with `skills`, `write_allow`, `required_sections`, `design_phase`, `blocked_during_design`, `overrides`, plus `match`, `class_marker`, `universal_sections`, `why_section`. `guard` loses `design_skills` and `design_forbidden_paths` (both now derived from `skill_classes`, single owner) and gains `project-initializer` in `executor_agents` |
| `.claude/hooks/ArchHook.java` | ✅ `schema`: `checkSkillBody` (class marker + required sections + `## Why`) per file, `checkSkillClasses` (coverage, one class per skill, `write_allow` key present, `overrides` names a listed skill) at sweep. `guard`: allowlist in `write`, `build`-class refusal in `call`, same-class no-replace, phase opened by every class. New `isSourceRepo` gate, also applied to `checkExecutorAgents`. Header comment and both Javadocs carry the Form 7c three sentences |
| `.claude/settings.json` | ✅ First `guard` registrations in this repo: `UserPromptSubmit` → `guard prompt`; `PreToolUse Skill\|Agent\|Task` → `guard call`; `PreToolUse Write\|Edit\|MultiEdit\|NotebookEdit` → `guard write`, deliberately with no `if`. `doctor` now reports 15 registrations across 4 events |
| `.claude/skills/project-bootstrap/templates/settings.json.example` | ✅ The four `guard write` entries filtered by `if` collapse into one with none — the allowlist is what filters now; `_comment` says why |
| All 16 `.claude/skills/*/SKILL.md` | ✅ `**Class:** <c>` in the contract section of every one. `arch-doctor` and `init-project` gained `## Contract`, `## Why …` and (init-project) `## Procedure`; `project-bootstrap` gained `## Contract` + `## Why …` and kept `## Output contract`; `java-patterns` gained `## Why …`; `use-case-design`'s `## Request` became `## Target` |
| `.claude/skills/new-feature/SKILL.md` | ✅ § Contract: "Writes outside `docs/`: none", enforced. § End of flow: stages only `docs/`; the compose service is reported as pending. § Final report: pending follow-ups with the command that fixes them |
| `.claude/skills/persistence-architect/SKILL.md` · `messaging-architect/SKILL.md` · `test-architect/SKILL.md` | ✅ Step 9 / setup mode records the missing service or the tag mismatch with the `/docker-architect` command, and no longer invokes it; the Contract of each says so |
| `.claude/skills/docker-architect/SKILL.md` | ✅ `description` and § How it's invoked: two invocation paths, not three — by hand, or chained by `project-bootstrap` step 4.10. § Contract states the class and that it is unreachable mid-design |
| `.claude/skills/project-bootstrap/SKILL.md` · `.claude/agents/archunit-installer.md` | ✅ The deferred-to-`docker-architect` sentences no longer say a design skill chains it |
| `.claude/agents/project-initializer.md` | ✅ `**Executor:** yes`, and why its writes bypass the guard |
| `@CLAUDE.md` | ✅ Invariant 9: a skill's second obligation is a class. ✅ Routing row for `skill_classes`. ✅ § Known pitfalls: the allowlist blocks with exit 2, the fix is the data, a design run cannot call a `build` skill, and nothing is restricted while no phase is open |
| `.claude/skills/claude-code-architect-designer/SKILL.md` | ✅ Phase 4 step 8's Skill row: a new skill is incomplete until a class lists it, with the override and the body line |
| `.claude/.ci/SkillTerritoryTest.java` (new) · `.github/workflows/validate.yml` | ✅ Eleven cases over the real `extensions.json`, no Docker and no project: no phase means no restriction, inside/outside the territory, the executor bypass, the `build`-class refusal mid-design, the callee's narrower territory, and the phase closing on an executor `Agent` call. Registered in the `hooks-cross-platform` job next to `BoundaryTest` |
| `.claude/.ci/InjectionPathTest.java` | ✅ Its synthetic `probe-skill` now needs a class: the throwaway project's copy of the schema gets one extra class holding only the probe (`stubSkillClass`), and the four fixtures carry the universal sections. Same shape as the `stubExecutorAgents` that was already there |
| `docs/03-new-feature.md` · `docs/en/03-new-feature.md` · `docs/08-audit-usage.md` · `docs/en/08-audit-usage.md` | ✅ Derived docs: the pipeline no longer chains `docker-architect`, and the `guard` reference is rewritten around `skill_classes` (three boundaries, allowlist, no path filter) |

Goes to the generated project: **yes** — `ArchHook.java` and `extensions.json` travel whole
through the `export` mode, and the `guard` registration reaches it through
`project-bootstrap`'s template. This record does not travel (invariant 9).

**Verified:** `schema` exit 0 here and in the exported tree; `claude plugin validate
.claude/skills` passes; `doctor` reports 15 registrations across 4 events (was 12 across 3);
all four `.claude/.ci/` tests pass; the `guard` modes exercised by hand across eleven cases;
all 7 blueprints export twice byte-identical outside the stamp, with no residue reported and
no `.claude/decisions/` path surviving into the exported skills; `rules/` still names no skill
or agent.

**Restart required:** `.claude/settings.json` is read only at session startup, so the `guard`
registrations added to this repository do nothing until `claude` is restarted. Inside a
generated project they arrive with generation and are active from its first session.

## Open, and named here so it is not discovered by surprise

`@CLAUDE.md` is already 242 lines, past the ~200-line target (`0057` § Open). This change
adds two more pitfall lines and a routing row. The extraction of § Known pitfalls into a
rule with `paths: .claude/**` is still its own decision, and this record does not make it.
