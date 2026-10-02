# 0113 · The one-call rule for throw-assertion lambdas becomes a Checkstyle check

- **Date:** 2026-10-02
- **Scenario:** Issue #70, triaged at `331d71d`: the one-call rule for `assertThatThrownBy`
  lambdas (`testing.md:80-82`) exists only as a norm line, with no Checkstyle module, ArchHook
  mode or executor checklist line enforcing it, and no test template for a Kafka listener unit
  test or a use-case IT against a real database, so the executor writes the forbidden shape
  (`java:S5778`) in test shapes no template covers.
- **Decision:** Option 1 — Checkstyle `MatchXpath` `OneCallInThrowLambda` in
  `project-bootstrap/templates/checkstyle-test.xml.example`, the `testing.md` line widened to
  every throw-assertion form, and the `migrations` entry `one-call-throw-lambda`
- **State:** approved by Lucas Fernandes, on 2026-10-02

## Reproduced on disk

The `issue-verifier` table from `/triage-issue 70`, confirmed rows that bear on this scenario,
checked at `331d71d` (no commits since).

| # | Claim | Evidence |
|---|---|---|
| 1 | `testing.md` forbids more than one call in the `assertThatThrownBy` lambda | `.claude/rules/testing.md:80-82` |
| 2 | Nothing executable enforces it | `project-bootstrap/templates/checkstyle-test.xml.example:22-28` holds only `IllegalIdentifierName`; 0 matches in `ArchHook.java` |
| 3 | `messaging-architect/templates/` ships no test exemplar | only adapters, relay, yml, spec |
| 4 | The correct form appears in four templates, none a listener test or a use-case IT | `PersistenceIT.java.example:100`, `DomainTest:38`, `UseCaseTest:69`, `LoggingCommonsMethodsTest:71` |
| 11 | No `KafkaConsumerListenerTest.java.example` | absent |
| 12 | The executor never names `assertThatThrownBy` / `S5778` | `agents/java-spring-boot-developer.md` cites `testing.md` only |
| 13 | No `ArchHook` heuristic for it | 0 matches |
| 21 | v0.13.7 touched none of these files | not already fixed |

Not inputs: claims 19–20 (the reporter's issue counts and the "17 → 2 → 2" recurrence —
server data, unproven here) and the issue's proposed fix. The other confirmed rows (Sonar
token type, `component_tree` probe, `sonar.exclusions`, `Persistable` in `ArchitectureTest`)
are separate symptoms, out of this record.

Found while designing, before proposing: the triage left one question to a layer-2 run —
whether a Checkstyle `MatchXpath` can express the norm without false positives. Probed on
Checkstyle 14.3.0 (latest release, `-t` AST dump plus a scratch config): a lambda argument is a
direct `ELIST/LAMBDA` child of the assertion's `METHOD_CALL`; the query below flagged all seven
forbidden shapes (two-call expression lambda, `new` as an argument, a chained `orElseThrow()`,
a block lambda with two statements, a qualified `Assertions.assertThatThrownBy`, `isThrownBy`
after `assertThatExceptionOfType`, `catchThrowableOfType` with a nested call) and let through
`() -> new Money(-1)`, `() -> service.handle(command)`, a method reference, a single-statement
block lambda and an unrelated `forEach` lambda with two calls. The one hit on a fixture written
as "good" — `() -> service.handle(command, List.of())` — is a real violation of the norm.

```
//METHOD_CALL[(IDENT | DOT/IDENT)[@text = ('assertThatThrownBy', 'isThrownBy',
  'assertThatCode', 'catchThrowable', 'catchThrowableOfType', 'catchException',
  'assertThrows', 'assertThrowsExactly')]]/ELIST/LAMBDA[count(.//METHOD_CALL)
  + count(.//LITERAL_NEW) > 1]
```

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | The rule line `0098` added did not stop the shape in test shapes no template covers; nothing fails before a Sonar scan | create nothing |
| 7 — mandatoriness | Enforce at build. The maintainer accepts the post-`0098` recurrence as the observed failure `0098` lacked — that record cut enforcement because "the test smells are Sonar's to catch", and Sonar catches them only after a full scan, with the gate already red | persuasion only (templates, checklist line) |
| — scope | Every throw-assertion form, not only `assertThatThrownBy`: AssertJ `isThrownBy`, `assertThatCode`, `catchThrowable`, `catchThrowableOfType`, `catchException`, JUnit `assertThrows`, `assertThrowsExactly`. The norm line widens to name them, since it owns the rule | a check narrower than S5778 |
| — templates | None. The check fails every shape; a template per shape is maintenance with no added guarantee | the issue's listener and use-case IT templates |
| 8 — destination | Both. New projects through `project-bootstrap`'s template; existing ones through a `migrations` entry for all seven blueprints, since `arch-adopt` writes no build file (`0104`, `0109`) | new projects only |
| 16 — existing mode | No `ArchHook` mode; the project's build already runs a Checkstyle execution over `src/test` at `verify` (`0097`) | Form 7c |
| 17 — CI | `templates` › `checkstyle-configs` already runs `checkstyle-test.xml.example` on the latest Checkstyle; it gains fixtures. The migration entry's shape is covered by `schema` | a new workflow |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `MatchXpath` `OneCallInThrowLambda` in `checkstyle-test.xml.example`; `testing.md` line widened to every throw-assertion form; `code-quality.md` § How to verify names it; `migrations` entry `one-call-throw-lambda`; fixtures in `CheckstyleConfigTest` | 9 | **Approved** |
| 2 | New `ArchHook.java` mode on `PostToolUse` `Edit(**/src/test/**/*.java)` that parses the lambda | 4 | Rejected — re-implements Checkstyle in a hook, Claude sessions only, a JVM per test edit |
| 3 | Persuasion: `KafkaConsumerListenerTest` and use-case IT templates, plus an executor checklist line citing `testing.md:80` | 5 | Rejected — `0098` already tried persuasion for this rule; a template per shape chases the next one |
| 4 | create nothing — keep `0098`'s call, Sonar catches S5778 | 3 | Rejected — Sonar reports it only after a full scan, with the gate already red |

### Option 1 — Checkstyle module + norm + migration (score 9)

**Motivator:** axis 7 — a norm broken again after it was written is invariant 6's case, and
the build is where the project's other mechanical rules already live (`0097`, `0108`).

**Pros:** fails `./mvnw verify`, which the executor runs before reporting — the shape is
fixed in the same run instead of a Sonar round later. Covers every test shape, present and
future, with no template per shape. Maven and Gradle read the same file. Runs once per build,
not per edit. The probe above found no false positive against the norm.

**Cons:** the list of assertion names now lives in the norm and in the XPath — the same
pairing `code-quality.md` already declares for Checkstyle's numbers ("changing one side
without the other is a bug"), and the fixtures prove the pair agrees. The migration prompt
spells the module XML verbatim and will drift from the template by design (an entry is never
edited after it ships). Reverses `0098` option B's "cut: enforcement".

**Points cut in the rubric:** invariant 2 strain (−0.5, a name list in two files, held
together by the test); maintenance (−0.5, verbatim XML in the migration prompt).

**CI:** `templates` · `checkstyle-configs` › `checkstyle.xml and checkstyle-test.xml against
fixtures` — `CheckstyleConfigTest.java` gains a bad fixture (one violation per form, each
expected under id `OneCallInThrowLambda`) and a good fixture that must pass the test config.
Both paths are already in `templates.yml`'s filters. The migration entry: `validate` ·
`design` › `frontmatter schema` and `hooks-cross-platform` › `MigrationsSchemaTest`.

### Option 2 — `ArchHook` mode (score 4)

Runs only inside a Claude session, re-implements in the hook what Checkstyle does in the
build, and pays a JVM per test edit. A CI or a human editing the file never sees it.
**Cut:** form fit, maintenance, cost of always running, precedent (`0097` put build checks in
Checkstyle). Anti-pattern 17 in spirit: a tool already runs the check.

### Option 3 — templates + checklist line (score 5)

What the issue proposed as changes 2 and 3. `0098` already tried persuasion for this rule and
the shape came back in a shape no template showed; a template per shape chases the next one.
A checklist line restates `testing.md:80`, which already loads on every `src/test` file.
**Cut:** enforcement (−2), maintenance (−1), invariant 2 risk (−1).

### Option 4 — create nothing (score 3)

`0098`'s premise holds only after a full Sonar scan; the gate is red by then.
**Cut:** form fit, enforcement, symptom unaddressed.

## References

| Claim | Source |
|---|---|
| A rule broken again after it was written becomes enforcement | `@CLAUDE.md` invariant 6; decision matrix § 3, "A rule written in prose was broken anyway, a second time" |
| `0098` cut enforcement for this line because Sonar catches test smells | `0098` § Interview axis 7, § Option B |
| `checkstyle-test.xml` runs over `src/test` at `verify`, Maven and Gradle | `0097`; `project-bootstrap/templates/pom.parent.xml.example`, `build.gradle.parent.example` |
| Mechanical rules live in the project's build, the numbers paired with the norm | `rules/code-quality.md:64-73` |
| `arch-adopt` writes no build file; existing projects get build changes through `migrations` | `0104`, `0109` |
| A Checkstyle config never runs here; only a fixture test proves it | `0097`, `0099`, `.claude/.ci/CheckstyleConfigTest.java` |
| Lambda argument AST shape, query behaviour | probe on Checkstyle 14.3.0, § Reproduced on disk |

## Propagation

| File | Change |
|---|---|
| `skills/project-bootstrap/templates/checkstyle-test.xml.example` | `MatchXpath` module `OneCallInThrowLambda`; header names the test-only shape it holds |
| `rules/testing.md` § Names and shape | Line widened from `assertThatThrownBy` to the eight names; a constructor counts, a chain is two; names the check and pairs its list with the rule's |
| `rules/code-quality.md` § How to verify | `checkstyle-test.xml` runs at `verify` and holds the restricted identifiers and the one-call lambda |
| `schemas/extensions.json` | `migrations.entries[one-call-throw-lambda]`, all 7 blueprints, the module XML verbatim; stops when `checkstyle-test.xml` is missing (points at `spotless-check-and-test-checkstyle`) or already has the id |
| `.ci/CheckstyleConfigTest.java` | Ten bad fixtures and one good fixture, test config only; `expectFlagged` takes the rule text |
| `docs/pt-br/07-ci-validate.md`, `docs/en/07-ci-validate.md` | `checkstyle-configs` row: the new fixtures and decision 0113 |

Not changed, on purpose: no test template (interview), no executor checklist line (it would
restate `testing.md:80`), nothing in `build.gradle.parent.example` or `pom.parent.xml.example`
(both already point at `checkstyle-test.xml`).

The migration's query and message were compared to the template's after writing: equal.

Goes to the generated project: **yes** — new projects through `project-bootstrap` step 4.6;
the rules and `extensions.json` through `export`; existing projects through the `migrations`
entry, which `/arch-adopt` prints on their next update and never runs.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `templates` · `checkstyle-configs` › `checkstyle.xml and checkstyle-test.xml against fixtures` | The module parses on the latest Checkstyle, rejects each of the ten bad shapes by id `OneCallInThrowLambda`, and passes the one-call shapes | green on 14.3.0 · red with `+ count(.//LITERAL_NEW)` removed (`NewArgumentTest`, `CatchThrowableOfTypeTest` fail by name) · red with the threshold at `> 0` (`OneCallTest` fails; its `forEach` line stays unflagged) |
| `validate` · `design` › `frontmatter schema` | The migration entry's fields, id pattern and blueprint ids | green (`schema` exit 0) |
| `validate` · `hooks-cross-platform` › `schema blocks a migrations entry no project would see` | `MigrationsSchemaTest` against the shipped block | green |

Both paths the fixture test reads are already in `templates.yml`'s `push` and `pull_request`
filters.
