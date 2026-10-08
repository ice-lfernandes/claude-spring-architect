# 0134 · The audit measures in billable tokens only — USD pricing and `pricing.json` are removed

- **Date:** 2026-10-08
- **Scenario:** "quero ja eliminar a precificacao em dolar de qualquer nivel" — after triaging
  issue #111 at `e834c9e`: drop USD pricing from the audit at every level (run report,
  per-piece table, summary, GENESIS.md, plan windows, doctor), remove `pricing.json`, and make
  billable tokens the only unit, with the model shown per piece
- **Decision:** option 1 — Form 7c, a change to the existing `audit` (render, `summary`,
  `genesis`) and `doctor` modes of `.claude/hooks/ArchHook.java`, with `audit.windows`,
  `audit.genesis` and `export` in `.claude/schemas/extensions.json`; `nerviz-cockpit` loses its
  spinner. No new piece
- **State:** approved by Lucas Fernandes, on 2026-10-08
- **Goes to the generated project:** yes — `ArchHook.java`, the jar and `extensions.json`
  travel whole through `export`; `export.retired` deletes the project's `pricing.json`

## Reproduced on disk

The confirmed rows of the `issue-verifier` table for #111, checked at `e834c9e` (= v0.20.0).
No commit since touches a cited file.

| Row | Claim | On disk |
|---|---|---|
| 9 | A closed run cannot be re-rendered after `pricing.json` is fixed | `ArchHook.java:5361` deletes the state log inside `if (closing)`; no mode renders a closed run. 0101 did not weigh repricing after the fact |
| 10 | One unpriced model blanks the whole run total; lookup is by exact id | `ArchHook.java:5936-5951` (`auditUsd`); 0101 rejected a partial sum on purpose |
| 11 | `claude-sonnet-5-5` is absent from `pricing.json` | `audit-pricing.json.example` lists opus-5-5, opus-5, sonnet-5, haiku-4-5 only |
| 12 | The per-piece table drops the model, though `Usage.model()` exists | `ArchHook.java:4761`; the model is written to `nodes.jsonl`, the table header (`:5048`) has no model column |
| 13 (first half) | Every cache write is priced at the 5-minute rate | Only `cache_creation_input_tokens` is read (`:5508`); the template pins the 5-minute rate. The 1-hour half stays unproven, and is moot without a price |

**The fact 0101 and 0133 did not weigh: the gap recurs on every model.** 0101 was opened by
`claude-opus-5-5` missing on 2026-10-01 (lessons-learned-018); row 11 is the same failure one
week later with `claude-sonnet-5-5`. 0101's answer — `doctor` names the gap — makes it visible,
not absent: every model Anthropic ships blanks every total in every generated project until
someone fetches the official page (invariant 8's discipline) and runs `/arch-adopt`. 0133
then divided by that same total, so its projection blanks with it. The `Unit` axis of 0133
chose USD because tokens are "model-blind"; that weight was taken without this recurrence.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Rows 9–13 above; the second unpriced-model incident in a week | create nothing |
| 7 — Mandatoriness | Information, not a guarantee | Form 7a gate, Form 8; axes 14–15 do not apply |
| 8 — Destination | Both — the `audit` and `doctor` modes travel inside `ArchHook.java`; the trail is wired only in the generated project | — |
| 16 — Existing mode | `audit` (render, `summary`, `genesis`) and `doctor` own every USD number; nothing new is needed | new Form 7c mode, any new piece |
| Plan windows (user) | **Keep, in tokens.** `plan-limits.json` becomes `{"pro": {"five_hour_tokens", "weekly_tokens"}}`; runs per window = budget × multiplier ÷ the run's billable tokens. Model-blind, accepted | remove the projection |
| Existing `pricing.json` (user) | **Deleted on the next `/arch-adopt`** through `export.retired` | leave it orphaned |
| Cockpit spinner (user) | **Removed from `nerviz-cockpit`.** It showed the turn's cost in USD (the runtime's own `$.session.usage().cost.usd`, not `pricing.json`) | keep it · show tokens instead |
| USD in prose (user) | **Rewritten in tokens** where a record holds the tokens of that run; the number is dropped where none does | keep as evidence |
| 17 — CI | `validate · hooks-cross-platform › AuditRenderTest` and `› GenesisTest` already render a run, `summary`, `doctor` and `genesis` in a throwaway project; `mods.yml` runs the cockpit's `claude plugin test` | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Tokens only, every level: `audit` render / `summary` / `genesis` / `doctor`, plan windows in tokens, `pricing.json` retired, cockpit spinner removed | 8 | **Approved** |
| 2 | Tokens as the headline, USD kept as an optional column when `pricing.json` exists | 5 | Rejected — keeps the 0101 failure for the projects that read cost |
| 3 | Keep USD and fix the price defects: family fallback, 1-hour cache-write rate, re-render of a closed run | 3 | Rejected — a guessed rate is worse than a blank, and each fix keeps a table current by hand |
| 4 | Create nothing — 0101 and 0133 stand | 2 | Rejected — the gap recurred twice in a week |

### Option 1 — tokens only, every level (score 8)

**Motivator:** axis 1 — the price table fails on every new model, and every USD number
inherits that; the user's answer on each level.

**What changes, by level:**

- **Run report.** The header loses `💰 Estimated cost`. `🧩 Tokens per piece` replaces the
  `💰 Cost` column with `🤖 Model` — the root's own models and each piece's, from `Usage.model()`
  (row 12). `📊 Tokens (aggregate)` loses the cost row; `🧮 billable (input + output + cache
  write)` is the headline.
- **Ledgers.** `history.jsonl` and `nodes.jsonl` stop writing `cost_usd` and `cost_self_usd`.
  Old rows keep them; nothing reads them. `runCost` and the legacy pt-BR `cost` parser go.
- **`audit summary`.** The `💰 Cost` column and the `cost:` total go; billable stays.
- **Plan windows (0133).** The same section and table, divided by billable tokens: the report
  by the run's `tokens_billable`, the summary by the mean of each piece's — root runs whole,
  chained pieces their own `tokens_self`. `audit.windows.*.budget` becomes `five_hour_tokens` /
  `weekly_tokens`, so `doctor`'s data-driven check refuses a 0133-era `*_usd` key by name.
  The note under the table says a token weighs the same on every model there. Old
  history rows carry tokens, so the projection now also covers runs that were never priced.
- **`audit genesis`.** The `{{cost}}` placeholder and the `Cost` row of `GENESIS.md.example`
  go; `audit.genesis.placeholders` keeps three. A GENESIS already filled is not touched.
- **`📈 What grew the context`.** Its `💰 Est.` column — the re-read priced at the piece's
  cache-read rate — goes; re-read tokens stay.
- **`doctor`.** The `Audit` line counts runs and nothing else: no pricing file, no unpriced
  model, no `AUDIT_RECENT_RUNS` read of `history.jsonl` for models (the constant stays for
  `summary`).
- **`pricing.json`.** `audit-pricing.json.example` is deleted, its `export.copy` entry
  removed, and `.claude/audit-usage/pricing.json` added to `export.retired`.
- **`nerviz-cockpit`.** The `Spinner` render hook, the `turn.step` hook and the session-cost
  tracking go; `turn.start` / `turn.complete` stay for the dialog's turn id. The band, the
  pane and the dialog are unchanged.
- **Prose.** USD measurements become the tokens their records hold: 0117's UC-005 run is 203
  turns, 58.8M cache read, 477k peak; 0130's fact 3 is 531,362 billable tokens, 427,889 peak.
  A USD figure no record backs with tokens is dropped.

**Pros:** every defect of rows 9–13 disappears with the price table instead of being patched
— there is nothing to re-render, no id to look up, no rate to pick. No number in the trail
depends on a page this repository must re-read by hand (invariant 8's discipline stops costing
a release per model). One reader fewer of a project file; `doctor` loses a check that was red
on every model launch.

**Cons:**
- **Model-blind.** A million billable tokens on Opus and on Sonnet look the same, though they
  weigh differently on the bill and on the plan. The `🤖 Model` column per piece is the
  mitigation, not a cure. Plan windows inherit it: a budget observed on an Opus-heavy week
  over-projects a Sonnet piece and the reverse.
- Cache reads are outside `billable`, as today; how much they weigh on a plan is unpublished.
- A project that read cost in the trail loses it. The runtime's own `/cost` still answers per
  session.
- A `plan-limits.json` written under 0133 (shipped today, v0.20.0) fails `doctor` by name until
  its keys are renamed.

**Points cut in the rubric:** 5 (maintenance — a wide change across the mode, two tests, a mod,
templates and both doc trees for a net removal), rounded — 8/9 → 8.

**CI:** existing steps, cases changed. `validate · hooks-cross-platform › AuditRenderTest`: the
report has no `💰` cell and has a `🤖 Model` column per piece; `summary` has no cost column;
`doctor` passes with no `pricing.json`; plan windows from a token budget give the expected runs
per plan, the time cap still binds, a `five_hour_usd` key fails `doctor` by name. `›
GenesisTest`: three placeholders filled, no cost written though a `pricing.json` is left in
the trail. `validate › export-determinism`: `pricing.json` is deleted from a target that has
it. `mods.yml` › `claude plugin test`: the spinner test becomes "the spinner is left as the
engine draws it". Red runs: the budget divided by `cost_usd` again; the spinner hook restored.

### Option 2 — tokens first, USD optional (score 5)

`pricing.json` stops shipping; a project that writes one gets a cost column back. Keeps the
0101 failure for exactly the projects that care about cost, keeps every reader of the file and
the `doctor` check, and keeps the plan windows' unit question open. **Cut:** 4 (half the
defect stays), 5 (two code paths), 7 (two documented behaviours).

### Option 3 — keep USD, fix the price (score 3)

A family fallback (`claude-sonnet-5-5` → `claude-sonnet-5`) prices a model at another model's
rate — a confident wrong number, which 0101 rejected as worse than a blank. The 1-hour rate
needs the transcript's split, unproven (row 13). Re-rendering a closed run needs the state log
kept or the transcripts re-read. Each is new code to keep a table current by hand. **Cut:** 1,
2 (strains invariant 8's discipline: a guessed rate), 4, 5, 6.

### Option 4 — create nothing (score 2)

0101 and 0133 stand. The next model blanks every total again, as it did twice in a week.
**Cut:** 1, 4, 5, 7, 9.

## References

| Claim | Source |
|---|---|
| A price is data from the official page, never memory | `@CLAUDE.md` invariant 8; `auditUsd` Javadoc |
| The gap recurred | `@.claude/decisions/0101-lessons-learned-018-unpriced-model.md` (opus-5-5); #111 row 11 (sonnet-5-5) |
| Plan windows divide the USD total; tokens rejected as model-blind | `@.claude/decisions/0133-audit-plan-window-projection.md` § Interview, `Unit` |
| `export.retired` deletes a file from the target on the next export | `extensions.json` `export.$comment_retired`; `@.claude/decisions/0082-rules-without-paths-load-at-launch.md` |
| A superseded record gets one line, not a rewrite | `@.claude/decisions/README.md`; precedent `0046` (superseded by `0110`) |
| A mod draws on top of a guarantee and is meta-repo only | `@.claude/decisions/0131-mods-in-architect-designer.md` |
| Token figures of the measured runs | `0117` § Inside the executor (UC-005); `0130` fact 3 |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | `auditUsd`, `auditCost`, `auditUnpriced`, `auditCostCell`, `money`, `usd`, `runCost`, `ldbl` deleted; header and aggregate lose the cost row; `🧩 Tokens per piece` swaps `💰 Cost` for `🤖 Model`; `📈 What grew the context` loses `💰 Est.`; `history.jsonl` / `nodes.jsonl` stop writing `cost`, `cost_usd`, `cost_self_usd` (`nodeRow` loses its `dir` parameter); `audit summary` loses the cost column, the cost total and the per-piece amount; `planCell`, `planWindowsSection`, `planWindowsSummary` divide billable tokens; `doctor`'s `Audit` line counts reports only; `audit genesis` fills three placeholders; `Growth.model` dropped |
| `.claude/hooks/ArchHook.jar` | Rebuilt under JDK 21; `build --verify` green |
| `.claude/schemas/extensions.json` | `audit.windows.*.budget` → `five_hour_tokens` / `weekly_tokens`; `$comment_plans`, `$comment_project_overrides`, `$comment_genesis`; `audit.genesis.placeholders` without `cost`; `export.copy` without the price template; `export.retired` + `.claude/audit-usage/pricing.json` |
| `.claude/skills/project-bootstrap/templates/audit-pricing.json.example` | Deleted |
| `.claude/skills/project-bootstrap/templates/GENESIS.md.example` | No `Cost` row, no `{{cost}}`, no pricing sentence |
| `.claude/skills/project-bootstrap/SKILL.md` · `references/project-files.md` · `references/verify-and-report.md` | No price template, no `pricing.json`; "The trail counts tokens, never money" replaces "`pricing.json` ages on its own" |
| `.claude/skills/init-project/SKILL.md` | `audit genesis` fills three figures, shows three lines |
| `.claude/skills/audit-usage/SKILL.md` | Description and vocabulary in tokens; `*` partial-sum entry gone; plan-limits keys in tokens; "Never prices the trail" |
| `.claude/agents/java-spring-boot-developer.md` · `.claude/skills/new-feature/SKILL.md` | USD figures rewritten as the tokens 0117 and 0130 hold |
| `.claude/mods/nerviz-cockpit/hooks/register.ts` · `tests/cockpit.test.ts` · `.claude-plugin/plugin.json` · `../.claude-plugin/marketplace.json` | `Spinner` render, `turn.step` hook and session-cost tracking removed; the spinner test asserts the suffix is left alone; descriptions without the spinner |
| `.claude/.ci/AuditRenderTest.java` · `GenesisTest.java` | Cases below |
| `.github/workflows/validate.yml` | Comments on the `AuditRenderTest` and `GenesisTest` steps |
| `.claude/decisions/0101…` · `0133…` · `0131…` | One superseded line each, after `State` (precedent `0046`) |
| `README.md` · `docs/{en,pt-br}/02, 03, 04, 06, 07, 08, 09, 10, 11` | Tokens, the model column, the plan-limits keys in tokens, `doctor`'s `Audit` row, `retired` deleting `pricing.json`, the spinner gone; the UC-007 USD split dropped (no record holds its tokens) |

Not changed, on purpose: `.claude/lessons-learned/*` and older decisions quote USD as history;
`claude-help.md` and `docs/*/claude-code-docs/` describe the runtime's own cost display.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · hooks-cross-platform › audit renders where the run spent, …` (`AuditRenderTest`) | No `💰` and no `USD` in a report though a `pricing.json` is in the trail; the `🤖 Model` column per piece, root and agent; no `cost` field in either ledger; `doctor`'s `Audit` line counts reports and never names `pricing`; `summary` shows no amount, even a legacy row's; plan windows: a 200-token run gives Pro 5 / 25, Max 5x 25, Max 20x 100; the summary's mean of 50 tokens caps Max 20x by time at 300; `doctor` refuses `five_hour_tokens: -1`, `monthly_tokens` and a 0133-era `weekly_usd` by name | Green, 72 checks. Red with the multiplier dropped from `planCell`: 3 checks fail by name. Red with the `💰 Cost` column restored: 2 checks fail by name. Source and jar restored; `build --verify` green |
| `validate · hooks-cross-platform › audit genesis fills GENESIS from the transcripts, once` (`GenesisTest`) | Three placeholders filled, tokens exact, no cost written though a `pricing.json` is in the trail; a filled record and an unknown session refused | Green. Red with a cost appended to the tokens cell: `no cost, though a pricing.json is left in the trail` fails |
| `validate › export-determinism` | `export.retired` deletes `.claude/audit-usage/pricing.json` from a target | Checked by hand: `export <tmp> --blueprint hexagonal` prints `Retired ........... 1 (deleted)` naming it; `schema` refuses a retired path that still exists here |
| `mods › claude plugin test` (`cockpit.test.ts`) | The spinner is left as the engine draws it while a phase is open | Green, 6/6 on 2.1.293. Red with the old `register.ts`: `Expected "…"`, `Received "… · claude-code-architect-designer"` |
| `validate › schema` | `extensions.json` parses; no dangling `export.copy` source | Green; every `validate` test green locally |
