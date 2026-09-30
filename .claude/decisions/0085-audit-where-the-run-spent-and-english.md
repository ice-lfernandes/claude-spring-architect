# 0085 · The audit trail says where a run spent, and speaks English

- **Date:** 2026-09-30
- **Scenario:** "analise a skill audit-usage e faça recomendações de como podemos enriquecer a
  auditoria de uso — quero também que o resumo seja na língua inglês"
- **Decision:** Option 1 — extend the existing `audit` mode of `.claude/hooks/ArchHook.java`
  (no new mode, no new registration), `.claude/skills/audit-usage/SKILL.md` in English, and a
  CI replay in `.claude/.ci/AuditRenderTest.java`.
- **State:** approved by Lucas Fernandes, on 2026-09-30 — option 1

## Interview

Only the axes that eliminated forms or options.

| Axis | Answer | Forms or options it eliminated |
|---|---|---|
| 1 — concrete symptom | "Where the run spent" is the question the trail fails today: which turns were the most expensive, how close the context got to compaction, how many calls per tool, and what the failures actually said. Cost per use case, run-to-run regression and Bash-written files were offered and **not** selected | Every enrichment outside those four |
| 2 — trigger | Unchanged: the capture is lifecycle events already wired (0035, 0038); the reading is `/audit-usage` | 4, 5, 6 |
| 7 — mandatoriness | Capture must exist always — settled in 0035; this changes what the existing capture derives, not whether it exists | 1, 2 as the capture |
| 8 — destination | Generated project only. The meta-repo stays unwired, as 0035 decided | Wiring `.claude/settings.json` here |
| cost budget | **Zero new event and zero new registration**: derive at render from what `auditRender` already reads (main and subagent transcripts) | A `PreToolUse` matcher on every tool |
| surface | Per-run report **and** `audit summary` — the ledgers gain fields so the summary aggregates across runs | Report-only option |
| language | **Everything the hook writes** — per-run report, `audit summary`, and `/audit-usage`'s answer — in English | Translating only in the skill; a configurable locale |
| legacy ledger | Read both: old rows keep their Portuguese status; classification is by the status emoji, which `audit summary` already does | Migrating `history.jsonl` in place |

## What was verified before proposing

- `auditRender` already opens the main transcript (`usageTurns`) and every subagent
  transcript (`subagentTranscripts`) on each render. Tool calls and tool errors are on the
  same lines: an assistant line carries one `{"type":"tool_use","name":…,"id":…}` block, a
  user line carries `{"type":"tool_result","tool_use_id":…,"is_error":true,…}` — checked
  against this session's own transcript on 2026-09-30. Observed layout, not documented:
  same caveat 0038 took for the subagent transcripts.
- The `fail` event records only `tool_name`; the report's "Retrabalho" section can only
  count, never say what failed.
- Every user-facing string of the report and the summary is Portuguese, hardcoded in
  `auditRender` / `auditSummary`, with `Locale pt-BR` for number grouping (`1.234`) and
  money (`USD 18,44`). `/audit-usage`'s body pins its answer to Portuguese "so the
  consolidated view and the per-run report read as one document" — the same argument now
  pulls toward English, since every other file of the generated `.claude/` is English.
- `runCost` parses legacy `cost` strings by deleting `.` and turning `,` into `.` — an
  English `USD 18.44` fed to it would read as 1844. New rows always carry `cost_usd`
  whenever they carry `cost`, so `runCost` never reaches that branch for them, but the
  parser has to accept both forms before the format changes.
- The Bash-written-files gap from lessons-learned-014 is **still open**: `audit file`
  matches `Write|Edit|MultiEdit|NotebookEdit` only. Not selected in axis 1; recorded here
  so nobody reads this change as having closed it.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Extend `auditRender` / `auditSummary`: one transcript scan yields tool calls, top turns, peak context and error text; ledgers gain two fields; every string in English | 8 | **Approved** |
| 2 | Option 1 limited to the per-run report — ledgers and summary untouched | 6 | Viable with caveat — contradicts the surface answer |
| 3 | `PreToolUse` on every tool to count calls, `audit fail` extended with the error text | 4 | Rejected — a JVM per tool call |
| 4 | Keep the hook in Portuguese; `/audit-usage` translates what it renders | 4 | Rejected — contradicts the language answer; the model re-renders what the hook formatted |
| 5 | Create nothing — `/cost` plus reading the transcript by hand | 3 | Rejected — answers none of the four questions |

### Option 1 — extend the existing `audit` mode (score 8)

**Motivator:** axis 1 (the four questions) under the zero-new-event budget.

**What changes, all inside the existing `audit` mode — no new mode, no new registration:**

1. **One scan per transcript.** `usageTurns` becomes a scan that returns, alongside the
   deduplicated usage turns it returns today, the `tool_use` names per message and the
   `tool_result` errors (`is_error: true`) with their timestamp and the tool name resolved
   through `tool_use_id`. Lines are still pre-filtered by substring before any JSON parse
   (`"usage"`, `"tool_use"`, `"is_error":true`), so the render cost grows by the parses of
   those lines, not by a second read.
2. **Attribution follows the token rule already in 0038.** An agent's calls and errors come
   from its own transcript; a main-thread turn belongs to the last main-thread skill started
   before it, otherwise to the root. No second attribution rule to explain.
3. **New report section `🔎 Where the run spent`:**
   - *Tool calls per piece* — count per tool name, per piece (root, nested skills, agents).
   - *Most expensive turns* — top 3 by billable tokens: timestamp, owning piece, tools that
     turn called.
   - *Peak context* — the largest single request (`input + cache read + cache write`), its
     piece and timestamp. An absolute number only: the model's window size is not written
     from memory (the discipline of invariant 8, applied to limits as 0035 applied it to
     prices).
   - *Failures* — "Rework" keeps its count per tool and gains one line per distinct error:
     tool, first line of the error, truncated to 160 characters, **passed through
     `audit.redact`** before it reaches the versioned report (invariant 11 — an error
     message can echo a command line holding a token).
4. **Ledgers.** `history.jsonl` and `nodes.jsonl` gain `tool_calls` (`"Bash:5,Read:12"` —
   `ev()` writes strings only) and `peak_context`. Absent on legacy rows, read as unknown,
   never as zero — the same rule `tokens_self` follows.
5. **`audit summary`.** The per-piece section gains the peak context seen across that
   piece's runs, and a bounded *Tool calls per piece* block (top five tools per piece), so
   the output stays bounded whatever the ledger size — the constraint 0038 set.
6. **English.** Every string in `auditRender` and `auditSummary`, the status labels
   (`✅ success`, `⚠️ success with recovered failures`, `❌ error`, `⏳ in progress`,
   `⏳ waiting for background subagent`), and number and money formatting on
   `Locale.ROOT` (`1,234`, `USD 18.44`). `runCost` accepts both decimal forms first. Status
   stays classified by its leading emoji, so legacy rows need no migration.
7. **`/audit-usage`.** The language line flips to English; the vocabulary list is rewritten
   in the new terms (piece, origin, root · nested · preloaded, own tokens); § 3's six-line
   summary gains "where it spent".
8. **A CI replay.** `.claude/.ci/AuditRenderTest.java` feeds a fixture transcript (usage,
   tool calls, one error holding a token) through `audit flush` and `audit close` and
   asserts the section, the ledger fields, and the redaction. A render that throws exits 0
   through `main`'s catch and freezes every later report — that is exactly how 0041's
   crash went unseen — and a parser over an undocumented layout is where the next one will
   come from.

**Pros:** answers all four selected questions; zero model tokens and zero extra JVMs per
event; reuses the transcript reads, the attribution rule and the redaction already in the
file; the English switch aligns the trail with the rest of the generated `.claude/`.

**Cons:** `ArchHook.java` grows again (~200 lines) in a family already flagged as too big
(lessons-learned-015 P, 0079). The new data rides on the transcript layout, which is
observed, not documented — a changed layout reads as empty sections, never as a crash, and
the CI replay is what notices it. Reports written before the change stay Portuguese; the
directory mixes two languages until those age out.

**Points cut in the rubric:** criterion 5 (maintenance — more surface in the monolith, on an
undocumented format). Criterion 9 holds only because nothing new is registered; the extra
parses land on a `Stop` render that already reads the same files. 8.

### Option 2 — report only (score 6)

Same scan and section, ledgers untouched. Smaller diff; nothing aggregates across runs, so
"which piece burns calls on `Bash`" still needs opening every report. Contradicts the
surface answer; kept as the fallback if the ledger change proves contentious.

### Option 3 — a `PreToolUse` on every tool (score 4)

Exact counts without the transcript, and the error text from the `PostToolUseFailure`
payload. Costs one JVM (~0.3 s since 0075) on every tool call of every session — the bill
lessons-learned-015 A already complained about — to observe what the transcript already
holds. Rejected by the cost answer and by criterion 9.

### Option 4 — translate in the skill (score 4)

No hook change: `/audit-usage` renders the Portuguese block in English. The reports on disk
stay Portuguese, the model re-renders what the JVM already formatted — the exact thing the
skill forbids ("never recompute") — and nothing in it answers "where the run spent".

### Option 5 — create nothing (score 3)

`/cost` reports a session, not a piece; the transcript answers every question, one `grep`
at a time, for whoever knows its layout.

## What implementation changed from the proposal

Written after the code, so nobody reads Option 1 above as the shipped design.

- **Four ledger fields in `history.jsonl`, not two.** `tool_calls` and `peak_context` hold
  the run's total, `tool_calls_self` and `peak_context_self` the root's own — the same
  split `tokens_billable` / `tokens_self` already makes, and for the same reason:
  `audit summary` sums per piece from the `_self` pair plus `nodes.jsonl`, so a nested
  piece is never counted twice. `nodes.jsonl` gains `tool_calls` and `peak_context`.
- **A failed `Bash` is not reported as `Exit code 1`.** Replaying the render over this
  session's own transcript showed every Bash failure opening with that line — three
  identical rows that said nothing. When the first line is `Exit code N` and another line
  follows, the report shows `exit N · <next line>`, redacted like the rest.
- **The error text is sanitized for Markdown** — `|` and backtick replaced — so a quoted
  error cannot break the table it sits in.
- **"Rework" also lists errors when no `fail` event arrived.** `PostToolUseFailure` and the
  transcript's `is_error` do not always agree; the section now says "No tool failed" only
  when both are empty. The run's status still reads `fail` events only, as before.
- **`Where the pieces spent` in `audit summary`** is one line per piece — calls, peak, top
  five tools — sorted by calls, and absent until a run recorded by this hook has closed.
  The `Latest runs` table gained `Calls` and `Peak` columns, `—` for older rows.
- **Verified** three ways: `AuditRenderTest` (fixture: repeated usage lines, a subagent
  transcript, a Bash error holding a token, a legacy pt-BR ledger row); a manual replay of
  `audit close` and `audit summary` over this session's real transcript (53 tool calls,
  peak 217,617, four tool errors in two distinct rows, all rendered); `build --verify` on the rebuilt jar.
  Not yet exercised in a live generated project.

## References

| Claim | Source |
|---|---|
| The capture is a hook; the reading is a skill | `@.claude/decisions/0035-auditoria-execucao-hook.md`, `@.claude/decisions/0036-skill-audit-usage.md` |
| Token attribution rule, subagent transcript lookup, summary must stay bounded | `@.claude/decisions/0038-audit-trail-every-skill-and-agent.md` § What implementation changed |
| A render that throws exits 0 and freezes the trail | `@.claude/decisions/0041-audit-background-subagent-tracking.md` |
| `tool_use` / `tool_result` / `is_error` line shapes | this session's transcript, inspected 2026-09-30 — observed, not documented |
| JVM cost per hook firing | `@.claude/lessons-learned/lessons-learned-015.md` § A · `@.claude/decisions/0075-precompiled-hook-jar.md` |
| Bash-written files still missing from the trail | `@.claude/lessons-learned/lessons-learned-014.md` § 1 · `project-bootstrap/templates/settings.json.example` `audit file` matcher |
| Error text reaches a versioned file, so it is redacted | `@CLAUDE.md` invariant 11 · `extensions.json` `audit.redact` |
| No window size or price from memory | `@CLAUDE.md` invariant 8 |
| Lists the hook reads live in `extensions.json` | `@CLAUDE.md` invariant 10 — nothing new to list here: tool names come from the transcript |
| Rubric | `.claude/skills/claude-code-architect-designer/references/decision-matrix.md` § 8 |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | `scan()` replaces the body of `usageTurns` (one pass: usage, `tool_use`, `is_error`); `Spend`, `CostlyTurn`, `ToolErr`, `Scan`; `mainOwner()` extracted from the token loop; `🔎 Where the run spent` section; four `history.jsonl` fields and two `nodes.jsonl` fields; `audit summary` columns and block; every report and summary string in English; `Locale.ROOT` for numbers and money (the `PT` constant is gone); `runCost` reads both cost formats |
| `.claude/hooks/ArchHook.jar` | Rebuilt; `build --verify` passes |
| `.claude/.ci/AuditRenderTest.java` | New |
| `.github/workflows/validate.yml` | Step running it, in the `hooks` job on every OS |
| `.claude/skills/audit-usage/SKILL.md` | Answers in English; vocabulary list in the new terms plus `Calls` and `Peak`; § 2 gains "where it went"; § 3's six lines include where the run spent |
| `.claude/schemas/extensions.json` | `audit.$comment`: `⏳ em andamento` → `⏳ in progress` |
| `docs/en/08-audit-usage.md`, `docs/pt-br/08-audit-usage.md` | Samples in English, the new section, the new ledger fields, legacy-row note |
| `README.md` | Audit sample in English; one line on the new section |

The other `docs/pt-br/*` hits for `em andamento` / `pré-carregada` are Portuguese prose, not
quotes of the report, and were left alone.

Goes to the generated project: **yes** — `ArchHook.java`, the jar, `extensions.json` and
`audit-usage` already travel through `export`; no template and no registration change.
