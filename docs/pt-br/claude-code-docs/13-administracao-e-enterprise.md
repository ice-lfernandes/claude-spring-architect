# 13 — Administração e enterprise

Páginas oficiais cobertas:

| Página | Link |
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

## Mapa de decisão do administrador

A página [admin-setup](https://code.claude.com/docs/en/admin-setup) organiza a implantação em
quatro decisões: **provedor de API**, **managed settings**, **enforcement de política** e
**monitoramento/dados**.

## Managed settings

Camada de maior precedência. Nada que o desenvolvedor configure a sobrescreve, salvo a lista
de exceções em que o valor **mais restritivo** vence (ver [07](07-settings-permissoes-e-seguranca.md)).

Mecanismos de entrega:

- **Server-managed settings** — buscados do console admin da claude.ai ou de um Claude apps
  gateway self-hosted. É o único mecanismo que alcança sessões cloud.
- **MDM / políticas de SO** e arquivos `managed-settings.json` em diretório de sistema.
- **Host que embute o Claude Code** (por exemplo Claude Desktop), via opção
  `managedSettings` do SDK.

O diretório de managed settings também entrega `CLAUDE.md` organizacional, skills enterprise
(`.claude/skills/`) e subagents gerenciados (`.claude/agents/`).

O que faz sentido enforçar por settings (e não por `CLAUDE.md`):

| Objetivo | Chave |
|----------|-------|
| Bloquear tools, comandos ou caminhos | `permissions.deny` |
| Impor sandbox | `sandbox.enabled` |
| Variáveis de ambiente e roteamento de provedor | `env` |
| Método de login e organização | `forceLoginMethod`, `forceLoginOrgUUID` |
| Travar modelos disponíveis | `availableModels` |
| Impedir bypass/auto | `permissions.disableBypassPermissionsMode`, `permissions.disableAutoMode` |
| Restringir customização a plugins | `strictPluginOnlyCustomization` |
| Só regras de permissão gerenciadas | `allowManagedPermissionRulesOnly` |

Verificação: `/status` mostra a fonte gerenciada ativa; entradas inválidas em managed
settings são descartadas individualmente, com fallback para o valor mais estrito.

## MCP gerenciado

`managedMcpServers` fornece servidores a todos os usuários e rankeia **acima** de local,
project e user. Também é possível restringir quais servidores usuários podem adicionar ou
conectar.

## Auto mode gerenciado

`auto-mode-config` define o contexto do classificador: repositórios, buckets e domínios
confiáveis pela organização, além de sobrescrever as regras padrão de bloqueio e permissão.
`claude auto-mode defaults` imprime as regras embutidas; `/auto-mode-setup` ajuda a redigir
entradas de `autoMode.environment`.

## Provedores e deployment

- **Anthropic API** (padrão), **Amazon Bedrock** (ARN de inference profile),
  **Claude Platform on AWS** (API operada pela Anthropic com autenticação AWS e billing via
  AWS Marketplace), **Google Cloud Agent Platform** (ex-Vertex AI), **Microsoft Foundry**
  (nome de deployment).
- [Feature availability](https://code.claude.com/docs/en/feature-availability) compara quais
  features existem em cada plano e provedor — consulte antes de prometer uma feature ao time.
- **Rede corporativa:** proxy, CA customizada e mTLS. **Corporate launcher:** roteia todos os
  processos que o Claude Code inicia (inclusive o serviço de background e sessões de agent
  view) por um binário exigido pela empresa.
- **Dev containers** para ambiente reproduzível.
- **Self-hosted environments:** sessões cloud rodando em infraestrutura sua, com runner
  (`claude self-hosted-runner`), configuração de sessão, testes ponta a ponta e verificação
  de identidade.

## Gateways

- **Claude apps gateway** (`claude gateway`): gateway da Anthropic para Bedrock, Claude
  Platform on AWS, Google Cloud e Microsoft Foundry, com SSO/OIDC, store em Postgres, limites
  de gasto por desenvolvedor (dia/semana/mês) e guias de deploy em AWS e GCP.
- **Outros LLM gateways**: como conectar (`ANTHROPIC_BASE_URL` e credencial), como distribuir
  para a organização e um guia de compatibilidade de protocolo (endpoints chamados, headers e
  campos de body que precisam ser encaminhados).

Lembre: `ANTHROPIC_BASE_URL` muda o destino da requisição, não o modelo. Tool search é
desligada automaticamente quando a base URL não é first-party.

## Custos e monitoramento

- **Costs:** rastrear tokens, limites de gasto de time e táticas de redução — gestão de
  contexto, escolha de modelo, configurações de extended thinking e hooks de
  pré-processamento.
- **Monitoring:** OpenTelemetry (traces, métricas e eventos) para o backend de observabilidade
  da empresa.
- **Analytics:** dashboard de adoção e velocidade de engenharia.
- Na sessão: `/usage` (custo, limites de plano, estatísticas), `/context` (uso da janela),
  status line com campos de custo e cache.

## Segurança e dados

- **Security:** arquitetura baseada em permissões, proteções embutidas, prompt injection e
  responsabilidades do usuário.
- **Data usage:** políticas de uso de dados da Anthropic.
- **Zero data retention (ZDR):** disponível para contas qualificadas no Claude for
  Enterprise; a página lista o escopo e **quais features ficam desabilitadas** sob ZDR.

## Adoção

**Communications kit** (anúncios de lançamento, campanha de mensagens, FAQ) e
**Champion kit** para conduzir o rollout na organização.
