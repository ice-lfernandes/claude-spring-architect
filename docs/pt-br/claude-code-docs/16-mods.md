# 16 — Mods

Páginas oficiais cobertas:

| Página | Link |
|--------|------|
| Mods overview | <https://code.claude.com/docs/en/plugins/mods/overview> |
| Create a mod | <https://code.claude.com/docs/en/plugins/mods/create> |
| React to events | <https://code.claude.com/docs/en/plugins/mods/events> |
| Draw in the interface | <https://code.claude.com/docs/en/plugins/mods/interface> |
| Use the mods API | <https://code.claude.com/docs/en/plugins/mods/api> |
| Test a mod | <https://code.claude.com/docs/en/plugins/mods/test> |
| Troubleshoot a mod | <https://code.claude.com/docs/en/plugins/mods/troubleshoot> |
| Manage mods for your organization | <https://code.claude.com/docs/en/plugins/mods/admin> |
| Mods reference | <https://code.claude.com/docs/en/plugins/mods/reference> |
| Interface gallery | <https://code.claude.com/docs/en/plugins/mods/gallery> |
| Tipos TypeScript (fonte canônica) | <https://github.com/anthropics/claude-code/blob/main/mods/types/claude-code.d.ts> |
| Mods de exemplo | <https://github.com/anthropics/claude-code-playground/tree/main/claude-code/mods> |
| Mods embutidos (fonte) | <https://github.com/anthropics/claude-code/tree/main/mods> |

Snapshot desta página: 2026-10-08, referência "as of v2.1.290". Mods existem desde a
**v2.1.287** (terminal) e **v2.1.286** (Desktop). A API muda entre releases; a fonte que
vence é o `.d.ts` que o Claude Code escreve em `.claude-plugin/types/` do próprio mod
(ver [Tipos para a sua versão](#tipos-para-a-sua-versão)).

---

## O que é um mod

Um mod é um **plugin cujo código roda dentro do processo do Claude Code**. É um arquivo
JavaScript ou TypeScript (o *hooks module*) que exporta `register(on, options)` e registra
*handlers* para eventos: uma tool prestes a rodar, um prompt enviado, uma parte da interface
prestes a ser desenhada. Cada handler pode **observar**, **reescrever** ou **assumir** o
evento.

Vocabulário da documentação: nestas páginas, "hook" significa o handler de um mod; o hook de
`settings.json` passa a ser chamado de **settings hook**. Ambos continuam existindo, nada foi
descontinuado.

Alguns recursos nativos já são mods: `/diff`, o carregamento de `AGENTS.md`, o guarda de
política `sec-default`, a telemetria e a skill `plugin-authoring`.

### Mod vs settings hook vs skill vs MCP

| | Mod | Settings hook | Skill | MCP server |
|---|-----|---------------|-------|------------|
| O que é | Funções num plugin, chamadas no processo do Claude Code | Comando shell, HTTP ou prompt num evento de lifecycle | `SKILL.md` que o Claude lê | Processo externo que dá tools |
| O que muda | Tool calls, prompts, comandos, turnos e o que a interface desenha | Se uma tool call ou prompt segue, seus argumentos/resultado, contexto adicionado | O que o Claude sabe e faz | Quais tools o Claude tem |
| Desenha na interface | **Sim** | Não | Não | Não |
| Escrito em | JS/TS | Script + `settings.json` | Markdown | Qualquer linguagem |
| Escolha quando | Quer painel, faixa acima do prompt, `/comando` próprio, ou reescrever um evento | Quer bloquear/permitir/logar com um script que já tem | Repete as mesmas instruções no chat | O Claude precisa alcançar um sistema externo |

Um plugin pode conter tudo isso ao mesmo tempo: mod + skill + MCP server no mesmo pacote.

### Onde roda e onde desenha

| Onde | Hooks rodam | Desenho aparece |
|------|-------------|-----------------|
| `claude` no terminal (inclui terminal integrado da IDE e plugin JetBrains) | Sim | Sim |
| Aba Code do app Desktop (exceto sessão WSL) | Sim | Sim, menos elementos marcados terminal-only |
| Sessão WSL no Desktop | Não (plugins indisponíveis) | Não |
| Painel de chat da extensão VS Code | Sim | **Não** |
| `claude -p` e Agent SDK | Sim | **Não** |
| Remote Control (claude.ai / mobile) | Sim, na sessão da máquina | No terminal da máquina |
| Sessão cloud | Sim, para plugin que chega lá | **Não** |

Consequência: um mod que desenha deve checar `e.surface` e ter *fallback* em texto
(`$.ui.log`, ou o `text` de um comando) onde nada é desenhado.

---

## Anatomia

```
first-mod/
├── .claude-plugin/
│   └── plugin.json          # manifesto de plugin comum; mods não adicionam campo obrigatório
├── hooks/
│   ├── hooks.json           # { "modules": ["./register.js"] } — é isso que torna o plugin um mod
│   └── register.js          # hooks module: export function register(on, options)
├── types/index.d.ts         # só se usa $.state ou adiciona namespace; apontado por "types" no manifesto
└── tests/*.test.ts          # claude plugin test
```

- `hooks.json` aceita **um** caminho em `modules`, relativo ao próprio arquivo. Pode conter
  settings hooks sob `hooks` no mesmo arquivo.
- Extensões aceitas: `.js .mjs .cjs .jsx .ts .mts .cts .tsx`. **ES module** obrigatório
  (`import`, nunca `require`). Sem Node.js, sem bundler, sem build.
- `options` traz os valores de `userConfig` declarados no manifesto, com defaults.
- Dentro do módulo **não há** APIs do Node, nem `setTimeout`: tudo que sai do módulo passa
  pela mods API (`$`). É isso que permite `claude plugin validate` listar o que o mod faz
  sem executá-lo.

### Ciclo de desenvolvimento

```
claude --plugin-dir ./first-mod           # carrega por uma sessão e recarrega ao salvar
claude plugin validate ./first-mod        # manifesto + análise estática: linhas hooks: e calls:
claude plugin test                        # roda *.test.ts, sem sessão, login ou rede
claude -p "/tally" --plugin-dir ./first-mod   # testa um /comando sem sessão interativa
claude --debug-file ./mod.log --plugin-dir ./first-mod   # log de carga, skip e recusas
```

- Cada salvamento recarrega o módulo: `register` roda de novo e variáveis de módulo zeram.
- Desenvolver **sempre** contra `--plugin-dir`, nunca contra a cópia instalada: o Claude Code
  cacheia o plugin instalado por versão; edição só chega após incrementar `version` e
  reinstalar.
- `CLAUDE_CODE_PLUGIN_DIRS` (env ou `env` no settings) equivale a `--plugin-dir` para apps
  sem flag; `CLAUDE_CODE_PLUGIN_DIR_WATCH=1` faz `-p` longo recarregar ao salvar.

### Pedir ao Claude que escreva o mod

A skill embutida `plugin-authoring` (`/plugin-authoring`) sabe onde escrever e quais
eventos a versão tem. O mod nasce em `~/.claude/dev-mods/<session-id>/<nome>/`; ao salvar o
primeiro arquivo, o Claude Code pergunta se ativa *hot reload* para a sessão. Esse diretório
é apagado após `cleanupPeriodDays`: para manter, copie para um lugar seu e carregue com
`--plugin-dir`, ou publique num marketplace. Não carrega em `claude -p`, `dontAsk`,
workspace não confiado, `--safe-mode`, `--bare`, `disableAllHooks`, ou sob política.

### Tipos para a sua versão

A cada carga via `--plugin-dir`, o Claude Code escreve em `.claude-plugin/types/` do mod:
`claude-code/index.d.ts` (eventos, métodos, elementos), `claude-code-tools/index.d.ts`
(inputs das tools nativas, narrowing por `e.tool`), `claude-code-mcp/index.d.ts` (tools MCP
conectadas) e um `tsconfig.json`. **Esses arquivos vencem qualquer página de documentação**
quando divergem.

---

## O hook function

```javascript
on('tool.call', { tool: 'Bash' }, async ($, e, next) => { ... })
```

| Argumento | O que é |
|-----------|---------|
| `$` | A mods API, por namespace: `$.ui`, `$.fs`, `$.process`… Sempre escrita por extenso (`$.fs.read`), nunca atribuída a variável nem desestruturada — a análise estática depende disso |
| `e` | O evento, **deeply frozen**. Para mudar, passe uma cópia a `next` |
| `next(e)` | O próximo handler da cadeia (outro mod, ou o comportamento do próprio Claude Code). Resolve no resultado |
| `next.signal` | `AbortSignal` que dispara quando o evento é abandonado (usuário interrompeu) |
| `next.origin` | `{ plugin, tier }` de quem disparou o evento; Claude Code é `{ plugin: 'engine', tier: 'core' }` |
| `next.budget` | `.ms` e `.remainingMs` do limite de tempo do hook |
| `next.to(e, tier)` | Pula para um tier posterior (`append`, `builtin`, `core`); só mods em `prependPlugins`/`appendPlugins` |
| `next.error`, `next.called` | Só no handler `.catch`: `kind` é `throw` ou `timeout` |

### Três modos

| Modo | Como | Efeito |
|------|------|--------|
| **Observar** | Faz algo e `return next(e)`; ou `await next(e)`, faz algo, retorna o resultado | Nada muda para o Claude |
| **Reescrever** | `return next({ ...e, text: e.text.trim() })`; ou altera o resultado após `await next(e)` | Handlers posteriores e o Claude Code veem a versão nova |
| **Assumir** | Retorna um resultado **sem chamar `next`** | Curto-circuito: mods posteriores e o comportamento nativo não rodam |

Cada evento tem seu formato de resultado (`{ deny }`, `{ result }`, `{ text }`, `{ decision }`…).

### Matchers

Segundo argumento de `on`. Objeto cujos campos são comparados com os do evento; valor
único, array de valores, ou regex:

```javascript
on('tool.call', { tool: 'Bash' }, hook)
on('tool.call', { tool: ['Edit', 'Write'] }, hook)
on('tool.call', { tool: /^mcp__github__/ }, hook)
```

- `'classic.*'` casa todo settings hook event; `'*'` casa tudo **exceto** telemetria.
- Registrar o mesmo evento duas vezes **sem matcher** falha a carga:
  `on("session.start") is registered twice without a matcher`.
- Nome do evento tem de ser **string literal** — variável ou loop falham a validação.

### Falha de hook

Hook que lança, estoura o tempo ou devolve formato errado é **pulado**, e a sessão continua:

- Falhou **antes** de chamar `next`: o próximo handler roda no lugar.
- Falhou **depois** de `next` resolver: aquele resultado vale, nada roda de novo.

Uma linha `my-mod: tool.call hook skipped: threw Error: boom` vai para o transcript (sessão
`--plugin-dir`) ou só para o debug log (mod instalado). **Hook que bloqueia deve falhar
fechado** com `.catch`:

```javascript
on('tool.call', { tool: 'Bash' }, guard).catch(async ($, e, next) => {
  return { deny: 'The command guard failed, so this command was not run: ' + next.error.kind }
})
```

Sem o `.catch`, um guard que estourou o tempo deixa o comando passar.

---

## Eventos

Lista completa e campos exatos: reference + `.d.ts`. Hooks em `turn.step` e `process.spawn`
são *async generators*; o resto, async functions.

### Tools

| Evento | Dispara quando | Retorno |
|--------|----------------|---------|
| `tool.call` | Tool prestes a rodar, inclusive de subagents e MCP. `e.tool` + argumentos como campos (`e.command`, `e.file_path`) | `next(e)`, `{ deny: reason }`, `{ result }` |
| `tool.check` | Claude Code decide se a call pode rodar, **depois** de regras de permissão e settings hooks. `next(e)` resolve na decisão deles; `e.input` tem os argumentos | `{ decision: 'allow' \| 'ask' \| 'deny', reason }` |
| `tool.describe` | Uma vez por tool, quando a description vai ao Claude | `{ description, isDeferred? }` |

Padrões:

- **Segurar e perguntar**: `await $.ui.ask('Run this command? ' + e.command, ['Run it', 'Refuse'])`
  dentro de `tool.call`. A espera dentro de uma chamada da mods API **não conta** no limite de
  tempo. Em `claude -p` ou se o usuário dispensar, `ask` rejeita: comece pela resposta segura.
- **Pós-execução**: `const r = await next(e)`; recusa vem como `{ deny }`, falha com `isError`.
- **Retry**: chamar `next(e)` de novo após `isError`.
- **Reescrever `deny`** como instrução acionável: o Claude lê esse texto como resultado da tool.
- `tool.check` serve para decisão **dependente de estado** (branch atual, valor gravado por
  outro hook); para comando ou caminho fixo, regra de permissão é mais barata.

### Prompts e o que o Claude lê

| Evento | Dispara quando | Retorno |
|--------|----------------|---------|
| `prompt.submit` | Prompt enviado. `e.text` | `next({ ...e, text })` (reescreve, transcript mostra o novo), `next({ ...e, context: [...(e.context ?? []), extra] })` (texto só o Claude lê), `{ drop: reason }` |
| `prompt.fill`, `prompt.suggest` | Texto vai entrar na caixa como rascunho / sugestão | `next(e)` com texto mudado |
| `prompt.edit` | Usuário edita a caixa (limite **50 ms**) | `next(e)` |
| `prompt.compose` | Claude Code monta o system prompt | `{ sections: [{ id, text, scope }] }` |
| `prompt.section` | Uma vez por seção nomeada do system prompt (`e.name`) | `{ text }` ou `{ text: null }` para omitir |
| `prompt.context` | Contexto da primeira mensagem da conversa | `{ blocks }` |
| `prompt.attachment` | Mensagem própria do Claude Code (reminder). `e.type`, `e.detail` | `{ text }` ou `{ text: null }` |
| `prompt.mention` | Arquivo @-mencionado vai ser lido (≥ 2.1.290) | `next({ ...e, path })` ou `{ deny }` |
| `skill.prompt` | Texto de uma skill expandido | `{ text }` |
| `attribution.text` | Texto de atribuição de commit/PR | `{ text }` |

Texto que **muda entre requisições** em `prompt.section`/`prompt.context`/`skill.prompt`
**invalida o prompt cache**.

### Comandos e configuração

| Evento | Dispara quando | Retorno |
|--------|----------------|---------|
| `command.run` | Comando vai rodar. `e.args` é o texto após o nome | `{ text }` (imprime e o Claude lê), `{}` (nada), `next(e)` |
| `command.describe` | Lista de comandos | `{ description, argumentHint, isHidden }` |
| `config.set` | Linha de `/config` vai mudar | `next({ ...e, value })` ou `{ deny }` |
| `config.describe` | Cada linha de `/config` | `{ label, description, isHidden }` |

### Turnos

| Evento | Dispara quando | Retorno |
|--------|----------------|---------|
| `turn.start` | Turno começa. `e.turnId` | `next(e)` |
| `turn.step` | **Uma requisição** ao modelo (turno com tools tem várias). `e.agentId` em subagent. **Async generator**: `const r = yield* next(e)`; `r.usage` tem `input_tokens`, `output_tokens`, `cache_read_input_tokens`, `cache_creation_input_tokens`, `model` | `yield* next(e)`, `next({ ...e, model })`, `next({ ...e, effort })`, ou responder sem chamar o modelo |
| `turn.complete` | Turno acabou (inclusive abortado: `e.isAborted`). `e.answer`, `e.durationMs`, `e.usage` (totais), `e.agentId` | `next(e)` ou `{ text }` para uma linha sob a resposta |

### Sessão

| Evento | Dispara quando | Retorno |
|--------|----------------|---------|
| `session.start` | Uma vez por mod carregado, antes do primeiro prompt, e a cada reload. **Não** após `/clear`, `/resume`, `/branch` | `next(e)` |
| `session.end` | Fim de sessão ou `/clear`/`/resume`/`/branch`. `e.reason`: `clear`, `resume`, `logout`, `prompt_input_exit`, `other` | `next(e)` |
| `session.compact` | Compactação iminente | `{ skip: reason }` |
| `session.receive` / `session.send` | Mensagem chega de / vai para outra sessão ou subagent. `e.text`, `e.origin.kind` (`peer`, `peer-send-message`, `task-notification`, `scheduled-trigger`; em `send`: `model` ou `plugin`) | `{ consumed: reason }` / `{ isDelivered: false, reason }` |
| `session.append` | Cada linha que a conversa guarda, antes de persistir | `next({ ...e, message })` |
| `session.attach` / `session.detach` | Outro app conecta/desconecta | `next(e)` |
| `session.measure` | Após cada turno e quando um limite de plano muda | `next(e)` |

### Subagents

| Evento | Dispara quando | Retorno |
|--------|----------------|---------|
| `agent.offer` | Tipo de subagent é oferecido ao Claude | `{ isOffered: false }` para esconder |
| `agent.spawn` | Subagent ou teammate vai começar (`e.isTeammate`) | `next({ ...e, model })` ou `{ deny }` |

### Interface

| Evento | Dispara quando |
|--------|----------------|
| `ui.render` | Um render site vai ser desenhado |
| `ui.resolve` | Na carga, por app × site × mod; o resultado é a tabela de elementos que `$.ui.resolve(e)` lê |
| `ui.press`, `ui.input`, `ui.select` | Controle desenhado por **um mod** foi usado (`e.element` = `key`). Outro mod vê antes do callback do dono e pode alterar ou responder no lugar |
| `ui.focus`, `ui.scroll` | Foco ou scroll de pane/band vai mudar |
| `ui.close` | Pane vai fechar. `e.id`, `e.origin.kind`: `plugin`, `person`, `unload` |
| `ui.message` | Um `Client` postou dados ao seu mod |
| `ui.fault` | Um `Client` seu falhou (`e.phase`: `load`/`render`/`run`; ≥ 2.1.289) |

### Outros mods e telemetria

| Evento | Dispara quando | Retorno |
|--------|----------------|---------|
| `plugin.register` | Um hooks module vai carregar. `e.tier`, `e.uses` (events, calls, env, state — o mesmo que `validate` imprime) | `{ refuse: reason }` |
| `engine.create` | A mods API está sendo montada para este mod | API alterada (adicionar namespace; tiers fora de `user` podem retirar) |
| `telemetry.log`, `telemetry.mark` | Registro de telemetria. Mod instalado **precisa** do filtro `{ to: 'collector' }` ou falha o `validate`; `*` não casa | `next(e)` ou `{ deny }` |

### Settings hook events

Cada evento de `settings.json` existe como `classic.<Evento>` (`classic.Stop`,
`classic.PostToolUse`, `classic.SessionStart`…). `e` é o JSON que o settings hook receberia
no stdin, inclusive `transcript_path`. `classic.SessionStart` com `e.source` em
`clear`/`resume`/`fork` é como um mod recarrega estado após `/clear`, já que `session.start`
não dispara de novo.

### Chamadas da mods API como eventos

Toda chamada `$.ns.metodo` é também um evento `ns.metodo` (`fs.read`, `model.complete`,
`ui.open`). Um mod **anterior na cadeia** observa, reescreve ou recusa (`{ deny }`,
`{ value }`) as chamadas dos posteriores. É assim que uma organização restringe o que mods
alcançam.

### Ordem da cadeia

1. `sec-default@builtin` (guarda nativo, `cc-plugin-sec-default` no `/plugin`), mods em
   `prependPlugins`, demais mods da organização fora de `appendPlugins`
2. Mods que **você** instalou (um mod roda antes dos que lista em `dependencies`)
3. Mods em `appendPlugins`
4. Demais mods embutidos

O primeiro é o mais externo: vê o evento antes e o resultado depois, e decide se os demais
rodam. Dentro de um módulo, hooks rodam na ordem em que `register` chamou `on`.

**Onde entram os settings hooks `PreToolUse`:**

- De **managed settings**: antes do primeiro mod; bloqueio é final, nenhum mod vê a call.
- De qualquer outro settings file ou `hooks/hooks.json` de plugin: **depois** do último mod
  chamar `next`, como parte do comportamento nativo. Mod que responde `tool.call` sem `next`
  impede que rodem. `tool.check` vem depois disso tudo, então um mod pode **aprovar** uma
  call que um `PreToolUse` fora de managed bloqueou.

---

## Mods API (`$`)

| Namespace | Métodos | Notas |
|-----------|---------|-------|
| `$.plugin` | `name`, `root` | |
| `$.ui` | `resolve`, `invalidate`, `open`, `close`, `panes`, `focus`, `scroll`, `toast`, `status`, `log`, `notice`, `ask`, `copy`, `selection`, `blit` | `log(text)` = linha dim no transcript que o Claude **não** lê; `log(text, { to: 'debug' })` vai ao debug log. `status` = linha sob o prompt com `⚠ mod:`. `toast` = 4 s |
| `$.command` | `register({ name, description, argumentHint, immediate })`, `run`, `list` | Registrar em `session.start`. Nome nativo em uso **lança** (`"/focus" refused: it is the built-in /focus`), então registre por último ou em `try/catch`. `immediate: true` roda durante um turno |
| `$.tool` | `register({ name, description, inputSchema, isDeferred })`, `call`, `check`, `list` | Claude vê como `mcp__<plugin>__<name>`; trate a call num `tool.call` filtrado por esse nome. `isDeferred: false` (≥ 2.1.293) tira do tool search |
| `$.agent` | `register`, `spawn`, `list` | |
| `$.model` | `complete({ model, system, prompt, maxTokens, timeoutMs, effort })`, `fork({ prompt })`, `classify` | `complete` sem histórico; verifique `r.isAnswered`, leia `r.reason`. `fork` pergunta **sobre a conversa atual**, aproveita cache. Gasta o plano/chave do usuário |
| `$.prompt` | `submit({ text, asUser })`, `read`, `fill`, `suggest`, `compose` | `submit` espera a sessão ficar ociosa e inicia turno; **não** dê `await` dentro de hook que roda enquanto o Claude trabalha |
| `$.turn` | `abort` | |
| `$.session` | `messages` (últimas 4.096), `cwd`, `root`, `model`, `turns`, `id`, `repo`, `surfaces`, `usage`, `version`, `compact`, `send`, `append`, `authorize` | `usage()` → `{ startedAt, context: { tokens, window, percent }, rateLimits: [{ kind, percentUsed, resetsAt }], cost }` |
| `$.config` | `list`, `set` | |
| `$.settings` | `read` | Settings files + managed policy |
| `$.env` | `get`, `set` | Nome como string literal; aparece em `env reads:`/`env writes:` do `validate` |
| `$.fs` | `read`, `write`, `list`, `exists`, `stat`, `ancestors` | Caminho relativo resolve contra o cwd da sessão. `list` não é recursivo. `write` **não é atômico**. 4 MiB por arquivo |
| `$.store` | `get`, `set`, `delete`, `keys` | JSON em `~/.claude/plugins/store/`, **compartilhado por todas as sessões da máquina**, 4 MiB. `get`+`set` não é atômico: releia antes de escrever |
| `$.state` | `get`, `set` + helpers `atom`, `read`, `update`, `derive`, `memberOf` de `'claude-code'` | Reativo: `ui.render` que lê um valor redesenha ao escrevê-lo. Dura a sessão; **zera em `/clear`, `/resume`, `/branch`**. Exige `types/index.d.ts` com `PluginState` |
| `$.clock` | `now`, `sleep`, `after`, `every` | Substituem `setTimeout`/`setInterval`. Timers param no reload. `sleep` **conta** no limite de tempo |
| `$.http` | `fetch(url, init)` → `{ status, ok, headers, text }` | Sujeito à política de rede da organização |
| `$.process` | `run(argv, { cwd, timeoutMs })` → `{ exitCode, stdout, stderr }`, `spawn` | Lista de argumentos, **sem shell**. Rejeita se não inicia ou estoura (30 s default, 10 min máx). Processo roda **fora do sandbox** |
| `$.mcp` | `call`, `connect` | `connect` só servidor que o próprio manifesto lista |
| `$.audio` | `play`, `speak` | |
| `$.telemetry` | `log`, `mark` | Só enviado quando o Claude Code ou mod embutido chama |

### Onde guardar estado

| Guardar em | Dura até | Para |
|------------|----------|------|
| Variável de módulo | Reload (cada salvamento em dev) | Valores descartáveis |
| `$.state` | Fim de sessão, `/clear`, `/resume`, `/branch` | Valores que um desenho depende e devem sobreviver a reload |
| `$.store` | O mod apagar, ou `cleanupPeriodDays` sem uso | Configuração, histórico, o que o usuário espera achar na próxima vez |

Padrão: copiar `$.store` → `$.state` em `session.start` **e** em
`classic.SessionStart` com `{ source: ['clear', 'resume', 'fork'] }`. Sem o segundo, após
`/clear` o desenho mostra o default e o próximo callback **grava o default por cima** do
armazenado.

---

## Interface

### Render sites

`ui.render` dispara para cada site; filtre com `{ component: '<Site>' }`. `e.surface` é
`terminal` ou `desktop`; `e.props` traz os dados do site; `e.viewport` tem `columns`, `rows`,
`isFullscreen`.

| Site | O que é | `e.props` | `e.requestId` | Onde |
|------|---------|-----------|---------------|------|
| `Pane` | Sidebar à direita (terminal fullscreen largo) ou região emoldurada acima do prompt. Vazio até um mod abrir com `$.ui.open` | `title`, `isFocused`, `bodyColumns`, `placement` (`dock`/`inline`), `scroll.bodyRows`, `view` | O `id` do `open` | Terminal, Desktop |
| `AbovePrompt` | Faixa acima do prompt, sempre presente, **compartilhada** por todos os mods | `hasSurvey`, `isWorking`, `maxRows`, `bodyColumns`, `scroll`, `view` | Única | Terminal, Desktop |
| `UserMessage`, `AssistantMessage` | Mensagens do transcript | texto, origem | id da mensagem | Terminal, Desktop |
| `ToolUse`, `ToolResult`, `ToolGroup` | Linha da tool call, resultado, grupo colapsado | nome, input, resultado | id da call | Terminal, Desktop |
| `CommandOutput` | Linha que um comando imprimiu | `command`, `text` | id | Terminal, Desktop |
| `AskUserQuestion` | Diálogo de pergunta do Claude. A árvore **tem de conter a referência nativa exatamente uma vez**, com seus elementos acima | pergunta, opções | id da call | Terminal, Desktop |
| `Spinner` | Linha animada enquanto trabalha | `word`, `message`, `suffix`, `mode` | id do agent | Terminal, Desktop |
| `ToolProgress` | Progresso ao vivo de uma tool | `kind` | id da call | Terminal |
| `TurnDuration` | Linha que fecha o turno | `word`, `durationMs` | id | Terminal |
| `InfoNotice`, `SessionMode`, `PromptHint` | Avisos sob o logo, modos no rodapé, dica sob o prompt | | | Terminal / ambos / ambos |

**O prompt de permissão não é render site**: nenhum mod altera o que ele mostra.

Numa sessão `--plugin-dir`, uma árvore inválida gera
`ui.render (Pane) refused: <motivo>; the engine drew its own` no transcript, e o Claude Code
desenha a versão nativa. Fora disso só o debug log registra. Pane vazia = procurar essa linha.

### Três atitudes num site nativo

```javascript
// Mudar um detalhe: manter o desenho nativo e alterar props
return next({ ...e, props: { ...e.props, suffix: ' · tool calls: ' + calls + '…' } })

// Substituir: devolver árvore sem chamar next
return Text({ children: ['Claude has made ' + calls + ' tool calls'] })

// Deixar como está
return next(e)

// Compor: a referência nativa { type: 'engine', ref } ao lado dos seus elementos
const theirs = await next(e)
return Box({ flexDirection: 'column', children: [theirs, Text({ children: ['under the spinner'] })] })
```

Na `AbovePrompt`, devolver árvore **substitui** o que os mods posteriores desenhariam; para
manter, coloque `await next(e)` entre os `children` de um `Box`.

### Elementos

Obtidos com `const { Box, Text, Button } = $.ui.resolve(e)`. Em `.tsx`/`.jsx` pode-se usar JSX.

| Elemento | Props principais | Terminal | Desktop |
|----------|------------------|:--------:|:-------:|
| `Box` | `key`, flex (`flexDirection`, `columnGap`…), `gap`, `padding`, `margin`, `width`, `height`, `borderStyle`, `backgroundColor`, `position`, `hover` | ✓ | ✓ |
| `Text` | `color` (theme key ou cor), `backgroundColor`, `bold`, `italic`, `underline`, `dimColor`, `inverse`, `wrap` (`wrap`, `truncate`, `truncate-start/middle/end`) | ✓ | ✓ |
| `Button` | `key`, `label`, `onPress(e)`, `hotkey` (1 dígito ou letra minúscula), `plain`, `dimColor`, `autoFocus`, `action` (keybinding nativo) | ✓ | ✓ |
| `Link`, `Code`, `Markdown` | `href`/`label`; código; `text` (**não** `children`), `key` se `onLinkPress` | ✓ | ✓ |
| `Input` | `key`, `label`, `placeholder`, `value`, `submitLabel`, `onSubmit(value)`, `onInput(value)`, `autoFocus` | ✓ | ✓ |
| `Select` | `key`, `label`, `options: [{ value, label }]`, `value`, `onSelect(value)`, `autoFocus` | ✓ | ✓ |
| `Svg` | documento SVG até 131.072 chars | | ✓ |
| `Client` | `module`, `key` — região desenhada por um segundo arquivo seu (animação, ponteiro); sem mods API, fala por `ui.message` | ✓ | ✓ |
| `Raster` | `key`, `columns` ≤ 512, `rows` ≤ 256, `cells` (base64 de triplas code point/cor/fundo em `Uint32`; `0x01000000` = cor default). Animar com `$.ui.blit` sem re-render | ✓ | |
| `Image` | PNG/RGBA até 2 MiB ou caminho | ✓ | |

`borderStyle`: `single`, `double`, `round`, `bold`, `singleDouble`, `doubleSingle`,
`classic`, `arrow`, `dashed`, `quote`. Nome inválido (`rounded`) = sem borda, sem erro.
`autoFocus`, `focus`, `closeOnEscape`, `holdToasts` aceitam **apenas `true`**; `false`
lança — adicione o campo condicionalmente.

### Pane: abrir, fechar, foco

```javascript
await $.ui.open({ id: 'hello-tabs', title: 'Hello tabs', focus: true, closeOnEscape: true, rows, columns })
await $.ui.close({ id: 'hello-tabs' })
```

- Pane aberta por **ação do usuário** (comando, botão) aparece em qualquer largura. Aberta
  **pelo mod sozinho** (timer, `turn.start`) só aparece em terminal ≥ **144 colunas**
  (110 após o usuário tê-la aberto uma vez). `open` resolve `{ isPlaced, reason }`. Para
  avisar sem abrir: `$.ui.toast`.
- Foco: `focus: true` só é concedido com prompt vazio e nada mais focado; `Ctrl+X Tab`;
  clique. Teclas com foco: Tab (próximo controle), ↑↓ (controles ou scroll), Enter, hotkey,
  PgUp/PgDn/Home/End, `Ctrl+X` + seta (redimensiona), `Ctrl+X X` (fecha), Esc (volta ao prompt).
  Tab e setas **não** são rebindáveis — jogo usa `w a s d`.
- Dígito como hotkey na **band** dispara também quando digitado sozinho num prompt vazio.
- Para um `/comando` abrir pane **durante** um turno: `immediate: true` no registro.

### Redesenhar

O desenho é um snapshot do último `ui.render`. O Claude Code redesenha sozinho quando props
ou largura mudam; **não** redesenha por timer nem por variável de módulo. Peça com
`$.ui.invalidate('ui.render')` (throttle 10/s; 30/s no terminal para pane visível, band e
hint). `$.state` redesenha sozinho ao escrever. Timer: `$.clock.every(1000, () =>
$.ui.invalidate('ui.render'))` em `session.start`.

---

## Testes

`claude plugin test [dir]` roda todo `*.test.ts`/`*.test.tsx`, sem sessão, login ou rede.
Exit 1 em falha. Kit em `'claude-code/testing'`: `test`, `expect`, `mock`, `tier`.

```typescript
import { expect, mock, test } from 'claude-code/testing'

test('/tally reports the tool calls the mod has seen', async ($, on) => {
  on('tool.call', () => ({ result: 'ok' }))             // stub: responde no lugar do Claude Code
  await $.tool.call({ tool: 'Bash', command: 'ls' })    // dispara o evento pelos hooks do mod
  await $.tool.call({ tool: 'Read', file_path: 'README.md' })
  const answer = await $.command.run({ command: 'tally', args: '' })
  expect(answer.text).toBe('Claude has made 2 tool calls since this mod loaded')
})
```

Regras do kit:

- `$` do teste **age como o Claude Code**: cada método dispara o evento homônimo pelos hooks
  do mod. Não é a mods API.
- `on` registra **stubs**. Stub de chamada da mods API devolve `{ value }`
  (`on('store.get', ($, e) => ({ value: saved.get(e.key) }))`); stub de evento devolve o
  resultado do evento (`{ result }`, `{ text }`). `{ deny: reason }` faz a chamada rejeitar.
  Erros: `returned neither { value } nor { deny }`, `no implementation for <nome>`.
- **Todo stub antes da primeira chamada em `$`.**
- `session.start` **não roda sozinho**: dispare `await $.session.start({...})` com stubs de
  `session.start` e `command.register`.
- Hook que devolve `next(e)` em `ui.render` precisa de stub devolvendo um elemento plano.
- `turn.step` stub é async generator; leia o stream até `done`.
- `$.ui.ask` chega ao teste como `tool.call` de `AskUserQuestion`.
- Mocks de namespace: `mock.clock(on)` (`advance`, `set`, `settle`, `sleep`, `now`),
  `mock.store(on, { count: 7 })`, `mock.env(on, { CI: 'true' })`. O kit responde sozinho
  `$.ui.invalidate` e `$.state`.
- Desenho: `const ui = await $.ui.mount({ plugin, component, requestId, surface, viewport, props })`
  → `ui.press({ key })`, `ui.input({ key, text, kind? })`, `ui.select({ key, value })`,
  `ui.find({ key } | { type, text })`, `ui.unmount()`. Testa a árvore e sua validade por app,
  **não** a pintura.
- Após `/clear`: não dispare `session.start`, dispare
  `$.classic.SessionStart({ source: 'clear' })` e monte.
- Mod de política: `tier('prepend')` no topo do arquivo; `test(nome, { plugins: [inline] }, fn)`
  carrega mods inline para serem admitidos ou recusados. Recusa lança na primeira chamada em `$`.
- Limite por teste: 5 s, salvo `timeoutMs`.

`claude plugin test` em diretório **sem** mod serve de diagnóstico: `no hooks module to load`
(mods podem carregar), `hooks modules are turned off here` (`disableAllHooks` ou política),
`hooks modules are turned off in this process` (desligado remotamente pela Anthropic).

---

## Limites

| Limite | Valor |
|--------|-------|
| Tempo de execução próprio de um hook por evento (sem contar `next` nem mods API, exceto `$.clock.sleep`) | 10 s; 50 ms em `prompt.edit` |
| Handler `.catch` | 1 s |
| Todos os `session.end` juntos | Orçamento do `SessionEnd` (1,5 s default), após os settings hooks |
| `$.process.run` | 30 s default, 10 min máximo |
| `$.model.complete` `maxTokens` | 1.024 default, até 64.000 ou o limite do modelo |
| `$.fs.read`/`write` | 4 MiB por arquivo |
| Texto numa árvore | Primeiros 100.000 chars |
| `$.store` | 4 MiB JSON total |
| `$.session.messages()` | 4.096 entradas mais novas |
| Redraw | 10/s; 30/s no terminal para pane visível, band expandida e hint |
| Toast | 4 s salvo `{ timeoutMs }` |
| Pane aberta sem pedido do usuário | ≥ 144 colunas; 110 após abrir uma vez |
| Nomes de comando, tool, agent, pane | letras, dígitos, `_`, `-`, até 64 |
| Um teste | 5 s salvo `timeoutMs` |

Hook que estoura o tempo é **pulado**; chamada que estoura tamanho é **rejeitada**.

---

## Segurança e governança

### O que um mod alcança

Roda **com as permissões do usuário, sem sandbox**. Pode: ler e escrever qualquer arquivo
do usuário, iniciar programas, acessar a rede; ler env vars e settings (inclusive chaves de
API); ver todo prompt e tool call; reescrever prompt/tool call, submeter prompt como se
fosse o usuário, mandar mensagem a outra sessão; **aprovar tool call antes do prompt de
permissão**; gastar o plano. Sandboxing isola os Bash do Claude, não um processo que o mod
inicia. Não altera o prompt de permissão.

Com `Read(.env)` negado, um mod ainda lê o arquivo com `$.fs.read`. Para limitar isso: não
carregar o mod, ou tratar a chamada num mod de política.

### Revisar antes de instalar

```
claude plugin validate ./some-mod
  ❯ ./register.js hooks: session.start, tool.call, ui.render{component=Pane}
  ❯ ./register.js calls: $.fs.read, $.http.fetch, $.store.set, $.ui.open
```

| Em `calls:` | Significa |
|-------------|-----------|
| `$.fs.read`, `$.fs.write` | Lê/escreve arquivos onde o usuário pode |
| `$.process.run`, `$.process.spawn` | Inicia programas como o usuário |
| `$.http.fetch` | Rede |
| `$.env.get`, `$.settings.read` | Env vars e settings, que podem conter chaves (`env reads:` nomeia cada uma) |
| `$.env.set` | Muda env para o Claude Code e todo comando/MCP iniciado depois (`env writes:`) |
| `$.mcp.call` | Chama tool MCP sob as regras da sessão |
| `$.model.complete` | Gasta plano/chave |
| `$.prompt.submit` | Submete prompt, inclusive como o usuário |
| `$.session.send` | Mensagem que outra sessão lê |

Em `hooks:`: `tool.call` e `prompt.submit` = vê e altera tudo; `session.append` = reescreve o
histórico antes de gravar; `ui.render{component=AskUserQuestion}` = redesenha o diálogo de
pergunta; `tool.check` = aprova/nega antes do prompt de permissão.

O Claude Code **recusa carregar** um mod cujo uso da mods API a análise estática não consegue
ler. `validate` também falha nome que pareça da Anthropic (prefixo `claude-`).

### Desligar

| Alcance | Como |
|---------|------|
| Um mod | Desabilitar/desinstalar no `/plugin` → **Installed** |
| Todos os instalados, uma sessão | `claude --safe-mode` (desliga também as outras customizações) |
| Todos os que você instalou, sempre | `"disableAllHooks": true` em `~/.claude/settings.json` (para também settings hooks e status line; o que a organização gerencia continua) |

`disableAllHooks` e `allowManagedModsOnly` param o mod e **deixam o resto do plugin**
(skills, agents, MCP) carregando. `--bare`, `--safe-mode` e `disableAllHooks` **não** param
mods embutidos; cada um tem seu switch no `/plugin`. `CLAUDE_CODE_ENABLE_FUNCTION_HOOKS`
(early access) é ignorado desde 2.1.287 — `0` não desliga nada.

### Managed settings

| Quero | Settings |
|-------|----------|
| Nenhum mod instalado, hooks intactos | `pluginConfigs["cc-plugin-sec-default@builtin"].options.allowManagedModsOnly: true`, sem mods próprios |
| Nenhum mod e nenhum hook, nem os managed | `disableAllHooks: true` |
| Só mods da organização | `allowManagedModsOnly` + instalar os mods **de forma que contem como da organização** |
| Qualquer mod de marketplaces aprovados | Restrições de marketplace + `disableSideloadFlags: true` (rejeita `--plugin-dir`, `--plugin-url`, `--agents`, `--mcp-config`; e mods que o Claude escreve na sessão) |
| Qualquer mod, com o seu checando os outros | Instalar o seu e listá-lo com `sec-default@builtin` em `prependPlugins` |

Um mod **conta como da organização** só quando: managed `enabledPlugins` o liga, managed
settings nomeiam o marketplace como **diretório local por caminho absoluto**
(`extraKnownMarketplaces` com `source: "directory"`), e o marketplace o lista por **caminho
relativo** (carga *in place*). Plugin copiado para o cache (GitHub, git, URL, npm) conta como
do usuário mesmo com `enabledPlugins` managed. Diretório deve ser gravável só por admin.

Guarda nativo `sec-default@builtin`: carrega quando a máquina tem managed settings **ou** o
usuário está logado em plano Team/Enterprise. Protege o que é managed (hooks managed,
system prompt, `CLAUDE.md` managed, settings, tools de MCP managed). Deny rules e
`PreToolUse` managed vencem mods (`allowModsToOverrideDenyRules` relaxa). Falha **fechado**:
sem conseguir ler managed settings, recusa todo mod de usuário. Opções só são lidas de
managed settings, sob a chave `cc-plugin-sec-default@builtin` (em `prependPlugins` aceita-se
`sec-default@builtin`). Setar `prependPlugins` **substitui o default**: inclua o guarda na
lista ou ele não carrega.

`prependPlugins`/`appendPlugins` em settings de usuário só valem em máquina sem managed
settings e usuário fora de Team/Enterprise. Repositório **nunca** define esses dois.

### Mod de política

Mod em `prependPlugins` trata `plugin.register` (recusa por `e.tier === 'user'` e
`e.uses.calls`) e intercepta chamadas da mods API pelo nome (`on('fs.write', …)`). Falhe
fechado com `.catch` que devolve `{ refuse }` para `tier === 'user'`. Logs via
`$.ui.log(msg, { to: 'debug' })` ou `$.http.fetch`. Limites: sessão roda sem o seu mod se o
worker de hooks cair 3 vezes (`/reload-plugins` recarrega) ou com `--safe-mode`.

---

## Troubleshooting

Primeiro `claude plugin validate`. Depois, a linha que o Claude Code escreve ao pular algo:
transcript (sessão `--plugin-dir` ou hot reload), debug log (`claude --debug` /
`--debug-file`), ou stderr (`claude -p` com `--plugin-dir`).

| Sintoma / mensagem | Causa | Ação |
|--------------------|-------|------|
| `/plugin` não lista o mod na linha `N mod active` | Módulo não carregou | Procure `hooks module <nome> not loaded: <motivo>` no debug log |
| `not loaded: disableAllHooks in managed settings` / `only managed plugins and built-in plugins run` / `installed plugins ... (--bare)` / `another plugin of that name loads first` | Política, `--bare`, nome duplicado | Conforme o motivo |
| `refused by cc-plugin-sec-default: mods are limited to your organization's by policy (allowManagedModsOnly)` | Guarda da organização | Admin |
| `tried to lift a deny rule in your settings` | `tool.check` aprovou call negada por deny rule; call continua negada | Admin: `allowModsToOverrideDenyRules` |
| `validate` passa sem linha `hooks:` | `hooks.json` sem `modules` | Adicione `"modules": ["./register.js"]` |
| `hooks module did not load: <arquivo:linha>` | Top-level do módulo lançou | Corrija |
| `options do not fit plugin.json userConfig` | `pluginConfigs` fora do schema | Ajuste o valor |
| Nada carrega em diretório novo | Trust prompt não respondido | Abra `claude` interativo e aceite |
| `<mod>: <evento> hook skipped: threw/timeout/...` | Hook lançou, estourou ou devolveu formato errado | Corrija; `.catch` se bloqueia |
| `<mod> registered /x but no command.run hook answered it` | Sem hook, matcher errado, hook devolveu `next(e)`, ou hook pulado (`focus: false` em `open` é um caminho) | Veja `hook skipped` |
| `<mod> was unloaded: it crashed the hooks worker` | Hook bloqueou a thread (loop sem await) | Corrija |
| `mods that run in the hooks worker are off for this session: it crashed 3 times` | Worker caiu 3× sem culpado | `/reload-plugins` |
| `a hook changed this call's input after the model wrote it` (auto mode) | Mod/hook alterou input após o classifier | Reemitir; se repete, desligar o mod ou sair do auto mode |
| Pane vazia ou com conteúdo nativo | Árvore inválida | `ui.render (Pane) refused: <motivo>` |
| `$.ui.open` roda e nada aparece | Terminal estreito, abertura não iniciada pelo usuário | Abra de comando/botão; leia `isPlaced` |
| Hotkeys mortas | Pane sem foco | `Ctrl+X Tab`, clique, ou `focus: true` |
| Edições não valem | Editando cópia instalada | `--plugin-dir` |
| Valor zera no reload / após `/clear` | Variável de módulo / `$.state` | `$.store` + recarregar em `classic.SessionStart` |

Debug log: `claude --debug-file ./mod-debug.log --plugin-dir ./first-mod`, depois
`tail -f ./mod-debug.log | grep first-mod`. Mod carregado aparece como
`hooks module first-mod@inline loaded (worker, environment 2, tier user); events: …`.
Reload quebrado: `reload failed, the previous version stays loaded: <motivo>`, e a versão
anterior segue rodando.

---

## Regras práticas

1. **Regra que deve valer sempre continua sendo settings hook ou `permissions`.** Mod não
   desenha em VS Code, `-p`, cloud e mobile, e `--safe-mode` o desliga. Um mod que bloqueia é
   camada de UX sobre uma garantia, não a garantia.
2. **Guard em mod falha fechado** com `.catch` → `{ deny }`. Sem isso, timeout = comando passa.
3. **`deny` é texto que o Claude lê**: escreva a saída, não só a recusa.
4. **Cheque `e.surface` e `e.agentId`.** Fallback em texto onde nada desenha; filtre
   subagents em `turn.step`/`turn.complete` quando só quer a conversa principal.
5. **`$.store` é compartilhado entre sessões e não é atômico**: uma chave por item, releia
   antes de gravar.
6. **Texto variável em `prompt.section`/`prompt.context`/`skill.prompt` invalida o cache.**
7. **`claude plugin validate` é a revisão de segurança**: leia `hooks:` e `calls:` de todo
   mod de terceiro antes de instalar. Mods **não são sandboxed**.
8. **Desenvolva com `--plugin-dir`**, teste com `claude plugin test`, e escreva no README a
   versão do Claude Code testada — a API muda entre releases.
