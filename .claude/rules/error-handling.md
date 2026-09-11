---
paths:
  - "**/*.java"
status: active
---

# Error handling — exception taxonomy

Transport-agnostic. No framework exception crosses the domain boundary. Mapping to a
protocol is the adapter's decision — HTTP in `@.claude/rules/api-rest.md`.

## Taxonomy

Typed family, not a flat `DomainException` with a status field:

| Type | When |
|---|---|
| `NotFoundException` | Referenced entity doesn't exist |
| `ValidationException` | Invariant violated while constructing the aggregate/VO |
| `BusinessRuleViolationException` | Well-formed input, violates a business rule |
| `ConflictException` | Duplicate or concurrent modification |

Domain `ValidationException` ≠ bean validation (`jakarta.validation` on the DTO): the
DTO blocks structural shape before the use case; the exception blocks the aggregate's
invariant. Two layers of defense.

## Shape of the base classes

`DomainException` is abstract and is the **only** class allowed to extend
`RuntimeException`. The four typed ones extend `DomainException` and add no state of
their own. `errorCode` (`UPPER_SNAKE_CASE`, e.g. `ORDER_OUT_OF_STOCK`) is `private
final` on the base, exposed via `errorCode()`, and required in every constructor. The
ones that wrap a cause (`BusinessRuleViolationException`, `ConflictException`) carry a
constructor with `Throwable cause`. Source code lives as a generated exemplar in
bootstrap, not in this rule.

The four are **not** `final`: they stay the mandatory family vocabulary — exactly four
families, never a fifth invented ad hoc — but each family may be narrowed by a named
subclass that adds no state, only a fixed `errorCode` given a real type instead of a
string every caller has to spell correctly (e.g. `OrderAlreadyPaidException extends
ConflictException`). Entry bar, so this doesn't quietly become "any exception name
goes": a named subclass only when the `errorCode` already repeats across two or more
call sites, or a caller needs to `catch` it specifically rather than branch on
`errorCode()`. A first occurrence stays the plain typed family with a string
`errorCode`; `domain-modeling`'s own step decides when a repeat earns the subclass.

## Prohibition — never generic

Forbidden to throw `RuntimeException`, `Exception`, `IllegalArgumentException`, or any
generic JDK exception, in any layer. If none of the four fits, a new subclass is
missing.

Wrapping is allowed: catch a library exception and rethrow it as the `cause` of the
typed one, preserving the stack trace.

```java
// WRONG
throw new IllegalArgumentException("id must not be null");

// RIGHT
throw new ValidationException("ORDER_ID_REQUIRED", "order id is required");
catch (DataIntegrityViolationException e) { throw new ConflictException("ORDER_ALREADY_EXISTS", "Order already exists", e); }
```

## Logging and verification

Level semantics (what `ERROR`, `WARN`, and `INFO` mean) are
`@.claude/rules/logging.md`'s call, not this file's — this section only fixes what's
specific to an exception once it reaches the log:

- Typed family: logged at the level `logging.md` assigns an expected business flow
  (`WARN`). No stack trace, `errorCode` as its own field.
- Unmapped: logged at the level `logging.md` assigns a bug (`ERROR`), with the full
  stack trace.

Never the real message or stack trace in the response to the caller — only in the log.

Verification: a contract test that triggers the case and asserts the thrown type +
`errorCode`.
