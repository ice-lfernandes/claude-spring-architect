# 0128 · `doctor gate` fails the generated project's boundaries job on a broken committed check

- **Date:** 2026-10-06
- **Scenario:** Issue #104, triaged at `982420e`: the generated architectural-boundaries CI job
  never fails because `ArchHook.java doctor` always exits 0, even when it reports
  `ENFORCEMENT OFF` / `Setup incomplete`.
- **Decision:** Option 1, Form 7c without a registration — `doctor gate`, a sub-form of the
  existing `doctor` mode in `.claude/hooks/ArchHook.java`, gating on `doctor.gate.labels` of
  `.claude/schemas/extensions.json`, run by both generated CI templates.
- **State:** approved by the maintainer, on 2026-10-06

## Reproduced on disk

The confirmed rows of the `issue-verifier` table, checked at `982420e`. No commit since touches
a cited file.

| # | Fact | Evidence |
|---|---|---|
| 1 | The `boundaries` job of the generated Maven CI has one check, `java .claude/hooks/ArchHook.java doctor`, commented "Proves enforcement is switched on for this machine, not just declared." | `project-bootstrap/templates/ci.yml.example:78-91` |
| 2 | The Gradle template carries the same step | `project-bootstrap/templates/ci-gradle.yml.example:79,89-91` |
| 3 | `main` exits 0 on every path | `hooks/ArchHook.java:87,92,94` |
| 4 | `doctor()` only prints; its verdict is a stderr line | `hooks/ArchHook.java:331-332,483-485` |
| 5 | The README lists `doctor` as non-blocking | `README.md:498` |
| 6 | With no `forbidden-imports.txt`, `doctor` prints `ENFORCEMENT OFF` and `Setup incomplete`, and exits 0 | run at `982420e`: `exit=0` |
| 7 | Nothing under `.claude/` changed between the reporter's ref `c95951f` and `982420e` | `git log c95951f..982420e -- .claude/` — 0 commits |
| 8 | Pushing that state passes the job | follows from 1, 3, 4, 6 |

The issue proposes no fix. Not a regression: the comment dates from the initial commit
`55588bd` and was copied into the Gradle template by `a381a20`.

Found while designing, not in the issue:

- **`0111` counts on this step.** Its CI table lists "Generated project `ci.yml.example` ›
  `doctor` — a bad `audited.json` shows as `❌`". The `❌` shows, and the job is green anyway.
- **This repository's own CI runs the same mode, and expects it incomplete.**
  `validate.yml:62-63,73` and `release.yml:121` run `doctor` here, where `Boundaries` and
  `Maven wrapper` are always `❌` (no `forbidden-imports.txt`, no `mvnw`). A `doctor` that exits
  non-zero on `Setup incomplete` turns them red.
- **A CI runner always fails two lines that are not defects.** `CLAUDE_PROJECT_DIR` is never
  set there, and `Compose` reports services that a `build` runner never started.
- **`doctor` is blind to Gradle.** `wrapper()` (`ArchHook.java:6558`) looks only for
  `mvnw`/`mvnw.cmd`, so a Gradle project always prints `Maven wrapper ❌` and `Setup
  incomplete`. Left to a separate issue (axis 9 below).

## Interview

The triaged table answered the axes that need no user. These are the ones that remained or
eliminated forms.

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 2 — trigger | A step of the generated project's CI | 1, 2, 3, 6: no model decision and no Claude Code runtime event is involved |
| 7 — mandatoriness | A CI gate, must hold | 4, 5: persuasion |
| 8 — destination | Generated project. This repository keeps the report-only `doctor` | — |
| 9 — integration | Gate every line that checks a committed file: `Boundaries`, `Schema`, `Hook jar`, `Hooks`, `MCP`, `Audit overrides`. Never `CLAUDE_PROJECT_DIR` and `Compose`, which are runner state. Never `Maven wrapper`: the `build` job already fails without a wrapper, and the line is wrong on Gradle (fixed separately) | Any form that fails on `Setup incomplete` as such |
| 9 — projects already generated | A `migrations` entry, so `/arch-adopt` prints the step change on update (`0104`'s mechanism). `export` never rewrites a project's `build.yml` | — |
| 16 — existing mode | `doctor` already computes every line; nothing reads its outcome | 7c as a *new* mode: the gate is a sub-form of `doctor`, as `compose gate` is of `compose` |
| 17 — CI coverage | Nothing tests `doctor`'s exit code today. `hooks-cross-platform` › `doctor` only runs it | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `doctor gate` sub-form, gating labels in `doctor.gate` of `extensions.json`, both CI templates call it | 9 | **Approved** |
| 2 | Template-only: `grep` `doctor`'s output for the ✅ lines in both CI templates | 6 | Rejected — the list and the label text get two owners, and the project file is never re-exported |
| 3 | `doctor` exits non-zero on `Setup incomplete` | 3 | Rejected — partial scope, red on every Gradle project and on this repo's own CI |
| 4 | Create nothing; fix the comment to say the step is diagnostic | 2 | Rejected — the observed failure stays |

### Option 1 — `doctor gate` (score 9)

**Motivator:** axes 7 and 16 — a gate is needed, and the check already exists in the mode.

**What it is:** `java .claude/hooks/ArchHook.java doctor gate` prints the same report as
`doctor`, then exits 1 when any line whose label is listed in `doctor.gate.labels` of
`extensions.json` printed `❌`. On stderr it names those labels, and each report line already
names its fix. A line `doctor` does not print (no `.mcp.json`, no audit directory) passes. The
bare `doctor` is unchanged: it exits 0, and this repository's `validate.yml` and `release.yml`
keep calling it. Both CI templates call `doctor gate`, and the step comment says what it gates.
A `migrations` entry carries the step change to projects already generated.

**Pros:** follows the `compose gate` precedent exactly: one report, two exits. The list of
gating lines is data, so an `/arch-adopt` update can change it without touching a project's
`build.yml`. The `0111` coverage claim becomes true. This repository's CI is unaffected.

**Cons:** a new argument and a new data block. Labels are matched by text, so renaming a
report label silently drops it from the gate unless a test catches it.

**Points cut in the rubric:** criterion 5 (an argument, a data block, a test, a migration
entry).

**CI:** new `.claude/.ci/DoctorGateTest.java`, a step in `validate` › `hooks-cross-platform`.
It builds a throwaway project with each gated line broken in turn (no `forbidden-imports.txt`,
an invalid `extensions.json` frontmatter, a stale jar hash, a bad `settings.json`, a bad
`.mcp.json`, a bad `audited.json`). Each case must exit 1 and name the label. A healthy project
must exit 0, with `CLAUDE_PROJECT_DIR` unset, no `mvnw` and a compose file whose services are
down. It also checks that every label in `doctor.gate.labels` appears in some fixture's output,
which catches the rename gap. The bare `doctor` must exit 0 on the broken fixture.

### Option 2 — template `grep` (score 6)

The precedent is `validate.yml:560-562`, which greps the exported tree's `doctor` output for
`Schema ............ ✅`.

**Cons:** the list of gating lines and their padded labels live in the project's `build.yml`,
which `export` never rewrites, while `doctor`'s text arrives with every `/arch-adopt` update.
The two drift apart with no owner. A grep for `✅` goes red on a rename, which is loud but false.
A grep for the absence of `❌` goes silently green, which is the issue again. The list sits
outside `extensions.json`.

**Points cut:** criterion 1 (the check reads files and belongs to the mode), 5 (two owners
of one list), 7 (a later change to the list needs a migration every time).

**CI:** a case in `TemplateCommentsTest` or a new template test in `templates.yml`. It cannot
prove the grep against a future `doctor`.

### Option 3 — `doctor` exits non-zero on `Setup incomplete` (score 3)

**Cons:** the verdict reads only `Boundaries` and `Maven wrapper`. It misses `Schema`, `Hook
jar`, `Hooks`, `MCP` and `Audit overrides`, which is the scope axis 9 chose. It turns every
Gradle project red (the `mvnw` blind spot). It turns this repository's `validate.yml:62,73` and
`release.yml:121` red unless they add `|| true`, which is a report mode pretending to be a
gate in two places.

**Points cut:** criteria 1, 4 (partial scope), 7, 9 (blocks a Gradle project on every push).

### Option 4 — create nothing (score 2)

**Cons:** the observed failure (fact 6) stays. The step only makes enforcement look
proven, and `0111`'s coverage claim stays false.

**Points cut:** criteria 1, 4, 7, 9 (a green check that proves nothing).

## References

| Claim | Source |
|---|---|
| A report mode with a gating sub-form that is silent while healthy and exits non-zero otherwise | `compose gate`, `ArchHook.java` § compose · `@CLAUDE.md` § Commands |
| A mode reads its lists from `extensions.json`, never from a constant | `@CLAUDE.md` invariant 10 |
| A guarantee only against an observed failure | `@CLAUDE.md` invariant 6 (mirror) · fact 6 above |
| `export` never rewrites a project's CI file; a convention change reaches existing projects as a `migrations` entry | `.claude/decisions/0104-use-case-subpackage-per-aggregate.md` · `extensions.json` › `migrations` |
| The generated CI may run only what exists inside the project | header of `project-bootstrap/templates/ci.yml.example` · `references/ci-coverage.md` |
| A new mode branch needs a test in both directions, and the jar is what the test runs | `references/ci-coverage.md` row 7c · `0084` |
| `0111` assumes this step fails on `❌` | `.claude/decisions/0111-audit-opt-out-owned-by-the-project.md:102,178` |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | `doctor(boolean gate)`; `report()` records failed labels in `REPORT_FAILED`; the gate exits 1 naming the gated labels that failed, and fails closed on an empty list; the top-level catch exits 1 for `doctor gate`, so a throw cannot pass the job. The Javadoc carries the "why this form" |
| `.claude/hooks/ArchHook.jar` | rebuilt under JDK 21 |
| `.claude/schemas/extensions.json` | `doctor.gate.labels` and `$comment_gate`; `migrations` entry `ci-boundaries-doctor-gate`, for every blueprint the earlier CI entries name |
| `.claude/skills/project-bootstrap/templates/ci.yml.example`, `ci-gradle.yml.example` | step `doctor gate`, with a comment saying what it gates and what never gates. Identical in both |
| `.claude/.ci/DoctorGateTest.java` | new |
| `.github/workflows/validate.yml` | step in `hooks-cross-platform` |
| `CLAUDE.md` | `doctor gate` row in § Commands |
| `docs/en/01-file-types.md`, `docs/pt-br/01-tipos-de-arquivo.md` | `doctor` row: `gate` and its blocking. `README.md:498` still says `doctor` never blocks: it is outside the `meta` territory, so `guard sweep` refused the edit. Left for a maintainer commit |
| `docs/en/07-ci-validate.md`, `docs/pt-br/07-ci-validate.md` | node H16, the step row, the test count fourteen → fifteen |
| `.claude/skills/claude-code-architect-designer/references/ci-coverage.md` | the generated `build` row and the axis-8 paragraph name `doctor gate` |

Goes to the generated project: **yes.** `export` copies `ArchHook.java`, the jar and
`extensions.json` whole. `project-bootstrap` writes the new step into new projects. Existing
projects see the `migrations` entry on their next `/arch-adopt` update. Not touched:
`validate.yml:62,73` and `release.yml:121` keep the bare `doctor` on purpose. `0111` stays as
written; its CI claim now holds through `doctor gate`.

Open, outside this record: `wrapper()` knows only `mvnw`, so a Gradle project's `doctor` reports
`Maven wrapper ❌`. To be filed as its own issue; the `tests` hook needs the same look.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · hooks-cross-platform › doctor gate fails on every gated line and only on those` | On a healthy throwaway project (`CLAUDE_PROJECT_DIR` unset; `Maven wrapper`, `Compose` and `git HEAD` red), the gate exits 0. Breaking each of the six gated lines exits 1 and names it. Every listed label is printed. The bare `doctor` exits 0 on `ENFORCEMENT OFF`. An empty list fails closed | Green: 9 cases. Red with the gate disabled (`if (true) return;`): 14 checks fail by name. Red with `Hook jar` renamed in `report(...)`: the rename check plus the `Hook jar` case fail, 3 by name. The jar was rebuilt from the restored source and is byte-identical |
| `validate · hooks-cross-platform › committed ArchHook.jar is what the source compiles to` | The jar carries the change | Green |
| `validate · design › frontmatter schema` | The new `migrations` entry is well-formed | Green; `MigrationsSchemaTest` green |
| Manual, recorded once | A tree written by `export --blueprint hexagonal`, `CLAUDE_PROJECT_DIR` unset: the gate fails on `Boundaries` alone (exit 1). With one rule in `forbidden-imports.txt` it exits 0 | Done before commit |
