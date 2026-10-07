# 0129 · A foreign container on our port warns, and does not block, while no container of ours exists

- **Date:** 2026-10-07
- **Scenario:** Issue #107, triaged at `a114ad7`: compose gate blocks every Stop, including
  doc-only turns, on a foreign-container port collision even when no container of this project
  exists, and repeats every turn.
- **Decision:** Form 7c — `composeReport()` in `.claude/hooks/ArchHook.java` routes foreign-port lines to `warnings` while `docker compose ps -a` lists no container of this project. The `Stop` registration of `compose gate` (Form 7a, decision 0064) is unchanged.
- **State:** approved by Lucas Fernandes, on 2026-10-07.

## Reproduced on disk before classifying

Checked against `a114ad7` (`v0.17.8`) by `issue-verifier`, layer `static`. Only the confirmed
rows are listed here. The fix the issue proposed is not an input to this record.

| Claim | On disk |
|---|---|
| `compose gate` is registered on `Stop` in the generated project | Confirmed, `project-bootstrap/templates/settings.json.example:272`, and `.claude/settings.json:176` |
| `composeGate()` skips only on `stop_hook_active` | Confirmed, `ArchHook.java:988-998`. There is no change-set input |
| The foreign-container check blocks even at `0/0 running` | Confirmed, `ArchHook.java:1062-1086`. The `docker ps` scan of the declared host ports runs whatever `running` and `total` are, and every line it writes goes to `detail`, which sets `ok=false` (`:1088`) |
| The block repeats on every turn | Confirmed by construction. `stop_hook_active` only resets within a turn, and no state carries across turns |
| `tests` already scopes itself to changed `*.java` | Confirmed, `ArchHook.java:207-215` |
| No per-developer off switch exists for a hook mode | Confirmed. The only `System.getenv` reads are `CLAUDE_PROJECT_DIR` and `CLAUDE_CONFIG_DIR` |
| Removing the registration is reported as drift and overwritten by `/arch-adopt` | Confirmed, `arch-adopt/SKILL.md:268-272` |
| Decision 0064 did not weigh a stack that was never started | Confirmed. The only cost it accepted is "a running stack that is legitimately half-down" |
| `doctor gate` never gates `Compose` or `tests` | Confirmed, `extensions.json` `doctor.gate.labels`, and `0128` ("Never `CLAUDE_PROJECT_DIR` and `Compose`, which are runner state") |

## What is actually open

`composeReport()` has two halves, and they fail for different reasons. The **file half**
(question 3, the advertised address, the placeholders) finds defects in files the repository
versions. The **daemon half** asks Docker about the machine. One part of it is about this
project's containers: are they running? The other is about everyone else's containers: does a
foreign container hold one of our declared host ports?

That second check exists for one case, the one 0064 was built from: a service stuck in
`created` because another project's container took its port. The collision matters when *our*
container tries to bind the port. With no container of this project at all (`docker compose
ps -a` lists nothing), nothing is binding yet. The line is a heads-up about the next
`docker compose up`, not a failure of the turn that just ended. Today it still goes into
`detail` and blocks, so a turn that edited one Markdown file cannot end until the developer
stops a container that belongs to another project.

0124 already split `composeReport()` into lines that block (`detail`) and lines that inform
(`warnings`, printed by `compose` and `doctor` and never read by the gate). The fix is to route
the foreign-port lines to the second list while this project has no container.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | The verified table above: a false block, on a doc-only turn, at `0/0 running`, repeated every turn | 9 (create nothing). Decision 0064 did not weigh this case, so the issue is not "by design" |
| 7 — mandatoriness | The gate stays. Only one class of its findings is wrong in one state | 1 · 2 · 4 · 5 |
| 14 — lifecycle event | `Stop`, unchanged | Any new registration |
| 15 — reaction | Warn, without blocking, when no container of this project exists. Block exactly as today once any container of ours exists, including stopped or `created` ones | Scoping by change set (options 2 and 3) |
| 16 — existing mode | `composeReport()`, which the `compose` report, `doctor` and `compose gate` share. 0124's `warnings` channel already renders in the first two | A new mode. This 7c edits an existing method |
| Off switch | None. The issue's `ARCH_HOOK_OFF` is rejected (option 4) | 8, and an env-var opt-out |
| 8 — destination | Both. `ArchHook.java` travels whole through `export`; the registration is unchanged | — |
| 17 — CI | Nothing testable in CI. The maintainer's call, reasons under option 1 | A new `ComposeGateTest` case |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `ArchHook.java` `composeReport()`: foreign-port lines go to `warnings` while `docker compose ps -a` lists no container of this project | 9 | **Approved** |
| 2 | Option 1, plus skip `compose gate` when the turn touched no file `composeReport()` reads, judged against the `guard prompt` baseline | 6 | Rejected — saves a `docker` call the issue never complained about, at the price of a trigger list that must mirror every input of `composeReport()` and of silencing the daemon half on unrelated turns |
| 3 | Scope by change set only, with the collision still blocking | 4 | Rejected — does not fix the reproduced case |
| 4 | `ARCH_HOOK_OFF`, a per-developer environment switch for hook modes | 2 | Rejected — breaks invariant 6, and its stated CI backstop does not exist (0128) |
| 5 | Create nothing — "a gate, not a bug" (0064, pitfalls § Compose) | 3 | Rejected — 0064 accepted a half-down running stack, not a stack that was never started |

### Option 1 — foreign-port lines warn while no container of ours exists (score 9)

**Motivator:** axes 1 and 15. The false block comes from one check, in one state, and that
state is visible in data `composeReport()` already holds (`total == 0`, from `docker compose ps
-a`).

**Shape.** In `composeReport()`, the foreign-container loop writes to `warnings` instead of
`detail` when `total == 0`. The text gains a clause that says why it is not blocking:
"cannot bind it once `docker compose up` runs (nothing of this project is up yet, so not blocking)". With
`total > 0` nothing changes. That case includes every container of ours that is stopped,
`exited` or `created`, which is 0064's case. The file half is untouched. `compose` and `doctor`
already print `warnings` (`ArchHook.java:961-965`, `:470`), so the developer still sees the
collision. Only the exit code changes.

**Pros:**
- Fixes the reproduced case completely: at `0/0`, with only a foreign collision, the gate is
  silent.
- About ten lines. No new data in `extensions.json`, since the condition is a count the method
  already computes, not a list. Invariant 10 is not touched.
- Precedent 0124: a line the report prints and the gate never reads.
- Keeps 0064's guarantee where it was bought: a `created` service blocked by a foreign port
  still blocks.

**Cons:**
- A developer who never starts the stack is told about the collision only by `compose` or
  `doctor`, not at turn end. That is accepted: there is no failure until they start it, and
  then the gate blocks.
- `total` counts this project's containers, not declared services. A partially created stack
  (one container of ours exists) still blocks on a collision with a different service's port.
  That is correct: the stack is being used.

**Points cut in the rubric:** § 8 criterion 7. The CI answer is "nothing testable" rather than
a test. The reason is below, and the maintainer chose it.

**CI:** nothing testable in CI. Proving it needs a live Docker daemon *and* a foreign container
publishing a port the fixture declares. `hooks-cross-platform` runs on ubuntu, macOS and
Windows, and only ubuntu has a daemon. The maintainer chose a by-hand run (recorded under
`## CI coverage`) over a Linux-only case. The existing `validate · hooks-cross-platform ›
ComposeGateTest` keeps proving the half this change must not move: file defects still exit 2,
no compose file stays silent, and `stop_hook_active` does not cascade.

### Option 2 — option 1 plus scoping by change set (score 6)

The gate returns before `composeReport()` when the turn changed no file in a trigger list kept
in `extensions.json` (`compose.gate.trigger_globs`). "Changed" is judged against the `guard
prompt` baseline, not `git diff HEAD`, because the latter counts earlier uncommitted work. It
saves the `docker` calls on doc-only turns (up to 10 s each when the daemon is slow).

Not recommended:
- The trigger list must mirror every input of `composeReport()`: the compose file,
  `app_config_globs`, and the `src/test` sources `imageTagMismatches` reads. A question added
  later without a glob goes silent.
- The daemon half exists to catch what no file change caused, such as a crashed service.
  Scoping by change set hides that on most turns.
- The baseline exists only where `guard prompt` is registered.
- The reported pain was the false block, not the cost of a check that passes.

Points cut: criteria 4 (it weakens enforcement of the daemon half), 5 (a new list kept in
sync by hand) and 6 (no precedent for scoping a `Stop` gate by files outside `*.java`).

### Option 3 — scope by change set only (score 4)

Leaves the false positive in place on every turn that touches `application*.yml` or a
`src/test` image pin, which is any persistence or messaging work. It does not resolve the
symptom of axis 1.

### Option 4 — `ARCH_HOOK_OFF` (score 2)

A variable in the shell or in `.claude/settings.local.json` lists the modes to skip. The issue
says `doctor gate` would keep CI enforcing everything. It would not: `doctor.gate.labels`
exclude `Compose` on purpose (0128), and `tests` is not a `doctor` line at all. An unbounded
"hook modes" switch would also reach `guard` and `check`, which are territory and boundary
enforcement. That breaks invariant 6 (a guarantee turned back into an individual choice,
invisible in git), so the score is capped at 4 or below. 0111 is the existing shape of an
opt-out, owned by the project and versioned, and no failure has been observed that only an
opt-out would fix.

### Option 5 — create nothing (score 3)

Treats the block as 0064's accepted cost. The pitfalls page says so: "A service stopped on
purpose also blocks the stop; that is a gate, not a bug." But 0064 weighed a *running* stack
that is half-down, where a container of ours is affected. Here no container of ours exists,
and the block cannot be cleared without touching another project.

## References

| Claim | Source |
|---|---|
| `compose gate`, `compose` and `doctor` share one definition of healthy | `ArchHook.java` `composeReport()` Javadoc · `0064` |
| `warnings` are printed by `compose`/`doctor` and never read by the gate | `0124` · `ArchHook.java:961-965`, `:470` · `ComposeGateTest` (login half) |
| The foreign-port check exists for a `created` service blocked by another project | `0064` option 3 · lessons-learned-008, cited in `composeReport()` |
| `doctor gate` never gates `Compose` | `0128` · `extensions.json` `doctor.gate.labels` |
| An opt-out is project-owned and versioned, not per developer | `0111` |
| Narrowing a `Stop` gate in preference to turning it off | `0116` (issue #76) |
| A rule that must always hold is a hook; a guarantee changes only against an observed failure | `@CLAUDE.md` invariant 6 · decision matrix § 2.2, anti-pattern 16 |
| A triaged issue's verified rows are the record's evidence; its proposed fix is not an input | `0103` |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | `composeReport()`: foreign-port lines go to `warnings` when `total == 0`, with a clause saying why they do not block. `warnings` is copied into a mutable list, since `datasourceLoginWarnings` can return `List.of()`. The healthy summary reads "no blocking port collision". The Javadoc of `composeGate()` carries the three-sentence justification |
| `.claude/hooks/ArchHook.jar` | Rebuilt under JDK 21. `build --verify` passes |
| `CLAUDE.md` § Commands | The `compose` row says a foreign container is a warning while no container of the project exists |
| `docs/en/11-pitfalls.md`, `docs/pt-br/11-pitfalls.md` § Compose | The exception to "a stopped service blocks too", with the issue and this record |
| `docs/en/04-arch-doctor.md`, `docs/pt-br/04-arch-doctor.md` | The `Compose` row's ❌ column |
| `docs/en/01-file-types.md`, `docs/pt-br/01-tipos-de-arquivo.md` | The `compose` mode row |

Not touched: `.claude/settings.json` and `project-bootstrap/templates/settings.json.example`.
The `Stop` registration is the same, so no restart is needed for the registration itself. A
running session picks up the new behaviour on its next turn end, because every hook launches
the jar.

Goes to the generated project: **yes**. `export` copies `ArchHook.java` and the rebuilt
`ArchHook.jar` whole, so `/arch-adopt` at the new tag carries it to existing projects.

## CI coverage

Nothing testable in CI, by the maintainer's choice. The case needs a live Docker daemon *and*
a foreign container publishing a port the fixture declares, and `hooks-cross-platform` has a
daemon only on its ubuntu leg. What CI still proves is the half this change must not move:
`validate · hooks-cross-platform › ComposeGateTest` (file defects exit 2, no compose file is
silent, `stop_hook_active` does not cascade, the login warning never blocks). It ran green
locally on the new jar, as did `ComposeTagTest` and `DoctorGateTest`.

Verified by hand on 2026-10-07 instead, with Docker 29.3.1. The fixture was a compose file
declaring `postgres` on `15432:5432`. A foreign `postgres:15-alpine` named
`nerviz-foreign-107` was started outside the project on the same port.

| Case | Jar | Result |
|---|---|---|
| No container of the project, foreign container on 15432 | `HEAD` (`a114ad7`) | exit 2, `1 problem(s) — 0/0 running`. This is the issue's report, reproduced (the red run) |
| Same state | new | `compose gate` exit 0, silent. `compose` prints `✅ 0/0 … no blocking port collision` and the ⚠️ line, then `Compose healthy, with 1 warning(s) above that do not block` |
| `docker compose up -d`: our `postgres` stuck in `created` (`Bind for 0.0.0.0:15432 failed`) | new | exit 2, `2 problem(s) — 0/1 running`: the `created` line plus the collision. This is 0064's case, still blocking |
| Same, `stop_hook_active: true` | new | exit 0, no cascade |
| Both removed | new | exit 0 |
