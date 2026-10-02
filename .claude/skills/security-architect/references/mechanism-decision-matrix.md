# Mechanism decision matrix — how a servlet REST API authenticates its callers

Reference for `security-architect`, step 4. Loads only when that skill runs. Norms are not
here: they are `@.claude/rules/authorization.md`, cited by path.

Sources: the Spring Security servlet reference (links in `servlet-reference-links.md`), read
on 2026-10-02. Starter coordinates and versions are never taken from this page — the Boot
parent of the project manages them (`@CLAUDE.md` invariant 8).

## 1. The mechanisms

| Mechanism | Who holds the credentials | Per-request cost | Revocation | Starters | Exemplar |
|---|---|---|---|---|---|
| **Resource server, signed token (JWT)** | An external identity provider (Keycloak, Auth0, Cognito, Entra ID, Okta) | Signature check against a cached key set — no network call per request | At expiry; immediate only with short lifetimes | security + oauth2-resource-server | `JwtResourceServerConfig.java.example` |
| **Resource server, opaque token** | An external identity provider | One introspection call per request (cache with care) | Immediate | security + oauth2-resource-server | `OpaqueTokenResourceServerConfig.java.example` |
| **Users owned by the application** | This application: a credentials table, hashed passwords | One password hash per request (HTTP Basic) — adaptive hashes are slow on purpose | Disable the user | security | `LocalUsersSecurityConfig.java.example` |
| **API key** | This application: a key table, hashed keys | One hash + lookup per request | Revoke the key | security | `ApiKeyAuthenticationConfig.java.example` |
| **Client credentials (machine to machine)** | The identity provider issues the machine a token | Same as the signed-token row | Same | same as signed token | the signed-token exemplar — a machine token is validated exactly like a user token; its scopes become `SCOPE_` authorities |

## 2. Decision — the first row that matches wins

| Question | If yes |
|---|---|
| Is the application reactive (WebFlux)? | **Out of scope** — stop, this skill is servlet only |
| Does an earlier case already use a mechanism for the same kind of caller? | **Inherit it.** A second one is a divergence to ask about |
| Does an identity provider exist or is one planned, and does it issue signed tokens? | **Signed token** |
| Does the provider issue only opaque tokens, or must a token be revocable before it expires? | **Opaque token** — write the per-request introspection cost next to the choice |
| Is the caller a machine that can run an OAuth client-credentials flow? | **Client credentials** — the signed-token path, with scopes |
| Is the caller a machine that cannot (a partner system with a fixed key, a webhook)? | **API key** |
| Does the application itself own the user accounts, with no provider at all? | **Users owned by the application** — HTTP Basic over TLS for an API. Issuing this application's own tokens means running an authorization server, a separate decision outside this matrix |

Two kinds of caller, two rows: a project may end with a signed token for people and an API key
for one partner. That is the only shape of "two mechanisms" `@.claude/rules/authorization.md`
§ Mechanism admits, and each gets its own filter chain, ordered, with a `securityMatcher`.

## 3. Where roles and groups sit — by provider

Asked once (step 3, token shape), written in § 3 of the partial. Common shapes:

| Provider | Roles | Groups | Scopes |
|---|---|---|---|
| Keycloak | `realm_access.roles` (realm) · `resource_access.<client>.roles` (client) | `groups` (with the group mapper on) | `scope` |
| Auth0 | a namespaced custom claim (`https://<ns>/roles`) set by an Action | custom claim | `scope` / `permissions` |
| Amazon Cognito | `cognito:groups` | `cognito:groups` | `scope` |
| Microsoft Entra ID | `roles` (app roles) | `groups` (object ids, overage claim past the limit) | `scp` |
| Okta | `groups` (custom claim) | `groups` | `scp` |

The converter in `JwtResourceServerConfig.java.example` reads one or more of these paths and
applies one prefix per kind: `ROLE_`, `GROUP_`, `SCOPE_`. A provider not in the table is asked
for a decoded sample token — never guessed.

## 4. Rejected on purpose

| Option | Why no exemplar |
|---|---|
| Session + form login | A browser application, not a REST API. Needs CSRF on and a session store; a separate decision |
| Spring Authorization Server inside the API | Turns the API into an identity provider. Its own project, its own decision |
| `WebSecurityConfigurerAdapter`, `authorizeRequests`, `.and()` chains | Removed from the framework; the exemplars use the component-based lambda DSL |
| Roles checked by `if` inside a use case | `@.claude/rules/authorization.md` § Boundary — roles are a request rule; only ownership is the use case's |
