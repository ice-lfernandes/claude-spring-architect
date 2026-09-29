# 04 — Hooks

Páginas oficiais cobertas:

| Página | Link |
|--------|------|
| Automate actions with hooks (guia) | <https://code.claude.com/docs/en/hooks-guide> |
| Hooks reference (schemas completos) | <https://code.claude.com/docs/en/hooks> |
| Intercept and control agent behavior with hooks (SDK) | <https://code.claude.com/docs/en/agent-sdk/hooks> |
| Exemplo oficial: bash command validator | <https://github.com/anthropics/claude-code/blob/main/examples/hooks/bash_command_validator_example.py> |

---

## Por que hooks existem

Hooks são comandos definidos por você que o Claude Code executa em pontos do seu ciclo de
vida. É a única camada **determinística** de comportamento: a ação acontece sempre, sem
depender de o modelo decidir executá-la.

Regra de decisão da documentação: *"Put guardrails in hooks. Uma instrução como 'never edit
`.env`' no CLAUDE.md ou numa skill é um pedido, não uma garantia. Um hook `PreToolUse` que
bloqueia a edição é enforcement."*

| | Hook | Skill |
|---|------|-------|
| Roda | Comando, HTTP, MCP tool, prompt LLM ou subagent | Instruções que o Claude lê |
| Disparo | Evento de lifecycle | Você digitando `/nome`, ou a description casando |
| Determinismo | Garantido | O modelo interpreta |
| Custo de contexto | Zero, salvo se retornar output | Description sempre; corpo ao usar |
| Bom para | Lint após edit, bloquear comando, log, notificação | Workflows com raciocínio, referência |

## Eventos de lifecycle

| Evento | Quando dispara |
|--------|----------------|
| `SessionStart` | Sessão começa ou é retomada |
| `Setup` | `--init-only`, ou `--init`/`--maintenance` em `-p` |
| `UserPromptSubmit` | Você envia um prompt, antes do modelo processar |
| `UserPromptExpansion` | Comando digitado expande em prompt (pode bloquear) |
| `PreToolUse` | Antes de uma tool executar (pode bloquear) |
| `PermissionRequest` | Quando uma tool precisa de decisão de permissão |
| `PermissionDenied` | Quando o auto mode nega uma chamada |
| `PostToolUse` | Depois de uma tool ter sucesso |
| `PostToolUseFailure` | Depois de uma tool falhar |
| `PostToolBatch` | Depois de um lote paralelo resolver |
| `Notification` | Claude Code emite notificação |
| `MessageDisplay` | Texto do assistente é exibido |
| `SubagentStart` / `SubagentStop` | Subagent criado / terminado |
| `TaskCreated` / `TaskCompleted` | Tarefa criada / concluída |
| `Stop` | Claude termina de responder |
| `StopFailure` | Turno termina por erro de API |
| `TeammateIdle` | Teammate de agent team vai ficar ocioso |
| `InstructionsLoaded` | `CLAUDE.md` ou rule entra no contexto |
| `ConfigChange` | Arquivo de configuração muda durante a sessão |
| `CwdChanged` | Diretório de trabalho muda (útil com direnv) |
| `DirectoryAdded` | `/add-dir` ou `register_repo_root` |
| `FileChanged` | Arquivo observado muda no disco (`matcher` lista os nomes) |
| `WorktreeCreate` / `WorktreeRemove` | Criação/remoção de worktree (substitui o comportamento git padrão) |
| `PreCompact` / `PostCompact` | Antes / depois da compactação |
| `PreModelSwitch` / `PostModelSwitch` | Antes (pode bloquear) / depois de trocar de modelo |
| `Elicitation` / `ElicitationResult` | MCP server pede input do usuário |
| `SessionEnd` | Sessão termina |

## Tipos de hook

- `"type": "command"` — roda um comando de shell (o caso normal).
- `"type": "http"` — POST do JSON do evento para uma URL; a resposta usa o mesmo formato de
  saída. Headers suportam `$VAR`, mas só variáveis listadas em `allowedEnvVars` são
  resolvidas. Status HTTP sozinho não bloqueia nada.
- `"type": "mcp_tool"` — chama uma tool de um MCP server configurado.
- `"type": "prompt"` — avaliação LLM de um turno só (Haiku por padrão). Responde
  `{"ok": true|false, "reason": "..."}`. Em `Stop`/`SubagentStop`, `ok:false` devolve o
  `reason` para o Claude continuar (`"impossible": true` libera a parada).
  Em `PreToolUse`/`PostToolUse`, por padrão o turno termina; `continueOnBlock: true` devolve
  o motivo ao modelo e continua.
- `"type": "agent"` — **experimental**: subagent que pode ler arquivos e rodar comandos antes
  de decidir. Timeout padrão 60s, até 50 turnos de tool. Não tem `impossible` nem
  `continueOnBlock` (comporta-se como `continueOnBlock: true`).

Use prompt hook quando o JSON de entrada basta para decidir; agent hook quando é preciso
verificar o estado real do codebase.

## Entrada e saída

O evento chega como JSON no **stdin**. Campos comuns: `session_id`, `cwd`,
`hook_event_name`, mais campos por evento (`tool_name`, `tool_input`, `prompt`, `source`...).

### Exit codes

- **0** — sem objeção. Em `PreToolUse` isso **não aprova** a chamada: o fluxo normal de
  permissão continua. Em `UserPromptSubmit`, `UserPromptExpansion`, `SessionStart` e
  `PostModelSwitch`, o stdout tratado como texto puro é adicionado ao contexto.
- **2** — bloqueia a ação; escreva o motivo em **stderr**. Onde o motivo aparece depende do
  evento: alguns devolvem ao modelo como feedback, outros mostram ao usuário, e alguns
  (como `ConfigChange` e `Elicitation`) não exibem nada. Eventos não bloqueáveis, como
  `SessionStart`, apenas mostram o stderr e seguem.
- **Qualquer outro** — se o stdout for um objeto JSON válido pelo schema, o JSON decide e o
  exit code é ignorado; senão é erro não-bloqueante e a ação prossegue.

### Saída JSON estruturada

Saia com 0 e escreva JSON no stdout. Exemplo de `PreToolUse`:

```json
{
  "hookSpecificOutput": {
    "hookEventName": "PreToolUse",
    "permissionDecision": "deny",
    "permissionDecisionReason": "Use rg instead of grep for better performance"
  }
}
```

`permissionDecision` aceita `allow` (pula o prompt, mas deny/ask e managed deny continuam
valendo), `deny`, `ask` e `defer` (só em `-p`, para um wrapper do Agent SDK retomar depois).

Para `UserPromptSubmit`, injeta-se texto com `hookSpecificOutput.additionalContext` —
**aninhado**; no nível raiz é ignorado silenciosamente. `PostToolUse` e `Stop` usam
`decision: "block"` no topo; `PermissionRequest` usa `hookSpecificOutput.decision.behavior`.

Não misture: ou exit 2 com stderr, ou exit 0 com JSON.

## Matchers

Sem matcher, o hook dispara em toda ocorrência do evento. `"Edit|Write"` (ou `"Edit, Write"`)
limita por nome de tool. Matchers são **case-sensitive**.

| Evento | O matcher filtra | Exemplos |
|--------|------------------|----------|
| `PreToolUse`, `PostToolUse`, `PostToolUseFailure`, `PermissionRequest`, `PermissionDenied` | Nome da tool | `Bash`, `Edit\|Write`, `mcp__.*` |
| `SessionStart` | Como a sessão iniciou | `startup`, `resume`, `clear`, `compact`, `fork` |
| `SessionEnd` | Por que terminou | `clear`, `resume`, `logout`, `prompt_input_exit`, `other` |
| `Notification` | Tipo de notificação | `permission_prompt`, `idle_prompt`, `agent_completed`, ... |
| `SubagentStart`/`SubagentStop` | Tipo de agente | `Explore`, `Plan`, nomes customizados |
| `PreCompact`/`PostCompact` | Origem | `manual`, `auto` |
| `ConfigChange` | Fonte de configuração | `user_settings`, `project_settings`, `local_settings`, `policy_settings`, `skills` |
| `InstructionsLoaded` | Motivo da carga | `session_start`, `nested_traversal`, `path_glob_match`, `include`, `compact` |
| `FileChanged` | Nomes literais de arquivo | `.envrc\|.env` |
| `UserPromptSubmit`, `PostToolBatch`, `Stop`, `CwdChanged`, `MessageDisplay`, ... | Sem matcher | Sempre dispara |

### Campo `if`

Filtra por nome **e argumentos** usando a sintaxe de regras de permissão, evitando spawnar o
processo à toa:

```json
{ "type": "command", "if": "Bash(git *)", "command": "$CLAUDE_PROJECT_DIR/.claude/hooks/check-git-policy.sh" }
```

Subcomandos dentro de `&&`, `$()` e crases são checados. Quando o Claude Code não consegue
determinar o que roda, ele executa o hook mesmo assim — o filtro é *best-effort*, então
enforcement duro vai no sistema de permissões.

`if` só funciona em eventos de tool; em outro evento ele impede o hook de rodar.

## Onde configurar

| Local | Escopo | Compartilhável |
|-------|--------|----------------|
| `~/.claude/settings.json` | Todos os seus projetos | Não |
| `.claude/settings.json` | Um projeto | Sim (commitado) |
| `.claude/settings.local.json` | Um projeto, só você | Não |
| Managed settings | Organização | Sim (admin) |
| Plugin `hooks/hooks.json` | Onde o plugin está ativo | Sim |
| Frontmatter de skill | Resto da sessão, após invocar | Sim |
| Frontmatter de subagent | Enquanto o subagent roda | Sim |

`/hooks` lista tudo agrupado por evento (menu somente leitura). `disableAllHooks: true`
desativa — managed settings continuam valendo a menos que também definam a chave.
Hooks **somam** entre fontes: todos os registrados para o evento disparam.

## Combinação de resultados

Todos os hooks do evento rodam **em paralelo até o fim**; um `deny` não impede os irmãos de
executar (nem os efeitos colaterais deles). Depois, o Claude Code combina: para decisões de
permissão em `PreToolUse` vence a mais restritiva, na ordem `deny` > `defer` > `ask` >
`allow`. Texto de `additionalContext` de todos é preservado.

## Hooks e permission modes

`PreToolUse` dispara **antes** de qualquer checagem de modo, em todos os modos, inclusive
`dontAsk` e `bypassPermissions` — então um hook pode aplicar política que o usuário não
consegue contornar trocando de modo. O inverso não vale: `allow` de hook não derruba deny de
settings nem o prompt de tools MCP marcadas `requiresUserInteraction`.

## Limitações

- Command hooks se comunicam só por stdout/stderr/exit code; não disparam comandos `/` nem
  tool calls.
- Timeouts: `command`/`http`/`mcp_tool` 10 min (30s para `UserPromptSubmit` e model switch,
  10s para `MessageDisplay`); `prompt` 30s; `agent` 60s; `SessionEnd` compartilha 1,5s.
- `PostToolUse` não desfaz nada — a tool já executou.
- `Stop` dispara sempre que o Claude termina de responder, não só ao concluir a tarefa; não
  dispara em interrupção do usuário (erro de API vira `StopFailure`). Cap de 8 bloqueios
  consecutivos; seu script deve checar `stop_hook_active`
  (`CLAUDE_CODE_STOP_HOOK_BLOCK_CAP` ajusta).
- Se vários `PreToolUse` retornam `updatedInput`, o último a terminar vence — e a ordem é
  não determinística.

## Exemplos canônicos

Formatar após edição:

```json
{"hooks":{"PostToolUse":[{"matcher":"Edit|Write","hooks":[
  {"type":"command","command":"jq -r '.tool_input.file_path' | xargs npx prettier --write"}]}]}}
```

Bloquear arquivos protegidos (`PreToolUse` + script com `exit 2`):

```bash
#!/bin/bash
INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')
FILE_PATH="${FILE_PATH//\\//}"
for pattern in ".env" "package-lock.json" ".git/"; do
  if [[ "$FILE_PATH" == *"$pattern"* ]]; then
    echo "Blocked: $FILE_PATH matches protected pattern '$pattern'" >&2
    exit 2
  fi
done
exit 0
```

Reinjetar contexto depois da compactação: `SessionStart` com matcher `compact` e um `echo`
(ou `git log --oneline -5`) cujo stdout entra no contexto.

Recarregar ambiente com direnv: `SessionStart` + `CwdChanged` escrevendo
`direnv export bash > "$CLAUDE_ENV_FILE"`.

Auto-aprovar um prompt específico: `PermissionRequest` com matcher estreito devolvendo
`{"hookSpecificOutput":{"hookEventName":"PermissionRequest","decision":{"behavior":"allow"}}}`.
Nunca use matcher vazio ou `.*` aqui.

## Troubleshooting

- **Hook não dispara:** confira em `/hooks`, o matcher exato (case-sensitive) e o evento certo.
- **"command not found":** use caminho absoluto ou `${CLAUDE_PROJECT_DIR}`; `"args": []` muda
  para exec form e evita o shell.
- **Script não roda:** `chmod +x`.
- **JSON sem efeito:** algo escreveu no stdout antes (tipicamente `echo` incondicional no seu
  `~/.bashrc`; proteja com `if [[ $- == *i* ]]`), ou o campo está no nível errado
  (`permissionDecision` vai dentro de `hookSpecificOutput`). `claude --debug` mostra
  `Hook JSON output had unrecognized keys`.
- **Cobertura total de mudanças de arquivo:** o Claude também altera arquivos via Bash. Para
  auditoria, use um hook `Stop` que varre a árvore uma vez por turno, ou também case
  `Bash|PowerShell` e liste com `git status --porcelain`; para reagir a um arquivo específico
  independentemente de quem escreveu, use `FileChanged`.
- **Logs:** `claude --debug-file /tmp/claude.log` e `tail -f`, ou `/debug` no meio da sessão.
