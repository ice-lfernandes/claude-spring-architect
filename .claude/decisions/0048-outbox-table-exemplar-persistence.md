# 0048 · `outbox_events` and its store become a `persistence-architect` exemplar pair, reached by a new step 4b mirroring 4a

- **Date:** 2026-09-26
- **Scenario:** `lessons-learned-010.md` item 2 — `outbox_events` and `OutboxRelayGateway`
  are shared infrastructure with no exemplar, although the repo already has the mechanism
  for exactly this case (`idempotency_keys`, step 4a).
- **Decision:** Form 1 (edit `persistence-architect/SKILL.md` — new step 4b — and
  `messaging-architect/SKILL.md` step 4a) + invariant-3 exemplar pair
  (`OutboxEventTable.sql.example`, `OutboxEventStore.java.example`).
- **State:** approved by Lucas Fernandes, on 2026-09-26 — option 1, on branch
  `feat/outbox-table-exemplar`.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Real, documented: the UC-003 run designed `outbox_events` from zero in prose (columns, `jsonb` payload, partial index, then three retry columns in a second pass), and `OutboxRelayGateway`/`PendingOutboxEvent` were hand-written signatures | "create nothing" (anti-pattern 9 does not apply) |
| 2 — trigger | Pipeline step 4 of `persistence-architect`, when `25-mensageria.md` requires Form B | 3, 5, 6 |
| 5 — nature | A step (model the shared table once, then reuse) plus boilerplate (DDL, entity, repository, adapter) | 4 — the *fact* "one outbox per project" is already owned by `messaging.md` § Publication timing; a second rule would collide |
| 8 — destination | Both: `persistence-architect` and its `templates/` travel via step 6.7 | — (forces this record, § 9 level 2) |
| 9 — integration | `messaging-architect`'s `OutboxRelayPublisher.java.example` already declares `OutboxRelayGateway` and `PendingOutboxEvent`; `persistence.md` already owns the table | An exemplar re-declaring the port (option 3) |
| Depth (user) | Pair, not trio: `OutboxEventTable.sql.example` + `OutboxEventStore.java.example`. 4a's third file (`IdempotentExecution`) has no outbox counterpart — that role is the relay, which already lives in `messaging-architect/templates/` | The trio-shaped option |
| Port owner (user) | `messaging-architect` declares the port; the persistence exemplar only implements it, with a comment pointing at the declaring exemplar | Moving the declaration, and duplicating it |
| Write side (user) | `OutboxAppender` stays elided in the messaging exemplar; the persistence pair covers the relay's read/mark side and the table | A write-side port in the persistence exemplar |
| Pruning (user) | Commented `DELETE`, at the tail of the SQL exemplar, the same shape `IdempotencyKeyTable.sql.example` uses for cleanup | A scheduled Java component |
| Shared-table norm (user) | No new bullet in `persistence.md` — the norm stays where it is (`messaging.md` § Publication timing for the outbox; step 4a's prose for `idempotency_keys`) | A Form 4 edit |
| `25-mensageria.md` as input (user) | Optional read: step 1 reads it when the file exists, and its § 2 outbox requirement feeds step 4b. Entry rule unchanged — absent file means no outbox to model | Making it mandatory (which would reorder the pipeline — that is item 3, not this item) |
| Cross-reference (user) | `messaging-architect` step 4a names step 4b **and** both exemplars by filename | Staying generic |
| Retention (user) | Default value in the exemplar, and step 4b confirms it with the user (`AskUserQuestion`, default as first option) instead of assuming it silently | A new permanent axis in step 3's table |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Exemplar pair + step 4b + optional `25-mensageria.md` read + retention confirmation + `messaging-architect` 4a naming the pair | 9 | **Recommended** |
| 2 | `OutboxEventTable.sql.example` only; entity, repository and adapter stay prose | 5 | Viable, cheaper — leaves half of item 2 open |
| 3 | Trio, with the port re-declared in the persistence exemplar and a `OutboxPruneJob.java.example` | 5 | Rejected — invariant 2 strain: two exemplars declaring `OutboxRelayGateway` diverge on the first edit |
| 4 | Create nothing | 3 | Rejected — the symptom is documented, and the mechanism it asks for already exists for `idempotency_keys` |

### Option 1 — exemplar pair + step 4b (score 9)

**Motivator:** axes 1 and 9. `outbox_events` is shared infrastructure with the same nature
as `idempotency_keys` — one table per project, no foreign key to an aggregate, its own
subpackage — and 4a is the proven mechanism for exactly that. What is missing is the second
instance, not a new design.

**Pros:**

- Invariant 3: the DDL, entity, Spring Data interface and the `OutboxRelayGateway` adapter
  are form, not a business decision. In prose, every later messaging use case derives a
  different signature for the same concept — which is what the UC-003 run did.
- The three retry columns (`attempts`, `last_error`, `dead_lettered`) and the partial index
  are a mechanical consequence of `messaging.md` § Publication timing and § Retry and DLQ.
  In a template they come for free; in prose they arrived late, and that lateness is what
  produced item 3's second persistence pass.
- Precedent in the repo, exact: step 4a, its three exemplars, and the same "modeled once,
  reused afterward" sentence in step 2's survey.
- The port stays single-owner: `messaging-architect` declares it (it owns the relay that
  consumes it), the persistence exemplar implements it. No interface written twice.
- The optional `25-mensageria.md` read costs nothing when the file is absent, and closes
  the input gap without reordering the pipeline — the reorder is item 3's decision, not
  this one's.

**Cons:**

- Two skills' files in one change, plus the bootstrap copy row. Mitigated by axis 8 forcing
  this record anyway.
- Step 4b adds one `AskUserQuestion` on retention for every Form B use case. Bounded: it
  only fires when the outbox does not exist yet, exactly like 4a.
- `project-bootstrap` step 6.7's `persistence-architect` row enumerates exemplars by name
  and must grow by two. That row's neighbor already carries a "this row has gone stale
  once" warning.

**Points cut in the rubric:** criterion 4 (enforcement) — nothing mechanical checks that
the migration in the partial actually carries the retry columns; the guarantee is the
exemplar plus `messaging.md`'s § How to verify grep, which only covers the relay's imports.

### Option 2 — SQL only (score 5)

Cheapest real change, and it fixes the schema half: columns, types, partial index and the
commented prune all land in one file. Rejected as insufficient on axis 1: two of the three
things item 2 names are Java shapes (`OutboxRelayGateway`'s adapter and the entity/repository
pair), and those are precisely where the UC-003 run hand-wrote signatures. Half the symptom
survives.

### Option 3 — trio with the port re-declared (score 5)

Self-contained exemplars read better in isolation, and a `OutboxPruneJob.java.example` would
give the pruning requirement a home. Rejected on the user's answers to the port and pruning
axes: the interface declared in two exemplars is one edit away from divergence (invariant 2
applied to form, the same reason 4a's port lives in exactly one file), and pruning is a
scheduled `DELETE` sized by retention — a commented block in the SQL, the shape the
idempotency exemplar already uses, not a Java component with a decision in it.

### Option 4 — create nothing (score 3)

Defensible only if outbox were speculative. It isn't: Form B is a named form in
`messaging.md` since record 0047, `messaging-architect` step 4a already flags the table as a
requirement for `persistence-architect`, and today that flag points at a skill with no
exemplar to fulfill it. The gap is load-bearing, not anticipated.

## References

| Claim | Source |
|---|---|
| Boilerplate goes to `templates/*.example`, never into a skill body | `@CLAUDE.md` invariant 3 |
| One norm, one owning file | `@CLAUDE.md` invariant 2 |
| A new skill file is only complete once the copy step is updated | `@CLAUDE.md` invariant 9 |
| Shared-infrastructure table, modeled once, reused afterward | `.claude/skills/persistence-architect/SKILL.md` step 4a and step 2 |
| One outbox for the whole project; the table belongs to the persistence rule | `@.claude/rules/messaging.md` § Publication timing |
| The relay reads outbox state through an application-layer abstraction | `@.claude/rules/architecture-ddd.md` § Adapters · `@.claude/rules/messaging.md` § Publication timing |
| `OutboxRelayGateway`/`PendingOutboxEvent` are already declared, with the schema requirement as a comment | `.claude/skills/messaging-architect/templates/OutboxRelayPublisher.java.example:22-28,41-64` |
| Shared infrastructure gets its own persistence subpackage, package-private by default | `@.claude/rules/persistence.md` § Boundary |
| Cleanup as a commented `DELETE` at the tail of the SQL exemplar | `.claude/skills/persistence-architect/templates/IdempotencyKeyTable.sql.example:46-54` |
| A question with fewer than two real options fails `InputValidationError` | `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example` § Known pitfalls |
| Form B was made a named choice, with its table deferred to this item | `@.claude/decisions/0047-publication-timing-outbox.md` § Interview, "Template depth" |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/persistence-architect/templates/OutboxEventTable.sql.example` | NEW — DDL, partial index on pending rows, retry columns, commented prune with a default retention |
| `.claude/skills/persistence-architect/templates/OutboxEventStore.java.example` | NEW — entity, Spring Data interface, adapter implementing `OutboxRelayGateway`; port declaration referenced, not repeated |
| `.claude/skills/persistence-architect/SKILL.md` | Step 1 reads `25-mensageria.md` when present; step 2 survey greps for `outbox_events`; NEW step 4b, with the retention question; § Boundary gains a `messaging-architect` row; partial-block table row; Contract (reads `messaging.md` § Publication timing, and the non-collision clause) |
| `.claude/skills/messaging-architect/SKILL.md` | Step 4a names step 4b and the two exemplars, and states the port stays declared there |
| `.claude/skills/messaging-architect/templates/OutboxRelayPublisher.java.example` | Schema-requirement comment points at the pair that fulfills it |
| `.claude/skills/project-bootstrap/SKILL.md` step 6.7 | `persistence-architect` row lists the new pair, plus the "copy the whole directory" warning its neighbor already carries |

`persistence-spec.md.example` was **not** touched: it demonstrates neither the idempotency
block nor the outbox one, and the block table in `SKILL.md` is where both are declared. Adding
one without the other would make the exemplar partial the odd owner of a conditional block.

Goes to the generated project: **yes** — `persistence-architect` and `messaging-architect`
with their `templates/` via step 6.7. No rule changes, so step 6.6 is untouched;
`examples/` fixtures carry only `00-caso-de-uso.md` and need nothing.
