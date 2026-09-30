# 0100 · A generated project turns its SonarQube report into a lessons-learned and an issue on this repository

- **Date:** 2026-09-30
- **Scenario:** "quero criar uma skill que é executada a partir do projeto gerado que: 1. rodar sonar 2. extrai relatorio de bugs, issues, duplicacao, cobertura de codigo 3. baseado nas regras do projeto descritas no .claude, gera um arquivo lessons-learning como foco em melhoria de codigo para reduzir ou zerar issues do sonar 4. apos confirmacao no projeto gerado, deve ser criado no meta-repo via github uma issue do arquivos lessons-learned"
- **Decision:** option 1 — Form 2, `.claude/skills/sonar-lessons/SKILL.md`, in a new skill class `report`; the issue's shape owned by `.github/ISSUE_TEMPLATE/sonar-lessons.yml`, fetched by the skill
- **State:** approved by Lucas Fernandes, 2026-09-30 — with a new class `report` instead of `build`, and with the YAML issue form
- **Goes to the generated project:** yes — `export.skills.include`

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | `lessons-learned-017.md` ("first SonarQube analysis of the generated project") was produced by a prompt pasted by hand inside the generated project, then copied here. Repeats every feature round | Create nothing |
| 2 — Trigger | Manual `/name` only — it runs the full build and publishes to a public repository | Form 1 (a model-invoked or chained publish) |
| 5 — Nature | Sequence: scan → extract → trace → write → confirm → publish | Forms 4 · 5 |
| 7 — Mandatoriness | Not a guarantee — a report the user asks for | Forms 7 · 8 |
| 8 — Destination | Runs in the generated project; lives here and travels through `export.skills.include` | — |
| 9 — Integration | Writes `docs/lessons-learned/` (outside `.claude/`, which `arch-adopt` rewrites on update). Reads `.claude/rules/**` and the exported skills' `templates/`. Does not write the build file, compose, or `SONAR_TOKEN` — missing scanner → stop, point at `/sonarqube-setup`; container down → stop, point at `docker compose up`; token missing → stop, name the variable | Any option that chains `sonarqube-setup` or `docker-architect` |
| 11 — CLI | `curl` against the SonarQube Web API and `gh issue create` cover both external systems | Form 6 (SonarQube MCP server: tool names at every startup of every project session for a once-per-round skill, write tools needing a `permissions.deny`, Docker or an extra jar outside the declared java/git/curl) |
| 12 — Credential | `SONAR_TOKEN` (name only, must be able to Browse — an analysis-only token answers 401/403 and the skill stops saying so); `gh` auth for the issue | Any token written to disk |
| — Privacy | This repository is public; a generated project may be private. The issue carries only the trace to this repository — Sonar rule keys, counts, the norm or template at fault — never project paths, packages, or code. The local file stays complete | Publishing the file as is |
| — Model | `opus`: step 3 is the judgment that produced lessons-learned-017 | `sonnet`, and the `ops` class (sonnet-only; an override cannot change `allowed_models`) |
| — Meta-repo side | Label + dedupe before creating | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Form 2 skill `sonar-lessons`, class `report`, curl + gh, one confirmation before the issue | 9 | **Approved** |
| 2 | Option 1 plus a `sonar-report-collector` agent (sonnet, read-only Bash) that runs the scan and extraction and returns a compact summary | 7 | Rejected — fails the counter-test of invariant 5: facets and small pages keep the extraction short inline |
| 3 | Create nothing — keep pasting the prompt | 2 | Rejected — axis 1: the prompt is pasted every round, the Form 2 trigger of § 3 |

### Option 1 — `.claude/skills/sonar-lessons/SKILL.md` (score 9)

**Motivator:** axis 1 (same prompt pasted every round) + axis 2 (side effect → manual).

**Mechanism.**

1. **Entry rule.** No `pom.xml`/`build.gradle` at the root, or no scanner in it
   (`sonar-maven-plugin` / `org.sonarqube`) → stop, point at `/sonarqube-setup`. Read
   `sonar.host.url` and `sonar.projectKey` from the build file.
   `curl <host>/api/system/status` not `UP` → stop, point at `docker compose up -d sonarqube`
   or the external server. `SONAR_TOKEN` unset → stop, name it.
2. **Scan.** `./mvnw -B -q verify sonar:sonar` (Gradle: `./gradlew -q build sonar`) — `-q`
   instead of a redirect, which `guard bash` would refuse outside the territory. Wait on
   the compute engine task named in `target/sonar/report-task.txt` (`/api/ce/task`) before
   reading anything: the analysis is asynchronous and an early read returns the previous one.
3. **Extract**, per `references/sonar-web-api.md`: `/api/qualitygates/project_status`,
   `/api/measures/component` (bugs, vulnerabilities, code_smells, security_hotspots,
   coverage, new_coverage, duplicated_lines_density, ncloc), `/api/issues/search` with
   `facets=rules,impactSoftwareQualities,impactSeverities,scopes` for counts and a small `ps` per rule for samples,
   `/api/duplications/show` for each file carrying a duplicated block.
4. **Trace.** Group by Sonar rule. For each group, find the owner in this project's
   `.claude/`: the norm in `rules/` (via `00-index.md`), the exported template that emits
   the pattern, the Checkstyle config, or none ("the model decided alone"). Code the
   bootstrap generated has no template in the project (`project-bootstrap` is excluded
   from export) — the lesson names it as `project-bootstrap` plus the file's role, and the
   maintainer maps it. Propose the fix at the owner, not in the project.
5. **Write** `docs/lessons-learned/sonar-NNN.md` from `templates/lessons-learned.md.example`
   (snapshot, origin table, one § per rule group, the structural lesson).
6. **Confirm** (`AskUserQuestion`): fetch the issue form
   (`raw.githubusercontent.com/<slug>/HEAD/.github/ISSUE_TEMPLATE/sonar-lessons.yml`), write
   `docs/lessons-learned/sonar-NNN.issue.md` with one `### <label>` per field — the shape
   the web form produces — apply the privacy rule, list open duplicates
   (`gh issue list --label sonar-lessons --search <rule key>`), and ask. On yes:
   `gh issue create -R <slug> --title … --label sonar-lessons --body-file <that file>`,
   retried once without `--label` when the caller cannot label (no triage permission).
   Slug from `source.git_url`; version from the stamp `.claude/.arch-provenance.json`.

**Class — `report`, new.** Asked at approval whether `build` was the right home; it was not:
`build` exists to materialize files outside `docs/`, and requires only `## Procedure` where
a skill depending on two external systems owes `## Failure modes`. `ops` allows only
`sonnet` and writes no file, and an override replaces only `write_allow`, never
`allowed_models`. `report`: `blocked_during_design`, `allowed_models: [opus]`,
`write_allow: [docs/lessons-learned/**]` as the class default (no override — a second report
skill joins by name), required `## Procedure` and `## Failure modes`. Pure data: `ArchHook.java`
reads classes by key, no class name is in the source. Precedent for a one-member class:
`meta`, `ops`.

**allowed-tools.** `Read, Write, Glob, Grep, AskUserQuestion, Bash(./mvnw:*),
Bash(./gradlew:*), Bash(curl:*), Bash(java:*), Bash(gh auth status:*), Bash(gh issue list:*),
Bash(gh issue create:*)` — scoped, no unfiltered-Bash marker. `java` runs `ArchHook.java
doctor`, which names `.claude/` files edited since the export — an owner edited locally may
not carry the defect upstream.

**Meta-repo side — the YAML form, and why it is not a second owner.** The user asked for the
form. A form applies only in the web UI and `gh issue create --body-file` ignores it, so a
skill template for the body next to it would have been two owners of one shape
(invariant 2). Resolved by making the form the **only** owner: the skill has no
`issue-body` template, fetches the form at step 6, and fills its fields. Hand-filed and
skill-filed issues are the same shape because they read the same file; the privacy rule is
stated in the form's field descriptions, which is where the skill reads it. The fetch is the
one thing the project reads that it does not carry (invariant 9) — acceptable because the
target of that step is the same repository: without reaching it there is no issue to file,
and the lessons-learned file, the deliverable, is already written. Label `sonar-lessons`
must exist on the repository for the form's `labels` to apply.

**Pros:** one piece; nothing loaded until `/sonar-lessons` is typed; `curl`/`gh` already
dependencies; territory enforced by `guard`; the published body is a file on disk, reviewed
before the confirmation and kept after it.

**Cons:** the main thread reads Sonar JSON — mitigated by facets and small pages. One more
class in `skill_classes`.

**Points cut in the rubric:** none whole; criterion 5 (maintenance) shaved by
`references/sonar-web-api.md`, which must follow Web API changes, and by the class.

### Option 2 — Option 1 + `.claude/agents/sonar-report-collector.md` (score 7)

Invariant 5 reasons 1 and 3: the scan log and raw JSON stay in a `sonnet` subagent, the
skill gets a compact summary and does the tracing on `opus`. Cut: criterion 1 (the counter-test
of decision matrix § 5 — the output can be kept short by facets inline), criterion 5 (a
second piece, an `agent_classes` entry, an `installer`-like class that writes nothing),
criterion 7 (heavier propagation). Worth reopening if a real run shows the extraction
flooding the context.

## References

| Claim | Source |
|---|---|
| Side effect → `disable-model-invocation: true` | `.claude/skills/claude-code-architect-designer/references/decision-matrix.md` § 4 |
| CLI before MCP | same file § 2.1 · `@claude-help.md` § 9 |
| Agent only for three reasons, counter-test | same file § 5 · `@CLAUDE.md` invariant 5 |
| Override replaces only `write_allow`; `allowed_models` is per class | `.claude/hooks/ArchHook.java` (`schema`, skill class checks) · `.claude/schemas/extensions.json` `skill_classes` |
| Meta-repo slug available inside the project | `.claude/schemas/extensions.json` `source.git_url`, kept by the export |
| `build` class with per-skill territory and `opus` | `skill_classes.build`, precedent `sonarqube-setup` (decision 0091) |
| Shape of a Sonar lessons-learned | `.claude/lessons-learned/lessons-learned-017.md` |
| No token written anywhere | `@CLAUDE.md` invariant 11 · `sonarqube-setup/SKILL.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/sonar-lessons/SKILL.md` | New — Form 2, `model: opus`, scoped `allowed-tools`, `**Class:** report` |
| `.claude/skills/sonar-lessons/references/sonar-web-api.md` | New — every Web API call, the fields read, status codes, `/api/webservices/list` as the fallback when a parameter was renamed |
| `.claude/skills/sonar-lessons/templates/lessons-learned.md.example` | New — shape of `docs/lessons-learned/sonar-NNN.md`, taken from lessons-learned-017 |
| `.github/ISSUE_TEMPLATE/sonar-lessons.yml` | New — single owner of the issue's title prefix, label, sections and privacy rule |
| `.claude/schemas/extensions.json` | `skill_classes.report` (new class); `export.skills.include` + `sonar-lessons`; `docs/lessons-learned` in `doctor.uc_references.exempt_paths` and `doctor.bl_references.exempt_paths` — a lessons-learned cites use case folders as history, and a later rename must not report it |
| `CLAUDE.md` | Routing row |
| `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example` | Routing row in the generated project |
| `docs/pt-br/01-tipos-de-arquivo.md`, `docs/en/01-file-types.md` | Seven classes; `report` row |
| `docs/pt-br/09-diferenciais.md`, `docs/en/09-differentiators.md` | Row |
| `README.md` | Skill tree and the list of skills copied into the project |
| GitHub label `sonar-lessons` | Created on the repository with `gh label create`, outside git |
| `.claude/.ci/SkillTerritoryTest.java` | Six `report` cases: the phase opens on `/sonar-lessons`, `docs/lessons-learned/` allowed, `pom.xml` and `src/` refused, a `Skill(sonar-lessons)` call refused mid-design |
| `.claude/.ci/BashGuardTest.java` | Four cases in the report phase: `./mvnw -q verify sonar:sonar` and `gh issue create --body-file` allowed, the scan redirected to `target/` refused — the reason the skill uses `-q` |
| `.github/workflows/validate.yml` | Step `issue forms cited by skills exist` |
| `docs/pt-br/07-ci-validate.md`, `docs/en/07-ci-validate.md` | Case counts (22 → 28, 16 → 20) and the new step |

## Review of CI and `doctor` after approval

Asked after the first write: what the pipeline and `/arch-doctor` should add for this skill.
Applied only what was recommended:

- **The skill no longer names its label.** It read `--label sonar-lessons` in two commands
  while the form declared `labels:` — a rename there would have left every project filing
  under the old name. Title prefix and label now come from the fetched form, like the sections.
- **`## Issue` outcome line** at the end of `sonar-NNN.md`, written on every exit from step 6.
- **Progress against the previous run** — *Previous* and *Δ* columns in the snapshot, each
  group marked *new* or *recurring*, a *Gone since* list. The stated goal is fewer issues each
  round; nothing measured it.
- **Guard tests pin the `report` class and the `-q` choice.** If `guard bash` ever read
  `./mvnw` as a writer of `target/`, the skill would stop in every generated project with
  nothing in review to show it.
- **CI step for the fetched form.** Exported projects fetch it from HEAD; renaming it here
  breaks all of them while the updated skill in this repository keeps working.

Not applied, recorded so they are not re-proposed without new evidence:

- **A `Sonar` line in `doctor`** (scanner declared, host, `SONAR_TOKEN` set). Step 1 of the
  skill already checks all three before the build; the line would be a second owner of the
  same readiness check, plus Java, data and a jar rebuild. Reopen if `/arch-doctor` is wanted
  as the Sonar diagnosis independent of the skill.
- **A weekly contract test of `references/sonar-web-api.md` against a real SonarQube
  container.** No observed drift yet (invariant 6, mirror side); the reference already sends
  the model to `/api/webservices/list` when a parameter is refused.
- **Per-class hint in the guard's block message.** The text ("Put the content in the spec as
  a code block…") is design-phase advice printed for every class. Predates this skill and
  affects `build` and `meta` too — a separate decision, since it changes `ArchHook.java`.
