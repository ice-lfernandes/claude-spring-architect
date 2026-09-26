# Differentiators — what this repository does that its neighbors don't

Primary source: the `.claude/` itself (`hooks/ArchHook.java`, `blueprints/*.yaml`,
`skills/*/SKILL.md`, `schemas/extensions.json`), `.github/workflows/validate.yml`, and
`CLAUDE.md` § Invariants. The comparison with other projects was made on 2026-09-24
from their public READMEs; links at the end.

## How the neighboring projects are organized

There are dozens of "Claude Code + Spring Boot" repositories on GitHub. Almost all of
them fall into one of three families:

| Family | What it ships | Examples |
|---|---|---|
| **Static template** | A repository with a `pom.xml`, a pinned Spring version, and a `.claude/` with skills and agents. You clone it and adapt. The architecture is whatever came with the template | `piomin/claude-ai-spring-boot` and its forks; `ryu-qqq/claude-spring-standards` (fixed hexagonal, 15 skills, 12 commands, 5 hooks) |
| **Skills pack** | Knowledge `SKILL.md` files (Spring, JPA, Security, WebFlux) to copy into `.claude/skills/`. They generate no project and verify nothing | `rrezartprebreza/spring-boot-skills`, `a-pavithraa/springboot-skills-marketplace` |
| **Agents + hooks bundle** | Agents per role (backend, reviewer, security, devops…) plus generic hooks: format on save, block `rm -rf` | `altmemy/claude-code-templates/claude-spring-boot` (7 agents) |

The common trait: **the architecture is prose**. It lives in a `CLAUDE.md` or a skill,
and what makes the model respect it is the model remembering. Where a hook exists, it
formats or blocks a dangerous command — it never reads the declared architecture.
Versions are pinned in the template's `pom.xml`. None of them generates a project; none
measures what each skill cost.

## The differentiators, largest first

### 1 · A generator, not a template

`/init-project` clones nothing. It calls the Spring Initializr at run time
(`curl start.spring.io/starter.tgz`), restructures the result according to the chosen
blueprint, and copies into the project everything the project will cite: norms,
development skills, executor agents, the hook, and the schema. Consequences:

- **Versions never come from memory** (`CLAUDE.md` invariant 8). A public template with
  `spring-boot 3.4.1` pinned is obsolete in weeks; here CI fails if someone writes a
  version as a fact.
- **The generated project is self-contained** (invariant 9). Whoever clones
  `pedidos-api` doesn't have this repository and doesn't need it: `/new-feature`,
  `/arch-doctor`, `/audit-usage`, and every norm are already inside. `README.md`,
  `README.pt-br.md`, and a `GENESIS.md` recording the generation itself travel along.
- **Zero business code** (D21). No `ExampleController` to delete: the first feature is
  born from a real spec.

### 2 · Architecture is data, and CI proves it

Seven architectures out of the box — `hexagonal`, `clean-architecture-multi-module`,
`clean-architecture-single-module`, `layered`, `onion`, `vertical-slice`,
`modular-monolith` — plus `custom-template` to describe your own. Each one is a YAML
with modules, `depends_on`, `forbidden_imports`, `packages.map`, `architecture_paths`,
features, and honest trade-offs (a blueprint with no `trade_offs` is invalid).

The test that proves the design: **adding an architecture touches no skill, agent, or
command**. The `new blueprint doesn't touch prompts` job in `validate.yml` checks that
on every push. In the neighboring projects, switching from hexagonal to onion means
rewriting the prompts.

See [05-blueprints.md](05-blueprints.md).

### 3 · One declaration, three effects

A module's `forbidden_imports` field in the blueprint feeds, with no manual copy:

1. the module's local `CLAUDE.md` — so the model **knows**;
2. `.claude/forbidden-imports.txt` — so the `check` hook **blocks** the write the moment
   it happens (`exit 2`, inside Claude Code);
3. `depends_on` in the POMs — so the **compiler** refuses the import, in `multi-module`.

Later, `test-architect` (setup mode) installs ArchUnit and the JaCoCo gate (80% lines /
70% branches) through the `archunit-installer` agent, which translates `packages.map`
into ArchUnit rules. Add Checkstyle in the `validate` phase and a `lombok.config` with
`flagUsage = ERROR` for `@Data`/`@Setter`. None of these layers depends on the model
remembering the rule.

### 4 · Enforcement in a single Java file, eight modes, no shell

`.claude/hooks/ArchHook.java` runs in single-file mode (`java ArchHook.java <mode>`),
invoked in exec form (`command: java`, `args: [...]`) — no shell, no `chmod`,
identical on Linux, macOS, and Windows. Zero Python, zero `.sh`/`.ps1` twins. The eight
modes:

| Mode | Event | Blocks? | What it does |
|---|---|---|---|
| `check` | `PostToolUse` Write\|Edit | yes | forbidden imports + incremental `test-compile` of the touched module |
| `format` | `PostToolUse` Write\|Edit | no | `spotless:apply` on the module |
| `tests` | `Stop` | yes | tests of the modules changed since `HEAD` |
| `schema` | `PreToolUse`/`PostToolUse`/`Stop` | yes | frontmatter of skills/agents/rules and `.mcp.json` against `extensions.json`, with a secret scan |
| `guard` | `UserPromptSubmit`, `PreToolUse` | yes | a design skill never writes under `src/`; an approved spec is immutable |
| `audit` | 10 lifecycle events | no | execution trail of every skill and agent |
| `compose` | manual, and inside `doctor` | no | every compose service is `running`, no foreign container on its ports |
| `doctor` | manual (`/arch-doctor`) | no | setup diagnosis |

CI runs `doctor`, a `BoundaryTest` that injects a forbidden import and an
`InjectionPathTest` that injects a cwd-relative injection — both require `exit 2` — plus
a `ComposeTagTest` that requires an `image:` tag disagreeing with the one `src/test` pins
to be reported, on `ubuntu-latest`, `macos-latest`, and `windows-latest`. "Cross-platform"
is a verified fact, not a claim.

See [01-file-types.md § Hook](01-file-types.md#hook).

### 5 · A deterministic execution trail, per run

No neighboring project answers "what did this feature cost, and what did it chain".
Here the hook's `audit` mode writes, at every `Stop`, one Markdown report per skill or
agent invocation in the generated project — opened by a `/command` or by the model's
own `Skill`/`Agent` call — with tokens and cost per piece (no double counting), a chain
tree with duration bars, files touched, permissions requested, tools that failed, rules
that should have loaded, and `HEAD` before and after. The initial prompt goes in
redacted (tokens, passwords, and private keys blanked by default in
`extensions.json`). Two ledgers (`history.jsonl`, `nodes.jsonl`) feed
`ArchHook.java audit summary`, and the `/audit-usage` skill renders the consolidated
view.

It's a hook, not a skill, because it has to survive the model forgetting, the session
dying, and Ctrl+C (D35, D38). Zero tokens are spent producing the report.

See [08-audit-usage.md](08-audit-usage.md).

### 6 · A spec-first pipeline, with boundaries that are hooks

`/new-feature` designs **one use case per run** across five partials with a single
owner each (`use-case-design` → `domain-modeling` → `rest-api-architect` →
`persistence-architect` → `test-architect`, plus a conditional `messaging-architect`),
consolidates them into a `UC-NNN-spec.md` with a `draft → approved → implemented`
lifecycle, asks for approval, and only then offers the `java-spring-boot-developer`
executor — a separate agent, with restricted tools, that only writes under `src/`.

Three boundaries stopped being prose after being violated in real runs:

- **Design writes only under `docs/`** — `guard` blocks `Write`/`Edit` under `src/**`
  while a design skill is open, except from inside an executor agent.
- **An approved spec is immutable** — `guard` freezes the `docs/use-cases/UC-*/` folder
  whose spec is `approved` or `implemented`.
- **Git only through `git-publish`**, behind two `AskUserQuestion` gates; `git push` is
  always `ask` and `git push --force` is `deny`.

See [03-new-feature.md](03-new-feature.md).

### 7 · The `.claude/` has an architecture of its own, and 11 invariants gated in CI

The same Clean Architecture applied to Java applies to the AI files: `hooks/` verifies
`agents/`, which invoke `skills/`, which cite `rules/` + `blueprints/` — leaves that
cite nobody. Eleven invariants (`CLAUDE.md`), and the `design` job in `validate.yml`
fails the build when one is violated. The ones no neighbor has:

- **`rules/` is a leaf** — no norm mentions a skill, agent, or command.
- **Each norm has one owner** — a known phrase in two files under `rules/` is a bug.
- **A norm carries no code** — boilerplate lives in `skills/*/templates/*.example`.
- **Frontmatter has a schema** — `extensions.json` owns the fields the runtime
  recognizes. The runtime ignores an invented field silently, and `claude plugin
  validate` lets it through; `ArchHook.java schema` does not.
- **Exemplars actually compile** — the `exemplar-imports` job downloads a real
  `starter.tgz` and resolves every `import` of every `.java.example` against the
  classpath.
- **No version written as a fact** outside the decision history.
- **No dependency outside JDK, git, and curl** — a `pip install`/`npm install` under
  `.claude/` fails CI.

See [07-ci-validate.md](07-ci-validate.md).

### 8 · A meta-tool that decides the shape of the next extension

`/claude-code-architect-designer` interviews, applies a decision matrix, and picks one
of six forms — auto-invocable skill, manual skill, subagent, rule, `CLAUDE.md` section,
shared or per-agent MCP server — or answers "create nothing" (a CLI already solves it,
or it's a hook/`permissions.deny` matter). An agent is only born for one of three
reasons (context, tools, model); anything that can't justify one becomes a skill.

Every piece in this repository was born from a symptom observed in a real run,
recorded under `lessons-learned/` and remediated by a numbered decision under
`decisions/`. Those two directories stay out of the public repository on purpose
(`.gitignore`): they are the maintainer's history, not a norm.

See [06-claude-code-architect-designer.md](06-claude-code-architect-designer.md).

### 9 · Cheap context by construction

- The root `CLAUDE.md` stays under 200 lines: invariants and routing, nothing else.
- Norms enter through `paths` when a file in their territory is touched. The globs of
  `api-rest.md`, `persistence.md`, `value-objects.md`, `observability.md`, and
  `messaging.md` are **rewritten at generation time** from the blueprint's
  `packages.map` — a glob copied verbatim would leave the norm unloaded, silently.
- `metadata:` was banned from frontmatter (it costs tokens on every invocation and
  enforces nothing); contracts live in the body, where they are actual instruction.
- Design knowledge travels preloaded into the executor (`java-patterns` via `skills:`),
  with no turn of its own.

### 10 · Cross-cutting concerns that arrive solved

None is unique on its own; together, no neighbor gathers them:

| Concern | Where it lives |
|---|---|
| `Idempotency-Key` from the first endpoint, via AOP and a shared table | `rest-api-architect` + `persistence-architect` (D40, D44) |
| Structured logging with sensitive-data masking (`@LogExecution`, `@MaskSensitiveData`) | `commons-logging-installer`, triggered by `/new-feature`'s pre-flight |
| Observability: OTLP collector with traces **and** metrics pipelines, Jaeger or Grafana + Tempo + Prometheus backend | `docker-architect` |
| A container that "started" but isn't answering, a port held by a sibling project | `ArchHook.java compose` |
| Kafka producer/consumer with at-least-once, retry, and DLQ | `messaging-architect` + `rules/messaging.md` |
| Pagination without `Pageable` crossing the application port | `rules/architecture-ddd.md` (D42) |
| Secrets: `permissions.deny` on `*.env`, `*.pem`, `application-prod.yml`; a scan of `headers`/`env` in `.mcp.json` | `settings.json` + `ArchHook.java schema` |

## Comparison table

| Capability | claude-spring-architect | Static template | Skills pack | Agents + hooks bundle |
|---|---|---|---|---|
| Generates the project via the Initializr, live versions | ✅ | ❌ (clone, pinned version) | ❌ | ❌ |
| Selectable architecture as data (7 + custom) | ✅ | ❌ (one, fixed) | ❌ | ❌ |
| Boundary derived from the blueprint and blocked by a hook | ✅ | ❌ | ❌ | ❌ (generic hooks) |
| Cross-platform hook with no shell, tested on 3 OSes in CI | ✅ | ❌ | — | ❌ (bash) |
| Execution trail per run, cost per skill/agent | ✅ | ❌ | ❌ | ❌ |
| Spec-first pipeline with hook-frozen specs | ✅ | ❌ | ❌ | partial (agents per role, no spec) |
| The `.claude/`'s own invariants verified in CI | ✅ | ❌ | ❌ | ❌ |
| Frontmatter schema that catches an invented field | ✅ | ❌ | ❌ | ❌ |
| Exemplars compiled against a real classpath in CI | ✅ | ❌ | ❌ | ❌ |
| Generated project self-contained, independent of the generator | ✅ | ✅ (it is the clone) | — | ✅ |
| Spring/JPA knowledge skills | ✅ (12 norms + 9 design skills) | ✅ | ✅ (sometimes broader) | ✅ |
| Codex/Cursor support beyond Claude Code | ❌ | partial | ✅ | partial |

## What is not a differentiator — said honestly

- **Pure Spring knowledge.** Skills packs like `spring-boot-skills` cover Security,
  WebFlux, and more versions. There is no `rules/security.md` here yet (planned in
  `00-index.md`).
- **Gradle.** The schema accepts `build.tool: gradle`, but only POM templates exist;
  the path exercised end to end is Maven.
- **Cost.** The full pipeline is expensive by design — one real `/new-feature` run cost
  ~USD 15 for a two-field aggregate. The audit trail exists precisely to measure that,
  and the cost discipline is in [03-new-feature.md § Cost discipline](03-new-feature.md).
- **Claude Code only.** Hooks, `paths`, `disable-model-invocation`, and `context: fork`
  belong to the Claude Code runtime; nothing here runs on Codex or Cursor.
- **Not affiliated with Anthropic.** "Claude" in the name follows the ecosystem's
  practice; it doesn't indicate an official product.

## Sources for the comparison

- [piomin/claude-ai-spring-boot](https://github.com/piomin/claude-ai-spring-boot)
- [ryu-qqq/claude-spring-standards](https://github.com/ryu-qqq/claude-spring-standards)
- [rrezartprebreza/spring-boot-skills](https://github.com/rrezartprebreza/spring-boot-skills)
- [a-pavithraa/springboot-skills-marketplace](https://github.com/a-pavithraa/springboot-skills-marketplace)
- [altmemy/claude-code-templates — claude-spring-boot](https://github.com/altmemy/claude-code-templates/tree/main/claude-spring-boot)
