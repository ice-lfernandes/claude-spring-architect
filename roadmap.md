# Roadmap

Status of `claude-spring-architect` by delivery phase, followed by a checklist of every major
feature and pattern the meta-repo claims to have. This file records the current state;
it is not a norm and is not loaded by any skill or hook.

## Phases

| Phase | Deliverable | Status |
|---|---|---|
| 1 | Skeleton: `CLAUDE.md`, `rules/`, `settings.json`, hooks | ✅ |
| 2 | `project-bootstrap`, `hexagonal` blueprint, agent, skills, exemplars | ✅ run end-to-end, green build |
| 3 | Remaining blueprints, all satisfying `_schema.md` | ✅ `hexagonal`, `clean-architecture-multi-module`, `clean-architecture-single-module`, `layered`, `modular-monolith`, `onion`, `vertical-slice` done |
| 4 | Feature skills (`use-case-design` → `domain-modeling` → `persistence-architect` → `rest-api-architect` → `test-architect`) | ✅ all 5 partials have an owner |
| 5 | `new-feature` orchestrator + `java-spring-boot-developer` executor | ✅ end-to-end run against `demo-app`, gaps remediated in D24 |
| 6 | Publication: README, LICENSE, cross-platform CI | ✅ only missing examples with real output |
| 7 | Post-bootstrap extension skills (`docker-architect`, `messaging-architect`, `git-publish`) and meta-tooling (`claude-code-architect-designer`, now also deciding MCP server placement) | ✅ done; more triggered by real symptoms as they show up |

Deliberate order: rules come **before** skills. Writing skills first leads to rules
copied inside them — exactly the duplication this design exists to avoid.

## Feature checklist

### Architecture blueprints (`.claude/blueprints/`)

- [x] `hexagonal`
- [x] `clean-architecture-multi-module`
- [x] `clean-architecture-single-module`
- [x] `layered`
- [x] `modular-monolith`
- [x] `onion`
- [x] `vertical-slice`
- [x] `custom-template` (contract to write a new one)
- [x] `_schema.md` (contract every blueprint fulfills)

### Norms (`.claude/rules/`)

- [x] `architecture-ddd.md`
- [x] `naming.md`
- [x] `code-quality.md`
- [x] `error-handling.md`
- [x] `api-rest.md`
- [x] `lombok.md`
- [x] `value-objects.md`
- [x] `persistence.md`
- [x] `testing.md`
- [x] `observability.md`
- [x] `logging.md`
- [x] `messaging.md`
- [ ] `security.md` (planned — authn/authz, secrets, sensitive data, PII)
- [ ] `git-workflow.md` (planned — branches, commit messages, PRs)

### Feature-design pipeline (`/new-feature`)

- [x] `use-case-design`
- [x] `domain-modeling`
- [x] `persistence-architect`
- [x] `rest-api-architect`
- [x] `test-architect`
- [x] `new-feature` orchestrator + consolidation
- [x] `messaging-architect` (optional step, fires when an event needs external delivery)
- [x] `docker-architect` (chained on demand by persistence/test/messaging)
- [x] `java-spring-boot-developer` (executor agent)
- [x] `git-publish` (offers git init/commit + `gh` create/push, two confirmation
      gates — chained by `project-initializer` after a green bootstrap build and by
      `/new-feature` after every future executor run; see D34)

### Meta-tooling (stays in this repo, not copied into generated projects)

- [x] `project-bootstrap`
- [x] `init-project`
- [x] `claude-code-architect-designer` (6 forms, including shared/per-agent MCP server
      — Form 6a/6b, decision matrix § 2.1's CLI-first check; see D33)
- [x] `project-initializer` (agent driving `/init-project`; `Skill` tool added to chain
      into `git-publish` after a green build)

### Copied into every generated project

- [x] `arch-doctor`
- [x] `java-patterns` (preloaded catalog, applied by the executor, never invoked as a turn)
- [x] `git-publish` (see Feature-design pipeline above)
- [x] `archunit-installer` (agent, `test-architect`'s setup mode)
- [x] `ArchHook.java` + `schemas/extensions.json`

### Enforcement

- [x] Hook-based forbidden-import checks derived from the blueprint
- [x] Frontmatter schema validation (`ArchHook.java schema`)
- [x] Cross-platform hook as a single Java file, exec form (no `.sh`/`.ps1` twins)
- [x] CI (`validate.yml`) — OS matrix (`ubuntu-latest`, `macos-latest`, `windows-latest`)
- [x] CI — invariant 1 (rules is a leaf)
- [x] CI — invariant 2 (single owner per norm, fixed phrase list)
- [x] CI — invariant 3 (no code boilerplate inside `rules/`)
- [x] CI — invariant 4 (no new `commands/`)
- [x] CI — invariant 7 (new blueprint doesn't touch prompts)
- [x] CI — invariant 8 (no hardcoded Spring/Java version)
- [x] CI — invariant 10 (recognized frontmatter fields have a single owner)
- [x] CI — exemplar imports resolve against a real `start.spring.io` request
- [ ] CI — invariant 9 (generated project is self-contained) — only checked manually, not automated
- [ ] `claude plugin validate` in CI — not installed on the GitHub Actions runner, run by hand before a PR

### Documentation

- [x] README (EN)
- [x] `docs/` PT-BR (`00-visao-geral`, `01-tipos-de-arquivo`, `02-init-project`, `03-new-feature`, `04-arch-doctor`, `05-blueprints`, `06-claude-code-architect-designer`, `07-ci-validate`)
- [x] `docs/en/` mirror of every PT-BR doc
- [x] `blueprints/README.md` + `blueprints/README.pt-br.md` (architecture overview)
- [x] LICENSE
- [ ] Worked examples with real output (still a gap noted in phase 6)

## Future ideas

- [ ] `/refactoring` — new orchestrating, invocable skill
- [ ] `/solution-design-architect` — new orchestrating, invocable skill

Done: GitHub remote-repo creation in `project-initializer`, shipped as the `git-publish`
skill (Form 1, decision matrix § 2.1 CLI-first check — `gh repo create` + `git push`,
no MCP server) — see D34 above and `@.claude/decisions/0034-git-publish-skill.md`.
