# 0075 · Hooks run a versioned, precompiled `ArchHook.jar` instead of source-launching `ArchHook.java`

- **Date:** 2026-09-29
- **Scenario:** lessons-learned-015 § A — every hook registration runs `java ArchHook.java
  <mode>`, which compiles the whole source on every call, and `format`/`check` carry no `if`.
- **Decision:** Form 7a + 7c — option 1: `build` mode in `.claude/hooks/ArchHook.java`,
  versioned `.claude/hooks/ArchHook.jar`, every registration in `.claude/settings.json` and
  `project-bootstrap/templates/settings.json.example` launching it with `java -jar`
- **State:** approved by Lucas Fernandes, on 2026-09-29, with one addition: "garanta mesma
  versão ao executar o javac" — see § Same `javac`, pinned

## Measured

Same machine, JDK 21.0.11, `ArchHook.java` at 260 KB (lessons-learned-015 measured it at
129 KB — the cost grows with every mode added).

| Invocation | `format` | `check` | `schema` |
|---|---|---|---|
| `java ArchHook.java <mode>` (source launch) | 3471 ms | 3051 ms | 3354 ms |
| `java -cp <classes> ArchHook <mode>` | 333 ms | 288 ms | — |

`javac` of the source: 3533 ms. The source launcher pays that compile on **every** call, and
`format` and `check` then return at their first line for any file that is not `.java`
(`ArchHook.java:83`, `:141`).

What that costs per event, in this repository today: every `Bash` call 1 JVM (`guard bash`),
every edit 3 (`guard write`, `format`, `check`) plus `schema` under `.claude/`, every `Stop` 4
(`schema`, `guard sweep`, `compose gate`, `tests`) — roughly 14 s of hook latency at the end
of each turn. The generated project's template adds the `audit` entries on top.

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Measured above: ~3.3 s per hook, most of them with zero useful work | Create nothing |
| 7 — Mandatoriness | The hooks already exist as guarantees; the question is their price, not whether they run | 1–6 |
| 8 — Destination | Both: this repo's `settings.json` and `project-bootstrap/templates/settings.json.example` | — |
| 16 — Existing mode | Every mode stays; what changes is how it is launched, plus one new mode to produce the artifact | 7a alone (the jar has to come from somewhere) |
| Scope | `if` filters + precompiled artifact now; consolidating modes per event (§ A.3) later | — |
| Artifact | **Versioned jar**, chosen over a gitignored jar built on `SessionStart` and over compiling in bootstrap step 7 | See options 2 and 3 |
| `Write(...)` in `if` | Replace with `Edit(...)` in the same change | — |

## Same `javac`, pinned

The user approved option 1 on the condition that the jar is always produced by the same
compiler. What makes that true:

- `hook_build` in `.claude/schemas/extensions.json` holds the pin (`javac_feature: 21`,
  `release: 21`), plus the source, jar, main class and hash entry paths — invariant 10, no
  constant in the Java.
- `build` refuses, exit 2, when `Runtime.version().feature()` differs from `javac_feature`,
  naming the JDK it needs. It compiles in-process (`ToolProvider`), so the `javac` is the
  running JDK's own — there is no second binary on `PATH` to disagree with it.
- The jar is deterministic: entries STORED, sorted, timestamps fixed at 1980-02-01, the
  manifest written by hand. The source hash is taken over the text with CRLF normalized to
  LF. A CRLF checkout of the same source produces a byte-identical jar — verified.
- `build --verify` compiles into memory and compares bytes with the committed jar. CI runs it
  on Linux, macOS and Windows under Temurin 21 (`.github/workflows/validate.yml`,
  `hooks-cross-platform`). That is what tells a *tampered* jar from a stale one: the hash
  entry alone can be forged, a byte comparison against a fresh compile cannot.

Consequence worth knowing: a JDK 22+ alone on a machine can *run* the jar but cannot
*rebuild* it. Changing the pin is a one-line data change plus a rebuild, in one commit.

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `if` filters + versioned `.claude/hooks/ArchHook.jar` + `build` mode (7a + 7c) | 8 | Proposed |
| 2 | `if` filters + gitignored jar built on `SessionStart` (7a + 7c) | 6 | Rejected by the user — the missing-jar window |
| 3 | `if` filters only (7a) | 5 | Rejected — leaves every `Bash`, prompt and `Stop` at ~3.3 s per JVM |
| 4 | create nothing | 2 | Rejected — the cost is measured, not anticipated |

### Option 1 — versioned `ArchHook.jar` (score 8)

**Motivator:** axis 1, measured; the user's choice on the artifact axis.

**Shape:**

- New mode `build` (Form 7c): compiles `ArchHook.java` in-process through
  `javax.tools.ToolProvider` with `--release 21`, and writes `.claude/hooks/ArchHook.jar`
  with `Main-Class: ArchHook`, entries sorted and timestamps fixed, plus a resource holding
  the SHA-256 of the source **with line endings normalized to `\n`** — `* text=auto` in
  `.gitattributes` means a Windows checkout may hold CRLF, and a raw hash would call a fresh
  jar stale (commit `775f000` was the same trap in the `$ARGUMENTS` check).
- Every hook registration, both files: `"command": "java"`,
  `"args": ["-jar", "${CLAUDE_PROJECT_DIR}/.claude/hooks/ArchHook.jar", "<mode>", …]`. A jar
  path has no classpath separator, so exec form stays identical on Windows and Unix — a
  `-cp a;b` would not.
- `schema` gains a staleness check: normalized hash of `ArchHook.java` against the one inside
  the jar; mismatch → exit 2 naming `java .claude/hooks/ArchHook.java build` as the fix.
  `doctor` reports the same line. CI already runs `schema`, so a commit that changes the
  source without rebuilding fails there too.
- This repo only: `PostToolUse` with `"if": "Edit(.claude/hooks/ArchHook.java)"` runs
  `java .claude/hooks/ArchHook.java build` (source launch on purpose — a source that no longer
  compiles reports its compile errors right there). The generated project does not edit its
  hook; `arch-adopt` replaces both files through `export`.
- `export.copy` gains `ArchHook.jar`; `.gitattributes` gains `*.jar binary`.
- `format` and `check`: `"if": "Edit(**/*.java)"`. Lossless — both return at their first line
  for anything else.
- The four `"if": "Write(...)"` entries under `PreToolUse` become `Edit(...)`: path rules are
  checked only against `Edit(...)` and `Read(...)`, and `Edit` covers every built-in tool
  that edits files. **Corrected while writing:** the Pre/Post `schema` pairs are *not*
  folded. Under `PreToolUse`, only `Write` carries the full new content; on an `Edit` the
  mode would read the stale file on disk and could block the very edit that fixes it. So the
  `PreToolUse` entries keep `matcher: "Write"` and only their `if` changes.
- The manual commands in `CLAUDE.md` § Commands keep the source launch: a person running a
  mode by hand pays 3 s once and never runs a stale artifact.

**Pros:** ~10× per hook (~3.3 s → ~0.3 s) on every event, in both repositories; `.md` edits
spawn no `format`/`check` JVM at all; works from the first second of a fresh clone; no
`SessionStart` cost; no window in which the jar is missing.

**Cons:**
- A ~150 KB binary in git, and every change to the hook is a rebuild plus a commit of the
  jar. The auto-rebuild entry and the `schema` check make forgetting loud, not impossible.
- **Trust:** a reviewer cannot read what the jar executes. The `schema` hash check catches a
  stale jar, not a tampered one — a jar can embed the right hash over different classes.
  Closed in this same change by the user's addition — § Same `javac`, pinned.
- A JDK older than 21 cannot load the classes — already the stated dependency.

**Points cut in the rubric:** 5 (maintenance: binary artifact + rebuild discipline),
8 (trust surface: opaque executable in the repo).

### Option 2 — gitignored jar, built on `SessionStart` (score 6)

Same launch and filters, jar not versioned; `java ArchHook.java build` on `SessionStart`
(~3.5 s per start, `/clear` and resume). Rejected by the user: if that build fails or times
out, every `java -jar` exits 1, which **does not block** — `guard`, `check` and `schema` are
off in silence until the next start. Exec form has no shell, so there is no fallback to the
source launch.

### Option 3 — `if` filters only (score 5)

Pure JSON. Fixes `.md` edits; leaves `guard bash` at ~3.5 s on every `Bash` call, the
`UserPromptSubmit` entries on every prompt, and ~14 s on every `Stop`.

### Compiling in bootstrap step 7 (not scored)

Not an alternative: it does not cover this repository, and a clone of the generated project
either lacks the classes (option 2 without its `SessionStart`) or versions them (option 1).

## Found while writing

- **`export` must copy the source verbatim.** `transform` rewrites text in every copied file;
  applied to `ArchHook.java` it would make the generated project's source hash differ from the
  jar next to it, and that project's `schema` would call its own jar stale on day one. The
  `copy` entry now carries `"verbatim": true`, and the jar travels through a separate
  `binary_copy` group — kept out of the text pipeline, the stamp and the residue scan, which
  would otherwise read a zip as UTF-8. Verified by exporting to a scratch repository: jar and
  source byte-identical, `doctor` there reports `Hook jar ✅`.
- **`.gitattributes` gains `*.jar binary`** — `* text=auto` would otherwise be free to guess.

## Measured after

| Invocation | Before | After |
|---|---|---|
| `schema` (full sweep) | 3354 ms | ~650 ms |
| `check` on a non-`.java` file | 3051 ms | not spawned (`if`); ~170 ms when launched by hand |

## References

| Claim | Source |
|---|---|
| `if` uses permission-rule syntax, tool events only, best-effort | `.claude/claude-code-docs/04-hooks.md` § Campo `if`; `@claude-help.md` § 8 |
| Path rules are checked only against `Edit(...)`/`Read(...)`; `Edit` covers every built-in editing tool | `.claude/claude-code-docs/07-settings-permissoes-e-seguranca.md`, paths table |
| A hook exiting non-zero other than 2 does not block | `references/hook-events.md` § Exit codes |
| `ROOT` comes from `CLAUDE_PROJECT_DIR`, not from the source path — `-jar` changes nothing | `.claude/hooks/ArchHook.java:34` |
| A second hook file is out of scope; enforcement stays in one executable | `claude-code-architect-designer` § Out of scope |
| Invariant 10: the mode reads its lists from `extensions.json` | `@CLAUDE.md` invariant 10 |
| Deterministic STORED entries; `ToolProvider` compiles with the running JDK | `.claude/hooks/ArchHook.java` § build |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` | `build` mode (`--verify`); `schema` and `doctor` report a stale or missing jar; `export` honours `verbatim` and copies `binary_copy` |
| `.claude/hooks/ArchHook.jar` | new, built under JDK 21.0.11 |
| `.claude/schemas/extensions.json` | `hook_build` block; `export.copy` ArchHook entry `verbatim`; `export.binary_copy` |
| `.claude/settings.json` | every entry `-jar …ArchHook.jar`; `Write(…)` ifs → `Edit(…)`; `format`/`check` gain `if: Edit(**/*.java)`; new `PostToolUse` `build` entry (source launch, this repo only) |
| `.claude/skills/project-bootstrap/templates/settings.json.example` | same, without the `build` entry |
| `.gitattributes` | `*.jar binary` |
| `.github/workflows/validate.yml` | `build --verify` and a `java -jar … doctor` step on all three OSes |
| `CLAUDE.md` | § Commands: `build`, `build --verify`; § Known pitfalls: hooks run the jar |
| `docs/11-pitfalls.md` | `Write(<path>)` in `if` never matches; `build` does not read stdin |
| `.claude/skills/claude-code-architect-designer/` | `SKILL.md` step 8 and 10, `references/hook-events.md` `if` row, `templates/hook-entry.json.example`, `templates/hook-mode.java.example` |

Goes to the generated project: **yes** — via step 6.6, `export` copies the source verbatim, the
jar through `binary_copy`, and `hook_build` inside `extensions.json`; the template's
registrations launch the jar.

**Restart warning:** `settings.json` is read only at session startup. Until `claude` is
restarted, this session keeps running the source-launched registrations.
