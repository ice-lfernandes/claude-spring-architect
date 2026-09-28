# 0042 · Spring Data's `Pageable`/`Page` at the REST and persistence boundary, never in the application port

- **Date:** 2026-09-17
- **Scenario:** wanted a standard for using Spring Data when generated endpoints paginate,
  and asked whether it belongs in a skill or a rule.
- **Decision:** three edits to already-existing rule files — `architecture-ddd.md` §
  Application, `api-rest.md` § Pagination, `persistence.md` § Queries — plus new/updated
  exemplars in `rest-api-architect/templates/` (`PageCriteria.java.example` new;
  `Api.java.example`, `Controller.java.example`, `RestMapper.java.example`,
  `OpenApiDocs.java.example` updated) and one line in
  `project-bootstrap/templates/application.yml.example`
  (`spring.data.web.pageable.max-page-size`). No new rule file, no new skill.
- **State:** approved by Lucas Fernandes, on 2026-09-17

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Generated code seen hand-rolling `int page, int size, String sort` instead of Spring Data | Eliminated "create nothing" |
| 4/9 — territory | REST and persistence adapters share the outer ring and may both depend on Spring Data directly; the application/use-case port must keep its own framework-free pagination type | Eliminated "port takes `Pageable` directly"; eliminated a single rule spanning both adapter territories plus the port |
| — (follow-up) | User proposed either merging `rest-api-architect`+`persistence-architect`, or one new rule with `paths` across `**/rest/*`, `**/controller/*`, `**/persistence/*` | Both rejected — see Options 2 and 3 |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Extend the 3 existing owning rule files + `rest-api-architect` exemplars | 9 | **Approved** |
| 2 | New `pagination.md` rule, `paths` across REST + persistence globs | 4-5 | Rejected — duplicates territory `00-index.md` already assigns to `api-rest.md` and `persistence.md` (invariant 2); worse context cost (loads persistence-only detail when only a controller file is touched, and vice versa) |
| 3 | Merge `rest-api-architect` and `persistence-architect` skills | — | Rejected — skills aren't where a declarative fact lives (invariant 1); merging doesn't change which rule owns the norm |
| 4 | Create nothing | 3 | Rejected — symptom confirmed (axis 1), and the existing exemplar (`Controller.java.example` passing raw `page,size,sort` into the port) already contradicted the now-agreed convention |

### Option 1 — 3 rule edits + exemplars (score 9)

**Motivator:** axis 4/9 — REST and persistence are the same outer ring (framework
dependency acceptable in both), the application port is the one boundary that must stay
framework-free.

**Pros:** each edit stays inside its rule's already-declared topic (`00-index.md` lists
"pagination" under both `api-rest.md` and `persistence.md` already); zero new files at
the rule level; exemplar gives the convention a compilable shape instead of prose nobody
implements the same way twice, matching this repo's existing pattern
(`PageResponse.java.example` already exists for the output envelope).

**Cons:** touches three rule files instead of one; `PageCriteria.java.example` is
defined inside `rest-api-architect/templates/` even though the type itself belongs to
`application.port.in` — same precedent as `persistence-architect`'s
`IdempotencyKeyStore.java.example` owning `IdempotencyRequest`/`StoredResponse`, which
are also application-layer types consumed from `rest-api-architect`'s
`Controller.java.example`.

**Points cut in the rubric:** § 8 criterion 5 (maintenance cost) — half a point, three
files instead of one, judged worth it for closing the actual gap.

### Option 2 — new `pagination.md` rule (score 4-5)

Would either duplicate content already in `api-rest.md`/`persistence.md` (invariant 2)
or require extracting their existing pagination bullets into the new file — a bigger,
riskier diff for the same outcome, and it fights the split `00-index.md` already
documents.

### Option 3 — merge the two skills

Skills aren't where a rule's normative text lives (`references/decision-matrix.md` § 6);
merging containers doesn't change file ownership of the norm.

## References

| Claim | Source |
|---|---|
| Rule = declarative fact + territory, Form 4 | `references/decision-matrix.md` § 2 |
| Single owner, no duplication | `@CLAUDE.md` invariant 2 |
| `api-rest.md`/`persistence.md` already both list "pagination" as covered | `@.claude/rules/00-index.md` |
| Boilerplate lives in `templates/*.example`, not rule prose | `@CLAUDE.md` invariant 3 |
| Precedent: a skill's templates defining a type another skill's exemplar consumes | `persistence-architect/templates/IdempotencyKeyStore.java.example` (`IdempotencyRequest`/`StoredResponse`) used from `rest-api-architect/templates/Controller.java.example` |
| `spring.data.web.pageable.max-page-size` needed because Spring Data doesn't cap `size` on its own | Spring Data Web `PageableHandlerMethodArgumentResolver` behavior — no built-in max rejection without this property |

## Propagation

| File | Change |
|---|---|
| `.claude/rules/architecture-ddd.md` | § Application — new bullet naming `Pageable`/`Page`/`Sort` as framework types banned from port signatures |
| `.claude/rules/api-rest.md` | § Pagination — new bullet: controller binds `Pageable`, converts before the port call, `@ParameterObject` + `max-page-size` notes |
| `.claude/rules/persistence.md` | § Queries — new bullet: repository takes `Pageable`/returns `Page<Entity>`, adapter maps at its own boundary |
| `.claude/skills/rest-api-architect/templates/PageCriteria.java.example` | New — the application-owned pagination/sort type |
| `.claude/skills/rest-api-architect/templates/Api.java.example` | `list` takes `@ParameterObject @PageableDefault Pageable pageable` instead of three primitives |
| `.claude/skills/rest-api-architect/templates/Controller.java.example` | `list` converts via `UserMapper.toCriteria(pageable)` |
| `.claude/skills/rest-api-architect/templates/RestMapper.java.example` | New `toCriteria(Pageable)` — the only place `Pageable` and this adapter's mapper meet |
| `.claude/skills/rest-api-architect/templates/OpenApiDocs.java.example` | Comment noting `@Parameter` still documents the `Pageable`-bound param by name |
| `.claude/skills/rest-api-architect/SKILL.md` | Artifact table row: added `PageCriteria.java.example` |
| `.claude/skills/project-bootstrap/templates/application.yml.example` | `spring.data.web.pageable.max-page-size: 100` |

Goes to the generated project: **yes, via step 6.6 (the three rule files) and step 6.7 /
step 7 (the `rest-api-architect` and `project-bootstrap` templates) — all five already
have an existing propagation path, none of this opened a new one.**
