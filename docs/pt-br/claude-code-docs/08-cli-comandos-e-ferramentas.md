# 08 — CLI, comandos `/` e ferramentas

Páginas oficiais cobertas:

| Página | Link |
|--------|------|
| CLI reference | <https://code.claude.com/docs/en/cli-reference> |
| Commands | <https://code.claude.com/docs/en/commands> |
| Tools reference | <https://code.claude.com/docs/en/tools-reference> |
| Interactive mode | <https://code.claude.com/docs/en/interactive-mode> |
| Checkpointing | <https://code.claude.com/docs/en/checkpointing> |
| Manage sessions | <https://code.claude.com/docs/en/sessions> |
| Customize your status line | <https://code.claude.com/docs/en/statusline> |
| Customize keyboard shortcuts | <https://code.claude.com/docs/en/keybindings> |
| Configure your terminal | <https://code.claude.com/docs/en/terminal-config> |

---

## Comandos de shell

| Comando | Uso |
|---------|-----|
| `claude` | Sessão interativa |
| `claude "query"` | Sessão interativa com prompt inicial |
| `claude -p "query"` | Modo não interativo (print), sai ao terminar |
| `cat file \| claude -p "query"` | Processa conteúdo vindo do pipe |
| `claude -c` / `claude -c -p "query"` | Continua a conversa mais recente do diretório |
| `claude -r "<sessão>" "query"` | Retoma sessão por ID ou nome |
| `claude update` / `claude install [versão]` | Atualiza / (re)instala o binário nativo |
| `claude auth login\|logout\|status` | Autenticação (status sai 0 se logado, 1 se não) |
| `claude doctor` | Diagnóstico read-only de instalação e settings |
| `claude mcp ...` | Gerencia MCP servers (`login`, `logout`, `list`, `get`, `add`, `remove`) |
| `claude plugin ...` | Gerencia plugins |
| `claude agents` / `attach` / `logs` / `stop` / `respawn` / `rm` | Sessões em background (agent view) |
| `claude setup-token` | Token OAuth de longa duração para CI |
| `claude project purge [path]` | Apaga todo o estado local do projeto |
| `claude ultrareview [alvo]` | Revisão multi-agente não interativa |

## Flags mais relevantes

- **Sessão e contexto:** `--continue`, `--resume`, `--fork-session`, `--session-id`,
  `--name`, `--no-session-persistence`, `--add-dir`, `--worktree`, `--tmux`, `--teleport`,
  `--cloud`, `--autocompact`.
- **Permissões e tools:** `--permission-mode`, `--dangerously-skip-permissions`,
  `--allow-dangerously-skip-permissions`, `--allowedTools`, `--disallowedTools`, `--tools`,
  `--permission-prompt-tool`, `--permission-prompts`, `--restricted`, `--safe-mode`, `--bare`.
- **Configuração:** `--settings`, `--setting-sources`, `--mcp-config`, `--strict-mcp-config`,
  `--plugin-dir`, `--plugin-url`, `--agents`, `--agent`, `--disable-slash-commands`.
- **System prompt:** `--append-system-prompt[-file]`, `--system-prompt[-file]`,
  `--append-subagent-system-prompt[-file]`, `--exclude-dynamic-system-prompt-sections`.
- **Modelo:** `--model`, `--fallback-model`, `--effort`, `--advisor`.
- **Saída (print mode):** `--output-format text|json|stream-json`, `--input-format`,
  `--include-partial-messages`, `--include-hook-events`, `--forward-subagent-text`,
  `--json-schema`, `--max-turns`, `--max-budget-usd`, `--verbose`.
- **Diagnóstico:** `--debug[=categorias]`, `--debug-file <path>`.

`claude --help` não lista todas as flags — a ausência lá não significa que a flag não existe.

## Comandos `/` (seleção)

Fluxo do dia a dia:

| Comando | Função |
|---------|--------|
| `/init` | Gera `CLAUDE.md` inicial (`CLAUDE_CODE_NEW_INIT=1` para fluxo interativo) |
| `/doctor` | Checkup de setup, propõe correções e cortes no `CLAUDE.md` |
| `/context [all]` | Mostra o uso do contexto em grade colorida |
| `/compact [instruções]` | Resume a conversa para liberar contexto |
| `/clear [nome]` | Nova conversa com contexto vazio |
| `/rewind` (`Esc Esc`) | Restaura conversa e/ou código, ou resume a partir de um ponto |
| `/memory` | Edita `CLAUDE.md`, liga/desliga auto memory |
| `/permissions` | Regras allow/ask/deny (aba **Auto mode** quando disponível) |
| `/hooks` | Lista hooks por evento (somente leitura) |
| `/skills`, `/reload-skills`, `/skill-doctor` | Listar, recarregar e medir skills |
| `/plugin`, `/reload-plugins` | Gerenciar plugins |
| `/mcp` | Status e autenticação de MCP servers |
| `/model`, `/effort`, `/fast`, `/advisor` | Modelo, esforço, fast mode, advisor |
| `/output-style` | Trocar estilo de resposta |
| `/config`, `/status`, `/usage` (`/cost`, `/stats`) | Configuração, status, custo e limites |
| `/diff`, `/code-review` (`/review`), `/security-review`, `/simplify` | Revisão |
| `/plan`, `/goal`, `/loop`, `/batch`, `/subtask`, `/tasks`, `/workflows` | Planejar e paralelizar |
| `/agents`, `/list-agents` | Subagents (o wizard foi removido na v2.1.198) |
| `/resume`, `/rename`, `/branch`, `/export`, `/recap` | Gestão de sessão |
| `/add-dir`, `/cd` | Diretórios de trabalho |
| `/btw` | Pergunta lateral cuja resposta **não** entra no histórico |
| `/verify`, `/run`, `/run-skill-generator` | Rodar e verificar a aplicação |
| `/bug`, `/feedback` | Reportar problema / enviar feedback |

Entradas marcadas **Skill** são skills bundled (prompt entregue ao Claude); marcadas
**Workflow** são dynamic workflows que rodam em background (`/deep-research`).
Nem todo comando aparece para todo usuário — depende de plataforma, plano e ambiente.

## Catálogo de tools

Nomes canônicos usados em regras de permissão e matchers de hook:

- **Arquivos:** `Read`, `Write`, `Edit`, `NotebookEdit`
- **Busca:** `Glob`, `Grep` (ausentes por padrão em macOS/Linux/WSL, onde o Bash cobre),
  `LSP` (code intelligence, inativa até instalar um plugin de linguagem)
- **Execução:** `Bash`, `PowerShell`, `Monitor` (roda comando em background e devolve cada
  linha de saída)
- **Web:** `WebFetch`, `WebSearch`
- **Agentes e tarefas:** `Agent`, `ListAgents`, `SendMessage`, `SubagentHandback`,
  `Workflow`, `TaskCreate`/`TaskGet`/`TaskList`/`TaskUpdate`/`TaskStop`/`TaskOutput`,
  `TodoWrite` (desativada por padrão)
- **Fluxo:** `EnterPlanMode`, `ExitPlanMode`, `EnterWorktree`, `ExitWorktree`,
  `AskUserQuestion`, `EndConversation`
- **Agendamento:** `CronCreate`, `CronDelete`, `CronList`, `ScheduleWakeup`, `RemoteTrigger`
- **MCP:** `ListMcpResourcesTool`, `ReadMcpResourceTool`, `WaitForMcpServers`, `ToolSearch`
- **Saída para o usuário:** `Artifact`, `PushNotification`, `SendUserFile`, `ReportFindings`,
  `SendFeedback`, `ShareOnboardingGuide`
- **Skills:** `Skill`

Comportamentos importantes: Bash tem timeout padrão de 2 minutos e faz backgrounding
automático de comandos longos; saídas acima do teto inline viram caminho de arquivo mais
preview; a tool `Edit` exige leitura prévia do arquivo em muitos casos; `WebFetch` tem
domínios de documentação pré-aprovados; `WebSearch` tem limite por sessão.

O rótulo mostrado no transcript pode diferir do nome canônico (`Stop Task` é `TaskStop`) —
regras e matchers usam o **nome canônico**.

## Modo interativo

- **Interromper e dirigir:** `Esc` para parar; digitar e dar Enter enfileira a mensagem, que
  o Claude lê ao fim das tool calls em andamento.
- **Rewind:** `Esc Esc` ou `/rewind`; opções de restaurar conversa, código, ambos, ou
  resumir a partir de/até um ponto.
- **Shell mode:** prefixo `!` roda um comando direto no shell.
- **Background:** `Ctrl+B` manda a tarefa atual para segundo plano.
- **Transcript:** `Ctrl+O`; `Ctrl+G` abre o plano no editor; `Ctrl+R` busca reversa no
  histórico.
- **Vim mode** completo (NORMAL/INSERT, text objects, visual), spell check, emoji
  shortcodes, ditado de voz, sugestões de prompt e task list embutida.

## Sessões e checkpoints

- Transcripts em `~/.claude/projects/` (JSONL); `cleanupPeriodDays` controla a retenção
  (arquivos de auto memory ficam de fora do sweep).
- `/resume` mostra sessões do worktree atual por padrão, com atalhos para ampliar a busca.
- `/rename` dá nome; trate sessões como branches de trabalho.
- Checkpoints cobrem **apenas** mudanças via tools de edição: não rastreiam mudanças feitas
  por comandos Bash, por processos externos ou por subagents em background, e não restauram
  caminhos symlinkados. Não substituem git.

## Status line

`statusLine` em settings ou `/statusline`. Recebe dados por JSON (modelo, diretório, git,
campos de context window e de prompt cache, custo, duração, limites) e imprime uma ou mais
linhas — útil para acompanhar o uso do contexto continuamente.
