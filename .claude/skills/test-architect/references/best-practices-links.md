# External sources — testing

Reference for the `test-architect` skill. Each row has the source and what to fetch
from it. Official documentation first; wherever it diverges from any summary, the
official source wins. Wherever it diverges from `@.claude/rules/testing.md`, this
repository's rule wins.

**Freshness warning.** This list was put together on 2026-09-08. URLs change and
versions ship. If a link fails or the cited version is stale, resolve the project's
real version (`./mvnw dependency:tree`) before applying what's written there — never
write versions from memory (`@CLAUDE.md`, invariant 8).

## Official — tools

| Source | Go there for |
|---|---|
| <https://docs.junit.org/current/user-guide/> | Lifecycle, `@ParameterizedTest`, `@Nested`, `assertThrows`, parallel execution |
| <https://assertj.github.io/doc/> | Fluent assertions, `extracting`, recursive comparison, exception assertion |
| <https://site.mockito.org/> | `mock`, `given`/`willThrow`, `verify`, `ArgumentCaptor`, and when **not** to use a double |
| <https://java.testcontainers.org/modules/databases/> | Database container, reuse, lifecycle |
| <https://www.archunit.org/userguide/html/000_Index.html> | Rules API, `ArchTest`, layer rules, freezing violations |
| <https://www.jacoco.org/jacoco/trunk/doc/maven.html> | `prepare-agent`, `check`, `rules`, `limits`, exclusions |

## Official — Spring

| Source | Go there for |
|---|---|
| <https://docs.spring.io/spring-boot/reference/testing/index.html> | Slices, `@SpringBootTest`, test profiles, test configuration |
| <https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html> | Substituting beans with a double (`@MockitoBean`), and what each slice autoconfigures |
| <https://docs.spring.io/spring-framework/reference/testing/spring-mvc-test-framework.html> | `MockMvc`: requests, assertions, `jsonPath` |
| <https://docs.spring.io/spring-boot/reference/testing/testcontainers.html> | `@ServiceConnection`: wires the datasource to the container with no manual config |
| <https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html> | Context caching — because different configurations multiply startup time |

## Secondary reference

| Source | Go there for | Caveat |
|---|---|---|
| <https://martinfowler.com/articles/practical-test-pyramid.html> | Why the base is wide, and what each level costs | Predates Boot's slices; the concrete shape comes from Spring's docs |
| <https://testdesiderata.com/> | Twelve properties of a good test — isolated, deterministic, fast, specific | A list of principles, not recipes |
| <https://martinfowler.com/articles/mocksArentStubs.html> | Difference between a state double and an interaction double, and when each one lies | Vocabulary predates Mockito; translate the terms |
| <https://microsoft.github.io/code-with-engineering-playbook/automated-testing/> | Catalog of test types and anti-patterns | Language-agnostic |

## Resolving versions

Always from the repository, never from memory, and never from `search.maven.org`'s
index, which returns stale versions:

```bash
curl -sS 'https://repo1.maven.org/maven2/com/tngtech/archunit/archunit-junit5/maven-metadata.xml' \
  | grep -o '<release>[^<]*</release>'
```

JUnit, AssertJ, Mockito, Testcontainers, and JaCoCo have their version managed by
`spring-boot-starter-parent`: declare them **without** `<version>`. ArchUnit doesn't —
it's the only one in this list that needs an explicit version.
