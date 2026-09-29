# 15 — Troubleshooting e glossário

Páginas oficiais cobertas:

| Página | Link |
|--------|------|
| Troubleshoot installation and login | <https://code.claude.com/docs/en/troubleshoot-install> |
| Troubleshooting | <https://code.claude.com/docs/en/troubleshooting> |
| Debug your configuration | <https://code.claude.com/docs/en/debug-your-config> |
| Error reference | <https://code.claude.com/docs/en/errors> |
| Glossary | <https://code.claude.com/docs/en/glossary> |
| Changelog | <https://code.claude.com/docs/en/changelog> |
| What's new | <https://code.claude.com/docs/en/whats-new/index> |

---

## Fluxo de diagnóstico

| Sintoma | Primeiro comando |
|---------|------------------|
| `claude: command not found` após instalar | [Fix your PATH](https://code.claude.com/docs/en/troubleshoot-install#command-not-found-claude-after-installation) |
| Algo na configuração não pega | `claude doctor` (fora da sessão) ou `/doctor` (dentro) |
| Quais settings carregaram | `/status` → linha `Setting sources` |
| Quais `CLAUDE.md` e rules carregaram | `/context` → **Memory files** (ou `/memory`) |
| Hooks não disparam | `/hooks` e `claude --debug-file /tmp/claude.log` |
| MCP não conecta | `/mcp`, `claude mcp list`, `claude mcp get <nome>` |
| Skill não dispara | `/skills`, `/skill-doctor`, e reforçar a `description` |
| Configuração quebrada, causa desconhecida | `claude --safe-mode` (desliga todas as customizações) ou `--bare` |
| Mensagem de erro específica | [Error reference](https://code.claude.com/docs/en/errors) |

## Problemas frequentes e causa

| Problema | Causa típica |
|----------|--------------|
| `CLAUDE.md` ignorado | Arquivo grande demais, instrução vaga, ou conflito entre arquivos. Se precisa valer sempre, vire hook |
| `AGENTS.md` não carrega | Existe um `CLAUDE.md`/`CLAUDE.local.md` no caminho, versão < v2.1.277, ou **Project instructions** em `claude-md`/`managed-only` |
| Instrução some após `/compact` | Só existia na conversa, ou é rule com `paths:` que ainda não casou |
| Auto-compact em loop (thrashing) | Um arquivo ou saída enorme reenche o contexto a cada resumo |
| Prompt de permissão repetido depois de "don't ask again" | O allow salvo no arquivo local não vence um `ask` de projeto/managed |
| Chave commitada não vale para o time | A chave é de escopo user/managed, ou espera pelo workspace trust |
| Hook com JSON sem efeito | Algo escreveu no stdout antes (echo no profile), ou campo fora de `hookSpecificOutput` |
| Stop hook bloqueando demais | Não checa `stop_hook_active`; cap de 8 bloqueios consecutivos |
| Skill nova não aparece | Diretório criado depois do início da sessão → `/reload-skills` |
| Subagent novo não aparece | Diretório `agents/` criado depois do início → reiniciar |
| Skill pessoal não existe na routine | Sessões cloud não leem `~/.claude/skills/` |
| Campo de frontmatter ignorado | Nome errado: campos desconhecidos são descartados **sem erro** |
| Uso alto de CPU/memória, travas | [Troubleshooting](https://code.claude.com/docs/en/troubleshooting) |

## Flags de diagnóstico

```bash
claude --debug                      # debug com filtro opcional: --debug='mcp,startup'
claude --debug-file /tmp/claude.log # grava o log num caminho conhecido
claude --safe-mode                  # sem CLAUDE.md, skills, hooks, plugins, MCP
claude --bare                       # startup mínimo, sem auto-discovery
claude doctor                       # diagnóstico read-only, sem abrir sessão
claude project purge [path]         # apaga estado local do projeto
```

## Glossário (termos oficiais)

- **Agentic coding** — fluxo em que a IA lê arquivos, roda comandos e faz mudanças
  autonomamente enquanto você observa, redireciona ou se afasta.
- **Agentic harness** — as tools, a gestão de contexto e o ambiente de execução que
  transformam um modelo de linguagem num agente de codificação. O Claude Code é a harness; o
  Claude é o modelo dentro dela.
- **Agentic loop** — gather context → take action → verify results, repetindo.
- **AGENTS.md** — arquivo de instruções de projeto para agentes de código em geral; lido
  pelo Claude quando não há `CLAUDE.md`.
- **CLAUDE.md** — instruções persistentes, carregadas a cada sessão **como mensagem de
  usuário depois do system prompt**.
- **Auto memory** — notas que o próprio Claude escreve, por repositório, em
  `~/.claude/projects/`.
- **Auto mode** — permission mode em que um classificador separado revisa as ações no lugar
  do usuário.
- **Bare mode** (`--bare`) — inicia sem hooks, skills, comandos, subagents, plugins, MCP,
  auto memory e `CLAUDE.md`.
- **Bundled skills** — playbooks prompt-based que vêm com o Claude Code (`/batch`,
  `/code-review`, `/debug`, `/loop`...), diferentes dos comandos built-in, que executam
  lógica fixa.
- **Channel** — MCP server que empurra eventos para dentro de uma sessão em execução.
- **Checkpoint** — ponto de restauração criado a cada prompt que inicia um turno.
- **`.claude` directory** — onde o Claude Code lê configuração de projeto; o equivalente de
  usuário é `~/.claude`.
- **Cloud session** — sessão que roda em infraestrutura na nuvem e continua depois que você
  fecha o laptop.
- **Command** — instrução reutilizável invocada com `/nome`. Não confundir com subcomandos
  do CLI (`claude mcp add`) nem com o campo `command` de um MCP stdio.
- **Compaction** — resumo automático da conversa quando a janela de contexto se aproxima do
  limite.
- **Connector** — MCP server adicionado à conta claude.ai em vez de configurado no Claude Code.
- **Context window** — memória de trabalho da sessão.
- **Dispatch** — roteador de tarefas iniciado pelo celular que cria uma sessão no app desktop.
- **Effort level** — controla o raciocínio adaptativo.
- **Extended thinking** — raciocínio visível antes da resposta.
- **Frontmatter** — bloco YAML no topo de um Markdown, entre `---`; usado por skills,
  subagents, output styles e rules.
- **Hook** — handler executado automaticamente num ponto do lifecycle; três níveis: evento,
  matcher e handler.
- **Managed settings** — settings impostos pela organização; settings de usuário e projeto
  não os sobrescrevem.
- **MCP / MCP server / MCP Tool Search** — protocolo, servidor e mecanismo de carregamento
  sob demanda de definições de tool.
- **Non-interactive mode** — `claude -p`, sem UI interativa.
- **Output style** — instruções que definem papel, tom e formato das respostas.
- **Subagent** — worker delegado dentro de uma sessão, com contexto próprio, que devolve
  resumo.
- **Surface** — cada lugar onde o Claude Code roda (terminal, IDE, desktop, web, Slack, CI).

Glossário completo: <https://code.claude.com/docs/en/glossary>.

## Acompanhar mudanças

- [Changelog](https://code.claude.com/docs/en/changelog) — notas de release por versão.
- [What's new](https://code.claude.com/docs/en/whats-new/index) — resumos semanais.
- `/release-notes` dentro da sessão abre o changelog com seletor de versão.

Muitas frases desta documentação começam com "requires Claude Code v2.1.x or later". Sempre
confira `claude --version` antes de concluir que uma feature não existe.
