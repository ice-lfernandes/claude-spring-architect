---
paths:
  - "**/*.java"
status: active
---

# Naming — packages, classes, methods, tests

Single principle: the name states the **role**, never the design pattern. No `Impl`
suffix. No generic `execute()`.

## Packages

Lowercase, no underscore, logical role as suffix. The concrete map comes from the
active blueprint's `packages.map` (e.g. hexagonal: `domain.model`, `domain.event`,
`application.port.in`, `application.service`, `adapter.in.rest`,
`adapter.out.persistence`). A blueprint may redefine the map; the principle doesn't
change.

## Classes

| Role | Convention | Example |
|---|---|---|
| Entity, aggregate, value object | Noun | `Order`, `Money` |
| Use case implementation | `<Verb><Noun>Service` | `ConfirmOrderService` |
| REST controller | `<Resource>Controller` | `OrderController` |
| Inbound REST DTO | `<Verb><Resource>Request` | `ConfirmOrderRequest` |
| Outbound REST DTO | `<Resource>Response` | `OrderResponse` |
| Mapper (DTO or JPA entity ↔ domain) | `<Resource>Mapper` | `OrderMapper` |
| JPA entity — name distinct from the aggregate | `<Resource>Entity` | `OrderEntity` |
| Outbound port implementation | `<Resource>RepositoryAdapter` | `OrderRepositoryAdapter` |
| Spring Data interface | `<Resource>JpaRepository` | `OrderJpaRepository` |
| Messaging consumer / producer | `<Event>Listener` / `<Event>Publisher` | `OrderConfirmedListener` |
| Global exception handler | `<Area>ExceptionHandler` | `ApiExceptionHandler` |
| Spring configuration | `<Area>Config` | `SecurityConfig` |

## Methods

- Use case: a specific verb — `ConfirmOrderUseCase.confirm(...)`.
- Boolean predicate: prefix `is`, `has`, or `can` — `isPaid()`.
- Value object or aggregate with invariants: static factory `of`, `from`, or `create`,
  never a public constructor.

## Tests

Class, method, and test data factory names are the rule of
`@.claude/rules/testing.md` § Names and shape and § Test data. This rule doesn't repeat
them: the convention used to be written in both files and diverged — English with a
`should` prefix here, descriptive Portuguese there.
