# 0065 · `guard sweep` on `Stop` — a filesystem-shaped backstop behind the tool-shaped guards

- **Date:** 2026-09-28
- **Scenario:** lessons-learned-014 § 1, second half — the suggested fix that
  `0063-bash-write-enforcement.md` deliberately left for later: back the parser with a check
  that does not care which tool wrote.
- **Decision:** Form 7a + Form 7c — a `Stop` registration in `.claude/settings.json` and in
  `project-bootstrap/templates/settings.json.example`, plus a `guard sweep` submode diffing the
  working tree against the phase's territory and the frozen `UC-NNN` folders.
- **State:** approved by Lucas Fernandes, on 2026-09-28.

## Why this exists although `guard bash` shipped

`guard bash` reads the shapes it knows. A generated script, a `python3 -c` with
`open(..., 'w')`, an editor invoked from the shell, a target built from a variable — all pass,
by design, because a parser that blocked on what it could not read would stop `./mvnw` on its
first false positive. The record for 0063 says so in as many words and names this as the
complement.

`guard sweep` is the other shape of the same rule: it looks at what changed on disk, so it
holds regardless of how it got there. What it cannot be is a substitute — it runs after the
write, and a frozen spec overwritten by `sed -i` is already overwritten. Together they are
prevention for the spellings anyone actually types, and detection for the rest.

**What it is still not:** git-shaped, not filesystem-shaped in the strict sense. A path git
ignores never appears in `git status --porcelain` and is therefore never swept — in this
repository that is `.claude/decisions/` and `.claude/lessons-learned/`, both ignored on
purpose. Worth knowing before someone reads the sweep as total coverage.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Two files written through a heredoc with no refusal and no audit entry; the same run's harness preferred `Bash` for every edit | 9 (create nothing) |
| 7 — mandatoriness | Cannot fail: territory and frozen folders have no second net anywhere | 1 · 2 · 3 · 4 · 5 |
| 14 — lifecycle event | `Stop` — it needs the turn to be over to know what the turn wrote | Every tool matcher |
| 15 — reaction | exit 2, so the model sees the lines and reverts or reports; `stop_hook_active` bounds it to one firing | Reporting at exit 0 |
| — baseline | A snapshot taken at `UserPromptSubmit`, where `guard prompt` already runs and already writes state | A bare `git status --porcelain`, which reports a tree dirty from before the run — lessons-learned-014 § 11's exact complaint |
| 16 — existing mode | `guard` holds the checks; what is new is the source of the path list and the need to collect every violation instead of exiting on the first | A new hook file |
| 8 — destination | Both | A single-destination registration |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `guard sweep` with a `UserPromptSubmit` baseline | 8 | **Approved** |
| 2 | `guard sweep` over bare `git status --porcelain` | 6 | Rejected — reports work that predates the run; § 11 records that exact noise |
| 3 | `git diff HEAD --name-only` as the baseline | 6 | Rejected — spans commits rather than the turn, and misses untracked files unless it grows a second command |
| 4 | Create nothing; `guard bash` is enough | 4 | Rejected — the parser's blind spots are documented in 0063 as the reason this option exists |

### Option 1 — `guard sweep` with a prompt-time baseline (score 8)

**Motivator:** axis 7 plus 0063's own written limit. The guarantee has no second net and the
first net admits, by design, everything it cannot parse.

**Shape.**

- `guard prompt` already runs at `UserPromptSubmit` and already writes the phase state. It also
  writes `<session>.baseline`: the `git status --porcelain --untracked-files=all` of the moment
  the turn started.
- `guard sweep` runs at `Stop`. It returns immediately on `stop_hook_active`, or when there is
  no baseline, no git, or no open phase. Otherwise it takes the porcelain now, keeps the entries
  that are new or whose status changed since the baseline, and runs each path through the same
  territory and frozen-folder checks `guard write` and `guard bash` use.
- The checks move from "print and exit on the first violation" to "return the lines", so the
  sweep can report every path at once. `guardPath` keeps its behaviour by printing what the
  shared check returns and exiting 2 — one owner for the rules, two callers.
- **The three legal writes inside a frozen folder are recognised without `Edit`'s arguments.**
  The sweep has no `old_string`/`new_string`, so it compares the working copy against
  `git show HEAD:<path>`: a spec whose only differences are the `status:` line and `[ ]` → `[x]`
  toggles passes, anything else is reported. Without that, the executor's own legitimate close
  would be flagged every run.

**Pros:** covers every spelling the parser will never learn, including tools nobody has thought
of. One JVM per turn, on an event that already pays for `tests` and `schema`. Reuses the rules
whole — a widened `write_allow` widens for all three callers.

**Cons:** criterion 5. It adds a baseline file, a git comparison, and a second exit path through
the check, which is real surface. And it is git-shaped: an ignored path is invisible to it, so
the coverage claim has a boundary that has to be stated wherever the sweep is described.

**Points cut in the rubric:** § 8 criterion 5 (baseline state plus the `git show` comparison).

### Option 2 — bare `git status --porcelain` (score 6)

No baseline, no new state. Rejected because lessons-learned-014 § 11 records the failure this
produces from the other direction: a modified `history.jsonl` and an untracked report, both from
a previous run, present at the start of this one. Every sweep would open by reporting them.

### Option 3 — `git diff HEAD --name-only` (score 6)

Against the last commit, covering a whole multi-turn run. Rejected: it answers a different
question — "what has this branch changed" rather than "what did this turn write" — and untracked
files, which is what a heredoc usually creates, need a second command to appear at all.

### Option 4 — create nothing (score 4)

Rely on `guard bash`. Rejected against 0063's own text, which names the parser's blind spots and
this sweep as the complement. Capped by invariant 6 for leaving a guarantee unbought where the
failure is observed.

## References

| Claim | Source |
|---|---|
| The parser admits what it cannot read, by design | `.claude/decisions/0063-bash-write-enforcement.md` · `ArchHook.java` `guardBash` javadoc |
| A rule that must always hold is a hook | `@CLAUDE.md` invariant 6 |
| One owner for the territory and frozen-folder rules | `@CLAUDE.md` invariant 2 · `ArchHook.java` `guardPath` |
| A `Stop` hook honours `stop_hook_active` or it cascades | `ArchHook.java` `tests()` · `@claude-help.md` § 8 |
| A dirty tree from before the run is invisible to an index-only check | lessons-learned-014 § 11 |
| The three writes a frozen folder admits | `guard.frozen_statuses`, `guard.frozen_exempt_basenames` · `.claude/decisions/0060-lessons-learned-013-shipping-defects.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | `case "sweep"` in `guard`'s dispatch; `guardSweep`; `guard prompt` writes the baseline; the territory and frozen checks return their lines so both callers share them; the frozen-spec exemption gains a `git show HEAD:` comparison for the sweep's sake |
| `.claude/settings.json` | `Stop` entry running `guard sweep` |
| `.claude/skills/project-bootstrap/templates/settings.json.example` | the same entry |
| `CLAUDE.md` § Commands | a row for running the sweep by hand |
| `CLAUDE.md` § Known pitfalls | the entry saying what the sweep covers, that it is git-shaped, and that an ignored path is never swept |

Goes to the generated project: **yes** — the registration through the template (step 6.6), the
mode for free with `export`'s copy of `ArchHook.java`.

**Restart warning:** `.claude/settings.json` is read only at session startup.

## Found while building it

`git status --porcelain` collapses a brand-new directory into a single `?? src/` entry, so the
first write under a path git has never seen was invisible to the sweep — which is exactly the
write a heredoc makes. Both the baseline and the sweep run `--untracked-files=all`. Recorded
because the failure was silent: the sweep returned 0 and read as "nothing written outside".

## Verification run before reporting it done

| Case | Result |
|---|---|
| Shell write to `src/main/Bar.java` with `/new-feature` open | exit 2, `orchestrator` territory message naming the path |
| Files already dirty at baseline time (a modified hook, two untracked docs) | not reported — the baseline is what makes this quiet |
| A spec closed `approved` → `implemented` with its checkboxes ticked | exit 0 — admitted through the `git show HEAD:` comparison |
| The same spec with a body line also rewritten | exit 2, frozen-folder message |
| `CHANGELOG.md` written inside the frozen folder | exit 0 |
| Any of the above with `stop_hook_active: true` | exit 0 — no cascade |
| `src/` write with no phase open | exit 0 — nothing describes a territory outside a run |

`claude plugin validate .claude/skills` passes, `schema` passes, and `doctor` reports
`Hooks ✅ 18 registration(s) across 4 event(s)`.
