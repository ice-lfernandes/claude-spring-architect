# Lessons learned 004 — `/new-feature` redesign of `UC-003-initiate-kyc-verification`

Run date: 2026-09-30. Scope: the `/new-feature` run that redesigned `UC-003` from scratch after
`846a0ed` removed the previous approved spec, through consolidation, approval, "Not now" at the
executor offer, and `git-publish` (`f38d7e6`, pushed to `origin/main`). No code was generated.
Audit report: `.claude/audit-usage/2026-09-30T12-45-27--new-feature.md` (18m01s active, status
"success with recovered failures").

This file records what went wrong, what the framework left open, and — the main point — every
place where **the model decided something the framework should have decided for it**. Every fix
lands in the **meta-repository** (blueprint, skills, hooks, rules), not in this project.

---

## § 1 · `Partial status` stays ❌ forever, because no piece owns it

**What happened.** `00-caso-de-uso.md` line 5 reads, after approval:

```
> Partial status: `10-dominio.md` ❌ · `25-mensageria.md` ❌ · `35-jobs.md` ❌ · `30-rest.md` ❌ · `20-persistencia.md` ❌ · `40-testes.md` ❌
```

All six partials exist and the consolidated spec is `status: approved`. The same line is ❌ in
`UC-001-register-customer` and `UC-002-create-account`, which are **implemented**.

**Why.** The line comes from `use-case-design/templates/use-case-spec.md.example` and is written
once, when the parent spec is born — before any partial exists. `grep -rn "Partial status" .claude/`
finds it only in that template. No `SKILL.md` has the duty to update it: not the partial owners,
not `/new-feature` consolidation, not the approval step, not the executor. The spec lifecycle
lives in `UC-NNN-spec.md`'s `status:` line, so the header in `00` is a second status field with
no writer.

Two more defects in the same line:

- The template lists **four** partials (`10`, `20`, `30`, `40`). `25-mensageria.md` and
  `35-jobs.md` are absent, so the model extended the line on its own and chose the order
  (pipeline order, not file order).
- After approval nobody can fix it anymore without breaking the immutability of an approved
  folder — and after implementation the guard freezes the folder. So the wrong value becomes
  permanent at the exact moment it becomes wrong.

**Fix (pick one).**

1. Remove the line from the template. `UC-NNN-spec.md` `status:` plus the files on disk already
   answer the question, and `/new-feature`'s survey reads them.
2. Or give it an owner: each design skill flips its own marker when it writes its partial
   (still inside the open draft phase), the template lists all partials the pipeline can
   produce with `➖` for "not applicable", and consolidation refuses to write `UC-NNN-spec.md`
   while any applicable marker is ❌.

Option 1 is cheaper and removes a duplicated source of truth.

---

## § 2 · The bounded context has a reader and no writer

**Root cause.** The bounded context is a project fact that the framework **reads** but no piece
**writes**. `grep -rni "bounded.context" .claude/` finds exactly three places, all on the reading
side:

| Where | Role |
|---|---|
| `rules/messaging.md:138` | defines the topic shape `<bounded-context>.<aggregate>.<event-in-past-tense>` |
| `skills/messaging-architect/SKILL.md:109-115` | "The bounded context comes from here too — **read it, never ask**", then `grep -i "bounded context" CLAUDE.md` |
| `skills/messaging-architect/templates/messaging-spec.md.example:21` | an example value |

No skill, agent, template or hook produces it. `arch-adopt`, `arch-doctor` and the `CLAUDE.md`
generation do not ask for it or write it. `project-initializer` lives only in the
meta-repository and could not be checked from here, but the `CLAUDE.md` it generated for this
project has no such line. The skill treats the value as a declared fact "with the same standing
as the base package". The base package is collected at project creation; the bounded context is
collected by nobody. It exists only if a human remembers to edit `CLAUDE.md` by hand.

**Why the gap turns into a dead end.** Three properties of the framework combine:

1. **The only fallback is "stop", at the most expensive point.** The missing value is detected
   at `messaging-architect` step 2. That is the fourth skill of a `/new-feature` run, after
   `00`, `10` and `30` are already written. Stopping throws away a run for a one-line fact.
2. **The guard blocks the fix inside the run.** The `design` class has no write access to
   `CLAUDE.md`, so even when the value is asked, it cannot be recorded where the skill reads it.
3. **The alternative source exists only after the fact.** The skill also accepts an existing
   topic in `src/main/resources`. The first topic appears only after the first messaging case
   is **implemented**. Until then every messaging design hits the same stop. That includes the
   next one in this project, because UC-003 is approved but not implemented.

**How it showed up in this run.** Both stop conditions held ("not declared **and** no topic
exists yet"). The model did not stop: the framework left no path that kept the run alive. It
asked the user with three options it invented (`onboarding`, `banking`, `customer`), none marked
recommended. Then it tried to `Edit` the root `CLAUDE.md`, and `ArchHook.java guard` refused:

```
❌ `use-case-design` + `domain-modeling` + `rest-api-architect` + `messaging-architect` is class `design` — CLAUDE.md is outside its territory.
```

`banking` went into `25-mensageria.md` as a follow-up. `CLAUDE.md` still has no bounded context.

**Why it keeps coming back.** This is the **fourth** record: `lessons-learned-001.md` § 1,
`lessons-learned-003.md` "Beyond this run", the previous UC-003 design, and this run. Each run
records the symptom as a follow-up. A follow-up has no owner, so no piece ever closes it. Every
fix proposed so far was about the moment of reading. None gave the value a writer.

**Fix: give the fact a writer, before any feature run.**

- **Collect it where the base package is collected.** `project-initializer` asks for it at
  creation. `arch-adopt` asks for it on adoption or upgrade when it is missing. Store it in
  `CLAUDE.md`, or in a machine-readable file the hooks and skills read (e.g.
  `.claude/project.json`) with `CLAUDE.md` rendered from it.
- **Check it before any partial is written.** `/arch-doctor` reports it when missing.
  `/new-feature`'s entry guardrail refuses to start when the request involves a broker and the
  value is absent, and prints the command that sets it.
- **Keep `messaging-architect`'s read-only rule.** Once the value has a writer, "read it, never
  ask" is correct, and the stop at step 2 becomes unreachable in practice instead of routine.

Rejected alternative: letting the `design` class append that one line to `CLAUDE.md`. It closes
this run's symptom, but it keeps collection per use case. That is exactly what the skill forbids,
because two sessions can then pick two namespaces (`lessons-learned-012` § 5).

---

## § 3 · `infrastructure.scheduling` is missing from `CLAUDE.md`, and the model used the blueprint instead of stopping

**What happened.** `jobs-architect` step 2: the `.scheduling` package comes from the root
`CLAUDE.md`; "No such entry → stop and say which line is missing from the blueprint". The
`Module structure` table in `CLAUDE.md` has no `.scheduling` row. The model found the package in
`.claude/blueprints/clean-architecture-single-module/clean-architecture-single-module.yaml` `packages.map`, continued, and
recorded a "doc gap" in `35-jobs.md`.

**Why — the sentence was rewritten by `ArchHook.java export`, not written that way.** The
skill in this project says two different things about the same fact:

- Step 2, line 120: "And **the project's packages, documented in the root `CLAUDE.md` and the
  module `CLAUDE.md` files** entry ending in `.scheduling` … No such entry → stop and say which
  line is missing from **the blueprint**".
- Contract, lines 288-289: "Reads … **the active blueprint's `packages.map`**".

The broken grammar in step 2 ("…`CLAUDE.md` files entry ending in…") shows what happened. The
meta-repository sentence was almost certainly "And the active blueprint's `packages.map` entry
ending in `.scheduling`". `export` (the mode `arch-adopt` runs to copy `.claude/` into a
project) applies literal `find`/`with` pairs from the `export.replace` block (`ArchHook.java`
around line 1580: `body.replace(find, with)`). Its premise, from its own header comment: "every
citation to `decisions/` or `blueprints/` cut, since neither exists inside a project". So
"the active blueprint's `packages.map`" became a pointer to `CLAUDE.md`. The same substituted
phrase appears in eight more exported files (`domain-modeling`, `messaging-architect`,
`test-architect`, `use-case-design`, `gof-design-patterns`, `commons-logging-installer`, two
templates).

Three defects follow from it:

1. **The premise is false for this project.** `.claude/blueprints/clean-architecture-single-module/clean-architecture-single-module.yaml`
   exists here, and its `packages.map` has `infrastructure.scheduling` (line 66). The
   rewrite points the skill away from a source that is present to one that has drifted.
2. **The replace is a literal substring match, so it is partial.** Where the phrase breaks
   across a line ("the active blueprint's\n`packages.map`", contract lines 288-289), it did not
   match and survived. The stop message ("missing from the blueprint") was never in the pattern
   either. Result: one skill, two sources for one fact.
3. **`CLAUDE.md` is not regenerated.** The upgrade to v0.9.0 (`cc0765f`) added
   `infrastructure.scheduling` to the blueprint and left the `CLAUDE.md` `Module structure`
   table untouched. So the target of the rewrite is stale by construction after any upgrade that
   adds a package.

The model saw the contradiction, took the blueprint (the contract's source), and continued. That
was the right source, but it contradicts the step-2 instruction to stop, and no rule settled it.

**Fix.**

- Drop the `blueprints/` → `CLAUDE.md` replace for `packages.map`. `arch-adopt` already copies the
  blueprint YAML into the project, so the citation resolves. If some projects lack it, make
  `export` always ship the active blueprint's YAML instead of rewriting prose around its absence.
- Make `export.replace` fail the export when a `find` matches only part of a file's occurrences,
  or match across whitespace (`\s+`), so a line break cannot make one file cite two sources.
- `arch-adopt` regenerates the `CLAUDE.md` `Module structure` table from `packages.map` on every
  upgrade, and `/arch-doctor` checks that the two agree.

---

## § 4 · The `UC-003` number was reused, against `naming.md`, because the framework has no "redesign" path

**What happened.** `.claude/rules/naming.md` § Use case identifier: "`NNN` is sequential across
the whole project and never reused — a number that existed in the history belongs to the case
that had it, even after that case's folder is deleted." The previous approved `UC-003` was
deleted in `846a0ed` "to be redesigned from scratch". The model reused `UC-003` with the same
slug, wrote the reason in `00-caso-de-uso.md`, and reported it as a decision made without asking.

**Why.** The framework has exactly two states for an approved spec: immutable, or deleted.
An approved-but-never-implemented spec that is outdated (it predated blueprint v0.9.0 and lacked
the jobs partial, the declared dependencies and the personal-data decision) has no supported way
back to `draft`. The previous session deleted it — which the rule treats as a burned number —
and this session then had to choose between the rule (UC-004, leaving a hole and a confusing
history) and the intent of the deletion commit (a redesign of the same case). The model chose,
the framework did not.

**Fix.** Add a supported transition `approved → draft` for a spec whose status was never
`implemented`: `/new-feature UC-NNN-slug --redesign` (or an explicit input-table row) that asks
for confirmation, flips `status:` back to `draft`, records the reason in the case's
`CHANGELOG.md`, and resumes the pipeline on the same number. Then `naming.md` keeps "never
reused" true without exception, and nobody deletes approved folders.

---

## § 5 · `BACKLOG.md` was in the pre-v0.9.0 format, and the model migrated it inline

**What happened.** The existing `BACKLOG.md` had no `Id` column and no `## Retired` table
(old header: "No number reserved… Remove the line when its `/new-feature` starts"). The
v0.9.0 template requires `BL-NN` ids and a `Retired` table, and `/new-feature` consolidation
requires the `BL-NN` of every deferred row. The model rewrote the header, assigned `BL-01` to
the existing customer+account row, and appended `BL-02` and `BL-03`.

Side effect: the old KYC-callback row had been **deleted** by `846a0ed`, not retired, so the
history of that row is lost, and `## Retired` starts empty even though a row did leave the
backlog.

**Why.** `arch-adopt` upgrades `.claude/**` but migrates nothing under `docs/`. A template change
that changes the shape of a project document has no migration step, so the first run that
touches the document migrates it by inference.

**Fix.** Each blueprint release that changes a `docs/` template ships a migration in
`arch-adopt` (header rewrite, id assignment in row order) and a check in `/arch-doctor`
("`BACKLOG.md` has no `Id` column").

---

## § 6 · `persistence-architect` step 4b contradicts itself, and the model resolved it silently

**What happened.** `25-mensageria.md` § 6 required, correctly worded as a guarantee: "bounded
attempts and a growing wait between attempts of the same row", in order per aggregate, correct
with more than one instance. The outbox exemplar has no column to record the next attempt and no
lease column. The model added `next_attempt_at` and `claimed_until` to the table, a
`NOT EXISTS` per-aggregate order check to the claim, and marked them "added to the exemplar's
column set". It asked the user about retention and deploy shape, not about the columns.

**Why.** Step 4b says both:

- "The column set is this step's, and the exemplar is its only source."
- "If it cannot hold with the exemplar's columns — per-row exponential backoff has nowhere to
  record the next attempt — … stop the pipeline and ask."

The second sentence describes this exact case. But the lease strategy the same step recommends
("`claimed_at` (+ optional owner id)") also adds a column outside the exemplar, so the skill
already accepts additions for the claim. The model read the exception as permission for the
backoff too. Either reading is defensible, which is the problem.

Related: persistence also **added a requirement upstream** — bounded producer timeouts
(`max.block.ms=5000`, `request.timeout.ms=3000`, `delivery.timeout.ms=5000`) on messaging's
producer config, so that the lease (PT5M) outlives any send. Consolidation carried it into the
messaging block. No rule says a downstream partial may add requirements to an upstream one.

**Fix.** Put `next_attempt_at` and a lease column into `OutboxEventTable.sql.example` itself:
the messaging rule already makes per-row backoff and multi-instance safety the default
guarantee, so the exemplar should deliver the default. Then delete the "stop and ask" sentence,
or narrow it to guarantees the new exemplar still cannot meet. And state in `/new-feature`
§ Precedence that a downstream partial may add a requirement to an upstream one only as a
listed divergence that consolidation resolves.

---

## § 7 · `CHANGELOG.md` lines are written at design time, in present tense, for code that does not exist

**What happened.** Consolidation wrote, as required:

```
- 2026-09-30 · UC-003 · `Customer` gains `status` (born `KYC_IN_PROGRESS`); `RegisterCustomerUseCase` appends ...
```

into `UC-001-register-customer/CHANGELOG.md`, and a similar line into UC-002's. The user then
chose "Not now". Today both logs say UC-001's code changed; it did not.

**Why.** `/new-feature` consolidation step 2 owns the write, and the header of every
`CHANGELOG.md` says "Changes later use cases made to this case's **code**". Design time is the
wrong moment for a log of code changes: if the executor never runs, diverges, or the spec is
redesigned (§ 4), the log is false.

**Fix.** Either the executor writes (or confirms) the line after a green build, or
consolidation writes it with an explicit state: `2026-09-30 · UC-003 (approved, not
implemented) · …`, and the executor rewrites the state when it sets `implemented`.

---

## § 8 · The worktree is dirty after every run, by design

**What happened.** `git-publish` committed and pushed with a clean tree. Right after, the
`Stop` hook wrote the run's audit report. `git status` now shows:

```
 M .claude/audit-usage/history.jsonl
 M .claude/audit-usage/nodes.jsonl
?? .claude/audit-usage/2026-09-30T12-45-27--new-feature.md
```

**Why.** `git-publish` stages `.claude/audit-usage/` to avoid exactly this
(`lessons-learned-012` § 15), but the report of the current run does not exist yet at commit
time — it is written at `Stop`, after the commit. Staging can never include it. Every run ends
dirty, and the next run's pre-existing-work check sees it.

**Fix.** Pick one: write the audit trail to an ignored location and commit it with a separate
`chore(audit):` commit on demand; or have the next run's `git-publish` treat
`.claude/audit-usage/**` as always-owned (never "pre-existing work", staged silently); or
gitignore the trail entirely and keep it local.

---

## § 9 · "Commit everything dirty" keeps the `docs(...)` message for unrelated `pom.xml` changes

**What happened.** The entry guardrail and `git-publish` both correctly flagged
`pom.xml`/`docker-compose.yml` (sonarqube changes from an earlier `sonarqube-setup` run) as
pre-existing work. The user chose "Tudo que está sujo". The commit went out as
`docs(UC-003-initiate-kyc-verification): approved spec` with a build-plugin change and a new
compose service inside. The body mentions it; the type is still wrong.

**Why.** `git-publish` offers two options for pre-existing work and one message. Choosing "all"
does not change the message or offer a second commit. Separately, `sonarqube-setup` does not
chain `git-publish`, which is why its changes were still dirty when this run started.

**Fix.** On "everything dirty", `git-publish` offers **two commits in one push**: the run's
paths with the run's message, then the rest with a `chore:` message listing the files. And every
skill that writes outside `docs/` (like `sonarqube-setup`) chains `git-publish` at its end, as
`project-initializer` does.

---

## § 10 · Writes through `Bash` still bypass the tool-scoped hooks

**What happened.** The approval step flipped the spec with
`sed -i '' 's/^status: draft$/status: approved/' …/UC-003-spec.md`. A `cd` inside a Bash call
also changed the working directory for later calls ("Shell cwd was reset…").

**Why.** Same root cause as `lessons-learned-003.md` § 1: `guard write`, `check`, `format` and
`audit file` are matched on `Write|Edit`. This time the write was inside the orchestrator's
territory, so nothing was actually violated — but the `audit file` changed-file list does not see
it, and `status:` transitions are exactly what the audit trail should record. The harness in this
session again instructed the model to prefer `sed`/heredocs for small edits.

**Fix.** `/new-feature` performs the `status:` flip with `Edit` explicitly (one sentence in
§ Approval), and `guard bash` recognizes `sed -i`/redirections that target
`docs/use-cases/**` and routes them through the same territory check.

---

## § 11 · Smaller decisions the model made that a rule or template should have made

Each of these is now in the approved spec, so the executor will implement them verbatim. None
was asked; none is wrong in isolation; each is a place where two runs could decide differently.

| Decision | Where | Framework gap |
|---|---|---|
| `RelayOutboxEventsUseCase.Settings` as a **nested** record | `25-mensageria.md` | `CLAUDE.md` says `application.usecase` holds only `*UseCase` + `*Command`. A use case that needs settings has no legal top-level type, so the model nested it to satisfy the naming rule. The rule should name the allowed third type (or `application.shared`) |
| `RelayOutboxEventsUseCase` with **no** `@Transactional` | `25-mensageria.md` | `CLAUDE.md` defines `application.usecase` as "the transaction boundary". The relay pass must not be one transaction (claim, send and mark are separate units). The exemplar knows it; the package table contradicts it |
| `RegisterCustomerUseCase` returns `Customer` instead of `CustomerId` | `10-dominio.md` | Changes an approved case's port signature so the controller can read `status`. Legitimate, recorded in the CHANGELOG, but no rule says whether an extension may change an approved inbound port's return type or must add a query |
| Producer timeout values (5000/3000/5000 ms) and lease PT5M | `20-persistencia.md` | Derived by the model to make the lease safe. The exemplar should ship consistent defaults for both (see § 6) |
| ShedLock version `7.10.1` pinned in the spec | `35-jobs.md` | Resolved correctly from Maven Central, not from memory. But a version in an approved spec goes stale before implementation; the executor should resolve it again at build time |
| Test profile `application-test.yml` + `@ActiveProfiles("test")` on every `@SpringBootTest`, including UC-001/UC-002 tests | `40-testes.md` | The first scheduled job of a project always needs this. It belongs to `jobs-architect`/`test-architect` setup, once per project, not to whichever case adds the first job |

---

## § 12 · After compaction, the run's decisions exist only in the raw transcript

**What happened.** The session was compacted after the final report. Building this file
required parsing `~/.claude/projects/…/044d974e-….jsonl` to recover the questions asked, the
answers, and the guard refusals. The audit report has cost, duration and chain; it has none of
those.

**Fix.** The `audit` hook records, per run, every `AskUserQuestion` (question, options, answer),
every guard refusal (exact message), and every decision a skill reports as "made without
asking". That is the material a lessons-learned file needs, and it is the part a compaction
summary loses first.

---

## What worked

- The entry guardrail found the pre-existing `pom.xml`/`docker-compose.yml` changes and handed
  the list to `git-publish`, which asked before staging them (`lessons-learned-012` § 7 and
  `lessons-learned-014` § 11 are closed).
- `guard write` refused the `CLAUDE.md` edit from a design phase (`lessons-learned-012` § 4 is
  closed).
- `frozen_exempt_basenames: ["CHANGELOG.md"]` let consolidation write the two CHANGELOG lines
  with `Write`, no workaround (`lessons-learned-002` § 1 / `lessons-learned-003` § 1 are closed
  for this path).
- Deferred items all got a `BL-NN` and an owner: the ACTIVE gate (`BL-02`), dead-letter
  operations (`BL-03`). No approved case became unreachable (`lessons-learned-003` § 7 closed).
- Personal data was asked, not inferred: payload fields, form in transit, CPF in the response,
  outbox retention. The KYC service's idempotency was named as "assumed", and reported as a
  finding.
- The prune job shipped with its retention property in the same design
  (`lessons-learned-014` § 9 closed).

---

## Summary — who should close each gap

| § | Gap | Owner (meta-repository) |
|---|---|---|
| 1 | `Partial status` line has no writer; template lists 4 of 6 partials | `use-case-design` template, `/new-feature` |
| 2 | Bounded context has a reader (`messaging-architect`) and no writer; detected mid-run, unfixable inside it (4th occurrence) | `project-initializer`/`arch-adopt` (writer), `/arch-doctor` + `/new-feature` guardrail (check) |
| 3 | `export.replace` rewrote "blueprint `packages.map`" to `CLAUDE.md` (false premise, partial match); `CLAUDE.md` not regenerated | `ArchHook.java export` + `extensions.json` `export.replace`, `arch-adopt`, `/arch-doctor` |
| 4 | No `approved → draft` transition; number reuse forced | `/new-feature` input table, `naming.md` |
| 5 | `docs/` templates change with no migration | `arch-adopt`, `/arch-doctor` |
| 6 | Step 4b self-contradiction; exemplar lacks backoff and lease columns; downstream→upstream requirements unregulated | `persistence-architect` templates and step 4b, `/new-feature` § Precedence |
| 7 | CHANGELOG written before the code exists | `/new-feature` consolidation, `java-spring-boot-developer` |
| 8 | Audit trail dirties the tree after every run | `audit` hook, `git-publish` |
| 9 | "Commit everything" keeps a wrong commit type; `sonarqube-setup` does not chain `git-publish` | `git-publish`, `sonarqube-setup` |
| 10 | `status:` flip through `sed -i` invisible to hooks | `/new-feature` § Approval, `guard bash` |
| 11 | Package naming vs. relay settings/transactions; port-signature changes; first-job test profile | `CLAUDE.md` template / `naming.md`, `jobs-architect`, `test-architect` |
| 12 | No per-run decision log survives compaction | `audit` hook |
