# 0121 · A design skill owns the outbound HTTP adapter, and failures of a dependency get three integration families

- **Date:** 2026-10-05
- **Scenario:** "Criar o http-client-architect: skill de design do adapter HTTP de saída (cliente
  REST) de um caso de uso já modelado, no pipeline /new-feature depois de domain-modeling declarar
  o port de saída e antes de test-architect. Inclui as famílias de erro de infraestrutura
  (dependência indisponível, resultado desconhecido), que hoje conflitam com 'exactly four
  families' em rules/error-handling.md. Importante, cada opção de implementação de http client tem
  que ter seus templates."
- **Decision:** Option 1 — Form 1 `.claude/skills/http-client-architect/SKILL.md` (class `design`, partial
  `28-cliente-http.md`) + Form 4 `.claude/rules/http-client.md`; `error-handling.md` gains a second
  root, `IntegrationException`, with three families; `use-case-design` asks External calls (the
  authentication scheme always); `domain-modeling` gives every output port a kind; `/new-feature`
  runs it as step 3c, after security and before messaging.
- **State:** approved by Lucas Fernandes, on 2026-10-05 — option 1, with three deviations recorded
  below: onion's key is `app.http`, not `integration`; the engine exemplars are builder
  customizers, not replacement beans; the retry allowlist is a predicate, not `includes`

## What the context showed

| Fact | Where |
|---|---|
| No piece designs an outbound HTTP call; nothing names `RestClient`, `@HttpExchange`, a timeout or a retry policy for one | grep over `.claude/rules`, `.claude/skills`, `.claude/agents` |
| `api-rest.md` already maps a dependency failure to 502 / 503 + `Retry-After` / 504 — but no exception reaches those rows: its "Domain types to statuses" table has the four domain families and "Unmapped → 500" | `.claude/rules/api-rest.md` § Errors — 500 family, § Error body |
| `error-handling.md` allows exactly four families, all under `DomainException`, the only class allowed to extend `RuntimeException` | `.claude/rules/error-handling.md` § Shape of the base classes |
| `logging.md` already has a row for an outbound integration adapter (outcome + latency, never the payload) | `.claude/rules/logging.md` per-class-type table |
| `messaging.md` already retries a transient failure in the listener — a second retry in an HTTP adapter called from it multiplies attempts | `.claude/rules/messaging.md` § retry |
| No blueprint has a package for an outbound HTTP adapter; `0087` added `*.scheduling` to all eight the same way | `packages.map` of each blueprint |
| `exemplar-imports` resolves imports against an Initializr project without `oauth2-client`, `webflux`, Spring Cloud OpenFeign, Resilience4j or Apache HttpClient 5 | `.github/workflows/validate.yml` › `exemplar-imports` |

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — symptom | Anticipation from the roadmap: no generated project has called an external API yet | — (weighed in the score, decision matrix § 3) |
| 2 — trigger | Model decision inside `/new-feature`, when a use case has an outbound port to an external HTTP system; standalone by description | Form 2 |
| 5 — nature | Multi-step: inherit, interview the provider contract, choose client and engine, decide retry/resilience/auth, write the partial | Forms 4 and 5 as the whole answer |
| 6 — isolation | None — the interview is the task | Form 3 |
| 7 — mandatoriness | Persuasion; no hook before an observed failure | Forms 7 and 8 |
| 8 — destination | Both — development skill, `export.skills.include` | — |
| 9 — integration | Own design skill and partial; runs as step 3c of `/new-feature` (after security, before messaging) because it generates requirements for persistence (the stored `Idempotency-Key` of an outbound POST) and for jobs (reconciling an unknown outcome) | Running after persistence (second pass, `0049`) |
| — detection | `domain-modeling`'s Ports table gains a kind column (`persistence` · `messaging` · `external HTTP`); `use-case-design` asks an explicit **External calls** block whenever a side effect reaches another system, with the authentication scheme always asked | A bare "side effects" line read by inference |
| — failure taxonomy | Three integration families under a second abstract root `IntegrationException`, sibling of `DomainException`: `DependencyResponseException` → 502, `DependencyUnavailableException` → 503 + `Retry-After`, `OutcomeUnknownException` → 504. `error-handling.md` becomes "four domain families + three integration families" | Two families; the same root as `DomainException`; a sealed result type per port |
| — clients with templates | `@HttpExchange` over `RestClient` (default) · `RestClient` direct · OpenAPI Generator · `WebClient` · OpenFeign | — |
| — `WebClient` scope | Servlet only: chosen solely to consume a stream (SSE / NDJSON). A WebFlux application stays out of scope until a reactive skill exists, the same frontier `0112` drew | `WebClient` + `block()` as a general alternative; reactive stack in this skill |
| — OpenFeign scope | Legacy only: an adopted project that already uses it keeps the same norms, plus the migration recipe to `@HttpExchange`; new code never chooses it | Feign as a first-class option |
| — engines with templates | Apache HttpClient 5 (default) · JDK `HttpClient` · Reactor Netty (only under `WebClient`) | Jetty |
| — resilience | Framework 7 `@Retryable` always with an `includes` allowlist; Resilience4j circuit breaker and bulkhead when the interview names a slow or unstable dependency | Spring Retry (archived), Failsafe (dormant) |
| — outbound authentication | Templates for every scenario: OAuth2 client credentials, token relay of the caller's bearer, API key / static header, HTTP Basic, mTLS through an SSL bundle (bundle creation stays with `transport-security-setup`), HMAC request signing | Leaving authentication to the executor |
| — tests | `test-architect` owns the WireMock template and reads the partial's mandatory cases (`0112`'s split) | Test templates inside the new skill |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `skills/http-client-architect/` (Form 1, class `design`, partial `28-cliente-http.md`) + `rules/http-client.md` (Form 4) + `error-handling.md`/`api-rest.md` amended for the integration families + `adapter.out.http`-equivalent key in the eight blueprints | 8 | **Approved** |
| 2 | `skills/http-client-architect/` alone, norms and integration families in its `references/` | 5 | Rejected — the executor never reads a design skill's `references/`; two owners of the taxonomy |
| 3 | Taxonomy amendment and the norm only, no skill: the executor builds the adapter from `rules/http-client.md` | 4 | Rejected — nothing interviews the provider; persistence never learns the stored key |
| 4 | Create nothing | 3 | Rejected — library defaults become the incident; a provider's 503 surfaces as our 500 |

### Option 1 — `http-client-architect` + `rules/http-client.md` + integration families (score 8)

**Motivator:** axes 2, 9 and the failure-taxonomy answer — an outbound call is a per-use-case
decision (which provider, which client, which retry) with project-wide norms behind it, and the
5xx statuses `api-rest.md` already promises have no exception to come from.

**Pros:** one owner for "how this service calls another one"; the provider contract is
interviewed in `use-case-design` (External calls) and modeled as a port kind in
`domain-modeling`, so an outbound call is never discovered by the executor; the engine, the
`spring.http.clients.*` defaults and the OAuth2 client registration are project facts, decided
by the first case and inherited afterwards from code and earlier `28-cliente-http.md` partials
(precedent: the mechanism in `security-architect`); the stored idempotency key and the
reconciliation job reach persistence and jobs in their first pass; the executor gets the norms
by `paths`; the integration families close the gap between `api-rest.md`'s 502/503/504 rows
and `error-handling.md`.

**Cons:** largest propagation so far — `error-handling.md` (second root, three families, the
"never a fifth" sentence rewritten), `api-rest.md` (three rows), `ApiExceptionHandler` exemplar,
`use-case-design` (External calls block), `domain-modeling` (port kind column), `new-feature`
(step 3c, precedence row, spec block `3.3`, declared-dependency gate), `java-spring-boot-developer`
(Block H), `test-architect` (WireMock template), eight blueprints, `exemplar-imports`, routing
and docs. About 30 templates, five clients × three engines × six authentication schemes — each
one a shape that ages with Boot. Built on anticipation (§ 3 of the decision matrix). The second
root strains the letter of `error-handling.md` and is a norm change, recorded here.

**Points cut in the rubric:** 1 (anticipation, § 3) · 5 (maintenance: many files, many
templates).

**CI:** `validate · design › frontmatter schema` covers the skill (class, sections, model,
export entry) and the rule (`paths`, `derived_paths`); `rules is a leaf`, `norm contains no
code boilerplate`, `every norm paths matches some blueprint`, `exemplars have the .example
suffix`, `exemplar bodies carry no comment but Javadoc` cover the files. **Change needed:**
`validate · exemplar-imports › reference project from the Initializr` gains the dependencies
the new exemplars import (`oauth2-client`, `webflux`, the OpenFeign and Resilience4j Spring
Cloud ids, and `httpclient5` added to the pom the way `micrometer-tracing-bridge-otel` already
is — managed by the Boot BOM, no version written), and `PREFIXES` gains `org.apache.hc`,
`io.github.resilience4j`, `reactor`, `feign`. WireMock and the OpenAPI Generator plugin are not
BOM-managed and stay unchecked, said in the step's comment. Green run, then red with one
dependency removed. Beyond CI, once: every exemplar compiled in a fresh Initializr project and
the WireMock test exemplar run green, as `0112` did.

### Option 2 — skill alone, norms in `references/` (score 5)

The executor never loads a design skill's `references/`; the timeout, retry allowlist and
tolerant-reader norms would reach code only through each partial, and the integration
families would live outside `error-handling.md`, which still says "exactly four" — two owners
of the taxonomy. Same rejection as `0087`'s and `0112`'s option 2/3. Cut: 2 (invariant 2
pressure), 4, 6, 7.
CI: as option 1 minus the rule checks.

### Option 3 — norm and taxonomy only, no skill (score 4)

Cheapest, and the families and norms land. But nothing interviews the provider: retry owner,
idempotency support, quota, authentication scheme and OpenAPI availability are decided by the
executor at write time, with no partial to review — and persistence never learns about the
stored key. A per-use-case decision with no design step fails decision matrix § 2 (multi-step
procedure → skill). Cut: 1, 4, 6, 7, 9 (a judgment call made silently in code).
CI: the rule checks only.

### Option 4 — create nothing (score 3)

Defensible only because axis 1 is anticipation. Rejected direction: the first external call
would be improvised by the executor — the library defaults the research lists (no timeout in
the JDK client, five connections per route in Apache HttpClient 5, `@Retryable` retrying every
exception) are the incident, and a 503 from a provider would surface as a 500 of ours.
CI: nothing to test.

## References

| Claim | Source |
|---|---|
| A pipeline design piece is Form 1, not Form 2 | `claude-code-architect-designer/references/decision-matrix.md` § 4 · `decisions/0007-pipeline-skills-invocation.md` |
| No agent: the interview is the task | decision matrix § 5 counter-test |
| Skill + rule, not skill alone | `decisions/0087-jobs-architect-skill.md`, `decisions/0112-security-architect-skill.md` |
| Norm in one owner, cited by path; rules a leaf | `@CLAUDE.md` invariants 1 and 2 |
| Boilerplate only in `templates/*.example` of the skill that emits it | `@CLAUDE.md` invariant 3 |
| Library versions from the Boot BOM or `maven-metadata.xml`, never written | `@CLAUDE.md` invariant 8 |
| Development skill in `export.skills.include` and a class | `@CLAUDE.md` invariant 9 |
| A late schema requirement forces a second persistence pass | `decisions/0049-messaging-before-persistence.md` |
| First-use project-wide code declared NEW in the partial, written by the executor | `decisions/0044-idempotent-first-endpoint.md` |
| A new package role is a key in every blueprint's `packages.map` | `decisions/0087-jobs-architect-skill.md` · `@.claude/blueprints/_schema.md` |
| Rule globs derived from `packages.map` | `export.derived_paths` in `schemas/extensions.json` |
| `RestTemplate` deprecation schedule, HTTP interface registry and groups | <https://spring.io/blog/2025/09/30/the-state-of-http-clients-in-spring> · <https://spring.io/blog/2025/09/23/http-service-client-enhancements/> |
| `RestClient`, HTTP interfaces, `WebClient` | <https://docs.spring.io/spring-framework/reference/integration/rest-clients.html> |
| `spring.http.clients.*`, factories, service-client groups | <https://docs.spring.io/spring-boot/reference/io/rest-client.html> |
| `@Retryable` / `@EnableResilientMethods` | <https://docs.spring.io/spring-framework/reference/7.0/core/resilience.html> |
| Retry only on an allowlist, backoff with jitter, one retrying layer | <https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter> · <https://sre.google/sre-book/addressing-cascading-failures/> |
| Idempotency, `Retry-After`, status semantics | RFC 9110 |
| Remote errors read by `type` | RFC 9457 |
| Third-party responses are untrusted input; SSRF | OWASP API10:2023 |

## Deviations from the proposal, decided while writing

| Proposed | Written | Why |
|---|---|---|
| onion's key `integration` | `app.http: "app.http"` | onion's modules are rings (`domain`, `persistence`, `presentation`, `app`); a top-level `integration` package belongs to no module and sits outside `architecture_paths`. `app` is the composition root and already hosts `app.scheduling` for the same reason (`0087`) |
| An engine bean that replaces Boot's `ClientHttpRequestFactoryBuilder` | `ClientHttpRequestFactoryBuilderCustomizer<…>` / `ClientHttpConnectorBuilderCustomizer<…>` | Boot 4 tunes its own builder through typed customizers; replacing the bean drops what Boot wires on it. The customizer is typed to one engine, so `spring.http.clients.imperative.factory` becomes mandatory — without it the customizer is skipped in silence |
| `@Retryable` with an `includes` allowlist | `@Retryable(predicate = DependencyRetryPredicate.class)` + `@IdempotentCall` | `includes` lists types; the rule needs "outcome unknown only when the call is idempotent" and "never when a `Retry-After` was announced", which a type list cannot say. The predicate is still an allowlist — it returns false for everything it does not name |

## Research — verified on the classpath, not from memory (2026-10-05)

An Initializr project with `web,spring-restclient,spring-webclient,oauth2-client,cloud-feign,…`
(Boot 4.1.1, Framework 7.0.9, Spring Cloud 2025.1.3), plus `httpclient5` (Boot-managed) and
`resilience4j-spring-boot4` / `wiremock-spring-boot` at the `<release>` of their
`maven-metadata.xml`. Every API the exemplars name was read with `javap` first; every exemplar was
then compiled; the default adapter and the integration-test exemplar were run.

| Fact | Consequence in the exemplars |
|---|---|
| `spring.http.serviceclient.<group>` binds `base-url`, `default-header`, `apiversion.*`, and the client settings (`connect-timeout`, `read-timeout`, `redirects`, `ssl.bundle`) | The adapters carry no URL, timeout or header in code; mTLS and a header API key need no Java |
| `@ImportHttpServices` proxies a package-private interface | HTTP interface, DTOs and client config stay package-private in the provider's subpackage |
| `ClientHttpRequestFactoryBuilderCustomizer` exists in `spring-boot-http-client` | Engine exemplars are customizers (deviation above) |
| `@Retryable` has `predicate`, `jitter`, `maxDelay`, `multiplier`; inert without `@EnableResilientMethods` | `DependencyRetryPredicate`; `OutboundHttpConfig` carries the switch |
| `OAuth2RestClientHttpServiceGroupConfigurer` + `@ClientRegistrationId` (type or method) | Client credentials needs one configurer bean, selected per interface |
| `HttpClientErrorException.UnprocessableContent` is the 422 type | The 422 catch in the default adapter |
| **Runtime, first run:** the pool lease timeout surfaced as `OutcomeUnknownException` — its type is `org.apache.hc.core5.http.ConnectionRequestTimeoutException`, an `InterruptedIOException` | Added to "not sent" in `HttpFailureTranslator`; re-run → `DependencyUnavailableException`. The same run proved the customizer is applied: a pool of one starved the second concurrent call at the configured 200 ms |
| **Runtime:** a host refused by `InetAddressFilter` throws `FilteredHostException`, not a `RestClientException` | The webhook adapter catches it into a business rejection; the translator would never have seen it |
| **Runtime:** the OAuth2 client starter in a project with no `SecurityFilterChain` → `/actuator/info` and any endpoint 401 (404 without the starter); `/actuator/health` 200 either way | The partial's § 10 records the impact; the exemplar's header says so |
| `resilience4j-spring-boot4` resolves its core modules to Spring Cloud's older Resilience4j when OpenFeign is in the build | Import `resilience4j-bom` ahead of `spring-cloud-dependencies`; recorded in the decision matrix § 5 and in step 11 of the skill |
| OpenAPI Generator 7.25 (`spring` / `spring-http-interface`, `useSpringBoot4`, `useJackson3`, `enumUnknownDefaultCase`) generates a plain `@HttpExchange` interface returning `ResponseEntity<T>` | `OpenApiGeneratedAdapter.java.example` compiled against the generated sources |

## Propagation

| File | Change |
|---|---|
| `.claude/skills/http-client-architect/SKILL.md` | New — entry rule on a port of kind `external HTTP`, inherit-first survey, six-axis interview, client/engine/auth choice through the matrix, eleven-block partial |
| `.claude/skills/http-client-architect/references/client-decision-matrix.md` | New — client, engine and authentication tables (first match wins), the defaults each exemplar answers, the facts observed while verifying |
| `.claude/skills/http-client-architect/references/http-client-reference-links.md` | New — Spring, library, practice and RFC sources |
| `.claude/skills/http-client-architect/templates/*.example` (19) | New — `http-client-spec.md`; clients `HttpExchangeAdapter`, `RestClientAdapter`, `OpenApiGeneratedAdapter` + `openapi-generator-maven.xml` / `-gradle.kts`, `WebClientStreamAdapter`, `FeignClientAdapter`; engines `ApacheHttpClient5Config`, `JdkHttpClientConfig`, `ReactorNettyConfig`; shared `IntegrationExceptions`, `HttpFailureTranslator`; authentication `OAuth2ClientCredentialsConfig`, `TokenRelayInterceptor`, `ApiKeyAuthConfig`, `BasicAuthConfig`, `MutualTlsClientConfig`, `HmacSigningInterceptor`; `application-http-client.yml` |
| `.claude/rules/http-client.md` | New norm — boundary, client choice, engine and pool, timeouts, retry, failures, resilience, outbound authentication, security, consumption, observability, tests, How to verify |
| `.claude/rules/error-handling.md` | Second root `IntegrationException` and § Integration families; "exactly four" now "four domain families"; seven in all; logging level for the new families |
| `.claude/rules/api-rest.md` | The three families in the status table (502 / 503 + `Retry-After` / 504); `dependency` never in the body |
| `.claude/rules/observability.md` | `paths` gains the two HTTP-client globs |
| `.claude/rules/00-index.md` | Row in the written table; listed among the package-territory rules |
| `.claude/schemas/extensions.json` | `skill_classes.design.skills`, `export.skills.include`, `derived_paths["http-client.md"]` (suffix `.http`, optional), `.http` added to `observability.md`'s suffixes |
| `.claude/skills/rest-api-architect/templates/ApiExceptionHandler.java.example` | Three integration handlers and `integrationProblem` |
| `.claude/skills/use-case-design/SKILL.md` + `use-case-spec.md.example` | Sixth interview block External calls, authentication always asked; checklist rows split inbound/outbound; `External calls` row |
| `.claude/skills/domain-modeling/SKILL.md` + `domain-spec.md.example` | Step 4d — port of kind `external HTTP`, domain outcomes, idempotency key, unknown-outcome state; every output port names its kind |
| `.claude/skills/new-feature/SKILL.md` + `feature-spec.md.example` | Step 3c and its order rationale, 4b/5/6 wiring, precedence row, § 8 dependency gate, Block H recipient, spec block `3.3 Outbound HTTP` |
| `.claude/skills/persistence-architect/SKILL.md` | Reads `28-cliente-http.md` § 10; stops without it when the port exists |
| `.claude/skills/jobs-architect/SKILL.md` | Entry row: a reconciliation pass asked by `28-cliente-http.md` § 10 |
| `.claude/skills/test-architect/SKILL.md` + `HttpClientAdapterIT.java.example` | Reads § 9; WireMock exemplar with the nine mandatory cases |
| `.claude/agents/java-spring-boot-developer.md` | Block H (H1-H3) after Block 2; reads `http-client.md`; `[http]` write row; `pom.xml` entitled by § 8; tests when Block H ran |
| `.claude/blueprints/*/*.yaml` (7 with a yaml + custom template) | `.http` key: hexagonal `adapter.out.http` (compiled into `bootstrap`), clean `infrastructure.http`, layered `client` (+ path and dependency rules), onion `app.http`, modular-monolith `shared.internal.adapter.http`, vertical-slice `integration` (+ path and dependency rules), custom `app.http` |
| `CLAUDE.md`, `project-bootstrap/templates/root.CLAUDE.md.example` | Routing row |
| `.github/workflows/validate.yml` | `exemplar-imports`: `spring-restclient,spring-webclient,oauth2-client,cloud-feign`; `httpclient5`, `resilience4j-spring-boot4`, `wiremock-spring-boot` added with versions read from `maven-metadata.xml` per run; six new import prefixes |
| `README.md`, `docs/{en,pt-br}/00,01,03,09` | Pipeline order and diagrams, partial and precedence rows, skill and norm counts (23 skills, 16 norms), design-class count. Delegated (Phase 4 step 8) with this record; `docs/*/07-ci-validate.md` unchanged — it describes `exemplar-imports` without listing the Initializr dependencies |

Goes to the generated project: **yes** — `http-client-architect` via `export.skills.include`,
`http-client.md` via `export.rules` with its glob derived per blueprint. Checked with
`ArchHook.java export` into every blueprint with a yaml: hexagonal `**/adapter/out/http/**`, clean
`**/infrastructure/http/**`, layered `**/client/**`, onion `**/app/http/**`, modular-monolith
`**/shared/internal/adapter/http/**`, vertical-slice `**/integration/**` — two exports identical,
no dead citation, `schema` green inside each.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · design › frontmatter schema` | Skill in a class with its sections and model, in `export.skills.include`; rule has `paths` and a `derived_paths` entry | green locally; red with the export entry and the `derived_paths` entry removed — both named |
| `validate · design › rules is a leaf`, `norm contains no code boilerplate`, `each norm has a single owner`, `every norm paths matches some blueprint`, `exemplars have the .example suffix`, `no hardcoded Spring/Java version` | The rule and the exemplars respect the repository's shape | green locally (one dated version note rewritten to the allowed "Tested to compile" form) |
| `validate · design › exemplar bodies carry no comment but Javadoc` | No body comment in the 17 new or changed Java exemplars | green — 111 exemplars |
| `validate · exemplar-imports › every exemplar import exists in a JAR` | Every import of the new exemplars resolves against what the Initializr and Maven Central resolve today | green — 269 imports; red with `httpclient5`, Resilience4j, WireMock and Feign jars removed — each missing class named |
| `export-determinism › export twice per blueprint` · `the exported tree passes its own schema` | The new skill, rule and blueprint keys export deterministically and validate inside a project | green for all seven blueprints |

Beyond CI, once, before committing: every Java exemplar split on its `// --- ` blocks and compiled
with `javac` against the reference classpath (15 + `ApiExceptionHandler`); the default adapter,
the shared translator and the HC5 customizer run in a fresh Initializr project under the
`HttpClientAdapterIT` exemplar as written — 9 / 9 green. Red twice: without
`@EnableResilientMethods`, 2 failures (`Expected exactly 3 requests … but received 1`); without
the retry predicate, 3 failures (`Expected exactly 1 requests … but received 3`). Not added as a CI
job: the exemplars are shape references, not verbatim copies, and `exemplar-imports` holds the API
surface — a `templates.yml` job that runs `HttpClientAdapterIT` is the next step if an exemplar
ships broken.
