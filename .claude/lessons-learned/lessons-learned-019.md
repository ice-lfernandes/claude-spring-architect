# Lessons learned 019 — a generated project serves only Claude Code users; AGENTS.md as the first step to portability

Date: 2026-10-02. Scope: a landscape review of similar projects, made while repositioning
`README.md` and `docs/{en,pt-br}/09-*.md` as a "stack-specialized agent harness". This is
not a run that broke. It is a gap found by comparison: a project generated here is useful
only to the developers on the team who run Claude Code. This file records the gap, why it
matters, and the constraints any fix must respect. It decides nothing. The design belongs
to `/claude-code-architect-designer`.

---

## Observation

On 2026-10-02 we mapped the neighbors of this repository (sources and star counts in
`docs/en/09-differentiators.md`). One pattern holds across nearly all of them: **they run
on more than one coding agent, and this repository runs on one.**

| Project | Agents it targets |
|---|---|
| `loiane/specs-driven-development-spring-angular` (the closest Spring neighbor) | Claude Code, GitHub Copilot, Windsurf |
| `jabrena/plinth` | Cursor, Claude Code, Codex, Copilot |
| `a-pavithraa/springboot-skills-marketplace` | Claude Code and Codex. Its README installs the same `SKILL.md` files into `~/.claude/skills/` and `~/.codex/skills/` |
| GitHub Spec Kit | 30+ agents |
| `maxritter/pilot-shell` | Claude Code and Codex |
| **claude-spring-architect** | **Claude Code only** |

The new `README.md` § "Limits, said plainly" and `09-differentiators.md` § "What is not a
differentiator" now state this openly. Stating a limit does not remove it.

## Why it matters

1. **Teams are mixed.** A Spring team adopting a generated project usually has some
   developers on Claude Code, some on Copilot (often the corporate default), and some on
   Cursor or IntelliJ. For everyone not on Claude Code, today the project's `.claude/` is
   an inert folder. Its architecture, norms, and forbidden imports never reach their
   agent, and that agent writes code with no guidance at all.
2. **The guides are the cheap part to share.** The harness has two halves (the vocabulary
   is in `09-differentiators.md` § "What category this is"):
   - **Guides** steer the model before it acts: the root `CLAUDE.md`, per-module
     `CLAUDE.md`, `rules/`, the blueprint's `forbidden_imports`, and the approved specs
     under `docs/use-cases/`. They are mostly plain Markdown, and most of their content
     does not depend on the runtime.
   - **Sensors** check what it did: `ArchHook` modes registered in `settings.json`, the
     write-territory `guard`, the frozen spec folder, the audit trail. These are wired to
     Claude Code's hook events and do not port.

   Some of the sensors already work for everyone, because they live in the build rather
   than the agent: ArchUnit, the JaCoCo gate, Checkstyle, `lombok.config`, and the module
   graph in the POMs. A Copilot user's code is still refused by `./mvnw verify`. What a
   Copilot user lacks is the guidance that would have made the code right the first time.
3. **The industry converged on a format.** `AGENTS.md` (agents.md) is a plain-Markdown
   context file at the repository root, read automatically by most coding agents (Codex,
   Cursor, Copilot, Gemini CLI, Jules, Aider and others, per the agents.md site). It is
   stewarded by the Linux Foundation. Its scope is the same as a root `CLAUDE.md`: build and
   test commands, conventions, architecture, and what not to do. There is no schema.
   Nested `AGENTS.md` files in subdirectories apply to that subtree, which mirrors this
   repository's per-module `CLAUDE.md`.
4. **Skills are converging too.** The same `SKILL.md` shape now loads in more than one
   agent. a-pavithraa's README installs one skill set into both Claude Code and Codex, and
   the landscape review found other tools adopting "skills". The skill bodies here are
   the most Claude-specific part (`context: fork`, `disable-model-invocation`, `Skill` and
   `Agent` tool calls, `!` injections), so this is a later and harder step, not the first.

## Mapping of harness pieces to other ecosystems

These are the equivalents as understood on 2026-10-02. Every cell outside the Claude Code
column must be **re-verified against each vendor's documentation at design time**, because
these features change monthly.

| Harness piece | Claude Code (today) | AGENTS.md | GitHub Copilot | Cursor |
|---|---|---|---|---|
| Always-on project context | root `CLAUDE.md` | root `AGENTS.md` | `.github/copilot-instructions.md`; also reads `AGENTS.md` | `.cursor/rules` with always-apply; also reads `AGENTS.md` |
| Per-module context | `<module>/CLAUDE.md` | nested `AGENTS.md` | nested `AGENTS.md` (to verify) | nested `AGENTS.md` (to verify) |
| Norms scoped by path | `rules/*.md` with `paths` | none: one file per directory, no globs | `.github/instructions/*.instructions.md` with `applyTo` globs | `.cursor/rules/*.mdc` with `globs` |
| Procedures | `skills/*/SKILL.md` | none | prompt files, custom agents; skills support to verify | commands; skills support to verify |
| Executor with restricted tools | `agents/*.md` | none | custom agents (`.github/agents/`) | to verify |
| Write-time sensors | `ArchHook` via `settings.json` | none | to verify | `hooks.json`: `beforeShellExecution` can deny; `afterFileEdit` only observes (per the review; to verify) |
| Build-time sensors | ArchUnit, JaCoCo, Checkstyle, POM graph | same: the build runs for everyone | same | same |

Reading the table: `AGENTS.md` covers the first two rows with one file per directory and
nothing to install. Copilot and Cursor each also have a path-scoped norm mechanism close
to `rules/` with `paths`, which would take more work. Nobody else has an equivalent of the
write-territory `guard`, so that part stays Claude Code only.

## Constraints any fix must respect

These come from `CLAUDE.md` § Invariants. They are why "just write an AGENTS.md" is not the
whole answer.

- **Invariant 2 (one owner per norm).** An `AGENTS.md` that restates the norms is a second
  copy, and it will drift. It can **cite** `rules/*.md` by path, and every agent that reads
  `AGENTS.md` can open a cited file. Copying content into a vendor format
  (`.instructions.md`, `.mdc`) is a derivation, and needs the same mechanism that already
  derives `paths` from `packages.map` (`export.derived_paths`). It must not be a hand-kept
  second file.
- **Invariant 7 (architectures are data).** The blueprint-specific content (module list,
  `forbidden_imports`, package map) must come from the blueprint at generation time, as
  the module `CLAUDE.md` already does. It must not be written into a template for one
  architecture.
- **Invariant 9 (self-contained, and the export manifest is data).** A new file in the
  generated project is an entry in the `export` block of
  `.claude/schemas/extensions.json`, or a template that `project-bootstrap` renders, and
  `arch-adopt` has to deliver it on update too. A project adopted before the change needs a
  `migrations` entry if the file changes how the project is read.
- **One source for the root context.** The generated root `CLAUDE.md`
  (`project-bootstrap/templates/root.CLAUDE.md.example`) and a generated `AGENTS.md` would
  carry nearly the same facts. Two candidate shapes, to be weighed at design time:
  - `AGENTS.md` holds the portable facts, and `CLAUDE.md` imports it (`@AGENTS.md`) and adds
    only the Claude Code routing (skills, hooks, `/new-feature`);
  - `CLAUDE.md` stays the owner, and `AGENTS.md` is a short file that points to it and to
    `rules/`.

  Verify at design time whether Claude Code reads `AGENTS.md` natively. If it does, the
  first shape may need no import at all.
- **Honesty about what does not port.** The generated `AGENTS.md` must say that the
  write-territory, frozen-spec, and audit guarantees hold only under Claude Code. A Copilot
  user who reads "approved specs are frozen" and then edits one has been misled.
- **Cheapest mechanism first.** The first step is a generated file and nothing more: no
  vendor-specific rule derivation and no cross-agent hooks until a real team on Copilot or
  Cursor reports the gap the file did not close.

## Proposed next step

Run `/claude-code-architect-designer` with this file as input, scoped to **one** change:
generate an `AGENTS.md` (root, plus one per module where the blueprint emits a module
`CLAUDE.md`) during `/init-project`, and deliver it through `arch-adopt`. Questions the
design must answer:

1. Which file owns the portable facts, `AGENTS.md` or `CLAUDE.md` (see the two shapes
   above)?
2. Does the module-level `forbidden_imports` text get rendered into both files from the
   blueprint, or does one file cite the other?
3. Which `export` entry, which `migrations` entry, and which CI check prove the file
   exists, matches the blueprint, and cites only paths that exist?
4. Does Claude Code read `AGENTS.md` natively today, and does that change the answer to 1?

Out of scope until there is evidence from a real mixed team:
- deriving `.github/instructions/*.instructions.md` or `.cursor/rules/*.mdc` from `rules/`;
- porting skills to other runtimes;
- any sensor beyond the build.

## Sources

- Landscape review and star counts: `docs/en/09-differentiators.md` (2026-10-02).
- AGENTS.md: https://agents.md and https://github.com/agentsmd/agents.md
- Multi-agent installation of the same skills: README of
  https://github.com/a-pavithraa/springboot-skills-marketplace
- Multi-agent SDD toolkits: https://github.com/loiane/specs-driven-development-spring-angular,
  https://github.com/jabrena/plinth, https://github.com/github/spec-kit
