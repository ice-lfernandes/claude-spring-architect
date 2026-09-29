# 13 — Administration and enterprise

Official pages covered:

| Page | Link |
|--------|------|
| Set up Claude Code for your organization | <https://code.claude.com/docs/en/admin-setup> |
| Advanced setup | <https://code.claude.com/docs/en/setup> |
| Authentication | <https://code.claude.com/docs/en/authentication> |
| Deploy managed settings | <https://code.claude.com/docs/en/managed-settings> |
| Configure server-managed settings | <https://code.claude.com/docs/en/server-managed-settings> |
| Control MCP server access | <https://code.claude.com/docs/en/managed-mcp> |
| Configure auto mode | <https://code.claude.com/docs/en/auto-mode-config> |
| Enterprise deployment overview | <https://code.claude.com/docs/en/third-party-integrations> |
| Feature availability | <https://code.claude.com/docs/en/feature-availability> |
| Amazon Bedrock | <https://code.claude.com/docs/en/amazon-bedrock> |
| Claude Platform on AWS | <https://code.claude.com/docs/en/claude-platform-on-aws> |
| Google Cloud Agent Platform | <https://code.claude.com/docs/en/google-vertex-ai> |
| Microsoft Foundry | <https://code.claude.com/docs/en/microsoft-foundry> |
| Enterprise network configuration | <https://code.claude.com/docs/en/network-config> |
| Corporate launcher | <https://code.claude.com/docs/en/corporate-launcher> |
| Development containers | <https://code.claude.com/docs/en/devcontainer> |
| Run Claude Code through a gateway | <https://code.claude.com/docs/en/gateways> |
| Claude apps gateway | <https://code.claude.com/docs/en/claude-apps-gateway> |
| Other LLM gateways | <https://code.claude.com/docs/en/llm-gateway> |
| Monitoring (OpenTelemetry) | <https://code.claude.com/docs/en/monitoring-usage> |
| Manage costs effectively | <https://code.claude.com/docs/en/costs> |
| Track team usage with analytics | <https://code.claude.com/docs/en/analytics> |
| Security | <https://code.claude.com/docs/en/security> |
| Data usage | <https://code.claude.com/docs/en/data-usage> |
| Zero data retention | <https://code.claude.com/docs/en/zero-data-retention> |
| Self-hosted environments | <https://code.claude.com/docs/en/self-hosted-environments> |
| Communications kit | <https://code.claude.com/docs/en/communications-kit> |
| Champion kit | <https://code.claude.com/docs/en/champion-kit> |

---

## Administrator decision map

The [admin-setup](https://code.claude.com/docs/en/admin-setup) page organizes deployment into
four decisions: **API provider**, **managed settings**, **policy enforcement** and
**monitoring/data**.

## Managed settings

Highest-precedence layer. Nothing a developer configures overrides it, except the list of
exceptions where the **most restrictive** value wins (see
[07](07-settings-permissions-and-security.md)).

Delivery mechanisms:

- **Server-managed settings** — fetched from the claude.ai admin console or from a
  self-hosted Claude apps gateway. It is the only mechanism that reaches cloud sessions.
- **MDM / OS policies** and `managed-settings.json` files in a system directory.
- **Host that embeds Claude Code** (for example Claude Desktop), via the SDK's
  `managedSettings` option.

The managed settings directory also delivers organizational `CLAUDE.md`, enterprise skills
(`.claude/skills/`) and managed subagents (`.claude/agents/`).

What makes sense to enforce through settings (and not through `CLAUDE.md`):

| Goal | Key |
|----------|-------|
| Block tools, commands or paths | `permissions.deny` |
| Impose sandbox | `sandbox.enabled` |
| Environment variables and provider routing | `env` |
| Login method and organization | `forceLoginMethod`, `forceLoginOrgUUID` |
| Lock available models | `availableModels` |
| Prevent bypass/auto | `permissions.disableBypassPermissionsMode`, `permissions.disableAutoMode` |
| Restrict customization to plugins | `strictPluginOnlyCustomization` |
| Managed permission rules only | `allowManagedPermissionRulesOnly` |

Verification: `/status` shows the active managed source; invalid entries in managed settings
are discarded individually, falling back to the strictest value.

## Managed MCP

`managedMcpServers` provides servers to all users and ranks **above** local, project and
user. It is also possible to restrict which servers users can add or connect.

## Managed auto mode

`auto-mode-config` defines the classifier's context: repositories, buckets and domains the
organization trusts, plus overriding the default block and allow rules.
`claude auto-mode defaults` prints the built-in rules; `/auto-mode-setup` helps draft
`autoMode.environment` entries.

## Providers and deployment

- **Anthropic API** (default), **Amazon Bedrock** (inference profile ARN),
  **Claude Platform on AWS** (Anthropic-operated API with AWS authentication and billing via
  AWS Marketplace), **Google Cloud Agent Platform** (formerly Vertex AI), **Microsoft
  Foundry** (deployment name).
- [Feature availability](https://code.claude.com/docs/en/feature-availability) compares which
  features exist in each plan and provider — check it before promising a feature to the team.
- **Corporate network:** proxy, custom CA and mTLS. **Corporate launcher:** routes every
  process Claude Code starts (including the background service and agent view sessions)
  through a binary the company requires.
- **Dev containers** for a reproducible environment.
- **Self-hosted environments:** cloud sessions running on your own infrastructure, with a
  runner (`claude self-hosted-runner`), session configuration, end-to-end tests and identity
  verification.

## Gateways

- **Claude apps gateway** (`claude gateway`): Anthropic's gateway for Bedrock, Claude
  Platform on AWS, Google Cloud and Microsoft Foundry, with SSO/OIDC, a Postgres store,
  per-developer spend limits (day/week/month) and deploy guides for AWS and GCP.
- **Other LLM gateways**: how to connect (`ANTHROPIC_BASE_URL` and credential), how to
  distribute to the organization, and a protocol compatibility guide (endpoints called,
  headers and body fields that must be forwarded).

Remember: `ANTHROPIC_BASE_URL` changes the request destination, not the model. Tool search is
turned off automatically when the base URL is not first-party.

## Costs and monitoring

- **Costs:** tracking tokens, team spend limits and reduction tactics — context management,
  model choice, extended thinking settings and preprocessing hooks.
- **Monitoring:** OpenTelemetry (traces, metrics and events) to the company's observability
  backend.
- **Analytics:** dashboard of adoption and engineering velocity.
- In the session: `/usage` (cost, plan limits, statistics), `/context` (window usage), status
  line with cost and cache fields.

## Security and data

- **Security:** permission-based architecture, built-in protections, prompt injection and
  user responsibilities.
- **Data usage:** Anthropic's data usage policies.
- **Zero data retention (ZDR):** available for qualifying Claude for Enterprise accounts; the
  page lists the scope and **which features are disabled** under ZDR.

## Adoption

**Communications kit** (launch announcements, messaging campaign, FAQ) and **Champion kit**
to drive the rollout in the organization.
