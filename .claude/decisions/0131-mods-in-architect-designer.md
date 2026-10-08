# 0131 · Extend `claude-code-architect-designer` to cover mods, and ship the first one

- **Date:** 2026-10-08
- **Scenario:** "Form 9: mods como forma de extensão, com references, templates, hook de validate e bloco mods em extensions.json" — after the question "should `claude-code-architect-designer` absorb this scope, or should a new skill handle creating, editing and updating mods?"
- **Decision:** option 1 — Form 9 in `.claude/skills/claude-code-architect-designer/SKILL.md`, and the first mod, `.claude/mods/nerviz-cockpit/`, with all four items
- **State:** approved by Lucas Fernandes, on 2026-10-08
- **Goes to the generated project:** no — axis 8 = meta-repo only

## Interview

Six explicit questions were put to the user, about the skill's own scope, one level up. Same
method as 0033 and 0053.

| Axis | Answer | Options it eliminated |
|---|---|---|
| 1 — symptom | No mod exists. Form 9 lands **together with a first real mod** (`nerviz-cockpit`), so the templates come from code that runs | Form 9 alone (templates unproven until a first mod, anti-pattern 9); record only |
| 8 — destination | **Meta-repo only.** `export` does not change; the generated project gets no second runtime and no version floor | "both", "generated project only" |
| Loading | **Local directory marketplace** declared in `.claude/settings.json` (`extraKnownMarketplaces` + `enabledPlugins`) — versioned, loads in place | `--plugin-dir` per person (forgotten), `CLAUDE_CODE_PLUGIN_DIRS` in `env` |
| 7/15 — power | **UX, plus blocking only as a mirror.** A mod may hold a call only where a settings hook or `permissions.deny` already guarantees the same outcome; it never loosens a decision | A mod as the guarantee itself (does not load in `-p`, VS Code, cloud, `--safe-mode`); UX with no `tool.*` hook at all |
| Language | **TypeScript** — `register.ts`, `*.test.ts`, types from the installed version's `.claude-plugin/types/*.d.ts`; no build step | JavaScript |
| 17 — CI | **`schema` in Java + a path-filtered CI job with a pinned Claude CLI** running `claude plugin validate` and `claude plugin test` (which need no session, sign-in or network). Node enters this repo's CI only | `schema` + local `plugin test` only; local only |
| Version floor | `mods.min_version` in `extensions.json`; `doctor` gains a `Mods` line. This machine runs 2.1.280; mods need ≥ 2.1.287 | Floor written only in prose |
| Cockpit v1 | Band `AbovePrompt` (open phase, class, territory), `Spinner` suffix (phase, turn cost), `/nerviz-doctor` (runs `ArchHook.jar doctor`, shows it in a `Pane`, no turn), dialog over `guard bash` | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Form 9 in the designer + `nerviz-cockpit` v1 with all four items | 7 | **Approved** |
| 2 | Form 9 in the designer + `nerviz-cockpit` v1 read-only (no dialog over `guard bash`) | 8 | Rejected by the user — the dialog ships with the first mod; option 1's con 3 is answered by the `.catch` change below |
| 3 | Record only — Form 9 waits for a mod with an observed failure behind it | 5 | Rejected — leaves the answer on paper |
| 4 | A new skill dedicated to mods (`mod-designer`) | 3 | Rejected — duplicates the interview, the matrix, Phase 3.5 and propagation (invariant 2); contradicts 0033 and 0053 |

### Option 1 — Form 9 + cockpit v1, four items (score 7)

**Motivator:** axis 1 — the open phase, its class and its write territory are invisible
until `guard` refuses a write; the refusal is the first time the person learns a phase was
open (`docs/pt-br/11-pitfalls.md`, write territories). Axis 7 answered UX with mirrored
blocking.

**Shape:**

```
.claude/mods/
├── .claude-plugin/marketplace.json        # marketplace root, lists ./nerviz-cockpit
└── nerviz-cockpit/
    ├── .claude-plugin/plugin.json
    ├── hooks/hooks.json                   # "modules": ["./register.ts"]
    ├── register.ts                        # band, spinner, /nerviz-doctor, guard dialog
    └── tests/*.test.ts

.claude/settings.json      extraKnownMarketplaces (directory .claude/mods) + enabledPlugins
.claude/schemas/extensions.json
  ├── mods { match, min_version, events, mirrored_events, forbidden_calls, ci_cli_version }
  └── settings.allowed  += extraKnownMarketplaces
.claude/hooks/ArchHook.java
  ├── schema   + mods check (data from `mods`)                       ← 7c on an existing mode
  ├── guard status  (read-only JSON: open phase, class, write_allow)  ← 7c, one sub-mode
  └── doctor   + `Mods` line (CLI version vs floor, mods on disk)
.claude/skills/claude-code-architect-designer/
  ├── SKILL.md            Form 9 row, axes 18-20, Phase 4 steps, Out of scope, Contract
  ├── references/mod-events.md      events, render sites, mods API, the mirror norm
  ├── references/decision-matrix.md § 2 row + § 2.3 (mod vs settings hook)
  ├── references/hook-events.md     plugin row: ❌ → Form 9
  └── templates/mod.*.example       plugin.json, hooks.json, register.ts, test.ts, marketplace.json

cockpit ──$.process.run──▶ java -jar ArchHook.jar guard status | doctor
        ──tool.check(Bash)─▶ await next(e)  (settings hook guard bash decides) ─▶ render reason
```

**Pros:** one owner for "which form" (the designer already decides hook vs skill vs
permission, and a mod is decided against exactly those); same shape as 0033 (MCP) and 0053
(hooks); every rule stays in `ArchHook.java` — the mod renders what `guard status`, `guard
bash` and `doctor` already decide, so no rule is restated in TypeScript (invariant 2); the
guarantee stays in settings hooks (invariant 6).

**Cons:**

1. **A second language** in a repo whose hooks are one Java file. Bounded: TypeScript lives
   only under `.claude/mods/**`, never travels (axis 8), and holds no rule.
2. **Node enters this repo's CI** (one path-filtered job). `@CLAUDE.md` § Dependencies is
   about the user's machine and the generated project, and neither changes — but it is a
   new dependency of the pipeline.
3. **The dialog intercepts every `Bash` call.** It never decides — it awaits `next(e)`,
   which carries `guard bash`'s decision, and only renders it — but a `.catch` that returns
   `deny` makes a bug in the mod a refused `Bash` for the developer. The CI job's tests are
   the mitigation, not a cure.
4. **Novel design** — no mod in the repo; the marketplace-in-project-settings path is
   unverified on this machine (2.1.280).
5. **This machine cannot run a mod** until `claude update` reaches ≥ 2.1.287 — Phase 4 step
   11 needs it.

**Points cut in the rubric:** 5 (maintenance: second language, Node in CI, a new sub-mode);
6 (no precedent for a mod); 8 (trust surface: an in-process, unsandboxed hook on every
`Bash` call, with `process.run`). 6/9 → 7.

**CI:** `validate · design › frontmatter schema` (extended: mod structure from `mods` in
`extensions.json`); `validate · hooks-cross-platform` new step `ModsSchemaTest` (schema
accepts the cockpit, fails by name on a mod with an unknown event, a `tool.*` hook without
a mirror, a missing `.catch`) and a case in `BashGuardTest`-style `GuardStatusTest` for the
new sub-mode; `templates` new job `mods` (pinned `@anthropic-ai/claude-code`, `claude plugin
validate .claude/mods/nerviz-cockpit` + `claude plugin test .claude/mods/nerviz-cockpit`),
paths `.claude/mods/**` and the workflow in both trigger lists.

### Option 2 — Form 9 + cockpit v1 read-only (score 8)

Same as option 1 without the dialog over `guard bash`. The cockpit registers no `tool.*`
hook: band, spinner and `/nerviz-doctor` only. Removes con 3 entirely and the trust-surface
point (the mod observes and draws, `process.run` on `java` only), and keeps the dialog for
when a `guard bash` refusal is observed to be misread — the trigger axis 1 asks for.
`mods.mirrored_events` and the `.catch` check still ship in `schema`, so the dialog later
is a mod edit, not a design change. Points cut: 5, 6. 7/9 → 8. **CI:** as option 1, minus
the dialog's test cases.

### Option 3 — record only (score 5)

Keep this record, change nothing, add Form 9 when a mod has an observed failure behind it.
Cheapest; no Node, no TypeScript. Leaves the answer to the scenario's question on paper only
and the band's symptom (axis 1) unaddressed. Points cut: 1 (the scenario asks for a form),
4, 6, 7. **CI:** nothing testable, because nothing is written.

### Option 4 — a dedicated `mod-designer` skill (score 3)

Rejected. The question "mod, settings hook, skill or permission?" is the designer's; a
separate skill makes the user pre-classify before invoking it. It would carry its own copy
of the interview, the matrix, Phase 3.5 and the propagation table (invariant 2) and its own
`meta`-class territory over `.claude/**`. 0033 and 0053 absorbed MCP and hooks the same way.
A separate piece is justified only if mods grow into daily UI iteration — and then it is an
`executor` agent with `write_allow: .claude/mods/**`, designed by this skill, not a second
designer.

## References

| Claim | Source |
|---|---|
| A mod is a plugin whose `hooks/hooks.json` has `modules`; runs in-process, no sandbox | `docs/en/claude-code-docs/16-mods.md` § What a mod is; <https://code.claude.com/docs/en/plugins/mods/overview> |
| Mods do not draw in `-p`, VS Code, cloud, mobile; `--safe-mode` turns them off | `16-mods.md` § Where it runs; rule 1 |
| `tool.check` runs after permission rules and settings hooks; `next(e)` resolves to their decision | `16-mods.md` § Events, `tool.check` row |
| Managed `PreToolUse` runs before mods; other `PreToolUse` after the last mod's `next` | `16-mods.md` § Chain order |
| `claude plugin test` needs no session, sign-in or network | `16-mods.md` § Tests; <https://code.claude.com/docs/en/plugins/mods/test> |
| Mods available from v2.1.287; this machine runs 2.1.280 | `claude --version` |
| A local directory marketplace listed by relative path loads in place | `docs/en/claude-code-docs/06-plugins-and-marketplaces.md` |
| Invariant 6 (guarantee is a hook or `permissions.deny`), 2 (single owner), 9 (export), 10 (lists are data) | `@CLAUDE.md` |
| Absorbing a new extension kind into the designer | `0033-mcp-in-architect-designer.md`, `0053-hooks-in-architect-designer.md` |
| `settings.allowed` rejects an unknown top-level key of `settings.json` | `.claude/schemas/extensions.json` › `settings.allowed` |
| Guard phase state lives in `${java.io.tmpdir}/archhook-guard/<session>` | `ArchHook.java` `guardState` |

## What changed while writing it

Three departures from the option as proposed, each found by running the code, not by
re-deciding:

1. **`.catch` falls through instead of refusing.** The proposal said a gating hook's `.catch`
   returns `deny`. The 2.1.293 `.d.ts` shows the handler gets a replay-safe `next`: once
   `next.called` it resolves to what the guarantees beneath already decided, and when not it
   runs them once. Since a mod here holds no refusal of its own, `.catch(($, e, next) =>
   next(e))` keeps every guarantee and removes con 3 of option 1 — a bug in the mod can no
   longer refuse a `Bash` call the guards admitted. The data key is `gating_events`, not
   `mirrored_events`, and it covers the `classic.*` events that block too:
   `claude plugin validate` flagged `classic.UserPromptSubmit` as a gating hook without
   `.catch`.
2. **The dialog recognizes a guard refusal by `mods.deny_markers`.** A Bash `deny` also comes
   from a refused permission prompt, and asking there would repeat a question the person just
   answered. The markers are fragments of `guard`'s own messages; `guard status` hands them to
   the mod, and `schema` fails when one no longer appears in `ArchHook.java`.
3. **The CI job is its own workflow, `mods.yml`, not a job in `templates.yml`.** A path filter
   is per workflow: in `templates.yml` every mod edit would also run the three Maven jobs.

Loading was verified against the settings reference before writing: a repository's
`extraKnownMarketplaces` accepts a `directory` source with a relative path, resolved against
the main checkout, applied after workspace trust (<https://code.claude.com/docs/en/plugins/org>
§ Require plugins per repository).

## Propagation

| File | Change |
|---|---|
| `.claude/skills/claude-code-architect-designer/SKILL.md` | Form 9 row and prose, axes 18-20, Phase 2/3.5/4/5 rules for Form 9, propagation row, Out of scope (second hook file carve-out, mod as guarantee, `plugin-authoring`), Contract writes `.claude/mods/**`; `allowed-tools` + `Bash(claude plugin test:*)` |
| `.claude/skills/claude-code-architect-designer/references/mod-events.md` | New — layout, events, render sites, calls, tests, pitfalls |
| `…/references/decision-matrix.md` | § 1 interface line, § 2 Form 9 row, § 2.3, anti-patterns 22-23, rubric criterion 9, § 9 record rules |
| `…/references/hook-events.md` | Plugin row points to Form 9 |
| `…/references/ci-coverage.md` | `mods` pipeline row, Form 9 row |
| `…/templates/mod.{marketplace,plugin,hooks}.json.example`, `mod.register.ts.example`, `mod.test.ts.example` | New — proven by copying them into a scratch mod: validate passes, the test passes |
| `.claude/mods/.claude-plugin/marketplace.json`, `.claude/mods/nerviz-cockpit/**` | New — the mod, its manifest, hooks module, tests, engine-written `tsconfig.json` |
| `.claude/settings.json` | `extraKnownMarketplaces.nerviz-mods`, `enabledPlugins` `nerviz-cockpit@nerviz-mods`, `PostToolUse` `Edit(.claude/mods/**)` → `schema`, `_comment` |
| `.claude/schemas/extensions.json` | `mods` block; `settings.allowed` + `extraKnownMarketplaces` |
| `.claude/hooks/ArchHook.java` + `.jar` | `guard status`; `schema` → `checkMods`; `doctor` → `Mods` line |
| `CLAUDE.md` | Diagram, Dependencies, `guard status` command row, routing row for mods, "nine" |
| `docs/{pt-br,en}/06-claude-code-architect-designer.md` | Nine forms, Form 9 prose, axes 18-20, decision table row, Out of scope |
| `docs/{pt-br,en}/07-ci-validate.md` | Two `hooks-cross-platform` steps, `mods.yml` section, local commands |
| `docs/{pt-br,en}/11-pitfalls.md` | Part 2 § Mods |
| `docs/pt-br/00-visao-geral.md`, `docs/en/00-overview.md`, `README.md` | "nine forms" |

Goes to the generated project: **no** — axis 8 = meta-repo only. `export` copies by list and
lists no mod; `.claude/settings.json` of this repo is never exported (the project receives
`project-bootstrap/templates/settings.json.example`).

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · hooks-cross-platform › schema blocks a mod that would load half-way` (`ModsSchemaTest`) | Shipped mods pass; unknown event, gating hook without `.catch`, `$.http.fetch`, `$.process.run` of `sh`, unlisted mod, disabled mod, no tests, stale deny marker each exit 2 by name; a tree without `.claude/mods/` is silent | Green on the tree (11/11) · red with the `.catch` check disabled in `checkModSource`: "gating hook without .catch blocked — expected exit 2 … got 0" |
| `validate · hooks-cross-platform › guard status prints the open phase the cockpit draws` (`GuardStatusTest`) | Empty phase without one, `meta` + `.claude/**` after `/claude-code-architect-designer`, a same-class `Skill` call keeps it, markers equal `mods.deny_markers`, the next prompt empties it | Green (9/9) · red with the `phase` key renamed: three cases fail naming `"phase":[]` |
| `mods · mods › claude plugin validate` | The marketplace and the mod read as the engine reads them; no `gating hook without .catch` line | Green on 2.1.293 (`hooks:` and `calls:` lines as designed) |
| `mods · mods › claude plugin test` (`tests/cockpit.test.ts`) | Band, empty band, spinner, guard dialog held, non-guard refusal asks nothing, `/nerviz-doctor` as text where nothing draws | Green (6/6) on 2.1.293 · red with `isGuardRefusal` returning `true`: "a refusal the guard did not write asks nothing" fails |
