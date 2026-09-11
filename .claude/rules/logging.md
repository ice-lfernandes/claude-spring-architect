---
paths:
  - "**/*.java"
status: active
---

# Logging — format, level, and per-class-type content

Single principle: **a log line is the only witness left once the debugger is gone.**
Whoever reads it later — an on-call engineer, an audit, a postmortem — reconstructs what
happened from the line alone, not from re-running the request.

## Why this is a rule and not part of `observability.md`

Both used to live in `observability.md`. Splitting them fixes an ownership overlap: this
file owns what gets written and how; `@.claude/rules/observability.md` owns which vendor
mechanism supplies the correlation identifier's value and how metrics/tracing
integration is annotated. A rule about log content that also decided the tracing
vendor's wiring was two topics wearing one name.

## `logback.xml`

Every project ships a `logback-spring.xml`, present from the first commit, in
`src/main/resources` of the module that gets packaged — **not** at the project root:
logback only resolves its config from the classpath root, and a root-level file is
never on it. One file, no per-module copy. `logback-spring.xml`, not plain
`logback.xml`, so Spring Boot's own profile-aware initialization is what loads it.
Default pattern, console appender:

```
%d{ISO8601} %-5level [traceId=%X{traceId}] %logger{36} - %msg%n
```

`traceId` is an MDC key, populated per `@.claude/rules/observability.md`'s decision on
where the value comes from — this file doesn't decide that, only that the field exists
and where it sits in the line.

No JSON encoder by default. Until the project declares a structured-logging dependency,
the pattern above is the contract; adding one means updating the dependency and this
line in the same change, not silently diverging from it.

## Format and level

- One event per line, structured, with the correlation identifier as its own field —
  never interpolated into the middle of the message.
- Level, by business meaning: `ERROR` only for what requires human action; `WARN` for
  recovered degradation or an expected business rejection; `INFO` for a business state
  transition. An expected failure translated into the client's response is `WARN` or
  `INFO`, never `ERROR`.
- The exception enters the log as an exception, with the stack, not as concatenated
  text — except where `@.claude/rules/error-handling.md` says otherwise for a typed
  business exception (no stack, `errorCode` as its own field).
- **Zero sensitive data in the log**: credentials, tokens, cards, personal documents.
  Where the value identifies the record, log the technical key, not the data.
- Logging inside the domain is a smell. The domain throws and returns; the adapter or
  application service is the one that logs, because it's the one that knows what the
  event means to the outside.

## Per class type

What each layer logs, so "log something" doesn't turn into copy-pasted judgment calls
per class. Names per `@.claude/rules/naming.md` and the active blueprint's
`packages.map`.

| Class type | What it logs | Level |
|---|---|---|
| `<Resource>Controller` (inbound REST) | method + path, response status, duration | `INFO` on success; `WARN`/`ERROR` per `error-handling.md`'s exception-family mapping on failure |
| `<Verb><Noun>UseCase` / `<Verb><Noun>Service` (inbound port + its implementation) | one line per business state transition (`from → to`, not the full payload) | `INFO` |
| `<Resource>RepositoryAdapter` (outbound persistence) | query identifier, duration | `WARN` when the duration crosses `@.claude/rules/persistence.md`'s slow-query threshold, otherwise not logged |
| domain (aggregate, value object, domain service) | nothing | — always a smell, see § Format and level |
| outbound integration adapter (external HTTP/gateway client) | outcome (success/failure) and latency, never the raw request/response payload | `INFO` on success, `WARN` on a failure the caller recovers from |
| inbound event/message listener | event or message type, correlation identifier, outcome | `INFO` |

The inbound port (`UseCase`) is an interface — the log line physically lives in the
implementing `Service`, since interfaces have no method body. Listed together because
they're one decision (what this use case logs at its own boundary), not two.

## Admitted exception

A service without an inbound adapter — message processor, scheduled task — has no HTTP
response to attach the identifier to, but the general log rule above still applies:
one line per business transition, correlation identifier as its own field, using the
message's or the execution's identifier in place of the response's. Where that
identifier comes from is `observability.md`'s decision, not this file's.

## How to verify

No mechanical check today — this is a `Review`-verified rule, same class as
`naming.md` and `value-objects.md`. The one part that's structurally checkable — no
`org.slf4j.Logger` field in the domain package — isn't enforced by an ArchUnit rule yet;
adding one is `test-architect`'s call, not this file's.
