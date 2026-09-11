---
paths:
  - "**/*.java"
status: active
---

# Code quality — SOLID and Clean Code

Covers only what no other rule already covers. Layer SRP and DIP live in
`@.claude/rules/architecture-ddd.md`; names in `@.claude/rules/naming.md`; exceptions in
`@.claude/rules/error-handling.md`. Don't repeat them here.

## SOLID — what's missing

- **OCP** — behavior that varies by case enters as a new implementation of an existing
  abstraction, not as a new `if`/`switch` inside the existing one. Applies once the axis
  has already varied once; before that it's speculation.
- **LSP** — a subtype doesn't tighten a precondition, doesn't relax a postcondition,
  doesn't throw where the base doesn't throw. An inherited method that throws
  `UnsupportedOperationException` is a violation: the hierarchy is wrong, not the
  caller.
- **ISP** — the client depends only on what it uses. An interface with methods that half
  the implementations leave empty splits in two.

## Clean Code — hard limits

- Function: one level of abstraction, ≤ 20 lines, ≤ 3 parameters, cyclomatic complexity
  ≤ 10. A boolean parameter is forbidden — that's two functions with different names.
- Class: ≤ 200 lines. Fields: `private final` by default.
- Guard clause first; no `else` after `return`; nesting ≤ 2 levels.
- No magic numbers or strings — named constant or value object.
- No `null` crossing a boundary: `Optional` on a query's return only, never on a field,
  parameter, or collection. Empty collection, never `null`.
- Mutability is an explicit decision: `record` or immutable class by default.
- A hidden side effect is a bug — a function whose name says it queries doesn't write.

## Comments

A comment explains **why**, never **what**. A comment that describes the code is a sign
of a bad name — fix the name. Forbidden: commented-out code, `TODO` without an issue
reference, Javadoc that repeats the signature.

## How to verify

A rule in markdown doesn't enforce anything. What's mechanical lives in the generated
project's build:

- **Checkstyle** (`config/checkstyle/checkstyle.xml`, `validate` phase) — length,
  parameters, complexity, nesting, magic numbers, generic `catch`. The numbers in that
  file and in this rule are the same: changing one side without the other is a bug.
- **ArchUnit** (`ArchitectureTest.java` in the main module) — boundaries, ISP, `private
  final` fields in the domain, generic `throw`, field injection
- Human review only for OCP, LSP, abstraction level, and the boolean parameter
