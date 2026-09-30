# 0028 · `logging.md` rule + default `logback.xml` in `project-bootstrap`

- **Date:** 2026-09-09
- **Scenario:** wants a skill for log standardization by file type, applied after
  `/init-project`: (1) logback usage, (2) general standardization, (3) standardization
  per class type (rest, adapterRepository, aggregator...).
- **Decision:** `.claude/rules/logging.md` (Form 4) + `.claude/skills/project-bootstrap/templates/logback.xml.example` (extends an existing skill, not a new form)
- **State:** approved by Lucas Fernandes, on 2026-09-09

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Both: no default `logback.xml` from project creation, and no per-class-type log convention | Rules out "create nothing" |
| 2 — trigger | `logback.xml` fixed from project creation (no interview needed); per-class-type content is a rule that auto-loads while classes are generated | Rules out Form 2/3 for both halves — no procedure, no isolation need |
| 9 — collision | Pre-existing overlap found in two places, not one: `observability.md`'s old § Logs restated general level semantics, and `error-handling.md`'s § Logging and verification restated the same WARN/ERROR split for exceptions specifically. User's own answer narrowed `observability.md` to vendor wiring only | Forces a 3-file edit (new rule + two narrowed/trimmed existing rules), not a clean add |
| 4 — nature of per-class-type content | Declarative table, no boilerplate to generate | Rules out a code-generating skill; confirms Form 4 |

**Literal request was "a skill."** Classified as a rule instead: the content is a
declarative fact with a territory (`**/*.java`, blueprint-independent — applies to
every layer, including domain, which needs to know it logs nothing), not a
procedure. The decision-matrix table's row for this shape points at Form 4, and the
`logback.xml` half needed no new piece at all — it slots into `project-bootstrap`'s
existing single-root-config-file pattern (`lombok.config`, step 4.8).

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `logging.md` (new) + narrow `observability.md` + trim `error-handling.md` + `logback.xml.example` in `project-bootstrap` | 9 | **Approved** |
| 2 | Add `logging.md` only, leave `observability.md` and `error-handling.md` untouched | 5 | Rejected — leaves the WARN/ERROR duplication between two files standing; contradicts the user's own axis-9 answer narrowing `observability.md` |
| 3 | create nothing | 2 | Rejected — contradicts axis 1 |

### Option 1 — three-file split (score 9)

**Motivator:** axis 9. Two pre-existing rules claimed the same "what level for what
kind of event" territory; adding a third without resolving that would have made it
three.

**Pros:** single owner per fact afterward — `logging.md` owns format/level/per-class
content, `observability.md` owns only vendor wiring (tracing bridge origin, metrics
annotations), `error-handling.md` keeps only what's exception-specific (stack trace
presence, `errorCode` field) and cites `logging.md` for the level name instead of
restating it; `logback.xml.example` follows the exact precedent of `lombok.config`
(step 4.8 → new step 4.9), zero new mechanism.

**Cons:** touches two files whose content predates this session — real, but the
alternative (Option 2) leaves a known duplication in place on record.

**Points cut in the rubric:** maintenance cost — three files change instead of one;
accepted because the alternative is a known bug, not a hypothetical one.

## References

| Claim | Source |
|---|---|
| `lombok.config` single-root-file pattern | `.claude/skills/project-bootstrap/SKILL.md` step 4.8 |
| `traceId` is the correlation field name | `.claude/rules/api-rest.md:115,141` |
| Pre-existing level-semantics duplication | `.claude/rules/observability.md` § Logs (pre-edit) vs `.claude/rules/error-handling.md` § Logging and verification (pre-edit, lines 55-64) |
| Canonical class-type suffixes | `.claude/rules/naming.md:20-35` |
| `packages.map` keys for `hexagonal` | `.claude/blueprints/hexagonal.yaml:61-79` |

## Propagation

| File | Change |
|---|---|
| `.claude/rules/logging.md` | Created |
| `.claude/rules/observability.md` | Narrowed to vendor wiring (tracing origin, metrics annotations); Logs section removed |
| `.claude/rules/error-handling.md` | § Logging and verification trimmed to exception-specific detail, cites `logging.md` for level names |
| `.claude/skills/project-bootstrap/templates/logback.xml.example` | Created |
| `.claude/skills/project-bootstrap/SKILL.md` | New step 4.9; `logging.md` added to step 6.6's copy table |
| `.claude/rules/00-index.md` | `observability.md` row description narrowed; `logging.md` row added |

Goes to the generated project: **yes** — `logging.md` copies verbatim via step 6.6
(`**/*.java`, blueprint-independent, same group as `naming.md`/`error-handling.md`);
`logback.xml` is generated at project creation via the new step 4.9.
