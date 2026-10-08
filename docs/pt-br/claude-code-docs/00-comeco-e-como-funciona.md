# 00 — Começar e como o Claude Code funciona

Páginas oficiais cobertas:

| Página | Link |
|--------|------|
| Overview | <https://code.claude.com/docs/en/overview> |
| Quickstart | <https://code.claude.com/docs/en/quickstart> |
| Changelog | <https://code.claude.com/docs/en/changelog> |
| How Claude Code works | <https://code.claude.com/docs/en/how-claude-code-works> |
| Extend Claude Code | <https://code.claude.com/docs/en/features-overview> |
| Explore the `.claude` directory | <https://code.claude.com/docs/en/claude-directory> |
| Explore the context window | <https://code.claude.com/docs/en/context-window> |
| How Claude Code uses prompt caching | <https://code.claude.com/docs/en/prompt-caching> |
| Manage sessions | <https://code.claude.com/docs/en/sessions> |
| Checkpointing | <https://code.claude.com/docs/en/checkpointing> |

---

## O que é

Claude Code é uma ferramenta agêntica de codificação: lê o codebase, edita arquivos, roda
comandos e se integra a ferramentas de desenvolvimento. Roda em terminal, extensões de IDE
(VS Code, JetBrains), app desktop e na web (claude.ai/code), além de CI/CD, Slack e Chrome.
Todas as superfícies usam o mesmo motor, então `CLAUDE.md`, settings e MCP servers do repo
valem em todas.

### Instalação (referência rápida)

```bash
# macOS, Linux, WSL
curl -fsSL https://claude.ai/install.sh | bash
# Windows PowerShell
irm https://claude.ai/install.ps1 | iex
# Homebrew (não atualiza sozinho)
brew install --cask claude-code
# WinGet
winget install Anthropic.ClaudeCode
```

Instalações nativas se atualizam em background; Homebrew e WinGet não. Em Windows nativo,
instalar o Git for Windows é recomendado, senão a tool de shell vira PowerShell. Depois:
`cd seu-projeto && claude`.

## O loop agêntico

Três fases que se misturam: **gather context → take action → verify results**, repetindo até
a tarefa terminar. O modelo decide o próximo passo a partir do resultado do anterior, e você
pode interromper a qualquer momento (`Esc`) ou enfileirar uma correção digitando enquanto ele
trabalha.

Dois componentes sustentam o loop:

- **Modelos** — Sonnet para a maioria das tarefas, Opus para raciocínio arquitetural. Troca
  com `/model` ou `claude --model <nome>`.
- **Tools** — sem tools o modelo só responde texto. Categorias: operações de arquivo, busca,
  execução, web, e code intelligence (LSP, via plugin de linguagem).

A camada ao redor do modelo, que fornece as tools e administra o contexto, é o que a
documentação chama de *agentic harness*.

## O que o Claude acessa ao rodar `claude` num diretório

- Arquivos do diretório e subdiretórios (outros, só com permissão).
- O terminal: qualquer comando que você poderia rodar.
- Estado git: branch atual, mudanças não commitadas, histórico recente.
- `CLAUDE.md` (ou `AGENTS.md`) e auto memory (primeiras 200 linhas ou 25 KB de `MEMORY.md`).
- Extensões configuradas: MCP servers, skills, subagents, Chrome.

## Ambientes de execução

| Ambiente | Onde o código roda | Uso |
|----------|--------------------|-----|
| Local | Sua máquina | Padrão, acesso total |
| Cloud | VMs da Anthropic ou self-hosted | Offload, repos que você não tem local |
| Remote Control | Sua máquina, controlada pelo browser | UI web com execução local |

## Sessões

Cada mensagem, tool use e resultado é gravado em JSONL sob `~/.claude/projects/`. Antes de
editar arquivos, o Claude tira snapshot deles — é isso que permite o rewind.

- Sessões são **independentes**: cada nova sessão começa com contexto limpo. O que atravessa
  sessões é `CLAUDE.md` e auto memory.
- `claude --continue` / `claude --resume` reabrem a mesma sessão (mesmo ID, mensagens
  anexadas). `--fork-session` ou `/branch` copiam o histórico para um novo ID.
- Sessões são atreladas ao diretório; para paralelizar, use git worktrees.
- Trocar de branch muda os arquivos que o Claude vê, mas não apaga o histórico da conversa.

## Context window

A janela guarda: histórico da conversa, conteúdo de arquivos lidos, saídas de comando,
`CLAUDE.md`, auto memory, skills carregadas e instruções de sistema.

Regras práticas:

- `/context` mostra o que está ocupando espaço; `/context all` detalha tokens por tool MCP.
- Quando enche, o Claude Code limpa saídas antigas de tools primeiro, depois resume a
  conversa. Pedidos e trechos-chave sobrevivem; instruções detalhadas do começo podem sumir.
- O que precisa persistir vai para `CLAUDE.md`, não para a conversa.
- Para controlar a compactação: seção "Compact Instructions" no `CLAUDE.md`, ou
  `/compact foco aqui`.
- Se um único arquivo ou saída é grande demais e o contexto reenche a cada resumo, o Claude
  Code para de auto-compactar depois de algumas tentativas e mostra erro (thrashing).
- Definições de tools MCP são **deferidas** por padrão (tool search): só nomes e instruções
  do servidor consomem contexto até uma tool ser usada.

### Custo de contexto por feature

| Feature | Quando carrega | O que carrega | Custo |
|---------|----------------|---------------|-------|
| `CLAUDE.md` | Início da sessão | Conteúdo completo | Toda requisição |
| Output style | Início e ao trocar | Instruções do estilo ativo | Toda requisição |
| Skills | Início + ao usar | Descrições no início, corpo ao usar | Baixo |
| MCP servers | Início | Nomes de tools; schemas sob demanda | Baixo até usar |
| Code intelligence | Após edits e sob demanda | Diagnósticos e símbolos | Baixo (reduz leituras) |
| Subagents | Ao spawnar | Janela separada | Isolado |
| Hooks | No trigger | Nada (roda fora) | Zero, salvo se retornar output |
| Mods | No evento | Nada, salvo `context` em `prompt.submit`, `tool.register` ou texto variável em `prompt.section` (invalida cache) | Zero a baixo |

## Checkpoints e segurança

- Edits de arquivo são reversíveis: `Esc Esc` ou `/rewind` restauram conversa e/ou código.
- Checkpoints são independentes do git e sobrevivem ao resume; cobrem **apenas** mudanças
  feitas pelas tools de edição (não mudanças via Bash ou processos externos) e não restauram
  symlinks/hard links.
- Ações em sistemas remotos (DB, APIs, deploys) não são "checkpointáveis" — isso se controla
  com permission mode e regras de permissão.

## Permission modes (visão rápida)

`Shift+Tab` cicla entre eles:

- **Auto** — um classificador revisa a maioria das ações em background e bloqueia as
  arriscadas. A partir da v2.1.283, é o modo inicial padrão em sessões interativas de
  terminal e VS Code (em versões anteriores, só nos planos Pro, Max e Team).
- **Manual** (`default`) — pergunta antes de editar arquivos e rodar comandos.
- **Accept edits** — edita arquivos e roda comandos comuns de filesystem sem perguntar.
- **Plan** — explora e propõe plano sem editar o código-fonte.

## O diretório `.claude/`

No projeto:

```
seu-projeto/
├── CLAUDE.md              # instruções do projeto (ou .claude/CLAUDE.md)
├── .mcp.json              # MCP servers do projeto (raiz, não dentro de .claude/)
├── .worktreeinclude       # arquivos gitignored a copiar para worktrees novos
└── .claude/
    ├── settings.json       # permissões, hooks, statusLine, model, env (commitado)
    ├── settings.local.json # overrides pessoais (gitignored)
    ├── rules/              # instruções por tópico, opcionalmente com paths:
    ├── skills/<nome>/SKILL.md
    ├── commands/<nome>.md  # formato antigo, ainda funciona
    └── agents/<nome>.md    # subagents do projeto
```

No home (`~/.claude/`): `CLAUDE.md`, `settings.json`, `skills/`, `agents/`, `rules/`,
`plugins/`, `projects/<projeto>/` (transcripts e `memory/`). `~/.claude.json` é escrito pelo
próprio Claude Code (login, MCP, estado por projeto) e não deve ser editado à mão.

## Prompt caching (o que preserva e o que invalida)

Invalidam o cache: trocar de modelo, mudar effort level, ligar fast mode, conectar/remover
MCP server, habilitar/desabilitar plugin, negar uma tool inteira, compactar a conversa,
acumular muitas imagens, atualizar o Claude Code.

Preservam o cache: editar arquivos do repositório, editar `CLAUDE.md` no meio da sessão,
trocar permission mode, trocar output style, invocar skills e comandos, `/recap`, rewind.

TTL padrão configurável (`5m` / `1h`); subagents podem escolher via frontmatter
`experimental.cacheTtl`.

## Trabalhando bem (do próprio guia)

- Pergunte ao próprio Claude: "como configuro hooks?", "como estruturo meu CLAUDE.md?".
- `/init` gera um `CLAUDE.md` inicial; `/doctor` roda um checkup de setup e propõe correções.
- É conversa, não prompt perfeito: comece, corrija, itere.
- Delegue em vez de ditar: dê contexto e direção, não a lista de arquivos a ler.
