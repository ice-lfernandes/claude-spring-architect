# 0049 · `messaging-architect` moves ahead of `persistence-architect` in the pipeline, and `25-mensageria.md` gains a mandatory `Schema requirements` block

- **Date:** 2026-09-26
- **Scenario:** `lessons-learned-010.md` item 3 — messaging runs at step 5, after
  persistence at step 4, so the UC-003 run discovered `attempts`, `last_error`,
  `dead_lettered` and a changed partial index *after* `20-persistencia.md` was written.
  Persistence ran twice, and `25-mensageria.md` § 2 invented the hand-off in prose. It is
  the same failure mode the orchestrator's own § *Why REST runs before persistence* note
  exists to prevent.
- **Decision:** Form 1 (edit `new-feature/SKILL.md`, `messaging-architect/SKILL.md`,
  `persistence-architect/SKILL.md`) + the exemplar partial
  (`messaging-spec.md.example` gains § 6).
- **State:** approved by Lucas Fernandes, on 2026-09-26 — option 1, on branch
  `feat/messaging-before-persistence`.

## What records 0047 and 0048 already did, and what they left open

Checked before proposing, because both touch the same three skills:

| Already done | Where | Still open |
|---|---|---|
| `persistence-architect` step 1 reads `25-mensageria.md` **when the file exists** | `persistence-architect/SKILL.md:98-103` | The read is optional and its own text accepts the second pass as legitimate: *"`messaging-architect` may legitimately run after this skill, and then the requirement arrives as a second pass on this same folder"* |
| Step 2's survey greps for `outbox_events`; step 4b models it once | `persistence-architect/SKILL.md:116,153-175` | Nothing makes the requirement arrive *before* step 4 runs |
| The outbox requirement has a named addressee instead of a hope | `messaging-architect/SKILL.md:141-152` | The requirement lives in § 2's prose, and the dedupe table in § 3's; there is no list, so there is no single thing for persistence to read |
| Form B is a named choice with a criterion | `@.claude/rules/messaging.md` § Publication timing (record 0047) | Order untouched — 0048 § Interview says so explicitly: *"without reordering the pipeline — the reorder is item 3's decision, not this one's"* |

So item 3 is not fixed. 0048 formalized the *content* of one hand-off; item 3 is about
*when* the hand-off happens and about every other schema requirement messaging produces.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Real, documented: `20-persistencia.md` revised after the fact, and `UC-003-spec.md`'s partial table carries the scar (`revised once, after 25-mensageria.md flagged the relay's own port/schema need`) | "create nothing" |
| 2 — trigger | Pipeline sequencing (orchestrator) + partial shape (layer skill) | 3, 4, 5, 6 — no external system, no declarative fact without a territory |
| 5 — nature | A step, in three skills | 4 — "who runs before whom" is procedure, and a rule may not name a skill (invariant 1) |
| 8 — destination | Both: all three skills travel via step 6.7 | — (forces this record, § 3.5 level 3) |
| 9 — integration | `new-feature` owns the order; `messaging-architect` owns its partial's blocks; `persistence-architect` owns the tables. No new owner is created | Any option adding a fourth owner for schema requirements |
| Scope (user) | All three lessons: reorder, block, precedence note | Partial-scope options 2 and 3 |
| Position (user) | Domain → REST → messaging → persistence → tests | Messaging immediately after domain |
| Block shape (user) | A sixth block of its own, mandatory, `none` when empty | A closing list per block, mirroring REST literally |
| Validation (user) | `new-feature` step 5 validates six blocks, not five | A block the orchestrator never checks |
| Input strength (user) | `25-mensageria.md` becomes **mandatory** input to `persistence-architect` when `10-dominio.md` § Events names external delivery | Keeping 0048's optional read as the only rule |
| Precedence (user) | A note on the existing schema row, not a second row | Splitting "who asked" from "what it looks like" into two rows |
| Exemplar (user) | `none` plus a comment naming what lands there under Form B | A filled outbox list contradicting the exemplar's own Form A |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Reorder + § 6 block, validated + mandatory input + precedence note | 9 | **Recommended** |
| 2 | Reorder only | 6 | Viable — fixes `/new-feature`, leaves manual and resumed runs with no mechanism |
| 3 | § 6 block + precedence note, order unchanged | 5 | Viable — documents the second pass instead of preventing it |
| 4 | Formalize the second persistence pass as a numbered step 4-bis | 4 | Rejected — makes the defect the design |
| 5 | Create nothing | 2 | Rejected — the symptom is measured, and the repo already fixed the identical one for REST |

### Option 1 — reorder + block + mandatory input + note (score 9)

**Motivator:** axes 1 and 9. The orchestrator's own § *Why REST runs before persistence*
states the criterion — *"the order follows who generates requirements for whom"* — and
messaging generates schema requirements exactly the way REST does. Applying a criterion
the repo already wrote costs nothing to justify.

**Pros:**

- `messaging-architect` depends only on step 2 (`10-dominio.md`), per the orchestrator's
  own *Integrates with* list. Moving it ahead of persistence is free: no dependency is
  violated, no skill gains an input it cannot have.
- After the move, `persistence-architect` step 1 reads **two** requirement lists, both
  already written — `30-rest.md` block 4 and `25-mensageria.md` § 6 — instead of one plus
  a promise. Step 4a and step 4b then fire in the same pass, which is what 4b was shaped
  for.
- The sixth block gives every messaging-born schema requirement one address. Today the
  outbox lives in § 2's prose and the dedupe table in § 3's; a reader looking for "what
  must persistence build" has to find two paragraphs and know they are the whole set.
- Mandatory input closes the manual path: `/persistence-architect` run by hand on a folder
  whose domain partial names external delivery now stops instead of designing a schema it
  will have to revise. Symmetric with the `30-rest.md` rule already in step 1.
- The precedence note ends the ambiguity the consolidation table gained when messaging
  started producing columns: the requirement may be born in `25-mensageria.md` or
  `30-rest.md`; the final form — name, type, index, migration — is always
  `20-persistencia.md`'s.

**Cons:**

- Three `SKILL.md` files plus one exemplar partial. The orchestrator's edit is the
  delicate one: step numbering appears in the *Integrates with* list, the step headings,
  the depends-on parentheses, and the consolidation heading, and a missed one leaves the
  pipeline describing two different orders.
- `docker-architect`, chained by `messaging-architect` step 9, now runs earlier in the
  pipeline. That is unrelated to schema, but it moves the Kafka image-tag handshake of
  item 6 one step further from `test-architect` — the handshake stays prose either way,
  and item 6 is the record that has to fix it.
- Design order now differs from implementation order: the consolidated spec keeps
  messaging as block 6 and the executor keeps Block M between REST and Tests. Stated in
  writing so the difference reads as deliberate.

**Points cut in the rubric:** criterion 4 (enforcement) — nothing mechanical verifies the
order. `ArchHook.java` has no notion of partial sequencing (`grep` for `25-mensageria` in
it returns nothing), and the guarantee remains the orchestrator's text plus
`persistence-architect`'s entry stop.

### Option 2 — reorder only (score 6)

Cheapest fix of the cause, and it would have prevented the UC-003 run entirely. Rejected
as incomplete on the user's scope answer: a resumed run, or `messaging-architect` invoked
by hand months later, still produces requirements with nowhere to put them. The lesson
itself says doing both is correct — the reorder avoids the second pass, the block
documents it when it is unavoidable.

### Option 3 — block and note, order unchanged (score 5)

Keeps `new-feature` untouched, which is the file with the most numbering to break.
Rejected on axis 1: the second pass is the measured symptom, and this option only makes it
tidier. The repo already refused this trade once, for REST.

### Option 4 — a numbered step 4-bis (score 4)

Writing "persistence runs again after messaging" into the pipeline would make the hand-off
explicit and auditable. Rejected: it spends a pipeline step to institutionalize rework
that a reorder removes, and it doubles the most expensive design skill in the run by
design.

### Option 5 — create nothing (score 2)

Defensible only if the second pass were cheap or rare. It is neither: `20-persistencia.md`
was edited three times in one run, and Form B is now a named form that every durable event
selects.

## References

| Claim | Source |
|---|---|
| The order follows who generates requirements for whom | `.claude/skills/new-feature/SKILL.md` § Why REST runs before persistence |
| `messaging-architect` depends only on the domain partial | `.claude/skills/new-feature/SKILL.md:65-67` (*Integrates with*, step 5 "depends on 2") |
| A requirement left in prose is a second persistence pass later | `.claude/skills/rest-api-architect/SKILL.md:191-195` |
| Persistence already reads REST's requirement list in its first pass | `.claude/skills/persistence-architect/SKILL.md:92-96` |
| The optional messaging read, and the reorder deferred to this record | `@.claude/decisions/0048-outbox-table-exemplar-persistence.md` § Interview, "`25-mensageria.md` as input" |
| Form B's table is persistence's job via step 4b | `.claude/skills/persistence-architect/SKILL.md:153-175` |
| An empty block is written as "none", never deleted | `.claude/skills/messaging-architect/SKILL.md` § What the partial contains |
| A rule may not name a skill | `@CLAUDE.md` invariant 1 |
| A new skill file is only complete once the copy step is updated | `@CLAUDE.md` invariant 9 |
| Nothing mechanical enforces partial order | `.claude/hooks/ArchHook.java` — no reference to any partial filename |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/new-feature/SKILL.md` | *Integrates with*: messaging becomes 4 (conditional, depends on 2), persistence 5 (depends on 2,3,4), tests 6 (depends on 1,2,3,4,5). § *Why REST runs before persistence* renamed and extended to cover messaging. Step headings 4 and 5 swap, with their depends-on. Step 5 (persistence) states it reads both requirement lists. Step 4 (messaging) validates **six** blocks. Consolidation heading's numbering. Precedence table: note on the schema row naming where a requirement is born and who fixes its form. One line saying design order ≠ implementation order (spec block 6, executor Block M unchanged) |
| `.claude/skills/messaging-architect/SKILL.md` | Step 4a and step 5a point their requirement at the new § 6 instead of "the partial". Step 8 says six blocks. § What the partial contains gains the § 6 row. Step 10's report names the block. Boundary table: this skill now acts before `persistence-architect`. Contract: the § 6 list is what `persistence-architect` reads |
| `.claude/skills/messaging-architect/templates/messaging-spec.md.example` | New § 6 · Schema requirements — `none` for this Form A case, plus the comment naming what lands there under Form B (outbox columns, dedupe table) |
| `.claude/skills/persistence-architect/SKILL.md` | Entry rule: the two conditional stops (`30-rest.md`, `25-mensageria.md`) stated together. Step 1: `25-mensageria.md` becomes mandatory input when `10-dominio.md` § Events names external delivery — stop and say to run `/messaging-architect` first; the optional-read wording and the "legitimate second pass" sentence go, replaced by the stop. Step 4b sources its column list from § 6. Boundary table reordered: both requirement-producing skills act before this one. Contract: both partials named as conditional-mandatory input |

Goes to the generated project: **yes** — all three skills travel via step 6.7, which
copies whole directories. No new file is created, so no row of step 6.7 grows and no rule
changes; step 6.6 is untouched. `examples/` fixtures carry only `00-caso-de-uso.md` and
need nothing.
