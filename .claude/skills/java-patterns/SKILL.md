---
name: java-patterns
description: >
  Chooses and implements a design pattern in Java/Spring from the symptom that
  justifies it. Use when the request involves a design pattern, Strategy, Factory,
  Decorator, Builder, Specification, "how to structure this class", a growing
  if/switch chain, or refactoring a class with too many responsibilities.
disable-model-invocation: true
allowed-tools: Read, Write, Edit, Glob, Grep
model: opus
---

# Java Patterns

## Why this is a skill and not a rule

A pattern is not a rule. A rule says *always*; a pattern says *when*. That's why this is
a skill and not `rules/` — it only costs context when invoked. Form 2 (manual invocation)
and not Form 1, because a catalog the model reaches on its own turns into a pattern applied
without the symptom that justifies it, which is what the entry rule below exists to stop.

**Entry rule: no pattern without a symptom observed in the code, or a force the approved
spec already names.** Applying a pattern preemptively is overengineering — a Factory for
two lines, a Strategy for a single `if`, an interface with one implementation that will
never have a second. A force named in the spec is not preemption: variants or rules the
spec enumerates, a cross-cutting concern it applies over several implementations,
construction it constrains with invariants, a predicate it uses both to decide and to
query — code written from that spec would show the symptom on its first version, and
choosing the pattern before writing it is how that first version is never written badly.
The "When **not**" column binds either way. If you can point neither to the file and line
that hurts nor to the spec line that names the force, the answer is "apply no pattern at
all".

Pinned to `opus`: choosing a pattern against a growing chain is judgment, and the pin only raises the model for the rest of the turn (`@.claude/decisions/0081-skill-model-required-per-class.md`).

## Procedure

1. Name the symptom in the actual code (`file:line`), or the force in the approved spec
   (its path and the line that names it) — never in the abstract.
2. Look up the symptom in the table. No matching row, stop — write simple code.
3. Confirm the pattern doesn't collide with the active blueprint's `packages.map`.
4. Generate from the exemplar in `templates/`, with the names from
   `@.claude/rules/naming.md`. Size limits: `@.claude/rules/code-quality.md`.
5. Record it in an ADR if the pattern changes the shape of an entire module.

## Catalog

| Symptom in code | Pattern | When **not** |
|---|---|---|
| `switch`/`if-else` over a type or enum, repeated in ≥ 2 places, growing with each new rule | Strategy | Two stable branches that have never grown |
| Construction with invariants, many steps, or possible invalid combinations | Static factory (`of`, `from`) | A record with validation in the compact constructor is already enough |
| ≥ 4 optional constructor parameters | Builder | Record + `with*`; Lombok `@Builder` on a domain object hides invariants |
| Cross-cutting behavior (cache, retry, audit) repeated across implementations | Decorator | A Spring aspect already exists (`@Transactional`, `@Cacheable`) that does the same thing |
| Business predicate duplicated between service and query | Specification | Predicate used only once |
| Output adapter with multiple technologies (JPA + external REST) behind the same port | Adapter (already the blueprint) | A single technology — the port is enough |
| Aggregate that emits ≥ 2 events consumed by several modules | Domain Events | A single consumer in the same module — direct call |

This table is the enforceable subset: seven rows, each with a symptom threshold and a
"when not" column. For a symptom that doesn't match any row above,
`references/pattern-catalog.md` covers the full GoF catalog — every pattern on
<https://refactoring.guru/design-patterns/java>, each with a shape reference in
`templates/` and this repo's verdict on the ones that don't (forbidden, or already
covered by the language/framework). It's triage, not a second menu: the entry rule below
still gates every row in it the same way it gates the seven here — an exemplar existing
is not permission to apply it without a real symptom.

## Forbidden in this repository

- **Singleton** — the Spring container already gives singleton scope; the manual version
  with `static` prevents testing and hides global state.
- **Service Locator / `ApplicationContext.getBean`** — violates the composition set in
  `@.claude/rules/architecture-ddd.md`.
- **`Impl` suffix**, always — `@.claude/rules/naming.md`: the name says the role, not
  that it's an implementation of something.
- **Pattern name standing in for a role name that exists** (`OrderStrategy` instead of
  `DiscountPolicy`, `OrderDecorator` instead of naming what it actually adds) — same
  rule, and the two curated exemplars (`Strategy.java.example`, `Decorator.java.example`)
  deliberately avoid the pattern name for exactly this reason. Not forbidden when the
  pattern name **is** the established Java role vocabulary with no better alternative —
  `Adapter`, `Repository`, `Builder`, `Factory` are accepted the same way
  `@.claude/rules/naming.md`'s own convention table already accepts
  `<Resource>RepositoryAdapter`. The test: is there a business-meaningful name this
  class could have instead? If yes, use it. If the honest answer is "no, this is just
  the JDK-idiomatic word for this role," the pattern name stays.

## Contract

**Class:** build — the territory is `skill_classes.build`'s override for this skill in
`@.claude/schemas/extensions.json`: the `src/` tree, nothing else. It sits in `build` rather
than with the design skills because it materializes Java code, and `ArchHook.java guard`
therefore also makes it unreachable from inside a design run.

**Reads** `.claude/rules/code-quality.md`, `.claude/rules/naming.md`,
`.claude/rules/architecture-ddd.md`, the active blueprint's `packages.map`, and
`references/pattern-catalog.md` when the symptom doesn't match any row of the curated
table above.

**Writes** domain and application code in the generated project. Doesn't touch the
inbound REST adapter — the package the blueprint's `packages.map` assigns to that role,
whether `adapter.in.rest` or `infrastructure.rest` — which is `rest-api-architect`'s
territory.

**Two invocation modes, one writer per mode.** Standalone — a human runs
`/java-patterns` against real code with an observed symptom; this skill is the sole
writer for that turn, as above. Injected — in a generated project, `ArchHook.java context
subagent` runs at `SubagentStart` and hands the sections listed in `subagent_context` of
`@.claude/schemas/extensions.json` (this file's `## Why this is a skill and not a rule`,
`## Catalog` and `## Forbidden in this repository`) to every agent whose `agent_classes`
entry declares `pattern_catalog: true`, before its first turn. The agent applies a matching
row while writing the feature, never as a separate invocation. In that mode this skill
writes nothing itself: the agent remains the sole owner of `src/**` (invariant 2), and the
catalog is reference material, the same role `templates/*.example` plays for the other
four pipeline skills. Renaming one of those three headings empties the injection —
`ArchHook.java schema` fails on it by name.
`@.claude/decisions/0077-pattern-catalog-injected-at-subagent-start.md`, which supersedes
0025: a skill with `disable-model-invocation: true` cannot be preloaded through an agent's
`skills:` field, so the catalog 0025 described never arrived.

**Doesn't collide with `domain-modeling`**, which also deals with domain and
application. The split is by moment, not by folder: that one produces the
`10-dominio.md` spec **before** code exists; this one refactors code that already
exists and already hurts, from a concrete `file:line`. Without a symptom in the code,
this isn't the right skill — it's the other one, or none.

**Does not** create rules. If a new limit has to always hold, it goes to
`rules/code-quality.md`, not here.
