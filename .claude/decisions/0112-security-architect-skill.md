# 0112 · A design skill owns servlet API security: mechanism, per-endpoint access, ownership, and the project-wide filter chain

- **Date:** 2026-10-02
- **Scenario:** "quero criar uma nova skill de conhecimento de codigo para aplicar quando pedido
  em uma new feature a parte de seguranca com apis rests (spring-security). Se durante entrevista
  de caso de uso for mencionado ou perguntado por seguranca da api, roles, apenas grupos de
  usuario ou proprio usuario pode executar determinado endpoint." Servlet only — WebFlux and
  reactive security belong to a future reactive skill.
- **Decision:** Option 1 — Form 1 `.claude/skills/security-architect/SKILL.md` (class `design`, partial
  `32-seguranca.md`) + Form 4 `.claude/rules/authorization.md`; `use-case-design` asks `Access` on every
  HTTP trigger; `/new-feature` runs it as step 3b, after REST and before messaging.
- **State:** approved by Lucas Fernandes, on 2026-10-02 — option 1, with both deviations accepted:
  the mechanism is inherited from code and earlier partials instead of the root `CLAUDE.md`, and
  hexagonal/onion/custom-template put the wiring in a `security` subpackage of the REST adapter

## What the context showed

| Fact | Where |
|---|---|
| No piece designs authentication or authorization; nothing names Spring Security | grep over `.claude/rules`, `.claude/skills`, `.claude/agents`, `.claude/blueprints` |
| The norm is already planned, unwritten: `authorization.md`, "Authentication and authorization at the entry boundary", paths derived from the entry-layer package | `.claude/rules/00-index.md` § Planned rules |
| `personal-data.md` excludes authn/authz on purpose and points at that planned file | `.claude/rules/personal-data.md` § Scope |
| `api-rest.md` owns the 401 / 403 / 404 choice already; it stays there | `.claude/rules/api-rest.md` § Errors — 400 family |
| No starter in the Initializr dependency list of `exemplar-imports`: a `.java.example` importing `org.springframework.security.*` fails that job today | `.github/workflows/validate.yml` › `exemplar-imports` |
| `.config` exists in 5 of 8 blueprints; hexagonal, onion and custom-template have none | `packages.map` of each blueprint |
| Actuator ships `show-details: when-authorized` with nothing that authorizes | `project-bootstrap/templates/features/actuator/application-actuator.yml.example` |

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Concrete anticipation: no run yet, the next generated projects need protected APIs | — (weighed in the score, decision matrix § 3) |
| 2 — trigger | Model decision inside `/new-feature` when the use case restricts access; standalone by description | Form 2 |
| 5 — nature | Multi-step: inherit, interview, decide mechanism and rules, write partial | Forms 4 and 5 as the whole answer |
| 6 — isolation | None — the interview is the task | Form 3 |
| 7 — mandatoriness | Persuasion; a hook only after an observed failure | Forms 7 and 8 |
| 8 — destination | Both — development skill, `export.skills.include` | — |
| 9 — integration | Own design skill and partial, after REST and before persistence; `use-case-design` always asks "who may execute" on an HTTP trigger | "Extend `rest-api-architect`" as the whole answer |
| — mechanisms | OAuth2 resource server JWT, opaque-token introspection, users in the application (`UserDetailsService` + `PasswordEncoder`), API key / client credentials | — |
| — ownership | Roles and groups at the adapter (`authorizeHttpRequests` / `@PreAuthorize`); "only the owner" is a business rule decided in the use case from an actor the command carries | Ownership as a `@PreAuthorize` bean expression |
| — mechanism scope | A project fact: decided by the first case that needs it, inherited afterwards; a second mechanism only by explicit decision (e.g. JWT for users + API key for machines) | Per-use-case mechanism |
| — norm | `rules/authorization.md`, out of the planned list | Norms only in the skill's `references/` |
| — global setup | The executor writes `SecurityConfig` and friends the first time, declared `NEW` in the partial | Installer agent; `project-bootstrap` feature |
| — tests | Test shape stays `test-architect`'s: it gains the template and reads the partial's cases | Security test templates in the new skill |
| — package | Reuse `.config`; no blueprint edit | A new `.security` key in eight blueprints |
| — default access | Authenticated (deny-by-default), except the springdoc OpenAPI / Swagger UI paths, public unless the interview says they are blocked too | Public by default |
| — extra scope | CORS, Actuator protection, headers and CSRF, authentication/authorization audit events | — |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `skills/security-architect/` (Form 1, class `design`, partial `32-seguranca.md`) + `rules/authorization.md` (Form 4) + an `Access` row in `use-case-design` | 8 | **Approved** |
| 2 | `rest-api-architect` gains a security block and the templates + `rules/authorization.md` | 6 | Rejected — re-interviews a project-wide mechanism per endpoint; loads security into every REST run |
| 3 | `skills/security-architect/` alone, norms in its `references/` | 5 | Rejected — the executor never reads a design skill's `references/` |
| 4 | Create nothing | 3 | Rejected — the first secured case would be improvised with no norm |

### Option 1 — `security-architect` + `rules/authorization.md` (score 8)

**Motivator:** axes 2, 9 and the norm answer — access is a per-use-case decision with a
project-wide mechanism behind it, and no piece owns either.

**Pros:** one owner for "who may call this and how they prove it"; the mechanism is decided
once and inherited from the code on disk and earlier `32-seguranca.md` partials (precedent: the
replica count in `jobs-architect`); `use-case-design` always asks, so an open endpoint is a
recorded decision, never an omission; the executor gets the norms by `paths`; a case with a
public endpoint in an unsecured project pays nothing (step skipped).

**Cons:** biggest propagation — `use-case-design` (Access row), `domain-modeling` (actor in the
command, ownership invariant when Access = owner), `new-feature` (step 3b, precedence row,
declared-dependency gate, spec block), `test-architect` (template, reads the partial), executor
(Block S), `exemplar-imports` dependency list, routing and docs. Built on anticipation (§ 3 of
the decision matrix): no run has failed yet. The project-fact answer cannot land in the root
`CLAUDE.md` as the interview first sketched — a design skill's territory is `docs/use-cases/**`
— so it is inherited from code and partials instead.

**Points cut in the rubric:** 1 (anticipation, § 3) · 5 (maintenance: many files touched).

**CI:** `validate · design › frontmatter schema` covers the skill (class, sections, model,
export entry) and the rule (`paths`, `derived_paths`); `rules is a leaf of the graph`, `norm
contains no code boilerplate`, `every norm paths matches some blueprint`, `exemplars have the
.example suffix at the end`, `exemplar bodies carry no comment but Javadoc` cover the files.
**Change needed:** `validate · exemplar-imports › reference project from the Initializr` gains
`security,oauth2-resource-server` in `dependencies=` — without it every new template fails the
job (and `spring-security-test` comes with the starter). Green run, then red with the
dependency removed. Nothing in the generated project's `ci.yml.example`: `verify` already fails
on what the templates produce.

### Option 2 — security block inside `rest-api-architect` (score 6)

Fewer pieces, but the mechanism (a project fact) gets re-interviewed inside a per-endpoint skill
already at 310 lines, every REST run loads security content even for a public endpoint, and the
local-users / API-key tables enter as REST schema requirements. Cut: 3, 5, 6.
CI: same as option 1.

### Option 3 — skill alone, norms in `references/` (score 5)

The executor never loads a design skill's `references/`; deny-by-default, 401-vs-403 and "no
`permitAll` without a recorded reason" reach code only through each partial. Same rejection as
0087's option 2. Cut: 2 (invariant 2 pressure), 4, 6, 7.
CI: as option 1 minus the rule checks.

### Option 4 — create nothing (score 3)

Defensible only because axis 1 is anticipation. Rejected direction: the first secured case
would be improvised by the executor with no norm, and an accidentally open endpoint costs
days, not minutes (axis 10).
CI: nothing to test.

## References

| Claim | Source |
|---|---|
| A pipeline design piece is Form 1, not Form 2 | `claude-code-architect-designer/references/decision-matrix.md` § 4 · `decisions/0007-pipeline-skills-invocation.md` |
| No agent: the interview is the task | decision matrix § 5 counter-test |
| Skill + rule, not skill alone | `decisions/0087-jobs-architect-skill.md` options 1 and 2 |
| Norm in one owner, cited by path; rules a leaf | `@CLAUDE.md` invariants 1 and 2 |
| Boilerplate only in `templates/*.example` | `@CLAUDE.md` invariant 3 |
| Spring Security version comes from the Boot BOM, never written | `@CLAUDE.md` invariant 8 |
| Development skill in `export.skills.include` and a class | `@CLAUDE.md` invariant 9 |
| A late schema requirement forces a second persistence pass | `decisions/0049-messaging-before-persistence.md` |
| First-use project-wide code declared NEW in the partial, written by the executor | `decisions/0044-idempotent-first-endpoint.md` |
| Rule globs derived from `packages.map` | `export.derived_paths` in `schemas/extensions.json` |
| Templates follow the servlet reference | <https://docs.spring.io/spring-security/reference/servlet/index.html> |

## Research — the generation the templates target (2026-10-02)

Read from the Spring Security reference and the Initializr's current default, then checked with
`javap` against the resolved classpath (Boot 4.1.1, Spring Security 7.1.1). What shaped the
exemplars:

| Fact | Consequence in the exemplars |
|---|---|
| `and()`, `authorizeRequests`, `AntPathRequestMatcher`/`MvcRequestMatcher` removed; `build()` and the DSL no longer declare `throws Exception` | Lambda DSL only, no `throws` on the chain bean |
| A `Customizer<HttpSecurity>` bean is applied by the framework to every chain | The mechanism is an own `AuthenticationMechanism` interface, never a customizer bean (it would be applied twice) |
| A `JwtDecoder` bean replaces Boot's and drops the `audiences` validation | No decoder bean; Boot properties carry issuer and audience |
| No Problem Details support in the framework; the bearer entry point writes no body | Entry point and access-denied handler delegate to the MVC resolvers; one advice writes every 401/403 |
| `@WebMvcTest` does not pick up `@Configuration` classes | The test exemplar imports the chain; `addFilters = false` forbidden |
| Authorization events are not published without an `AuthorizationEventPublisher` bean | `SecurityAuditListener` registers one |
| `AnnotationTemplateExpressionDefaults` is still opt-in | Published as a static bean next to `@EnableMethodSecurity` |
| `AuthenticationFilter`'s default success handler redirects; a filter `@Component` runs twice | API-key filter built inside the mechanism with a no-op success handler |
| Boot 4 starters: `spring-boot-starter-security-oauth2-resource-server` (old name deprecated), test starters `spring-boot-starter-security-test` and `…-oauth2-resource-server-test` | Partial exemplar uses the Boot 4 names; the skill takes them from the Initializr ids, never from memory |
| Every authentication gains a factor authority (`FACTOR_BEARER`, `FACTOR_PASSWORD`) | Tests never assert an exact authority set |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/security-architect/SKILL.md` | New — entry rule on `Access` × chain on disk, inherit-first survey, interview, mechanism choice, eleven-block partial |
| `.claude/skills/security-architect/references/mechanism-decision-matrix.md` | New — mechanisms, first-match decision table, where each provider puts roles and groups |
| `.claude/skills/security-architect/references/servlet-reference-links.md` | New — the user's three links plus the servlet reference by topic, Boot side, RFCs |
| `.claude/skills/security-architect/templates/*.example` | New — `security-spec.md`, `SecurityConfig` (+ `AuthenticationMechanism`, `SecurityAccessProperties`), `JwtResourceServerConfig` (+ `TokenAuthoritiesProperties`, `ClaimPathAuthoritiesConverter`), `OpaqueTokenResourceServerConfig`, `LocalUsersSecurityConfig`, `ApiKeyAuthenticationConfig`, `ProblemDetailSecurityHandlers`, `SecurityExceptionHandler`, `MethodSecurityAnnotations`, `CurrentActorResolution`, `SecurityAuditListener`, `OpenApiSecurityConfig`, `application-security.yml` |
| `.claude/rules/authorization.md` | New norm — boundary, default access, mechanism, transport hardening, failure responses, credentials and logs, tests, How to verify |
| `.claude/rules/00-index.md` | Row in the written table; out of the planned table; listed among the package-territory rules |
| `.claude/rules/personal-data.md` | § Scope cites `authorization.md` instead of the planned entry |
| `.claude/schemas/extensions.json` | `skill_classes.design.skills`, `export.skills.include`, `derived_paths["authorization.md"]` (suffixes `.rest`, `.config`, slice fallback) |
| `.claude/skills/use-case-design/SKILL.md` + `use-case-spec.md.example` | `Access` interview block on every HTTP trigger; checklist line against asking mechanism questions; `Access` row in the parent spec |
| `.claude/skills/domain-modeling/SKILL.md` | Step 4c — owner field, actor in the command, ownership invariant raising the not-found exception |
| `.claude/skills/new-feature/SKILL.md` + `feature-spec.md.example` | Step 3b and its table, order rationale, precedence row, declared-dependency gate on § 8, `permitAll` gate, Block S recipient, spec block `4.5 Security` |
| `.claude/skills/test-architect/SKILL.md` + `SecuredControllerTest.java.example` | Reads `32-seguranca.md` § 10; three-way endpoint proof through the real chain; test starters in § 5 |
| `.claude/agents/java-spring-boot-developer.md` | Block S (S1-S3) after Block 3; reads `authorization.md`; `[config]` write row; `pom.xml` entitled by security § 8; tests when Block S ran |
| `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example` | Routing row in the generated project |
| `CLAUDE.md` | Routing row |
| `.github/workflows/validate.yml` | `exemplar-imports`: `security,oauth2-resource-server` in the Initializr dependency list |
| `README.md`, `docs/{en,pt-br}/00,01,03,09` | Pipeline order, partial list, skill and norm lists. `docs/*/07-ci-validate.md` unchanged: it describes `exemplar-imports` without listing the Initializr dependencies |

Goes to the generated project: **yes** — `security-architect` via `export.skills.include`,
`authorization.md` via `export.rules` with its glob derived per blueprint. Checked with
`ArchHook.java export` into hexagonal (`**/adapter/in/rest/**`), clean single-module
(`**/infrastructure/rest/**`, `**/infrastructure/config/**`), layered (`**/controller/**`,
`**/config/**`) and vertical-slice (`**/config/**` — the fallback does not fire when `.config`
matches, recorded in the `derived_paths` comment), with `schema` green inside each result.

Not changed, on purpose: the thirteen `use-case-design/examples/` keep no `Access` row — they
illustrate boundaries, and the template carries the row. No blueprint edit.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · design › frontmatter schema` | Skill in a class with its sections and model, in `export.skills.include`; rule has `paths` and a `derived_paths` entry | green locally (`schema` exit 0) |
| `validate · design › rules is a leaf`, `norm contains no code boilerplate`, `each norm has a single owner`, `every norm paths matches some blueprint`, `exemplars have the .example suffix`, `exemplar bodies carry no comment but Javadoc` | The rule and the 13 exemplars respect the repository's shape | all green locally (`TemplateCommentsTest`: 93 exemplars) |
| `validate · exemplar-imports › every exemplar import exists in a JAR` | Every `org.springframework.security`/`boot.security` import of the new exemplars exists in the classpath the Initializr resolves today | green with `security,oauth2-resource-server` added; red with the security jars removed — `❌ org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest does not exist in any JAR of the reference project.` and every other security class by name |

Beyond CI, once, before committing: every security exemplar split into a fresh Initializr
project (Boot 4.1.1) and compiled; 15 web-slice and unit tests run green — the
`SecuredControllerTest` exemplar as written, method-security denial → 403 (not 500), anonymous →
401 with the challenge, API key valid/wrong/missing, HTTP Basic right/wrong password, Keycloak
claim paths. Red once: without `@Order(Ordered.HIGHEST_PRECEDENCE)` on `SecurityExceptionHandler`
the method-security tests fail with `Status expected:<403> but was:<500>` — the defect the
advice ordering exists against. Not added as a CI job: the exemplars are shape references, not
verbatim copies, and `exemplar-imports` already holds the API surface; a `templates.yml` job is
the next step if an exemplar ships broken.
