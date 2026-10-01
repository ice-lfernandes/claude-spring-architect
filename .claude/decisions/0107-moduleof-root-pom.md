# 0107 · `moduleOf` maps root `src/` to the root POM, so single-module projects get compile, format and tests hooks

- **Date:** 2026-10-01
- **Scenario:** Issue #60, triaged at `538d113` — `ArchHook.moduleOf` never checks the project
  root for `pom.xml`, so in single-module projects it returns null and the `format`, `check`
  step 2 and `tests` hooks silently do nothing.
- **Decision:** Option A — in `.claude/hooks/ArchHook.java`, `moduleOf` returns `"."` when
  the walk finds no nearer POM, `rel` starts with `src/` and `ROOT/pom.xml` exists. `check`,
  `format`, `tests`, their registrations and their stderr text are unchanged. No new mode,
  no new registration.
- **State:** approved by Lucas Fernandes, on 2026-10-01

## Reproduced on disk before classifying

Checked by `issue-verifier` against `538d113` (`/triage-issue 60`, verdict `confirmed`, layer
static). HEAD is still `538d113`. Only the confirmed rows below are inputs here. The issue's
proposed fix is not an input, and neither are its refuted or unproven rows.

| Claim | On disk |
|---|---|
| `moduleOf` walks up from the file's parent and never tests `ROOT/pom.xml` | Confirmed, `ArchHook.java:5726-5736`: for `src/main/java/X.java` the walk visits `src/main/java`, `src/main`, `src`, then `getParent()` is `null` and the loop ends |
| `check` step 2 and `format` return silently on `null` | Confirmed, `:131` (`if (module == null) return; // no POM yet`) and `:153` |
| `tests` (Stop) drops the nulls and returns on an empty set | Confirmed, `:171-174`. The issue did not report this caller |
| The `s.isEmpty() ? "."` branch can't be reached | Confirmed, `:5731`: the walk never reaches the empty path |
| `moduleOf` has been this way since the first commit; nothing has fixed it | Confirmed, `git log -L` back to `55588bd`; `v0.13.2` = `538d113` |

Reach, checked here: four of the seven blueprints declare `layout: single-module`
(`clean-architecture-single-module`, `layered`, `modular-monolith`, `vertical-slice`). In every
project generated from one of them, the edit-time compile check, the formatter and the
Stop-time test run have never executed. Decision 0097 measured the `check` hook's command by
hand on `demo-clean-arch-single-module` (§ Measured, 1.90 s) without noticing that the hook
itself never ran it.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Confirmed by triage: three registered hooks are no-ops in four blueprints | create nothing |
| 7 — mandatoriness | The hooks are already the guarantee; the defect is inside them | Forms 1–6 |
| 8 — destination | Both: `ArchHook.java`, the jar and `extensions.json` travel through `export` whole | — |
| 16 — existing mode | `check`, `format` and `tests` own the behavior; no new mode, no new registration | new Form 7c mode, new 7a entry |
| — scope | `moduleOf` only. Claim 5 (no `migrations` entry for 0097's build change), claims 6–7 (Kafka `DefaultErrorHandler` template, listener test template) and the #48 leftovers are separate runs | bundling unrelated fixes into one record |
| — root scope | Only a file under the root `src/` maps to the root POM | "any file under root": an edit to `.claude/hooks/ArchHook.java` matches `Edit(**/*.java)` and would compile and format the whole project |
| — Stop tests | On, same contract as multi-module | `tests` skipping `"."` |
| — doctor | No new line | a `Module map` line in `doctor` |

Measured for the Stop answer: `./mvnw -q -o -pl . -am test` on
`demo-clean-arch-single-module` (61 test files, surefire only) took 20.1 s cold and 16.7 s
warm. It runs only when a `.java` file changed in the turn (`tests` reads `git diff` and
untracked files).

Checked for the callers: Maven 3.9.16 accepts `-pl . -am` on a single-module POM (exit 0). So
`"."` can go to all three callers as is, and none needs a special case.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| A | `moduleOf` falls back to `"."` when `rel` starts with `src/` and `ROOT/pom.xml` exists; callers unchanged; new `ModuleMapTest` | 9 | **Approved** |
| B | Same fallback, plus each of the three callers drops `-pl` when the module is `"."` | 7 | Viable, not recommended: `-pl .` already works, so this adds three branches that prove nothing |
| C | Root fallback for any file with no nearer POM | 5 | Rejected by the root-scope answer |
| D | Create nothing; document in `11-pitfalls.md` that single-module projects have no compile/format/test hooks | 2 | Rejected: an observed failure in a guarantee that four blueprints ship |

### Option A — root fallback scoped to `src/` (score 9)

**Motivator:** axes 1 and 16. The defect is in a helper shared by three existing modes, and
the root-scope answer limits the fallback to Maven's standard source tree.

**What changes:** in `moduleOf`, after the walk finds no nearer POM, return `"."` when `rel`
starts with `src/` and `ROOT/pom.xml` is a regular file. Otherwise return `null`, as today. The
unreachable `s.isEmpty() ? "."` branch goes away. Multi-module results don't change: a file in
`domain/src/...` still finds `domain/pom.xml` first. A file under `.claude/` still maps to
`null`. `check`, `format`, `tests`, the registrations and the stderr text don't change.

**Pros:** one method, one condition. It reaches all three callers at once. It is null-safe
mid-generation: with no root POM yet it still returns `null`, which is what the `// no POM
yet` comment expects.

**Cons:** single-module projects start paying for checks they have never run: about 1.9 s of
`test-compile` per Java edit (0097) and 17–20 s of `test` at a Stop with Java changes
(measured above). A project with a test that is red today but never ran will start blocking
at `Stop`. That is the hook doing its job, and the report must say so.
`src/` is a literal in the source. It is Maven's standard layout, the same kind of constant as
the `"pom.xml"` literal next to it, and not a list a blueprint varies. Invariant 10 isn't
strained, but whoever adds Gradle support to these modes has to revisit it.

**Points cut in the rubric:** 9 (cost of always running) — a new process cost per Java edit
and per Stop in four blueprints, though `if: Edit(**/*.java)` and the git diff already narrow
it.

**CI:** new `.claude/.ci/ModuleMapTest.java`, a step in `validate` › `hooks-cross-platform`
(ubuntu, macos, windows). It builds a throwaway project with a stub `mvnw` / `mvnw.cmd` that
records its arguments, runs the jar's `format`, `check` and `tests`, and asserts on the
recorded `-pl`. Cases: a single-module `src/main/java` file gives `-pl .`; a multi-module
`domain/src/...` file gives `-pl domain`; `.claude/hooks/ArchHook.java` in a single-module
project gives no call; no `pom.xml` gives no call; `tests` with a changed root `src/` file gives
`-pl .`. Red run: revert the fallback, and the single-module cases must fail by name.

### Option B — fallback plus `-pl` omitted for `"."` (score 7)

Same as A, plus a branch in each caller. Shown to be unnecessary by the `-pl . -am` run above.
It would leave the `-pl .` / no-`-pl` difference to be re-proven by whoever reads it next.
**CI:** the same `ModuleMapTest`, asserting no `-pl` instead.

### Option C — fallback for any file (score 5)

Simpler condition. But `.claude/hooks/ArchHook.java` is a `.java` file inside every generated
project and matches the hooks' `if`. Editing it would run `spotless:apply` and `test-compile`
over the whole project, and a change to it in the turn would run the whole test suite at Stop.
**CI:** same test, without the `.claude/` case.

### Option D — create nothing (score 2)

Invariant 6: a guarantee that ships and never fires is worse than none, because 0097 and the
templates were designed assuming it runs.

## References

| Claim | Source |
|---|---|
| The three callers and the walk | `.claude/hooks/ArchHook.java:130-131, 151-154, 168-174, 5726-5736` at `538d113` |
| `format` and `check` filtered by `if: Edit(**/*.java)` | `.claude/skills/project-bootstrap/templates/settings.json.example` `_comment` |
| Spotless formats only `src/main/java` and `src/test/java` by default; no `includes` set | `.claude/skills/project-bootstrap/templates/pom.parent.xml.example:91-117` |
| `test-compile` cost on a single-module project | `.claude/decisions/0097-lessons-learned-017-offline-build-loop.md` § Measured |
| Hook modes travel whole into the generated project | `@CLAUDE.md` invariant 9; `export` copies `ArchHook.java`, the jar and `extensions.json` |
| A hook test spawns the jar, not the source | `.claude/decisions/0084-ci-covers-jar-and-post-0075-guards.md` |
| Throwaway git repo as a test fixture | `.claude/.ci/SweepTest.java` |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | `moduleOf`: the root fallback scoped to `src/`; the unreachable `s.isEmpty() ? "."` branch removed; the Javadoc names this record |
| `.claude/hooks/ArchHook.jar` | Rebuilt under JDK 21 (`build --verify` green) |
| `.claude/.ci/ModuleMapTest.java` | New — see § CI coverage |
| `.github/workflows/validate.yml` | `hooks-cross-platform` › new step after `hook blocks forbidden import` |
| `docs/en/07-ci-validate.md`, `docs/pt-br/07-ci-validate.md` | Job diagram node `H13`, a row in § What each check covers, the local-run line; "ten tests" corrected to twelve (it was already eleven before this) |

No registration changed, and neither did `CLAUDE.md` § Commands (no new mode) nor
`11-pitfalls.md` (the hooks now do what the pitfalls page already assumed they did).

Goes to the generated project: **yes**, through `export`, which copies `ArchHook.java`, the
jar and `extensions.json` whole. A project that already exists gets it with `/arch-adopt`. On
the first `Stop` with a Java change, a single-module project starts running its unit suite
for the first time. A test that was red and never ran will block there, and that is the hook
working.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · hooks-cross-platform › format, check and tests hand Maven the module the edited file belongs to` | `ModuleMapTest.java`, 7 cases over throwaway projects whose `mvnw` stub records its arguments: single-module `src/main` through `format` (`-pl . spotless:apply`) and `check` (`-pl . -am test-compile`), `src/test` through `check`, `tests` with an untracked root `src/` file (`-pl . -am test`), multi-module `domain/src/...` (`-pl domain`), and no Maven call for `.claude/hooks/Tool.java` in a single-module project or for a project with no `pom.xml` | Green on the tree (macOS, JDK 21). Red with the fallback disabled: the 4 single-module cases fail with `Maven never called`. Red with the `src/` scope removed: `single-module .claude/ file` fails with `Maven called with: -q -o -pl . -am test-compile` |

The Windows leg runs the `mvnw.cmd` stub, which was not run locally. Its first CI run is the
proof. A failure there would be `Maven never called` on every case that expects a call.
