# 0035 · The execution trail of the orchestrator skills is a hook, not a skill

- **Date:** 2026-09-15
- **Scenario:** "quero criar um histórico de auditoria ao final de cada skill orquestradora
  (disable-model-invocation: true)" — in the generated project, not in this meta-repo.
- **Decision:** Out of the six forms — `audit` mode in `.claude/hooks/ArchHook.java`,
  wired into `project-bootstrap/templates/settings.json.example`. No skill created.
- **State:** approved by Lucas Fernandes, on 2026-09-15

## Interview

Only the axes that eliminated forms.

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — concrete symptom | Invisible cost; pipeline debugging; reproducibility/compliance | — (three real symptoms; anti-pattern 9 does not apply) |
| 2 — trigger | End of an orchestrator skill's execution — a lifecycle event | 4, 5, 6 |
| 4 — territory | Every skill in the generated project with `disable-model-invocation: true` | — |
| 6 — isolation | None: no verbose output to hide, no tools to restrict, no model to change | 3 |
| 7 — mandatoriness | **Always, without exception** — must survive the model forgetting, the session dying, and Ctrl+C | **1, 2** — and with them all six forms |
| 8 — destination | Generated project only | Meta-repo `settings.json` stays unwired |
| 11-13 — MCP | Not applicable: no external system | 6a, 6b |

Axis 7 is the one that decided it. § 2 of the decision matrix reads top to bottom and
its first row — "must it happen always, without depending on the model's judgment?" —
matched before any of the six forms was considered.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `ArchHook.java audit` + wiring in the bootstrap template | 9 | **Approved** |
| 2 | Option 1 + a Form 2 skill `/audit-usage` to query the history | 7 | Rejected — nothing to query yet |
| 3 | Form 1 skill that renders the report at the end of each orchestrator | 3 | Rejected — persuasion where a guarantee existed |
| 4 | Create nothing (`/cost` + the transcript) | 4 | Rejected — covers none of the three symptoms |

### Option 1 — `ArchHook.java audit` (score 9)

**Motivator:** axis 7. The trail has to exist even when the execution dies halfway, and
only a lifecycle event delivers that.

**Pros:** deterministic; zero tokens spent producing the report; zero permanent context
cost; reuses `Json`, `frontmatter()`, `glob()`, `run()` and `relative()` already in the
file; the set of audited skills is data (`disable-model-invocation: true` in the
frontmatter), so a skill written later is audited without this Java being touched —
invariant 7.

**Cons:** fixed layout, no natural-language synthesis. `ArchHook.java` gains its first
mode that *produces an artifact* instead of blocking one. The payloads of
`PermissionRequest`/`PermissionDenied` are not documented in `@claude-help.md` § 8 (only
the event names are), so what they capture is best-effort — which is why "permissions
added" is derived from a diff of `settings.local.json` instead, and the events only
count requests.

**Points cut in the rubric:** § 8 criterion 6 (precedent in the repo) — no prior mode
writes an output file. 8.75, rounded to 9.

**Design that the code alone does not explain:**

- **Append-only, report derived.** Every phase appends one line to
  `.claude/audit-usage/.state/<session>.ndjson`; the Markdown is rebuilt from that log
  at every `Stop`. A `kill -9` during a read-modify-write of a structured file loses
  everything; an append loses at most the last line.
- **The directory is the on/off switch.** No `.claude/audit-usage/` means every phase
  returns immediately. That is how this meta-repo pays nothing for a mode it doesn't
  use, and how a generated project opts out: delete the directory.
- **`.state/` is gitignored, the reports are not.** The live log of a run in progress
  would leave a dirty working tree at every `Stop`; the finished reports and
  `history.jsonl` are the artifact and are versioned.
- **Prompt redaction is mandatory, and its patterns live in
  `extensions.json`'s `audit.redact` block.** The prompt goes into a versioned file; a
  token pasted into it would be irreversible in git history — invariant 11. The
  patterns live with every other pattern list this hook reads, invariant 10. `args` is
  redacted along with `prompt`: it is a slice of the same text and it is what the
  report puts in its title.
- **Prices are data with null defaults.** `pricing.json` ships unfilled and the report
  prints "não configurado" rather than a confident `US$ 0.00` — the same reason
  invariant 8 forbids writing Java and Spring versions from memory.
- **Rules are inferred, and the report says so.** No hook event exposes which rule
  entered context. The report crosses the files touched against each rule's `paths` and
  labels the section "inferred by territory" — the rules that *should* have loaded.
  Self-declaration by the model was rejected: it is persuasion, and axis 7 said always.
- **Node duration is attribution by window**, from a node's start to the next node at
  the same depth or shallower. Stated in the report itself so nobody reads it as an
  isolated measurement.

### Option 2 — Option 1 + skill `/audit-usage` (score 7)

Same hook; a Form 2 skill would answer "which step was the most expensive across the
last ten runs?". Rejected **now, not forever**: with no accumulated history there is
nothing to query, and a piece born on anticipation dies of disuse — anti-pattern 9. It
becomes a legitimate candidate once `history.jsonl` has real volume; the ledger exists
precisely so that skill can be written without changing the capture.

### Option 3 — Form 1 skill rendering the report (score 3)

Rubric criterion 4: persuasion where a guarantee was available. A pipeline that dies
mid-run produces no record at all, contradicting axis 7 head-on. Anti-pattern 2.

### Option 4 — create nothing (score 4)

`/cost` reports the session, not the skill execution. No chain, no permissions, no
status, nothing versioned. Covers none of the three symptoms of axis 1.

## What the report contains

Beyond what the request named, and approved in the interview: estimated cost in USD
(1), cache hit ratio (2), duration bars per step (3), rework per tool (5), `HEAD` at
start and end plus the commits produced (7), and a top-3 of the most expensive steps
(9). All derived from data already captured — none of them required an extra event.

## References

| Claim | Source |
|---|---|
| "Always, without depending on the model" resolves to a hook before any of the six forms | `references/decision-matrix.md` § 2 row 1, § 1 (persuasion/guarantee line) |
| Invariant: what must always hold is a hook, not prose | `@CLAUDE.md` invariant 6 |
| The events used exist | `@claude-help.md` § 8 "Events" |
| A hook receives JSON on stdin and must never crash the session | `@claude-help.md` § 8 · `.claude/hooks/ArchHook.java` `main`'s catch-all |
| Field and pattern lists have a single owner in `extensions.json` | `@CLAUDE.md` invariants 2 and 10 |
| No literal secret in a versioned file | `@CLAUDE.md` invariant 11 |
| The set of audited skills is data, not code | `@CLAUDE.md` invariant 7 |
| Numbers that change outside this repo are never written from memory | `@CLAUDE.md` invariant 8 (applied to prices) |
| The generated project is self-contained | `@CLAUDE.md` invariant 9 — hook and schema already travel in step 7 |
| A piece with no observed symptom should not be created | `references/decision-matrix.md` § 7 anti-pattern 9 (why Option 2 waits) |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | `audit` mode: 10 phases, NDJSON log, Markdown renderer, ledger, redaction, token and cost aggregation, rule inference. `doctor` gains an "Audit" line |
| `.claude/schemas/extensions.json` | New top-level `audit.redact` block — patterns and token prefixes |
| `.claude/skills/project-bootstrap/templates/settings.json.example` | Wires the 10 phases; `audit flush` runs before `schema` on `Stop` |
| `.claude/skills/project-bootstrap/templates/audit-pricing.json.example` | New template — prices with `null` defaults |
| `.claude/skills/project-bootstrap/SKILL.md` | Step 7 parts 4 and 5; preconditions; `Writes` list; output contract line |
| `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example` | Command row and routing row for `.claude/audit-usage/` |
| `CLAUDE.md` | Command row, routing row, and the known pitfall "the `audit` mode is off in this repository, on purpose" |

Goes to the generated project: **yes** — `ArchHook.java` and `extensions.json` are
already copied verbatim in step 7, and the wiring travels in `settings.json.example`.
This meta-repo's own `.claude/settings.json` is **not** wired: without
`.claude/audit-usage/` the mode is inert, and auditing the design of the tool is not
what the request asked for.
