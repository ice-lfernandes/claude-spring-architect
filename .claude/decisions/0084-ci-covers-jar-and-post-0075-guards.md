# 0084 · CI exercises the jar that hooks run, and every guard mode 0063–0077 added

- **Date:** 2026-09-29
- **Scenario:** "Bloco A" of the PR #39 staleness review — `.github/workflows/validate.yml` predates decisions 0075–0083: it tests the source while hooks run the jar, never runs `guard bash`, `guard sweep`, `compose gate` or `context subagent`, keeps the native-command list hardcoded in YAML, and still calls `python3`
- **Decision:** option 1 — data + `schema` check + four jar-driven tests + workflow and export-job fixes
- **State:** approved by Lucas Fernandes, 2026-09-29
- **Goes to the generated project:** yes — `extensions.json` and the rebuilt `ArchHook.jar` travel whole via `export`; the workflow and `.claude/.ci/` do not

## Interview

Axes 1, 5, 7, 8, 9 answered from the workflow, the hook and `extensions.json`; four
scoping questions answered by the user (items 4–7 below).

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Observed, in the file. (a) Every `.claude/.ci/*Test.java` spawns `java ArchHook.java`, while every registration runs `java -jar ArchHook.jar` (0075) — the tests prove the source, CI's `build --verify` proves source == jar, but nothing proves the thing hooks launch *behaves*. (b) `guard bash` (0063/0076), `guard sweep` (0065), `compose gate` (0064), `context subagent` (0077) have zero CI coverage. (c) The export job requires no `ArchHook.jar` in the tree it exports, and runs the exported project's `schema` with the **source** hook of this repo. (d) `export.retired` (0082) deletes files in a target and nothing tests it. (e) `python3` at the id read contradicts `@CLAUDE.md` § Dependencies. (f) The NATIVE shadow list lives only in YAML and is stale (no `review`, `status`, `export`, `skills`, …); a generated project never runs it. (g) The rule-`paths` step allows `'**/*.java'`, the glob 0082 retired | "create nothing" |
| 4 — Territory (user) | A rule with no `paths` loads at launch (0082); `schema` skips it (`checkRuleTerritories` `continue`). Fix as data: `types.rule.required` gains `paths` | A YAML-only check |
| 5 — Nature | Mechanical checks over files and process exits | Forms 1–5 |
| 6 — Native commands (user) | "Incluir: dado + schema" — the list becomes data in `extensions.json`, `schema` fails a skill folder named after one, the workflow step goes | YAML list (invariant 10) |
| 7 — Mandatoriness | CI is the guarantee for the hook itself; the native check is Form 7c-like (a new `schema` check) → record mandatory | — |
| 7b — Test target (user) | "Contra o jar" — tests spawn `java -jar .claude/hooks/ArchHook.jar`, placed after `build --verify` in the same job | Testing the source |
| 5b — Tests (user) | `BashGuardTest`, `SubagentContextTest`, `SweepTest`, `ComposeGateTest` | — |
| 8 — Destination | Both: `extensions.json` + jar travel; the generated project's `schema` gains the native and `paths` checks for free | — |
| 9 — Integration | `schema` already owns frontmatter and skill-folder validation; `checkRuleTerritories` owns rule globs. No new mode, no new registration | Form 7a, new mode |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Full package: data (`rule.required` + `native_commands`), one `schema` check, four new tests, all tests against the jar, export-job fixes | 8 | approved |
| 2 | CI-only: same tests and export fixes, NATIVE refreshed but kept in YAML, `paths` checked by a workflow step | 5 | not chosen |
| 3 | Export-job fixes only (jar required + `cmp`, exported jar runs its own `schema`/`doctor`, retired test, no `python3`) | 5 | not chosen |
| 4 | Create nothing | 2 | not chosen |

### Option 1 — data + `schema` check + `.claude/.ci/` + workflow (score 8)

**Motivator:** axes 1 (seven observed gaps), 6 and 7b (user), 8 (both).

**Mechanism.**

1. `.claude/schemas/extensions.json`
   - `types.rule.required`: `["status", "paths"]`.
   - New `types.skill.native_commands`: the built-in slash commands a skill folder must not
     shadow — the current YAML list refreshed (adds `help`, `review`, `security-review`,
     `status`, `export`, `plugin`, `skills`, `upgrade`, `release-notes`, `pr-comments`,
     `fast`, `effort`, `feedback`, `exit`, `sandbox`, `ide`, `install-github-app`,
     `privacy-settings`).
2. `.claude/hooks/ArchHook.java` — `schema` fails, by name, a `.claude/skills/<dir>/` whose
   `<dir>` is in `native_commands`, pointing at `@docs/pt-br/11-pitfalls.md`. List read from
   data (invariant 10). Rebuild with `ArchHook.java build`.
3. `.claude/.ci/` — five existing tests switch to `-jar .claude/hooks/ArchHook.jar`; four new:
   - `BashGuardTest` — `guard bash` exits 2 on `git push -f`, `-uf`, `--force-with-lease`,
     `origin +main`, `git -C . push --force`, `sh -c "git push --force"`; exits 2 on
     `echo x > src/X.java` and `sed -i … src/X.java` under an open design phase; exits 0 on
     `git push origin main`, `ls > /dev/null`, a `$VAR` target.
   - `SubagentContextTest` — `context subagent` with `java-spring-boot-developer` prints
     `additionalContext` of length ≤ `subagent_context.max_chars`; with
     `archunit-installer` and an unknown type prints nothing.
   - `SweepTest` — temp git repo with `.claude/schemas/extensions.json` copied in: dirty file
     A, `guard prompt` opening a design skill, write B outside its territory, `guard sweep`
     exits 2 naming B and not A; `stop_hook_active: true` exits 0.
   - `ComposeGateTest` — temp dir: kafka publishing `9092:9092` with
     `KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092` → `compose gate` exit 2 (the check
     is file-only, no Docker needed); host-advertised variant and no compose file → exit 0.
4. `.github/workflows/validate.yml`
   - hooks-cross-platform: `build --verify` stays before every test step; add the four.
   - design: NATIVE step removed (now `schema`); `'**/*.java'` dropped from the `paths`
     allowlist.
   - export-determinism: require `.claude/hooks/ArchHook.jar` and `cmp` it with the source
     jar; run the exported tree's own `java -jar $OUT/.claude/hooks/ArchHook.jar schema` and
     `doctor`; new step seeds `$OUT/.claude/rules/security.md`, exports, asserts it is gone;
     `python3` id read replaced with `grep`/`sed`.
   - Header comment: claims list updated (jar reproducibility, guard coverage).

**Pros:** closes all seven gaps; the two lists become data both the meta-repo and every
generated project check (invariants 10, 9); tests exercise the artifact the runtime runs;
no new hook mode, no new registration, no JVM cost at runtime.

**Cons:** four more JVM spawns per matrix leg (~seconds × 3 OS); a `schema` change means a
jar rebuild in the same commit; a Windows leg must handle `git` in a temp dir for
`SweepTest` (already on the runner).

**Points cut in the rubric:** criterion 9 (cost) — CI minutes; criterion on reach — the
native check only fires at `schema` time, not when the folder is created.

### Option 2 — CI-only (score 5)

Same coverage, but the native list and the `paths` requirement stay as YAML. Violates
invariant 10 (a list read by a check, owned outside `extensions.json`), and a generated
project — which has no copy of this workflow — never checks either.

### Option 3 — export-job fixes only (score 5)

Cheap and closes (c), (d), (e). Leaves the four guard modes untested and the tests
exercising the source rather than the jar — gaps (a), (b), the two with the widest blast
radius, since a regression in `guard bash` blocks every shell command.

### Option 4 — create nothing (score 2)

Every gap was observed in the file; `python3` alone breaks the stated dependency contract.

## References

| Claim | Source |
|---|---|
| Hooks run the jar; CI verifies jar == source | `.claude/decisions/0075-precompiled-hook-jar.md`, `validate.yml` step `build --verify` |
| `guard bash` force-push and write shapes are data | `.claude/decisions/0076-bash-scope-and-force-push-guard.md`, `extensions.json` `guard.force_push`, `guard.bash_write_shapes` |
| `guard sweep` diffs against the `guard prompt` baseline; git-shaped | `.claude/decisions/0065-guard-sweep-on-stop.md`, `ArchHook.java` `guardBaseline`/`guardSweep` |
| `compose gate` file-only half runs without Docker | `.claude/decisions/0064-compose-gate-on-stop.md`, `ArchHook.java` `composeReport` |
| `context subagent` output bounded by `max_chars` | `.claude/decisions/0077-pattern-catalog-injected-at-subagent-start.md` |
| Rule without `paths` loads at launch; `**/*.java` retired | `.claude/decisions/0082-rules-without-paths-load-at-launch.md` |
| A list a mode reads lives in `extensions.json` | `@CLAUDE.md` invariant 10 |
| No Python dependency | `@CLAUDE.md` § Dependencies |
| Skill named after a native command is silently shadowed | `@docs/pt-br/11-pitfalls.md` |

## Found while writing — the jar was never committed

PR #39's CI failed on every leg of `hooks-cross-platform`, in `design` and in
`export-determinism` ("ArchHook.jar is not what … compiles to", "export.binary_copy names
`.claude/hooks/ArchHook.jar` — no such file", "ArchHook.jar is missing"). The root cause
was not in the workflow: `.gitignore` carries `*.jar`, so 0075's jar existed on the
author's disk and nowhere else. Fixed with a negation line, `!.claude/hooks/ArchHook.jar`,
right under `*.jar`; `.gitattributes` already marks `*.jar binary`.

Not fixed, noted for `arch-adopt`: an adopted project whose own `.gitignore` ignores
`*.jar` drops the exported jar on its first `git add -A`, and every hook in it then fails
to launch. The Initializr's `.gitignore` does not ignore `*.jar`, so a generated project is
not exposed.

## Propagation

| File | Change |
|---|---|
| `.claude/schemas/extensions.json` | `types.rule.required` = `["status","paths"]`; new `types.skill.native_commands` |
| `.claude/hooks/ArchHook.java` + `ArchHook.jar` | `checkNativeShadow`, called from `checkSkillBody`; jar rebuilt |
| `.gitignore` | `!.claude/hooks/ArchHook.jar` |
| `.claude/.ci/{Boundary,InjectionPath,ComposeTag,SkillTerritory,AgentTerritory}Test.java` | spawn `java -jar .claude/hooks/ArchHook.jar` |
| `.claude/.ci/{BashGuard,Sweep,ComposeGate,SubagentContext}Test.java` | new |
| `.github/workflows/validate.yml` | header claims; four new steps after `build --verify`; NATIVE step removed; `'**/*.java'` dropped from the `paths` allowlist; export job requires and `cmp`s the jar, runs the exported jar's `schema`/`doctor`, reads the stamp with `sed`, new `export.retired` step reading the list from `extensions.json` |
| `CLAUDE.md` | `schema` Commands row names the two new checks; jar pitfall says it is committed and un-ignored |
| `docs/{en,pt-br}/07-ci-validate.md` | diagram (build --verify, four tests, export job) and table rows |
| `docs/{en,pt-br}/11-pitfalls.md` | native-command pitfall points at `schema` and `native_commands` |
