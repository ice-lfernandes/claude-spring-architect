# 0109 · Projects generated before 0097 get a migration for its build checks

- **Date:** 2026-10-01
- **Scenario:** Issue #64, triaged at `20f079a` — `arch-adopt` ships no migration for decision
  0097's build change, so an adopted project's `./mvnw verify` skips `spotless:check` while
  `rules/code-quality.md` says it runs.
- **Decision:** Option 1 — one `migrations` entry, `spotless-check-and-test-checkstyle`, in
  `.claude/schemas/extensions.json`, for all seven blueprints
- **State:** approved by Lucas Fernandes, on 2026-10-01

## Reproduced on disk

The `issue-verifier` table from `/triage-issue 64`, confirmed rows only, checked at `20f079a`
(= `v0.13.4`; no commits since).

| # | Claim | Evidence |
|---|---|---|
| 1 | `code-quality.md` promises `spotless:check` at `verify` | `.claude/rules/code-quality.md:74` |
| 2 | `pom.parent.xml.example` binds `spotless-check` at `verify` (0097) | `project-bootstrap/templates/pom.parent.xml.example:103-116` |
| 3 | `migrations.entries` holds exactly three ids | `use-case-subpackage-per-aggregate`, `kafka-string-wire-contract`, `javadoc-only-comments` |
| 4 | None of them covers 0097's build change or 0107 | No entry mentions Spotless, `spotless-check` or `checkstyle-test` |
| 5 | A pre-0097 Maven build declares Spotless with no `<execution>`, so `verify` skips the check | 0097 § Reproduced on disk, first row; 0097 § Propagation, last paragraph — `arch-adopt` writes no build file |
| 6 | #60's claim 5 was this same gap; 0107 deferred it, and nothing open tracks it | #60 triage comment; `0107-moduleof-root-pom.md:42` |
| 7 | Files written while `format` was a no-op stay unformatted until edited again | `project-bootstrap/templates/settings.json.example`: `format` is `PostToolUse` on `Edit(**/*.java)`; nothing runs `spotless:apply` on an update |
| 8 | 0107 shipped in `v0.13.3` | first tag containing it |
| 9 | The executor reports `./mvnw verify` green while CI fails | `.claude/agents/java-spring-boot-developer.md:674` |
| 11 | 0107 decided against a `doctor` line | `0107-moduleof-root-pom.md:45` |

Not an input: claim 10 (the reporter's CI failure count — unproven here) and the issue's
proposed fix.

Found while designing, not in the issue: 0097 changed more than Spotless. The same release
added the `checkstyle-test` execution, `config/checkstyle/checkstyle-test.xml` and
`IllegalIdentifierName` in `checkstyle.xml` — the enforcement of `rules/naming.md`
§ Restricted identifiers. That rule travels with `export`; its check does not. On Gradle,
`spotlessCheck` was already in `check`, but `checkstyleTest` ran the full production config
over `src/test` until 0097 pointed it at `checkstyle-test.xml`.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 2 — trigger | `/arch-adopt` on an update, step 9 | every form but data in an existing mechanism |
| 5 — nature | A data entry in `migrations.entries`, owned by `extensions.json` | Forms 1–5 |
| 7 — mandatoriness | Shown, never run — 0104 axis 8 fixed it: "never run automatically, the user decides" | Forms 7, 8; any automatic rewrite of the build file |
| 8 — destination | Travels: the `migrations` block is a sibling of `export` inside `extensions.json` | — |
| — scope | The whole 0097 build change, Maven and Gradle, not only `spotless-check` | a Spotless-only entry |
| — shape | Build snippets verbatim, like `javadoc-only-comments`: `project-bootstrap`'s templates do not travel, so the project has nowhere to read them | a prose description |
| — 0107 | No entry of its own; the prompt runs `spotless:apply` before `verify`, which reformats what the no-op hook left behind | a second id for a hook fix |
| 17 — CI | Nothing new; `schema` and `MigrationsSchemaTest` already prove the entry's shape | a test parsing prompt XML |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | One `migrations` entry, `spotless-check-and-test-checkstyle`, all 7 blueprints, the whole 0097 build change verbatim, Maven and Gradle, `spotless:apply` before `verify` | 9 | **Approved** |
| 2 | One entry with only the Maven `spotless-check` execution | 6 | Rejected — leaves the restricted-identifiers check behind, and a second entry for the same decision later |
| 3 | create nothing — 0097 accepted that generated projects are not reached | 3 | Rejected — 0104 made a migration the answer for every convention change |
| 4 | `arch-adopt` edits the build file itself | 2 | Rejected — contradicts 0104 axis 8, outside `arch-adopt`'s territory |

### Option 1 — `migrations.entries[spotless-check-and-test-checkstyle]` (score 9)

**Motivator:** axis 7 via 0104 — `arch-adopt` hands over a migration for every convention
change, and 0097 predates that mechanism by one day.

**Pros:** closes #64 and #60's claim 5 together; the restricted-identifiers rule the project
already reads becomes checked; one id, so a project sees the whole 0097 change once; data
only — no skill, hook or test changes; the prompt is conditional, so a project generated
between `v0.10.0` and `v0.13.4` (which already has the executions) is told to stop.

**Cons:** a long prompt — the XML and Groovy have to be spelled out. Projects generated from
`v0.10.0` onward see an entry that does not apply to them; `blueprints` cannot filter by
generation date.

**Points cut in the rubric:** maintenance (−0.5, a long verbatim prompt that will drift from
the template — by design, an entry is never edited after it ships); context cost (−0.5, shown
to projects that already have it).

**CI:** `validate` · `design` › `frontmatter schema` checks `id`, required fields and that all
7 blueprints exist; `hooks-cross-platform` › `MigrationsSchemaTest` proves `schema` blocks a
malformed entry. Nothing new.

### Option 2 — Spotless-only entry (score 6)

Fixes the symptom the issue names. Leaves `IllegalIdentifierName`, `checkstyle-test` and
Gradle's `checkstyleTest` behind, so a later entry for the same release shows the same
project a second migration. **Cut:** propagation (the rule travels unchecked), maintenance (a
second entry for one decision).

### Option 3 — create nothing (score 3)

0097's "−0.5 propagation" was written before `migrations` existed; 0104 made a migration the
fixed answer for every convention change. **Cut:** form fit, enforcement, propagation.

### Option 4 — `arch-adopt` writes the build file (score 2)

Contradicts 0104 axis 8 ("any automatic rewrite of project code" — eliminated), and the build
file is outside `arch-adopt`'s `build` territory except for `sonarqube-setup`'s lines.
**Cut:** invariants (0104), trust surface, form fit.

## References

| Claim | Source |
|---|---|
| A migration for every convention change, never run automatically | `0104-use-case-subpackage-per-aggregate.md:40` |
| Entries are convention changes, never edited after shipping | `extensions.json` › `migrations.$comment` |
| A migration prompt may edit a build/config file verbatim | `extensions.json` › `javadoc-only-comments` |
| `arch-adopt` shows only ids absent from the project's copy, filtered by blueprint | `.claude/skills/arch-adopt/SKILL.md` step 5, step 9 |
| What 0097 changed in the build | `0097-lessons-learned-017-offline-build-loop.md` § Propagation |
| `project-bootstrap`'s templates do not travel | `extensions.json` › `export.skills.exclude` |
| Build tool is not a blueprint property | `.claude/blueprints/_schema.md` — `build.tool` overridable at initialization |

## Propagation

| File | Change |
|---|---|
| `.claude/schemas/extensions.json` | `migrations.entries` + `spotless-check-and-test-checkstyle` |

Nothing else: `arch-adopt` step 9 reads the block as data, and no doc lists the entry ids.

Goes to the generated project: **yes** — the `migrations` block travels inside
`extensions.json`. A project updated through `/arch-adopt` sees the entry once; a project
generated after this commit carries the id and never sees it.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate` · `design` › `frontmatter schema` | The new entry has every required field, an id in pattern, unique, and names existing blueprints | Green on the tree. Red with `onion` misspelled `onoin` in this entry: exit 2, `migrations.entries[3] \`spotless-check-and-test-checkstyle\` — blueprint \`onoin\` does not exist` |
| `validate` · `hooks-cross-platform` › `MigrationsSchemaTest` | `schema` blocks a malformed entry and accepts the shipped block | Green, 6/6 |

Nothing new was added, by the interview's choice: an entry is never edited after it ships, so
its verbatim snippets drifting from `project-bootstrap`'s templates later is the rule, not a
defect. Checked by hand once, not in CI: the four XML fragments of the prompt parse, and the
`IllegalIdentifierName` format is byte for byte the one in `checkstyle.xml.example`.
