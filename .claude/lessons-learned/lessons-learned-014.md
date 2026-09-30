# Lessons learned 003 — post-pipeline implementation of `UC-003`, outside `/new-feature`

Run date: 2026-09-28. Scope: the session that implemented `UC-003-initiate-kyc-verification`
from an already-`approved` spec **without the `/new-feature` orchestrator being open**, and
then committed it. Two `/new-feature` invocations did happen, but both ended in the entry
guardrail (one error, one listing); the executor, the post-executor bookkeeping and
`git-publish` all ran from plain prompts on the main thread.

That is exactly what makes this run worth recording. `lessons-learned-001.md` covers the
design run and `lessons-learned-002.md` covers the executor's own blind spots. This file
covers the third case nobody wrote down: **what the pipeline loses when the orchestrator is
not the one driving**, plus the enforcement holes that only become visible from that
position.

Every fix below lands in the **meta-repository** (the blueprint/skill/hook source), not in
this generated project. Cross-references to `lessons-learned-002.md` mean "already recorded
there, not repeated here".

---

## § 1 · Every enforcement hook is tool-scoped, and this run wrote into two frozen folders through `Bash`

**What happened.** `UC-003-spec.md` § Impact on approved use cases owes one `CHANGELOG.md`
line to `UC-001-register-customer` and one to `UC-002-create-account`.
`lessons-learned-002.md` § 1 records that write being **refused** by `ArchHook.java guard`:

```
❌ UC-001-register-customer is implemented — its specs are immutable.
```

In this run the same two files were created successfully, with no refusal, because they were
written with a shell heredoc (`cat > docs/use-cases/UC-001-register-customer/CHANGELOG.md`)
instead of the `Write` tool. Nothing objected, and nothing recorded it.

**Why this is a gap, not a one-off.** `.claude/settings.json` wires every enforcement by tool
matcher, and `Bash` is in none of them:

| Hook | Matcher | Enforces |
|---|---|---|
| `guard write` | `Write\|Edit\|MultiEdit\|NotebookEdit` | skill-class territory, frozen `UC-NNN` folders |
| `check` | `Write\|Edit` (PostToolUse) | `.claude/forbidden-imports.txt` — the layer boundary |
| `format` | `Write\|Edit` (PostToolUse) | Spotless |
| `audit file` | `Write\|Edit` (PostToolUse) | the changed-file list of `/audit-usage` |
| `schema` | `Write(.claude/**/*.md)` / `Edit(.claude/**/*.md)` | skill and rule frontmatter |

A file written through `Bash` — `cat >`, `tee`, `sed -i`, a generated script, a `python3 -c`
one-liner — skips all five. The enforcement layer this project documents as its safety net is
a **tool-shaped** net, not a filesystem-shaped one.

This is not hypothetical pressure: the harness in this session carried a standing instruction
to prefer `Bash` for reads and edits ("make file changes with `sed`, heredocs, or short
scripts, rather than using the dedicated `Read`, `Edit`, or `Write` tools"). A host-level
setting silently disabled the project's entire write-time enforcement, and nothing in the
project could observe that it had.

**What is and is not actually at risk.** Being precise matters here, because the exposure is
uneven:

- **Layer boundary — mitigated.** `ArchitectureTest.java` (ArchUnit) enforces
  `domain_knows_no_one`, `domain_has_no_framework`, `application_does_not_know_infrastructure`,
  `no_field_injection` and eight more at `./mvnw verify`. A Bash-written framework import into
  `domain` survives the hook and still fails the build. This is the one hole the project
  closed by other means.
- **Frozen-spec immutability — fully open.** An approved or implemented spec can be rewritten
  in place with one `sed -i`, which is the single guarantee the whole "an approved spec is
  immutable" design rests on.
- **Skill-class territory — fully open.** The `/new-feature` contract's "a design run writes
  under `docs/` only, and that is enforced, not promised" is only true of `Write`/`Edit` calls.
- **Spotless — open.** A Bash-written `.java` stays unformatted until someone runs
  `./mvnw spotless:apply`.
- **Audit trail — silently incomplete.** The two `CHANGELOG.md` files created this run do not
  appear in `/audit-usage`'s file list for the run that created them. The trail under-reports
  rather than errs, which is the worse failure mode: it reads as complete.

**Lesson / suggested fix.**

1. Add a `PreToolUse` matcher for `Bash` that parses the command for write shapes
   (`>`/`>>` redirection, `tee`, `sed -i`, `cp`, `mv`, `install`, `python -c` with `open(...,'w')`)
   and applies the same territory and frozen-folder checks to the target path. It cannot be
   perfect — that is not the bar; the bar is that the obvious spellings stop being free.
2. Back it with a **filesystem-shaped** backstop that does not care which tool wrote: the
   `Stop` hook already runs `tests`; add a `guard sweep` there that diffs the working tree
   against the phase's territory and the set of frozen `UC-NNN` folders, and reports (or
   fails) on anything written outside it.
3. Correct `CLAUDE.md` § *The boundary is not enforced by the compiler*. It currently says the
   hook "blocks the write — but only inside Claude Code". The true statement is narrower:
   *inside Claude Code, and only through `Write`/`Edit`*.
4. `lessons-learned-002.md` § 1's proposed `CHANGELOG.md` exemption is still the right fix, and
   this run raises its priority rather than lowering it: the legitimate path must not stay
   harder than the bypass, or the bypass becomes the habit.

---

## § 2 · The write matchers disagree with the check matchers

Minor, and visible in the same table above: `guard write` matches
`Write|Edit|MultiEdit|NotebookEdit`, while `check`, `format` and `audit file` match only
`Write|Edit`. A `MultiEdit` is therefore territory-checked but never boundary-checked,
formatted, or recorded in the audit trail. Whatever the final decision on `Bash`, the four
matchers should be a single constant with one list.

---

## § 3 · The executor has no entry point outside `/new-feature`, so the orchestrator's duties were re-derived from memory

**What happened.** The user asked how to invoke `java-spring-boot-developer`, then asked for
the spec to be implemented. Everything `/new-feature` § End of flow owns then happened
**outside any run**, reconstructed by hand from the skill text that happened to be in context
because the user had invoked `/new-feature` minutes earlier for an unrelated argument:

- the one-time setup pre-flight (the ArchUnit and commons-logging greps),
- the `Agent` delegation with the spec path,
- the `CHANGELOG.md` writes consolidation step 2 mandates,
- the final report with its four mandated findings (pending compose services, unreachable
  approved cases, delegated consumer guarantees, personal data crossing in clear),
- the `git-publish` chaining with a `feat(UC-NNN-slug): …` message shape.

**Why this is a gap.** `/new-feature`'s input table row 3 refuses an `approved` argument
outright: *"approved spec is immutable — describe the change as a new feature"*. That is the
correct answer for **editing** the spec and the wrong answer for **implementing** it. Combined
with the pipeline's own closing advice — run `/clear` before the next feature — the normal,
recommended life of an approved spec is: the run that approved it ends, the context is
cleared, and from then on there is **no supported way to reach the executor offer**. Every
guarantee that lives in § End of flow becomes something the model either remembers or drops.
This run happened to remember because of an accident of context.

**Lesson / suggested fix.** Give the second half of the pipeline its own front door. Either:

- a new input-table row — `EXACT`, `FOLDER`, status `approved` → run § End of flow from the
  executor offer onwards (pre-flight, delegate, changelogs, report, `git-publish`), skipping
  steps 1–6 and consolidation entirely; or
- a separate `/implement-spec UC-NNN-slug` skill of class `orchestrator` owning exactly that
  sequence, which `/new-feature` also calls at its end.

The first is cheaper and keeps one owner. What must not stay true is that the only path to
those five obligations is a conversation that never cleared its context.

---

## § 4 · `status: implemented` was set before the checklist was ticked, and the guard froze 23 unchecked boxes

**What happened.** `UC-003-spec.md` now reads `status: implemented` with **23 `[ ]` and zero
`[x]`**, although all 19 fixed steps plus M1–M3 are done. The executor reported the cause: the
frozen-folder guard admits only two writes inside a closed `UC-NNN` folder — `isStatusClose`
(the `status:` line) and `isChecklistToggle` (`[ ]` → `[x]`) — and only *while* the status is
`approved`. Flipping the status first therefore froze the checklist forever, and the attempted
revert was refused too.

**Why this is a gap.** No document states the order. `ArchHook.java` implements one valid
sequence (tick every box, then close the status) and neither
`java-spring-boot-developer.md` nor `/new-feature`'s lifecycle table says so. The spec is now
permanently misleading to any reader who trusts the checklist over the code.

**Lesson / suggested fix.** Two independent fixes, both cheap, and doing both is right:

1. State the order in the executor's contract: *the checklist is toggled before the status
   line is closed; the status flip is the last write to the folder.*
2. Keep `isChecklistToggle` admissible after `implemented` as well. A `[ ]` → `[x]` toggle is
   monotone and carries no risk the immutability rule exists to prevent.

---

## § 5 · Two contracts disagree about what status an unreachable use case leaves behind

The executor's own operating contract prescribes leaving the spec `approved` — not
`implemented` — when the run leaves an approved use case unreachable end to end (which is
exactly what happened to `UC-002`, § 7 below). `ArchHook.java guard` admits only the
`approved → implemented` transition. The executor followed its contract's *other* half,
closed the spec, and then could not revert; the reopening advice in the hook's message
(`set status: draft by hand`) is wrong for this case, since a `draft` spec is an *open* case
and would block the next `/new-feature` run at input-table row 6.

**Lesson / suggested fix.** Pick one owner for the "implemented but not reachable" state.
The cleanest option is a third status value — `implemented-blocked`, with the blocking case
named — admitted by the hook as a legal close and recognised by the input table as *not*
open. Today the concept exists in prose in three places and in the state machine in none.

---

## § 6 · Both partials are missing their declared-dependency section, and the executor edited `pom.xml` by inference

**What happened.** `25-mensageria.md` ends at `## 6 · Schema requirements`: there is no
`§ 7 Declared dependencies`. `20-persistencia.md` ends at `## 5 · Configuration`: there is no
`§ 6 Declared dependencies`. `/new-feature` defines both as validated blocks and states that
each is "the only list the executor may act on when it writes `pom.xml`". With neither list
present, the executor added `spring-boot-starter-kafka` to `pom.xml` on the strength of
checklist item M3 and `§ 5 Configuration` — a defensible inference, and precisely the decision
the rule reserves for the design skill.

**Why this is a gap.** `lessons-learned-002.md` § 10 already records that `pom.xml` has no
owner in the pipeline. This run shows the **mechanism** that let it through: the missing
sections are checked in steps 4 and 5, which only execute inside a `/new-feature` run that
reaches those steps. `UC-003`'s partials were written and consolidated in earlier runs, and
the spec reached `approved` with both lists absent. A validation that lives in a step is
skipped by every path that does not take that step.

**Lesson / suggested fix.** Move the gate to **consolidation**, which every run passes and
which is where `status: draft` is first written: a spec cannot be consolidated while any
partial that applies is missing its dependency list. `none` stays a valid value; absence does
not. Same reasoning as the existing `Satisfied by` gate, which is already enforced at
consolidation rather than in a step.

---

## § 7 · The `Satisfied by` gate requires a *named* backlog case, and `BACKLOG.md` is designed to have no names

**What happened.** `UC-003` makes `UC-002-create-account` require `Customer.status() == ACTIVE`,
and nothing in `src/main` assigns `ACTIVE`. The satisfier is the KYC callback consumer, which
lives in `docs/use-cases/BACKLOG.md` as a row with a description and no identifier. Both the
executor's report and this session's final report named it **`UC-004`** — a number that exists
nowhere. It was invented, twice, independently, because the sentence needed a name.

**Why this is a gap — a real contract contradiction.** `/new-feature` consolidation states
that a row adding a precondition must name its satisfier, and that the satisfier is "an
approved `UC-NNN`, this case, or a **named** backlog case", stopping the pipeline when it is
"missing or vague". `BACKLOG.md`'s own header, written by `use-case-design`, states: *"**No
number reserved** — a number is given when the case is designed"* and *"No number, no slug.
Both are fixed only when the case is designed."* A named backlog case cannot exist. The gate
is unsatisfiable by construction whenever the satisfier is in the backlog, which is the most
common case by far.

**Lesson / suggested fix.** Give backlog rows a stable, non-`UC` identifier at insertion time
— `BL-07`, say — which `use-case-design` consumes and retires when it assigns the real
`UC-NNN`. It keeps the "no number reserved" property that the header is protecting (a backlog
row must not squat on a `UC` number) while giving the consolidation gate something real to
point at. Then make the gate cite that identifier, and the final report repeat it.

---

## § 8 · Personal data crossing a boundary in clear is a *report* item, never a *gate*

`KycVerificationRequestedPayload.securityNumber` carries a CPF in clear to an external KYC
service over Kafka. `@.claude/rules/security.md` § In transit requires the decision to be
recorded, with receiver and reason, in the design that publishes it — "the exception is the
record, not the absence of one". No partial records it.

`lessons-learned-002.md` § 7 covers the masking rule stopping at logs. The new finding is
structural: **nothing in the pipeline can stop on this.** `/new-feature`'s final report asks
the model to *report* every personal-data field crossing in clear, which is detection after
the spec is approved, after the code is written, and after the commit. `messaging-architect`
designs the payload and has no step that cross-checks its field names against
`@.claude/rules/value-objects.md` § Catalog. In this run the finding surfaced only because the
executor volunteered it.

**Lesson / suggested fix.** Add the check where the payload is designed, not where the run is
summarised: `messaging-architect` (and `rest-api-architect`, for response bodies leaving the
process) runs `security.md` § How to verify grep 1 against the payload it just designed, and
emits either a masked/reduced form or a `§ Recorded decision` block naming the receiver. A
missing block is a gap in the same sense as a missing `§ 6` — it stops consolidation.

---

## § 9 · A retention window was designed, then scoped out of every partial, and has no owner

`20-persistencia.md` § 1 decides a 7-day retention for `outbox_events`.
`V5__create_outbox_events.sql` carries a commented-out `DELETE` and defers pruning to "its own
scheduled job". `application.yml` line 63 states that `prune-after: P7D` is *deliberately* not
configured. So: a retention decision exists in the design, a placeholder exists in the
migration, the property is intentionally absent, and no backlog row, no use case and no
checklist item owns the job that would enforce it.

`@.claude/rules/security.md` § At rest is unambiguous: *"Personal data kept 'until someone
prunes it' is kept forever. A store whose retention window is a property nobody reads has no
retention."* The outbox payload carries the CPF (§ 8), so this is the rule's own example.

**Lesson / suggested fix.** Anything a partial writes under *Pending outside this partial*
that a rule requires must leave the run with an owner: either a `BACKLOG.md` row (§ 7's
identifier makes that citable) or a checklist item. Consolidation should refuse a spec that
declares a retention window with no satisfier, the same way it refuses an unsatisfied
precondition.

---

## § 10 · `git-publish` hardcodes a model name in the attribution line

`.claude/skills/git-publish/SKILL.md:130` prescribes appending
`Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>` to every commit. This session's
harness supplied `Claude Opus 5`, and the audit trail records the executor run as
`claude-sonnet-5, claude-opus-5` — two models on one feature. The model followed the session's
attribution over the skill's literal, which was right but undocumented.

**Lesson / suggested fix.** The skill should say *"append the attribution line the session
provides"* and carry the literal only as a fallback example. A skill file is the wrong place
to pin a model version: it is copied into every generated project and goes stale there
silently.

---

## § 11 · The entry guardrail's pre-existing-work check looks only at the index

`/new-feature` § Entry guardrail step 2's fourth case runs `git diff --cached --stat` to catch
"staged work that is not this run's". At the start of this session the working tree had
`.claude/audit-usage/history.jsonl` modified and one untracked audit report, both from a
previous run — unstaged, so invisible to that check, and both swept into this run's commit by
`git add -A`. Harmless here (the skill deliberately commits the audit trail with the run), but
the check's shape misses the general case: a half-finished edit from before the run rides
along, and the guardrail reports a clean start.

**Lesson / suggested fix.** Compare `git status --porcelain` at guardrail time, not just the
index, and carry the full dirty list — staged and unstaged — to `git-publish` the way
`IGNORED` already travels.

---

## § 12 · The input table's error path met an input it describes wrongly, and the model answered past it

`/new-feature UC-003-spec` matched row 4 (`EXACT`, no `FOLDER`) and produced
`use case not found; to create one, describe the feature`. The argument was in fact the
**basename of the spec file inside an existing folder** —
`docs/use-cases/UC-003-initiate-kyc-verification/UC-003-spec.md`. The message is literally
true and practically misleading: the case exists, the user named it by its spec file.

The model then printed the fixed error **and** a survey table **and** two suggested next
commands, against the rule *"An error has a fixed shape and ends the run. After it: no skill
call, no write, no question."* No harm was done and the extra output is what the user
actually needed — which is the point: the rule is being violated because it is slightly wrong,
not because it was forgotten.

**Lesson / suggested fix.** Two small changes. Add a row for an argument that matches
`UC-NNN-*` and resolves to exactly one existing `UC-NNN-<slug>` folder: answer with that
folder's real slug and status instead of "not found". And relax the error rule from "no
output after the error" to "no *side effect* after the error" — the survey is a read, and it
is the cheapest way to make a near-miss self-correcting.

---

## § 13 · The Kafka healthcheck names a script that is not on the image's `PATH`, and the running broker predates the compose definition

**What happened.** Running the application on the host against the compose stack, the
producer loops on:

```
Bootstrap broker localhost:29092 (id: -1 rack: null isFenced: false) disconnected
Connection to node -1 (localhost/127.0.0.1:29092) could not be established. Node may not be available.
```

Two independent defects, both in the `kafka` service:

**(a) The `kafka` block never declared the `EXTERNAL` listener, so no host-side client could
ever reach the broker.** `docker ps` showed it publishing `0.0.0.0:9092->9092/tcp` and nothing
else, with 29092 closed on the host. The cause is the file, not a stale container:
`docker-compose.yml` declared `KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093`,
advertised `PLAINTEXT://kafka:9092` alone, and published only 9092 —
`git show HEAD:docker-compose.yml | grep -c EXTERNAL` returns `0`, so it had been that way
since the initial commit. `application.yml` defaults to
`${KAFKA_BOOTSTRAP_SERVERS:localhost:29092}` (the address `messaging-architect` owns), so
every run started outside compose aimed at a listener that did not exist. Publishing the port
alone would not have been enough either: `kafka:9092` does not resolve on the host, so the
metadata response would have broken the connection a second time.

This is `lessons-learned-002.md` § 11 — **recorded there, still unfixed here**. The
meta-repository's `templates/kafka-service.yml.example` already carries the two-listener
shape; this project's block predates it, and nothing re-applies a corrected template to a
service that already exists. That is the durable lesson: **a template fix is not a project
fix**, and no signal in the system distinguishes the two — the healthcheck runs inside the
container where `localhost` *is* the broker, and Testcontainers wires its own advertised
listeners, so both green signals described a broker this file does not produce.

Applied in this run: `EXTERNAL://:29092` added to `KAFKA_LISTENERS`,
`EXTERNAL://localhost:29092` to `KAFKA_ADVERTISED_LISTENERS`, the `EXTERNAL:PLAINTEXT`
protocol-map entry, `KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT`, and the `29092:29092`
publish; then `docker compose up -d --force-recreate kafka`. Verified from outside the
container — `docker run --rm --network host apache/kafka:3.8.0
/opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:29092` answers
`localhost:29092 (id: 1 rack: null)` — and `java .claude/hooks/ArchHook.java compose` now
reports `published ports advertised to the host`.

**Correction of record.** The first version of this section claimed the running container had
drifted from a compose file that already declared `EXTERNAL`. That was wrong: the file never
declared it, and `HEAD` proves it. The wrong diagnosis came from reading a `sed` range over
`docker-compose.yml` instead of the service block itself, and it survived until
`docker compose config` printed the resolved service and contradicted it. Worth keeping
visible, because it is the same failure the skill's own step 3 warns about for a different
command (`grep -A2 "^services:"`): **a partial read of a YAML file is a confident wrong
answer, not a missing one.**

**(b) The healthcheck has never once succeeded.** The service declares:

```yaml
test: [ "CMD", "kafka-broker-api-versions.sh", "--bootstrap-server", "localhost:9092" ]
```

and the container's health log holds fifteen consecutive failures of:

```
OCI runtime exec failed: exec failed: unable to start container process: exec: "kafka-broker-api-versions.sh": executable file not found in $PATH
```

In `apache/kafka:3.8.0` those scripts live in `/opt/kafka/bin`, and that directory is not on
the image's `PATH` (`/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin`).
The broker itself is healthy — its log ends at `Kafka Server started` — and the check is what
is broken. The container is therefore permanently `unhealthy`, and since the `app` service
declares `depends_on: kafka: condition: service_healthy`, **`docker compose up app` can never
start the application**. Nobody noticed because every run so far started the app from the host
or from Testcontainers, and neither path waits on that condition.

**Why this is a gap, not an operational slip.** `docker-architect` owns this file and is the
skill that wrote the block. `lessons-learned-002.md` § 11 already recorded the *listener* half
of this same block being wrong (advertising only the internal hostname). The listener half was
fixed; the healthcheck half shipped alongside it and was never executed, because no run in the
project's history has ever waited on `service_healthy` for Kafka. A healthcheck that has never
returned 0 is worse than no healthcheck: an absent one blocks nothing, while this one blocks
every dependent service, silently, until someone reads `docker inspect`.

The deeper pattern is the one § 1 and § 6 also show: **the project verifies what it runs, and
these blocks are never run.** The compose file is written by a skill, committed, and exercised
only in the shapes the daily workflow happens to use.

**Lesson / suggested fix.**

1. Correct the healthcheck to the absolute path —
   `["CMD", "/opt/kafka/bin/kafka-broker-api-versions.sh", "--bootstrap-server", "localhost:9092"]`
   — and note that the same wrong path will be copied into any Testcontainers wait strategy
   derived from it.
2. Add a mandatory step to `docker-architect`: every healthcheck it writes is executed once
   against the started container in the same run, and the exit code is recorded in the
   partial. Writing a healthcheck that was never observed to pass is the defect, not the
   specific script name.
3. **The detector for defect (a) already exists and had simply never been run.**
   `java .claude/hooks/ArchHook.java compose` checks, from the file alone, that every
   published port is advertised at an address the host can resolve — the skill's own step 7
   calls it "the one command that verifies the result", "not optional", and names this exact
   shape. A `kafka` block publishing 9092 while advertising only `kafka:9092` is what it is
   written to catch. It was never executed against this file in the project's history, which
   is why a service committed on day one stayed broken for the host until a use case finally
   needed it. The fix is not a new check: it is making the existing one run without anyone
   remembering to type it — a `Stop` hook, or the same gate that already runs `tests`.
4. A separate, smaller idea worth having anyway: compare each running container's
   `Config.Env` and `HostConfig.PortBindings` against the service definition on disk and
   report mismatches. It is not what happened here, but "the container predates the file" is a
   real state, and `docker compose config` was the only thing that could distinguish it from
   defect (a) during this diagnosis.

## § 14 · The messaging templates organize their package flat, and the persistence ones don't

`@.claude/rules/persistence.md` § Boundary states the convention and states it as absolute:

> **One subpackage per aggregate inside the persistence adapter**, always — including
> while the project has a single aggregate. […] A single flat package holding every
> aggregate's classes breaks SRP at the package level and grows without bound: 3
> aggregates are already 13 classes. The `test/` tree mirrors the same subpackages.

The persistence templates repeat it where the executor actually reads, in a `PACKAGE:`
paragraph of the exemplar header, and they repeat it for the two classes that are *not*
aggregates — which is the harder half:

- `templates/RepositoryAdapter.java.example` — "one subpackage per aggregate — `…persistence.user`,
  `…persistence.order`, never a single flat `…persistence` holding both".
- `templates/IdempotencyKeyStore.java.example` — "it isn't an aggregate, it's shared
  infrastructure, and that is exactly why it must not be dumped into the persistence
  package root: a root that holds 'whatever belongs to nobody' is the flat package the rule
  exists to prevent."
- `templates/OutboxEventStore.java.example` — the same paragraph again, for `…persistence.outbox`.

Every file marker in those exemplars carries the subpackage (`// --- user/UserRepositoryAdapter.java`),
so an executor copying the shape copies the layout with it.

**`@.claude/rules/messaging.md` § Boundary has no equivalent paragraph, and the messaging
templates declare one flat package.** `KafkaProducerAdapter.java.example`,
`KafkaConsumerAdapter.java.example` and `OutboxRelayPublisher.java.example` put the payload
record, the producer adapter, the outbox writer, the appender, the relay and the consumer all
in `com.example.demo.adapter.out.messaging` / `…adapter.in.messaging`, with file markers that
carry no directory. The only packaging statement the rule makes is the opposite kind — that
broker `@Configuration` classes belong *in* the adapter package rather than in a project-wide
configuration one, which settles where the wiring lives and says nothing about how the
adapter is organized inside.

This run produced exactly what the two sets of templates describe:

```
infrastructure/persistence/          infrastructure/messaging/
├── account/      (4 classes)        ├── KycVerificationRequestedPayload.java
├── customer/     (4 classes)        ├── OutboxProperties.java
├── idempotency/  (4 classes)        ├── OutboxRegisterEventAdapter.java
├── outbox/       (3 classes)        ├── OutboxRelay.java
└── package-info.java                ├── SchedulingConfig.java
                                     ├── TopicResolver.java
                                     └── package-info.java
```

The test tree mirrors the persistence split (`…/persistence/account/`, `/customer/`,
`/idempotency/`, `/outbox/`) and has nothing to mirror on the messaging side.

The asymmetry is sharpest on the one concern that exists on both sides. The outbox got its
own subpackage in persistence — `persistence.outbox`, with the paragraph above explaining
why — and stayed flat in messaging, where four of the six classes (`OutboxProperties`,
`OutboxRegisterEventAdapter`, `OutboxRelay`, `SchedulingConfig`) are that same concern's
other half. One feature, one concern, organized two ways, because two template sets were
written to different standards.

Nothing caught it, and nothing could: `ArchitectureTest.java` constrains what may reside in
`..infrastructure.persistence..` and `..infrastructure.messaging..`, never how either is
subdivided. Persistence's split survives on the strength of a comment in an exemplar, which
is also why the messaging half has no split at all.

**The change.** Make the messaging adapter organize like the persistence one, in the same
three places persistence states it:

1. **`@.claude/rules/messaging.md` § Boundary** gets the twin paragraph. The unit is the
   concern rather than the aggregate, because that is what the messaging adapter actually
   holds: one subpackage per event flow — the payload record plus the producer adapter that
   writes it, or the consumer adapter plus its dedupe — and one per piece of shared transport
   infrastructure, `…messaging.outbox` first among them. Same two riders as persistence: the
   `test/` tree mirrors the subpackages, and package-private stays the default, which is what
   makes the split load-bearing instead of cosmetic.
2. **The three messaging exemplars** get a `PACKAGE:` header paragraph and directory-carrying
   file markers (`// --- outbox/OutboxRelay.java`), exactly as `RepositoryAdapter.java.example`
   has. The executor reads the template, not the rule.
3. **`messaging.outbox` and `persistence.outbox` are named as a pair** where each exemplar
   already cross-references the other — `OutboxEventStore.java.example` and
   `OutboxRelayPublisher.java.example` already name each other for the port's signature, so
   the package name belongs in the same note.
4. **One ArchUnit rule ends the reliance on template prose**, for both halves at once: no
   class may reside directly in `infrastructure.persistence` or `infrastructure.messaging`,
   `package-info` excepted. It is the only form of this convention that a hand-written class
   cannot miss, and today neither half has it.

The durable lesson is the one § 6 and § 13 keep restating in other materials: **a convention
that lives only in the template of one skill is a convention for that skill only.** Both
adapters are written by the same executor in the same run; only one of them was told how.

---

## Summary — who should close each gap

| § | Gap | Owner |
|---|---|---|
| 1 | Enforcement is tool-scoped; `Bash` writes bypass guard, check, format, audit and schema | `.claude/settings.json` + `ArchHook.java` (new `Bash` matcher, `Stop` sweep) + `CLAUDE.md` wording |
| 2 | `guard write` and `check`/`format`/`audit file` match different tool sets | `.claude/settings.json` |
| 3 | No entry point to the executor offer for an `approved` spec after the run ends | `/new-feature` input table, or a new `/implement-spec` orchestrator |
| 4 | Checklist frozen unticked because the status was closed first | `java-spring-boot-developer.md` (order) + `ArchHook.java` (allow toggle after close) |
| 5 | "Implemented but unreachable" has no legal state | `ArchHook.java` + `/new-feature` lifecycle table |
| 6 | Missing dependency sections are only checked inside steps 4/5 | `/new-feature` consolidation gate |
| 7 | `Satisfied by` demands a named backlog case; `BACKLOG.md` forbids names | `use-case-design` (backlog identifier) + `/new-feature` gate |
| 8 | Personal data in transit is reported, never gated | `messaging-architect`, `rest-api-architect` |
| 9 | Retention decided, deferred, and left with no owner | `persistence-architect` + consolidation gate |
| 10 | Hardcoded model name in the commit attribution | `git-publish` |
| 11 | Pre-existing-work check reads the index only | `/new-feature` entry guardrail |
| 12 | Row 4 misdescribes a spec-file argument; error rule bans a useful read | `/new-feature` input table |
| 13 | Kafka healthcheck script is not on the image's `PATH`, so `app` can never start under compose; the `EXTERNAL` listener was never in this project's block, and the check that catches it was never run | `docker-architect` (healthcheck path in `templates/kafka-service.yml.example`, verify-once step, re-applying a corrected template to existing services) + whatever makes `ArchHook.java compose` run unprompted |
| 14 | Messaging templates declare one flat adapter package while persistence templates mandate a subpackage per aggregate and per shared concern; nothing enforces either | `.claude/rules/messaging.md` § Boundary + the three `messaging-architect` exemplars + one ArchUnit rule covering both adapters |

---

## Beyond this run — observations not raised in the conversation

These are not failures of this run. They are things the next one will trip over.

**`git-workflow.md` is the rule that keeps being needed and keeps not existing.**
`.claude/rules/00-index.md` lists it as planned. This run committed a full feature to
`use-case-kyc/interation-4`, a branch created outside any skill, with no policy on where a
feature lands, whether it rebases, or whether it becomes a PR. `git-publish` pushes "the
current branch" — whatever that happens to be. Two lessons-learned files now reference git
behaviour with no rule to cite. Also: that branch name carries a typo (`interation`), which
is permanent once pushed — a naming rule would have caught it, and there is no branch-name
convention anywhere in `.claude/rules/`.

**`CLAUDE.md` has drifted again, in the section that matters most.** § *The boundary is not
enforced by the compiler* still states that ArchUnit "is **not installed yet**" and that until
`test-architect` installs it, `./mvnw verify` "verifies neither the dependency direction nor
coverage". It is installed: `ArchitectureTest.java` holds thirteen `@ArchTest` rules and the
JaCoCo gate ran at 92.6%/82.4% against an 80/70 threshold this session. A reader following
that section today would believe the project has no verification precisely where it has the
most. `lessons-learned-001.md` § 2 recorded the same class of drift; this is its second
occurrence, which argues for a mechanical check (a `Stop` hook assertion that the claims in
that section match the tree) rather than another manual fix.

**The bounded context is still undeclared.** `lessons-learned-001.md` § 1 recorded it,
`25-mensageria.md` flags it again, and `banking` still does not appear in `CLAUDE.md`. Three
runs, three records, no owner. It needs to become a checklist item somewhere or it will be in
`lessons-learned-004.md` too.

**The commit mixes the feature with its own audit trail.** `.claude/audit-usage/*.md` and
`history.jsonl`/`nodes.jsonl` are staged with the feature by `git-publish`'s own rule, and for
a good reason (leaving them out makes every later diff start dirty). The side effect is that
`git show --stat` for a feature commit is 51 files of which 6 are telemetry, and every feature
commit conflicts with every other on `history.jsonl`. Worth deciding deliberately: a separate
`chore(audit):` commit in the same push would keep both properties.

**Nothing verified the Testcontainers image tag against `docker-compose.yml` this run.**
`docker-architect` owns that consistency, and it was never invoked — correctly, since no
service was pending. But the check itself only exists inside that skill, so a drift between
the compose tag and the tag pinned in `TestcontainersConfiguration.java` has no detector on a
run that does not touch Docker.

**`M4` remains deferred, so no test observes a message reaching a broker.**
`lessons-learned-002.md` § 3 covers the mechanism. Recorded here only to note that the
deferral survived another full run, is now committed, and the backlog row that would close it
(the KYC callback consumer, § 7) is also the row with no identifier.
