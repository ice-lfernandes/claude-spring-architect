# 0101 · Remediation of lessons-learned-018 — a model with no price is named, not hidden

- **Date:** 2026-10-01
- **Scenario:** `@.claude/lessons-learned/lessons-learned-018.md` — the audit report printed
  `— (fill in pricing.json)` for every run on `claude-opus-5-5`, because `pricing.json` had
  no entry for it, and nothing named the missing model.
- **Decision:** Option A — all four fixes inside the existing `audit` and `doctor` modes of
  `.claude/hooks/ArchHook.java`, plus `claude-opus-5-5` in the shipped price template. No new
  mode, no new registration.
- **State:** approved by Lucas Fernandes, on 2026-10-01

> **Superseded by `0134-audit-tokens-only-no-usd-pricing.md`.** The trail no longer prices
> anything: `pricing.json`, its template and the `doctor` check this record added are gone,
> because the gap it named recurred one week later with `claude-sonnet-5-5` (issue #111) — a
> fact this record did not weigh. Kept as history.

## Reproduced on disk before classifying

Checked against `effd2e3`.

| Claim | On disk |
|---|---|
| `auditUsd` matches model ids exactly, no family fallback | Confirmed, `ArchHook.java:4484-4500` — `get(pr, "models", e.getKey())`, `return null` on the first miss |
| Any unpriced model makes the whole amount `null` | Confirmed, same method; documented in its Javadoc ("not a partial sum") |
| The placeholder names the file, not the gap | Confirmed, `ArchHook.java:3978` — `— (fill in `.claude/audit-usage/pricing.json`)` |
| The header shows the model but not the cost | Confirmed, `ArchHook.java:3886` — `🤖 Model` row; cost only in `📊 Tokens (aggregate)` |
| `doctor` checks only that `pricing.json` exists | Confirmed, `ArchHook.java:239-241` |
| The shipped price list has no `claude-opus-5-5` | Confirmed, `project-bootstrap/templates/audit-pricing.json.example` lists `claude-opus-5`, `claude-sonnet-5`, `claude-haiku-4-5-20251001`, filled 2026-09-26 |
| A local fix in the project is reverted by the next update | Confirmed — the template is in `export.copy` (`extensions.json`), so `export` writes it on every run; the same entry is what carries this fix to existing projects |
| Official Opus 5.5 rates | Fetched 2026-10-01 from https://platform.claude.com/docs/en/about-claude/pricing: input 4, 5-minute cache write 5, cache hit 0.20 (0.05x, footnote 2), output 20 USD/MTok. The three models already listed match the page unchanged |

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | 8 runs on 2026-09-30 with `cost_usd: null`; the report sent the reader to a file that looked complete | create nothing |
| 5 — nature | A data gap (one price) plus two display gaps in an existing mode | new skill, rule, agent |
| 7 — mandatoriness | A diagnostic, not a correctness or security rule: a missing price must be *visible*, never blocking | Form 7a gate, Form 8 |
| 8 — destination | Both — `ArchHook.java`, the jar and the price template all travel through `export` | — |
| 16 — existing mode | `audit` and `doctor` already own this; no new mode, no new registration | new Form 7c mode |
| — fix 1 | Fetch the official page; never from memory | `null` placeholder entry |
| — fixes 2/3 | Header row `💰 Estimated cost` after `🤖 Model`, placeholder names the unpriced model(s); total stays unknown | partial lower-bound sum |
| — fix 4 | `doctor` compares the models of the last 15 runs in `history.jsonl` against `pricing.json`; ❌ on the `Audit` line | whole ledger; ✅ with a note |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| A | All four fixes, inside the existing `audit` and `doctor` modes, plus the price in the template | 9 | **Approved** |
| B | Fixes 1–3 only, no `doctor` check | 6 | Rejected — the gap reopens silently on every model upgrade |
| C | Fix 1 only — the price | 4 | Rejected — the next model reproduces the incident exactly |
| D | A `SessionStart` hook that fails when the session model has no price | 2 | Rejected — blocks work over a cost display; invariant 6, mirror |

### A — four fixes in existing modes (score 9)

**Motivator:** axis 1 — the price was missing and nothing said which one; axis 16 — both
checks already live in modes that run.

**What changes:**
- `audit-pricing.json.example` gains `claude-opus-5-5` (4 / 20 / 0.2 / 5) and a new
  `$comment` date.
- One helper names the models of a `Usage` that have no complete price in `pricing.json`.
  `auditRender` computes the aggregate cost once, prints it as `💰 Estimated cost` in the
  header after `🤖 Model`, and reuses the cell in `📊 Tokens (aggregate)`. The placeholder
  reads `— (no price for claude-opus-5-5 in .claude/audit-usage/pricing.json)`, or
  `— (no .claude/audit-usage/pricing.json)` when the file is absent. Per-piece cells stay `—`.
- `doctor`'s `Audit` line reads the `model` field of the last 15 rows of `history.jsonl`
  (split on `, `; rows without it skipped), and reports ❌ naming each id with no price. The
  15 becomes one constant shared with `audit summary`, which already bounds itself to 15
  runs — a model that left use stops alarming on its own.
- `AuditRenderTest` gains a case: a `pricing.json` without the run's model renders the
  header row and names that model.

**Pros:** the next model upgrade is announced by `/arch-doctor` before anyone reads a report;
the report says what is missing in its first table; no price is ever invented — the
"unknown, not partial" rule of `auditUsd` stays.

**Cons:** `doctor` now parses `history.jsonl` (bounded to 15 rows). The ❌ appears in every
generated project the day a new model ships, until its `pricing.json` is updated — that is
the point, but it will be seen as noise by whoever ignores cost.

**Points cut in the rubric:** maintenance (−1, a third reader of `pricing.json`).

### B — fixes 1–3, no `doctor` (score 6)

The report names the gap, but only after a run has spent unpriced. Lessons § "Why nothing
flagged it" 3: a model change is routine, so the gap reopens on each upgrade with no warning
until someone reads a report. **Cut:** recurrence (−3).

### C — the price only (score 4)

Closes today's row and nothing else. The next model reproduces the incident exactly.
**Cut:** recurrence (−3), discoverability (−2), placeholder still misleading (−1).

### D — a `SessionStart` gate (score 2)

A JVM per session to refuse work over a cost display. A missing price is not a correctness
bug; blocking on it is the mirror of invariant 6. **Cut:** cost of always running, form fit,
trust — it would block sessions in projects that never read cost.

## References

| Claim | Source |
|---|---|
| Prices are data, never memory | `ArchHook.java` `auditUsd` Javadoc; `@CLAUDE.md` invariant 8 (same discipline) |
| The template reaches existing projects on update | `export.copy` in `@.claude/schemas/extensions.json` |
| `audit summary` bounds itself to 15 runs | `ArchHook.java:4520`, `:4597` |
| A hook for something nobody needs blocked is waste | `@CLAUDE.md` invariant 6, mirror clause |
| `doctor` is a report, not a gate | `ArchHook.java` `report()` writes to stderr; no registration runs `doctor` |
| Precedent for a lessons-learned remediation record | `0096`, `0097`, `0098` |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/project-bootstrap/templates/audit-pricing.json.example` | `claude-opus-5-5`: input 4, output 20, cache_read 0.2, cache_write 5; `$comment` date 2026-10-01 |
| `.claude/hooks/ArchHook.java` | `AUDIT_RECENT_RUNS = 15`, shared with `audit summary`; `auditUnpriced` names every model with no complete price; `auditCostCell` renders the amount or the named gap; `auditRender` prints `💰 Estimated cost` after `🤖 Model` and reuses the cell in `📊 Tokens (aggregate)`; `doctor`'s `Audit` line is ❌ naming each unpriced model of the last 15 runs |
| `.claude/hooks/ArchHook.jar` | Rebuilt; `build --verify` passes |
| `.claude/.ci/AuditRenderTest.java` | A `pricing.json` without the fixture's model: header and aggregate name it, `doctor` names it |
| `.claude/skills/project-bootstrap/references/project-files.md` | `pricing.json ages on its own` describes the named cell and the `doctor` line |
| `docs/pt-br/04-arch-doctor.md`, `docs/en/04-arch-doctor.md` | `Audit` row |
| `docs/pt-br/08-audit-usage.md`, `docs/en/08-audit-usage.md` | Header carries the cost; the pricing bullet (no longer "ships empty"); the `doctor` paragraph |

Goes to the generated project: **yes** — `ArchHook.java`, the jar and the price template are
in `export.copy` / `export.binary_copy`, so `/arch-adopt` delivers all of it; the template is
written over the project's `pricing.json`.

Not changed, on purpose: past reports and the `null` rows already in a project's
`history.jsonl` are not re-priced.
