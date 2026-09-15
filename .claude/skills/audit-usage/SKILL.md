---
name: audit-usage
description: >
  Reads the execution trail the `audit` hook writes into `.claude/audit-usage/` and
  consolidates it across runs — spend per skill, duration, failure rate, permissions
  granted, and which report to open next. Answers "what has this project cost me so
  far", which no single report answers. Explicit invocation only.
argument-hint: "[empty for the consolidated view | last | <skill> | <report name fragment>]"
disable-model-invocation: true
allowed-tools: Bash, Read
---

## Trail on disk

!`ls -1t .claude/audit-usage 2>/dev/null`

## Ledger — one line per finished run

!`tail -n 60 .claude/audit-usage/history.jsonl 2>/dev/null`

## Pricing

!`cat .claude/audit-usage/pricing.json 2>/dev/null`

---

Everything below reads. This skill never writes into `.claude/audit-usage/` and never
edits project code — the trail is written by `ArchHook.java audit`, at `Stop`, and by
nothing else.

Answer in the language of the reports themselves (Portuguese), so the consolidated view
and the per-run report read as one document.

## When the three blocks came back empty

The trail is off: `.claude/audit-usage/` does not exist. That is the switch, not a
failure. Say so, and give the two commands that turn it on — creating the directory is
enough, the hook is already wired in `.claude/settings.json`:

```bash
mkdir -p .claude/audit-usage
printf '.claude/audit-usage/.state/\n' >> .gitignore
```

Then stop. Do not create the directory yourself: switching a versioned audit trail on is
the user's decision, not a side effect of asking what it holds.

If the directory exists but `history.jsonl` is missing, no run has **finished** yet. A
`<timestamp>--<skill>.md` may still be there from a run in progress — report it as such
and say the ledger line is written only when the run closes.

## Procedure

### 1 · Route on the argument

| Argument | Do this |
|---|---|
| empty | § 2 — consolidated view over the whole ledger |
| `last` / `último` | § 3 for the newest `.md` in the listing above |
| a skill name (`new-feature`, `init-project`, …) | § 2 filtered to that skill, then § 3 for its newest report |
| any other text | treat it as a fragment of a report filename; § 3 for the single match. Two or more matches: list them and ask which |

### 2 · Consolidated view

Parse the ledger block above — one JSON object per line, fields `iso`, `skill`,
`duration_ms`, `status`, `tokens_billable`, `cost`, `files`, `failures`, `report`.
`cost` is absent on runs recorded while `pricing.json` still had `null` prices; treat
absent as unknown, never as zero.

Render, in this order:

1. **Header line** — how many runs, over what period, how many distinct skills.
2. **Per-run table**, newest first, at most 15 rows: `| # | 🕐 Quando | 🎯 Skill | Status
   | ⏱️ Duração | 🧮 Faturável | 💰 Custo | 📁 Arq. | 🔁 Falhas |`. Reuse the status icon
   already in the ledger (`✅` / `⚠️` / `❌`). Older rows collapse into one
   `… mais N execuções` line.
3. **Totals** — sum of billable tokens, sum of cost, total wall-clock, run count.
   Sum only the runs that carry a `cost`, and say how many were left out.
4. **Spend per skill**, as a bar chart, descending, so the money pit is visible without
   arithmetic:

   ```text
   /new-feature      ███████████████████░░░░░  62%   1.240.500 tok   US$ 18,44   7×
   /init-project     ███████░░░░░░░░░░░░░░░░░  23%     460.100 tok   US$  6,84   2×
   ```

   Bars are 25 characters, `█` and `░`, proportional to billable tokens — not to
   duration, which is wall-clock and includes time the user spent thinking.
5. **Health** — failure rate (`failures > 0` over total), and the skill with the worst
   ratio. A skill that fails repeatedly is a bad spec, not bad luck.
6. **What to read next** — the path of the single most relevant report and the exact
   `/audit-usage <fragment>` that opens it.

### 3 · Single run

`Read` the `.md` file. It is already a finished, icon-rich report — **do not re-render
it and do not paste it back in full.** Summarize in at most six lines: what was run, the
outcome, what it cost, the longest step, and anything that deserves attention
(`⏳ em andamento`, compaction incidents, permissions added, repeated tool failures).
Then give the path so the user can open the whole thing.

Point out `⏳ em andamento` when you see it: it means the run never closed — the session
was killed, or it is still open right now.

## What this skill will not do

- **Never edits or deletes anything under `.claude/audit-usage/`.** Pruning old reports
  is the user's call; if they ask, show the command and let them run it.
- **Never recomputes a cost from memory.** The numbers come from the ledger and from
  `pricing.json`. If prices are `null`, the honest answer is "custo não configurado" plus
  the path to fill in — never an invented rate. A price written from memory is wrong the
  day after it changes, and it looks exactly as authoritative as a correct one.
- **Never reads `.state/`.** Those are the hook's open append-only logs; the rendered
  `.md` is the readable form.
- **Never quotes a redacted value.** The reports blank credential-shaped text on the way
  in. If something still looks like a live secret in a report, say so and point at
  `.claude/schemas/extensions.json`'s `audit.redact` block — a pattern is missing there.

## Why this is a skill, and why the hook ignores it

The hook already answers "what happened in **this** run". Nothing answers "what have the
last twenty runs cost, and which skill is eating the budget" — that needs the ledger
parsed and aggregated, which is a procedure, not a norm and not enforcement.

`audit-usage` is one of the skills the trail does **not** record — the observers, listed
in `.claude/schemas/extensions.json` under `audit.exclude_skills` alongside
`arch-doctor`. Without that, reading the trail would append a report about reading the
trail, and every later read would be mostly reads.

Invoking it still **closes** whatever run is open, and that is the useful half: within a
single session a report stays stamped `⏳ em andamento` until something ends the run, so
asking for the report is what finalizes it.

## Contract

- **Reads** — `.claude/audit-usage/*.md`, `.claude/audit-usage/history.jsonl`,
  `.claude/audit-usage/pricing.json`
- **Writes** — nothing
- **Handoff** — none. Terminal skill: it reports and stops
