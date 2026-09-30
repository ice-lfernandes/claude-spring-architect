# 0056 · Remediation of lessons-learned-012 — the mechanical half

- **Date:** 2026-09-27
- **Scenario:** `@.claude/lessons-learned/lessons-learned-012.md` — the second
  `/new-feature` run of the same KYC case in `demo-clean-arch-single-module`, in two
  invocations (USD 8,25, 271.624 billable tokens, 9 files). Sixteen items plus a header
  note. The user scoped this run to the **mechanical** ones: a defect of a program, of an
  injection, or of a written procedure — not a norm the model inferred.
- **Decision:** Option 1 — edit what exists, and extend the two modes that already run.
  No new piece: 6 skills, `ArchHook.java` (`checkArguments`, the citation transform,
  `doctor`'s `UC references`, `model` in both ledgers), `extensions.json` (`arguments`,
  `doctor`, `body_transforms`, `gitignore_lines`) and `@CLAUDE.md`.
- **State:** approved by Lucas Fernandes, on 2026-09-27

## Scope of this run

In: items **2, 3, 6, 7, 12, 14**, the header note (the trail does not record the model
per node), and the versioning question of items **15/16**, which the user answered.

Out, and deliberately so — a second run takes them, with item 11's mechanism already
chosen: items **4** (Avro as an `AskUserQuestion` over a rule default), **5** (bounded
context is a project fact), **8** (`scope-boundary.md` vs. the outbox's Form B), **9**
(outbox columns and who owns retry pacing), **10** (a use case that creates no use-case
class), **11** (CPF logged raw since UC-001 — decided: candidates derived from
`value-objects.md`'s catalog **plus** an ArchUnit test over `infrastructure.rest.dto`),
**13** (image tag and healthcheck verified, catalog line for a new service).

Item **1** — `disable-model-invocation: true` did not stop `Skill(new-feature)` — is
closed with **no action**, by the user's answer. Neither the text nor a bug report.

## Three of the items were reproduced before classifying

| Item | Reproduction |
|---|---|
| 2 | `.claude/skills/new-feature/SKILL.md:418` carries a literal `$ARGUMENTS` in prose. A survey found **7 occurrences across 6 skills**, every one of them a sentence *about* the argument, so every one is interpolated at run time into a self-contradicting instruction — `test-architect:34` reads "`$ARGUMENTS` empty" and arrives as "`UC-003-initiate-kyc-verification` empty" |
| 3 | `java .claude/hooks/ArchHook.java export /tmp/… --blueprint clean-architecture-single-module` leaves `- **** — ` (`new-feature:498`, `:503`), `Inherits D15 —.` (`persistence-architect:51`, `test-architect:46`), `Inherits D15 — — and …` (`rest-api-architect:43`) and `matches a row —.` (`new-feature:102`). **Six mangled sentences, and the run reports no residue** — `residue_markers` looks for the path that was removed, so it never sees what removing it left behind |
| 12 | The injection is `grep -E "^\s{2}\S+:$"`, which matches every two-space key in the file, `volumes:`' children included. The portrait the run received listed `postgres-data:` as a service and omitted two real ones |

## Interview

| Axis | Answer | Forms/options it eliminated |
|---|---|---|
| 1 — symptom | Sixteen items from one real run; the three above reproduced on this machine today | "create nothing" for items 2, 3, 6, 7, 12, 14 |
| 5 — nature | Every fix lands on a file that already has an owner: 6 skills, `ArchHook.java`, `extensions.json`, the `export` manifest | Forms 1, 3, 4, 6, 7b |
| 7 — mandatoriness | Items 2 and 3 are failures of a transform and of a text, not of judgment; both recur silently | Prose-only fixes for 2 and 3 |
| 8 — destination | **Both.** `new-feature`, `git-publish`, `docker-architect`, `messaging-architect`, `persistence-architect`, `test-architect`, `ArchHook.java` and `extensions.json` are all in `export`'s payload | — |
| 16 — existing mode | `schema` and `doctor` already exist and already visit the files each new check needs | A new mode (Form 7c as a *new* mode) |
| Item 3 — shape of the fix | Replace the citation with text ("decision recorded in the meta-repository"), **and** add the mangling markers to `residue_markers` | Cutting the whole sentence; detection only |
| Item 2 — beyond the sentence | Fix the 7 sentences **and** add a `schema` lint: `$ARGUMENTS` outside a code block in a skill that declares `argument-hint` | Fixing the sentence alone |
| Items 6/7 — where the check lives | Commands in the guardrail (step 1 crosses `find` with `git ls-tree` over HEAD; step 2 reports a pre-existing dirty index) plus a fifth state in `git-publish` | A new `ucstate` Java mode; a frontmatter injection |
| Items 12/14 — mechanism | `awk` in the injection, bounded to the `services:` block, and the orphan-`UC-` scan added to `doctor` | Reusing the `compose` mode's YAML parser through a `--list-services` flag |
| Items 15/16 — versioning | `.claude/audit-usage/` **is** versioned (`git-publish` stages it by default); `docs/lessons-learned/` is **not** (it enters the generated project's `.gitignore`) | Versioning both; ignoring both; deferring |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Edit what exists, and extend the two modes that already run: `schema` gains the `$ARGUMENTS` lint, `doctor` gains the orphan-`UC-` scan | 9 | **Approved** |
| 2 | The same edits with no new check: fix the 7 sentences, fix the transform, and trust review | 6 | Rejected — each of the two defects recurs silently, and item 2 already recurred twice inside one run |
| 3 | A new `ucstate` mode in `ArchHook.java` crossing disk, index and HEAD, invoked by the guardrail and by `git-publish` | 5 | Rejected by the user — the guardrail is already a sequence of commands, and a new mode buys determinism nobody asked for |
| 4 | Create nothing; record the sixteen items as known pitfalls | 2 | Rejected — item 3 ships six dead sentences into every project exported today |

### Option 1 — edit what exists, extend `schema` and `doctor` (score 9)

**Motivator:** axis 1 — three defects reproduced today, one of them already inside every
exported tree; axis 5 — every fix has an owning file; axis 16 — no new mode is needed,
because both checks belong to modes that already open these files.

**Pros.** No new piece enters the inventory. The `$ARGUMENTS` lint reuses
`checkInjections`'s exact shape — same fence and double-backtick stripping, same
`typedFile` scope, its list in `extensions.json` — so it costs one more regex on an event
`schema` already answers. The transform fix turns a dead sentence into a true one instead
of deleting a line the project's reader would then miss, and the mangling markers make the
CI's existing "reports a surviving dead citation" step fail on the next sentence whose
shape `replace` does not handle. The `awk` portrait is what the skill's own decision table
depends on ("a service already present is left alone"), and the same one-liner replaces
the wrong `grep -A2` in three skills.

**Cons.** Two, both real. `residue_markers` gains substrings (`****`, `— —`, `—.`) that
are shapes, not paths — a legitimate sentence ending in an em dash before a period would
be reported as residue, and the escape hatch for that is rewording the sentence, not a
flag. And the `$ARGUMENTS` lint blocks an edit for a reason the author may read as
pedantic: the fix is to write "the argument" or "the target above", which the error
message has to say outright (anti-pattern 18).

**Points cut in the rubric:** criterion 9 — the lint fires on every edit under
`.claude/` that a `types` entry captures, and `schema` already pays that JVM, so the
marginal cost is a regex; the point is cut anyway because a false positive from the
mangling markers costs a person a minute, not a millisecond.

### Option 2 — the same edits, no new check (score 6)

Defensible on cost: seven sentences and two data entries, no Java. Rejected because the
failure mode of both items is silence. Item 2's literal `$ARGUMENTS` survived one full
remediation cycle and appeared twice in one run; item 3 shipped six mangled sentences
without the export saying a word.

### Option 3 — a new `ucstate` mode (score 5)

Real determinism, testable in `.claude/.ci/`. Rejected by the user: the guardrail is
already a list of commands whose output the model reads, and the two missing checks are
one `git ls-tree` and one `git diff --cached --stat`. Invariant 6's mirror — a new mode
against a failure two commands already surface.

### Option 4 — create nothing (score 2)

Recorded only to name what it would mean: every project exported today keeps the six
mangled sentences, and the next `/new-feature` recycles a `UC-NNN` number again.

## References

| Claim | Source |
|---|---|
| The new check belongs to a mode that already exists, not to a new one | `references/decision-matrix.md` § 2.2, row 4; anti-pattern 17 |
| A blocking check must name the way out | `references/decision-matrix.md` § 7, anti-pattern 18 |
| Lists a mode reads are data in `extensions.json`, never constants in the Java | `@CLAUDE.md` invariant 10 |
| A guarantee is bought against an observed failure | `@CLAUDE.md` invariant 6 and its mirror |
| The lint's shape already exists | `.claude/hooks/ArchHook.java` `checkInjections` + `injections` in `.claude/schemas/extensions.json` |
| The CI already fails on a reported dead citation | `.github/workflows/validate.yml` job `export-determinism`, step "export twice per blueprint" |
| `.claude/audit-usage/.state/` is already the only ignored part of the trail | `export.gitignore_lines` in `.claude/schemas/extensions.json` |
| Everything touched here travels into the generated project | `export.skills.include` — all six skills are listed |

## What is deferred, and with what already decided

| Item | State |
|---|---|
| 4 — Avro chosen by `AskUserQuestion` over a rule default | Open. The general form is the one the lessons-learned names: a rule default is not a question. Either `messaging-architect` step 3 gains a serialization axis with the trigger and the six-place cost written into the question, or the partial records the default and the condition and never asks |
| 5 — bounded context | Open. It is a project fact with the same standing as the base package; the open part is where it lives (root `CLAUDE.md` of the generated project, a rule, or the blueprint) and `messaging-architect` reading instead of asking |
| 8 — `scope-boundary.md` vs. the outbox's Form B | Open. Needs the explicit exception: broker publication is a separate effect **under Form A**; under Form B the append shares the transaction and does not split the use case |
| 9 — outbox columns and retry pacing | Open. `messaging-architect` step 4a must cite `OutboxEventTable.sql.example`, and the consolidation's precedence table needs the tie-break: column shape is persistence, retry semantics is messaging, and a case where one depends on the other stops the pipeline |
| 10 — a use case that creates no use-case class | Open. Either "extension of an approved use case" gets its own numbering and slug rule, or it becomes a different artifact with its own template; plus an "altered by" index outside the immutable file |
| 11 — CPF logged raw since UC-001 | Open, **mechanism already decided** by the user in this interview: masking candidates derive mechanically from `@.claude/rules/value-objects.md`'s catalog (cpf, cnpj, email, phone, document), **and** `test-architect` installs an ArchUnit test — a DTO in the REST DTO package with a field matching the catalog implements `LogMask`. Not prose in two partials in series |
| 13 — image tag and healthcheck of a new service | Open. Verify the tag the way the Maven version is verified, and give every new image a line in `docker-architect`'s catalog after its first use |
| 1 — `disable-model-invocation` did not block `Skill(new-feature)` | **Closed with no action**, by the user's decision. Neither the text nor a bug report |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/new-feature/SKILL.md` | ✅ Item 2: the `$ARGUMENTS` sentence, now "with **no argument at all**". ✅ Item 6: guardrail step 1 crosses the survey with `git ls-tree -d --name-only HEAD docs/use-cases`, stops when a `UC-NNN` is in HEAD and not on disk, and diffs `BACKLOG.md` against HEAD. ✅ Item 7: guardrail step 2 gains a fourth case — `git diff --cached --stat` non-empty before the run |
| `.claude/skills/git-publish/SKILL.md` | ✅ Item 7: `git diff --cached --stat` in step 1 and a fourth state, `PRE_EXISTING_INDEX`, with the scope question and `git add -- <paths>` instead of `-A`. ✅ Item 15: `.claude/audit-usage/` staged always, named or not |
| `.claude/skills/test-architect/SKILL.md` · `docker-architect` · `messaging-architect` · `persistence-architect` · `rest-api-architect` | ✅ Item 2: the remaining 6 literal `$ARGUMENTS` in prose |
| `.claude/skills/docker-architect/SKILL.md` | ✅ Item 12: the injection lists only the keys inside `services:` (`awk`, comment-stripped); step 3 carries the same command and says why `grep -A2` is never it |
| `.claude/skills/messaging-architect/SKILL.md` (step 9) · `persistence-architect` (step 9) | ✅ Item 12: the same `grep -A2 "^services:"` replaced |
| `.claude/skills/audit-usage/SKILL.md` | ✅ Header note: the `Modelo` column in the vocabulary, with what `—` means and why it is read before comparing two runs |
| `.claude/hooks/ArchHook.java` | ✅ Item 2: `checkArguments`, called from `checkOne` next to `checkInjections`. ✅ Item 3: the in-sentence citation becomes prose, the lead-in cut consumes a list of records, and the standalone-sentence shape is cut. ✅ Item 14: `orphanUseCaseRefs` + `doctor`'s `UC references` line, gated on `docs/use-cases` existing. ✅ Header note: `model` in `history.jsonl`, in each `nodes.jsonl` row, and a `🤖 Modelo` column in `audit summary` |
| `.claude/schemas/extensions.json` | ✅ `arguments` (marker, `requires_field`, interpolation line, exemptions); ✅ `body_transforms.citation_replacement`, the third `cut_shapes` entry, the three mangling `residue_markers`, and `clause_pattern` removed as dead; ✅ `doctor.uc_references`; ✅ `export.gitignore_lines` += `docs/lessons-learned/`, with the comment stating why the audit trail is versioned and the lessons are not |
| `CLAUDE.md` | ✅ § Routing: a stale `docs/use-cases/UC-` citation goes to `ArchHook.java doctor`. ✅ § Known pitfalls: `$ARGUMENTS` is interpolated at every occurrence and `schema` rejects it; `grep -A2 "^services:"` is not the list of services |
| `.claude/lessons-learned/lessons-learned-012.md` | ✅ The three reproductions recorded under items 2, 3 and 12 |

**Verified:** `schema` exit 0, and exit 2 with the right message on a planted
`$ARGUMENTS` in prose; all 7 blueprints export twice byte-identical outside the stamp,
with no residue and no mangled shape left in any exported file; the `awk` portrait returns
four services on a fixture where the old `grep` returned three plus a volume; `doctor`
reports `no docs/use-cases — nothing to check` here and names
`docker-compose.yml cites docs/use-cases/UC-003-gone` on a fixture that reproduces § 14;
`audit summary` renders the two-run Sonnet/Opus comparison from the model column.

Goes to the generated project: **yes** — all seven skills, `ArchHook.java` and
`extensions.json` are in `export`'s payload; `docs/lessons-learned/` reaches it through
`export.gitignore_lines`.
