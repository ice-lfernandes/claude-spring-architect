# 0111 · A generated project decides which of its pieces the audit trail records

- **Date:** 2026-10-02
- **Scenario:** Issue #67, triaged at `edfcf69` — "quero criar uma configuracao que permita o
  projeto gerado setar qual skill ou agent deve ser auditado. O default segue como hoje. Essa
  configuracao que altera o comportamento dentro da pasta .claude no projeto gerado deve ter
  apenas essa permissao para alteracao desse cenario" — approved as "aprovo a 1 mas com uma
  nova regra. Arch-Adopt esta fora dessa possibilidade. Somente ele Nunca é auditado, o resto
  tem o seu default e pode ser configurado pelo projeto gerado"
- **Decision:** Option A, amended — Form 7c change to the existing `audit` mode
  (`ArchHook.java` `isAuditExcluded`, `doctor`), data in `.claude/schemas/extensions.json`
  (`audit.project_overrides`, `skill_classes.classes.build.overrides.arch-adopt.audited`)
- **State:** approved by Lucas Fernandes, on 2026-10-02 — Option A plus the `arch-adopt` rule

## Reproduced on disk

Confirmed rows of the `issue-verifier` table, checked at `edfcf69`. Refuted and unproven rows,
and the issue's proposed fix, are not inputs.

| Claim | Evidence at `edfcf69` |
|---|---|
| The only per-piece switch is the class flag | `ArchHook.java:3722-3728` — `isAuditExcluded` reads `<kind>_classes.classes.<cls>.audited` and nothing else |
| Only `observer` and `ops` declare `audited: false` | `extensions.json` — the two hits |
| The whole trail is switched off by deleting `.claude/audit-usage/` | `ArchHook.java:3605` |
| Every audited run writes a report, whatever its size | `ArchHook.java:4442-4443` — unconditional `Files.writeString` |
| `overrides.<piece>` exists only for `write_allow` | `skill_classes.classes.build.overrides.arch-adopt`, `agent_classes.classes.installer.overrides` |
| A project-local edit to `extensions.json` does not survive an update | `export.copy` names `.claude/schemas/extensions.json`; `exportFiles` (`ArchHook.java:1735-1755`) writes it whole |
| No audit change between `v0.13.5` and `HEAD` | `git diff v0.13.5..HEAD` |

Found while designing, not in the issue: `export` writes only the paths its manifest names
(`copy`, `overwrite`, `optional_copy`, `binary_copy`) and deletes only `export.retired`. A file
the manifest never names — `history.jsonl`, `GENESIS.md` — survives every `/arch-adopt`.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Concrete symptom | A run's report lands after the work it records, the tree ends dirty, and the project carries `chore: commit pending audit-usage trail` commits — the oscillation `0086` cut for `git-publish` | create nothing as "anticipation" |
| 8 — Destination | The **generated project** sets it, and the setting survives `/arch-adopt`. The default stays what the classes say today | an upstream-only switch in `extensions.json` (`export` overwrites it) |
| 9 — Integration | The file changes only *which pieces are audited* — nothing else inside `.claude/` | a general local overlay of `extensions.json` |
| 7, 15 — Mandatoriness, reaction | Read by the existing `audit` mode; no blocking | Form 8 |
| 16 — Existing mode | `audit` already runs the check through `isAuditExcluded` | a new mode |
| — Semantics of an excluded piece | Same as `0086`: no run of its own — no `.md`, no `history.jsonl` line; chained inside another run it stays a node of that run | ledger-only variant |
| — Size threshold (`audit.report_when`) | Rejected | Option E |
| — At approval | `arch-adopt` is **never** audited and out of the project file's reach; every other piece keeps its class default and is configurable per project | a project-settable `arch-adopt` |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| A | `.claude/audit-usage/audited.json` — project-owned map piece → boolean, path named in `extensions.json`, never in `export`, checked by `doctor` | 8 | **Approved**, amended: `arch-adopt` fixed off |
| B | Same file, opt-out only: `{"skip": [...]}` | 7 | Rejected — cannot set a piece back on |
| C | Upstream `overrides.<piece>.audited` in `skill_classes` / `agent_classes` (the issue's F1) | 5 | Rejected as the project's switch — does not meet axis 8. Kept for the one fixed piece, `arch-adopt` |
| D | Create nothing — the project adds `.claude/audit-usage/*--<piece>.md` to `.gitignore` | 3 | Rejected — `history.jsonl` still dirties the tree |
| E | `audit.report_when` size threshold (the issue's F2) | 3 | Rejected in the interview |

### Option A — `.claude/audit-usage/audited.json`, a boolean per piece (score 8)

**Motivator:** axis 8 — the setting belongs to the project and must outlive an update.

**Shape:**

```json
{
  "skills": { "report-issue": false, "git-publish": true },
  "agents": {}
}
```

**How it reads:** `isAuditExcluded(kind, name)` looks the piece up in the project file first;
a boolean there wins. Absent file, absent name, or a non-boolean value → the class flag, as
today. `true` on an observer or `ops` piece re-enables it; that is the owner's call, and the
record says what it brings back (`0086`: the feedback loop, the dirty tree after a publish).

**Why this path:** next to `pricing.json` and `history.jsonl`, the files the trail already
reads. Deleting `.claude/audit-usage/` still switches everything off, the override with it.
The path itself is data — `audit.project_overrides` in `extensions.json` — so no path is a
constant in the Java (invariant 10). `export` never names it, so `/arch-adopt` never touches it.

**"Only this permission":** `doctor`'s `Audit overrides` line validates the file strictly — top-level
keys only `skills` and `agents`, each value a boolean, each name a skill or agent that exists
under `.claude/`. Anything else fails by name: a typo would otherwise switch nothing, in
silence (the failure mode invariant 10 names), and a second key would turn the file into a
general overlay of `extensions.json`.

**Pros:** survives updates by construction, no merge; default unchanged; the class flag stays
the upstream owner and the file only overrides per project; one read of a small file per
prompt or Skill/Agent call, next to the `extensions.json` read that is already there.

**Cons:** strains invariant 2 — two places answer "is this piece audited": the class
(upstream default) and the file (project decision). Bounded by a fixed precedence (file wins,
per piece) and by `doctor` naming every override, so an excluded piece is never silent.
Re-enabling an observer is allowed and re-opens what `0086` closed.

**Points cut in the rubric:** criterion 2 (invariant 2 strained, not violated); criterion 5
(a second file the `audit` mode reads, and a validator).

**CI:** `validate` · `hooks-cross-platform` › `audit renders where the run spent, redacts tool
errors, skips observer-class pieces` — new cases in `.claude/.ci/AuditRenderTest.java`: a
`build`-class skill set `false` in the file leaves no report and no ledger line; an observer
set `true` opens a run; no file → today's behaviour. A `doctor` case for an unknown name and a
non-boolean value. Generated project: `ci.yml.example` already runs `doctor`.

**The amendment — `arch-adopt` fixed off.** Its class entry
`skill_classes.classes.build.overrides.arch-adopt` gains `"audited": false`, beside its
`write_allow`: the issue's F1 spelling (Option C), used for the one piece whose value the
project must not set. Read first, so the project file cannot turn it back on, and `doctor`
fails by name when the file tries. Why this piece: its whole output is a `git diff` of
`.claude/` handed over for review and its step 1 refuses a dirty tree — a report of its own
lands in that diff after the export. The rest of `build` stays audited. Precedence, first
boolean wins: piece override in the class → project file → class flag.

### Option B — opt-out list `{"skip": [...]}` (score 7)

Same file, same read, same validation, one direction only: a piece can leave the trail, never
re-enter it. Safer against re-opening `0086`, but it does not answer "set *which* piece is
audited" — an owner who wants `git-publish` recorded has no way. Cut on criterion 1 (narrower
than the request) and criterion 2 (same strain). **CI:** the same `AuditRenderTest` cases,
minus the re-enable one.

### Option C — upstream `overrides.<piece>.audited` (score 5)

The issue's F1. Sound mechanism, precedent in `write_allow` overrides, no invariant strained.
It fails axis 8: `export` writes `extensions.json` whole, so the project cannot own the value —
only this repository can. Viable only if the maintainer decides upstream which pieces leave,
which the interview declined. **CI:** one `AuditRenderTest` case.

### Option D — create nothing, `.gitignore` the reports (score 3)

Zero code. Does not fix the symptom: `history.jsonl` is versioned and still gains a line at
every close, so the tree still ends dirty. Cut on criteria 1 and 4.

### Option E — `audit.report_when` size threshold (score 3)

`auditRender` writes the report at every `Stop` (`ArchHook.java:3628`), before duration, cost
and status are final; the ledger line is written only at close (`:4445`). A threshold means
deleting a report already on disk, or dropping the `⏳ in progress` view `0086` relies on, with
arbitrary numbers. `always_on: src_changed` is always false for `arch-adopt`, whose territory
is `.claude/**`. Rejected in the interview.

## References

| Claim | Source |
|---|---|
| Exclusion is a class property today; observers and `ops` declare it | `@.claude/decisions/0086-audit-exclusion-owned-by-the-class.md` |
| `export` writes only what its manifest names | `ArchHook.java` `exportFiles`; `extensions.json` `export` |
| Lists and paths a hook reads live in `extensions.json` | `@CLAUDE.md` invariant 10 |
| A norm in two places diverges | `@CLAUDE.md` invariant 2 |
| `doctor` already validates trail data (`pricing.json` vs recent models) | `ArchHook.java` `doctor` › `Audit` line; `@.claude/decisions/0101-lessons-learned-018-unpriced-model.md` |
| `AuditRenderTest` builds a throwaway project with the real `extensions.json` | `.claude/.ci/AuditRenderTest.java`; `references/ci-coverage.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | `isAuditExcluded(dir, kind, name)` — three levels; `auditProjectOverrides`; `auditOverrideProblems`; `doctor` › `Audit overrides` line; both call sites pass the trail directory |
| `.claude/hooks/ArchHook.jar` | Rebuilt; `build --verify` green |
| `.claude/schemas/extensions.json` | `audit.project_overrides: "audited.json"` with `$comment_project_overrides`; `audit.$comment` names the three levels; `build.overrides.arch-adopt.audited: false`, explained in `build.$comment` |
| `.claude/.ci/AuditRenderTest.java` | Cases below; header and failure message name 0111 |
| `.github/workflows/validate.yml` | Step renamed `audit renders where the run spent, redacts tool errors, skips what class or project turns off`; comment names 0111 |
| `docs/en/07-ci-validate.md`, `docs/pt-br/07-ci-validate.md` | The step's row |
| `docs/en/08-audit-usage.md`, `docs/pt-br/08-audit-usage.md` | `arch-adopt` exception; § Per project: `audited.json` |
| `.claude/skills/audit-usage/SKILL.md` | Where the project switches a piece — the skill the owner reads inside the project |
| `.claude/skills/arch-adopt/SKILL.md` | `## Contract`: leaves no report; never touches `audited.json` |
| `claude-help.md`, `CLAUDE.md` (routing row) | The three levels in one line |

Goes to the generated project: **yes** — `ArchHook.java`, the jar, `extensions.json` and both
skills already travel through `export`. `audited.json` itself is never shipped: absent means
today's behaviour, and the project writes it if it wants one. No `migrations` entry: no
existing project is left behind by a default that did not change.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · hooks-cross-platform › audit renders where the run spent, redacts tool errors, skips what class or project turns off` | `arch-adopt` opens no run, with or without the file; `false` in the file takes `report-issue` out, typed or called by the model; `true` puts `audit-usage` back; the file cannot turn `arch-adopt` on; `doctor` names `arch-adopt`, an unknown piece, a non-boolean and a foreign key, and lists a valid file | Green on the tree. Red with `"audited": false` dropped from `arch-adopt`'s override — 3 checks fail by name. Red with the project-file read disabled in `isAuditExcluded` — 2 checks fail by name |
| `validate · hooks-cross-platform › committed ArchHook.jar is what the source compiles to` | The jar carries the change | Green |
| Generated project `ci.yml.example` › `doctor` | A bad `audited.json` shows as `❌` on the `Audit overrides` line | Already present, nothing added |
