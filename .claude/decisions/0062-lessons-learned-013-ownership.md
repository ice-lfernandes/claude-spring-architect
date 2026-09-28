# 0062 · Remediation of lessons-learned-013 — the ownership half

- **Date:** 2026-09-28
- **Scenario:** the items `0060` deferred: `@.claude/lessons-learned/lessons-learned-013.md`
  §§ 4, 5, 7, 9 and the remainder of 10 — the places where ownership had a hole and the run
  fell into it, rather than a program or an exemplar being wrong.
- **Decision:** one new rule (`security.md`, personal data only) and edits to what exists:
  5 skills, 1 agent, 2 rules, 4 templates, 2 build templates, the generated project's own
  `CLAUDE.md` template. No new hook, no new mode, no new agent.
- **State:** approved by Lucas Fernandes, on 2026-09-28

## Interview

| Axis | Answer | What it eliminated |
|---|---|---|
| § 5 — where detection lives | **Design + executor + consolidation.** `use-case-design` requires a `Satisfied by` column on any impact row that adds a precondition; `/new-feature`'s consolidation stops when it is missing or vague; the executor stops and reports when the code it writes leaves an approved case unreachable through its own entry point | Executor-only detection (the spec is already approved by then, which is too late); the fixture-signature check as a fourth mechanism |
| § 9 — which rule wins | **`messaging.md` wins, and the exception is written in it.** Adapter-local wiring stays with its adapter, `@Configuration` included; the grep stays absolute, with no annotation escape hatch; the coverage exclusion follows the decision instead of the package name | The module table winning (would weaken the only boundary check the messaging adapter has); leaving the contradiction to be re-resolved by a failing grep |
| § 7 — `security.md`'s first scope | **Personal data at rest and in transit, and nothing else.** Secrets and authn/authz stay in `00-index.md`'s planned table, unwritten | Writing the full planned scope (anti-pattern 9 for two of its three subjects) |
| § 8.7 — state transition | **Yes, a step in `domain-modeling`.** A state field with more than one value owes, per value, either a method on the aggregate or a **named** backlog case — and the reidratation path is never an answer | Relying on § 5's escalation alone; an `AskUserQuestion` whose only product is a recorded answer |
| § 4 — claim strategy (answered before `0060`) | Explicit decision in `persistence-architect`, three options, written into the partial; the exemplar keeps the unlocked read as the documented single-instance case | `FOR UPDATE SKIP LOCKED` as the exemplar default; renaming the method and nothing else |
| § 10 — remainder | The persistence partial gains the same **Declared dependencies** block the messaging one got in `0060`; the executor's `pom.xml` territory was already widened there | A second widening; an `ops` write path |

## Why `security.md` is a rule (Form 4) and has no `paths`

A declarative fact with no procedure — Form 4 by § 2 of the decision matrix. It carries no
`paths` because its territory is not a package: personal data is decided wherever a payload,
a column or a message body is designed, and that is `adapter/out/messaging` in one
architecture and `infrastructure/messaging` in another, plus persistence, plus REST. A glob
per architecture would be a `derived_paths` entry per layer; a cited rule is one line in each
place that needs it. It also keeps invariant 1 satisfiable: the rule names no skill, and the
three places that cite it are skills and templates, pointing inward.

The division of labour with the two rules that were already there, so none of the three
diverges: `logging.md` § Masking candidates owns the **log line**; `value-objects.md`
§ Catalog owns **which types** are personal data; `security.md` owns **everywhere else the
same field lands** — a column, a payload, a topic.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | The five answers above, each in the file that already owns the subject, plus `security.md` as a new rule | 9 | **Approved** |
| 2 | The same, with `security.md` deferred and the personal-data question living only in `messaging-architect` | 6 | Rejected — leaves at-rest uncovered: the `jsonb` payload column and its retention have no norm, and that is half of the observed defect |
| 3 | The same, with § 5 detected only by the executor | 6 | Rejected — the question belongs where the precondition is invented, and by executor time the spec is approved and immutable |
| 4 | Create nothing; record the five as known pitfalls | 2 | Rejected — § 5 shipped a dead use case with a green build, and § 7 shipped a CPF in clear to another company |

### Option 1 (score 9)

**Motivator:** every one of the five has an observed occurrence in one run, and each lands on
a file that already owns its subject — except personal data at rest, which owned nothing,
which is why one rule is created.

**Pros.** § 5 is caught three times, each at the moment the information exists, and the last
of the three is the one that cannot be skipped: the executor sees the code. § 9 stops being a
contradiction and the coverage exclusion stops depending on a package name. § 4 turns a
Javadoc assumption into a recorded decision with its cost written next to it. § 7 gives the
field's second and third copies a norm, where only the log had one. § 8.7 closes the gap that
made § 5 invisible.

**Cons.** Three, all real. The `Satisfied by` column is prose — nothing mechanical checks that
the named case actually produces the state, and the executor's grep is a heuristic on a name
the design chose. `security.md` without `paths` is loaded only when cited, so a fourth place
that designs a payload and cites nothing is uncovered. And `domain-modeling`'s step 4b can be
answered with a named backlog case forever: it forces the statement, not the transition.

**Points cut in the rubric:** criterion 4 — three of the five are persuasion where no
mechanism was available; the coverage exclusion and the grep are the only parts that execute.

## References

| Claim | Source |
|---|---|
| A rule may not name a skill, an agent or a command | `@CLAUDE.md` invariant 1 |
| One norm, one owning file; others cite it by path | `@CLAUDE.md` invariant 2 |
| A norm written ahead of its first observed failure is decoration | `@CLAUDE.md` invariant 6's mirror; `references/decision-matrix.md` § 7, anti-pattern 9 |
| Declare `paths` only where there is identifiable territory; cross-cutting rules are cited | `@.claude/rules/00-index.md` § How a rule enters context; `references/decision-matrix.md` § 6 |
| Persistence owns the outbox table, its columns and the pacing; messaging owns the broker side and the guarantee | `@.claude/decisions/0057-lessons-learned-012-inferences.md`, item 9 |
| The executor may write `pom.xml` only for a spec-declared dependency | `agent_classes.executor` in `@.claude/schemas/extensions.json`; `@.claude/decisions/0060-lessons-learned-013-shipping-defects.md` |
| The exemplar's own Javadoc already stated the single-instance assumption and named the lease as a schema change | `persistence-architect/templates/OutboxEventStore.java.example` |

## Propagation

| File | Change |
|---|---|
| `.claude/rules/security.md` | **New.** Personal data at rest and in transit: scope statement, what counts (with the two traps — the field name need not say so, a value object does not make it safe), at rest (named columns, serialized payload columns, retention, deriving instead of storing), in transit (the question asked before serializing, the three legitimate answers, retention by the receiver, an external receiver as a boundary), three greps, the admitted exception |
| `.claude/rules/00-index.md` | `security.md` moves from planned to written; the no-`paths` reason stated; the `architecture-ddd.md` sentence kept anchored to the phrase `export`'s `body_transforms` rewrites — changing its wording shipped a dead `.claude/blueprints/` citation, which the export's residue check caught |
| `.claude/rules/messaging.md` | § Boundary gains the adapter-local wiring exception (`@Configuration` included), with both consequences spelled out; § Delivery semantics gains who-absorbs-the-duplicate (contracted / assumed / unknown) and the two distinct duplicate sources |
| `.claude/skills/use-case-design/SKILL.md` + `templates/use-case-spec.md.example` | An impact row that adds a precondition carries `Satisfied by`: an approved `UC-NNN`, this case, or a named backlog case with the consequence stated. "The tests construct the state" is not a satisfier |
| `.claude/skills/new-feature/SKILL.md` | Consolidation stops on a precondition row with no satisfier; the final report gains three findings (unreachable approved cases, dedupe delegated with no contract, personal data crossing in clear); the persistence partial is validated as six blocks |
| `.claude/agents/java-spring-boot-developer.md` | § Failure mode gains the unreachable-use-case escalation, with the grep, the report shape, and the two things it must not do (add the transition; manufacture the state in a fixture). `security.md` added to the reads for Block 2 and Block M |
| `.claude/skills/domain-modeling/SKILL.md` + `templates/domain-spec.md.example` | Step 4b: state reachability per value — a method or a named backlog case, never the reidratation path. The partial's aggregate block carries the table |
| `.claude/skills/messaging-architect/SKILL.md` + `templates/messaging-spec.md.example` | Two interview axes (personal data in the payload; who guarantees dedupe when the consumer is external), both with their sub-sections; § 2 of the partial carries the personal-data table, § 3 the dedupe-owner table; both reported at the end of the run |
| `.claude/skills/persistence-architect/SKILL.md` + `templates/persistence-spec.md.example` | The claim strategy is decided out loud among three, written into § 1; § 1 also carries personal data at rest; new § 6 · Declared dependencies; block counts updated |
| `project-bootstrap/templates/pom.parent.xml.example` · `build.gradle.parent.example` | Coverage excludes configuration classes by what they are (`**/*Config.class`, `**/*Configuration.class`) as well as by path, so the wiring exception does not move the coverage number |
| `project-bootstrap/templates/root.CLAUDE.md.example` | § Module structure states the adapter-local wiring exception and why the coverage exclusion follows it |

**Verified:** `schema` full sweep exit 0 · `claude plugin validate .claude/skills` passes ·
`export` for `clean-architecture-single-module` writes 137 files with **no** surviving dead
citation (the one this run introduced in `00-index.md` was found by that check and fixed) ·
`security.md` travels with no `paths`, as intended.

Goes to the generated project: **yes** for everything except the two `use-case-design` /
`new-feature` files, which travel as part of `export.skills.include` like the rest of the
pipeline; `security.md` travels with `export.rules` and needs no `derived_paths` entry
precisely because it has no `paths`.
