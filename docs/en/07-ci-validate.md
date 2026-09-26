# `validate.yml` — CI for the invariants

Primary source: `.github/workflows/validate.yml`, `.claude/hooks/ArchHook.java`
(`schema()` method), `CLAUDE.md` § Invariants.

## Why it exists

The invariants in `CLAUDE.md` and the norms under `rules/` are prose, not code —
nothing stops an edit from violating one silently. `ArchHook.java check` only guards
the Java boundary of a **generated** project; it never runs against this repository's
own prompt graph. `validate.yml` is what turns each invariant into a deterministic,
repo-wide check on every `push` and `pull_request`, instead of a claim that only holds
as long as whoever writes the next skill remembers it.

## Job diagram

```mermaid
flowchart TD
    T[push / pull_request / workflow_dispatch] --> J1
    T --> J2
    T --> J3

    subgraph J1["hooks-cross-platform (matrix: ubuntu · macos · windows)"]
        H1[ArchHook.java doctor]
        H2[BoundaryTest.java — forbidden import → exit 2]
        H3[InjectionPathTest.java — cwd-relative injection → exit 2]
        H4[ComposeTagTest.java — image tag disagreeing with src/test → reported]
    end

    subgraph J2["design (ubuntu-latest)"]
        D1[schema — frontmatter]
        D3[shadow of a native slash command]
        D4[blueprint doesn't touch prompts]
        D5[rules is a leaf]
        D6[norm has no code boilerplate]
        D7[no commands/]
        D8[.example suffix]
        D9[blueprint templates exist]
        D10[norm has a single owner]
        D11[no dependency outside the Java ecosystem]
        D12[decisions/ is isolated]
        D13[no hardcoded version]
        D14[paths matches a blueprint]
        D15[norm is in the derivation table]
        D16[every rule, skill and agent has a copy-list row]
    end

    subgraph J3["exemplar-imports (ubuntu-latest)"]
        E1[downloads a real starter.tgz from the Initializr]
        E2[every import in a .java.example resolves in the classpath]
        E3[no symbol denylisted as deprecated]
    end
```

## What each check covers

| Job / step | Verifies | Against what |
|---|---|---|
| `hooks-cross-platform` | `ArchHook.java doctor`, `BoundaryTest`, `InjectionPathTest` and `ComposeTagTest` on all three OSes | Decision D8 — "cross-platform" as a fact, not a claim |
| `hook reports a compose image tag that disagrees with src/test` | `ComposeTagTest.java` builds a throwaway project holding a `docker-compose.yml` and a `DockerImageName.parse(...)` under `src/test`, and requires `ArchHook.java compose` to report the divergence, expand `${VAR:-default}`, and stay quiet when the tags agree | The suite passing against an engine version nobody runs. It runs on all three OSes because question 3 of the `compose` mode compares two files and needs no Docker — which also proves the `src/test/` path match survives Windows' separator |
| `frontmatter schema` | `java .claude/hooks/ArchHook.java schema` | Invariant 10 — `extensions.json` is the single owner of recognized frontmatter; an invented field or `metadata:` fails loud instead of being silently ignored by the runtime. The same mode also requires every `` !`command` `` injection to resolve paths from `${CLAUDE_PROJECT_DIR}` — a relative one reports a file as absent whenever the shell's cwd has drifted |
| `skill name doesn't shadow a native slash command` | skill folder name against a denylist (`doctor`, `init`, `context`, `memory`, …) | A skill silently replacing a native command instead of erroring |
| `new blueprint doesn't touch prompts` | adding a blueprint leaves `.claude/skills` and `.claude/agents` untouched | Invariant 7 — architectures are data |
| `rules is a leaf of the graph` | no rule mentions "skill", "agent", "subagent" | Invariant 1 |
| `norm contains no code boilerplate` | no `class`/`record`/`interface`/`enum` declaration inside `rules/` | Invariant 3 |
| `no new commands/` | `.claude/commands/` doesn't exist | Invariant 4 |
| `exemplar imports have the .example suffix at the end` | every file under `skills/*/templates/` | Precondition globs that key off the suffix |
| `blueprint-declared templates exist on disk` | every `templates.<role>` in a blueprint resolves to a real file | A blueprint pointing at a renamed or deleted template |
| `each norm has a single owner` | a fixed list of known terms appears in at most one file under `rules/` | Invariant 2 — narrow by construction, see note below |
| `no dependencies outside the Java ecosystem` | no `pip install`, `npm install`, `node `, bare `python` in `.claude/` | Decision D7 |
| `every norm paths matches some blueprint` / `norm is in the bootstrap's derivation table` | a rule's `paths` glob names a package some blueprint declares, and step 6.6 of `project-bootstrap` knows to rewrite it | Gap 8 of `decisions/0024-lessons-learned-001-remediation.md` — a rule that silently never auto-loads |
| `every rule, skill and agent has a row in the bootstrap copy list` | every file in `rules/` has a row in § 6.6 of `project-bootstrap/SKILL.md`, and every skill and agent has a row marked ✅ or ❌ in §§ 6.7/6.8 | Invariant 9 — those tables **are** the copy lists that make the generated project self-contained. A new norm missing from § 6.6 breaks nothing at generation time: it breaks for whoever clones the project later and follows a citation to a file that was never copied |
| `decisions/ doesn't grow paths or enter 00-index.md` | no file in `decisions/` declares `paths:`; none is listed in `rules/00-index.md` | `decisions/` is history, not a rule — see Known pitfalls |
| `no hardcoded Spring/Java version outside decisions/` | no `Spring Boot X.Y` / `Java NN` written as fact in `rules/`, `skills/`, `blueprints/`, `CLAUDE.md` | Invariant 8 — versions are resolved via Spring Initializr, never written from memory. Excludes the `JDK 21+` minimum-requirement line and dated "Tested to compile" notes in exemplars, which record a past verification, not a version to use |
| `exemplar-imports` (whole job) | every `import` in a `.java.example` resolves against JARs from a real `start.spring.io` request, and none uses a name denylisted as deprecated | Gaps 4, 5, 9 of `decisions/0024-lessons-learned-001-remediation.md` — an exemplar that "compiles in the head of whoever wrote it" |

Note on "each norm has a single owner": the check tests a fixed list of literal
phrases (`Constructor injection`, `Zero framework`, `RuntimeException`), not a general
duplication detector. It catches regressions of terms already known to have drifted
once; a new rule added without a new phrase in that list isn't covered.

## Workflow posture

Four decisions that verify no invariant at all — they are the cost and the surface of
CI itself:

- **`permissions: contents: read`** at the top. No job writes: each one reads the
  checkout and runs `java`. Without it the `GITHUB_TOKEN` inherits the repository
  default, which is write.
- **`concurrency` with `cancel-in-progress`**. `push: [main]` and `pull_request` both
  fire on the same head when a PR branch is pushed; the superseded run is cancelled
  instead of racing the new one to the same conclusion.
- **`timeout-minutes` on every job** — 15 on the two hook/prompt jobs, 30 on
  `exemplar-imports`, which depends on the network. The default is 6h.
- **Actions pinned by commit SHA**, with the tag as a comment beside it. `@v4` is a
  moving ref the action's owner can repoint — a tag pin makes what CI ran
  unreproducible. Bump both together.

The `design` and `exemplar-imports` jobs declare `defaults.run.shell: bash` to get
`pipefail`, which the default shell (`bash -e {0}`) lacks. Per job, never at workflow
level: the `hooks-cross-platform` matrix has a `windows-latest` leg whose default shell
is pwsh.

## What's not here yet

- Invariant 9 (the generated project is self-contained) is now **half** covered: the
  `every rule, skill and agent has a row in the bootstrap copy list` step proves the
  tables of §§ 6.6/6.7/6.8 cover the disk — the part whose failure mode was silent.
  That the generated project *actually* compiles and holds no dead path stays a manual
  check, via the command block at `project-bootstrap/SKILL.md` § 8 ("Verify"): it
  requires generating a real project against the Initializr, and that cost hasn't been
  automated.
- `claude plugin validate .claude/skills` — the CLI isn't installed on the GitHub
  Actions runner, and installing it would pull in a dependency outside
  `java`/`git`/`curl` (see `CLAUDE.md` § Dependencies). Run it by hand before opening
  a PR; it's a cheap, complementary check to the `schema` step above, catches
  malformed YAML.

## Running it locally

```bash
# The same commands the `design` job runs, one by one:
java .claude/hooks/ArchHook.java schema
claude plugin validate .claude/skills   # not run in CI — CLI missing on the runner
java .claude/hooks/ArchHook.java doctor
java .claude/.ci/BoundaryTest.java
java .claude/.ci/InjectionPathTest.java
java .claude/.ci/ComposeTagTest.java
```

A `git push` without running this first still goes through the local hook
(`settings.json`), but only CI runs the `design` and `exemplar-imports` job steps —
those two have no local hook equivalent.
