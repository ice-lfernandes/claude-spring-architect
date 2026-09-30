---
name: sonar-lessons
description: >
  Runs this project's SonarQube analysis, extracts the quality gate, bugs,
  vulnerabilities, code smells, duplication and coverage through the SonarQube Web API,
  traces every group of findings to the norm, template or Checkstyle setting in `.claude/`
  that produced it, and writes `docs/lessons-learned/sonar-NNN.md` focused on fixing the
  cause so the next analysis has fewer issues or none. After an explicit confirmation it
  opens a sanitized issue on the claude-spring-architect repository, where the fix belongs.
  Explicit invocation only.
disable-model-invocation: true
allowed-tools: Read, Write, Glob, Grep, AskUserQuestion, Bash(./mvnw:*), Bash(./gradlew:*), Bash(curl:*), Bash(java:*), Bash(gh auth status:*), Bash(gh issue list:*), Bash(gh issue create:*)
model: opus
effort: high
---

# Sonar Lessons

Turns one SonarQube analysis into a lesson the **meta-repository** can act on. A Sonar
issue in a generated project is almost never a one-off: the code came from a template, a
norm allowed it, or nothing in `.claude/` said anything and the model decided alone. Fixing
it here fixes one project; fixing the owner fixes every project generated afterwards. This
skill finds the owner, writes it down, and — only when told to — hands it to the
repository that owns it.

**Entry rule: this skill runs the analysis, it never sets it up.** No scanner in the root
build file, no server answering, no token — stop and say which one, and what fixes it. It
writes nothing outside `docs/lessons-learned/`: not the build file, not the compose file,
not the token.

**Privacy rule: the issue carries the trace, never the project.** The claude-spring-architect
repository is public and this project may not be. The local lessons-learned file is complete;
the issue body carries Sonar rule keys, counts, and paths **inside `.claude/`** only — never a
path under `src/`, a package, a class name of this project, a code excerpt, the project key,
or the server URL. The issue form's field descriptions state the same rule per field.

## Why this is a skill (Form 2) and not an agent

Form 2 because it has two side effects the model must not trigger on its own — a full
`verify` plus analysis, and a public issue — which is axis 2 of the design interview: the
user types `/sonar-lessons`, nothing chains it. The symptom behind it (axis 1) was the same
long prompt pasted into every feature round to produce a Sonar lessons-learned by hand. The
closest rejected form was the same skill plus a `sonnet` collector agent holding the build
log and raw JSON; it failed the counter-test of invariant 5, since `facets` and small pages
already keep the extraction short. Record: `@.claude/decisions/0100-sonar-lessons-skill.md`.

Runs on `opus`: step 4 — deciding which norm or template owns a finding, and what the fix
at the owner is — is the judgment the hand-written lessons-learned needed.

## Procedure

Endpoints, parameters and response fields of every call below:
`references/sonar-web-api.md`. Every `curl` authenticates with `-u "$SONAR_TOKEN:"` and
never prints the variable.

### 1 · Preconditions — stop on the first that fails

1. Root build file: `pom.xml` → maven, `build.gradle` → gradle. Neither → stop: nothing to
   analyze (this is also what happens when the skill is typed in the claude-spring-architect
   repository itself).
2. Scanner declared — `sonar-maven-plugin` / `org.sonarqube` in that file. Absent → stop:
   "run `/sonarqube-setup` first".
3. `sonar.host.url` and `sonar.projectKey` read from the build file (`sonar.organization`
   too, for SonarCloud).
4. `curl -sS <host>/api/system/status` answers `"status":"UP"`. Connection refused on
   `localhost` → stop: "`docker compose up -d sonarqube`, wait for `UP`, re-run". Any other
   host → stop, naming the host.
5. `SONAR_TOKEN` set and valid — `curl -sS -u "$SONAR_TOKEN:" <host>/api/authentication/validate`
   answers `"valid":true`. Unset or invalid → stop, naming the variable. Browse permission
   is only proven by the first read of step 3, and a 401/403 there stops the same way.

### 2 · Run the analysis

| Tool | Command |
|---|---|
| maven | `./mvnw -B -q verify sonar:sonar` |
| gradle | `./gradlew -q build sonar` |

`-q` rather than a redirect to a log file: the output stays short, and a redirect is a
write outside this skill's territory. A red build stops here — report the failing module
and the first error line; a lessons-learned about an analysis that never ran is fiction.

The analysis is **asynchronous**. Read `ceTaskId` from `target/sonar/report-task.txt`
(`build/sonar/report-task.txt` for gradle) and poll `/api/ce/task?id=<ceTaskId>` until
`SUCCESS`. Reading measures before that returns the **previous** analysis, silently.
`FAILED` or `CANCELED` → stop with the task's `errorMessage`.

### 3 · Extract

In this order, all read-only:

1. **Quality gate** — status and every condition, with its threshold and actual value.
2. **Measures** — the list in the reference: bugs, vulnerabilities, code smells, hotspots,
   coverage, duplication, ratings, `ncloc`, and the `new_*` metrics the gate reads.
3. **Issues by rule** — one call with `ps=1` and facets on rules, software qualities,
   severities and scopes: the counts, without paging through every issue. Then, for each
   rule, one call with a small `ps` for sample locations, split `MAIN` / `TEST`.
4. **Rule text** — `/api/rules/show` for each rule key: its name and why it matters.
5. **Duplication** — files with duplicated blocks, then the blocks of each.
6. **Coverage** — the files with the most uncovered lines.

Large answers are the only real cost of this skill: always request the facet first and
page only what step 4 needs.

### 4 · Trace each finding group to its owner

Group by Sonar rule (a duplicated block is its own group). For each group, find the piece
of **this project's `.claude/`** that produced it, in this order, and stop at the first hit:

| Origin | How to find it |
|---|---|
| **Template copied verbatim** | `Grep` the flagged construct in `.claude/skills/*/templates/*.example`. A hit means every project gets the issue |
| **Norm that allows or requires it** | `.claude/rules/00-index.md` → the norm for that layer; a norm that prescribes the flagged shape, or explicitly permits it |
| **Checkstyle / build configuration** | `config/checkstyle/*.xml` and the root build file — a check that is missing, or disabled, where Sonar has the equivalent rule |
| **Test template teaches the pattern** | Same as the first row, restricted to `test-architect` and `new-feature` test templates |
| **Code the bootstrap generated** | No template in the project — `project-bootstrap` does not travel. Name it `project-bootstrap` plus the role of the file (entry point, exception handler, config class) |
| **Nothing — the model decided alone** | No norm and no template covers the construct. The fix is a norm, or a line in the one that owns that layer |

Then decide the **fix at the owner**: which file in `.claude/`, what change, and whether it
also needs enforcement (a Checkstyle check, an ArchUnit rule) so the issue cannot come back.
Say it concretely — a file path, a line, the shape of the change — never "improve the
template".

Check whether an owner file was edited locally since the export:
`java .claude/hooks/ArchHook.java doctor` names the edited `.claude/` files. When the file at
fault is one of them, the lesson says so: the meta-repository's version may not have the defect.

### 5 · Write `docs/lessons-learned/sonar-NNN.md`

`NNN` is the highest existing `sonar-*.md` plus one, `001` when the directory is empty.
Fill `templates/lessons-learned.md.example`: snapshot, issues by origin, one section per
group, and the structural lesson — what in `.claude/` would have caught the whole class of
issue before the analysis did. Complete here: project paths, lines, excerpts all belong in
this file.

**Progress against the previous run.** When `sonar-<NNN-1>.md` exists, read its snapshot
and fill the snapshot's *Previous* and *Δ* columns, and mark each finding group *new*,
*recurring* (same rule key in the previous file) or — in a closing list — *gone*. The goal
is fewer issues each round; this is the only place that says whether it is happening. A
recurring group whose previous file already named the fix at the owner says so: the fix was
proposed and has not reached this project (not merged upstream, or not pulled with
`/arch-adopt`).

Report the path and the snapshot line, then go on to step 6.

### 6 · Offer the issue — one `AskUserQuestion`, nothing sent before it

1. **Target** — the slug from `source.git_url` in `.claude/schemas/extensions.json`
   (`https://github.com/<owner>/<repo>.git` → `<owner>/<repo>`). The stamp
   `.claude/.arch-provenance.json` gives `ref`, `commit` and `blueprint`: the version of
   the meta-repository this project came from, which the issue must name.
2. **Form** — fetch the issue form the repository owns:
   `curl -sS https://raw.githubusercontent.com/<owner>/<repo>/HEAD/.github/ISSUE_TEMPLATE/sonar-lessons.yml`.
   It is the single owner of the issue's title prefix (`title:`), labels (`labels:`),
   sections and what each may carry — read all four from it, never from this file: a label
   renamed there must not leave this skill filing under the old name. No answer → stop
   step 6: the file of step 5 is the deliverable, and no issue is written from memory of a
   form.
3. **Body** — write `docs/lessons-learned/sonar-NNN.issue.md` with one `### <label>` heading
   per form field, in the form's order, filled from the lessons-learned under that field's
   description — the same shape the web form produces. A `markdown` element has no label
   and is skipped; a `checkboxes` field is rendered as `- [X] <option>`, and only once the
   statement is true. Apply the privacy rule to every line; re-read the file for `src/`,
   the project's base package, and the project key before showing it.
4. **Duplicates** — `gh auth status`, then
   `gh issue list -R <slug> --state open --label <form label> --search "<rule key>"` for
   the three rules with the most issues. Hits are listed in the question.
5. **Ask** — show the body file's path and its sections, the duplicates found, and three
   options: *create the issue*; *I'll edit the body first* — stop, and the report carries
   the `gh issue create` command to run after the edit; *don't publish*.
6. **Create**, on the first option only:
   `gh issue create -R <slug> --title "<form title prefix><summary>" --label <form label> --body-file docs/lessons-learned/sonar-NNN.issue.md`.
   A failure naming the label (no triage permission on that repository) → the same command
   once without `--label`. Report the issue URL.
7. **Record the outcome** in the `## Issue` line at the end of `sonar-NNN.md`: the URL,
   *declined*, *left for a manual edit* (with the command), or *not created — <reason>*.
   Every path out of step 6 writes it, including the stops of item 2 and item 4: the file is
   what a later reader — or the next run's step 5 — checks to know whether the lesson
   reached the meta-repository.

## Failure modes

**No scanner in the build file:**
```
❌ pom.xml declares no sonar-maven-plugin. Run /sonarqube-setup, then /sonar-lessons.
Nothing written.
```

**Server down:**
```
❌ http://localhost:9000 refused the connection.
Run `docker compose up -d sonarqube`, wait for /api/system/status to answer UP, re-run.
```

**Token missing, invalid, or unable to read:**
```
❌ SONAR_TOKEN is not set — export a user token (My Account → Security) and re-run.
❌ /api/issues/search answered 403 — SONAR_TOKEN can analyze but not browse this project.
   A project analysis token cannot read the Web API; use a user token with Browse.
```

**Build red:**
```
❌ verify failed in <module> — <first error line>. No analysis ran; nothing written.
```

**Compute engine task failed:**
```
❌ Analysis task <id> ended FAILED: <errorMessage>. The previous analysis is still on the
server; nothing was read from it.
```

**Form not reachable, or `gh` not authenticated:**
```
⚠️ docs/lessons-learned/sonar-NNN.md written. Issue not created: <the form could not be
fetched | gh not authenticated — run `gh auth login`>. Create it later with:
gh issue create -R <slug> --title "<title>" --label <form label> --body-file docs/lessons-learned/sonar-NNN.issue.md
```

**Privacy check hit:**
```
⚠️ sonar-NNN.issue.md line <n> names <src/… | the base package | the project key>.
Rewritten to the .claude/ owner before asking. Review the file before confirming.
```

## Report

```
✅ Sonar lessons — <quality gate status> · <N> issues (<main> main / <test> test) · coverage <x> % · duplication <y> %

Lessons ..... docs/lessons-learned/sonar-NNN.md — <G> groups: <t> template, <r> norm, <c> checkstyle, <b> bootstrap, <m> model alone
Top owners .. <.claude path> (<n> issues) · <.claude path> (<n>) · …
Issue ....... <URL | not created — <reason> | declined>
Dashboard ... <host>/dashboard?id=<projectKey>
```

## Contract

**Class:** report — the territory is `skill_classes.report` in
`@.claude/schemas/extensions.json`: `docs/lessons-learned/**`, nothing else. `ArchHook.java
guard` enforces it; `target/` and `build/`, which the analysis writes, are ignored by git
and outside what `guard sweep` sees.

**Reads** the root build file, `target/sonar/report-task.txt` (or `build/sonar/`),
`.claude/rules/**`, `.claude/skills/*/templates/**`, `config/checkstyle/**`,
`.claude/schemas/extensions.json` (`source.git_url`), `.claude/.arch-provenance.json`, and
the SonarQube Web API.

**Writes** `docs/lessons-learned/sonar-NNN.md` and, when step 6 runs,
`docs/lessons-learned/sonar-NNN.issue.md` — the exact body that was, or would have been,
published.

**Publishes** one issue on the repository named by `source.git_url`, only after the
confirmation of step 6, with the body file as it stands on disk.

**Never writes** the build file, `docker-compose.yml`, a token, or anything under `src/`. A
finding is fixed at its owner in the meta-repository, or by `/new-feature` in this
project — not by this skill.

**Does not own** the issue's shape: `.github/ISSUE_TEMPLATE/sonar-lessons.yml` in the
meta-repository does, fetched at step 6 — the web form and this skill produce the same
issue because they read the same file.

**Not chained** by any skill. Travels into the generated project
(`export.skills.include`); in the claude-spring-architect repository itself it stops at
step 1, which has no build file.
