---
name: new-feature
description: Orchestrates the feature pipeline — one use case per run, 5 design skills → spec.md for the executor
disable-model-invocation: true
argument-hint: "<feature description> | UC-NNN-slug | empty to list"
---

# `/new-feature` — Feature pipeline orchestrator

## Why this is Form 2 (manual skill)

Axis 2 (manual trigger) + axis 5 (procedural) + axis 8 (both). Form 1 rejected — user
requested manual invocation. Subagent rejected — none of the three reasons apply
(context, tools, model). A fixed-order procedure rules out a rule and CLAUDE.md.

**Manual only.** `disable-model-invocation: true` hides this skill from the model: the
`Skill` tool can't call it, and trying is blocked. When a conversation reaches the point
of running it, print the exact command for the user to type — don't attempt the call.

---

## Contract

**Ownership:** Feature zero→spec orchestrator. Owns the sequence, the input table, the
spec lifecycle (`status:`), and the final output (`UC-NNN-spec.md`). Does **not** own the
use case number or slug — `use-case-design` does.

**Reads:**
- `pom.xml`, `.claude/forbidden-imports.txt`, and this project's domain package,
  discovered and never assumed — validates the generated project
- `docs/use-cases/UC-*/` — folder existence and each spec's `status:` line, by command
- `docs/use-cases/UC-NNN-<slug>/*.md` — the partials of the case being run

**Writes (indirectly via layer skills):**
- `docs/use-cases/UC-NNN-<slug>/00-caso-de-uso.md` and `docs/use-cases/BACKLOG.md` — via
  `use-case-design`
- `docs/use-cases/UC-NNN-<slug>/10-dominio.md` — via `domain-modeling`
- `docs/use-cases/UC-NNN-<slug>/20-persistencia.md` — via `persistence-architect`,
  migration SQL included as a code block, never as a file under `src/`
- `docs/use-cases/UC-NNN-<slug>/25-mensageria.md` — via `messaging-architect`, only if
  `10-dominio.md`'s Events block names external (Kafka) delivery
- `docs/use-cases/UC-NNN-<slug>/30-rest.md` — via `rest-api-architect`
- `docs/use-cases/UC-NNN-<slug>/40-testes.md` — via `test-architect`

**Writes (indirectly, outside `docs/`):**
- `docker-compose.yml` and `docker/init/**` — via `docker-architect`, when
  `persistence-architect` step 9 or `messaging-architect` step 9 chains it because a
  partial named a service the compose file doesn't declare yet. The design phase is not
  confined to `docs/`: these are the two territories it reaches, they belong to
  `docker-architect` alone, and the "Not now" branch (§ End of flow) stages them for the
  same reason it stages the spec

**Writes (directly):**
- `docs/use-cases/UC-NNN-<slug>/UC-NNN-spec.md` — implementation plan (executor-ready),
  including its `status:` line (`draft` → `approved`)

**Never writes under `src/`.** Neither does any skill it chains. Every file under `src/`
— migrations included — belongs to `java-spring-boot-developer`. The one exception: the
executor-offer pre-flight (§ End of flow) may trigger `archunit-installer` (via
`test-architect`'s setup mode) and `commons-logging-installer` (directly) before
delegating. Those writes are the installers' own, one-time, gated by their own
`AskUserQuestion` — not this orchestrator writing business code.

**Never runs `git add`, `git commit`, or `git push`.** Neither does any skill it chains.
Git happens only through `git-publish`, behind its two confirmations, at the end steps
below. No end of this flow is left without an explicit git instruction.

**Integrates with (sequence ordered by depends-on):**
1. `use-case-design` — invoked for a new case; skipped on resume when `00-caso-de-uso.md` exists
2. `domain-modeling` — invoked if `10-dominio.md` is missing (depends on 1)
3. `rest-api-architect` — invoked if `30-rest.md` is missing (depends on 2)
4. `messaging-architect` — invoked if `10-dominio.md`'s Events block names external
   (Kafka) delivery and `25-mensageria.md` is missing (depends on 2). Skipped entirely
   when the event, if any, stays in-process — not every use case needs it
5. `persistence-architect` — invoked if `20-persistencia.md` is missing (depends on 2, 3, 4)
6. `test-architect` — invoked if `40-testes.md` is missing (depends on 2,3,4,5)

**Why REST and messaging both run before persistence.** The order follows who generates
requirements for whom. REST generates schema requirements — `Idempotency-Key` on a
creation `POST` needs the shared key table — and persistence generates none for REST.
With persistence first, the table was discovered after the partial was written, and
persistence ran twice.

Messaging is the same shape and was learned the same way: a transactional outbox needs the
shared `outbox_events` table, a consumer needs the shared dedupe table, and neither is
visible from the domain partial alone. With messaging after persistence, `20-persistencia.md` was
written without the relay's retry columns and its partial index, and persistence ran twice
again. Messaging depends only on step 2, so running it before persistence costs nothing —
and persistence then reads both requirement lists, `30-rest.md` block 4 and
`25-mensageria.md` § 6, in its first pass.

**Design order isn't implementation order.** Messaging is designed fourth and implemented
sixth: `UC-NNN-spec.md` keeps messaging as block 6, and the executor's conditional Block M
still runs between REST and Tests. The pipeline orders by who needs whose requirements;
the spec orders by what compiles against what.
7. `java-spring-boot-developer` — offered, only for an `approved` spec, after the
   one-time setup pre-flight (§ End of flow) checks for ArchUnit and commons-logging gaps
8. `git-publish` — invoked at the end, per § End of flow

`java-patterns` is not a pipeline step: it carries no spec and this orchestrator never
invokes it. Its catalog travels preloaded inside `java-spring-boot-developer` and gets
applied there, directly, when a symptom already in the generated code matches a row —
`@.claude/decisions/0025-java-patterns-preloaded-in-executor.md`.

---

## Spec lifecycle

`UC-NNN-spec.md` opens with a frontmatter carrying one field:

| `status:` | Written by | When |
|---|---|---|
| `draft` | this skill | at consolidation |
| `approved` | this skill | only after the user's explicit approval, § End of flow |
| `implemented` | `java-spring-boot-developer` | with a green build |

A use case folder is **open** when it has no `UC-NNN-spec.md` yet, or its spec is `draft`.

**One `/new-feature` produces one use case.** The next one only starts once the previous
is `approved` or `implemented`.

**An approved spec is immutable.** Later cases read approved specs as a read-only
contract and reuse the aggregate already modeled. A change a new case needs in an
approved one goes into the `## Impact on approved use cases` section of the **new**
spec; the executor applies it in code. Don't edit the old spec.

Decide what the current case needs. Don't defer or anticipate a decision "for the future
case" — the future case decides it, in its own impact section.

---

## Entry guardrail

Runs before any skill invocation, any write, and any question. Every check is a command;
the model doesn't interpret the input.

### 1 · Input table — closed

Survey the use cases once:

```bash
find docs/use-cases -mindepth 1 -maxdepth 1 -type d -name 'UC-*' 2>/dev/null | sort | while read -r d; do s=$(find "$d" -maxdepth 1 -name 'UC-*-spec.md' -exec grep -m1 '^status:' {} \;); echo "$d ${s:-status: (no spec)}"; done
```

Classify the argument with the argument in single quotes:

```bash
printf '%s' '<argument>' | grep -Eqx 'UC-[0-9]{3}-[a-z0-9]+(-[a-z0-9]+)*' && echo EXACT
printf '%s' '<argument>' | grep -Eiq 'uc-[0-9]' && echo UC_LIKE
test -d 'docs/use-cases/<argument>' && echo FOLDER
```

Evaluate in order and **stop at the first row that matches**. There is no "any other
text" row.

| # | Input | Result |
|---|---|---|
| 1 | empty | ✅ list the survey above — each case with its `status` — and stop |
| 2 | `EXACT`, `FOLDER`, status `draft` or `(no spec)` | ✅ resume: skip `use-case-design`, generate only the missing partials, then consolidation |
| 3 | `EXACT`, `FOLDER`, status `approved` or `implemented` | ❌ `approved spec is immutable — describe the change as a new feature` |
| 4 | `EXACT`, no `FOLDER` | ❌ `use case not found; to create one, describe the feature` |
| 5 | `UC_LIKE` but not `EXACT` (slug plus context, malformed slug, two slugs, a path) | ❌ `ambiguous argument` |
| 6 | free text, and the survey shows an open case | ❌ `<UC folder> is open — resume or approve it first` |
| 7 | free text, and no open case | ✅ new: pass the description **as is** to `use-case-design` |
| — | anything else | ❌ `unrecognized argument` |

**The model doesn't interpret the input.** It doesn't fix a slug, separate a slug from
context, or infer intent. A text that almost matches a row doesn't match it.

**An error has a fixed shape and ends the run.** After it: no skill call, no write, no
question.

```text
❌ /new-feature: <reason>.
Usage: /new-feature <feature description>   → new use case
       /new-feature UC-NNN-slug              → resume a draft case
       /new-feature                          → list
```

### 2 · Worktree — decided here, never later

```bash
git rev-parse --show-toplevel
git rev-parse --git-dir --git-common-dir
```

Both fail → not a git repository yet: no worktree, paths are relative to the project
root. Different `--git-dir` and `--git-common-dir` → the session is inside a worktree. Every
path this run writes is under `--show-toplevel`, and nothing is written in the main
checkout. If the work should be isolated in a worktree and isn't yet, that is decided
**now**, before any question or write. Never enter or leave a worktree mid-flow — edits
get refused outside it and orphan folders appear in the main checkout.

**Third case: the project root itself is gitignored by a parent repository.**

```bash
git check-ignore -q . && echo IGNORED
```

Happens when this project lives inside another repo's ignored path (e.g. a demo under
this meta-repo's own `examples/`, which is 100% gitignored). The pipeline runs normally
— nothing above depends on the root being tracked — but flag it here, once, so the
run's own state carries the fact forward instead of `git-publish` discovering a `git
status` that doesn't match the feature just implemented (lessons-learned-006 § 8:
`src/`, `docs/use-cases/` all ignored, only an unrelated dirty file showed up in
`status`). `IGNORED` → note it in the run's context and pass it to `git-publish`'s
invocation at § End of flow so its own state check (`@.claude/skills/git-publish/SKILL.md`
step 1) knows to warn instead of assuming the diff matches the feature.

### 3 · Project

Checks `pom.xml` (exists + parseable), `.claude/forbidden-imports.txt`, and that a domain
package exists. **Discover it, don't assume it.**

```bash
find src/main/java -type d -name domain
```

`src/domain/` and `adapter/` don't exist in any blueprint: they're Maven module paths,
and even in multi-module blueprints the layer lives at `<module>/src/main/java/…`. A
guardrail that checks a literal path either fails on a valid project or passes by
accident — neither case guards anything. The package name comes from the active
blueprint's `packages.map`; where the blueprint isn't at hand, it comes from the `find`
above.

### 4 · Disk

Available space > 100MB, write permission on `docs/use-cases/`.

Without ✅ validation, anti-pattern 9 (invoking against an invalid project).

---

## Procedure — 6 steps + consolidation

**Execution discipline** — the run's cost is turns × context size, not file size:

- **No progress messages** between steps or tools ("moving to step 3", "domain ok"). Each
  one is a turn that re-reads the whole conversation. The only report is § Final report.
- **Independent writes in one turn** — parallel tool calls, not one file per turn.
- **Dependency versions come from the build file.** Read `pom.xml` before resolving any
  version; no web search for a version the project already declares.
- A step's "Output" below is a check the run makes, not a message it prints.

### Step 1: Use case (new case only)

**Invoke** `use-case-design` via the Skill tool, with the user's description as is.

`use-case-design` owns the boundary, the number, and the slug. When the description
holds several use cases, it asks the user which one to design, designs that one, and
writes the rest to `docs/use-cases/BACKLOG.md`. This run continues with the one it
created — never with the others.

**Output:** `docs/use-cases/UC-NNN-<slug>/00-caso-de-uso.md` — its folder name is the
`UC-NNN-<slug>` every later step uses.

### Short path — checked right after step 1

A trivial case doesn't need five partials. Check all four against `00-caso-de-uso.md`
and the approved specs:

| Criterion | Unmet when |
|---|---|
| Aggregate reused from an approved spec | the component table has a NEW aggregate or value object |
| No new table, column, or migration | the component table has a NEW or CHANGE migration or entity |
| No new exception | an error situation has no exception already named in an approved spec |
| No question asked to the user | `use-case-design` needed `AskUserQuestion` |

All four met → skip steps 2–6 and write `UC-NNN-spec.md` from
`templates/feature-spec-short.md.example`: every row names its source, an approved spec or
a rule. A row that needs judgment to fill means the case isn't trivial — discard the
short spec and take steps 2–6. One criterion unmet → steps 2–6.

### Step 2: Domain (depends on 1)

If `10-dominio.md` is missing: **invoke** `/domain-modeling UC-NNN`.
If it exists: read, validate (four blocks: aggregate, VOs, invariants, ports).

**Output:** "✅ Domain ready" or a list of gaps.

### Step 3: REST (depends on 1,2)

If `30-rest.md` is missing: **invoke** `/rest-api-architect UC-NNN`.
If it exists: read, validate (five blocks: resources, DTOs, errors, pagination, idempotency).

**Output:** "✅ REST ready" or gaps.

### Step 4: Messaging (depends on 2, conditional)

Read `10-dominio.md`'s Events block. If it names external (Kafka) delivery for the
event and `25-mensageria.md` is missing: **invoke** `/messaging-architect UC-NNN`. If
the event is absent or stays in-process, skip this step — not every use case needs it.
If `25-mensageria.md` exists: read, validate (six blocks: topic and delivery, producer,
consumer and idempotency, retry/DLQ, configuration, schema requirements).

A missing § 6 is a gap, not an omission: it's the list step 5 reads. `none` is a valid
value there and means publication Form A with no consumer dedupe table to build.

**Output:** "✅ Messaging ready", "— skipped (no external delivery)", or gaps.

### Step 5: Persistence (depends on 1,2,3,4)

If `20-persistencia.md` is missing: **invoke** `/persistence-architect UC-NNN`. It reads
both schema requirement lists in the same pass — `30-rest.md` block 4 (the idempotency
table among them) and, when step 4 ran, `25-mensageria.md` § 6 (the shared outbox table,
the dedupe table). If it exists: read, validate (three blocks: mapping, migrations,
transactions).

**Output:** "✅ Persistence ready" or gaps.

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
   | Table, column, key, index, migration | `20-persistencia.md` — including every table or column another partial *asked for*: the requirement is born in `30-rest.md` block 4 or `25-mensageria.md` § 6, the final form (name, type, nullability, index, migration) is always this one's |
   | Topic, delivery guarantee, retry/DLQ | `25-mensageria.md` |
   | Aggregate name, value object, port, event | `10-dominio.md` |
   | Exception class and `errorCode` | `10-dominio.md` |
| Use case boundary, invariants, error situations | `00-caso-de-uso.md` |
   | Name and level of each test | `40-testes.md` |

   `UC-NNN-spec.md` carries **a single value per fact** — the winner — across all
   blocks, including those that had inherited the old value. The discarded versions go
   into a `## Resolved divergences` section at the end, one line each: fact, discarded
   value, adopted value, partial that decided. The executor reads one truth; the audit
   trail is still there.

   A divergence the table doesn't resolve — two facts from the same owner, or a
   business-rule contradiction — **stops the pipeline** and asks. Don't invent it or
   stack it.

2. Consolidate into a single file: `UC-NNN-spec.md`, with `status: draft`, **by reference**
   - Each block holds only the final decisions — one value per fact — and the path of the
     partial that details it. Never copy a partial's tables, SQL, or code: a copied
     consolidation doubled the output of a real run and added nothing the partial lacked
   - Structure: 5 blocks (use case, domain, persistence, REST, tests), plus a 6th
     (messaging) only when step 4 wasn't skipped
   - `## Impact on approved use cases`: every row from the same section of each partial —
     "none" when all are empty
   - Order: implementation order (depends-on)
   - Recipient: `java-spring-boot-developer` agent — a spec with § 6 runs the executor's
     conditional Block M (steps M1-M4, between REST and Tests), which generates the Kafka
     producer/consumer adapters from `25-mensageria.md`. Doesn't touch the fixed 19-item
     checklist either way

3. **Check whether the architecture tests can now be turned on.** The bootstrap doesn't
   install ArchUnit or the coverage gate by design: a `check` over an empty set proves
   nothing. After the first feature that condition no longer holds, and nothing in the
   pipeline was watching for it.

   ```bash
   grep -rl "ArchRule\|ArchTest" --include='*.java' src/test/ 2>/dev/null | head -1
   find src/main/java -name '*.java' ! -name 'package-info.java' | head -1
   ```

   Second command with a result and the first without: the project now has business
   classes and still has no boundary enforcement outside the editor. In `single-module`
   the compiler doesn't enforce anything either, so there's no verification at all.
   **Propose running `test-architect` in setup mode** — propose, don't run it: detection
   is mechanical, authorization is the user's.

---

## End of flow

Every branch below ends in an explicit instruction. None of them runs git outside
`git-publish`.

### Approval — always asked

`AskUserQuestion`: **Approve `UC-NNN-spec.md`** / **Keep as draft**.

- **Keep as draft** → report the spec path and the resume command
  (`/new-feature UC-NNN-<slug>`), and stop. No git: a draft isn't a deliverable.
- **Approve** → change the spec's line to `status: approved`, then the executor offer.

### Executor offer — always asked, only for `approved`

`AskUserQuestion`: **Implement now** (`java-spring-boot-developer`) / **Not now**.

- **Implement now** →
  - **One-time setup pre-flight, before delegating.** The executor is about to write the
    first `.java` under `src/` for this project run — the only point in the pipeline
    where "is the one-time infrastructure installed yet" actually matters. Detect what's
    missing, don't assume:

    ```bash
    grep -rl "ArchRule\|ArchTest" --include='*.java' src/test/ 2>/dev/null | head -1
    find . -type d -iname commons -o -type d -path '*shared/logging' 2>/dev/null
    ```

    First command empty → ArchUnit not installed yet (same gap step 3 already flagged,
    if this use case is the first one). Second command empty, or the directory it finds
    has nothing but `package-info.java` → commons-logging classes not installed yet.
    Either gap found → `AskUserQuestion`, one option per gap found: **Install now** /
    **Skip for this run**.
    - ArchUnit, install now → **invoke** `test-architect` via the `Skill` tool with empty
      `$ARGUMENTS` (setup mode) — same route step 3 already names, never invoke
      `archunit-installer` directly, it stays `test-architect`'s alone.
    - Commons-logging, install now → **invoke** `commons-logging-installer` directly via
      the `Agent` tool. No owning per-feature skill to route through: this orchestrator
      is the trigger, same as it owns `git-publish`'s invocation.
    - **Both gaps found and both answered "Install now" → never fire the `Skill` call
      and the `Agent` call in the same turn.** `Skill(test-architect)` opens a design
      phase (`ArchHook.java`'s guard) and `Agent(commons-logging-installer)` closes one —
      two independent `PreToolUse` hooks with no ordering guarantee between them when
      dispatched together, and the loser blocks every write the other agent makes under
      `src/` for the rest of the run. Run them one at a time, in separate turns: the
      `Skill` call first, wait for it to finish, then the `Agent` call.
      `.claude/hooks/ArchHook.java` § guard documents the same rule; lessons-learned-006
      § 1 is the run that hit it (138k tokens, zero files written, had to relaunch alone).
    - Either **Skip** → proceed to the executor anyway. A spec that doesn't cite
      `@LogExecution`/`@MaskSensitiveData` or ArchUnit doesn't need either installed to
      compile; skipping isn't a gate failure, it's the user's call.
    - Neither gap found → skip this pre-flight silently, no question asked.
  - Delegate to `java-spring-boot-developer`, sending the spec path.
  - **Success** (final summary reports the checklist complete, the build green, and the
    spec at `status: implemented`) → **invoke** `git-publish` via the `Skill` tool, with
    `feat(UC-NNN-<slug>): <one-line summary>` as context.
  - **Failure** → report the executor's failure and stop. No git.
- **Not now** → **invoke** `git-publish` via the `Skill` tool, with context
  `docs(UC-NNN-<slug>): approved spec` and these paths to stage:
  - `docs/use-cases/UC-NNN-<slug>/` and `docs/use-cases/BACKLOG.md` — always;
  - `docker-compose.yml` and `docker/init/**` — **when `docker-architect` ran in this
    execution**, chained by `persistence-architect` step 9 or `messaging-architect`
    step 9.

  Design-only does **not** mean "nothing outside `docs/` changed". `docker-architect` is
  the legitimate owner of those two territories and the pipeline chains it as soon as a
  partial names a service the compose file doesn't have yet — a Kafka broker, a database
  engine, an observability backend. Staging only `docs/` leaves that change uncommitted
  and unmentioned, which is how a `kafka` service went orphan once
  (`lessons-learned-010.md` § 7). List the extra paths in the `git-publish` context so
  the diff the user confirms is the whole run, and name them in the final report.

`git-publish`'s two confirmation gates decide whether anything is committed or pushed —
this orchestrator only triggers the offer.

### Final report

Last message of the run, and the only report: the spec path and status, the backlog
entries left for later (if any), **every file written outside `docs/`** (today only
`docker-architect`'s two territories — "none" when it didn't run), what `git-publish`
did, and a recommendation to run
`/clear` before the next `/new-feature` — a clean context per use case keeps cost
measurable per case.

---

## Template: UC-NNN-spec.md

Lives at `.claude/skills/new-feature/templates/feature-spec.md.example`; the short path
uses `templates/feature-spec-short.md.example`.

Structure (5 blocks, implementation order — 6 when messaging applies):
- Frontmatter: `status:`
- Block 1: Use case
- Block 2: Domain model (aggregate, VOs, invariants, ports, events)
- Block 3: Persistence (JPA mapping, migrations, transactions)
- Block 3.5 (conditional): Messaging (producer/consumer, delivery semantics, retry/DLQ)
  — present only when `10-dominio.md` named external delivery for the event; "none"
  otherwise. See the known gap above — the executor doesn't consume this block yet
- Block 4: REST API (resources, DTOs, errors, pagination, idempotency)
- Block 5: Tests (pyramid, critical cases, coverage, checklist)
- Impact on approved use cases ("none" when empty)
- Final section: Resolved divergences (empty when there were none)

See `templates/feature-spec.md.example` for the full shape.

---

## References

- **D14** — ordered pipeline: use-case → domain → persistence → REST → tests → executor. Order
  of REST and persistence swapped by `0037`.
- **D17** — pipeline skills without `disable-model-invocation` (so `/new-feature` can call them).
- **D20** — `/new-feature` deferred; `java-spring-boot-developer` executor; scope: both.
- **D22** — `/new-feature` design (this record).
- **`@.claude/decisions/0032-messaging-architect-skill.md`** — `messaging-architect`
  chained as a conditional step, same pattern as `persistence-architect`/`rest-api-architect`.
- **`@.claude/agents/commons-logging-installer.md`** — one-time logging/masking setup,
  triggered directly from the executor-offer pre-flight, same "installed once, not at
  bootstrap" shape as `archunit-installer`.
- **`@.claude/decisions/0037-lessons-learned-005-remediation.md`** — closed input table,
  one use case per run, spec lifecycle, no `src/` and no git outside `git-publish`.
- **Invariant 2** (`@CLAUDE.md`) — single owner. The orchestrator owns `UC-NNN-spec.md`;
  `use-case-design` owns number and slug.
- **Invariant 8** (`@CLAUDE.md`) — specs via skills; code via executor.

---

## Entry into generated projects

Step 6.7 copies skills. `/new-feature` travels the same way — the user runs
`/new-feature <feature description>` to design a new feature.

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
