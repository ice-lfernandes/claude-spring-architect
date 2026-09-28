# 0044 · `@Idempotent` from the first `Idempotency-Key` endpoint, not only the second

- **Date:** 2026-09-17
- **Scenario:** lessons-learned-006 (UC-001-register-customer, `demo-clean-arch-single-module`)
  — the executor's first protected endpoint hand-called `IdempotentExecution`, per the
  rule's original "first endpoint manual, second+ `@Idempotent`" split. Reviewing the
  result, the user judged the split not worth keeping.
- **Decision:** amend `rest-api-architect/SKILL.md` step 7 — every `Idempotency-Key`
  endpoint, first one included, uses `@Idempotent` + `IdempotencyAspect`.
- **State:** approved by the user, on 2026-09-17.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| Concrete symptom | `CustomerController`'s first protected endpoint carried ~30 extra lines of by-hand response (de)serialization that `IdempotentExecution`/`IdempotencyAspect` already solves generically — code the second endpoint would have discarded anyway once the aspect appeared | — (this is a content edit to an existing skill's body, not a new form) |
| Cost of getting it wrong | Every future first-`Idempotency-Key`-endpoint project repeats the same throwaway manual code until a second endpoint forces the migration | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Always `@Idempotent`, drop the first/second split | — | **Approved** |
| 2 | Keep the original split (reflection cost only justified from the second endpoint on) | — | Rejected — the reflection/lookup cost the split was protecting against is smaller than the ~30 lines of hand-rolled (de)serialization it was causing, observed live |

### Option 1 — always `@Idempotent` (approved)

**Motivator:** concrete symptom above — real code, not anticipation.

**Pros:** one code path regardless of endpoint count; `Controller.java.example` already
used `@Idempotent` (the template had drifted ahead of the prose in `SKILL.md`, which
still described the old two-tier rule) — this decision makes the prose match the
exemplar instead of the other way around.

**Cons:** the reflection-based response-type lookup and body-index argument
(`IdempotencyAspect.java.example`'s own header comment, "COST OF THE ANNOTATION") are
now paid on the very first protected endpoint of a project, even one that never gets a
second. Accepted: fixed cost per project, not per endpoint.

## References

| Claim | Source |
|---|---|
| `Controller.java.example` already implements the `@Idempotent`-from-the-start shape | `.claude/skills/rest-api-architect/templates/Controller.java.example`, method `create` |
| Original split's rationale (reflection cost) | `.claude/skills/rest-api-architect/templates/IdempotencyAspect.java.example` header, "COST OF THE ANNOTATION, ONLY REFLECTION BUYS THESE" |
| Observed cost of the manual path | `.claude/lessons-learned/lessons-learned-006.md` § 10 |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/rest-api-architect/SKILL.md` | Step 7 rewritten: no first/second split; table row for the pagination/idempotency block updated |

Goes to the generated project: **no** — `rest-api-architect` is a development skill
copied at step 6.7, but this record itself is meta-repo history (invariant 9); only the
`SKILL.md` change ships, via the normal step 6.7 copy.
