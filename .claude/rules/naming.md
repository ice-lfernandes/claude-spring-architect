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

## Architecture vocabulary

The names of the use case, its ports, and their implementations change with the
architecture — a concrete `<Verb><Noun>UseCase` in one, a `<Verb><Noun>UseCase` port
implemented by `<Verb><Noun>Service` in another, a `<Verb><Noun>Handler` in a third. They
belong to the active blueprint's naming convention, not to this file. Where that
convention and the table above disagree, the convention wins.

<!-- Written at generation time: the active blueprint's naming convention, verbatim.
     Empty in any copy that isn't a generated project. -->

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
