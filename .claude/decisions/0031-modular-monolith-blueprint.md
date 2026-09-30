# 0031 · Design of the `modular-monolith` blueprint (Spring Modulith)

- **Date:** 2026-09-11
- **Scenario:** Write `modular-monolith.yaml` from four references (Spring Modulith's
  project page, Baeldung, the spring.io "Introducing Spring Modulith" post, and a
  paywalled Stackademic article). Unlike `hexagonal`/`clean-architecture-*`/`layered`,
  this architecture's defining concern — bounded-context isolation — sits at a
  different axis than the domain/application/adapter layering `rules/architecture-ddd.md`
  already governs, and its module names are feature data the blueprint can't know in
  advance.
- **Decision:** `layout: single-module`, one bootstrap-created module (`shared`, a
  Shared Kernel) providing the literal skeleton `packages.map` needs, full DDD layering
  nested inside every module's `internal` package (user's explicit choice — see
  Interview below), cross-module isolation delegated entirely to Spring Modulith's own
  `ApplicationModules.verify()` (`ModularityTests.java`, written by `project-bootstrap`
  step 4.7, not by `archunit-installer`).
- **Status:** approved by Lucas Fernandes, on 2026-09-11

---

## Interview

Asked directly, before writing anything: inside each bounded-context module, keep full
domain/application/adapter DDD layering, or just Spring Modulith's own two-tier
`module`/`module.internal` split (what all three real sources actually show)?

**Answer: DDD-nested per module.** No four sources describe this — it's this
blueprint's own addition, matching the rigor of the other three blueprints instead of
Modulith's simpler default. Chosen specifically to avoid a documented exception to
`rules/architecture-ddd.md`'s "doesn't change with the blueprint" line, at the cost of
inventing the `internal` sub-shape myself (resolved below: same `hexagonal` vocabulary,
nested one level deeper).

---

## Three problems this design had to solve, in order

### 1 — Two independent boundary concerns, two independent mechanisms

A modular monolith has to enforce **cross-module** isolation (order can't reach
inventory's `internal`) and, inside each module, the same **intra-module** layering
every other blueprint here enforces (domain → application → adapter). These are not
the same check:

- Cross-module: no fixed `from`/`to` exists to declare — module names are feature data.
  `dependency_rules.forbidden`'s schema (`{from, to[], reason}` against a static role
  list) structurally cannot express "no module may reach any *other* module's
  `internal`" without enumerating modules that don't exist yet.
- Intra-module: same shape as `hexagonal`'s three rules, just repeated once per module
  — expressible with ArchUnit's own `..` wildcard package matcher
  (`..internal.domain..`), which matches at any nesting depth regardless of which
  module precedes it.

**Resolution:** `dependency_rules.forbidden` in this blueprint only covers the
intra-module direction (three rules, textually identical in shape to `hexagonal`'s).
Cross-module isolation is Spring Modulith's own job —
`ApplicationModules.of(Application.class).verify()`, one test class
(`ModularityTests.java`), written once, that re-derives the module list from the
package tree on every run. It needs no blueprint-declared module names at all, which is
exactly why it's the right tool here and `archunit-installer` isn't.

### 2 — Where does `ModularityTests.java` get written, and when?

`archunit-installer` (D30) exists specifically because ArchUnit's `should()` rules fail
either vacuously or by breaking the build over zero business classes — that's why
setup mode waits until real code exists. `ApplicationModules.verify()` doesn't have
that problem: zero modules means zero structural violations, a genuinely meaningful
green, not a vacuous one. It starts enforcing correctly the moment the first bounded
context appears.

**Resolution:** `project-bootstrap` step 4.7 writes it directly, gated on
`features.spring-modulith`, the same way it already writes `db/migration/` for
`flyway` and merges `application-actuator.yml` for `actuator` — a new row in an
existing, generic table, not a special case for this blueprint's `id`. New exemplar:
`templates/features/spring-modulith/ModularityTests.java.example`.

### 3 — `packages.map` assumes a fixed, literal, creatable set of roles. This blueprint's real roles repeat once per module, and module names don't exist yet.

Every other blueprint's `packages.map` describes the *only* domain/application/adapter
set the project will ever have — literal, creatable at bootstrap (step 4.7: one
`package-info.java` per role), and stable for the life of the project.
`modular-monolith` breaks both assumptions: `order.internal.domain`,
`inventory.internal.domain`, and so on are all "the same role," but under a
module-specific prefix that `project-bootstrap` writes zero business code and
therefore cannot invent.

Three options considered:

| # | Option | Verdict |
|---|---|---|
| 1 | Leave `packages.map` empty / minimal (just `config`) | Rejected — `project-bootstrap` step 6.6 requires "at least one glob matches something built in step 4.7" for each rule with `paths`; with nothing module-shaped on disk, `value-objects.md`/`api-rest.md`/`persistence.md` would have nothing to point at, silently dead from commit one |
| 2 | Invent a fake example module (e.g. `example`) purely to satisfy the check | Rejected — indistinguishable from real business code to anyone reading the generated project; violates the spirit of "bootstrap emits zero business code" even if not the letter |
| 3 | **Bootstrap one real, non-business module: `shared`, a Shared Kernel** | **Approved** |

**Why `shared` isn't business code the other blueprints' invariant forbids:** a Shared
Kernel (cross-cutting value objects/events genuinely common to more than one bounded
context) is architecture-shaped data, the same category as `domain`/`application` in
`hexagonal` — present in every real modular monolith regardless of which features get
built, unlike `order` or `inventory`. Marked `@ApplicationModule(type = OPEN)` so other
modules may reference it without tripping Modulith's own isolation check — a real,
documented Spring Modulith pattern, not one this blueprint invented.

**Consequence for the four rules whose `paths` project-bootstrap step 6.6 derives from
`packages.map`** (`api-rest.md`, `persistence.md`, `value-objects.md`,
`observability.md`): the literal, `shared`-scoped glob the map produces satisfies the
step 4.7 check today, but only covers `shared` — it would silently miss every real
module's `internal.adapter.rest`, etc. `modular-monolith.yaml` documents, in a comment
directly above `packages.map`, that step 6.6 must add a second, wildcarded glob
(`**/internal/<role>/**/*.java`) alongside the literal one for those four rules. This
is prose guidance inside the blueprint file, the same mechanism every blueprint already
uses for its naming-convention comment — not a change to `project-bootstrap`'s written
procedure, which already says the derivation follows "the same discipline
`architecture_paths` already gets," i.e., reads the blueprint's own guidance.

---

## Options evaluated for the intra-module shape

| # | Option | Verdict |
|---|---|---|
| 1 | Modulith-pure: `<module>` (public) + `<module>.internal` (flat, hidden) | Rejected by the user — closest to all 3 real sources, but is a documented exception to `rules/architecture-ddd.md`'s stated universality |
| 2 | **DDD-nested: `<module>.internal.{domain,application,adapter}`, `hexagonal`'s naming** | **Approved** — no invariant exception; the four sources don't describe this shape, so it's this blueprint's own addition, recorded here for that reason |

---

## Propagation

| File | Change | Status |
|---|---|---|
| `.claude/blueprints/modular-monolith/modular-monolith.yaml` | New file | ✅ Written |
| `.claude/blueprints/modular-monolith/references/*.md` | Four new files, one per source | ✅ Written |
| `.claude/skills/project-bootstrap/SKILL.md` § 4.7 | New `spring-modulith` feature-config row | ✅ Written |
| `.claude/skills/project-bootstrap/templates/features/spring-modulith/ModularityTests.java.example` | New exemplar | ✅ Written |
| `.claude/skills/project-bootstrap/references/dependency-catalog.md` | New `spring-modulith` row (Initializr id `modulith`, BOM resolved live) | ✅ Written |
| `.claude/blueprints/_schema.md`, `docs/05-blueprints.md` (pt+en), `README.md`, `claude-help.md`, `CONTEXT.md`, `blueprint-selection.md` | Removed `modular-monolith` from every "pending blueprint" list | ✅ Written |
| `.claude/decisions/0031-modular-monolith-blueprint.md` | New record | ✅ Written |

## Next steps (out of scope for this decision)

1. Run `/init-project --blueprint modular-monolith` once against a real target to
   confirm `shared`'s package-info.java stubs, the POM's `spring-modulith-bom` import,
   and `ModularityTests.java` all come out compilable and green.
2. First real feature: confirm `domain-modeling` correctly infers the
   `<module>.internal.domain.*` nesting for a brand-new module from this blueprint's
   naming-convention comment, without any special-casing added to that skill.
3. Confirm step 6.6's two-glob-per-rule instruction (literal + wildcard) actually gets
   applied when someone runs `/init-project` for real — this is prose guidance, not
   enforced by any hook, and prose guidance that's never been exercised is a claim, not
   a fact.
