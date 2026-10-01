# Lessons learned 006 — the audit report stopped printing the estimated cost

Run date: 2026-09-30. Scope: `.claude/audit-usage/2026-09-30T15-08-05--new-feature.md`, the
report of `/new-feature UC-003-initiate-kyc-verification`. The `📊 Tokens (aggregate)` table
printed `💰 estimated cost | — (fill in .claude/audit-usage/pricing.json)`, and the
`🧩 Tokens per piece` table printed `—` for the `/new-feature` root. However, the nested
`🤖 java-spring-boot-developer` row in the same report was priced: `USD 6.17`.
`pricing.json` exists and is filled in.

---

## Symptom

| Where | Value printed | Expected |
|---|---|---|
| `🧩 Tokens per piece` · `/new-feature` (root, 25,883 billable) | `—` | a USD amount |
| `🧩 Tokens per piece` · `🤖 java-spring-boot-developer` (461,027 billable) | `USD 6.17` | a USD amount |
| `📊 Tokens (aggregate)` · `💰 estimated cost` (486,910 billable) | `— (fill in pricing.json)` | a USD amount |
| `history.jsonl` · `cost_usd` of the run | `null` | a USD amount |

The report header said `🤖 Model | claude-sonnet-5, claude-opus-5-5`.

## Root cause

`pricing.json` has no entry for the model the main thread ran on.

- The main thread (the `/new-feature` root) ran on **`claude-opus-5-5`**. The subagent ran on
  **`claude-sonnet-5`**.
- `pricing.json` lists `claude-opus-5`, `claude-sonnet-5` and `claude-haiku-4-5-20251001`. It
  was filled on 2026-09-26, before the session moved to Opus 5.5. It has no `claude-opus-5-5`.
- `ArchHook.auditUsd` (`.claude/hooks/ArchHook.java`, "Prices are data, never memory") looks up
  each model id with an **exact key match**: `get(pr, "models", e.getKey())`. There is no
  prefix or family fallback, so `claude-opus-5-5` does not resolve to `claude-opus-5`.
  That is correct: the two are different models and can have different prices.
- By design, "any model with usage and no price makes the whole amount unknown, not a partial
  sum". So:
  - the root piece has only `claude-opus-5-5` usage, so its cost is `null`;
  - the subagent has only `claude-sonnet-5` usage, so it is priced (`USD 6.17`);
  - the aggregate mixes both models, so it is `null`, even though 95 % of its billable
    tokens came from the priced subagent.

The hook behaved as documented. The data went stale. No price was invented, which is the
intended failure mode.

## Blast radius

`history.jsonl` shows the pattern. Every run on `claude-opus-5-5` has `cost_usd: null`, and
every run on `claude-opus-5` or `claude-sonnet-5` is priced:

| Runs | Model | `cost_usd` |
|---|---|---|
| 2026-09-28, 2 runs | `claude-opus-5` | priced |
| 2026-09-28 and 2026-09-30, 2 runs | `claude-sonnet-5` | priced |
| 2026-09-30, 7 runs | `claude-opus-5-5` | `null` |
| 2026-09-30T15:08:05, 1 run | `claude-sonnet-5, claude-opus-5-5` | `null` |

So `/audit-usage` totals since 2026-09-30 are partial (`*`). The runs of 2026-09-17 to
2026-09-26 carry no model at all. They predate the `model` field in the ledger and are out of
scope here.

## Why nothing flagged it

1. **The placeholder text points at the file, not at the gap.** `— (fill in pricing.json)`
   reads as "the file is missing or empty". The file was complete for every model it listed,
   so the message sent the reader to a file that looked correct. The report never names the
   model that has no price.
2. **The one signal was not in the first table.** The cost appeared only near the bottom of
   the report, in the aggregate table. The header showed the models but not the cost, so a
   reader of the summary saw no cost and no hint that one was missing.
3. **Nothing checks `pricing.json` against the models in use.** `arch-doctor` reports
   `pricing.json missing` (`ArchHook.java`, the `priced` check), but not "present, and missing
   the model this session runs on". A model change is a routine event, so this happens again
   on every model upgrade.

## What changed in this run

Nothing in this project's tooling. `ArchHook.java` and `ArchHook.jar` are shipped by the
blueprint, so a local edit is reverted by the next `arch-adopt` update. A header-row change was
tried here and then reverted. Every hook fix below lands in the meta-repository
(`claude-spring-architect`).

`pricing.json` was **not** changed. A price is a number that changes outside this repository.
It is written only from the official pricing page
(https://platform.claude.com/docs/en/about-claude/pricing), never from memory.

## Fixes still open

| # | Fix | Where |
|---|---|---|
| 1 | Add `claude-opus-5-5` to `pricing.json` from the official pricing page, and update its `$comment` date | `.claude/audit-usage/pricing.json` |
| 2 | Add a `💰 Estimated cost` row to the report header (the first table), after `🤖 Model`. Compute the cost once in `auditRender`, before the header, and reuse the same cell in the `📊 Tokens (aggregate)` table | `claude-spring-architect` · `ArchHook.auditRender` |
| 3 | Name the unpriced model(s) in the placeholder: `— (no price for claude-opus-5-5 in pricing.json)`, not `— (fill in pricing.json)` | `claude-spring-architect` · `ArchHook.auditUsd` / `auditRender` |
| 4 | In `arch-doctor`, compare the model ids in `history.jsonl` (or the current session model) against the keys in `pricing.json`, and warn on each id that has no price | `claude-spring-architect` · `ArchHook.doctor` |

Past reports are not recomputed. After fix 1, the 8 `null` rows of 2026-09-30 in `history.jsonl` stay `null`
unless the ledger is re-priced from its token counts.
