# 0070 · A partial may defer what a norm requires, but not without leaving an owner

- **Date:** 2026-09-28
- **Scenario:** lessons-learned-014 § 9 — `20-persistencia.md` § 1 decided a 7-day retention for
  `outbox_events`, the migration carried a commented-out `DELETE` deferring the prune to "its own
  scheduled job", `application.yml` deliberately left `prune-after: P7D` out, and **no backlog
  row, no use case and no checklist item owns that job.** The outbox payload carries a CPF, so
  this is `@.claude/rules/security.md` § At rest's own example.
- **Decision:** No new piece. A `## Deferred` block the partials already half-write becomes
  explicit, and consolidation gates it: a deferred item a norm requires must name its owner — a
  checklist item or a `BL-NN` — and consolidation is what writes the backlog row.
- **State:** approved by Lucas Fernandes, on 2026-09-28.

## What was already right, and what it left open

`persistence-architect` step 4 is not silent on this. It already says: *"Either the partial
designs the pruning pass in the same run, or § 1 records the retention as a decision and the
property stays out of `application.yml` until its reader exists."* The run took the second
branch, correctly, and the branch has no ending — recording a decision is not the same as
leaving someone holding it.

`@.claude/rules/security.md` § At rest is unambiguous about what that costs: *"Personal data kept
'until someone prunes it' is kept forever. A store whose retention window is a property nobody
reads has no retention."*

So the defect is not the deferral. **It is that deferring is currently free.** The general shape:
a partial writes "out of scope for this run" about something a norm makes mandatory, the spec
consolidates, the run ends, and the obligation exists only in a sentence inside a frozen folder.

Retention is the observed instance. Deferred masking, a deferred index, a reconciliation job
named but not designed — all the same shape, which is why the rule is written generally and not
about `prune-after`.

## Why this lands now and not before

The gate needs something to point at. Until `0069-backlog-identifier.md` a backlog row had no
identifier, so "name the owner" would have had exactly the failure § 7 records: two reports
inventing a `UC` number because the sentence needed a name. `BL-NN` is what makes this
enforceable, and this decision is the second half of that one.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | A retention window with a migration placeholder, a property deliberately absent, and no owner anywhere | 9 (create nothing) |
| — scope | General: anything a partial defers that a norm requires | A rule about `prune-after` alone, which the next deferral of a different shape would walk past |
| — owner form | A checklist item **or** a `BL-NN` — the same pair `Satisfied by` already uses, and they mean different things: the checklist says this run does it, `BL-NN` says a later one does and the spec states what is untrue until then | Either alone |
| 9 — integration | Consolidation writes the `BACKLOG.md` row and assigns the `BL-NN`; the partial declares the deferral and the intended owner | The partial writing to `BACKLOG.md` itself — two writers on one file, two concurrent `BL-NN` assignments in one run |
| 7 — mandatoriness | The check runs where every run passes, same as `Satisfied by` and the dependency lists | Forms 7 · 8 |
| 8 — destination | Both | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `## Deferred` in the partials + consolidation gate + consolidation writes the row | 8 | **Approved** |
| 2 | The same, but the partial writes its own `BACKLOG.md` row | 6 | Rejected — `use-case-design` owns that file and the `BL-NN` sequence; two writers in one run collide on the next number |
| 3 | Narrow rule about the retention window only | 5 | Rejected — the next deferral of another shape reproduces the gap; the general form costs the same sentence |
| 4 | Create nothing — the existing "either/or" is enough | 3 | Rejected — it is what produced the finding: the second branch has no ending |

### Option 1 (score 8)

**Shape.**

- **`## Deferred` in a partial** — one row per item the partial decided not to do in this run,
  with: what was decided, what is missing, the norm that requires it (by path), and the
  **intended owner**: `checklist` or `backlog`. A partial with nothing deferred writes `none`,
  and — the same distinction `0068` drew for the dependency lists — **absence is not `none`**.
- **Consolidation gates it:** a deferred row whose norm makes the item mandatory must resolve to
  an owner. `checklist` → the item is added to the spec's checklist and the run implements it.
  `backlog` → consolidation appends the row to `docs/use-cases/BACKLOG.md`, assigns the `BL-NN`,
  and writes it back into the spec's `## Out of scope` with the consequence stated: what the
  project does **not** guarantee until that row ships. Unresolved, the pipeline stops and asks —
  it is not filled in by inference.
- **Consolidation is the only writer** of the backlog row, which keeps `BL-NN` assignment in one
  place per run. The partial names the intent; the identifier is issued once.
- **The final report repeats the deferred items with their owners**, next to the findings it
  already carries.

**Pros:** the branch that exists specifically to *allow* deferring gains the one thing that makes
it honest. General enough to cover the next shape, cheap enough to be one block per partial and
one bullet in consolidation. The consequence sentence lands where a reader of the spec sees it,
not in a run report nobody reopens.

**Cons:** persuasion, like `0068` — a model that skips consolidation's bullets skips this too.
And a `## Deferred` block that says `none` on every partial of a simple case is ceremony the
reader pays for. Criterion 4, and a point of criterion 5.

**Points cut in the rubric:** § 8 criterion 4 (persuasion where a hook was possible) and part of
criterion 5 (one more mandatory block per partial).

### Option 2 — the partial writes the row (score 6)

Shorter by one hop, and it puts a second writer on `BACKLOG.md` inside a single run. Two
partials deferring in the same run would each read the file, each compute "highest plus one",
and each write the same `BL-NN`. Rejected on invariant 2.

### Option 3 — retention only (score 5)

A rule naming `app.outbox.prune-after`. Verifiable and narrow, and it answers the instance
instead of the shape. The lessons-learned entry itself generalises — *"Anything a partial writes
under pending that a rule requires must leave the run with an owner"* — and the general sentence
costs no more than the specific one.

## References

| Claim | Source |
|---|---|
| The retention branch that defers exists already, and has no ending | `.claude/skills/persistence-architect/SKILL.md` step 4 |
| A retention window nobody reads is no retention | `@.claude/rules/security.md` § At rest |
| The outbox payload carries personal data, so this is that rule's own example | lessons-learned-014 §§ 8, 9 |
| A backlog row is citable by `BL-NN`, and consolidation can assign it | `.claude/decisions/0069-backlog-identifier.md` |
| `use-case-design` owns `BACKLOG.md` | `.claude/skills/use-case-design/SKILL.md` § Contract |
| A gate belongs where every run passes, next to `Satisfied by` | `.claude/decisions/0068-dependency-list-gate-at-consolidation.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/persistence-architect/SKILL.md` | the `## Deferred` block in the partial's block list; step 4's retention branch points at it instead of ending in "records the retention as a decision" |
| `.claude/skills/messaging-architect/SKILL.md` | the same block, same shape |
| `.claude/skills/new-feature/SKILL.md` | the consolidation gate; consolidation writes the `BACKLOG.md` row and assigns the `BL-NN`; the final report lists the deferred items with their owners |
| `.claude/skills/use-case-design/SKILL.md` | its ownership of `BACKLOG.md` now reads "assigns `BL-NN` on append, and so does `/new-feature`'s consolidation when a partial defers" — one sequence, two writers named, never a third |

Goes to the generated project: **yes** — all three skills travel.

**Restart warning:** none.

## Found while propagating

`messaging-architect`'s own block table listed six rows while its step 8 said "seven blocks":
the `Declared dependencies` row — the subject of `0068` — had never been added to it. Fixed in
the same pass, since it is the same table and the same kind of drift: the count in the prose and
the rows in the table are two statements of one fact, and nothing compares them.

The template `messaging-spec.md.example` now carries `## 8 · Deferred` with the outbox retention
as its worked row, which is the case that produced this decision.

## Verification

`claude plugin validate .claude/skills` and `java .claude/hooks/ArchHook.java schema` pass.
Nothing here is executable: the gate is prose in consolidation, in the same bullet list as
`Satisfied by` and the dependency lists, and the check on it is that all three now read the same
way — a value, an owner, and "absence is not `none`".
