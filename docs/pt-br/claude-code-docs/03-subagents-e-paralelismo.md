# 03 — Subagents e trabalho paralelo

Páginas oficiais cobertas:

| Página | Link |
|--------|------|
| Run agents in parallel | <https://code.claude.com/docs/en/agents> |
| Create custom subagents | <https://code.claude.com/docs/en/sub-agents> |
| Manage multiple agents with agent view | <https://code.claude.com/docs/en/agent-view> |
| Orchestrate teams of Claude Code sessions | <https://code.claude.com/docs/en/agent-teams> |
| Message your other Claude Code sessions | <https://code.claude.com/docs/en/cross-session-messaging> |
| Orchestrate subagents at scale with dynamic workflows | <https://code.claude.com/docs/en/workflows> |
| Run parallel sessions with worktrees | <https://code.claude.com/docs/en/worktrees> |
| Let Claude coordinate ongoing work with Projects | <https://code.claude.com/docs/en/claude-projects> |

---

## Cinco maneiras de trabalhar em paralelo

| Abordagem | O que dá | Quem coordena |
|-----------|----------|---------------|
| Subagents | Workers delegados dentro de uma sessão, com contexto próprio, devolvem resumo | O Claude, turno a turno |
| Agent view (`claude agents`) | Uma tela para despachar e monitorar sessões em background (research preview) | Você |
| Agent teams | Sessões coordenadas com task list compartilhada e mensageria (experimental, desligado por padrão) | Um lead agent |
| Projects | Uma conversa contínua em claude.ai/code ou desktop, com threads paralelas | O Claude |
| Dynamic workflows | Um script que roda muitos subagents e cruza resultados | O script |

Apoios que não são "modos de rodar agentes": **worktrees** (checkout git separado por
sessão), **cross-session messaging** (o Claude entrega recados entre suas sessões) e
`/batch` (skill que divide uma mudança grande em 5 a 30 subagents isolados por worktree).

> Rodar várias sessões ou subagents multiplica o uso de tokens.

## Subagents

Cada subagent roda em **janela de contexto própria**, com system prompt customizado, acesso
específico a tools e permissões independentes. Ele faz requisições próprias, que contam para
os mesmos limites de uso.

Servem para: preservar contexto, restringir tools, reutilizar configurações, especializar
comportamento e controlar custo (roteando para modelos mais baratos).

> Descrições de subagents ocupam contexto. Acima de 15.000 tokens somados (fora os
> built-in), o Claude Code avisa no startup. Corte as `description` e mova detalhe para o
> system prompt, que só carrega quando o subagent roda.

### Built-in

| Agente | Modelo | Tools | Uso |
|--------|--------|-------|-----|
| `Explore` | Herda da conversa (no máximo Opus na Claude API) | Só leitura; Write/Edit negados | Busca e análise de codebase |
| `Plan` | Herda da conversa | Só leitura | Pesquisa durante plan mode |
| `general-purpose` | `CLAUDE_CODE_SUBAGENT_MODEL` ou o da conversa | Todas as disponíveis a subagents | Tarefas complexas de vários passos |
| `claude` | Segue a ordem de modelo | Todas | Catch-all; agente padrão de background sessions |
| `statusline-setup` | Sonnet | — | `/statusline` |
| `claude-code-guide` | Haiku | — | Perguntas sobre o próprio Claude Code |

`Explore` e `Plan` **pulam** `CLAUDE.md` e o snapshot de git status para ficarem baratos.
Todos os outros carregam ambos, salvo `omitClaudeMd: true`.

Para restringir: `permissions.deny` com o tipo específico, negar a tool `Agent` inteira,
`CLAUDE_CODE_DISABLE_EXPLORE_PLAN_AGENTS=1`, ou
`CLAUDE_AGENT_SDK_DISABLE_BUILTIN_AGENTS=1` em headless/SDK.

### Escopo e precedência

| Local | Escopo | Prioridade |
|-------|--------|-----------|
| Managed settings | Organização | 1 (maior) |
| Flag `--agents` (JSON) | Sessão atual | 2 |
| `.claude/agents/` | Projeto | 3 |
| `~/.claude/agents/` | Todos os seus projetos | 4 |
| `agents/` de plugin | Onde o plugin está ativo | 5 (menor) |

Diretórios são varridos recursivamente; a identidade vem do campo `name`, não do caminho
(exceto em plugins, onde subpastas viram parte do identificador `plugin:pasta:nome`).
Nomes duplicados no mesmo diretório carregam só um, escolhido por ordem de leitura do
filesystem — `/doctor` reporta isso. Subagents de plugin ignoram `hooks`, `mcpServers` e
`permissionMode` por segurança.

### Arquivo de subagent

```markdown
---
name: code-reviewer
description: Reviews code for quality and best practices
tools: Read, Glob, Grep
model: sonnet
---

You are a code reviewer. When invoked, analyze the code and provide
specific, actionable feedback on quality, security, and best practices.
```

Só `name` e `description` são obrigatórios. Campos multi-palavra usam camelCase
(`maxTurns`, `disallowedTools`) e campo desconhecido é ignorado sem erro.

Campos relevantes: `tools`, `disallowedTools`, `model` (`sonnet|opus|haiku|fable|<id>|inherit`),
`permissionMode`, `maxTurns`, `skills` (pré-carrega o **conteúdo completo** das skills),
`mcpServers`, `hooks`, `memory` (`user|project|local`), `background`, `omitClaudeMd`,
`effort`, `isolation: worktree`, `color`, `initialPrompt`, `experimental.cacheTtl`.

O corpo Markdown vira o system prompt. O subagent recebe **só** esse prompt mais detalhes
básicos de ambiente — não o system prompt do Claude Code.

Claude Code observa `~/.claude/agents/` e `.claude/agents/` e aplica mudanças sem restart.
Exceções que exigem restart: primeiro arquivo num diretório `agents` que não existia,
diretórios vindos de `--add-dir`, e sessões com `--disable-slash-commands`.

### Foreground vs. background

- Foreground bloqueia a conversa principal; prompts de permissão chegam direto.
- Background roda em paralelo; prompts aparecem na sessão principal.
- Com fork mode ligado (padrão em sessões interativas), os subagents vão para background.
- Background usa um conjunto **menor** de tools embutidas, exceto forks e subagents
  retomados em foreground.
- `Ctrl+B` manda uma tarefa em execução para o background. `CLAUDE_CODE_DISABLE_BACKGROUND_TASKS=1`
  força tudo em foreground.
- Limite: 20 subagents concorrentes por sessão (`Concurrent subagent limit reached`).

### Fork da conversa (`/subtask`)

Um fork é um subagent que **herda toda a conversa**, o mesmo system prompt, as mesmas tools
e o mesmo modelo — e compartilha o prompt cache do pai, o que o torna barato de iniciar.
Custo: perde o isolamento de entrada.

| | Fork | Subagent normal |
|---|------|-----------------|
| Contexto | Conversa completa | Fresco, só o prompt passado |
| System prompt e tools | Iguais aos da sessão | Da definição, filtradas em background |
| Modelo | Igual ao da sessão | Campo `model` |
| Prompt cache | Compartilhado | Separado |

Fork mode é ligado por padrão em sessões interativas, desligado em `-p` e no Agent SDK
(`CLAUDE_CODE_FORK_SUBAGENT=1|0` força). Para manter fork mode ligado mas impedir o Claude de
criar forks, negue `Agent(fork)`.

### Padrões de uso

- Delegue pesquisa: *"use subagents to investigate how our auth handles token refresh"*.
- Revisão adversarial: um subagent lê só o diff e os critérios, sem o raciocínio que produziu
  a mudança. Instrua a reportar apenas lacunas de correção/requisito, senão ele "encontra"
  problemas por construção e leva a over-engineering.
- Combine com skills nas duas direções: skill com `context: fork` (a skill é a tarefa,
  o agente é o executor) ou subagent com campo `skills` (a skill é referência pré-carregada).

## Dynamic workflows

Quando o trabalho passa de alguns subagents, o plano sai da cabeça do modelo e vira **código**:

| | Subagents | Skills | Agent teams | Workflows |
|---|---|---|---|---|
| Quem decide o próximo passo | Claude, turno a turno | Claude | O lead | O script |
| Onde ficam os resultados intermediários | Contexto do Claude | Contexto | Task list compartilhada | Variáveis do script |
| Escala | Poucas tarefas por turno | Idem | Alguns peers longos | Dezenas a centenas de agentes |
| Interrupção | Reinicia o turno | Reinicia o turno | Teammates continuam | Resumível na mesma sessão |

- Workflow bundled: `/deep-research <pergunta>` — fan-out de buscas, cross-check das fontes,
  votação por claim e relatório citado (claims não verificadas são marcadas como tal).
- Para criar: peça no prompt ("escreva um workflow para..."), use a palavra-chave
  `ultracode`, ou `/workflow-authoring` para carregar a referência.
- `/workflows` abre a visão de progresso: fases, contagem de agentes, tokens, tempo; teclas
  `p` (pausar), `x` (parar), `r` (reiniciar agente), `s` (salvar o script como comando),
  `f` (filtrar por status).
- Um run salvo vira comando próprio e pode ser distribuído num plugin.
- Casos típicos: auditar muitos arquivos pelo mesmo problema, insistir até um check passar,
  migrar arquivos em paralelo, revisar cada arquivo alterado e escrever um resumo único,
  pesquisar um tema em muitas fontes.

## Agent teams (experimental)

Sessões coordenadas por um lead, com task list compartilhada e mensagens entre teammates.
Desligado por padrão. Pontos de atenção da documentação: escolha de modo de exibição, tamanho
do time, dimensionamento de tarefas, gates de qualidade por hooks, conflito de arquivos
(teams **não** isolam teammates em worktrees) e sessões tmux órfãs.

## Worktrees

Cada sessão trabalha num checkout git separado, então edições paralelas não colidem.
`--worktree`, a tool `EnterWorktree`, ou `isolation: worktree` num subagent. O arquivo
`.worktreeinclude` (raiz do projeto, sintaxe `.gitignore`) lista arquivos gitignored — como
`.env` — a copiar para cada novo worktree.

Claude Code impede que comandos de um subagent isolado escrevam no checkout principal:
bloqueia redirecionamento de git para fora do worktree e recusa comandos cuja forma não
permite verificar onde o git roda.

## Verificar trabalho em andamento

| O quê | Comando |
|-------|---------|
| Sessões em background | `claude agents` (agent view) |
| Qualquer coisa em background na sessão | `/tasks` |
| Runs de workflow | `/workflows` |
| Agentes endereçáveis por mensagem | `/list-agents` |
