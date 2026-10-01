# 0108 · Javadoc is the only comment in generated code, enforced by Checkstyle on `src/main`

- **Date:** 2026-10-01
- **Scenario:** Issue #62, triaged at `a792341` — no mechanism keeps `//` and `/* */` comments
  out of generated code: the norm allows why-comments, 60 of 82 exemplars carry 717 own-line
  `//` lines, and Checkstyle checks only TODO format.
- **Decision:** Option A — `.claude/rules/code-quality.md` § Comments rewritten to "Javadoc is
  the only comment"; five comment modules in
  `project-bootstrap/templates/checkstyle.xml.example` at `error`, `src/main` only; every
  Java exemplar body cleaned; a `migrations` entry; fixtures in `CheckstyleConfigTest` and a
  new `TemplateCommentsTest` step in `validate` › `design`. No hook, no mode, no Java in
  `ArchHook.java`.
- **State:** approved by Lucas Fernandes, on 2026-10-01

## Reproduced on disk before classifying

Checked by `issue-verifier` against `a792341` (`/triage-issue 62`, verdict `confirmed`, layer
static). HEAD is still `a792341`. Only the confirmed rows below are inputs here. The issue's
proposed fix is not an input, and neither are its refuted or unproven rows (4 and 5 partly
true, 8 and 9 unproven).

| Claim | On disk |
|---|---|
| The norm allows why-comments; "Javadoc only" is written nowhere | `.claude/rules/code-quality.md:45` — "A comment explains **why**, never **what**" |
| The exemplars carry many `//` lines | 717 own-line `//` in 60 of 82 `*.java.example`. After the `package` line, outside the designer's own `hook-mode.java.example`: 548 own-line `//`, 21 trailing `//`, 1 `/* */`, in 57 templates |
| Runs of three or more `//` in nine templates | All nine confirmed; longest are `Controller` (21) and `ArchitectureTest` (16) |
| Checkstyle has no comment check except `TodoComment` | `project-bootstrap/templates/checkstyle.xml.example:130-134`; its XML comment promises "no commented-out code", the module checks only TODO format, at `warning` — which `violationSeverity=error` lets through |
| § How to verify lists no mechanical check for comments | `code-quality.md:49-63` |
| Empty test-double bodies need a comment (S1186/S108) | Already a norm: `.claude/rules/testing.md:97-98`, decision 0098 |
| `/arch-adopt` does not carry Checkstyle changes into existing projects | No "checkstyle" in `arch-adopt/SKILL.md` or `extensions.json` |
| Unchanged between v0.13.2 and v0.13.3 | No commit touches either file in that range |
| The executor copies the exemplar's shape | `.claude/agents/java-spring-boot-developer.md:81` — "shape exemplars" |

Checked here, not by the verifier: the `check` hook (PostToolUse, `Edit(**/*.java)`) runs
`./mvnw -q -o -pl <module> -am test-compile` (`ArchHook.java` `check`, step 2). `test-compile`
passes through `validate`, where the POM binds Checkstyle. A Checkstyle module at `error`
therefore blocks inside the session on the edit that wrote the comment, with no new hook and
no new registration.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Confirmed by triage (table above) | create nothing |
| 5 — nature | A fact about code: "Javadoc is the only comment", with two exceptions — a comment inside an otherwise empty body, and a tool directive (`NOSONAR`, `CHECKSTYLE:OFF`, `spotless:off`/`on`, `@formatter:off`/`on`). `// TODO(#N)` is no longer an exception: the TODO lives in the issue | new skill, new agent |
| 7 — mandatoriness | Checkstyle `error` in the generated project's build | prose-only option |
| 4 — territory | `src/main` only. `checkstyle-test.xml.example` stays as it is; test code follows the norm without a mechanical check | a module in the test config |
| 16 — existing mode | `check` already runs Checkstyle on every Java edit | new Form 7a entry, new Form 7c mode |
| 8 — destination | Both: the norm travels via `export.rules`, the Checkstyle config via `project-bootstrap`, the migration entry via `extensions.json` | — |
| — exemplars | Clean every template body; the leading `EXEMPLAR` block stays. An instruction to the model moves into that block, which the translation strips; a design why becomes Javadoc on the type or method (on an extracted method, where it was a run); a comment that only describes the code goes | cleaning only verbatim copies |
| — existing projects | A `migrations` entry: `/arch-adopt` prints the note and a prompt to add the module and convert the comments | release note only |
| — regression | A `validate.yml` › `design` step fails when a template body regains a comment | no check |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| A | Norm rewrite + Checkstyle comment modules at `error` on `src/main` + every template body cleaned + `migrations` entry + fixtures and a `design` step | 9 | **Approved** |
| B | Same norm and Checkstyle, cleaning only the templates copied as files (`new-feature/templates/commons/**`, `JpaEntity`, the idempotency aspect) and the nine with runs | 6 | Rejected — moves the cleanup cost into every generated project's every feature |
| C | Norm rewrite + every template cleaned, no Checkstyle module | 5 | Rejected — persuasion where the build gave a guarantee at no runtime cost |
| D | New `ArchHook.java` mode scanning comments on PostToolUse | 3 | Rejected — anti-pattern 17: `check` already runs Checkstyle |

### Option A — norm + Checkstyle + all templates (score 9)

**Motivator:** axes 1 and 7 — the symptom is confirmed and the user wants it to break the
build; axis 16 — `check` already carries Checkstyle into the session.

**Pros:** The guarantee is the build the project already runs: no new hook, no new mode,
no Java. The block message is Checkstyle's own `message` property, naming
`code-quality.md` § Comments and the way out. Clean exemplars remove the source of the
density, so the executor is not blocked on every copied comment. Existing projects hear
of it through the mechanism decision 0104 built for exactly this.

**Cons:** Large diff — 57 templates, ~570 lines to move, convert or drop, by hand: deciding
which comment becomes Javadoc is design, not transcription, so it is not delegated. The
empty-body exception needs a multi-line regex with a bounded look-behind, and a text block
holding a line that starts with `//` would be flagged (none in the templates today).
Invariant 2: `testing.md:97-98` *requires* the fixture comment; `code-quality.md` only
*permits* a comment in an empty body — the two must stay worded that way, permission in
one, obligation in the other, neither restating the other.

**Points cut in the rubric:** criterion 5 (maintenance) — a regex in XML is harder to read
than a module with a name.

**CI:** `templates` › `checkstyle-configs` › `CheckstyleConfigTest` gains fixtures — own-line
`//`, trailing `//`, own-line `/* */`, `/**` inside a body: rejected by `checkstyle.xml`; a
URL in a string, a comment in an empty body, `// NOSONAR`, `// spotless:off`: accepted. Plus
a new step in `validate` › `design` over every `*.java.example` outside
`claude-code-architect-designer/` (its template is `ArchHook.java`'s shape, not generated
code). `JavaTemplatesTest` already compiles the verbatim templates. `ci.yml.example`: nothing
— `./mvnw verify` already runs Checkstyle.

### Option B — Checkstyle + only verbatim and long-run templates (score 6)

Smaller diff, and the build stays green for the files copied as-is. But 40-odd shape
exemplars keep their comments: the executor copies them, `check` blocks, the executor
deletes them, on every feature. The cost moves from this repository's one-time cleanup
into every generated project's every run. Criterion 5 and 9 cut.

### Option C — norm + templates, no Checkstyle (score 5)

The cheapest piece, and it removes the cause. But the norm is persuasion; nothing catches
the comment the model writes on its own, and the user answered that the rule must break
the build. Criterion 4 (enforcement) cut twice: a guarantee was available at no runtime
cost.

### Option D — a new `ArchHook.java` mode (score 3)

Anti-pattern 17: `check` already runs Checkstyle on the same event. A mode would also only
run inside Claude Code; the build runs in CI and on every developer's machine. Criteria 1,
5, 6 and 9 cut.

## References

| Claim | Source |
|---|---|
| A guarantee belongs in a hook or the build, not prose | `@CLAUDE.md` invariant 6 |
| `rules/` cannot carry an instruction to template authors | `@CLAUDE.md` invariant 1; triage note on the issue's F1 |
| The empty-body comment is owned by `testing.md` | `@CLAUDE.md` invariant 2; `.claude/rules/testing.md:97-98`; decision 0098 |
| `check` runs `test-compile`, which passes `validate` | `.claude/hooks/ArchHook.java` `check` step 2; `code-quality.md` § How to verify (Checkstyle at `validate`) |
| Reusing an existing mode beats a new one | decision matrix § 2.2, anti-pattern 17 |
| Existing projects learn of a convention change through `migrations` | `.claude/schemas/extensions.json` `migrations`; decision 0104 |
| Checkstyle configs are proven against fixtures in `templates.yml` | decisions 0097, 0099; `.claude/.ci/CheckstyleConfigTest.java` |
| The `EXEMPLAR` block is scaffolding the translation strips | `.claude/agents/commons-logging-installer.md:113` |

## As built

Where the build departed from the option as proposed, and why:

- **`TodoComment` stays, at `error`, with `format` `\bTODO\b`.** The interview answer was to
  drop it because the regex already catches every `//`. A `TODO` inside a Javadoc is not a
  `//`, and the norm forbids a `TODO` in any form; the module is the only one that reads
  Javadoc text.
- **Five modules, not three.** `RegexpMultiline` × 2 (own-line `//` and `/* */`, raw file,
  Checker level, `fileExtensions` `java` so a migration's SQL `/* */` is never read),
  `TrailingComment` with `format` `^\s*$` (the default lets `} // end` through),
  `InvalidJavadocPosition`, `TodoComment`. Each carries a `message` naming
  `code-quality.md` § Comments and the way out.
- **The empty-body exception is in the regex:** a `//` line whose previous line ends in `{`
  and whose next line starts with `}`. The look-behind is bounded (`{0,40}`) because Java's
  engine refuses an unbounded one. `EmptyBlock` (`option: text`) already requires that text.
- **`// --- ` at column 0 is not a comment of the body.** It separates the files or
  sections of a multi-file exemplar and the translation never copies it; 125 such markers
  exist. `TemplateCommentsTest` skips them; a marker that ran on into a second `//` line
  was split into the marker and a Javadoc.
- **A block that closed a file** (a variant, a counter-example, `application.yml`, the
  wiring snippet, "collaborators assumed") is scaffolding: it moved into the leading header,
  which the translation strips. A `/*` block that preceded a declaration became `/**`.
- **`ApiExceptionHandler`'s no-observability variant** carried its constructor's
  explanation as `//` lines inside the header snippet the executor copies. The snippet now
  gives that text as the constructor's Javadoc.
- **Gradle.** The `check` hook runs `./mvnw` only, so in a Gradle project the comment check
  runs at `gradle check`/`build`, not on the edit. Maven projects get it on every Java edit.
- **Done in this thread, not delegated.** Mechanical moves (a closing block into the header,
  `/*` before a declaration into `/**`, a `//` run before a member into its Javadoc, an
  in-body run into its method's Javadoc) ran as throwaway scripts; every result was read,
  and the files where the move lost meaning were rewritten by hand.
- **Counted at the end:** 508 body comments across 60 templates (the triage's 717 counted
  header lines too, and the `// --- ` markers), all converted, moved or dropped; 56 files
  changed. A duplicate four-line paragraph in `Controller.java.example` went with them.

## Propagation

| File | Change |
|---|---|
| `.claude/rules/code-quality.md` | § Comments: Javadoc is the only comment, two exceptions, `TODO` forbidden in any form; § How to verify names the comment check and says test code is held by review |
| `.claude/skills/project-bootstrap/templates/checkstyle.xml.example` | The five comment modules; the misleading `TodoComment` comment and its `warning` severity gone |
| 56 `*.java.example` under `.claude/skills/` | Body comments converted to Javadoc, moved into the `EXEMPLAR` header, or dropped |
| `.claude/schemas/extensions.json` | `migrations` entry `javadoc-only-comments`, all seven blueprints; its prompt carries the five modules inline, since `project-bootstrap` is not exported |
| `.claude/.ci/CheckstyleConfigTest.java` | Six comment fixtures against `checkstyle.xml`, one against `checkstyle-test.xml` |
| `.claude/.ci/TemplateCommentsTest.java` | New: exemplar bodies against the patterns read from `checkstyle.xml.example` |
| `.github/workflows/validate.yml` | `design` › `exemplar bodies carry no comment but Javadoc` |
| `docs/pt-br/07-ci-validate.md`, `docs/en/07-ci-validate.md` | The new step (diagram node and row); the `checkstyle-configs` row lists the comment fixtures |

Goes to the generated project: **yes** — the norm via `export.rules`, the Checkstyle config
via `project-bootstrap` step 4.6 on new projects, the cleaned exemplars via the exported
development skills, and the `migrations` entry via `extensions.json` for `/arch-adopt` to
print on an update. `ci.yml.example` is unchanged: `./mvnw verify` already runs Checkstyle.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `templates` · `checkstyle-configs` › `CheckstyleConfigTest` | `checkstyle.xml` rejects own-line `//`, trailing `//`, `/* */`, `/** */` in a body and a Javadoc `TODO`, each by its module; passes both exceptions and `"http://…"`/`"/api/*"` strings; `checkstyle-test.xml` lets a comment through | Green on Checkstyle 14.3.0. Red with the empty-body look-arounds removed (`AllowedComments.java` fails); red with `LineComment` and `TrailingComment` removed (both fixtures fail by name) |
| `validate` · `design` › `exemplar bodies carry no comment but Javadoc` | No `*.java.example` body carries a comment the generated project's Checkstyle would reject | Green: 81 exemplars. Red with an own-line `//`, a trailing `//` and a `/* */` injected into `Aggregate.java.example`: three violations, each by file, line and form |
| `templates` · `java-templates` › `JavaTemplatesTest` | The verbatim-copied templates still compile and their shipped tests pass after the rewrite | Green: 19 files, 3 test classes |
| `validate` · `design` › `frontmatter schema` | The new `migrations` entry has every required field and names existing blueprints | Green; `MigrationsSchemaTest` green |

Checked by hand once, not in CI: the five real Checkstyle 14.3.0 modules over every
exemplar split on its `// --- ` markers (172 compilation units) — zero violations, so no
converted Javadoc sits in an invalid position. `TemplateCommentsTest` approximates
`TrailingComment` with a lexer and leaves `InvalidJavadocPosition` to the project's build;
this run is what covered the two.
