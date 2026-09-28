# 0050 · A component that sweeps or polls persisted state reads it through an application-layer abstraction — one bullet in `architecture-ddd.md` § Adapters

- **Date:** 2026-09-26
- **Scenario:** `lessons-learned-010.md` item 4 — the outbox relay has to read and update
  `outbox_events`, whose JPA entity lives in `infrastructure.persistence`, while the relay
  itself lives in `infrastructure.messaging`. The model spotted the sideways dependency and
  invented `OutboxRelayGateway` in the application layer, with a correct justification. It
  was a good read of the generated project's module table, not a criterion: nothing written
  said so, and `ArchHook.java check` / ArchUnit only reject the bad import **after** the
  executor writes the class — by then the spec has already blessed the wrong design.
- **Decision:** Form 4 — one bullet added to `@.claude/rules/architecture-ddd.md`
  § Adapters.
- **State:** approved by Lucas Fernandes, on 2026-09-26 — option 1.

## What the repo already has, and the gap it leaves

Checked before proposing, because the citation already exists:

| Already there | Where | Still open |
|---|---|---|
| `messaging-architect` states the norm and cites the rule for it | `.claude/skills/messaging-architect/SKILL.md:229-231` — *"Form B's relay reads that state through the application-layer `OutboxRelayGateway`, never through the persistence adapter's entity or repository (`@.claude/rules/architecture-ddd.md` § Adapters)"* | **The cited section does not carry the norm.** § Adapters says only *"an adapter only knows the abstractions it needs"* — a reader who follows the citation finds nothing about a sibling adapter's state |
| The rule is read by the skill that designs relays | same file, § Contract Reads, "(Adapters section)" | The read returns a generic sentence, so the criterion still has to be re-derived each time |
| Import-level enforcement exists | `ArchHook.java check` over `.claude/forbidden-imports.txt`, derived from the blueprint's `depends_on`; plus ArchUnit in the generated project | Both act on written files. The design decision happens two phases earlier, in the partials, where nothing mechanical looks |
| The mirror case is already a named discipline | `IdempotentExecution` for `idempotency_keys` (`persistence-architect` step 4a) | It is a template pair, not a stated norm — it shows the shape without saying why |

So item 4 is a **dangling citation**: the pointer was written by record 0047/0048, the
target never was. This record writes the target.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Real and recent: UC-003's relay. Correct outcome, derived by reading the module table, with nothing written to derive it from | "create nothing" |
| 2 — trigger | Touching code in an adapter package; no external system | 6a, 6b |
| 5 — nature | Declarative fact — a dependency direction, not a sequence of steps | 1, 2, 3 |
| 7 — mandatoriness (user) | Nothing new. The design-time gap is persuasion by definition; the code-time import is already blocked | Hook / `permissions.deny` — out of scope, not needed |
| 8 — destination | Both: `architecture-ddd.md` is master content copied to `<project>/.claude/rules/` with `paths` from the blueprint's `architecture_paths` | — (forces this record, § 9 level 2) |
| 9 — integration (user) | Owner is `architecture-ddd.md` § Adapters — dependency direction is that file's subject | `persistence.md`, a new rule file |
| Scope of wording (user) | General: any sweeping or polling component — relay, scheduler, reconciler | A rule naming the outbox pattern |
| Citation (user) | `messaging-architect` cites it — and already does, verbatim | Adding a second citation where one exists |

Axes 3, 4, 6, 10, 11, 12 and 13 changed nothing: territory comes from the file's existing
`paths` derivation, there is no isolation question, and no external system is involved.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Bullet in `architecture-ddd.md` § Adapters | 9 | **Recommended** |
| 2 | New § *Sweeping components* in the same file | 6 | Viable — a section for one sentence, and it splits § Adapters' subject in two |
| 3 | Bullet in `persistence.md` § Adapter boundary | 5 | Viable — wrong owner for a dependency-direction norm, and its `paths` miss the relay's package |
| 4 | New rule `rules/sweeping-components.md` | 3 | Rejected — one bullet does not sustain a file, and it costs `00-index.md` + step 6.6 |
| 5 | Create nothing | 3 | Rejected — leaves `messaging-architect:229-231` citing a section that says nothing of the kind |

### Option 1 — bullet in `architecture-ddd.md` § Adapters (score 9)

**Motivator:** axis 9. The norm is dependency direction between two adapters, which is
this file's declared subject (*"Dependencies point inward"*), and § Adapters is where the
existing citation already points.

**Pros:**

- Makes an existing citation true. `messaging-architect:229-231` sends the reader to
  § Adapters for exactly this; today that reader finds a generic sentence and re-derives
  the criterion, which is what item 4 measured.
- No new file: nothing enters `00-index.md`, nothing enters step 6.6 of
  `project-bootstrap`, and the generated project gets the bullet for free — the whole file
  is copied with `paths` from the blueprint's `architecture_paths`.
- Stays a leaf (invariant 1). The wording names layers and component roles — relay,
  scheduler, reconciler — and no skill, agent, or command.
- Blueprint-agnostic. It states "the application layer owns the abstraction", not
  `infrastructure.messaging` / `infrastructure.persistence`, so it holds in hexagonal,
  layered, onion, vertical-slice and modular-monolith alike, where only the package names
  differ.
- General wording (user's answer) catches the next case before it exists: a `@Scheduled`
  reconciler, a retry sweeper, a report poller — none of which is an outbox.

**Cons:**

- Persuasion, not guarantee. At design time nothing verifies it; the import is only
  rejected once the class exists. Accepted deliberately (axis 7): the failure being fixed
  is a spec written before any code.
- § Adapters grows to five bullets, and the new one is the longest. The file is the most
  widely copied rule in the repo, so every added line costs in every generated project.

**Points cut in the rubric:** criterion 4 (enforcement) — the check that exists runs after
the fact, and this record deliberately adds none.

### Option 2 — a section of its own in the same file (score 6)

A `## Sweeping and scheduled components` heading would be easier to cite by name. Rejected:
§ Adapters already owns "which abstractions an adapter may know", and a second heading for
one sentence makes the reader choose between two sections for the same question. Worth
revisiting only if the norm grows past three bullets.

### Option 3 — `persistence.md` § Adapter boundary (score 5)

Defensible because the state being read *is* persisted. Rejected on two counts: the subject
is dependency direction between sibling adapters, which `architecture-ddd.md` owns
(invariant 2); and `persistence.md`'s `paths` cover the persistence package, so the rule
would not auto-load while someone edits the relay — precisely the file where it matters.

### Option 4 — a new rule file (score 3)

Correct only if this were a theme. It is one bullet, and a new file obliges an
`00-index.md` row plus a step 6.6 row in `project-bootstrap` (invariant 9) for no gain in
retrievability: `architecture-ddd.md` is already loaded wherever this applies.

### Option 5 — create nothing (score 3)

The outcome was right in UC-003, and the import guard exists. Rejected because the
guard fires after the spec is written, and because doing nothing keeps a citation in
`messaging-architect` pointing at a section that does not contain what it claims — a
divergence that reads as drift the next time someone checks it.

## References

| Claim | Source |
|---|---|
| `rules/` is a leaf: a norm may not name a skill | `@CLAUDE.md` invariant 1 |
| One owning file per norm; others cite it by path | `@CLAUDE.md` invariant 2 |
| A new rule would owe `00-index.md` and step 6.6 | `@CLAUDE.md` invariant 9 · `references/decision-matrix.md` § 6 |
| If it must always hold, it is a hook, not prose | `@CLAUDE.md` invariant 6 — weighed and declined on axis 7: the gap is design-time |
| Dependency direction is `architecture-ddd.md`'s subject | `@.claude/rules/00-index.md` — *"Universal DDD + layers, dependency direction, boundaries"* |
| The file has no `paths` of its own; they come from the blueprint | `.claude/rules/architecture-ddd.md` frontmatter comment · `@.claude/blueprints/_schema.md` `architecture_paths` |
| The citation to be made true already exists | `.claude/skills/messaging-architect/SKILL.md:229-231` |
| Import enforcement acts only on written files | `.claude/hooks/ArchHook.java:66-101` (`check` reads `.claude/forbidden-imports.txt`) |
| Precedent for the same discipline, as templates | `.claude/skills/persistence-architect/SKILL.md` step 4a — `IdempotentExecution.java.example` |
| A rule's level-1 justification may not name skills | `references/decision-matrix.md` § 9, Form 4 restriction |

## Propagation

| File | Change |
|---|---|
| `.claude/rules/architecture-ddd.md` | § Adapters gains one bullet: a component that sweeps or polls persisted state reads and updates it through an abstraction the application layer owns, never through a sibling adapter's entity or repository |

Goes to the generated project: **yes** — `project-bootstrap` step 6.6 already copies
`architecture-ddd.md`, rewriting its `paths` from the blueprint's `architecture_paths`. No
row of step 6.6 grows, `00-index.md` is untouched (the file is already in the written-rules
table), and `messaging-architect`'s citation needs no edit — it already points here.

No `## Why this is a rule` section is added to `architecture-ddd.md`: the file is an
existing rule with many bullets and level 1 of § 9 applies to a *created* file. The
justification lives in this record, and naming `messaging-architect` in it is only possible
here — invariant 1 forbids it inside the rule.
