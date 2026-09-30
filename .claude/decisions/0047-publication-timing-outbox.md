# 0047 · Publication timing (publish-after-commit vs. transactional outbox) becomes a named choice in `messaging.md`, owned by `messaging-architect`

- **Date:** 2026-09-26
- **Scenario:** `lessons-learned-010.md` item 1 — the transactional outbox pattern was
  inferred by the model, `messaging.md` doesn't fix the relation between publication and
  the transaction, and `messaging-architect`'s own template assumes the opposite form.
- **Decision:** Form 4 (edit `@.claude/rules/messaging.md` § Delivery semantics) + Form 1
  (edit `messaging-architect/SKILL.md` step 3 and `domain-modeling/SKILL.md` step 3) +
  invariant-3 templates (`OutboxRelayPublisher.java.example`, outbox block in
  `application-kafka.yml.example`) + two fixtures (`UC-106` one line, new
  `UC-113-settle-merchant-payout`).
- **State:** approved by Lucas Fernandes, on 2026-09-26 — option 1, on branch
  `feat/messaging-publication-timing`.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Real, documented: `25-mensageria.md` of UC-003 had to record a divergence **against its own skill's exemplar**, in prose | "create nothing" (anti-pattern 9 does not apply) |
| 2 — trigger | Touching messaging adapter files (rule) + pipeline step 5 (skill) | 3, 5, 6 |
| 5 — nature | Both: a declarative fact (which forms exist, criterion to choose) **and** a step (asking, then writing the partial) | Neither 4 nor 1 alone — the answer is both, each owning its half |
| 8 — destination | Both: `messaging.md` travels via step 6.6, `messaging-architect` via step 6.7 | — (forces the decision record, § 9 level 2) |
| 9 — integration | `messaging.md` already owns § Delivery semantics; a second file about outbox would collide | A new `rules/outbox.md` (option 3) |
| Scope (user) | Rule + interview axis + template, all three | Partial-scope options |
| Forms (user) | Both forms stay, with a written criterion | "fix a single form" options |
| Owner (user) | Both skills, with distinct roles: `domain-modeling` records whether the event is critical, `messaging-architect` decides the form | Option 2 (`messaging-architect` alone) |
| Default (user) | `publish-after-commit` is the default; outbox when the event can't be lost between commit and publish | Keeps current templates and UC-106 valid |
| Template depth (user) | Relay + port + schema requirement as a comment; the `outbox_events` table and its store stay with item 2 | An invasion of `persistence-architect/templates/` |
| Fixtures (user) | One line of reason in UC-106, plus a **new** example use case carrying the outbox pattern | "leave UC-106 as is" |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `messaging.md` § Publication timing + producer axis in `messaging-architect` + durability row in `domain-modeling` + `OutboxRelayPublisher.java.example` + UC-113 fixture | 9 | **Approved** |
| 2 | Same, but the form choice lives only in `messaging-architect` step 3 | 7 | Rejected — `domain-modeling` already interviews about the event and already writes the Events block; without a durability record there, the producer axis has to re-derive from prose what the domain pass already knew |
| 3 | New rule file `.claude/rules/outbox.md` | 3 | Rejected — invariant 2: `messaging.md` owns delivery semantics; a second file about when the publish happens splits one theme across two owners |
| 4 | Criterion written only into the skill, rule untouched | 4 | Rejected — invariant 2 in the other direction: *which forms exist and when each applies* is a declarative fact with a territory (`**/messaging/**`), so it belongs to the rule, which auto-loads for anyone touching an adapter without invoking any skill |

### Option 1 — rule + two skills + template (score 9)

**Motivator:** axes 5 and 9. The gap is two-sided: the *fact* (two publication forms, one
criterion) has no owner, and the *step* that applies it has no interview axis. Writing
only one half leaves the other repeating item 1's failure.

**Pros:**

- `messaging.md` § Delivery semantics today fixes the delivery *guarantee* and leaves the
  publication *moment* open — and the moment is what decides whether the project gains a
  table, an outbound port, and a `@Scheduled` component. The rule closes exactly that hole,
  names no skill, and stays a leaf (invariant 1).
- The producer axis is the first one in `messaging-architect` step 3's table: all five of
  today's axes are consumer-side, which is why UC-003 — a use case that only produces —
  found none applicable and wrote *"No `AskUserQuestion` this pass"* while the most
  expensive decision of the design happened two skills earlier.
- The template pair ends the exemplar bug: a skill whose template contradicts the partial
  it just wrote is a defect of the exemplar, not of the model.
- Precedent for two forms in one skill: `docker-architect` § step 2.5 already offers two
  observability backends behind one collector, each with its own `templates/`.

**Cons:**

- Three files in three skills plus the rule — the largest propagation surface of the four
  options. Mitigated by axis 8 forcing this record anyway.
- `OutboxRelayPublisher.java.example` names a port (`OutboxRelayGateway`) whose table and
  store belong to item 2 of the same lessons-learned. Until item 2 lands, the exemplar
  carries the schema requirement as a comment and points at `persistence-architect`.

**Points cut in the rubric:** criterion 7 (complete propagation) — full only once
`project-bootstrap` step 6.7's `messaging-architect` row lists the new exemplar; the row
enumerates templates by name and has gone stale before (see the `docker-architect` row's
own warning).

### Option 2 — `messaging-architect` alone (score 7)

Viable, and cheaper. Rejected on the user's answer to the owner axis: `domain-modeling`
step 3 already asks *"does the aggregate emit an event?"*, and whether losing that event
between commit and publish is acceptable is a property of the event, not of its transport.
Recorded there, the producer axis in step 5 reads it instead of re-asking.

### Option 3 — `rules/outbox.md` (score 3)

Violates invariant 2, which caps it at ≤ 4 regardless of the rest.

### Option 4 — skill only, rule untouched (score 4)

Leaves the fact unloadable for anyone editing an adapter outside the pipeline: `paths` on
`messaging.md` is what puts it in context, and a criterion living only in a skill body
never reaches that reader.

## References

| Claim | Source |
|---|---|
| A rule may not name a skill | `@CLAUDE.md` invariant 1 |
| One norm, one owning file | `@CLAUDE.md` invariant 2 |
| Boilerplate goes to `templates/*.example` | `@CLAUDE.md` invariant 3 |
| A new rule/skill is only complete once the copy step is updated | `@CLAUDE.md` invariant 9 |
| `paths` auto-loads a rule; explicit citation is the fallback | `@.claude/rules/00-index.md` § How a rule enters context |
| Two variants of one concern can live in one skill with a template each | `.claude/skills/docker-architect/SKILL.md` step 2.5 (Jaeger vs. Grafana stack) |
| The delivery guarantee is fixed, the moment isn't | `@.claude/rules/messaging.md` § Delivery semantics |
| Today's five interview axes are all consumer-side | `.claude/skills/messaging-architect/SKILL.md:113-122` |
| The producer template calls `KafkaTemplate` directly | `.claude/skills/messaging-architect/templates/KafkaProducerAdapter.java.example:48,54,56` |
| The only documented publication form in the repo | `.claude/skills/use-case-design/examples/UC-106-confirm-order/00-caso-de-uso.md:90` |
| `examples/` is not copied into the generated project | `.claude/skills/use-case-design/examples/README.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/rules/messaging.md` | New § Publication timing: the two forms, the criterion, the default |
| `.claude/skills/messaging-architect/SKILL.md` | Producer axis in step 3; step 4 split by form; Contract and partial-block table mention the form |
| `.claude/skills/messaging-architect/templates/OutboxRelayPublisher.java.example` | NEW — relay component, `OutboxRelayGateway` port, `PendingOutboxEvent`, schema requirement as a comment |
| `.claude/skills/messaging-architect/templates/KafkaProducerAdapter.java.example` | Header says which of the two forms it shows |
| `.claude/skills/messaging-architect/templates/application-kafka.yml.example` | Outbox relay block (poll interval, batch size, max attempts) |
| `.claude/skills/messaging-architect/templates/messaging-spec.md.example` | Publication-form row in block 2 |
| `.claude/skills/domain-modeling/SKILL.md` | Durability gap in step 3's table |
| `.claude/skills/domain-modeling/templates/domain-spec.md.example` | `Durability` column in § 4 · Events |
| `.claude/skills/use-case-design/examples/UC-106-confirm-order/00-caso-de-uso.md` | One line saying why publish-after-commit is enough here |
| `.claude/skills/use-case-design/examples/UC-113-settle-merchant-payout/00-caso-de-uso.md` | NEW — fixture whose event can't be lost; states the durability requirement, never the form |
| `.claude/skills/use-case-design/examples/README.md` | Thirteenth row; "Twelve" → "Thirteen"; paragraph pairing row 13 with row 6 |
| `.claude/rules/messaging.md` § How to verify | `grep` asserting the relay imports no persistence entity or repository |
| `.claude/skills/project-bootstrap/SKILL.md` step 6.7 | `messaging-architect` row lists the new exemplar |

Goes to the generated project: **yes** — `messaging.md` via step 6.6, `messaging-architect`
and `domain-modeling` (with their `templates/`) via step 6.7. `examples/` does **not**
travel: it is meta-repo pipeline fixture, per that folder's own README.
