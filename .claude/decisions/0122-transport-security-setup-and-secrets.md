# 0122 · Transport security is decided once per project by a setup skill, and secrets get one owning norm

- **Date:** 2026-10-05
- **Scenario:** "Criar transport-security-setup (skill de setup no formato da sonarqube-setup, encadeada por project-bootstrap e arch-adopt, topologias A borda / B direta, C mTLS como decisão registrada), mais rules/transport-security.md (dona do checklist do dossiê) e rules/secrets.md (hoje planejada em 00-index). […] Conteste a distribuição de donos se algum estiver errado, e decida se chave privada/keystore versionado vira hook agora ou espera ocorrência. Cada exemplar tem que compilar antes de entrar em templates/."
- **Decision:** option 1 — Form 1, `.claude/skills/transport-security-setup/SKILL.md`, chained by `project-bootstrap` step 7.5 and `arch-adopt` step 7.5; Form 4, `.claude/rules/transport-security.md` and `.claude/rules/secrets.md`; no hook
- **State:** approved by Lucas Fernandes, 2026-10-05
- **Goes to the generated project:** yes — the skill in `export.skills.include`, both rules by `export.rules`

## Interview

| Axis | Answer | Forms it eliminated |
|---|---|---|
| 1 — Symptom | Anticipation, from the roadmap. No generated project has asked for TLS yet. Two defects exist on disk today regardless: `SecurityConfig.java.example` and `authorization.md` § Transport hardening promise HSTS, which is never written behind an edge without forward headers and never written at all without Spring Security; and "no credential in a versioned file" is stated in four norms (`authorization.md`, `http-client.md`, `persistence.md`, `messaging.md`) — invariant 2 | Every hook (invariant 6, mirror) — option 2 |
| 2 — Trigger | Chained by `project-bootstrap` and by `arch-adopt`, and invocable by hand — the `sonarqube-setup` shape | Form 2 (a chained skill must stay model-invocable, `0007`), Forms 4/5 alone (nobody asks the topology) |
| 5 — Nature | One interview (topology, local profile), then fixed edits per answer | Forms 4 · 5 for the procedure; the facts it enforces are Form 4 |
| 6 — Isolation | None of invariant 5's reasons: the output is a handful of edits and one IT run; `guard` already restricts the territory; `sonnet` like `sonarqube-setup` | Form 3 |
| 8 — Destination | Generated project (adoption runs inside it) | Registration in this repo only |
| 9 — Integration | `application.yml` and `Dockerfile` are written by `project-bootstrap` at generation; `docker-compose.yml` has one owner (`docker-architect`); the filter chain is `security-architect`'s; outbound TLS is `http-client-architect`'s (`http-client.md` § Security, `MutualTlsClientConfig.java.example`) | Any option giving the compose file, the chain or outbound TLS a second owner |
| — Topology | Ask, A (edge) suggested; the answer is a project fact in the root `CLAUDE.md`. Also ask whether the `local` profile skips HTTPS → `local` = plain HTTP 8080, no bundle | An option that assumes one topology |
| — HTTP/2 | On by default (`server.http2.enabled: true`) | — |
| — HSTS | Always the application: Spring Security's default writer when it is on the classpath, otherwise a minimal filter conditional on its absence, which steps aside on its own when Spring Security arrives. The edge is told not to write it | Edge-owned HSTS (untestable from the repo, duplicates once Spring Security arrives); adding the Security starter only for headers (Boot's default chain answers 401 everywhere — observed in `0121`) |
| — mTLS inbound | Waits for a use case. The setup offers C only as the TLS side (`client-auth`, truststore); the X.509 mechanism is a backlog row of `security-architect` | A `MutualTlsX509Config` template now |
| — Local edge | Caddy in compose, through `docker-architect` | — |
| — secrets.md | Consolidate: one owner, the four norms cite it | A keys-only `secrets.md` that leaves the four statements diverging |

## Options evaluated

| # | Option | Score | Verdict |
|---|---|---|---|
| 1 | `transport-security-setup` (Form 1, class `build`) + `rules/transport-security.md` + `rules/secrets.md` consolidating four norms; Caddy templates in `docker-architect`; engine row in `http-client-architect`; no hook | 8 | **Approved** |
| 2 | Option 1 + private-key detection in `guard` now (content scan of `Write`/`Edit` for `PRIVATE KEY` blocks and keystore paths outside test resources) | 6 | Rejected for now — no observed occurrence; revisit on the first one |
| 3 | Norms + exemplars only (the research's option 1) | 4 | Rejected — templates with no emitting skill (invariant 3) |
| 4 | A transport block inside `security-architect` (the research's option 3) | 3 | Rejected — design class, per use case |
| 5 | Create nothing | 3 | Rejected — leaves HSTS and invariant 2 broken |

### Option 1 — `transport-security-setup` + two norms (score 8)

**Motivator:** axes 2 and 9. Transport is decided once per project, before any use case,
and the adopted project is reached only by a skill that travels.

**Owners, contested against the research proposal:**

| Artifact | Research proposed | Decided here | Why |
|---|---|---|---|
| Checklist norm | `rules/transport-security.md` | same | — |
| Credentials, keys, keystores, test material | (one checklist line) | `rules/secrets.md`, sole owner; `authorization.md`, `http-client.md`, `persistence.md`, `messaging.md` cite it | invariant 2, broken today |
| Topology, `server.*`, `spring.ssl.*` | the setup skill | same | — |
| `Dockerfile` port | the setup skill | **`docker-architect`**, chained | it already claimed the `Dockerfile` in prose; its `write_allow` lacked the file, and gains it here |
| TLS + HTTP/2 + HSTS proof | `test-architect` | **the setup skill** | `test-architect` designs one use case's tests as docs; a project-level transport proof has no use case. Precedent: `archunit-installer` writes `ArchitectureTest` |
| HSTS | `security-architect` | **the application, always**: Spring Security's default when present (no template change), else the setup's conditional filter | the interview; one writer in every state of the project |
| `redirectToHttps`, HTTP→HTTPS connector | `security-architect` / the setup | **not templated** | the norm forbids an HTTP listener for an API in production; no case asks for the browser-facing exception |
| `Http2Protocol` tuning | the setup | **not templated** | "only when a measurement asks" — none has |
| Outbound TLS client | the setup | **`http-client-architect`, already** | `MutualTlsClientConfig.java.example`, `http-client.md` § Security. Its engine matrix gains one row: HTTP/2 to a provider required → JDK client (Apache HttpClient 5 classic speaks HTTP/1.1 only) |
| X.509 inbound mechanism | `security-architect` | **deferred** | the interview |
| Caddy service + Caddyfile | `docker-architect` | same, Caddyfile at `docker/caddy/Caddyfile` | `docker-architect`'s territory is `docker-compose*.yml` and `docker/**`; the research's `infra/caddy/` is outside it |
| Kubernetes manifests | out | out | — |
| Pitfalls | `docs/*/11-pitfalls.md` | same | — |

**Test material:** no key or certificate file is committed, not even a self-signed one. The
IT generates its keystore at run time with the JDK's `keytool` into a temporary directory and
writes the two PEM files the production bundle reads — proven in Phase 4, so the fallback (a
committed self-signed pair) was never needed.

**Pros:** the precedent is exact (`sonarqube-setup`, `0091`); fixes the HSTS promise and the
four-way credential norm; outbound TLS stays with its owner; every template compiles.

**Cons:** built on anticipation (axis 1); a new Java filter template exists only for
projects without Spring Security.

**Points cut in the rubric:** criterion 4 (enforcement) → private-key material stays
persuasion; criterion 1, half (form fit) → no observed symptom, justified by failures that
are silent by construction (HSTS absent, certificate expiring with the app up) and
certificate lifetimes already shortened (200 days since 2026-03-15).

**CI:** `validate · design › frontmatter schema` (class, sections, export, model);
`validate · design › each norm has a single owner` gains the phrases the two norms own;
`validate · exemplar-imports` resolves the new `.java.example` imports;
`validate · design › compose service templates merge and parse` takes the Caddy service;
a new case in `.claude/.ci/SkillTerritoryTest.java` for the skill's override.

### Option 2 — Option 1 + private-key detection in `guard` (score 6)

`guard` already runs on every `Write`/`Edit`; a content pattern read from `extensions.json`
would cost no extra process. Rejected for now: no observed occurrence (anti-pattern 16), and
key material is produced by `keytool`/`openssl` through Bash, which `guard bash` judges by
path, not content — the hook would cover the rarer path. **Trigger to revisit:** the first
private key or keystore found in a versioned file of a generated project.

### Option 3 — Norms + exemplars only (score 4)

Templates with no skill that emits them strain invariant 3; nobody asks the topology, and
`arch-adopt` never reaches an adopted project.

### Option 4 — A block inside `security-architect` (score 3)

A design-class skill writes docs only and runs per use case when `Access` asks; a project with
no protected endpoint never decides transport.

### Option 5 — Create nothing (score 3)

Leaves the HSTS promise false and invariant 2 broken in four norms.

## References

| Claim | Source |
|---|---|
| A chained skill stays model-invocable | `@.claude/decisions/0007-pipeline-skills-invocation.md`, `sonarqube-setup/SKILL.md` § Why |
| Same-class chain sums territories (`build` → `docker-architect`) | `@.claude/decisions/0092-guard-same-class-chain-sums-territories.md` |
| Procedural setup skills run on `sonnet` | `@.claude/decisions/0080-procedural-skills-on-sonnet.md` |
| Security starter without a chain answers 401 everywhere | `@.claude/decisions/0121-http-client-architect-skill.md`, `client-decision-matrix.md` § 5 |
| A hook only against an observed failure | `@CLAUDE.md` invariant 6; decision matrix § 2.2 last row, anti-pattern 16 |
| Templates live in the skill that emits them | `@CLAUDE.md` invariant 3 |
| One owner per norm | `@CLAUDE.md` invariant 2 |

## Deviations found while verifying

Each one was found by running the exemplars, not by reading documentation:

1. **A bundle whose file is missing fails the startup even with `server.ssl.enabled: false`** —
   `sslBundleRegistry` loads every bundle eagerly. The research put the bundle in the base
   `application.yml` with a `local` override; that cannot start without the files. The bundle and
   `server.ssl` live in a second YAML document activated by
   `"!local & !docker & (!test | tls)"`: TLS in every profile production might use, plain on the
   developer's machine and in compose, and in tests only when `TransportSecurityIT` adds `tls`.
2. **With TLS on by default, every `@SpringBootTest` needs `@ActiveProfiles("test")`** — the
   generator's own `*ApplicationTests` failed reading `/etc/tls/tls.key` without it. The setup adds
   the profile, which is why its territory takes `src/test/**` whole.
3. **Spring Security's `HstsHeaderWriter` writes only when the header is absent** (read in its
   bytecode). A filter that stayed after Spring Security arrived would run first and silently
   override the chain's HSTS configuration — hence `@ConditionalOnMissingClass(SecurityFilterChain)`,
   not merely "don't write two headers".
4. **The TLS 1.1 refusal case of the research was dropped**: the JDK client refuses TLS 1.1 before
   the server is asked, so the assertion proved the client. The TLS 1.2 acceptance case replaces
   it, and goes red with `enabled-protocols: TLSv1.3`.
5. **`server.tomcat.remoteip.internal-proxies` takes a CIDR list** in this Boot generation; the
   edge template uses one, from `TRUSTED_PROXIES`, leaving loopback out.
6. **Caddy's admin API listens on `127.0.0.1` only, and busybox `wget localhost` resolves to `::1`**
   — the healthcheck names the IPv4 address, or the service never turns healthy.
7. **`docker-architect` could not write the `Dockerfile` it claims** — `write_allow` gained it.
8. **The CI step `every norm paths matches some blueprint` had its own copy of the exempt globs**,
   split globs with unquoted expansion and stripped hyphens inside them. It now reads
   `derived_paths_exempt_globs` from `extensions.json` — the list `schema` reads — one glob per
   line.

## Propagation

| File | Change |
|---|---|
| `.claude/skills/transport-security-setup/SKILL.md` + `templates/` (6) | new skill: two YAML shapes, `HstsHeaderFilter`, `ForwardedHeadersIT`, `TransportSecurityIT`, the `**Transport:` paragraph |
| `.claude/rules/transport-security.md`, `.claude/rules/secrets.md` | new norms |
| `.claude/rules/authorization.md`, `http-client.md`, `persistence.md`, `messaging.md`, `testing.md` | the credential sentence replaced by a citation of `secrets.md`; `authorization.md` cites `transport-security.md` § HSTS; `testing.md` admits the transport IT next to the startup test |
| `.claude/rules/00-index.md` | two rows written, `secrets.md` out of the planned table |
| `.claude/schemas/extensions.json` | `skill_classes.build` + override; `export.skills.include`; `docker-architect` gains `Dockerfile`; `derived_paths_exempt_globs` gains the non-package globs of both norms |
| `.claude/skills/project-bootstrap/SKILL.md` | step 7.5, the `Transport:` report line, the chain in § Contract |
| `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example` | routing row; invariant 6 cites `secrets.md` |
| `.claude/skills/arch-adopt/SKILL.md` | step 7.5, report line, chain in § Contract |
| `.claude/skills/docker-architect/SKILL.md` + `templates/caddy-edge-service.yml.example`, `templates/Caddyfile.example` | the fourth chained path, the Caddy catalog row, `EXPOSE` and the Caddyfile in § Contract |
| `.claude/skills/security-architect/templates/SecurityConfig.java.example` | the HSTS comment names the forwarded-header condition and the one-writer handover |
| `.claude/skills/http-client-architect/references/client-decision-matrix.md`, `templates/JdkHttpClientConfig.java.example` | engine row: a provider requiring HTTP/2 → JDK client |
| `CLAUDE.md` | routing row |
| `.github/workflows/validate.yml` | norm-paths step reads the exempt list from `extensions.json`; three single-owner terms; `requiresChannel(` on the deprecated-symbol denylist |
| `.github/workflows/templates.yml` + `.claude/.ci/TransportTemplatesTest.java` | new job: both topologies in a fresh Initializr project |
| `.claude/.ci/SkillTerritoryTest.java` | ten transport cases |
| `README.md`, `docs/{en,pt-br}/*` | counts, lists, report line, CI rows, pitfalls; the export counts of `02-init-project.md` and `10-arch-adopt.md`, already stale before this change, set to what `export` writes (19 rules, 20 skills, 3 agents) |

Goes to the generated project: **yes** — the skill by `export.skills.include`, both norms by
`export.rules`; the CI test and the decision stay here.

## CI coverage

| Pipeline · job › step | What it proves | Run |
|---|---|---|
| `validate · design › frontmatter schema` | class, sections, model, export entry of the skill; both norms' `paths` exempt or derived | green · red by name with the skill missing from `skill_classes` |
| `validate · design › every norm paths matches some blueprint` | the new globs pass because the exempt list says so, and only then | green · red by name (`docker/caddy/**` → `docker.caddy`) with that glob removed from the list |
| `validate · design › each norm has a single owner` | `forward-headers-strategy`, `reload-on-update`, the placeholder rule each in one norm | green · red by name with `forward-headers-strategy` added to `http-client.md` |
| `validate · design › compose service templates merge and parse` | the `edge` block merges into the base compose | green |
| `validate · exemplar-imports` | every import of the three new `.java.example` exists | green locally, 280 imports |
| `validate · hooks-cross-platform › SkillTerritoryTest` | the setup's file-level territory; `Dockerfile` only after `docker-architect` joins | green · red by name with `Dockerfile` added to the setup's `write_allow` |
| `templates · transport-templates` | both topologies pass their IT in a fresh Initializr project | green · red by name with `forward-headers-strategy: none` (A) and `enabled-protocols: TLSv1.3` (B) |

Also run by hand, not in CI: each IT red with HTTP/2 off and with `HstsHeaderFilter` removed; the
`local` profile starting plain on 8080 (h2c 200) without certificate files and the default profile
failing by the file's name; `docker compose up --wait` of `app` + `edge` answering `HTTP/2 200`
with HSTS through Caddy.
