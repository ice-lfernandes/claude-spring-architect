# 0055 · Remediation of lessons-learned-011 — the first real `arch-adopt` run

- **Date:** 2026-09-27
- **Scenario:** `/spring-architect:arch-adopt` was run for the first time from the
  published plugin, against `demo-clean-arch-single-module`. It succeeded — 134 files,
  `schema` exit 0, `doctor` operational — but only because the operator worked around
  four gaps by hand. `@.claude/lessons-learned/lessons-learned-011.md` records seven
  items; this record decides what changes and what does not.
- **Decision:** Option 1 — edit what exists. No new piece: `arch-adopt` (steps 2, 4, 5, 6),
  `ArchHook.java` (`main`'s stdin, `export`'s blueprint copy), the `export` manifest,
  `@CLAUDE.md` invariant 9 and § Known pitfalls. Items 3, 6 and 7 accepted and not fixed.
- **State:** approved by Lucas Fernandes, on 2026-09-27

## Interview

| Axis | Answer | Forms/options it eliminated |
|---|---|---|
| 1 — symptom | Four gaps observed in one run, each worked around by hand: no reachable `base_url`, a mode that hangs on stdin, a custom blueprint left in `/tmp`, and a package name the blueprint does not know | "create nothing" for items 1, 2, 4, 5 |
| 5 — nature | Every fix is an edit to a piece that already exists — no new skill, agent, rule or hook registration | Forms 1, 3, 4, 6, 7a, 7b, 8 |
| 7 — mandatoriness | The stdin hang and the lost blueprint are failures of a program, not of judgment | Prose-only fixes for items 2 and 4 |
| 8 — destination | Both: `arch-adopt`, `ArchHook.java` and `extensions.json` all travel into the generated project | — |
| 16 — existing mode | `export` already exists; what changes is what it copies and when `main` reads stdin | A new mode (7c as a *new* mode) |
| Scope | The four High items (1, 2, 4, 5). Items 3, 6 and 7 are recorded here as accepted, not fixed | Fixing all seven now |
| Item 1 — how the skill finds `base_url` | The skill fetches the source's `extensions.json` using `raw_url` + `ref`, both already in `.plugin-source.json` | Duplicating `base_url` into the plugin manifest (invariant 2) |
| Item 2 — the stdin hang | Only the hook-protocol modes read stdin | Documenting `</dev/null` and leaving `doctor` and `compose` broken the same way |
| Item 4 — the custom blueprint | `export` writes the blueprint into the project when the source does not have it; `arch-adopt` prefers the local copy | Always copying it; forbidding custom blueprints in adopt |
| Item 5 — the package-name divergence | Only the adopt-side warning. **Re-decided mid-interview**, see below | A CI job guarding `packages.map` |
| A `.claude/` with no stamp | Recognize it as already adopted: read the blueprint `rules/00-index.md` names in prose, confirm, don't re-detect | Treating it as a fresh install; refusing outright |
| Release | `v0.1.1`, then `./sync.sh v0.1.1` in the marketplace | Merging to `main` only — `latest-tag` would keep serving v0.1.0 with all four gaps |

### The one answer that changed during the interview

The lessons-learned left item 5's cause unconfirmed. `git log -S 'application.shared'`
settles it: the key was **added** on 2026-09-17 (`496b10f`, the lessons-learned-006
remediation), and `application.service` never existed in that blueprint at all. Nothing
was renamed. The demo carries `application/service/` because the blueprint had no entry
for that role when the project was generated and the executor invented one — which is
precisely the failure `496b10f` added the key to prevent.

So the CI job first approved — fail when a `packages.map` value changes without a
migration note — would not have caught this, and guards a failure nobody has observed.
Invariant 6's mirror applies, and it was dropped. What stays is the adopt-side warning,
which is what actually surfaced the divergence in the real run.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Edit what exists: `arch-adopt` (items 1, 4, 5, no-stamp), `ArchHook.java` (`main`'s stdin, `export`'s blueprint copy), the `export` manifest, `@CLAUDE.md` invariant 9 | 9 | **Proposed** |
| 2 | Same fixes plus a CI job over `packages.map` | 7 | Rejected — the confirmed cause is an added key, not a rename; the job guards an unobserved failure and fires on every legitimate blueprint evolution |
| 3 | A second skill, `arch-update`, owning the update path so adopt stays install-only | 4 | Rejected — two mechanisms for one operation, which D54 already rejected once; the no-stamp case is a branch, not a piece |
| 4 | Create nothing; record the four gaps as known pitfalls | 3 | Rejected — items 1, 2 and 4 each stop a project from updating itself, which is the property the whole initiative exists to deliver |

### Option 1 — edit what exists (score 9)

**Motivator:** axis 1 — four failures observed in one run; axis 5 — every one of them
lands on a file that already has an owner.

**Pros:** no new piece enters the inventory; the stdin fix repairs `doctor` and `compose`
at the same time, which nobody had reported yet because nobody had run them redirected;
the blueprint copy closes the only path by which adopt produces a project that cannot be
updated.

**Cons — one, and it is real:** copying a blueprint into the project contradicts the
sentence in invariant 9 that says `blueprints/` is left out on purpose. The exception is
narrow (only a blueprint the source does not have) and the reason is the opposite of the
original one: the file is left out when the source can always supply it, and must travel
when it cannot. Invariant 9 gets the sentence, not a footnote.

**Points cut in the rubric:** criterion 2 — the invariant needs rewording, which is a
strain even when the rewording is right.

### Option 2 — plus the CI job (score 7)

Defensible while the cause was unknown. With the cause confirmed it is a process spawned
per push to confirm what was already true, and it would block the blueprint evolution
that `496b10f` is an example of.

### Option 3 — a separate `arch-update` (score 4)

Install and update differ by one branch — which blueprint to use — and share fetch,
gate, write and verify. A second skill duplicates all four and drifts on the first fix
applied to only one of them.

### Option 4 — create nothing (score 3)

Recorded because three of the four items have a hand workaround that a careful operator
found once. The next operator is not this one.

## What is accepted and not fixed

| Item | Why it waits |
|---|---|
| 3 — `"commit": "unknown"` in the stamp | Real, and the fix (`--commit <sha>` passed by adopt) is clean. It buys precision the current `ref` already approximates, and no failure has followed from it yet |
| 6 — the clean-worktree gate blocks on `pricing.json` | The gate is right to be blind. `pricing.json` being in `export.copy` is the actual defect and is worth fixing, but it costs a user their own rate table only when they edited it, which the report can name |
| 7 — `doctor`'s `❌` for an unset `CLAUDE_PROJECT_DIR`, the hardcoded `/tmp/arch-src`, the report's three counts | Cosmetic. Listed so the next reader knows they were seen, not missed |

## References

| Claim | Source |
|---|---|
| `main` reads stdin before dispatching, so every mode blocks | `.claude/hooks/ArchHook.java:44` |
| The plugin ships one file and no `extensions.json` | The marketplace repo's `.plugin-source.json`; `claude-help.md` § 10 |
| `application.shared` was added, never renamed | `git log -S 'application.shared' -- .claude/blueprints/` → `496b10f`, 2026-09-17 |
| A guarantee is bought against an observed failure | `@CLAUDE.md` invariant 6 and its mirror |
| Lists a mode reads are data, never constants in the Java | `@CLAUDE.md` invariant 10 |
| `blueprints/` is left out of the generated project on purpose | `@CLAUDE.md` invariant 9 — amended by this record |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/arch-adopt/SKILL.md` | ✅ Step 2: `$SRC` defined once and the third path (fetch the source's `extensions.json` via `raw_url` + `ref`); step 4: three states instead of two, the near-match case, and the variant now surviving in the project; step 6: why `schema` keeps `</dev/null` and the others no longer need it |
| `.claude/hooks/ArchHook.java` | ✅ `main` reads stdin only for `check`, `format`, `tests`, `schema`, `audit`, `guard`; ✅ `exportBlueprint` writes the active blueprint into the project, `references/` citations dropped; ✅ the stamp is skipped by the residue scan, since it now lists a `blueprints/` path it wrote itself |
| `.claude/schemas/extensions.json` | ✅ `export.blueprint_copy` |
| `CLAUDE.md` | ✅ Invariant 9 — catalog stays, active blueprint travels; ✅ § Known pitfalls: which modes read stdin |
| `.claude/lessons-learned/lessons-learned-011.md` | ✅ Item 5's cause confirmed by `git log -S`, replacing "causa não confirmada" |
| Release | Tag `v0.1.1` after merge, then `./sync.sh v0.1.1` in `claude-spring-architect-marketplace` |

**Verified:** all seven blueprints export twice byte-identical outside the stamp, no
residue; the three `.ci/` tests pass; a scratch project adopted with a blueprint that
exists only in the fetched tree keeps it at `.claude/blueprints/<id>/<id>.yaml` and
`doctor` reports `134 file(s) unchanged since`; `export` and `doctor` now return with
stdin left open.

Goes to the generated project: **yes**, all of it — `arch-adopt`, `ArchHook.java` and
`extensions.json` are in `export`'s payload.
