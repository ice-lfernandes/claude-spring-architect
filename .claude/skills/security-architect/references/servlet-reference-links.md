# External sources — Spring Security on the servlet stack

Reference for `security-architect` and for whoever implements its partial. Each row has the
source and what to fetch from it. Official documentation first; where it diverges from any
summary, including this one, the official source wins.

**Freshness warning.** Read on 2026-10-02, against the Spring Security generation the Boot
parent then managed. URLs move and APIs change between majors — the last one removed
`and()`, `authorizeRequests` and the Ant/MVC request matchers. Resolve the project's real
versions (`./mvnw dependency:tree`) before applying anything here, and never write a version
from memory (`@CLAUDE.md`, invariant 8).

## The links the exemplars follow

| Source | Go there for |
|---|---|
| <https://spring.io/projects/spring-security#overview> | Current generation, support timeline |
| <https://docs.spring.io/spring-security/reference/index.html> | Entry point; *What's New* and the migration guide of the current major |
| <https://docs.spring.io/spring-security/reference/servlet/index.html> | Everything servlet — the scope of this skill |

## Servlet reference, by topic

| Topic | Page under `https://docs.spring.io/spring-security/reference/servlet/` | Exemplar |
|---|---|---|
| Filter chain architecture, `AuthenticationFilter`, `SecurityContextHolderStrategy` | `architecture.html` | `SecurityConfig`, `ApiKeyAuthenticationConfig` |
| Request authorization, matchers, ordering | `authorization/authorize-http-requests.html` | `SecurityConfig` |
| Method security, meta-annotation templates, `@AuthenticationPrincipal` | `authorization/method-security.html` | `MethodSecurityAnnotations`, `CurrentActorResolution` |
| Authorization events | `authorization/events.html` | `SecurityAuditListener` |
| Authentication events | `authentication/events.html` | `SecurityAuditListener` |
| Session management, stateless | `authentication/session-management.html` | `SecurityConfig` |
| Passwords, `UserDetailsService`, `DaoAuthenticationProvider` | `authentication/passwords/index.html` | `LocalUsersSecurityConfig` |
| Resource server, signed token | `oauth2/resource-server/jwt.html` | `JwtResourceServerConfig` |
| Resource server, opaque token | `oauth2/resource-server/opaque-token.html` | `OpaqueTokenResourceServerConfig` |
| Bearer token resolution and errors | `oauth2/resource-server/bearer-tokens.html` | `ProblemDetailSecurityHandlers` |
| CSRF — when disabling is legitimate | `exploits/csrf.html` | `SecurityConfig` |
| Security headers | `exploits/headers.html` | `SecurityConfig` |
| CORS | `integrations/cors.html` | `SecurityConfig` |
| MockMvc test support (`jwt()`, `opaqueToken()`, `httpBasic()`) | `test/mockmvc/index.html` | `test-architect/templates/SecuredControllerTest.java.example` |

## Boot side

| Source | Go there for |
|---|---|
| <https://docs.spring.io/spring-boot/reference/web/spring-security.html> | What Boot auto-configures and when it backs off |
| <https://docs.spring.io/spring-boot/reference/security/oauth2.html> | `spring.security.oauth2.resourceserver.*` properties, audiences |
| <https://docs.spring.io/spring-boot/reference/actuator/endpoints.html> | Securing actuator endpoints, `EndpointRequest` |
| <https://start.spring.io> (dependency ids `security`, `oauth2-resource-server`) | The starter artifacts for the project's Boot version — their names changed between majors |

## Normative

| Source | Go there for |
|---|---|
| <https://www.rfc-editor.org/rfc/rfc6750> | Bearer tokens, the `WWW-Authenticate` challenge |
| <https://www.rfc-editor.org/rfc/rfc7662> | Token introspection |
| <https://www.rfc-editor.org/rfc/rfc9068> | JWT profile for access tokens — audience, issuer |
| <https://www.rfc-editor.org/rfc/rfc9457> | Problem Details for the 401 and 403 bodies |
| <https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html> | REST security checklist |
| <https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html> | Password hashing |
