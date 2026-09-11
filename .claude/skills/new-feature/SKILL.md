---
name: new-feature
description: Orchestrates the feature pipeline — 5 design skills → spec.md for the executor
disable-model-invocation: true
argument-hint: "[UC-NNN-slug] or empty to list"
---

# `/new-feature` — Feature pipeline orchestrator

## Why this is Form 2 (manual skill)

Axis 2 (manual trigger) + axis 5 (procedural) + axis 8 (both). Form 1 rejected — user
requested manual invocation. Subagent rejected — none of the three reasons apply
(context, tools, model). A fixed-order procedure rules out a rule and CLAUDE.md.

---

## Contract

**Ownership:** Feature zero→spec orchestrator. Owns the sequence + final output
(`UC-NNN-spec.md`).

**Reads:**
- `pom.xml`, `.claude/forbidden-imports.txt`, and this project's domain package,
  discovered and never assumed — validates the generated project
- `docs/use-cases/UC-NNN-<slug>/00-caso-de-uso.md` — if it exists; if not, triggers creation

**Writes (indirectly via layer skills):**
- `docs/use-cases/UC-NNN-<slug>/00-caso-de-uso.md` — via `use-case-design`
- `docs/use-cases/UC-NNN-<slug>/10-dominio.md` — via `domain-modeling`
- `docs/use-cases/UC-NNN-<slug>/20-persistencia.md` — via `persistence-architect`
- `docs/use-cases/UC-NNN-<slug>/25-mensageria.md` — via `messaging-architect`, only if
  `10-dominio.md`'s Events block names external (Kafka) delivery
- `docs/use-cases/UC-NNN-<slug>/30-rest.md` — via `rest-api-architect`
- `docs/use-cases/UC-NNN-<slug>/40-testes.md` — via `test-architect`

**Writes (directly):**
- `docs/use-cases/UC-NNN-<slug>/UC-NNN-spec.md` — implementation plan (executor-ready)

**Integrates with (sequence ordered by depends-on):**
1. `use-case-design` — always invoked (creates the parent UC spec)
2. `domain-modeling` — invoked if `10-dominio.md` is missing (depends on 1)
3. `persistence-architect` — invoked if `20-persistencia.md` is missing (depends on 2)
4. `messaging-architect` — invoked if `10-dominio.md`'s Events block names external
   (Kafka) delivery and `25-mensageria.md` is missing (depends on 2). Skipped entirely
   when the event, if any, stays in-process — not every use case needs it
5. `rest-api-architect` — invoked if `30-rest.md` is missing (depends on 2)
6. `test-architect` — invoked if `40-testes.md` is missing (depends on 2,3,4,5)
7. `java-spring-boot-developer` — delegates implementation (once it exists; consumes UC-NNN-spec.md)

`java-patterns` is not a pipeline step: it carries no spec and this orchestrator never
invokes it. Its catalog travels preloaded inside `java-spring-boot-developer` (step 6)
and gets applied there, directly, when a symptom already in the generated code matches a
row — `@.claude/decisions/0025-java-patterns-preloaded-in-executor.md`.

---

## Entry guardrail

Before invoking any skill:

1. **Argument** — valid UC-NNN-<slug> (lists if empty; error if invalid format)
2. **Project** — checks `pom.xml` (exists + parseable), `.claude/forbidden-imports.txt`,
   and that a domain package exists. **Discover it, don't assume it.**

   ```bash
   ls -d src/main/java/*/*/*/domain 2>/dev/null || find src/main/java -type d -name domain
   ```

   `src/domain/` and `adapter/` don't exist in any blueprint: they're Maven module
   paths, and even in multi-module blueprints the layer lives at
   `<module>/src/main/java/…`. A guardrail that checks a literal path either fails on a
   valid project or passes by accident — neither case guards anything. The package
   name comes from the active blueprint's `packages.map`; where the blueprint isn't at
   hand, it comes from the `find` above.
3. **Disk** — available space > 100MB, write permission on `docs/use-cases/`
4. **New UC** — if the argued UC doesn't exist on disk, propose a name + slug (ask the user)
5. **Existing UC** — if `00-caso-de-uso.md` exists, offer: update specs or create a new UC

Without ✅ validation, anti-pattern 9 (invoking against an invalid project).

---

## Procedure — 6 steps + consolidation

### Step 1: Use case (always)

**Invoke** `/use-case-design UC-NNN-<slug>` via the Skill tool.

Re-invoke even if the UC exists — `use-case-design` decides (update or accept).

**Output:** `00-caso-de-uso.md` ready.

### Step 2: Domain (depends on 1)

If `10-dominio.md` is missing: **invoke** `/domain-modeling UC-NNN`.
If it exists: read, validate (four blocks: aggregate, VOs, invariants, ports).

**Output:** "✅ Domain ready" or a list of gaps.

### Step 3: Persistence (depends on 1,2)

If `20-persistencia.md` is missing: **invoke** `/persistence-architect UC-NNN`.
If it exists: read, validate (three blocks: mapping, migrations, transactions).

**Output:** "✅ Persistence ready" or gaps.

### Step 4: Messaging (depends on 2, conditional)

Read `10-dominio.md`'s Events block. If it names external (Kafka) delivery for the
event and `25-mensageria.md` is missing: **invoke** `/messaging-architect UC-NNN`. If
the event is absent or stays in-process, skip this step — not every use case needs it.
If `25-mensageria.md` exists: read, validate (producer/consumer, delivery semantics,
retry/DLQ).

**Output:** "✅ Messaging ready", "— skipped (no external delivery)", or gaps.

### Step 5: REST (depends on 1,2)

If `30-rest.md` is missing: **invoke** `/rest-api-architect UC-NNN`.
If it exists: read, validate (five blocks: resources, DTOs, errors, pagination, idempotency).

**Output:** "✅ REST ready" or gaps.

### Step 6: Tests (depends on 1,2,3,4,5)

If `40-testes.md` is missing: **invoke** `/test-architect UC-NNN` (design mode).
If it exists: read, validate (four blocks: pyramid, fixtures, coverage, cases).

**Output:** "✅ Tests ready" or gaps.

### Consolidation (after 1,2,3,5,6 ✅ — step 4 conditional)

If all specs that apply exist and validate (messaging only when step 4 wasn't skipped):

1. **Resolve divergences before consolidating.** The partials are written by different
   skills, and downstream corrects upstream: `30-rest.md` fixes the path and status
   that `00-caso-de-uso.md` had sketched, `20-persistencia.md` fixes the key that the
   domain described in prose. Stacking both versions and explaining in a note which one
   wins pushes the work onto the reader — and the note ends up two hundred lines away
   from where it matters.

   Walk through the five partials and, for each fact that appears in more than one with
   different values, apply this precedence:

   | Fact | Who wins |
   |---|---|
   | HTTP path, verb, status, body shape | `30-rest.md` |
   | Table, column, key, index, migration | `20-persistencia.md` |
   | Topic, delivery guarantee, retry/DLQ | `25-mensageria.md` |
   | Aggregate name, value object, port, event | `10-dominio.md` |
   | Use case boundary, invariants, business errors | `00-caso-de-uso.md` |
   | Name and level of each test | `40-testes.md` |

   `UC-NNN-spec.md` carries **a single value per fact** — the winner — across all
   blocks, including those that had inherited the old value. The discarded versions go
   into a `## Resolved divergences` section at the end, one line each: fact, discarded
   value, adopted value, partial that decided. The executor reads one truth; the audit
   trail is still there.

   A divergence the table doesn't resolve — two facts from the same owner, or a
   business-rule contradiction — **stops the pipeline** and asks. Don't invent it or
   stack it.

2. Consolidate into a single file: `UC-NNN-spec.md`
   - Structure: 5 blocks (use case, domain, persistence, REST, tests), plus a 6th
     (messaging) only when step 4 wasn't skipped
   - Order: implementation order (depends-on)
   - Recipient: `java-spring-boot-developer` agent — **known gap:** the executor's fixed
     19-step checklist doesn't yet implement a messaging block (no Kafka producer/consumer
     code generation step). Until it's written, a consolidated spec with a messaging
     block still needs manual implementation of that part. Declared, not silently
     dropped — same pattern as D23's "passo 6.8 falta"

3. **Check whether the architecture tests can now be turned on.** The bootstrap doesn't
   install ArchUnit or the coverage gate by design: a `check` over an empty set proves
   nothing. After the first feature that condition no longer holds, and nothing in the
   pipeline was watching for it.

   ```bash
   grep -rl "ArchRule\|ArchTest" --include=*.java src/test/ 2>/dev/null | head -1
   find src/main/java -name '*.java' ! -name 'package-info.java' | head -1
   ```

   Second command with a result and the first without: the project now has business
   classes and still has no boundary enforcement outside the editor. In `single-module`
   the compiler doesn't enforce anything either, so there's no verification at all.
   **Propose running `test-architect` in setup mode** — propose, don't run it: detection
   is mechanical, authorization is the user's.

4. Offer two paths:
   - **Spec ready:** "UC-NNN-spec.md consolidates all 5. Next: hand off to the executor or test the specs."
   - **With executor:** Delegate to `java-spring-boot-developer` (send the spec as context).

---

## Template: UC-NNN-spec.md

Lives at `.claude/skills/new-feature/templates/feature-spec.md.example`.

Structure (5 blocks, implementation order — 6 when messaging applies):
- Block 1: Use case
- Block 2: Domain model (aggregate, VOs, invariants, ports, events)
- Block 3: Persistence (JPA mapping, migrations, transactions)
- Block 3.5 (conditional): Messaging (producer/consumer, delivery semantics, retry/DLQ)
  — present only when `10-dominio.md` named external delivery for the event; "none"
  otherwise. See the known gap above — the executor doesn't consume this block yet
- Block 4: REST API (resources, DTOs, errors, pagination, idempotency)
- Block 5: Tests (pyramid, critical cases, coverage, checklist)
- Final section: Resolved divergences (empty when there were none)

See `templates/feature-spec.md.example` for the full shape.

---

## References

- **D14** — ordered pipeline: use-case → domain → persistence → REST → tests → executor.
- **D17** — pipeline skills without `disable-model-invocation` (so `/new-feature` can call them).
- **D20** — `/new-feature` deferred; `java-spring-boot-developer` executor; scope: both.
- **D22** — `/new-feature` design (this record).
- **`@.claude/decisions/0032-messaging-architect-skill.md`** — `messaging-architect`
  chained as a conditional step, same pattern as `persistence-architect`/`rest-api-architect`.
- **Invariant 2** (`@CLAUDE.md`) — single owner. The orchestrator owns `UC-NNN-spec.md`.
- **Invariant 8** (`@CLAUDE.md`) — specs via skills; code via executor.

---

## Entry into generated projects

Step 6.7 copies skills. `/new-feature` travels the same way — the user can run
`/new-feature UC-002-payment` to design a new feature.

No adaptation needed — skills already use relative paths.

---

## Operational note: long-running background work

Implementing a complete feature takes dozens of minutes. An executor launched in the
background **doesn't survive the machine sleeping**: the watchdog cuts the stream and
execution dies where it was. This happened three times in a row on the first real
feature, with not a single file written, because all three died still in the reading
phase.

Before delegating in the background, warn and offer both options: keep the machine
awake during execution (`caffeinate -i` on macOS), or implement on the main thread,
which is resumable. The executor writes by checkpoint precisely so that an interruption
leaves reusable progress — but no checkpoint helps if execution dies before the first
`Write`.
