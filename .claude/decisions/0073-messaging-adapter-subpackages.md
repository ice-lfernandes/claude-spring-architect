# 0073 · The messaging adapter is subdivided like the persistence one, by flow instead of by aggregate

- **Date:** 2026-09-28
- **Scenario:** lessons-learned-014 § 14 — `@.claude/rules/persistence.md` § Boundary mandates
  one subpackage per aggregate, its exemplars repeat it in a `PACKAGE:` header and carry the
  directory in every file marker, and a real feature produced `persistence/{account,customer,
  idempotency,outbox}/` against a flat `messaging/` holding six classes. Four of those six were
  the outbox's — the same concern that got its own subpackage on the persistence side.
- **Decision:** No new piece. The twin paragraph in `@.claude/rules/messaging.md` § Boundary,
  the `PACKAGE:` header and directory-carrying markers in the three messaging exemplars, and the
  two outbox subpackages named as a pair where the exemplars already cross-reference.
- **State:** approved by Lucas Fernandes, on 2026-09-28.

## The unit is the flow, not the aggregate

Mirroring persistence literally would give `…messaging.customer`, `…messaging.account`, and it
breaks on the first event that belongs to no single aggregate — the relay and the outbox belong
to none. What the messaging adapter actually holds is **flows**: a payload record plus the
producer that writes it, or a consumer plus its dedupe. One event routinely crosses aggregates,
and two events of one aggregate share nothing but their source.

So: one subpackage per flow (`…messaging.orderconfirmed`), plus one per piece of shared
transport infrastructure, `…messaging.outbox` first among them. Same two riders persistence
carries — the `test/` tree mirrors the subpackages, and package-private stays the default, which
is what makes the split load-bearing instead of cosmetic.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | One feature, one concern, organized two ways, because two template sets were written to different standards | 9 (create nothing) |
| — unit | Event flow plus shared transport infrastructure | Per aggregate (breaks on the outbox and on any cross-aggregate event); per direction (`adapter.in`/`adapter.out` already exists and the observed flat package sat inside one direction) |
| — enforcement | **Prose and exemplars only. No ArchUnit rule.** | The rule form, declined explicitly — see below |
| 9 — integration | `persistence.md` owns its half and `messaging.md` owns this one; the exemplars cross-reference by name | A shared rule file, which would give one norm two owners |
| 8 — destination | Both — the rule travels with `rules/`, the exemplars with their skill | — |

## The enforcement question, and the answer given

The ArchUnit form was on the table and is the only one a hand-written class cannot miss: no
class may reside directly in the package ending `persistence` or `messaging`, `package-info`
excepted. It needs no blueprint hook — `resideInAPackage("..messaging")` without a trailing `..`
matches the adapter root exactly, and both `adapter.out.messaging` and
`infrastructure.messaging` end the same way.

**It was declined; the convention ships as prose and exemplars.** Recorded plainly because the
lessons-learned entry's own conclusion is that *a convention that lives only in the template of
one skill is a convention for that skill only* — persistence's split survives on the strength of
a comment in an exemplar, and that is precisely why the messaging half had none. The two halves
are now written to the same standard, which removes the asymmetry; nothing mechanical checks
either. If a later run produces a flat adapter package again, the ArchUnit rule is the next
step and this record is where it starts.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Rule paragraph + `PACKAGE:` headers + directory markers, no ArchUnit | 7 | **Approved** |
| 2 | The same plus one ArchUnit rule covering both adapters | 9 | Not taken — declined at the interview |
| 3 | Mirror persistence literally, per aggregate | 4 | Rejected — the outbox, the relay and any cross-aggregate event belong to no aggregate |
| 4 | Create nothing | 2 | Rejected — the asymmetry is in the shipped templates and reproduces on every run |

Option 1 loses criterion 4 (persuasion where a guarantee was available) and part of criterion 9
is not in play at all, since nothing executes.

## References

| Claim | Source |
|---|---|
| Persistence mandates one subpackage per aggregate, including for non-aggregates | `@.claude/rules/persistence.md` § Boundary |
| Its exemplars repeat it where the executor reads | `persistence-architect/templates/{RepositoryAdapter,IdempotencyKeyStore,OutboxEventStore}.java.example` |
| `messaging.md` § Boundary's only packaging statement was about `@Configuration` placement | `@.claude/rules/messaging.md` § Boundary |
| `ArchitectureTest.java` constrains what resides in each adapter, never how it is subdivided | `test-architect/templates/ArchitectureTest.java.example` |
| A rule has one owning file; others cite it | `@CLAUDE.md` invariant 2 |

## Propagation

| File | Change |
|---|---|
| `@.claude/rules/messaging.md` § Boundary | the subpackage paragraph and the package-private one, twins of persistence's |
| `messaging-architect/templates/KafkaProducerAdapter.java.example` | `PACKAGE:` header; `orderconfirmed/` markers; the two `package` declarations |
| `messaging-architect/templates/KafkaConsumerAdapter.java.example` | the same |
| `messaging-architect/templates/OutboxRelayPublisher.java.example` | `PACKAGE:` header naming the pair; `outbox/` and `orderconfirmed/` markers; four `package` declarations; the elided-classes note says which subpackage each lands in and why `OutboxAppender` and `TopicResolver` are the named exception to package-private |
| `persistence-architect/templates/OutboxEventStore.java.example` | its `PACKAGE:` paragraph names `…messaging.outbox` as the pair |

Goes to the generated project: **yes** — `rules/` travels whole, both skills are in
`export.skills.include`.

**Restart warning:** none.

## Verification

`claude plugin validate .claude/skills` and `java .claude/hooks/ArchHook.java schema` pass.
Nothing here executes: the check is that the three messaging exemplars now carry the same three
things the persistence ones do — a `PACKAGE:` paragraph, a directory in every adapter-local file
marker, and a `package` declaration that agrees with the marker.
