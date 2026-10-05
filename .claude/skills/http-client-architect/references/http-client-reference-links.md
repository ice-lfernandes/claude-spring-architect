# References — outbound HTTP clients

Sources the norm and the exemplars follow. Loads only when `http-client-architect` runs. Where a
source and this page disagree, the source wins; where the official Spring documentation and a blog
disagree, the documentation wins.

## Spring

| Topic | Source |
|---|---|
| The four clients and where each stands (`RestTemplate` schedule) | <https://spring.io/blog/2025/09/30/the-state-of-http-clients-in-spring> |
| HTTP Service Client registry, groups, `@ImportHttpServices` | <https://spring.io/blog/2025/09/23/http-service-client-enhancements/> |
| `RestClient`, HTTP interfaces, `WebClient` | <https://docs.spring.io/spring-framework/reference/integration/rest-clients.html> |
| `@Retryable`, `@ConcurrencyLimit`, `@EnableResilientMethods` | <https://docs.spring.io/spring-framework/reference/7.0/core/resilience.html> |
| `spring.http.clients.*`, `spring.http.serviceclient.*`, engine selection, SSL bundles on clients | <https://docs.spring.io/spring-boot/reference/io/rest-client.html> |
| OAuth2 client, `OAuth2ClientHttpRequestInterceptor`, `@ClientRegistrationId` | <https://docs.spring.io/spring-security/reference/servlet/oauth2/client/authorized-clients.html> |
| SSL bundles | <https://docs.spring.io/spring-boot/reference/features/ssl.html> |
| Client observations (`http.client.requests`) | <https://docs.spring.io/spring-framework/reference/integration/observability.html> |

## Libraries

| Topic | Source |
|---|---|
| Apache HttpClient 5 connection management | <https://hc.apache.org/httpcomponents-client-5.x/> |
| Resilience4j with Spring Boot 4 | <https://resilience4j.readme.io/docs/getting-started-3> |
| OpenAPI Generator, `spring` generator options | <https://openapi-generator.tech/docs/generators/spring/> |
| Spring Cloud OpenFeign (status, configuration) | <https://docs.spring.io/spring-cloud-openfeign/reference/> |
| WireMock + Spring Boot integration | <https://github.com/wiremock/wiremock-spring-boot> |

## Practice

| Topic | Source |
|---|---|
| Timeouts, retries, backoff with jitter | <https://aws.amazon.com/builders-library/timeouts-retries-and-backoff-with-jitter> |
| Deadlines, retry budgets, cascading failures | <https://sre.google/sre-book/addressing-cascading-failures/> |
| Anti-corruption layer | <https://learn.microsoft.com/en-us/azure/architecture/patterns/anti-corruption-layer> |
| Unsafe consumption of APIs (third-party responses as untrusted input) | <https://owasp.org/API-Security/editions/2023/en/0xaa-unsafe-consumption-of-apis/> |
| SSRF | <https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html> |

## RFCs

| Topic | Source |
|---|---|
| Method idempotency, `Retry-After`, status semantics | RFC 9110 |
| Problem Details | RFC 9457 |
| Web linking (`Link: rel="next"`) | RFC 8288 |
| OAuth 2.0 client credentials | RFC 6749 § 4.4 |
| OAuth 2.0 token exchange | RFC 8693 |
| `Idempotency-Key` header (IETF draft — status checked when a partial relies on it) | draft-ietf-httpapi-idempotency-key-header |
