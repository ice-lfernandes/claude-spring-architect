# 0092 · A same-class Skill call joins the open phase, and the territory is the union

- **Date:** 2026-09-30
- **Scenario:** review of PR #44 (`sonarqube-setup`, decision 0091) — "com base nas novas features dessa branch, há possíveis melhorias em ci pipeline / documentações?"; a probe of `guard` found the new chains refused
- **Decision:** Form 7c — change to the existing `guard call` / `guard write` in `.claude/hooks/ArchHook.java`; no new mode, no new registration
- **State:** approved by Lucas Fernandes, 2026-09-30 — "soma dos territórios"
- **Goes to the generated project:** yes — `ArchHook.java` and the jar travel whole via `export`

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Observed by probe, not by a user: `/arch-adopt` → `Skill sonarqube-setup` → write `pom.xml` exit 2; same for `.github/workflows/build.yml`; `sonarqube-setup` → `Skill docker-architect` → write `docker-compose.yml` exit 2 | "create nothing" — both chains decision 0091 wires would fail on first use |
| 7 — Mandatoriness | Territory is already a guarantee (`guard`, deny by default); the bug is inside it | Forms 1–5 — prose cannot change what the hook admits |
| 16 — Existing mode | `guard call` already decides what a Skill call does to the phase | New mode, new registration |

**Cause.** `guardCall` kept the caller's phase on a same-class Skill call, on the stated
assumption that "two skills of one class share a territory". `build` does not: its class
default is empty and every skill carries its own override (`project-bootstrap` `**`,
`docker-architect` compose, `arch-adopt` `.claude/**`, `sonarqube-setup` build file and
workflows). The only same-class chain before 0091 was `project-bootstrap` →
`docker-architect`, which worked only because the caller's territory is `**`.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Same-class call **joins** the phase — the state file lists every skill, territory is the union | 8 | **Approved** |
| 2 | Same-class call keeps the caller only when the caller's territory is `**`, otherwise replaces with the callee | 6 | Rejected — the caller loses its own territory for the rest of the turn; `arch-adopt` could not report into `.claude/` after step 7, and the ordering of every future chain would matter |
| 3 | Give `arch-adopt` and `sonarqube-setup` wider overrides | 2 | Rejected — widens two territories to paper over a rule bug; `arch-adopt` "writes only `.claude/`" stops being true |
| 4 | Keep the bug, order the steps so no write follows a chain | 1 | Rejected — does not help: the callee itself is refused |

### Option 1 (score 8)

**Mechanism.** The phase state file holds one skill name per line, opener first.
`guardCall`: callee of the opener's class → appended (`guardJoin`) unless present; another
class → `guardOpen` replaces, as before. `guardViolations`: class read from the first line,
`write_allow` is the union of every line's; the block message names them joined by ` + `.
`guardRefuseBuildCall` reads the opener. A plain prompt still deletes the file.

**Why a union and not a stack.** The runtime gives no signal when a chained skill returns,
so a stack would never pop; the union is the honest shape of "everything this turn's chain
was entitled to write". It never exceeds the sum of territories the data already granted.

**Pros:** fixes both 0091 chains and keeps `project-bootstrap` → `docker-architect`
unchanged; cross-class narrowing (design stays docs-only) untouched; no data change.

**Cons:** within one turn, a skill early in a same-class chain can write the paths of one
chained later — bounded by the class, and only after the chain happened.

**Points cut in the rubric:** criterion 5 (a Java change and a jar rebuild).

## References

| Claim | Source |
|---|---|
| Same-class call kept the caller's phase | `.claude/hooks/ArchHook.java` `guardCall`, before this change |
| `build` has no common territory, one override per skill | `skill_classes.build` `$comment` in `.claude/schemas/extensions.json` |
| Cross-class call narrows to the callee | `.claude/.ci/SkillTerritoryTest.java` "the callee's narrower territory is what applies" |
| Every `ArchHook.java` change rebuilds the jar | `.claude/decisions/0075-precompiled-hook-jar.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` + `.jar` | `guardCall` joins; `guardJoin`, `phaseSkills`; `guardViolations` and `guardRefuseBuildCall` read the list. Jar rebuilt |
| `.claude/.ci/SkillTerritoryTest.java` | chain `arch-adopt` → `sonarqube-setup` → `docker-architect`: joins, union allows, deny-by-default outside |
| `.claude/skills/project-bootstrap/SKILL.md` | the Sonar step moves from 8.7 to 8.4 (before the README) — "last" was a workaround for this bug |
| `docs/pt-br/11-pitfalls.md`, `docs/en/11-pitfalls.md` | same-class chain sums, other class replaces |
| `docs/*/07-ci-validate.md` | `SkillTerritoryTest` now 22 cases, the chain among them |

Same review, outside the guard change, recorded here so it isn't orphaned:

| File | Change |
|---|---|
| `.github/workflows/validate.yml` | step `compose service templates merge and parse` — every `docker-architect` service template merged into the base compose, `docker compose config -q` |
| `docker-architect/templates/kafka-service.yml.example` | declares `kafka_data` under a top-level `volumes:` — it used the volume without declaring it |
| `docker-architect/templates/grafana-stack-service.yml.example` | named volumes as a real top-level block, not a comment, like the other templates |
| `project-bootstrap/templates/root.CLAUDE.md.example` | routing rows for `jobs-architect`, `docker-architect`, `sonarqube-setup`, `arch-adopt` — the generated project's table had drifted |
| `docs/*/00-overview`, `docs/*/09-*`, `docs/*/07-ci-validate.md` | `sonarqube-setup` node and chains; static-analysis row; the new CI step |

Written by the main thread, not delegated — Form 7 propagation.
