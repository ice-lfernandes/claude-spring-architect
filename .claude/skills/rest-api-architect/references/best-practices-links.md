# External sources — HTTP, REST, and OpenAPI

Reference for the `rest-api-architect` skill. Each row has the source and what to fetch
from it. Official documentation and RFCs first; wherever it diverges from any summary,
the official source wins.

**Freshness warning.** This list was put together on 2026-09-07. URLs change and
versions ship. If a link fails or the cited version is stale, resolve the project's real
version (`./mvnw dependency:tree`) before applying what's written there — never write
versions from memory (`@CLAUDE.md`, invariant 8).

## Normative — RFC

| Source | Go there for |
|---|---|
| <https://www.rfc-editor.org/rfc/rfc9110> | HTTP semantics: the exact meaning of each method and each status |
| <https://www.rfc-editor.org/rfc/rfc9457> | Problem Details. Supersedes RFC 7807, which is still cited everywhere |
| <https://www.rfc-editor.org/rfc/rfc7386> | JSON Merge Patch — the format the rule requires on `PATCH` |
| <https://www.rfc-editor.org/rfc/rfc9111> | HTTP caching: `ETag`, `If-Match`, revalidation |

## Official — Spring and OpenAPI

| Source | Go there for |
|---|---|
| <https://docs.spring.io/spring-framework/reference/web/webmvc.html> | Controllers, method arguments, content negotiation |
| <https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html> | `ProblemDetail`, `ErrorResponseException`, `@RestControllerAdvice` |
| <https://docs.spring.io/spring-boot/reference/web/servlet.html> | Web autoconfiguration, filters, interceptors, default error handling |
| <https://springdoc.org/> | Annotations, `openapi.json` generation, Boot integration. **Compatibility with the Boot version changes every major — confirm before pinning** |
| <https://spec.openapis.org/oas/latest.html> | The spec itself. Ultimate source for what `@Operation` and `@Schema` produce |
| <https://docs.spring.io/spring-boot/reference/actuator/tracing.html> | Micrometer Tracing: bridge, `Tracer`, `traceId` propagation |
| <https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-framework.html> | `MockMvc` for the contract tests |

## Secondary reference

| Source | Go there for | Caveat |
|---|---|---|
| <https://opensource.zalando.com/restful-api-guidelines/> | Complete, well-reasoned API guide: pagination, versioning, names | It's one company's opinion; wherever it diverges from `@.claude/rules/api-rest.md`, the rule wins |
| <https://cloud.google.com/apis/design> | Resource design, non-CRUD actions, field names | Assumes gRPC in several sections; filter accordingly |
| <https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2> | The exact definition of idempotent — the one the rule's verb table uses | — |

## Resolving the springdoc version

Not in the Spring Boot BOM, so the version is explicit and never written from memory:

```bash
curl -sS 'https://repo1.maven.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/maven-metadata.xml' \
  | grep -o '<release>[^<]*</release>'
```

The oracle is `maven-metadata.xml` from `repo1.maven.org` — the repository itself.
Don't use `search.maven.org`'s `solrsearch`: it's a separate index and returns stale
versions.

Confirm in <https://springdoc.org/>'s compatibility matrix which of these versions
matches the project's Boot major. No network, ask the user — don't guess.
