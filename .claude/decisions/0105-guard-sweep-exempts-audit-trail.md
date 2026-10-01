# 0105 · `guard sweep` stops reporting the audit trail as a phase write

- **Date:** 2026-10-01
- **Scenario:** Issue #55, triaged at `da6a227` — in a generated project, `guard sweep` reports
  files the `audit` hook writes under `.claude/audit-usage/` as writes of the open phase,
  outside its `write_allow`, and tells the model to revert them.
- **Decision:** Option A — `guard.sweep_exempt: [".claude/audit-usage/**"]` in
  `.claude/schemas/extensions.json`, read by `guardSweep` in `.claude/hooks/ArchHook.java`
  before the territory check. `guardViolations`, the tool-time guards and the stderr text are
  unchanged. No new mode, no new registration.
- **State:** approved by Lucas Fernandes, on 2026-10-01

## Reproduced on disk before classifying

Checked by `issue-verifier` against `da6a227` (`/triage-issue 55`, verdict `confirmed`, layer
static). No commit since touches a cited file. Only the confirmed rows are inputs here; the
issue's proposed fix is not.

| Claim | On disk |
|---|---|
| The sweep checks every porcelain entry changed since the baseline against `write_allow`, with no path exemption | Confirmed, `ArchHook.java:5421-5425` → `guardViolations` `:5192-5233`; `AUDIT_DIR` (`:3368`) appears in neither |
| An audit-usage path is reported outside the territory of the open class | Confirmed by running `guard prompt` + `guard write` on `history.jsonl`, `nodes.jsonl` and a report under `/new-feature` (`orchestrator`) and `/report-issue` (`report`) — the same `guardViolations` the sweep calls |
| The trail is versioned, so it reaches porcelain | Confirmed, `extensions.json` `$comment_gitignore`: only `.claude/audit-usage/.state/` is ignored |
| The audit hook writes there after the baseline | Confirmed: `audit flush` writes the run report at `Stop` (`:4234-4236`); `audit prompt` appends `history.jsonl` / `nodes.jsonl` at `UserPromptSubmit` (`:3460-3466`) in parallel with `guard prompt` |
| Intermittent | Consistent: hooks of one event run in parallel (`claude-help.md:896`), and porcelain is flagged only when a status line changes, so whether a write lands before or after `git status` is a race |
| The message says "Revert them", and following it deletes the trail | Confirmed, `ArchHook.java:5431` |
| `new-feature` and `git-publish` already treat the trail as never the run's work | Confirmed, `new-feature/SKILL.md:319-321`, `git-publish/SKILL.md:141` |

Refuted, not an input: "`history.jsonl` is written by `audit flush`" — it is written when a run
closes; the flush writes only the report. The symptom is unaffected.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Confirmed by triage: a false exit 2 at `Stop` whose advice destroys the trail `git-publish` commits | create nothing |
| 7 — mandatoriness | The sweep is already a guarantee; the defect is inside it | Forms 1–6 |
| 8 — destination | Both — `ArchHook.java`, the jar and `extensions.json` travel through `export` whole | — |
| 16 — existing mode | `guard sweep` owns the check; no new mode, no new registration | new Form 7c mode, new 7a entry |
| — scope | The whole `.claude/audit-usage/**`, not a closed list of today's files | closed file list |
| — tool-time | `guard write` and `guard bash` keep refusing the model's writes there; only the sweep exempts | exemption inside `guardViolations` |
| — message | Unchanged — once the trail is exempt, what remains listed is the turn's own writes | rewording the stderr |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| A | `guard.sweep_exempt` globs in `extensions.json`, read by `guardSweep` before `guardViolations` | 8 | **Approved** |
| B | `guardSweep` skips `AUDIT_DIR + "/"` from the Java constant, no data | 6 | Rejected — an exemption list held as a constant in the source (invariant 10); the next case needs Java and a jar rebuild |
| C | Exempt the path inside `guardViolations` | 3 | Rejected — opens tool-time writes to the trail for every class |
| D | Create nothing | 1 | Rejected — observed failure whose advice destroys versioned data |

### Option A — `guard.sweep_exempt` in `extensions.json` (score 8)

**Motivator:** axes 1 and 16 — the defect is in an existing mode; invariant 10 says the list a
mode reads lives in `extensions.json`.

**What changes:** `guard.sweep_exempt: [".claude/audit-usage/**"]` with a `$comment` naming
the reason (hook-written, versioned, parallel to the baseline). In `guardSweep`, an entry whose
path matches a `sweep_exempt` glob (`matchesAny`, the matcher `write_allow` already uses) is
skipped before `guardViolations`. `guardViolations` is untouched, so `guard write` and
`guard bash` still refuse the model.

**Pros:** one data line, no new mode, no new registration. A future hook that writes a
versioned path is one more glob, no Java and no jar rebuild. The exemption is visible where
every other guard list lives.

**Cons:** the path is now spelled in four places — `AUDIT_DIR`, `export.ensure_dirs`,
`export.gitignore_lines` and `sweep_exempt`. Invariant 2 is strained, not broken: each spelling
answers a different question (where the hook writes, what export creates, what git ignores,
what the sweep skips), and the `$comment` cites the constant. A model write to the trail
through a shell spelling `guard bash` does not parse now goes unseen at `Stop` too.

**Points cut in the rubric:** criterion 2 — invariant 2 strained by the fourth spelling.
Criterion 5 — one more list to keep in step with `AUDIT_DIR`.

**CI:** `SweepTest` (`validate` · `hooks-cross-platform` › `validate.yml:117`) gains two cases
under a `/new-feature` phase: a report and `history.jsonl` written under
`.claude/audit-usage/` after the baseline → sweep exit 0; a `guard write` on the same path →
exit 2, proving tool-time stays strict. Red run: remove the glob from the copied
`extensions.json` → the first case fails by name.

### Option B — `AUDIT_DIR` constant in `guardSweep` (score 6)

**Motivator:** single owner of the path — the constant the `audit` mode writes with.

**Pros:** one line of Java, no fourth spelling; the sweep follows the audit dir wherever the
constant moves.

**Cons:** invariant 10 strained — an exemption list of length one held as a constant in the
source; the next hook-written versioned path needs a Java edit and a jar rebuild. The
exemption is invisible to anyone reading the `guard` block.

**Points cut:** criterion 2 (invariant 10), criterion 4 (the exemption is not declared where
the guard's data is read), criterion 6 (every other guard exception is data).

**CI:** same `SweepTest` cases as A; red run by reverting the Java line.

### Option C — exempt in `guardViolations` (score 3)

Shared by `guard write`, `guard bash` and the sweep. The model could write, edit or delete
the trail through Write, Edit and Bash in every phase — the same as widening every class's
`write_allow`. Rejected in the interview; caps at ≤ 4 because it breaks what
`new-feature` and `git-publish` assume.

### Option D — create nothing (score 1)

The failure is observed and its advice is destructive. Not the mirror of invariant 6: the
guarantee exists, and it is wrong.

## References

| Claim | Source |
|---|---|
| Hooks matching one event run in parallel, so `Stop` ordering is a race | `@claude-help.md:896` |
| Lists a mode reads live in `extensions.json` | `@CLAUDE.md` invariant 10 |
| A norm in two places is a bug; each spelling must answer a different question | `@CLAUDE.md` invariant 2 |
| The sweep is git-shaped and runs the same territory checks as tool-time | `@.claude/decisions/0065-guard-sweep-on-stop.md` |
| The trail is versioned and committed with the run | `extensions.json` `export.$comment_gitignore`; `@.claude/decisions/0058-skill-classes-territory-schema.md` line 81 |
| A guard exception held as data, matched by basename/glob | `guard.frozen_exempt_basenames` — `@.claude/decisions/0060-lessons-learned-013-shipping-defects.md` |
| Triage verdict and claim table | `/triage-issue 55`, comment on issue #55 |

## Propagation

| File | Change |
|---|---|
| `.claude/schemas/extensions.json` | `guard.sweep_exempt` + `$comment_sweep_exempt` |
| `.claude/hooks/ArchHook.java` | `guardSweep` skips a path matching `sweep_exempt` before `guardViolations`; Javadoc names the exemption and that tool-time is untouched |
| `.claude/hooks/ArchHook.jar` | Rebuilt under JDK 21; `build --verify` green |
| `.claude/.ci/SweepTest.java` | Third turn under `/new-feature`: trail written after the baseline → silent; `guard write` on the trail → exit 2 |
| `docs/pt-br/11-pitfalls.md`, `docs/en/11-pitfalls.md` | One sentence in the `guard sweep` paragraph |
| `docs/pt-br/07-ci-validate.md`, `docs/en/07-ci-validate.md` | `SweepTest` row names the new cases and 0105 |
| `CLAUDE.md` | `guard sweep` row in § Commands: "the audit trail excepted" |

Goes to the generated project: **yes, via step 6.6** — `export` copies `ArchHook.java`,
`ArchHook.jar` and `schemas/extensions.json` whole; no template registration changes.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · hooks-cross-platform › java .claude/.ci/SweepTest.java` (`validate.yml:117`) | An audit-trail write during an `orchestrator` turn is not reported at `Stop`, and a `Write` to the same path is still refused at tool time | Green on the tree. Red with `sweep_exempt: []`: "audit trail written during the turn — not reported — expected exit 0, got 2", the sweep naming the report and `history.jsonl` with the exact message of issue #55 |

No template CI change: `SweepTest` lives under `.claude/.ci/`, which does not travel, and the
generated project gets the fix through the exported jar and `extensions.json`.
