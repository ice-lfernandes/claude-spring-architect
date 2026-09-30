# 0051 · Frontmatter `!`command`` injections resolve paths from `${CLAUDE_PROJECT_DIR:-.}`, never from the shell's cwd

- **Date:** 2026-09-26
- **Scenario:** item 5 of `lessons-learned-010.md` — the pre-check injections use relative
  paths and break silently when the shell's cwd has drifted; `docker-architect` reported
  `docker-compose.yml` did not exist while the file was present and complete at the root
- **Decision:** Option 1 — no new extension file. Twelve injections rewritten to
  `"${CLAUDE_PROJECT_DIR:-.}"`, one Form 5 entry in this repo's `CLAUDE.md` § Known
  pitfalls, and the `ArchHook.java schema` lint proposed rather than written.
  **Amended the same day:** the user asked for the lint too, so it was implemented —
  see § The lint, delivered below. The architect-designer skill proposed it and stopped;
  the hook change was made on the user's explicit instruction, which is the only way it
  happens (§ Out of scope: propose, don't execute)
- **State:** approved by the user, on 2026-09-26

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — concrete symptom | `docker-architect`'s `## Current compose state` block rendered `(no docker-compose.yml at project root — run project-bootstrap first)` with the file present. A prior `Bash` had run `cd .../skills/messaging-architect && cat templates/...`, and the persistent shell stayed there. The skill's entry rule fired on a false negative | 3, 6 |
| 2 — trigger | Skill invocation: the injection runs before the body reaches the model. Plus a model-conduct trigger: `cd` into a skill directory in an intermediate `Bash` contaminates every later injection of the session | 3, 6 |
| 3 — frequency | Every invocation of every pipeline skill. 12 relative injections across 10 skills | — |
| 4 — territory | None a glob captures: the territory is `SKILL.md` frontmatter, not project code | 4 |
| 5 — nature | The path fix is a mechanical edit to files that already exist. The conduct rule is a declarative fact | 1, 2, 3 for the fix; 4 for the conduct rule |
| 7 — mandatoriness | The fix has to hold for every future injection, including ones not yet written. That is a guarantee, not persuasion | everything above the line in § 1 of the matrix |
| 8 — destination | The seven pipeline skills copy **verbatim** in step 6.7, so the fix travels to the generated project with no extra edit. The conduct pitfall stays in this repo's `CLAUDE.md` only — user's choice | — |
| 9 — integration | No new owner. `ArchHook.java schema` already owns frontmatter validation; `arch-doctor` already owns machine diagnosis | 1, 2 |
| 10 — cost of getting it wrong | An injected state that lies is worse than one that is missing: it fires a guard that aborts the skill, or it seeds a design built on wrong state. In this run only the fact that the whole procedure was already in context let the run continue by hand | weight in the score |

Axes 11-13 skipped: no external system, so nothing MCP-shaped.

Decisions taken in the interview, applying to whichever option wins:

- Scope: **all 12** relative injections, not just the one that failed. `docker-architect:21`,
  `arch-doctor:18`, `claude-code-architect-designer:16,18,20,22`, `init-project:14,18`,
  `use-case-design:16`, `domain-modeling:16`, `rest-api-architect:17`,
  `persistence-architect:17`, `messaging-architect:17`, `test-architect:16`.
- Variable form: **`${CLAUDE_PROJECT_DIR:-.}`**, matching the precedent already in
  `arch-doctor:14` and `audit-usage:15`. One idiom across all 14 injections. When the
  variable is unset it degrades back to the cwd, which is the accepted cost: the
  diagnosis already exists — `ArchHook doctor` reports `CLAUDE_PROJECT_DIR NOT set`
  (`ArchHook.java:175`).
- Fallback: **separate messages** for "does not exist" and "could not look". A single
  `||` branch cannot tell one from the other, and it is the one that arms the entry rule.
- The two diagnostic injections (`arch-doctor:18`, `init-project:18`) get absolutized too:
  their intent is always the project root, never the current cwd.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | create nothing as an extension — 12 injection edits + one `CLAUDE.md` § Known pitfalls line (Form 5) + propose the `ArchHook schema` lint | 9 | **Approved** |
| 2 | `.claude/skills/skill-injection-audit/SKILL.md` — Form 2, audits the injections on demand | 5 | Rejected — duplicates territory `ArchHook.java schema` and `arch-doctor` already own |
| 3 | `.claude/rules/skill-authoring.md` — Form 4 with `paths: .claude/skills/**/SKILL.md` | 4 | Rejected — a norm about frontmatter injections has to name skills; breaks invariant 1, `rules/` is a leaf |
| 4 | fix the 12 injections and nothing else | 6 | Rejected — leaves the conduct rule unwritten and the guarantee unproposed; the next injection written by hand repeats the bug |

### Option 1 — create nothing + 12 edits + Form 5 line + hook proposal (score 9)

**Motivator:** axis 5 (the fix is a mechanical edit, not a new piece) and axis 7 (what
must always hold is a hook, not prose).

**Concrete content:**

1. Twelve injections rewritten to `"${CLAUDE_PROJECT_DIR:-.}/<path>"`. In
   `docker-architect:21`, the fallback splits in two so a missing project root and a
   missing `docker-compose.yml` no longer produce the same sentence.
2. One line in this repo's `CLAUDE.md` § Known pitfalls: `cd` into a skill directory in an
   intermediate `Bash` contaminates every later frontmatter injection of the session —
   read a template with `Read` at an absolute path, never `cd` + `cat`. Declarative, whole
   repo, no file territory — Form 5 by § 6 of the matrix. `CLAUDE.md` is at 172 lines;
   adding one entry keeps it under the ~200 target.
3. A proposal, not an implementation: `ArchHook.java schema` gains a check that fails a
   frontmatter `` !`…` `` injection carrying a relative path. Invariant 6 — if it must
   always hold it is a hook — and § Out of scope of this skill: propose and stop.

**Pros:** no new piece to maintain; the fix travels to the generated project for free
because step 6.7 copies `SKILL.md` verbatim; the idiom already has precedent in two
injections and in every `settings.json` hook entry; the one prose line covers the residual
model-conduct risk that path absolutization cannot reach.

**Cons:** step 3 is left open as a proposal — until the lint exists, nothing stops the
thirteenth injection from being written relative again. `${CLAUDE_PROJECT_DIR:-.}`
degrades silently to the cwd when the variable is unset, which is the same failure mode
this decision closes, only rarer.

**Points cut in the rubric:** § 8 criterion 4 (enforcement) — the guarantee is proposed,
not delivered in this change.

### Option 2 — `skill-injection-audit` skill (score 5)

A Form 2 skill that greps the injections and reports the relative ones. Fails anti-pattern
1 and 9 of § 7: `ArchHook.java schema` is the owner of frontmatter validation (invariant
10) and `arch-doctor` is the owner of "does this work on my machine". A third reader of
the same data is one more piece with no gain, and it is persuasion where the hook offers
a guarantee. Kept on record because it scores above 4 and will look attractive again to
whoever wants the lint without touching `ArchHook.java`.

### Option 3 — rule `skill-authoring.md` (score 4)

A norm stating "an injection resolves paths from the project root" cannot avoid naming
what carries an injection: a skill. Invariant 1 caps it at ≤ 4. The `paths` would also be
`.claude/skills/**/SKILL.md`, a territory of extension files rather than project code —
outside what `rules/` covers in either repo.

### Option 4 — fix only, write nothing (score 6)

Closes the 12 occurrences and stops. Rejected because it answers the symptom and drops
both lessons the item raises beyond the paths: the `cd` conduct that produced the drift,
and the absence of any mechanical guard. Real difference from option 1 is small, which is
why it scores 6 and not lower.

## References

| Claim | Source |
|---|---|
| `${CLAUDE_PROJECT_DIR}` is available in skill frontmatter and means the project root | `@claude-help.md` § 5, variables table (line 404) |
| The `` !`command` `` injection runs before the content reaches the model | `@claude-help.md` § 5, "Dynamic context injection" |
| `${CLAUDE_PROJECT_DIR:-.}` is this repo's existing idiom for an injection | `.claude/skills/arch-doctor/SKILL.md:14` · `.claude/skills/audit-usage/SKILL.md:15` |
| `CLAUDE_PROJECT_DIR` may be unset, and that is already diagnosed | `.claude/hooks/ArchHook.java:175` (`NOT set — hooks use the current directory`) |
| A rule may not mention a skill | `@CLAUDE.md` invariant 1 |
| What must always hold is a hook, not prose | `@CLAUDE.md` invariant 6 · decision matrix § 1 |
| Recognized frontmatter fields have a single owner, and `ArchHook.java schema` is what reads them | `@CLAUDE.md` invariant 10 |
| The fix reaches the generated project without extra propagation | `project-bootstrap/SKILL.md` § 6.7, "The rest of the content … copies verbatim" |
| `git rev-parse --show-toplevel` is not a usable alternative | `lessons-learned-010.md` item 12 — the demo project has no `.git` of its own |
| A Form 5 line is the right home for a repo-wide declarative fact with no territory | decision matrix § 6 |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/docker-architect/SKILL.md` | injection absolutized and rewritten as `if/elif/else` (always exits 0); three distinct fallback messages; a table under the entry rule saying which of the four outputs arms the rule and which does not |
| `.claude/skills/{use-case-design,domain-modeling,rest-api-architect,persistence-architect,messaging-architect,test-architect}/SKILL.md` | `find docs/use-cases` absolutized |
| `.claude/skills/arch-doctor/SKILL.md` | `find .claude` absolutized |
| `.claude/skills/init-project/SKILL.md` | `find .claude/blueprints` and `ls -a` absolutized |
| `.claude/skills/claude-code-architect-designer/SKILL.md` | the four inventory `ls` calls absolutized |
| `CLAUDE.md` | one § Known pitfalls entry on cwd drift and injections — 172 → 178 lines, under the ~200 target |

No routing-table, `00-index.md`, or step 6.6/6.7/7.5 edit is needed: no file is created and
no skill changes name or ownership.

Verification run after the edits:

- `grep -rn '!`' .claude/skills/*/SKILL.md | grep -v CLAUDE_PROJECT_DIR` returns nothing —
  all 14 injections in the repo now resolve from the variable.
- `claude plugin validate .claude/skills` → `✔ Validation passed`.
- `java .claude/hooks/ArchHook.java schema` → silent, exit 0.
- The three branches of the `docker-architect` injection exercised by hand with
  `CLAUDE_PROJECT_DIR` unreadable, readable-without-the-file, and the repo root: one
  distinct message each, exit 0 in all three.

## The lint, delivered

Asked for by the user right after the approval above, so the guarantee landed in the same
branch instead of staying a proposal. The split the skill's § Out of scope draws still
held: it proposed and stopped, and the hook was written only on an explicit instruction.

| File | Change |
|---|---|
| `.claude/schemas/extensions.json` | new `injections` block — `require: "${CLAUDE_PROJECT_DIR"`, empty `exempt_patterns`, and the reasoning in `$comment`. The list is data with a single owner (invariant 10); the Java reads it and hardcodes nothing |
| `.claude/hooks/ArchHook.java` | `checkInjections` + `typedFile`/`lineOf`/`shorten`, called from `checkOne` for every file the mode visits. Reports `path:line` and the fix. The mode's header comment and its `❌` headline changed from "frontmatter" to "extension file", since it now checks more than frontmatter |
| `.claude/.ci/InjectionPathTest.java` | new CI test, five cases: relative blocked with exit 2, `${CLAUDE_PROJECT_DIR}` accepted, prose mention ignored, fenced example ignored, file outside every `types` match not scanned. Copies the real `extensions.json` rather than a fixture, and derives the executor-agent stubs from it, so neither drifts |
| `.github/workflows/validate.yml` | runs it in `hooks-cross-platform`, next to `BoundaryTest`, on all three OSes |
| `project-bootstrap/SKILL.md` step 7 part 2 | the copy rule named only `decisions/0001`; generalized to every `decisions/` citation in any `$comment`. Three blocks already carried one before `injections` added a fourth — the instruction was already stale |
| `README.md`, `docs/07-ci-validate.md`, `docs/en/07-ci-validate.md`, `docs/09-diferenciais.md`, `docs/en/09-differentiators.md` | the new test and what `schema` now covers |
| `CLAUDE.md` | the § Known pitfalls line now names the hook and the exemption mechanism |

**Why the rule is blunt.** It requires the substring in *every* injection, not only in ones
that look like they name a path. `ls -a | head -30` in `init-project` named no path at all
and was still cwd-dependent — a path-shaped heuristic would have missed it. The cost is
that a genuinely cwd-independent injection needs a regex in `exempt_patterns`; the list is
empty today, and an entry added to it needs a reason in the commit.

**Three false positives the first run produced, each now a test case.** The lint blocked
the very document describing it: `.claude/decisions/0051-*.md` (this file) quotes
injections in prose. Fixes, in the hook: scan only files a `types` match captures — a
decision record isn't a file the runtime executes injections from, and `schema` receives
one on every Edit under `.claude/`; drop fenced blocks; drop double-backtick spans, which
is how markdown quotes a string that itself contains a backtick.

Goes to the generated project: **yes** — step 7 copies `ArchHook.java` and
`extensions.json` verbatim, so the lint runs there too, against that project's own skills.
`.claude/.ci/` does not travel: this repo's CI isn't the generated project's.

Goes to the generated project: **yes, via step 6.7 — the seven pipeline skills plus
`arch-doctor` and `docker-architect` copy verbatim, so the corrected injections travel with
them. The `CLAUDE.md` line does not: axis 8 was answered "this repo only", and
`root.CLAUDE.md.example` is left untouched on purpose.**
