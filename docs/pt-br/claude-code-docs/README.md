# Documentação indexada do Claude Code

Índice de estudo da documentação oficial do Claude Code, organizado pelos tópicos do
[Overview oficial](https://code.claude.com/docs/en/overview). Cada página deste diretório
resume um bloco de tópicos com as regras, normas, boas práticas e detalhes de
funcionamento, e aponta para o link de referência oficial de cada página.

- **Fonte:** <https://code.claude.com/docs/en/overview> e o índice completo em
  <https://code.claude.com/docs/llms.txt> (209+ páginas).
- **Snapshot:** 2026-09-27. O produto muda semanalmente (ver
  [What's new](https://code.claude.com/docs/en/whats-new/index)); confirme detalhes de versão
  antes de publicar conteúdo baseado nestes resumos.
- **Convenção:** qualquer URL oficial pode ser lida em Markdown puro acrescentando `.md`
  ao final (exemplo: `https://code.claude.com/docs/en/skills.md`).

## Índice por tópico

| # | Página | Cobre |
|---|--------|-------|
| 00 | [Começar e como funciona](00-comeco-e-como-funciona.md) | Overview, Quickstart, agentic loop, superfícies, `.claude/`, context window, prompt caching |
| 01 | [Rules: CLAUDE.md, rules e memória](01-rules-claude-md-e-memoria.md) | `CLAUDE.md`, `.claude/rules/`, `AGENTS.md`, auto memory, `/memory` |
| 02 | [Skills](02-skills.md) | `SKILL.md`, frontmatter, invocação, argumentos, `context: fork`, skills bundled |
| 03 | [Subagents e trabalho paralelo](03-subagents-e-paralelismo.md) | Subagents, agent view, agent teams, dynamic workflows, worktrees, mensagens entre sessões |
| 04 | [Hooks](04-hooks.md) | Eventos de lifecycle, exit codes, JSON de decisão, matchers, tipos de hook |
| 05 | [MCP](05-mcp.md) | Transportes, escopos, tool search, autenticação, segurança |
| 06 | [Plugins e marketplaces](06-plugins-e-marketplaces.md) | Estrutura de plugin, instalação, escopos, marketplaces, governança |
| 07 | [Settings, permissões e segurança](07-settings-permissoes-e-seguranca.md) | Arquivos de settings, precedência, regras de permissão, permission modes, sandboxing |
| 08 | [CLI, comandos e ferramentas](08-cli-comandos-e-ferramentas.md) | `claude` CLI, comandos `/`, catálogo de tools, modo interativo, sessões, checkpoints |
| 09 | [Modelo, efeito e estilo de resposta](09-modelo-efeito-e-output-styles.md) | Seleção de modelo, effort, fast mode, extended thinking, output styles |
| 10 | [Boas práticas e workflows](10-boas-praticas-e-workflows.md) | Best practices oficiais, common workflows, monorepos, prompt library |
| 11 | [Plataformas e integrações](11-plataformas-e-integracoes.md) | Terminal, VS Code, JetBrains, Desktop, Web, Slack, Chrome, CI/CD |
| 12 | [Automação, headless e agendamento](12-automacao-headless-e-agendamento.md) | `claude -p`, routines, `/loop`, `/goal`, channels, deep links |
| 13 | [Administração e enterprise](13-administracao-e-enterprise.md) | Managed settings, deployment, gateways, custos, monitoramento, dados |
| 14 | [Agent SDK](14-agent-sdk.md) | SDK TypeScript/Python, agent loop, tools customizadas, permissões, deploy |
| 15 | [Troubleshooting e glossário](15-troubleshooting-e-glossario.md) | Erros, diagnóstico de config, glossário dos termos oficiais |

## Os cinco pontos que sustentam todo o resto

1. **Loop agêntico:** o modelo repete *gather context → take action → verify results* usando
   tools, até a tarefa acabar ou você interromper. Documentação:
   [How Claude Code works](https://code.claude.com/docs/en/how-claude-code-works).
2. **Context window é o recurso escasso:** tudo que você adiciona (CLAUDE.md, skills, MCP,
   saídas de comando) compete por espaço, e a qualidade cai conforme a janela enche.
   Documentação: [Explore the context window](https://code.claude.com/docs/en/context-window).
3. **Instrução ≠ enforcement:** `CLAUDE.md`, rules, skills e output styles são *contexto* que
   o modelo interpreta. Apenas permissões, hooks e sandbox são aplicados pelo cliente,
   independentemente do que o modelo decidir. Documentação:
   [Extend Claude Code](https://code.claude.com/docs/en/features-overview).
4. **Camadas de configuração têm precedência definida:** managed > CLI > project local >
   project > user, com listas que somam em vez de substituir. Documentação:
   [Settings files and precedence](https://code.claude.com/docs/en/settings).
5. **Verificação fecha o loop:** dê ao Claude um teste, build ou script que produza
   pass/fail, senão "parece pronto" é o único sinal disponível. Documentação:
   [Best practices](https://code.claude.com/docs/en/best-practices).

## Como escolher a primitiva certa

| Você quer | Use | Determinístico? |
|-----------|-----|-----------------|
| Contexto que vale em toda sessão | `CLAUDE.md` | Não (heurístico) |
| Instrução que só vale para certos arquivos | `.claude/rules/*.md` com `paths:` | Não |
| Procedimento reutilizável, carregado sob demanda | Skill (`SKILL.md`) | Não |
| Trabalho pesado sem poluir o contexto principal | Subagent | Não |
| Conexão com sistema externo | MCP server | Não (mas a tool é real) |
| Algo que precisa acontecer **sempre** | Hook | Sim |
| Bloquear tool, comando ou caminho | Regra de permissão / sandbox | Sim |
| Empacotar tudo isso para reuso | Plugin | — |

Fonte da tabela: [Extend Claude Code](https://code.claude.com/docs/en/features-overview#match-features-to-your-goal).
