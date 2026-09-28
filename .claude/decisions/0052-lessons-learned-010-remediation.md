# 0052 · Remediation of lessons-learned-010, items 6 to 9

- **Date:** 2026-09-26
- **Scenario:** "`lessons-learned-010.md` APENAS item 6, 7, 8 e 9"
- **Decision:** four items, three forms — item 6 is a hook (`ArchHook.java` `compose`
  mode); item 7 and item 9 edit existing skills, no new piece; item 8 is Form 5 plus
  citations
- **State:** approved by the user on 2026-09-26, highest-scoring option of each item, **and
  the item 6 hook implemented in the same change** — the user asked for it explicitly,
  overriding this skill's "propose and stop" rule for hooks (§ Out of scope). Noted here
  because the skill's own contract says the hook lands in its own commit; the branch
  `feat/lessons-learned-010-items-6-9` carries both

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 7 — mandatoriness (item 6) | Compose image tag and the Testcontainers pin **must** match; today the only link is a YAML comment | All six — invariant 6 sends it to `ArchHook.java`, out of this skill's scope. Deliverable is the spec plus this record |
| 9 — integration (item 7) | Enumerate the non-design territories (`docker-compose.yml`, `docker/init/**`), don't derive from `git status` | Rejected the dynamic derivation: it would stage a file the user had dirty before the run |
| 5 — nature (item 8) | Runtime fact, holds in every session, whole repo | Form 4 (no code territory — it is about the tool, not about the project's files) |
| 8 — destination (item 8) | This repo's `CLAUDE.md`; the generated project already carries its own copy in `root.CLAUDE.md.example` | — (deliberate duplication, invariant 9) |
| 9 — integration (item 9) | Parent spec leaves the shape of a domain field open; `domain-modeling` decides | Rejected making `use-case-design` read `value-objects.md` — two owners of criterion 3, invariant 2 |

## Options evaluated

### Item 6 — compose tag ↔ Testcontainers pin, nothing verifies it

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `ArchHook.java` `compose` mode compares `image:` against `DockerImageName.parse` | 9 | **Approved and implemented**, by the user's explicit request |
| 2 | Norm line in `@.claude/rules/testing.md` saying the two tags are the same | 4 | Rejected — persuasion where a guarantee was available, invariant 6 |
| 3 | Create nothing, keep the YAML comment | 2 | Rejected — two hand-kept occurrences of the same agreement already exist (Postgres and Kafka) |

#### Option 1 (score 9)

**Motivator:** axis 7. The tag has to match for a Testcontainers run to reproduce what
compose runs; a mismatch is silent and only shows up as a test that passes against the
wrong engine version.

**Spec of the change, for whoever implements it:**

- Mode: `compose`, which `doctor` already folds in — no new subcommand, no new wiring in
  `settings.json`.
- Read: every `image:` value of `docker-compose.yml` (the file `composeHostPorts` already
  parses) and every `DockerImageName.parse("…")` literal under `src/test`.
- Compare by repository name, report on the tag: same repository (`apache/kafka`,
  `postgres`) with a different tag is one warning line naming both sides and both files.
- Never blocks, same as the rest of `compose` mode: Docker is not a dependency of this
  repository, and a project with no compose file or no `src/test` yields nothing to check.
- Two skills stop promising it in prose once the hook exists: `docker-architect` step 4
  and `test-architect`'s setup-mode report keep the handshake, the hook is what verifies it.

**As implemented:** `imageTagMismatches`, `composeImages`, `testImagePins`, `splitImage`
and `withTags` in `ArchHook.java`. Computed **before** the first `docker` call, so a
machine with the daemon down still gets the answer; a repository present on only one side
is not a mismatch; a digest pin (`repo@sha256:…`) and an unresolved `${VAR}` are skipped;
`target/`, `build/`, `.git/`, `node_modules/` and `.idea/` are pruned from the `src/test`
walk. Exercised on a fixture: `postgres` 17-alpine (compose) against 16-alpine (test) is
reported, an equal `apache/kafka:3.8.0` is not, a test-only `redis:7` is not, and a
compose-only `jaegertracing/all-in-one` is not.

**Cons:** infrastructure change, which this skill normally proposes and does not write.

**Points cut in the rubric:** criterion 7 — the check has no test of its own in the repo;
it was verified by hand on a fixture, the way the rest of `compose` mode is.

### Item 7 — `/new-feature`'s "Not now" branch stages only `docs/`

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Enumerate the non-design territories in the branch, and declare them in § Contract | 9 | **Approved** |
| 2 | Derive the path list from `git status --porcelain` minus `src/` | 6 | Rejected — stages a file the user had dirty before the run |
| 3 | Each infrastructure skill reports the paths it wrote, orchestrator accumulates | 7 | Rejected — depends on every skill honoring a report contract; option 1 is deterministic today |

#### Option 1 (score 9)

**Motivator:** axis 9. `docker-architect` is the legitimate owner of `docker-compose.yml`
and is chained by `persistence-architect` step 9 and `messaging-architect` step 9 — so
"design writes nothing outside `docs/`" was already false when it was written.

**Pros:** deterministic list; matches the owners table the skill already carries; the
§ Contract stops omitting an indirect write.

**Cons:** grows by one line per future infrastructure owner.

**Points cut in the rubric:** criterion 5 — maintenance, the list is manual.

### Item 8 — `AskUserQuestion` with one option fails

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Form 5 — pitfall in this repo's `CLAUDE.md`, plus the lower bound cited in each pipeline skill's step 3 | 9 | **Approved** |
| 2 | Only the line in the skills' step 3 | 6 | Rejected — the failure happens in any `AskUserQuestion`, not only in pipeline skills |
| 3 | New rule in `rules/` with `paths` over `.claude/skills/**` | 4 | Rejected — it is a fact about the runtime tool, not about the project's files; `00-index` and step 6.6 would carry a rule with nothing to verify |

#### Option 1 (score 9)

**Motivator:** axes 1 and 5. `InputValidationError ... "too_small" ... path:
["questions",1,"options"]` discarded a whole four-question batch in `domain-modeling`'s
interview; the norm existed only in `root.CLAUDE.md.example`, which this repo never loads.

**Pros:** owner is the file the runtime loads every session here; the five pipeline skills
gain the lower bound next to the upper bound they already state ("at most 4 questions").
`use-case-design` already carries the checklist row — it is the precedent being copied.

**Cons:** `CLAUDE.md` grows (180 lines today, stays under the ~200 target). The same
sentence exists in `root.CLAUDE.md.example`: deliberate, invariant 9 — two repos, one
`CLAUDE.md` each, neither loads the other's.

**Points cut in the rubric:** criterion 3 — one more always-loaded line.

### Item 9 — parent spec fixes value object vs enum

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Component table leaves the shape open for a domain field; new § Out of scope row; new checklist row in step 3 | 9 | **Approved** |
| 2 | Drop the domain rows from the parent spec's table | 5 | Rejected — § Spec structure declares `00-caso-de-uso.md` stands on its own |
| 3 | `use-case-design` reads `value-objects.md` and decides the shape itself | 4 | Rejected — two owners of criterion 3, invariant 2 |

#### Option 1 (score 9)

**Motivator:** axis 9. `use-case-design` does not read `value-objects.md` (not in its
§ Contract) and still wrote `CustomerStatus` as a generic value object; `domain-modeling`
applied criterion 3 and corrected it to `enum`. Every closed-set field repeats it.

**Pros:** exactly the mechanism already used for HTTP status, idempotency and exception
names — three rows of the same § Out of scope table. § Resolved divergences goes back to
recording real conflicts instead of collisions the pipeline guarantees.

**Cons:** the parent spec's component table becomes slightly less precise for a reader who
only opens that file.

**Points cut in the rubric:** criterion 6 — none; precedent is the table's own three rows.

## References

| Claim | Source |
|---|---|
| A rule that must always hold is a hook, not prose | `@CLAUDE.md` invariant 6 |
| This skill proposes hooks and does not write them | `claude-code-architect-designer/SKILL.md` § Out of scope |
| `compose` mode already parses the compose file and already runs inside `doctor` | `.claude/hooks/ArchHook.java` § compose, `composeHostPorts` |
| `docker-architect` owns `docker-compose.yml`; `test-architect` owns the Java-side pin | `docker-architect/SKILL.md` step 4 and § Contract · `test-architect/SKILL.md` § Contract |
| Design phase chains `docker-architect` | `persistence-architect` step 9 · `messaging-architect` step 9 |
| `AskUserQuestion` with one option fails | `project-bootstrap/templates/root.CLAUDE.md.example` § Known pitfalls · `InputValidationError` quoted in `lessons-learned-010.md` § 8 |
| A single norm has one owner; others cite it | `@CLAUDE.md` invariant 2 |
| Two `CLAUDE.md` files, one per repo, is not duplication | `@CLAUDE.md` invariant 9 |
| The shape of a domain component belongs to `domain-modeling` | `use-case-design/SKILL.md` § Out of scope (three existing rows) · `@.claude/rules/value-objects.md` criterion 3 |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | `compose` mode compares compose `image:` tags with `src/test`'s `DockerImageName.parse` pins; header comment and mode list updated |
| `.claude/skills/docker-architect/SKILL.md` | Step 4 — the handshake is verified by `ArchHook.java compose`, not only promised |
| `.claude/skills/test-architect/SKILL.md` | Setup mode — same check named after the tag is pinned; step 3 batch lower bound |
| `.claude/skills/arch-doctor/SKILL.md` | `description` — the tag check is part of what `doctor` reports |
| `CLAUDE.md` | Routing row for `ArchHook.java compose` — tag mismatch added, and that it needs no Docker |
| `.claude/skills/new-feature/SKILL.md` | "Not now" branch path list; final report names files written outside `docs/`; § Contract declares the indirect writes outside `docs/` |
| `CLAUDE.md` | § Known pitfalls — `AskUserQuestion` minimum of two options |
| `.claude/skills/domain-modeling/SKILL.md` | Step 3 — batch framing: at most 4 questions, never fewer than 2 real options |
| `.claude/skills/persistence-architect/SKILL.md` | Step 3 — lower bound alongside the existing upper bound |
| `.claude/skills/rest-api-architect/SKILL.md` | Step 3 — same |
| `.claude/skills/messaging-architect/SKILL.md` | Step 3 — same |
| `.claude/skills/test-architect/SKILL.md` | Step 3 — same |
| `.claude/skills/use-case-design/SKILL.md` | § Out of scope — shape of a domain component; step 3 checklist row |
| `.claude/skills/use-case-design/templates/use-case-spec.md.example` | Component table — open shape for a domain field |
Goes to the generated project: **the skill edits do, via step 6.7, and the hook change via
step 7 — both files are already listed there, no table changes.** This repo's `CLAUDE.md`
does not: the project's copy of the `AskUserQuestion` pitfall already lives in
`root.CLAUDE.md.example`, and the project's routing lives in that same template. This
record does not travel — invariant 9.
