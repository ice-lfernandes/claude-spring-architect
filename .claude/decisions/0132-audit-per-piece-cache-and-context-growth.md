# 0132 · The audit shows each piece's cache split and what grew its context, before the executor is cut again

- **Date:** 2026-10-08
- **Scenario:** "Issue #111, triaged at 74ba8b3 — on a jobs-only spec, 0130's three chained executor groups cut executor cost only 6.5% (USD 5.21 → 4.87) and run cost 0.5%, because cold starts, the orchestrator relay and extra cache write cancel the cache-read saving; the audit also lacks per-piece cache read/write and a group field on executor rows"
- **Decision:** option 1 — Form 7c, a change to the existing `audit` mode of `.claude/hooks/ArchHook.java` (render only) plus `audit.growth_top` in `.claude/schemas/extensions.json`; #111's fixes 1 and 2 deferred with triggers; a pitfall for fact 10. No new piece
- **State:** approved by Lucas Fernandes, on 2026-10-08
- **Goes to the generated project:** yes — `ArchHook.java`, the rebuilt jar and `extensions.json` travel whole through `export`; the audit hook is wired only there

## Reproduced on disk

From `issue-verifier`'s tables for `/triage-issue 111` (layer static, `74ba8b3`, run twice — the
second after the reporter attached both audit reports, the with-split `nodes.jsonl` rows and the
spec's partial status table). Confirmed rows only. The issue's proposed fixes were judged by the
verifier and are not inputs here.

| # | Fact | Evidence |
|---|---|---|
| 1 | 0130's revert test is against fact 3 (base-path spec, USD 9.88), on a spec of the same block set | `0130:150-153` |
| 2 | The measured pair is jobs-only — fact 4's shape, not fact 3's. The revert clause is untested, not failed | reporter's spec status table; `0130:20-21` |
| 3 | The pair is same use case and block set, not same spec: re-designed at v0.18.0, delivery 17 → 20 files | reporter's correction |
| 4 | With split: executor USD 4.87 (0.76 + 1.08 + 3.03), run USD 5.77, 165 executor calls, peak 216,207. Without: executor USD 5.21, run USD 5.80, 130 calls, peak 267,557 | with-split rows + report (pieces sum to 5.767177 vs row 5.767178); without-split report only, matching `0130:21` |
| 5 | Cache-read saving ≈ USD 0.49, cache-write rise ≈ USD 0.33, root 0.46 → 0.72 (`Agent` 1 → 3) | `audit-pricing.json.example:18-23`; reports' per-piece tables |
| 6 | The `tests` group is 62% of the executor and 52% of the run: 91 calls — `Bash` 35, `Read` 32, `Edit` 17, `Write` 6 | third executor row's `tool_calls` |
| 7 | The audit renders cache read/write only in the aggregate; per piece it has billable, cost, duration | `ArchHook.java:5028`, `nodeRow` `:5312-5316`, aggregate `:5062` |
| 8 | Per-message usage, cache split included, is already parsed | `ArchHook.java:5434` |
| 9 | The three executor rows carry no group. The `Agent` call's `description` is logged (`ArchHook.java:4583`, as `detail`) but never read back: a node's `detail` is an agent's **model** override (a skill's args), which only the Chain block shows. Corrected here — the verifier read `detail` as the description | `ArchHook.java:4583`, the `case "agent"` node build, `:5013`, `nodeRow` |
| 10 | An A/B across `/arch-adopt` loses the "before" rows when that run's commit was declined: the trail is staged with the run's commit, and `arch-adopt` refuses a dirty worktree with no exception for `.claude/audit-usage/` | `git-publish/SKILL.md:148`; `arch-adopt/SKILL.md:32-35` |

Refuted and left out: "same approved spec" (row 3); "0130's revert criterion is met" (row 2);
the quote attributed to 0117 (it is `0130:54`).

### What the facts do not say

Where the `tests` group's USD 3.03 went. Fact 6 gives tool counts, not what each call added to
the context — and in this agent cost is turns × context (`0117` § Inside the executor): a token
that enters early is re-read by every later turn. 6 writes against 85 other calls suggests
red → diagnose → fix → rerun cycles, but the audit cannot tell whether the context grew from
reading earlier code, from diagnostic output, or from the number of cycles. A fix aimed at one of
them (#111's fix 2, a tests-only agent, a test-runner agent) is a guess until it can.

Sizes measured here: the agent body is 61,487 bytes (~15k tokens); Blocks 1–3, H, S, M and J,
which the `tests` group never runs, are 25,484 (~6.4k tokens) — ≈ 580k cache read over 91
turns, ≈ USD 0.12 per run.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Facts 4–6: the split barely pays on a jobs-only spec, and the `tests` group is half the run | create nothing (for the measurement gap) |
| Scope (user) | All four of #111's directions considered: per-piece cache, group on the rows, `tests` reads, single delegation for small specs | — |
| Fix 1 — small-spec threshold (user) | **Deferred.** One measured pair, re-designed in between, cannot place a break-even; 0130 left untouched | block set, file count, skip empty groups — each picks a cut-off from one point |
| Fix 2 — the `tests` group (user) | **Measure first.** Signatures by `grep`, signatures in the hand-off and a stronger sentence each trade one cost for another without knowing which one dominates | grep signatures now · tests-only agent (≈ USD 0.12/run, invariant 2 strain splitting shared sections) · test-runner agent (build output already goes to a log; each rerun becomes a cold start + relay) |
| Audit shape (user) | Cache read/write and `detail` in both `nodes.jsonl` and § Tokens per piece | jsonl only · table only |
| Context growth (user) | A **top N** of the calls whose result cost most in re-reads, plus a per-tool total for each piece | per-tool totals only · none |
| Row 10 (user) | A pitfall entry only, no behaviour change | — |
| 7 — Mandatoriness | Measurement, not a guarantee | axes 14–16 do not apply |
| 8 — Destination | Both — `ArchHook.java` and `extensions.json` travel whole through `export`; the audit hook is wired only in `project-bootstrap/templates/settings.json.example` | — |
| 16 — Existing mode | `audit` renders the report; this changes what it renders. No new mode | new 7c mode |
| 17 — CI | `validate · hooks-cross-platform › AuditRenderTest` already renders a run from a throwaway project; it gains the cases | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `audit`: per-piece cache split + `detail` + context growth (top N and per tool) · pitfall for row 10 · fixes 1 and 2 deferred with triggers | 8 | **Approved** |
| 2 | `audit`: per-piece cache split + `detail` only | 6 | Rejected — leaves open why the `tests` group costs half the run |
| 3 | Fix 2 now — the `tests` group reads public signatures by `grep` | 4 | Rejected — persuasion aimed at an unmeasured cause |
| 4 | A tests-only executor agent with a Block-4-only body | 3 | Rejected — ≈ USD 0.12/run, strains invariant 2 |
| 5 | A test-runner agent | 2 | Rejected — each rerun becomes a cold start + relay |
| 6 | Create nothing | 2 | Rejected — facts 7 and 9 are confirmed gaps |

### Option 1 — measure what grew the context (score 8)

**Motivator:** axis 1 and the "What the facts do not say" gap — the next cut to the executor
needs to know which call's result is paid for in re-reads.

**Mechanism — `ArchHook.java` `audit` (render only; capture unchanged except where noted).**

- **Per-piece cache split.** `nodeRow` writes `cache_read` and `cache_write` beside
  `tokens_self`; § Tokens per piece gains the two columns. `Usage` already holds both.
- **`detail`.** `Node` gains `description`, read from the logged `Agent` call; `nodeRow` writes it
  as `detail` (redacted), and every table that names a piece appends it to the label, `🤖 java-spring-boot-developer (group
  tests)`. An A/B per group then reads a field, not a row position.
- **Context growth.** Per piece, in transcript order: growth after turn *k* = context(*k*+1) −
  context(*k*) − output(*k*), where context = input + cache read + cache write. A positive
  growth is attributed to the tool calls of turn *k*; its **re-read** = growth × the turns of the
  same piece after *k*, up to a compaction (context falling below half of the previous turn's).
  The scan keeps, per `tool_use`, a short **target** — `Read`/`Edit`/`Write`: the path;
  `Bash`: the command's first line, at most 80 chars — through `redact()`, the same as tool
  errors.
- **Report.** § Where the run spent gains `### 📈 What grew the context`: the top N calls by
  re-read (piece, tool, target, tokens added, turns re-read, re-read tokens, est. USD at the
  piece's cache-read price), and per piece a by-tool line (`Read +84k → 4.1M re-read`). N lives
  in `extensions.json` (`audit.growth_top`), invariant 10.
- **`nodes.jsonl`.** `reread` — the per-tool ledger in the `tool_calls` shape
  (`Read:4100000,Bash:900000`), so `audit summary` and a later A/B compare runs without opening
  reports. `history.jsonl` gains the root's own `cache_read_self`, `cache_write_self` and
  `reread_self`: the orchestrator relay is the root, and #111 measured it there.

**Deferred, with their triggers (written here, nothing else changes):**
- **Fix 1** — single delegation below a size. Reopen when 0130 § Verification's run exists (with
  split, fact-3 shape) **and** a second fact-4-shaped pair with this audit; the cut-off is chosen
  from both, by block set or by delivery size, whichever separates them.
- **Fix 2** — the `tests` group's reads. Reopen when one `tests` group run under this audit shows
  which tool and which targets dominate re-read; the fix aims at that one.

**Pros:** answers the question every proposed cut depends on, once, for every piece — not only
the executor. Pure render: nothing a run does changes. Fits 0085's "Where the run spent"
precedent. `nodes.jsonl` gains comparable fields, so the next A/B needs no hand-copied report.

**Cons:**
- Growth is an estimate: a turn with several tool calls gets one growth, split by call count;
  the system reminders the harness injects land on whichever call precedes them.
- Targets put paths and command heads into a versioned report — `redact()` covers secrets, not
  paths; the report already names the project's files in tool errors.
- No executor cost falls by this alone.

**Points cut in the rubric:** 5 (a section and four fields to maintain), rounded — 8/9 → 8.

**CI:** `validate · hooks-cross-platform › AuditRenderTest` — new cases on a synthetic
subagent transcript with known usage: `nodes.jsonl` carries `cache_read`, `cache_write`,
`detail` and `reread`; the per-piece table has the cache columns and the group label; the
growth section ranks a known large `Read` first with the expected re-read count, and stops
counting at a compaction. Red once with the growth attributed to turn *k*+1 instead of *k*.

### Option 2 — cache split and `detail` only (score 6)

Closes the two gaps the verifier confirmed (facts 7, 9) and leaves the open question open: the
next A/B shows *that* the `tests` group costs half the run again, not *why*. Cut: 1 (does not
reach the question the next fix depends on), 5.

### Option 3 — fix 2 now (score 4)

Persuasion aimed at an unmeasured cause: of the 32 `Read`, how many were earlier code is
unknown; a test often needs behaviour a signature does not show, and then reads the body anyway.
Cut: 1, 4, 6.

### Option 4 — tests-only executor agent (score 3)

A fresh, Block-4-only subagent is what the `tests` group already is; the difference is ~6.4k
tokens of body ≈ USD 0.12 per run. Splitting the shared sections (§ Execution rule, § Resume,
§ What enters the context) between two agents strains invariant 2. Cut: 2 (strain → capped), 5, 7.

### Option 5 — test-runner agent (score 2)

Build output already goes to a log, not the context (`0117`); the turn that runs the build is
the cost, and the writer still needs its result. Each rerun becomes a cold start + relay — the
costs that cancelled the split's saving (fact 5). Cut: 1, 3, 5, 6, 9.

### Option 6 — create nothing (score 2)

Facts 7 and 9 are confirmed measurement gaps, and the next executor change would be chosen blind.

## References

| Claim | Source |
|---|---|
| Executor cost is turns × context; build output into a log | `@.claude/decisions/0117-new-feature-cost-per-entry-scenario.md` § Inside the executor; `java-spring-boot-developer.md` § What enters the context |
| The split, its estimate and its revert test | `@.claude/decisions/0130-executor-split-by-block-group-implement-in-clean-session.md` |
| "Where the run spent" as the audit's section for this | `@.claude/decisions/0085-audit-where-the-run-spent-and-english.md` |
| Every list or number a mode reads lives in `extensions.json` | `@CLAUDE.md` invariant 10 |
| What the report writes passes through `redact()` | `extensions.json` `audit.redact`; `ArchHook.java:4676` |
| The audit runs only in a generated project | `@CLAUDE.md` routing, "Auditing what a skill or agent run…" |
| A mode is tested through the jar, both directions | `references/ci-coverage.md` § Form 7c; `0084` |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | `Turn` carries each call's target (`toolTarget`, raw; `targetCell` redacts and cuts it at render); `Node` gains `description`; `growthOf`, `growthByTool`, `rereadLedger`, `pieceLabel(Node)`; § Tokens per piece gains cache read and cache write; § Where the run spent gains `### 📈 What grew the context`; piece labels carry the agent call's description in every table; `nodeRow` writes `detail`, `cache_read`, `cache_write`, `reread`; the `history.jsonl` row writes `cache_read_self`, `cache_write_self`, `reread_self` |
| `.claude/hooks/ArchHook.jar` | Rebuilt under JDK 21 |
| `.claude/schemas/extensions.json` | `audit.growth_top: 10`, with its `$comment` |
| `.claude/.ci/AuditRenderTest.java` | `growth()`: a chained group's transcript with known context per request and a compaction; the existing agent label assertion now carries the call's description |
| `.github/workflows/validate.yml` | Comment on the `AuditRenderTest` step naming 0132 |
| `docs/pt-br/07-ci-validate.md` · `docs/en/07-ci-validate.md` | The `AuditRenderTest` row names the new claims and 0132 |
| `docs/pt-br/08-audit-usage.md` · `docs/en/08-audit-usage.md` | Tokens per piece example with the new columns and a group label; the growth section as a design decision; the new ledger fields |
| `docs/pt-br/11-pitfalls.md` · `docs/en/11-pitfalls.md` | Fact 10, under ownership inside a feature run |

Not touched, on the user's answer: 0130 (its § Verification still waits for a fact-3-shaped run with the split), `new-feature`, `java-spring-boot-developer`.

Goes to the generated project: **yes** — through `export`, which copies `ArchHook.java`, the jar and `extensions.json` whole. Nothing to add to the manifest. `AuditRenderTest` stays here: nothing under `.claude/.ci/` travels.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · hooks-cross-platform › audit renders where the run spent, redacts tool errors, skips what class or project turns off` | Each piece's cache read and write, its call's description in the label and in `nodes.jsonl`, the growth ranking (a large `Read` first, re-reads stopping at a compaction), a command's first line redacted, the per-tool line and the `reread` ledger | Green on the tree, 53/53. Red twice, each failing the same four checks by name: growth attributed to request k+1 instead of k; the compaction stop removed (re-reads counted past it). Source and jar restored; `build --verify` green |
| `validate › schema` (step `frontmatter schema`) | `extensions.json` still parses with the new key | Green |
