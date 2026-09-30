# 0099 · CI tests for the templates a project receives verbatim

- **Date:** 2026-09-30
- **Scenario:** after `0096`–`0098`, the user asked whether CI could validate the change. Two
  findings of that run were only caught by hand — the default `format` of Checkstyle's
  `IllegalIdentifierName` (`0097`), and whether the new commons test templates compile
  (`0098`) — because a template never runs in this repository.
- **Decision:** N1 + N2, in a new path-filtered workflow `.github/workflows/templates.yml`:
  `.claude/.ci/CheckstyleConfigTest.java` and `.claude/.ci/JavaTemplatesTest.java`. N3
  rejected.
- **State:** approved by Lucas Fernandes, on 2026-09-30

## What already existed

`validate.yml`'s `exemplar-imports` job downloads a `start.spring.io` project and proves every
`import` of every `.java.example` exists in a JAR. It does not compile anything: a template
whose imports all resolve can still not compile, and a shipped test can still fail.
`docs/*/07-ci-validate.md` § What's not here yet recorded "the generated project actually
compiles" as a manual check.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Two defects in one run that no existing check could see: a config value (`format`) and template compilation | create nothing |
| 7 — mandatoriness | A CI check — fails the PR, does not block a session | Forms 7 and 8 (no hook: the check needs network and minutes) |
| — levels | N1 + N2 | N3 |
| — trigger | Path filter only | every PR |
| — where | PR #46 | a separate PR |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| N1 | Checkstyle configs run on the latest release over fixtures: clean passes, `record`/`permits` fail | 9 | **Approved** |
| N2 | Templates copied verbatim (`new-feature/templates/commons/`, `JpaEntity.java.example`'s blocks) compiled in a fresh Initializr project; the three shipped `*Test` templates run | 8 | **Approved** |
| N3 | Full bootstrap build: merge `pom.parent.xml.example` in code, `spotless:apply`, `verify` | 4 | Rejected — reimplements in Java the merge `project-bootstrap` does by instruction; two implementations of one procedure diverge |

**N1 — Motivator:** axis 1, the `format` defect. **Pros:** ~5 s locally, no Maven; the
latest release is what `project-bootstrap` resolves, so a Checkstyle release that changes a
default fails here before it fails a project. **Cons:** depends on GitHub Releases carrying
the `-all` jar. **Cut:** none material.

**N2 — Motivator:** axis 1, template compilation. **Scope, deliberately narrow:** only
templates written as files. The "Compilable as-is" header marker was considered as the
selector and rejected: `rest-api-architect` and `test-architect` templates carry it but
reference classes a use case creates (`CreateUserUseCase`, `Email`); compiling them would need
stubs that test the stubs. **Cons:** network (start.spring.io, Maven Central), minutes on a cold
cache. **Cut:** maintenance (−1, the in-scope list lives in the test).

## Verified before committing

| Check | Result |
|---|---|
| `CheckstyleConfigTest` on the current templates | 6 assertions pass, Checkstyle 14.3.0 |
| `CheckstyleConfigTest` with `checkstyle-test.xml`'s `format` removed | fails, 2 assertions — `record` and `permits` pass the default |
| `JavaTemplatesTest` on the current templates | 19 files compile, 3 test classes pass |
| `JavaTemplatesTest` with a type error injected in `AssignedIdEntity` | fails, the compiler line names `AssignedIdEntity.java` |

## Why path-filtered, and not a required check

Both jobs pay network and a Maven build; a PR that touches no file they read has nothing for
them to prove. A path-filtered workflow cannot be a required status check — GitHub leaves it
pending forever on the PRs it skips — so a failure here is visible on the PR, not
merge-blocking by configuration. The paths list is duplicated under `push` and `pull_request`
(no YAML anchors): keep both identical.

## References

| Claim | Source |
|---|---|
| Versions resolved at runtime, never from memory | `@CLAUDE.md` invariant 8 — Checkstyle from Maven Central, Boot from start.spring.io |
| Only `java`, `git`, `curl` | `@CLAUDE.md` § Dependencies — both tests are single-file Java, HTTP through `java.net.http` |
| CI tests live in `.claude/.ci/` as single-file programs | `ComposeTagTest.java`, `ComposeGateTest.java` |
| The imports-only precedent | `validate.yml` job `exemplar-imports` |

## Propagation

| File | Change |
|---|---|
| `.github/workflows/templates.yml` | **New** — two jobs, path-filtered |
| `.claude/.ci/CheckstyleConfigTest.java` | **New** — N1 |
| `.claude/.ci/JavaTemplatesTest.java` | **New** — N2 |
| `docs/pt-br/07-ci-validate.md`, `docs/en/07-ci-validate.md` | § The sibling workflow; § What's not here yet; § How to run locally |

Goes to the generated project: **no** — CI of this repository only.
