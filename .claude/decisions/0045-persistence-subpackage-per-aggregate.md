# 0045 · Persistence adapter is organized as one subpackage per aggregate, and the norm lives in `persistence.md`

- **Date:** 2026-09-24
- **Scenario:** remediate `lessons-learned-007.md` — the `persistence-architect` exemplars
  generate a single flat package for every aggregate, and nothing documents when a
  package-private adapter class may become `public`.
- **Decision:** Option 1 — create nothing new; amend `.claude/rules/persistence.md`
  § Boundary (two bullets) and rewrite the exemplars that emit the persistence package.
- **State:** approved by the user, on 2026-09-24, with the open scope
  (`use-case-design`'s template and catalogs) included.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — concrete symptom | Real: the demo project `demo-clean-arch-single-module` arrived with 13 classes of 3 aggregates flat in `infrastructure/persistence/`, and needed a manual refactor. Gap 2 (`cannot find symbol: class CustomerRepositoryJpaAdapter`) is its mechanical consequence | Anti-pattern 9 (built on anticipation) does not apply |
| 4 — territory | Persistence adapter only. REST and messaging are future candidates with no norm today | Rule in `architecture-ddd.md` § Adapters; new cross-cutting rule |
| 5 — nature | Declarative fact ("the adapter of each aggregate lives in its own subpackage"), not a sequence of steps | Forms 1, 2, 3 |
| 7 — mandatoriness | May be verified in review. No ArchUnit rule, no hook | Option 3 below |
| 8 — destination | Both: `persistence.md` is copied at step 6.6, and `persistence-architect` at step 6.7 | — (it makes the level-2 record mandatory) |
| 9 — integration | `persistence.md` § Boundary already owns "where the adapter lives" and already has the `**/adapter/out/persistence/**` and `**/infrastructure/persistence/**` globs, which match subpackages | Option 2 below — a second owner for the same territory |
| Visibility (axis 5 applied to gap 2) | Also in `persistence.md` § Boundary, next to the norm that creates the need | `code-quality.md` |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Amend `rules/persistence.md` § Boundary + rewrite `persistence-architect` exemplars | 9 | **Approved** |
| 2 | New rule `rules/package-layout.md` | 3 | Rejected — second owner over the territory `persistence.md` already covers; strains invariant 2 |
| 3 | Option 1 **plus** an ArchUnit rule in `ArchitectureTest.java.example` | 8 | Rejected by axis 7 — the user chose norm + exemplar, without build-level enforcement |
| 4 | Create nothing | 3 | Rejected — the symptom repeats on every `/init-project` with more than one aggregate |

### Option 1 — amend `rules/persistence.md` + exemplars (score 9)

**Motivator:** axes 4, 5 and 9. The norm is a declarative fact whose territory is exactly
the one `persistence.md` already declares in `paths`, and the file already owns the
"where the adapter lives" theme in § Boundary. There is nothing to create — the seventh
answer of the skill, the cheapest one.

**Pros:**

- Single owner preserved (invariant 2). No new file, no new routing, no new `00-index`
  entry.
- Zero extra context cost: it rides on globs already declared.
- Propagation into the generated project is automatic — `persistence.md` at step 6.6 and
  `persistence-architect` at step 6.7 — with no change to `project-bootstrap`.
- The exemplar and the norm change in the same commit, so the generated code and the
  written rule cannot diverge.
- Gap 2 (visibility) sits next to the norm that creates it: whoever reads why the
  subpackage exists reads, in the same section, what to do when one aggregate genuinely
  needs another's class.

**Cons:**

- Pure persuasion. Nothing fails the build when a project is generated flat — it depends
  on the model reading the rule and on review.
- `persistence.md` § Boundary grows by two more bullets in an already long section.

**Points cut in the rubric:** criterion 4 (enforcement) — an ArchUnit rule was available
and was not used, by the user's decision on axis 7. The other seven criteria hold.

### Option 2 — `rules/package-layout.md` (score 3)

A rule whose `paths` would have to repeat `persistence.md`'s globs so it loads on the
same files: two files loading together over the same territory, with the "where the
adapter lives" theme split between them. Strains invariant 2, which caps the score at
≤ 4 (rubric § 8). It also costs an `00-index.md` entry and a line in step 6.6 for a norm
of two bullets.

### Option 3 — Option 1 plus an ArchUnit rule (score 8)

Technically viable and it is the form invariant 6 asks for when a rule must always hold.
It ties with Option 1 on the rubric — it gains criterion 4 (enforcement) and loses
criterion 5 (maintenance) — and axis 7 is what breaks the tie.

Limitation that is worth recording: ArchUnit cannot express "one subpackage per
aggregate", because it does not know the list of aggregates. What it can express is the
proxy "no class sits directly in the persistence package root, every class sits in a
subpackage of it" — which needs an exception for `package-info` and for any class that is
genuinely shared between aggregates. Partial enforcement of a norm, plus a maintained
exception list.

### Option 4 — create nothing (score 3)

Loses criterion 1 (form fit): axis 1 recorded a real, repeating symptom, so the § 2
table does match a row. The cost of not acting is one manual refactor of 13+ files per
generated project with more than one aggregate, plus a compile error with no rule to
point the way out.

## References

| Claim | Source |
|---|---|
| A declarative fact with an identifiable territory is Form 4, and an existing owner is amended rather than duplicated | `references/decision-matrix.md` § 2 and § 6; `@CLAUDE.md` invariants 1 and 2 |
| Violating an invariant caps an option at ≤ 4 | `references/decision-matrix.md` § 8 |
| A norm that must always hold is a hook or a test, not prose | `@CLAUDE.md` invariant 6 |
| A rule and a development skill both reach the generated project, at steps 6.6 and 6.7 | `@CLAUDE.md` invariant 9; `project-bootstrap/SKILL.md` steps 6.6 and 6.7 |
| The ArchUnit glob `..adapter.out.persistence..` already tolerates subpackages | `test-architect/templates/ArchitectureTest.java.example:122` |
| `persistence.md`'s globs (`**/adapter/out/persistence/**`) already match subpackages | `.claude/rules/persistence.md:3-4` |
| The exemplars generate a flat package today | `persistence-architect/templates/RepositoryAdapter.java.example:19`, `JpaEntity.java.example:28`, `SpringDataRepository.java.example:9`, `IdempotencyKeyStore.java.example:90` |

## Propagation

| File | Change |
|---|---|
| `.claude/rules/persistence.md` | § Boundary: bullet on one subpackage per aggregate, mirrored in `test/`; bullet on visibility (package-private default, `public` only for the class another aggregate consumes, prefer the port over the concrete adapter) |
| `.claude/skills/persistence-architect/templates/RepositoryAdapter.java.example` | `package …persistence.user;` + header line stating the norm |
| `.claude/skills/persistence-architect/templates/JpaEntity.java.example` | same |
| `.claude/skills/persistence-architect/templates/SpringDataRepository.java.example` | same, including the JPQL constructor expression's fully-qualified name |
| `.claude/skills/persistence-architect/templates/IdempotencyKeyStore.java.example` | `package …persistence.idempotency;` |
| `.claude/skills/persistence-architect/templates/persistence-spec.md.example` | the "entity `UserEntity` in `adapter/out/persistence`" line becomes the subpackage |
| `.claude/skills/test-architect/templates/PersistenceIT.java.example` | test package mirrors the main subpackage |
| `.claude/skills/test-architect/templates/ArchitectureTest.java.example` | comment at line 122 recording that the two-dot glob tolerates subpackages on purpose |
| `.claude/agents/java-spring-boot-developer.md` | the write territory `[persistence]/<aggregate>/**`, and the Block 2 validation paragraph (subpackage, package-private, cross-aggregate fixture via the port) |
| `.claude/skills/use-case-design/templates/use-case-spec.md.example` | three persistence rows and checklist item 9 carry the aggregate subpackage; note under the table stating the persistence path has one extra level whatever the blueprint calls the layer |
| `.claude/skills/use-case-design/examples/UC-101,104,106,107,108,109/00-caso-de-uso.md` | persistence rows and checklist items carry the subpackage: `order/`, `loanapplication/`, `cancellationaudit/` |
| `.claude/skills/java-patterns/templates/Decorator.java.example` | `package …persistence.order;` — a decorator over an outbound port lives in the decorated aggregate's subpackage, which is what lets it take the package-private adapter as its delegate |

Goes to the generated project: **yes** — `persistence.md` via step 6.6,
`persistence-architect`, `test-architect` and `use-case-design` via step 6.7. No change
needed in `project-bootstrap/SKILL.md`: all of them are already in those copy lists, and
templates travel with their skill.

Open scope, decided at approval: `use-case-design`'s spec template and its catalogs were
**included**. They are illustrative file catalogs, not emitted code, but they are what
`use-case-design` copies from when it writes a real `00-caso-de-uso.md`, so a flat path
there reintroduces the layout the rule forbids.

Not done, and deliberately: `UC-102`, `UC-103`, `UC-105`, `UC-110`, `UC-111` and `UC-112`
declare no persistence file, so nothing in them changed.
