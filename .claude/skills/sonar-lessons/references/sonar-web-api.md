# SonarQube Web API — the calls `sonar-lessons` makes

Read by `sonar-lessons` at steps 1–3. Every call is a `GET`, authenticated with
`curl -sS -u "$SONAR_TOKEN:" …` (the token as the user name, empty password — accepted by
SonarQube and SonarCloud alike). SonarCloud adds `organization=<sonar.organization>` where
a call takes a project.

**The server documents itself.** When a parameter below is rejected (`400` with
`"errors":[{"msg":…}]`) — the API has renamed parameters across major versions — read the
current contract from the same server instead of guessing:
`GET /api/webservices/list?include_internals=false`, and adjust. Never fall back to scraping
the dashboard HTML.

## Preconditions

| Call | Reads | Notes |
|---|---|---|
| `GET /api/system/status` | `status` | `UP` is the only state that serves reads. `STARTING` right after `docker compose up` — wait, don't fail. No auth needed |
| `GET /api/authentication/validate` | `valid` | `true` with a valid token. Proves authentication, not the Browse permission |

## Waiting for the analysis

`target/sonar/report-task.txt` (maven) or `build/sonar/report-task.txt` (gradle) is a
properties file the scanner writes at the end of the upload: `projectKey`, `serverUrl`,
`dashboardUrl`, `ceTaskId`, `ceTaskUrl`.

| Call | Reads | Notes |
|---|---|---|
| `GET /api/ce/task?id=<ceTaskId>` | `task.status`, `task.analysisId`, `task.errorMessage` | `PENDING` / `IN_PROGRESS` → poll again after a few seconds. `SUCCESS` → go on. `FAILED` / `CANCELED` → stop |

## Quality gate and measures

| Call | Reads |
|---|---|
| `GET /api/qualitygates/project_status?analysisId=<analysisId>` | `projectStatus.status`; `projectStatus.conditions[]` → `metricKey`, `comparator`, `errorThreshold`, `actualValue`, `status` |
| `GET /api/measures/component?component=<projectKey>&metricKeys=<list>` | `component.measures[]` → `metric`, `value`, and `period.value` for the `new_*` metrics |

`<list>`: `bugs,vulnerabilities,code_smells,security_hotspots,reliability_rating,security_rating,sqale_rating,coverage,line_coverage,branch_coverage,uncovered_lines,duplicated_lines_density,duplicated_blocks,ncloc,violations,new_violations,new_coverage,new_duplicated_lines_density`

`analysisId` rather than `projectKey` on the gate call: it pins the gate to the analysis
step 2 just produced.

## Issues

Counts first, without paging:

```
GET /api/issues/search?components=<projectKey>&resolved=false&ps=1
    &facets=rules,impactSoftwareQualities,impactSeverities,scopes
```

| Field | Use |
|---|---|
| `total` | open issues |
| `facets[rules].values[]` → `val`, `count` | one finding group per rule key, largest first |
| `facets[impactSoftwareQualities]` | `RELIABILITY` (what the dashboard counts as bugs), `SECURITY`, `MAINTAINABILITY` (code smells) |
| `facets[impactSeverities]` | `BLOCKER` … `INFO` |
| `facets[scopes]` | `MAIN` vs `TEST` — the split every lesson reports |

Samples, per rule:

```
GET /api/issues/search?components=<projectKey>&resolved=false&rules=<ruleKey>&ps=10
```

`issues[]` → `component` (`<projectKey>:<path>`), `line`, `message`, `impacts[]`, `scope`.
Ten per rule is enough to recognize the construct and grep for it in `.claude/`; the lesson
lists every location only when there are few, and the count otherwise.

A server older than the software-quality model rejects the two `impact*` facets — use
`types,severities` there, and read `BUG` / `VULNERABILITY` / `CODE_SMELL`.

## Rule text

| Call | Reads |
|---|---|
| `GET /api/rules/show?key=<ruleKey>` | `rule.name`, `rule.descriptionSections[]` (`key`: `root_cause`, `how_to_fix`) — the "why" and the canonical fix, quoted short in the lesson |

## Duplication

```
GET /api/measures/component_tree?component=<projectKey>&qualifiers=FIL
    &metricKeys=duplicated_blocks,duplicated_lines
    &metricSort=duplicated_blocks&s=metric&asc=false&metricSortFilter=withMeasuresOnly&ps=50
```

Then, for each file returned:

```
GET /api/duplications/show?key=<fileComponentKey>
```

`duplications[].blocks[]` → `from`, `size`, `_ref`; `files` maps each `_ref` to a component
key. One block shared by two files is one finding group — its owner is almost always a
template both files were generated from.

## Coverage

```
GET /api/measures/component_tree?component=<projectKey>&qualifiers=FIL
    &metricKeys=coverage,uncovered_lines,uncovered_conditions
    &metricSort=uncovered_lines&s=metric&asc=false&metricSortFilter=withMeasuresOnly&ps=20
```

The files with the most uncovered lines. A file with zero coverage that a template
produced points at the test template that should have come with it.

## Status codes

| Code | Meaning here |
|---|---|
| `401` | Token missing or invalid |
| `403` | Token valid, cannot Browse this project — typically a project analysis token, which can only run the scanner |
| `404` on `component` | Project key in the build file differs from the one analyzed — read `projectKey` from `report-task.txt` |
