---
paths:
  - "**/src/main/resources/application*.yml"
  - "**/src/main/resources/application*.properties"
  - "**/Dockerfile*"
  - "docker-compose*.yml"
  - "docker/caddy/**"
status: active
---

# Transport security — TLS, HTTP/2 and HSTS on the way in

How a request reaches this service: where TLS terminates, which certificate it presents, which
protocol versions it speaks, and what the response tells a browser about the next visit. Decided
once per project, before any endpoint exists, because every endpoint inherits it. Calls this
service makes to others are `@.claude/rules/http-client.md` § Security; this norm covers the
inbound side only.

## Topology

- **Where TLS terminates is a recorded project fact**, one of four:

  | Topology | TLS terminates at | The application holds |
  |---|---|---|
  | A · edge | an ingress, load balancer or reverse proxy in front of the application | no key; trusts forwarded headers from the edge only |
  | B · direct | the embedded server | the certificate and key, loaded from files mounted at run time |
  | C · mutual TLS | the embedded server, which also requires a client certificate | B, plus a trust store with only the private CA that issues client certificates |
  | C′ · service mesh | the mesh sidecar | nothing — the application is configured as in A |

- **A is the default.** B is chosen for a reason written next to the fact: no proxy in front, a
  compliance requirement for TLS up to the process, a client that reaches the process directly
- C is a decision about who the callers are, not about transport alone. A missing or untrusted
  client certificate fails the TLS handshake before any application code runs: the caller sees a
  TLS error, never a 401 with a body
- A certificate from a public CA is not a client certificate: public CAs stopped issuing the
  client-authentication extended key usage in 2026

## Behind an edge (A, C′)

- **The forwarded headers are honoured** (`server.forward-headers-strategy: native`, or
  `framework` when the edge sends the RFC 7239 `Forwarded` header). Without it the application
  believes every request arrived over plain HTTP: HSTS is never written, an HTTPS redirect loops,
  and every absolute URL it builds starts with `http://` — none of it with an error
- **Only the edge is trusted to send them.** The trusted proxy list
  (`server.tomcat.remoteip.internal-proxies`) names the edge's network, from configuration, per
  environment. Never empty, never "any address": a forwarded header from anyone else is a forged
  client address and a forged scheme
- `server.tomcat.redirect-context-root: false`, so a redirect issued by the server itself honours
  the forwarded scheme
- The edge forwards to the application over HTTP/2 cleartext (h2c) when it supports it

## Direct (B, C)

- **The certificate is configured only through an SSL bundle** (`spring.ssl.bundle.*`, referenced
  by `server.ssl.bundle`). Never the discrete `server.ssl.key-store*` / `server.ssl.certificate*`
  properties: they cannot be combined with a bundle, and with a bundle in place
  `server.ssl.ciphers` and `server.ssl.enabled-protocols` are ignored without a warning — the
  bundle's `options` hold them
- **The key and certificate files are mounted at run time** — a volume, a platform secret mounted
  as a directory, the renewal client's output directory — and their paths come from the
  environment. They are never in the repository, never in the image
  (`@.claude/rules/secrets.md`)
- **The bundle reloads on change** (`reload-on-update: true`), so a renewed certificate is served
  without a restart. A server that cannot reload (Jetty) is not used with B. A platform secret is
  mounted as a whole directory, never by `subPath`: a `subPath` file is never updated, and the
  certificate expires with the application running
- `verify-keys: true`, so a key that does not match its certificate fails the startup instead of
  the first handshake
- A PEM private key is PKCS#8 (`BEGIN PRIVATE KEY`). A PKCS#1 key (`BEGIN RSA PRIVATE KEY`) is
  converted before it is mounted
- Bundle names are lower case: set through an environment variable, a name is lower-cased, and a
  bundle written in another case is then "not found"
- **The TLS configuration is active in every profile except the ones that run on a developer
  machine or in a test that does not ask for it**, and is selected by a profile expression on the
  configuration document — never by a profile production has to remember to activate. A bundle
  whose file is missing fails the startup even when the server's TLS is disabled, so the bundle
  itself lives inside that document
- An API does not listen on plain HTTP in production. A redirect from HTTP only protects the
  second request; the first one already crossed the network in clear, credentials included

## Protocols

- **TLS 1.3 and 1.2, nothing older** (`enabled-protocols: TLSv1.3,TLSv1.2` on the bundle, or the
  edge's equivalent)
- **Cipher suites are the JDK's defaults**, which every quarterly update revises. A fixed list
  ages; it is written only when an audit requires one, with the requirement recorded next to it
- **HTTP/2 is on** (`server.http2.enabled: true`): h2 over ALPN with TLS, h2c without
- The server's HTTP/2 abuse limits (overhead and reset counters) keep their defaults — they are
  the defence against rapid-reset attacks. A change to stream concurrency is made for a measured
  reason recorded next to the value
- The server version stays the one the framework's dependency management selects: HTTP/2 denial
  of service is fixed there

## HSTS

- **The application writes `Strict-Transport-Security`, in every topology**, with a `max-age` of
  at least one year and `includeSubDomains`. The edge does not write it — one writer, so the
  value is the one the tests prove
- The header is written only on a request the application sees as secure. Behind an edge that is
  true only with the forwarded headers above
- **Exactly one component writes it.** When the security filter chain exists, its header writer
  is the owner. Without one, a single servlet filter writes the same value and stands aside the
  moment the security framework is on the classpath: the chain's writer skips the header when it
  is already present, so a filter that stayed would silently override the chain's configuration
- `preload` is a recorded decision: once a domain is on the browsers' preload list, leaving it
  takes months

## Certificates and monitoring

- **Renewal is automatic**, by ACME or by the platform. Public certificates are valid for at most
  200 days since 2026-03-15, 100 days from 2027-03-15 and 47 days from 2029-03-15; a manual
  renewal is an outage on a calendar
- The `ssl` health indicator warns inside a window of at least twice the renewal interval
  (`management.health.ssl.certificate-validity-warning-threshold`)
- **The `ssl` indicator is not part of the readiness group.** It reports `OUT_OF_SERVICE` for an
  invalid certificate, and in readiness one expired certificate removes every replica at once.
  Alerting reads the detailed health; probes read `/actuator/health/readiness` and
  `/actuator/health/liveness`, never the aggregate

## Tests

Transport is proven against a real server on a random port — a mock request never opens a
socket, never negotiates TLS, and never runs the server's forwarded-header handling.

| Topology | Case | Proves |
|---|---|---|
| A, C′ | A request carrying `X-Forwarded-Proto: https` from a trusted proxy address | It is secure: HSTS is written |
| A, C′ | The same request from an address outside the trusted list | The forwarded scheme is ignored: no HSTS |
| A, C′ | A cleartext HTTP/2 request | The server speaks h2c |
| B, C | A TLS 1.3 client | HTTP/2 over ALPN, TLS 1.3 negotiated, HSTS written |
| B, C | A client limited to TLS 1.2 | Accepted — the protocol list is the one configured |

The test certificate is generated when the test runs, valid for a day, for `localhost` only. No
certificate or key file is committed for a test (`@.claude/rules/secrets.md`).

## How to verify

```bash
# No discrete key-store properties next to a bundle. Zero lines is the expected result.
grep -rnE "server\.ssl\.(key-store|certificate|ciphers|enabled-protocols)|key-store:" src/main/resources/

# Behind an edge, the forwarded headers are honoured and the trusted list is set.
grep -rnE "forward-headers-strategy|internal-proxies" src/main/resources/

# HTTP/2 is on.
grep -rn "http2" src/main/resources/

# The ssl indicator is not in readiness. Zero lines is the expected result.
grep -rnA3 "readiness:" src/main/resources/ | grep -n "ssl"

# No HSTS written by the edge. Zero lines is the expected result.
grep -rni "strict-transport-security" docker/ 2>/dev/null
```
