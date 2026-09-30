# 0029 · New skill `docker-architect` for docker-compose/Dockerfile extension after bootstrap

- **Date:** 2026-09-10
- **Scenario:** Criar skill responsável por dockerização/containerização. Docker e
  docker-compose básicos nascem no `project-bootstrap`; extensões (serviço de DB,
  config de Testcontainers) acontecem depois, disparadas por skills do `/new-feature`
  quando necessário.
- **Decision:** Form 1 — `.claude/skills/docker-architect/SKILL.md`
- **State:** approved by Lucas Fernandes, on 2026-09-10

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 2 — Trigger | Ambos: auto-invocada por outras skills (persistence-architect, test-architect) e manual pelo usuário | Rules out Form 2-only and "fold into existing skills" |
| 8 — Destination | Ambos: meta-repo e projeto gerado | Rules out "meta-repo only"; forces step 6.7 propagation and mandatory Level-2 record |
| 1 — Symptom | Confirmado, duplo: DB modelado sem serviço no compose; Testcontainers sem imagem/config pinada | Rules out "create nothing" (anti-pattern 9) |
| — Base docker | `project-bootstrap` gera Dockerfile + compose mínimo (só app); skill nova só estende depois | Rules out "skill owns generation from scratch" |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `.claude/skills/docker-architect/SKILL.md` (Form 1) | 9 | **Approved** |
| 2 | Fold docker-editing logic into `persistence-architect` and `test-architect` bodies | 3 | Rejected — duplicated procedure, invariant 2 |
| 3 | Subagent `docker-architect` | 2 | Rejected — none of the three agent reasons apply |
| 4 | create nothing | 1 | Rejected — concrete symptom confirmed twice |

### Option 1 — `docker-architect` skill, Form 1 (score 9)

**Motivator:** Axis 2 (Ambos) matches exactly the precedent of the five `/new-feature`
pipeline skills (D0007): Form 1, no `disable-model-invocation`, invocable both by a
model decision inside a sibling skill and manually via `/docker-architect`.

**Pros:**
- Single owner of `docker-compose.yml`/`Dockerfile` edits after the bootstrap's initial
  write — `persistence-architect` and `test-architect` stop needing to touch YAML
  themselves, they just invoke this skill (invariant 2).
- Reuses the exact Form-1 precedent already validated in this repo — no new pattern to
  justify from scratch, except being called by a sibling skill instead of only by
  `new-feature` (novel, costs a rubric point).
- Development skill → travels to the generated project (step 6.7), so a project born
  today can still add a DB service to its compose file for UC-014 six months from now.

**Cons:**
- New file to maintain, with its own `templates/` (service snippets: Postgres, Mongo,
  Kafka, generic Testcontainers env) and its own `references/`.
- Being invoked by a sibling skill rather than only by `new-feature` is a pattern this
  repo hasn't used before — costs one rubric point (criterion 6).
- Enforcement stays persuasion-based: nothing blocks `persistence-architect` from
  editing `docker-compose.yml` inline by mistake. No hook exists for this and none is
  proposed — out of scope for this skill per its own § Out of scope.

**Points cut in the rubric:** criterion 6 (precedent) — 1 point, novel calling pattern
(sibling-invoked, not orchestrator-invoked).

### Option 2 — fold into `persistence-architect`/`test-architect` (score 3)

Same procedure (detect DB/Testcontainers need → edit `docker-compose.yml`) would have
to be written in both skills' bodies. First divergence on the next edit — invariant 2.
Capped ≤ 4 by the rubric's invariant-violation rule.

### Option 3 — subagent (score 2)

Fails the § 5 counter-test on all three points: the docker decision is short, fits in a
`templates/` reference, and produces a short diff — no context to isolate, no tool to
restrict, no model change justified. Anti-pattern 1.

### Option 4 — create nothing (score 1)

Rejected: axis 1 confirmed a concrete, repeated symptom (DB without a compose service,
Testcontainers without a pinned image), not anticipation. Anti-pattern 9 doesn't apply
here — the case is real.

## References

| Claim | Source |
|---|---|
| Form 1 = auto + manual invocation, precedent for pipeline pieces | `references/decision-matrix.md` § 4, and `.claude/decisions/0007-pipeline-skills-invocation.md` |
| Development skill → copied in step 6.7 | `project-bootstrap/SKILL.md` step 6.7 table |
| Invariant 2 — single owner, cited by path | `@CLAUDE.md` invariant 2 |
| `project-bootstrap` already writes base config from its own `templates/` (checkstyle, lombok, logback) without delegating to a development skill | `project-bootstrap/SKILL.md` steps 4.6, 4.8, 4.9 |
| `test-architect` already touches one line of a Docker-adjacent file (`TestcontainersConfiguration.java`'s image tag) without owning the compose file | `test-architect/SKILL.md` step 10 of setup mode |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/docker-architect/SKILL.md` | New skill, Form 1 |
| `.claude/skills/docker-architect/templates/postgres-service.yml.example`, `mysql-service.yml.example` | Boilerplate, invariant 3. H2 needs no container; other engines follow the same shape by hand until a real project needs a third template |
| `.claude/skills/project-bootstrap/SKILL.md` | New step 4.10: writes base `Dockerfile` + minimal `docker-compose.yml` (app service only) from its **own** `templates/`, no cross-skill dependency at generation time. Precondition list, `andaime` scope, Skill contract `Writes`, Output contract, step 6.7 table (+row), and the two point-3 `decisions/` grep lists all updated |
| `.claude/skills/project-bootstrap/templates/Dockerfile.example`, `docker-compose.yml.example` | Base shape, owned by bootstrap, mirrors the `logback-spring.xml.example` pattern |
| `.claude/skills/persistence-architect/SKILL.md` | Procedure step 9 (new) invokes `docker-architect` when the chosen engine has no compose service yet; Contract gained a "Does not edit `docker-compose.yml`" line |
| `.claude/skills/test-architect/SKILL.md` | Setup-mode step 10 invokes `docker-architect` on a tag mismatch between the Java pin and the compose service; Contract gained the same "Does not edit" line |
| `@CLAUDE.md` routing table | Row added: docker/docker-compose/service/Testcontainers-at-compose-level → `docker-architect` |

Goes to the generated project: **yes, via step 6.7** — same reason as
`persistence-architect`: only makes sense once the project exists, and a feature months
after bootstrap may still need a new service in `docker-compose.yml`.
