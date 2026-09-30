# 0064 · `compose gate` on `Stop` — the check that existed and was never run

- **Date:** 2026-09-28
- **Scenario:** lessons-learned-014 § 13.3 — a `kafka` block publishing 9092 while advertising
  only `kafka:9092` shipped on day one and stayed broken for every host client until a use case
  needed it. `ArchHook.java compose` is written to catch exactly that shape, from the file
  alone, and had never been executed against the project in its history.
- **Decision:** Form 7a + Form 7c — a `Stop` registration in `.claude/settings.json` and in
  `project-bootstrap/templates/settings.json.example`, plus a `compose gate` submode wrapping
  the existing `composeReport()`.
- **State:** approved by Lucas Fernandes, on 2026-09-28.

## What is actually open

The detector is not missing. `docker-architect` step 7 already calls `java
.claude/hooks/ArchHook.java compose` "the one command that verifies the result", "not
optional", and names this exact defect shape. It was never typed. Two green signals stood in
for it and both describe a broker the file does not produce: the healthcheck runs *inside* the
container, where `localhost` is the broker, and Testcontainers wires its own advertised
listeners.

So this is anti-pattern 17's mirror — not a new Java mode where a registration would do, but a
**registration that was never made for a mode that already exists**. What the mode lacks for
the job is only its exit behaviour: `compose()` prints a six-line report to stderr and never
exits non-zero, which is a diagnostic shape, not a gate shape.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | A service committed on day one, unreachable from the host, found only when a use case needed it. Observed twice: lessons-learned-002 § 11 fixed the listener half, this run found the healthcheck half | 9 (create nothing) |
| 7 — mandatoriness | Cannot depend on someone remembering to type it — that is precisely what failed | 1 · 2 · 4 · 5 |
| 14 — lifecycle event | `Stop`. It reads files (and the daemon when there is one), so it belongs where `tests` already is, not on a tool matcher | `PostToolUse`: nothing writes compose often enough to justify it |
| 15 — reaction | Silent when healthy, exit 2 with the failing lines when not | Registering `compose` as it stands |
| 16 — existing mode | `composeReport()` holds the whole check and is already shared with `doctor` | A new check. The 7c here is a wrapper, not a second definition of "healthy" |
| 8 — destination | Both | A single-destination registration |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `compose gate` submode + `Stop` registration | 9 | **Approved** |
| 2 | Register `compose` unchanged on `Stop` | 6 | Rejected — six lines of report per turn, green or not, and it never blocks |
| 3 | File-only submode, never touching the daemon | 7 | Rejected — drops "the container is not answering" and "a foreign container holds the port", two of the four things the mode checks |
| 4 | Mandatory verify step inside `docker-architect` | 4 | Rejected — invariant 6. Step 7 already says "not optional" and was skipped anyway |

### Option 1 — `compose gate` (score 9)

**Motivator:** axes 7 and 16. The check exists, is already shared with `doctor`, and needs one
thing to become enforcement: an exit code.

**Shape.** `compose` takes a submode, defaulting to today's `report` so
`java .claude/hooks/ArchHook.java compose` keeps printing what `@CLAUDE.md` § Commands
documents. `gate` calls the same `composeReport()`, returns silently when `ok()`, and otherwise
writes the failing detail lines and exits 2. `stop_hook_active` is honoured the way `tests`
does it, so a blocked stop never cascades.

**Pros:** no second definition of healthy — `doctor`, `/compose` by hand and the gate read one
report. Costs nothing on a project with no compose file: `composeReport()` returns before the
first `docker` call. The file-only half (image tag, advertised address) still answers on a
machine with Docker off, which is the half that catches the defect this gap is about.

**Cons:** criterion 9. `docker compose ps` has a 10-second timeout, so a turn that ends while
the daemon is starting can pay it. A running stack that is legitimately half-down — someone
stopped a service on purpose — blocks the stop until they say so, which is the cost of a gate
and not a bug.

**Points cut in the rubric:** § 8 criterion 9 (a daemon call per turn end, bounded at 10s).

### Option 2 — register `compose` unchanged (score 6)

Form 7a with no Java at all. Rejected on criterion 9 and on the evidence in the gap itself: a
report printed every turn whether or not anything is wrong is a report people learn to skip,
and `docker-architect` step 7 already proved that naming a check "not optional" does not make
it run.

### Option 3 — file-only submode (score 7)

Runs `imageTagMismatches` and `advertisedAddressIssues` only, never calling Docker. Cheaper and
fully deterministic. Rejected because the mode's other two checks — a service that is not
answering, and a foreign container holding a published port — are also things no other piece
looks at, and skipping the daemon to save 10 seconds at the end of a turn buys the wrong thing.

### Option 4 — mandatory step in `docker-architect` (score 4)

Have the skill run the check and record the exit code in the partial. Straining invariant 6:
this is prose where a guarantee is available, and the prose already exists and was skipped.
Kept as a *complement* — lessons-learned-014 § 13.2 asks for the healthcheck to be executed
once against the started container, which is a different check the gate cannot make — but not
as the answer here.

## References

| Claim | Source |
|---|---|
| A rule that must always hold is a hook, not prose | `@CLAUDE.md` invariant 6 |
| `compose` checks published-vs-advertised from the file alone, no Docker needed | `@CLAUDE.md` § Routing, `ArchHook.java` `composeReport()` |
| `doctor` and `compose` share one report so they cannot disagree | `ArchHook.java` `composeReport()` javadoc |
| A `Stop` hook honours `stop_hook_active` or it cascades | `ArchHook.java` `tests()` · `@claude-help.md` § 8 |
| A new mode is a `case` in the dispatch plus its own method, lists in `extensions.json` | `@CLAUDE.md` invariant 10 |
| Reusing an existing mode is the common case for Form 7 | `references/decision-matrix.md` § 2.2 · anti-pattern 17 |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | `compose` takes a submode; `gate` returns silently when healthy and exits 2 with the failing lines otherwise; `stop_hook_active` honoured |
| `.claude/settings.json` | `Stop` entry running `compose gate` |
| `.claude/skills/project-bootstrap/templates/settings.json.example` | the same entry |
| `CLAUDE.md` § Commands | the `compose gate` row next to the existing `compose` one |
| `CLAUDE.md` § Known pitfalls | the entry saying the check now runs unprompted, and what it still does not cover |

Goes to the generated project: **yes** — the registration through the template (step 6.6), the
mode for free with `export`'s copy of `ArchHook.java`.

**Restart warning:** `.claude/settings.json` is read only at session startup.

## Verification run before reporting it done

A mode that throws exits 0 through the top-level catch and reads as a pass, so the gate was
exercised by hand against a sandbox before it was called done.

| Case | Result |
|---|---|
| No compose file | exit 0, silent |
| `kafka` publishing 9092 while advertising only `PLAINTEXT://kafka:9092` | exit 2, naming the service, the published port and the fix — the lessons-learned-014 § 13(a) defect, read from the file with the daemon down |
| The same payload with `stop_hook_active: true` | exit 0 — no cascade |
| A healthy compose file with the daemon down | exit 0 — "not checked" is not a failure, so a machine without Docker running is never blocked |
| `java .claude/hooks/ArchHook.java compose` with no submode | unchanged six-line report |

`claude plugin validate .claude/skills` passes, `schema` passes, and `doctor` reports
`Hooks ✅ 18 registration(s) across 4 event(s)`.
