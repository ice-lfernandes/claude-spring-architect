# 0079 · Each `ArchHook.java` mode becomes a nested class behind a sealed `Mode`, still one file

- **Date:** 2026-09-29
- **Scenario:** lessons-learned-015 topic P — `ArchHook.java` at 5 314 lines, 192 `static` methods in one namespace, and the stdin policy living in a `switch` in `main`, away from the modes it describes
- **Decision:** option 3 — keep `ArchHook.java` as it is
- **State:** approved by Lucas Fernandes, 2026-09-29
- **Goes to the generated project:** yes — `export` copies `ArchHook.java` whole; the refactor travels with no manifest change

## Interview

Answered from the lesson and the source; no axis needed a question.

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | No failure observed. Maintenance cost: helpers of eleven modes share one namespace, and which mode reads stdin is decided in `main` (`:57-64`) instead of by the mode — it became a trap in `docs/11-pitfalls.md` instead of a compile-time fact | — |
| 5 — Nature | Internal structure of an existing Form 7c executable | Every form but 7c |
| 7 — Mandatoriness | The hook already is the guarantee; this touches what enforces, not what is enforced | No new registration, no `permissions` line |
| 8 — Destination | Both — the file travels whole | Nothing to propagate in `export` |
| 9 — Integration | `build` already jars every `ArchHook$*.class` (the jar holds 12 nested classes today); the five `.claude/.ci/*Test.java` spawn the hook as a process and call no method by name; `hook-mode.java.example` teaches the flat shape | The template must change with it |
| 10 — Cost of error | High and silent: a mode that throws exits **0** through `main`'s catch — a regression in `guard` switches protection off with no signal | Weight on "incremental, one mode per step, run by hand" |
| 16 — Existing mode | Not a new mode | — |

**Hard constraint, from the lesson:** one file. The JDK 21 source launcher runs a single file (multi-file is JDK 22+), `hook_build.javac_feature` pins 21, and `export`, the source hash and 0041/0054/0075 all assume one file.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `sealed interface Mode { boolean readsStdin(String[] args); void run(String[] args, String stdin) throws Exception; }`, one `static final class` per mode with its private helpers, shared utilities (`Json`, `err`, `relative`, `readOrNull`, `asMap`…) in a nested `Util`-shaped class; `main` resolves the mode name to an instance and asks it about stdin. One mode per step | 7 | not chosen — ~5 300-line diff for no observed failure |
| 2 | Keep flat static methods; replace the two `switch`es with one `enum Mode(readsStdin, handler)` table, and regroup helpers under their mode's `// ──` banner | 5 | not chosen — no failure observed from the stdin policy either |
| 3 | Create nothing | 3 | **approved** — the trap stays documented in `docs/11-pitfalls.md` |
| 4 | One file per mode (the obvious split) | 0 | rejected — breaks the JDK 21 single-file launcher, `export` and the source hash |

### Option 1 (score 7)

**Motivator:** axes 1 + 9. The gain is namespacing: `guard`'s 860 lines of helpers stop being callable from `audit` by accident, and a new mode **cannot compile** without answering `readsStdin` — the pitfall becomes a type error.

**Mechanism.**

1. `Mode` is `sealed`, `permits` the eleven classes; `main` maps the name with a `switch` over strings to an instance (unknown → the existing message), then `m.readsStdin(args) ? readAll(System.in) : ""`, then `m.run(args, stdin)`. `main` keeps no mode-specific knowledge.
2. Cross-mode calls become qualified and visible: `doctor` → `Compose.report()`, `Build.jarProblem()`; `schema` → `Build.jarProblem()`. Nestmates keep `private` access, so nothing needs widening.
3. Order, smallest risk first: `build` → `format`/`tests`/`check` → `doctor` → `compose` → `export` → `schema` → `context` → `audit` → `guard` last (largest, and a silent regression there costs the most). Records used by one mode (`ComposeReport`, `ImagePin`, `Tok`, `Node`, `Turn`, `Usage`) move inside it.
4. **Each step:** `build` (jar rebuilt), `build --verify`, the five `.claude/.ci/*Test.java`, `schema </dev/null`, and the moved mode run by hand with a payload that must produce its non-zero exit — the only check that catches a throw swallowed into exit 0.
5. `hook-mode.java.example` teaches the new shape (a class implementing `Mode`, the `permits` line, the dispatch line); `docs/11-pitfalls.md` § stdin says the answer is now `readsStdin`, enforced by the compiler.

**Pros:** helpers scoped per mode; stdin policy owned by the mode and forced by the type; the lesson's "done when" met literally; no runtime or build change.

**Cons:** a ~5 300-line diff that is almost all indentation — review by `git diff -w` plus the per-step checks, not by reading; eleven commits' worth of risk for zero observed failure; `private` does not isolate nestmates, so the scoping is convention made visible, not enforced.

**Points cut:** no observed failure (−1); size of the diff against a silent-exit-0 failure mode (−1); nestmate `private` gives less isolation than it looks (−1).

### Option 2 (score 5)

One table instead of two `switch`es answers the stdin half at ~40 lines of diff: a mode added without a `readsStdin` argument does not compile either. It leaves the 192-method namespace as it is, so the lesson's "each mode in a nested class" is not met. The honest cheap option.

### Option 3 (score 3)

Defensible — nothing is broken — but the stdin trap stays prose, and every new mode keeps adding to one namespace.

## Disagreement with the lesson

- **The swallowed exception is the real risk, and it is not structural.** `main`'s catch turns any throw into exit 0. Out of scope here (it changes behavior, and exit 1 vs 2 per mode is its own decision), but worth its own topic: without it, this refactor's safety rests entirely on running each mode by hand.

## References

| Claim | Source |
|---|---|
| JDK 21 source launcher runs one file; multi-file is JDK 22+ | JEP 330 / JEP 458; lessons-learned-015 § P |
| `build` jars every class the compiler emits, nested included | `ArchHook.java` `compileJar`; `unzip -l .claude/hooks/ArchHook.jar` |
| CI tests are black-box | `.claude/.ci/*.java` — no `ArchHook.<method>` reference |
| A mode's throw exits 0 | `ArchHook.java` `main`, top-level catch |
| Form 7c never adds a second hook file | `claude-code-architect-designer/SKILL.md` § Out of scope; 0041, 0054, 0075 |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | the refactor, one mode per step |
| `.claude/hooks/ArchHook.jar` | rebuilt every step |
| `.claude/skills/claude-code-architect-designer/templates/hook-mode.java.example` | the nested-class shape |
| `docs/11-pitfalls.md`, `docs/en/11-pitfalls.md` (if present) | stdin paragraph points at `readsStdin` |
| `CLAUDE.md` § Commands | nothing — mode names and arguments unchanged |
