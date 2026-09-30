# 0088 · `java-patterns` is renamed `gof-design-patterns`; adopted projects drop the old copy

- **Date:** 2026-09-30
- **Scenario:** "altere o nome da skill: java-patterns para gof-design-patterns, fica mais
  claro sobre o que se trata. Ajuste docs, referencia, readme e outros lugares"
- **Decision:** rename in place — `.claude/skills/gof-design-patterns/` (Form 2 unchanged:
  `disable-model-invocation: true`, class `build`, same `write_allow`), every live reference
  rewritten, and the old skill's files listed in `export.retired`.
- **State:** approved by Lucas Fernandes, on 2026-09-30 — the request itself named the new
  name and the scope

## Why a record for a rename

No form changed, so the usual criteria for a record barely apply. One does: the skill travels
to the generated project (axis 8 = "both"), and a rename there is not free. `export` only
ever wrote until 0082, so without `retired` an adopted project updated through `arch-adopt`
would keep `java-patterns/` next to `gof-design-patterns/` — two `/` commands, two copies of
the catalog, one of them no longer maintained. The record is also the pointer from the name
the history uses to the name the tree uses: 0025, 0026, 0027, 0058, 0077 and several
lessons-learned say `java-patterns`, and are not rewritten (`@.claude/decisions/README.md`).

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | Rename + list each old file in `export.retired` | 8 | **Approved** |
| 2 | Rename + teach `export` to retire a directory | 5 | Rejected — a Java change, a jar rebuild and a guard on recursive deletion for one rename; 20 lines of data do the same |
| 3 | Rename without `retired` | 3 | Rejected — adopted projects keep both skills |

## What was not renamed, and why

| Place | Reason |
|---|---|
| `.claude/decisions/**`, `.claude/lessons-learned/**` | History: records what was true on its date |
| The path `@.claude/decisions/0025-java-patterns-preloaded-in-executor.md` in `new-feature/SKILL.md` | A filename; renaming it would break the citation |
| `ArchHook.java` / `ArchHook.jar` | Hold no skill name — the catalog's path is data in `subagent_context.file` (invariant 10), so no rebuild |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/java-patterns/` → `.claude/skills/gof-design-patterns/` | `git mv`; `name:` and the `/` command in the body |
| `.claude/schemas/extensions.json` | `skill_classes.build.skills` and its override, `subagent_context.header`/`file`/`pointer`, `export.skills.include`, 20 entries in `export.retired`, `$comment_retired` notes files-not-directories |
| `.claude/agents/java-spring-boot-developer.md` | Every mention; D25 keeps the old name in parentheses |
| `.claude/skills/{domain-modeling,git-publish,new-feature,rest-api-architect,use-case-design}/SKILL.md` | Collision and routing mentions |
| `.claude/skills/project-bootstrap/templates/{root.CLAUDE.md.example,settings.json.example}` | Routing row, hook `_comment` |
| `.claude/skills/claude-code-architect-designer/{references/decision-matrix.md,templates/SKILL.md.example}` | Examples |
| `.claude/.ci/SubagentContextTest.java` | Header comment |
| `CLAUDE.md`, `README.md`, `claude-help.md`, `roadmap.md`, `docs/{en,pt-br}/**` | Every mention; `11-pitfalls.md` keeps the old name in parentheses |

Goes to the generated project: **yes** — the renamed skill via `export.skills.include`, and
the old one removed from an adopted project via `export.retired`.
