# 0040 · Generalize `Idempotency-Key` to a second endpoint via AOP — reuse the existing port

- **Date:** 2026-09-16
- **Scenario:** promote the improvements found while generalizing `Idempotency-Key` into
  an `@Idempotent` annotation + Spring AOP aspect in
  `demo-clean-architecture-single-module-init-project-example`
  (`LESSONS-LEARNED-idempotency-aop.md`) into this meta-repo's `rest-api-architect` and
  `persistence-architect` templates.
- **Decision:** Option A — `IdempotencyAspect.java.example` calls the existing
  `IdempotentExecution.execute()` via reflection; no second port. `StoredResponse` gains
  a `headers` field.
- **State:** approved by the user, on 2026-09-16.

## Context

The lessons-learned document found 7 issues while doing this generalization in a
generated project. Six were either already fixed in this repo's exemplars (`release()`
on rollback — `IdempotentExecution.java.example` / `IdempotencyKeyStore.java.example`
already had it) or scoped to that one project (test-pyramid effect, a Checkstyle
parameter-count hit). The one real meta-repo gap: this repo has no template for a
**second** `Idempotency-Key` endpoint that avoids hand-writing the claim/replay/complete
block again. The lesson's own exemplar for that closes the gap by inventing a **second,
incompatible port** (`begin/complete/release` + `IdempotencyOutcome`/
`IdempotencyCompletion`, vs. the existing `claim/complete/release` + `IdempotencyClaim`/
`StoredResponse`) and explicitly flags the two-port situation as an unresolved
"open question" before pasting.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Reuse existing port — aspect calls `IdempotentExecution` via reflection | 8 | **Approved** |
| 2 | Paste the lesson's exemplar as-is — second port + a written criteria table | 5 | Rejected — permanent two-port-vocabulary risk |
| 3 | Create nothing — tell developers to keep hand-writing the block per endpoint | 2 | Rejected — the duplication is exactly what the lesson observed and what OCP (`code-quality.md`) argues against once an axis has actually varied |

### Option 1 — reuse existing port (score 8)

**Motivator:** axis 9 (integration) — a second port with different method names for the
same problem is an ownership conflict against the existing, already-verified
`IdempotencyKeyPort`/`IdempotentExecution` (invariant 2: a norm — here, the idempotency
transaction shape `api-rest.md` § Idempotency describes — has one owning implementation,
not two divergent ones).

**Pros:**
- Zero changes to `IdempotentExecution.java.example` / `IdempotencyKeyStore.java.example`
  / `IdempotencyClaim.java` — already correct, already carries `release()`.
- One port, one persistence exemplar, two REST-layer wiring styles (explicit call for a
  single endpoint, `@Idempotent` + aspect once a second one appears) — matches the
  lesson's own stated trade-off (type safety vs. zero controller boilerplate) without
  the vocabulary split.
- `spring-boot-starter-aspectj` naming and the `IllegalThrows`/`ProceedingJoinPoint`
  Checkstyle suppression (Boot 4 renamed the AOP starter; no skill previously stated
  this) travel with the new exemplar's header comment, the same place the lesson found
  them.

**Cons:** `StoredResponse` gains a `headers` field — every caller of its constructor in
generated code changes from 2 to 3 args. Exemplar-only change (no live code to migrate),
scored against it in the rubric but not disqualifying.

### Option 2 — paste as-is (score 5)

Zero edits to verified files, but leaves two permanently divergent port vocabularies
(`begin` vs. `claim`, `IdempotencyCompletion` vs. `StoredResponse`) for the same
capability in the same skill's `templates/`, which is the exact risk the lesson itself
raised and asked to be resolved before pasting.

## References

| Claim | Source |
|---|---|
| `IdempotentExecution`/`IdempotencyKeyStore` already implement `release()` on rollback | `.claude/skills/persistence-architect/templates/IdempotentExecution.java.example`, `IdempotencyKeyStore.java.example` (read during this session, both already correct) |
| Two transactions, shared component, no headers in stored response (being extended) | `.claude/rules/api-rest.md` § Idempotency |
| `spring-boot-starter-aop` isn't in the Boot 4 BOM, renamed `-aspectj` | `LESSONS-LEARNED-idempotency-aop.md` entry 7 |
| `rest-api-architect`/`persistence-architect` are development skills copied to every generated project | `.claude/skills/project-bootstrap/SKILL.md` line ~1003 (step 6.7 glob) |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/persistence-architect/templates/IdempotentExecution.java.example` | `StoredResponse` gains `headers`; recorder/decode call sites updated |
| `.claude/skills/persistence-architect/templates/IdempotencyKeyStore.java.example` | `StoredResponse` construction sites updated for the new field |
| `.claude/skills/rest-api-architect/templates/IdempotencyAspect.java.example` (new) | `@Idempotent` annotation + aspect, reusing `IdempotentExecution` via reflection |
| `.claude/skills/rest-api-architect/templates/IdempotencyKeyInterceptor.java.example` | annotation-based `requiresKey` variant appended, documented as opt-in |
| `.claude/skills/rest-api-architect/SKILL.md` | one-paragraph selection criteria: 1 endpoint → explicit call, 2+ → `@Idempotent` |

Goes to the generated project: **yes, via step 6.7** — both skills are already copied
wholesale; no separate copy step needed.
