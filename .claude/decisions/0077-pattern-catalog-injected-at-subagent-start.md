# 0077 · The pattern catalog is injected at `SubagentStart` into each agent that declares it, in the generated project only

- **Date:** 2026-09-29
- **Scenario:** lessons-learned-015 topic K — "sempre oferecer ao agent de escrita de codigo o catalogo de design patterns GoF para todo codigo java nascer nas melhores boas praticas possiveis"
- **Decision:** option 1 — `SubagentStart` registered only in the template, mode `context subagent`, `pattern_catalog` per agent
- **State:** approved by Lucas Fernandes, 2026-09-29
- **Goes to the generated project:** yes — registration only in `project-bootstrap/templates/settings.json.example`; `ArchHook.java`/`.jar`/`extensions.json` travel whole via `export`

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | `java-spring-boot-developer` declares `skills: java-patterns`, and the catalog never reached it: the docs say a skill with `disable-model-invocation: true` cannot be preloaded. The agent body calls the catalog "preloaded in full" and applies it from memory it does not have | "keep as is" — the current config is dead |
| 7 — Mandatoriness | Must be in context **every** time a Java-writing agent runs; not the model's choice | 1 · 2 · 3 · 5 (all depend on the model deciding to read) |
| 8 — Destination | Generated project only, never the meta-repo | a registration in `.claude/settings.json` |
| 14 — Event | A subagent starting, before its first turn | `PreToolUse` (too late and too often), `SessionStart` (reaches the main thread, not the agent) |
| 15 — Reaction | Add context, never block | exit 2, Form 8 |
| 16 — Existing mode | No mode reads `SubagentStart` today | 7a alone — needs 7c |
| Behavior | No first version of badly shaped code written, discovered, and only then fixed | the agent's reactive rule "apply on the second occurrence" |
| Scope | Today `java-spring-boot-developer`; any future agent that writes Java in the generated project, without editing the hook | a `matcher` naming agents literally |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `SubagentStart` hook (template only) + mode `context subagent`; each agent declares `pattern_catalog` (true or false) in `agent_classes` (class default + override), mirrored by a body line; `schema` fails a `src/**` writer with no declaration | 8 | **approved** |
| 2 | Remove `disable-model-invocation` from `java-patterns` so `skills:` preload works | 4 | rejected — puts the description in every session of the meta-repo and the generated project (Form 1 by accident, which the skill's own body forbids), and covers only agents whose author remembers `skills:` |
| 3 | Agent body orders `Read` of the catalog as its first step | 3 | rejected — persuasion, not guarantee (invariant 6); every future agent must copy the order |
| 5 | `SubagentStart` in the agent's own frontmatter `hooks:` (7b) — the agent file carries its own registration | 3 | rejected — sub-agents docs list frontmatter hooks as firing while the agent runs and convert only `Stop` → `SubagentStop`; a `SubagentStart` there is not documented to fire, and a registration that may never run is dead config. Also lives in the meta-repo agent file, against axis 8 |
| 6 | The agent decides at runtime whether to `Read` the catalog | 2 | rejected — same failure as option 3: the choice is the model's, not a guarantee |
| 4 | Create nothing, only fix the prose | 1 | rejected — the observed gap stays: the agent believes it has a catalog it never received |

### Option 1 (score 8)

**Motivator:** axes 7 + 14 — mandatory, and `SubagentStart` is the one event whose `additionalContext` lands in the subagent's own conversation before its first turn.

**Mechanism.**

1. **Registration** — only in `project-bootstrap/templates/settings.json.example`: `SubagentStart`, **no `matcher`**, exec `java -jar .claude/hooks/ArchHook.jar context subagent`. No matcher on purpose: a literal agent-name regex is exactly what a future agent would miss. Cost: one JVM per subagent spawn — rare, not per tool call.
2. **Each agent declares whether it receives the catalog — no implicit "always".** Revised after review: `archunit-installer` and `commons-logging-installer` write Java under `src/` too, but copy fixed templates instead of designing code, so "every Java writer" was not literally true. The flag is `pattern_catalog: true|false` in `agent_classes`, class default plus `overrides.<agent>` (the same place `write_allow` already lives), and the agent body carries a `**Pattern catalog:** injected` or `**Pattern catalog:** not injected — <reason>` line that `schema` cross-checks against the data in both directions, the precedent being `**Executor:** yes`. Defaults: `executor` true; `installer` false, with a `$comment`; `driver` false (`project-initializer` writes through `project-bootstrap`'s templates). The mode reads `agent_type` from stdin, resolves the flag, and emits nothing when it is false.
3. **Guarantee against drift** — `schema` fails by name on any agent whose effective `write_allow` admits `src/**` and whose effective `pattern_catalog` is absent. A future Java-writing agent cannot be added without answering the question, and the answer is written where the agent reads it.
4. **Content, single owner, under the cap** — `additionalContext` is capped at 10 000 characters; above it the text is replaced by a file path and a 2 000-char preview the model is not asked to read. So the mode does **not** inject files whole. `subagent_context` lists `{file, sections}`; the mode extracts those `##` sections from `java-patterns/SKILL.md` (§ Catalog, § Forbidden in this repository, plus the entry rule) and appends one line pointing at `references/pattern-catalog.md` and `templates/` for the full GoF triage and exemplars. `schema` renders it and fails if it exceeds `subagent_context.max_chars` (9 000, margin under the cap). No second copy of the catalog exists anywhere.
5. **Meta-repo** — the mode exists here (the Java file travels whole) and is registered nowhere here. `java-spring-boot-developer` is never spawned in this repo.

**Entry rule changed from reactive to design-time** — this is where the recommendation disagrees with "always apply patterns". Pattern-by-default is overengineering (a Strategy over one `if`, an interface with one implementation), and `@.claude/rules/code-quality.md` would then fight the catalog. What the user asked — no badly shaped first version — is met by moving the trigger **earlier**, not by dropping it: a pattern is chosen when the **approved spec already names the force** (enumerated variants/rules, a cross-cutting concern over several implementations, construction with invariants, a predicate used by service and query) **or** the code on disk shows the symptom. The "When not" column stays binding. So `java-patterns`' entry rule becomes "observed symptom in the code, or a force named in the approved spec", and the agent's § Design patterns checks the spec before Block 1, not at the "second occurrence".

**Pros:** guaranteed, generated-project-only, data-driven for future agents, one owner, cap enforced by `schema`, cannot block anything.

**Cons:** Java change → jar rebuild (0075) and a new mode; a section heading renamed in `SKILL.md` would empty the injection — `schema` fails on a missing section, so it is loud. One JVM per subagent in the generated project, including `Explore`/`Plan` spawns that get nothing.

**Points cut in the rubric:** cost (new mode + rebuild); cost at runtime (JVM per spawn, unfiltered).

## References

| Claim | Source |
|---|---|
| A DMI skill cannot be preloaded via `skills:` | https://code.claude.com/docs/en/sub-agents — "You can't preload skills that set `disable-model-invocation: true`" |
| `SubagentStart` input has `agent_type`; `matcher` filters on agent type; cannot block | https://code.claude.com/docs/en/hooks § SubagentStart |
| `SubagentStart` `hookSpecificOutput.additionalContext` reaches the subagent before its first turn | same page, § Decision control |
| `additionalContext` capped at 10 000 chars; over it, path + 2 000-char preview, not read | same page, output limits |
| hook registered only in the template exists only in generated projects | `@CLAUDE.md` invariant 9 |
| lists and thresholds live in `extensions.json` | `@CLAUDE.md` invariant 10 |
| every `ArchHook.java` change rebuilds the jar | `.claude/decisions/0075-precompiled-hook-jar.md` |

## Propagation

| File | Change |
|---|---|
| `.claude/hooks/ArchHook.java` + `.jar` | mode `context subagent`; `schema` checks (an agent whose `write_allow` reaches `src/**` declares `pattern_catalog`; body marker matches the flag both ways; sections exist; rendered size ≤ `max_chars`). Jar rebuilt |
| `.claude/schemas/extensions.json` | `pattern_catalog` on each class (`executor` true, `installer`/`driver` false with `$comment`); `agent_classes.pattern_catalog_marker`; top-level `subagent_context` block: `file`, `header`, `sections`, `pointer`, `max_chars` |
| 4 agent bodies (`java-spring-boot-developer`, both installers, `project-initializer`) | `**Pattern catalog:**` line, cross-checked by `schema` |
| `project-bootstrap/templates/settings.json.example` | `SubagentStart` entry, exec form, no matcher |
| `.claude/agents/java-spring-boot-developer.md` | drop `skills: java-patterns`; line ~74 and § Design patterns rewritten: catalog arrives via hook, applied at design time from the spec's forces; D25 superseded |
| `.claude/skills/java-patterns/SKILL.md` | entry rule: code symptom **or** force named in the approved spec; Contract's "Two invocation modes" describes the injected mode, superseding 0025 |
| `CLAUDE.md` | routing row for design patterns: `/java-patterns` manual; Java-writing agents receive the catalog by hook. Commands row for the mode |
| `designer references/hook-events.md` | `SubagentStart`: `additionalContext` reaches the subagent; 10 000-char cap |
| `designer references/frontmatter-fields.md` | `skills` row: a DMI skill cannot be preloaded |
| `docs/11-pitfalls.md` | § Skills: DMI skill in `skills:` silently dropped; `context` added to the stdin-reading modes |

Goes to the generated project: **yes** — the registration only there; `ArchHook.java`/`.jar`/`extensions.json` travel whole via `export`.

Written by the main thread, not delegated — Form 7 propagation.
