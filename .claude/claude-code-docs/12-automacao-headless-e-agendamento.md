# 12 — Automação, headless e agendamento

Páginas oficiais cobertas:

| Página | Link |
|--------|------|
| Run Claude Code programmatically (headless) | <https://code.claude.com/docs/en/headless> |
| Automate actions with hooks | <https://code.claude.com/docs/en/hooks-guide> |
| Push events into a running session with channels | <https://code.claude.com/docs/en/channels> |
| Channels reference | <https://code.claude.com/docs/en/channels-reference> |
| Run prompts on a schedule (`/loop`, cron) | <https://code.claude.com/docs/en/scheduled-tasks> |
| Automate work with routines | <https://code.claude.com/docs/en/routines> |
| Schedule recurring tasks in Desktop | <https://code.claude.com/docs/en/desktop-scheduled-tasks> |
| Keep Claude working toward a goal | <https://code.claude.com/docs/en/goal> |
| Launch sessions from links | <https://code.claude.com/docs/en/deep-links> |
| Share session output as artifacts | <https://code.claude.com/docs/en/artifacts> |

---

## Modo não interativo (`-p`)

```bash
claude -p "Explain what this project does"
claude -p "List all API endpoints" --output-format json
claude -p "Analyze this log file" --output-format stream-json --verbose
tail -200 app.log | claude -p "Slack me if you see any anomalies"
git diff main --name-only | claude -p "review these changed files for security issues"
```

- `text` imprime texto puro; `json` devolve um objeto com campo `result`; `stream-json`
  imprime um objeto JSON por linha, começando por um evento de init.
- O run cria uma sessão resumível, a menos que você passe `--no-session-persistence`.
- Flags úteis: `--max-turns`, `--max-budget-usd`, `--json-schema` (saída validada),
  `--include-partial-messages`, `--include-hook-events`, `--forward-subagent-text`,
  `--allowedTools`, `--permission-prompt-tool`, `--permission-prompts`.
- **Bare mode** (`--bare`) pula auto-discovery de hooks, skills, comandos, subagents e
  plugins — startup mais rápido e ambiente previsível.
- `SIGTERM` interrompe o run; há comportamento definido para tarefas em background ao sair e
  para diretório de trabalho apagado.
- Em `-p`, fork mode está desligado por padrão, `PermissionRequest` só existe quando o
  wrapper do Agent SDK fornece o callback `canUseTool`, e servidores de `.mcp.json` carregam
  **sem** prompt de aprovação.

Padrão de fan-out documentado: gere a lista de alvos, itere chamando `claude -p` com
`--allowedTools` restrito, teste em 2-3 itens e só então rode no conjunto todo.

## Automação por eventos

- **Hooks** (ver [04](04-hooks.md)) — a base determinística: formatar após edição, bloquear
  comandos, logar, notificar, injetar contexto, auditar mudanças de configuração.
- **Channels** — um MCP server empurra mensagens, alertas e webhooks para dentro de uma
  sessão em execução: resultados de CI, mensagens de chat, eventos de monitoramento
  (`--channels`, research preview).
- **Deep links** — URLs `claude-cli://` abrem uma sessão no repositório certo com o prompt
  certo; embutíveis em runbooks, alertas e dashboards.

## Agendamento

| Mecanismo | Onde roda | Uso |
|-----------|-----------|-----|
| **Routines** (`/schedule`) | Nuvem | Continua rodando com o computador desligado; dispara por agenda, chamada de API ou evento do GitHub |
| **Desktop scheduled tasks** | Sua máquina | Acesso direto a arquivos e ferramentas locais; carrega `~/.claude/skills/` |
| **`/loop [intervalo] [prompt]`** | Sessão atual | Repete um prompt enquanto a sessão está aberta; polling e lembretes |
| **Tools de cron** (`CronCreate`, `CronList`, `CronDelete`) | Sessão atual | Tarefas agendadas dentro da sessão, restauradas em `--resume` |

Gotcha: cada execução de routine é uma sessão cloud nova. Uma skill que só existe em
`~/.claude/skills/` **não** é encontrada ali — habilite-a na conta claude.ai ou commite em
`.claude/skills/`.

Em `/loop` dinâmico (sem intervalo), o próprio Claude reagenda com `ScheduleWakeup`,
escolhendo o intervalo conforme o que está esperando.

## `/goal`: trabalhar até a condição ser satisfeita

`/goal <condição>` define uma condição de conclusão. Um avaliador separado re-checa depois de
cada turno e o Claude continua trabalhando até a condição ser cumprida, até um modelo julgá-la
impossível, ou até um erro que você precisa corrigir limpar o objetivo. `/goal clear` remove.

É o degrau intermediário entre pedir verificação no prompt e forçar por hook `Stop`.

## Artifacts

Publicam a saída da sessão como página web privada e interativa em claude.ai — útil quando o
resultado é melhor visto do que lido no terminal (um timeline de incidente que se atualiza
enquanto o Claude investiga, por exemplo). Controlado pelas chaves `enableArtifact` /
`disableArtifact` (o valor restritivo vence de qualquer escopo) e listável com `/artifacts`.

## Padrão de uso não supervisionado

Combinação recomendada pela documentação para rodar sem você olhando:

1. `--permission-mode auto` (ou `dontAsk` com allowlist em CI travado);
2. um check executável — `/goal` ou hook `Stop`;
3. `--max-turns` e/ou `--max-budget-usd` como trava de segurança;
4. saída estruturada (`--output-format json` ou `--json-schema`) para o script seguinte;
5. revisão adversarial (subagent ou `/code-review`) antes de tratar como pronto.
