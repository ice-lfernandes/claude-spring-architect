---
name: audit-usage
description: >
  Reads the execution trail the `audit` hook writes into `.claude/audit-usage/` and
  consolidates it across runs — spend per skill and agent, duration, failure rate,
  permissions granted, and which report to open next. Answers "what has this project
  cost me so far", which no single report answers. Explicit invocation only.
argument-hint: "[empty for the consolidated view | last | <skill or agent> | <report name fragment>]"
disable-model-invocation: true
allowed-tools: Bash, Read
---

## Consolidated trail

!`java "${CLAUDE_PROJECT_DIR:-.}/.claude/hooks/ArchHook.java" audit summary 2>&1`

---

Everything below reads. This skill never writes into `.claude/audit-usage/` and never
edits project code — the trail is written by `ArchHook.java audit`, at `Stop`, and by
nothing else.

Answer in the language of the reports themselves (Portuguese), so the consolidated view
and the per-run report read as one document.

## When the block came back empty

The trail is off: `.claude/audit-usage/` does not exist, and `audit summary` prints
nothing at all. That is the switch, not a failure. Say so, and give the two commands that
turn it on — creating the directory is enough, the hook is already wired in
`.claude/settings.json`:

```bash
mkdir -p .claude/audit-usage
printf '.claude/audit-usage/.state/\n' >> .gitignore
```

Then stop. Do not create the directory yourself: switching a versioned audit trail on is
the user's decision, not a side effect of asking what it holds.

If the block says `execuções fechadas: 0`, no run has **finished** yet. Any report listed
under `⏳ Sem linha no ledger` is from a run in progress — report it as such and say the
ledger line is written only when the run closes.

## What the block already did

`ArchHook.java audit summary` reads `history.jsonl` (one line per top-level run) and
`nodes.jsonl` (one line per skill or agent that run chained) and does the arithmetic:
totals, spend per piece without double counting, failure rate, and the reports worth
opening. The aggregation is Java on purpose — the ledgers grow without bound, and
injecting them raw would cost tokens on every read and leave the sums to you.

**Never recompute a number the block printed, and never read the `.jsonl` files to
re-derive one.** Your job is rendering and judgment, not arithmetic.

What the block's vocabulary means:

- **Peça** — a skill (`📘`) or an agent (`🤖`) of this project. Plugin skills and runtime
  agents are not recorded.
- **Origem** — `usuário` typed `/<skill>`; `modelo` invoked the piece on its own, with no
  run open.
- **raiz · aninhada · pré-carregada** — the piece opened its own run; it was chained
  inside another run; or it was loaded through an agent's `skills:` frontmatter (no
  tokens of its own — they are its agent's).
- **tokens próprios** — a piece's own spend. A run's total minus its chained pieces is
  the root's own; that is why the per-piece bars add up to the totals instead of
  exceeding them.
- **`*` after a cost** — partial sum: some invocations had no price configured.

## Procedure

### 1 · Route on the argument

| Argument | Do this |
|---|---|
| empty | § 2 — consolidated view |
| `last` / `último` | § 3 for the `mais recente` report the block names |
| a skill or agent name (`new-feature`, `java-spring-boot-developer`, …) | § 2 reduced to that piece's line, then § 3 for its newest report: `ls -1t .claude/audit-usage/*--<name>.md` when it ran as a root; otherwise the newest `run` of its line in `grep '"skill":"<name>"' .claude/audit-usage/nodes.jsonl` — a chained piece's report is its parent's |
| any other text | treat it as a fragment of a report filename (`ls -1t .claude/audit-usage`); § 3 for the single match. Two or more matches: list them and ask which |

### 2 · Consolidated view

Present the block, in its order, keeping its tables and the `text` bar chart intact —
bars are proportional to billable tokens, not to duration, which is wall-clock and
includes time the user spent thinking. Then add what the block can't:

1. **One line of reading** above the tables — where the money went, in words.
2. **Health** — name the piece with the worst failure ratio. A piece that fails
   repeatedly is a bad spec, not bad luck.
3. **What to read next** — the single most relevant report, and the exact
   `/audit-usage <fragment>` that opens it.

If the block says `custo: não configurado`, say so and point at
`.claude/audit-usage/pricing.json` — never fill the gap with a price.

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
- **Never recomputes a cost from memory.** The numbers come from `audit summary`, which
  prices from `pricing.json`. If prices are `null`, the honest answer is "custo não configurado" plus
  the path to fill in — never an invented rate. A price written from memory is wrong the
  day after it changes, and it looks exactly as authoritative as a correct one.
- **Never reads `.state/`.** Those are the hook's open append-only logs; the rendered
  `.md` is the readable form.
- **Never quotes a redacted value.** The reports blank credential-shaped text on the way
  in. If something still looks like a live secret in a report, say so and point at
  `.claude/schemas/extensions.json`'s `audit.redact` block — a pattern is missing there.

## Why this is a skill, and why the hook ignores it

The hook already answers "what happened in **this** run", and `audit summary` does the
sums across runs. Nothing turns that into "which piece is eating the budget, and which
report should I open" — judgment over data, routed by an argument, which is a procedure,
not a norm and not enforcement.

`audit-usage` is one of the skills the trail does **not** record — the observers, listed
in `.claude/schemas/extensions.json` under `audit.exclude_skills` alongside
`arch-doctor`. Without that, reading the trail would append a report about reading the
trail, and every later read would be mostly reads.

Invoking it still **closes** whatever run is open, and that is the useful half: within a
single session a report stays stamped `⏳ em andamento` until something ends the run, so
asking for the report is what finalizes it.

## Contract

- **Reads** — the output of `ArchHook.java audit summary`; `.claude/audit-usage/*.md`;
  `.claude/audit-usage/nodes.jsonl` only to find the report of a chained piece
- **Writes** — nothing
- **Handoff** — none. Terminal skill: it reports and stops
