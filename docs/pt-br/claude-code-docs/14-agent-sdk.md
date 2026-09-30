# 14 — Agent SDK

Páginas oficiais cobertas:

| Página | Link |
|--------|------|
| Agent SDK overview | <https://code.claude.com/docs/en/agent-sdk/overview> |
| Quickstart | <https://code.claude.com/docs/en/agent-sdk/quickstart> |
| Migrate to Claude Agent SDK | <https://code.claude.com/docs/en/agent-sdk/migration-guide> |
| Troubleshoot the Agent SDK | <https://code.claude.com/docs/en/agent-sdk/troubleshooting> |
| Configure your agent | <https://code.claude.com/docs/en/agent-sdk/configuration> |
| Examples | <https://code.claude.com/docs/en/agent-sdk/examples> |
| How the agent loop works | <https://code.claude.com/docs/en/agent-sdk/agent-loop> |
| Use Claude Code features in the SDK | <https://code.claude.com/docs/en/agent-sdk/claude-code-features> |
| Work with sessions | <https://code.claude.com/docs/en/agent-sdk/sessions> |
| Persist sessions to external storage | <https://code.claude.com/docs/en/agent-sdk/session-storage> |
| Streaming input | <https://code.claude.com/docs/en/agent-sdk/streaming-vs-single-mode> |
| Handle approvals and user input | <https://code.claude.com/docs/en/agent-sdk/user-input> |
| Stream responses in real-time | <https://code.claude.com/docs/en/agent-sdk/streaming-output> |
| Get structured output from agents | <https://code.claude.com/docs/en/agent-sdk/structured-outputs> |
| Give Claude custom tools | <https://code.claude.com/docs/en/agent-sdk/custom-tools> |
| Connect to external tools with MCP | <https://code.claude.com/docs/en/agent-sdk/mcp> |
| Scale to many tools with tool search | <https://code.claude.com/docs/en/agent-sdk/tool-search> |
| Subagents in the SDK | <https://code.claude.com/docs/en/agent-sdk/subagents> |
| Modifying system prompts | <https://code.claude.com/docs/en/agent-sdk/modifying-system-prompts> |
| Extend agents with skills | <https://code.claude.com/docs/en/agent-sdk/skills> |
| Plugins in the SDK | <https://code.claude.com/docs/en/agent-sdk/plugins> |
| Configure permissions | <https://code.claude.com/docs/en/agent-sdk/permissions> |
| Intercept behavior with hooks | <https://code.claude.com/docs/en/agent-sdk/hooks> |
| Rewind file changes with checkpointing | <https://code.claude.com/docs/en/agent-sdk/file-checkpointing> |
| Track cost and usage | <https://code.claude.com/docs/en/agent-sdk/cost-tracking> |
| Observability with OpenTelemetry | <https://code.claude.com/docs/en/agent-sdk/observability> |
| Track todos | <https://code.claude.com/docs/en/agent-sdk/todo-tracking> |
| Hosting the Agent SDK | <https://code.claude.com/docs/en/agent-sdk/hosting> |
| Securely deploying AI agents | <https://code.claude.com/docs/en/agent-sdk/secure-deployment> |
| Reference TypeScript | <https://code.claude.com/docs/en/agent-sdk/typescript> |
| Reference Python | <https://code.claude.com/docs/en/agent-sdk/python> |

---

## O que é

O Agent SDK expõe o Claude Code **como biblioteca**, para construir agentes de produção com
as mesmas tools, harness e capacidades — com controle total sobre orquestração, acesso a
tools e permissões. Disponível em TypeScript e Python.

Arquitetura: o SDK roda a CLI do Claude Code como subprocesso. Isso explica boa parte do
troubleshooting (CLI que não inicia, processo que sai, resultado sem saída estruturada).

## Conceitos centrais

- **Agent loop** — mesmo ciclo de mensagens, execução de tools e gestão de contexto da CLI.
- **Sessions** — persistem o histórico; `continue`, `resume` e `fork` para retomar runs
  anteriores. Dá para espelhar transcripts para storage próprio (object store, key-value,
  banco) e permitir que outro host retome a sessão.
- **Features do Claude Code no SDK** — instruções de projeto (`CLAUDE.md`), skills, hooks e
  plugins carregam no SDK; `settingSources` (equivalente a `--setting-sources`) controla
  quais fontes de settings do filesystem entram.

Defaults que diferem da CLI interativa: fork mode **desligado**, agentes built-in
desligáveis por `CLAUDE_AGENT_SDK_DISABLE_BUILTIN_AGENTS=1`, e `PermissionRequest` só existe
quando o host fornece o callback `canUseTool`.

## Entrada e saída

- **Streaming input** vs. modo single: dois modos de entrada, com casos de uso distintos.
- **Streaming output**: texto e tool calls em tempo real.
- **Structured outputs**: JSON validado por JSON Schema, Zod ou Pydantic após o uso de tools
  (equivalente ao `--json-schema` da CLI).
- **User input e approvals**: como expor os pedidos de aprovação e as perguntas do Claude
  para o usuário final e devolver as decisões ao SDK.

## Extensão com tools

- **Custom tools** via servidor MCP *in-process*: suas funções e APIs viram tools do agente.
- **MCP externo**: transportes, autenticação, tratamento de erro.
- **Tool search**: escala para milhares de tools carregando só as necessárias.
- **Subagents no SDK**: isolam contexto, rodam em paralelo e aplicam instruções
  especializadas; definíveis programaticamente (equivalente ao `--agents` da CLI).

## Customização de comportamento

- **System prompt**: escolher entre o preset `claude_code` e um prompt próprio; customizar
  via `CLAUDE.md`, output styles, `append`, ou substituição completa.
- **Skills**: controlar quais skills o agente pode invocar, despachar comandos por nome e
  escrever skills que as sessões descobrem.
- **Plugins**: carregar plugins para adicionar skills, agents, hooks e MCP servers.

## Controle e observabilidade

- **Permissões**: permission modes, hooks e regras allow/deny declarativas — a mesma
  semântica documentada em [07](07-settings-permissoes-e-seguranca.md).
- **Hooks**: interceptam o comportamento nos mesmos pontos de lifecycle.
- **File checkpointing**: rastreia mudanças de arquivo e restaura estados anteriores.
- **Cost tracking**: uso de tokens, estimativa de custo e configuração de prompt caching.
- **OpenTelemetry**: traces, métricas e eventos para o backend de observabilidade.
- **Todo tracking**: renderizar o progresso do Claude na sua aplicação a partir das tool
  calls estruturadas.

## Deploy

- **Hosting**: arquitetura de subprocesso, persistência de sessão, escala, observabilidade e
  isolamento multi-tenant em Docker, Kubernetes e serverless.
- **Secure deployment**: isolamento, gestão de credenciais e controles de rede.

## Migração

Os antigos SDKs "Claude Code" (TypeScript e Python) foram substituídos pelo **Claude Agent
SDK** — há guia de migração dedicado. A API de sessão V2 do SDK TypeScript foi **removida**;
a página correspondente existe só como referência histórica.
