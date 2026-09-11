# Blueprint feature → Spring Initializr dependency

Used in step 3 to build the `-d dependencies=` parameter.

| Feature | Initializr id | Notes |
|---|---|---|
| `rest` | `web` | `webflux` if the project is reactive — ask before |
| `persistence-jpa` | `data-jpa` | Also adds the driver: `postgresql`, `mysql`, `h2` |
| `flyway` | `flyway` | Migrations in `db/migration` |
| `messaging-kafka` | `kafka` | |
| `messaging-sqs` | — | Doesn't exist in the Initializr; add `spring-cloud-aws-starter-sqs` by hand |
| `openapi` | — | Doesn't exist in the Initializr; add `springdoc-openapi-starter-webmvc-ui` |
| `testcontainers` | `testcontainers` | Adds the module for the chosen DB |
| `actuator` | `actuator` | |
| `archunit` | — | Not a bootstrap dependency. The `test-architect` skill adds `com.tngtech.archunit:archunit-junit5` (test scope), only in the module with `contains_main: true`, once there are classes to check |
| `validation` | `validation` | **Mandatory whenever `rest` is active.** `api-rest.md` § Errors requires bean validation on the DTO, and without this dependency `@NotBlank`/`@Email`/`@Size` don't compile and the controller's `@Valid` has no processor. Not optional: it's a direct consequence of a rule the same blueprint installs |
| `observability` | — | Doesn't exist in the Initializr; add `io.micrometer:micrometer-tracing-bridge-otel` and `io.opentelemetry:opentelemetry-exporter-otlp`. Without this feature there's no `Tracer` bean, and an error handler that injects it prevents the context from starting — `.claude/rules/observability.md` § Correlation then requires generating the identifier locally and saying so in a comment |
| `uuid-v7` | — | Doesn't exist in the Initializr; add `com.fasterxml.uuid:java-uuid-generator`. Required whenever `persistence-jpa` is active and the primary key is `UUID`: `persistence.md` § Identity and keys requires v7, and JDK 21's `java.util.UUID.randomUUID()` generates v4. See the JDK 25 note below |
| `spring-modulith` | `modulith` | `modular-monolith` blueprint only. Adds `spring-modulith-starter-core` (compile). Also add by hand, same BOM: `spring-modulith-starter-test` (test scope, not in the Initializr catalog) — needed for `ApplicationModules`/`@ApplicationModuleTest`. Import `spring-modulith-bom` in `<dependencyManagement>` with `<scope>import</scope>`, version from `boms.spring-modulith.version` in the same `curl` response, never from memory — it moves independently of Spring Boot's own version |

## Warning — Spring Boot 4 renamed starters

The Initializr `id` above **doesn't change** between Boot versions, but the
`artifactId` recorded in `pom.xml` does. Confirmed in Boot 4 for this template:

- `spring-boot-starter-web` → **`spring-boot-starter-webmvc`**
- `spring-boot-starter-test` is no longer a single artifact: each production starter
  has its own `-test` (`spring-boot-starter-webmvc-test`,
  `spring-boot-starter-actuator-test`, …)
- `springdoc-openapi-starter-webmvc-ui` on the `2.8.x` line is for Boot 3; for Boot 4
  use the `3.x` line (verified: `3.1.0`) — `2.8.x` references classes that no longer
  exist
- `bootVersion` **doesn't accept** the `.RELEASE` suffix (`4.1.1.RELEASE` gives a 404
  on Maven Central); if you pin the version, pass just `4.1.1`. Preferable: don't pin
  and let the Initializr choose the current GA.

**Practical rule:** after `curl`-ing the Initializr, trust the `artifactId` that came
back in the generated `pom.xml`, never the name you have from memory — it may have
changed again.

## Warning — `uuid-v7` closes itself out when the target LTS moves up

JDK 25 generates UUID v7 in `java.util.UUID` itself. As long as the target is 21, the
dependency is what makes `persistence.md`'s requirement executable; when the target
moves up, the feature stops being necessary and the rule starts citing the JDK API
instead. Until then, **don't write a v7 generator by hand**: manipulating RFC 9562's
bits is infrastructure code that needs auditing, a bug in it doesn't break any test
(the `id` is still a valid UUID, it just stops being sortable), and Checkstyle's
`MagicNumber` forces naming around ten constants just for the file to pass the
`validate` phase.

## Warning — after the `curl`, confirm what ended up in `pom.xml`

Three of the features above have no `id` in the Initializr (`openapi`, `observability`,
`uuid-v7`) and one has one but tends to be missing by default (`validation`). After
step 3, confirm each one with `./mvnw dependency:tree` before moving on. A missing
dependency only shows up on the first real feature, when it's already the executor
discovering it — and resolving that in the middle of a use case is making an
infrastructure decision in the wrong place.

## Which module each one goes into

In a multi-module layout, dependencies **don't** all go to the root:

| Dependency | Module |
|---|---|
| `web`, `springdoc` | inbound REST adapter |
| `data-jpa`, driver, `flyway` | outbound persistence adapter |
| `kafka`, `sqs` | messaging adapter |
| `actuator`, `observability` | bootstrap |
| `validation` | wherever validation happens — never in `domain` |
| `uuid-v7` | wherever the id is generated: the application layer, not `domain` |
| `junit`, `assertj` | all, in `test` scope |

`domain` gets none. If you're adding a dependency to `domain`, stop and re-read
`.claude/rules/architecture-ddd.md`.

## Checking what's available

```bash
curl -sS https://start.spring.io/dependencies | head -50
```
