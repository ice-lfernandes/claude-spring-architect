# 0063 · Enforcement stops being tool-shaped: `guard bash` checks the paths a shell command writes

- **Date:** 2026-09-28
- **Scenario:** lessons-learned-014 § 1 — a run wrote two files with a `Bash` heredoc and
  passed through none of the five enforcement hooks, because every one of them matches on a
  tool name and `Bash` is in no matcher.
- **Decision:** Form 7a + Form 7c — a `Bash` `PreToolUse` registration in
  `.claude/settings.json` and in `project-bootstrap/templates/settings.json.example`, plus a
  `guard bash` submode in `.claude/hooks/ArchHook.java` reading its write shapes from
  `.claude/schemas/extensions.json`.
- **State:** approved by Lucas Fernandes, on 2026-09-28.

## What is actually still open

The lessons-learned example is stale and the record says so, because a later reader would
otherwise re-open a closed gap: the two files it names are
`docs/use-cases/UC-001-register-customer/CHANGELOG.md` and
`docs/use-cases/UC-002-create-account/CHANGELOG.md`, and `CHANGELOG.md` entered
`guard.frozen_exempt_basenames` in `0060-lessons-learned-013-shipping-defects.md`. Those two
writes are legitimate today through `Write` as well.

What is still open is the mechanism, and it is not hypothetical. Five hooks, five tool
matchers, none of which is `Bash`:

| Hook | Matcher | Enforces |
|---|---|---|
| `guard write` | `Write\|Edit\|MultiEdit\|NotebookEdit` | skill-class territory, frozen `UC-NNN` folders |
| `check` | `Write\|Edit` (PostToolUse) | `.claude/forbidden-imports.txt` — the layer boundary |
| `format` | `Write\|Edit` (PostToolUse) | Spotless |
| `audit file` | `Write\|Edit` (PostToolUse, generated project only) | the changed-file list of `/audit-usage` |
| `schema` | `Write(.claude/**/*.md)` / `Edit(…)` | frontmatter, `.mcp.json`, hook registrations |

`sed -i` over a frozen `UC-NNN-spec.md`, or `cat > docker-compose.yml` inside a design phase,
objects to nothing. The exposure is uneven and only one half of it has another net: the layer
boundary is re-checked by `ArchitectureTest.java` at `./mvnw verify`, and Spotless by
`./mvnw spotless:apply`. **Frozen-spec immutability and skill-class territory have no second
net at all** — they exist only as those two hooks.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Two files written through a heredoc, no refusal, no audit entry. The session's own harness carried a standing instruction to prefer `Bash` over `Write`/`Edit` | 9 (create nothing): the failure is observed, not anticipated |
| 7 — mandatoriness | Cannot be allowed to fail: it is the single guarantee behind "an approved spec is immutable" | 1 · 2 · 3 · 4 · 5 — everything above the line in the decision matrix § 1 |
| 14 — lifecycle event | `PreToolUse`, before the shell runs | `PostToolUse` for the territory check: the file would already be written |
| 15 — reaction | Block, exit 2, with the same stderr `guard write` already writes | Form 8 as the primary answer |
| 16 — existing mode | `guard` exists and holds the whole check; what it lacks is a reader for `tool_input.command` | Form 7c as a *new* mode — this is a submode of `guard`, next to `prompt`, `call` and `write` |
| — ambiguity | A write shape whose target does not resolve (a shell variable, `$(…)`, a generated script, `python3 -c`) passes silently | A deny-by-default parser |
| — post-write modes | `check`, `format` and `audit file` stay out: a `PostToolUse` matcher on `Bash` pays a JVM on every `ls` and `git status`, and two of the three already have another net | Covering all five hooks |
| 8 — destination | Both: this meta-repo and the generated project, where the frozen `UC-NNN` folders actually exist | A single-destination registration |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `guard bash` submode + `Bash` `PreToolUse` registration | 8 | **Approved** |
| 2 | `guard sweep` on `Stop` — filesystem-shaped backstop | 7 | Rejected for now — reports after the write, and a spec overwritten by `sed -i` is already lost. Complementary, not a substitute |
| 3 | `permissions.deny` on write-shaped commands (Form 8) | 5 | Rejected — bans the tool instead of enforcing the rule, and cannot express a redirect at all |
| 4 | Create nothing — correct the `CLAUDE.md` wording only | 3 | Rejected — invariant 6: prose where a guarantee was available, against a failure that was observed |

### Option 1 — `guard bash` (score 8)

**Motivator:** axes 7 and 16. The rule cannot fail, and the check that enforces it already
exists whole — `guardWrite` reads `tool_input.file_path`, applies the class territory and
then the frozen-folder check. The only thing missing is a second way to learn a path.

**Shape.** `guardWrite`'s body splits into `guardPath(sch, state, in, rel)` — territory, then
frozen folder, unchanged. `guard write` calls it once with `tool_input.file_path`.
`guard bash` calls it once per path parsed out of `tool_input.command`. Nothing about the
rules moves; only the source of the path is new.

**The parser, and what it deliberately is not.** Write shapes are data in
`guard.bash_write_shapes` of `@.claude/schemas/extensions.json` — invariant 10, no list in the
Java. Recognized: `>` and `>>` redirection (target is the next token), `tee`, `sed -i`, `cp`,
`mv`, `install`, `dd of=`. Every one of those spellings appears in the harness instruction
that caused the incident. A token that does not resolve to a literal path — one holding `$`,
a backtick, `$(`, or a glob — is skipped in silence, and a command with no recognized shape
exits 0 immediately. **The bar is not completeness; it is that the obvious spellings stop
being free.** A parser that blocks on what it cannot read would stop `./mvnw` and `git` on
its first false positive and be deleted the same week.

**Pros:** closes the two exposures that have no second net. Reuses the existing check whole,
so a territory widened in `skill_classes` widens for both paths at once — there is no second
copy of the rule to diverge. The blocked person reads the same stderr they already know.

**Cons:** criterion 9. `PreToolUse` on `Bash` has no usable `if`: a permission rule matches a
command prefix, and `cat > file` — the observed spelling — has no prefix that names the
write. So every shell command in the session pays a JVM startup, roughly 200 ms. The
alternative is narrowing to `Bash(sed:*)`-style rules and losing the redirect case, which is
the case that actually happened. Criterion 5 too: shell has more spellings than the list, and
each one added later is a real edit.

**Points cut in the rubric:** § 8 criterion 9 (a process per shell command, no filter
possible) and criterion 5 (the shape list is open-ended by nature).

### Option 2 — `guard sweep` on `Stop` (score 7)

A filesystem-shaped backstop: at `Stop`, diff the working tree against the open phase's
territory and the set of frozen `UC-NNN` folders. It does not care which tool wrote, so it
covers every spelling the parser will never learn, and it pays one JVM per turn instead of
one per command.

Rejected as the answer because it loses criterion 4: it reports a frozen spec **after** it has
been overwritten, and the immutability guarantee is exactly the one whose value is being
before the write. It also inherits lessons-learned-014 § 11's problem — a working tree dirty
from before the run makes it noisy on its first report.

Worth revisiting on its own after option 1 ships, as the complement that catches what the
parser cannot read.

### Option 3 — `permissions.deny` on write-shaped commands (score 5)

`Bash(sed -i:*)`, `Bash(tee:*)` and friends. Costs no process and is read before the call.

Rejected on three counts. It is not a flat refusal — the same `sed -i` is legitimate on a path
inside the active phase's territory, so a `deny` blocks the rule's own compliant case
(criterion 9). It bans tools rather than enforcing the rule, so the territory and the frozen
folders stay unenforced (criterion 4). And a permission rule matches a command prefix: `cat >
docs/use-cases/UC-001-register-customer/CHANGELOG.md` is `Bash(cat:*)`, which no one will deny
— the observed spelling escapes entirely (criterion 1).

### Option 4 — create nothing (score 3)

Correct `CLAUDE.md`'s wording to the narrower true statement — *inside Claude Code, and only
through `Write`/`Edit`* — and accept a tool-shaped net.

Rejected: invariant 6 says a rule that must always hold is a hook, not prose, and the failure
here was observed rather than feared. Capped at ≤ 4 for straining an invariant. The wording
fix itself is kept — it rides with option 1, restated for what will then be true.

## References

| Claim | Source |
|---|---|
| A hook fires on a tool-name `matcher`, and `PreToolUse` is one of the events that reads one | `.claude/schemas/extensions.json` § `settings.matcher_events` · `@claude-help.md` § 8 |
| A rule that must always hold is a hook, not prose | `@CLAUDE.md` invariant 6 |
| A Form 7c mode reads its lists from `extensions.json`, never from a constant in the Java | `@CLAUDE.md` invariant 10 |
| The territory and frozen-folder checks live in one method and are reused, not copied | `@CLAUDE.md` invariant 2 · `.claude/hooks/ArchHook.java` `guardWrite` |
| `CHANGELOG.md` is already writable inside a frozen folder, so the lessons' example is closed | `.claude/decisions/0060-lessons-learned-013-shipping-defects.md` · `guard.frozen_exempt_basenames` |
| A blocking hook names the rule, the file and the way out | `references/decision-matrix.md` § 7 anti-pattern 18 · the stderr `guardWrite` already writes |
| A hook with no filter pays a JVM per event, and that is what gets it deleted | `references/decision-matrix.md` § 8 criterion 9 · anti-pattern 15 |
| `.claude/settings.json` is read only at session startup | `@claude-help.md` § 8 |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | `case "bash"` in `guard`'s dispatch; `guardBash` parsing `tool_input.command`; `guardWrite`'s body extracted to `guardPath` and called by both |
| `.claude/schemas/extensions.json` | `guard.bash_write_shapes` — the recognized shapes and where each one's target sits — plus the `$comment` that says why an unresolvable target passes |
| `.claude/settings.json` | `PreToolUse` group with `matcher: "Bash"` running `guard bash`; § 2 alignment — `format` and `check` move from `Write\|Edit` to `Write\|Edit\|MultiEdit\|NotebookEdit` |
| `.claude/skills/project-bootstrap/templates/settings.json.example` | the same registration, and the same alignment including `audit file` |
| `CLAUDE.md` § Known pitfalls | the new entry: which shapes are checked, which pass, and the corrected statement of what the enforcement covers |
| `CLAUDE.md` § Commands | a row for `java .claude/hooks/ArchHook.java guard bash` run by hand |

Goes to the generated project: **yes** — the registration through
`project-bootstrap/templates/settings.json.example` (step 6.6), the mode and its data for free,
since `export` copies `ArchHook.java` and `schemas/extensions.json` whole.

**Restart warning:** `.claude/settings.json` is read only at session startup. Nothing here
fires until `claude` is restarted.

## Verification run before reporting it done

`guard bash` was exercised by hand against a sandbox holding a frozen `UC-001-x` folder and an
open `new-feature` phase — a mode that throws exits 0 through the top-level catch and reads as
a pass, so the mode running is not evidence that it works.

| Command under test | Result |
|---|---|
| `cat > docs/use-cases/UC-001-x/notes.md <<'EOF'` (body holding `>` lines) | exit 2, frozen-folder message; the heredoc body was not read as redirects |
| `cat > docs/use-cases/UC-001-x/CHANGELOG.md <<EOF` | exit 0 — `frozen_exempt_basenames` |
| `sed -i '' s/approved/draft/ docs/use-cases/UC-001-x/UC-001-spec.md` | exit 2 |
| `echo x \| tee docs/use-cases/UC-001-x/z.md` | exit 2 |
| `cp $SRC docs/use-cases/UC-001-x/y.md` | exit 2 on the literal target; the `$SRC` source skipped |
| `cat > src/main/Foo.java <<EOF` with `/new-feature` open | exit 2, `orchestrator` territory message |
| `cat > docs/use-cases/UC-009-y/00-caso-de-uso.md <<EOF`, same phase | exit 0 — inside territory |
| `ls -la`, `./mvnw -q test`, `git commit -m "fix: a > b"` | exit 0 — no shape, and a quoted `>` is a character |
| `echo hi > /tmp/whatever.txt` | exit 0 — outside the repository |

`java .claude/hooks/ArchHook.java schema` passes, `claude plugin validate .claude/skills`
passes, and `doctor` reports `Hooks ✅ 16 registration(s) across 4 event(s)` — one more than
before, which is the count intended.
