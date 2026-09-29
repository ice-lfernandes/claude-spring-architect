# 10 — Boas práticas e workflows

Páginas oficiais cobertas:

| Página | Link |
|--------|------|
| Best practices for Claude Code | <https://code.claude.com/docs/en/best-practices> |
| Common workflows | <https://code.claude.com/docs/en/common-workflows> |
| Prompt library | <https://code.claude.com/docs/en/prompt-library> |
| Set up Claude Code in a monorepo or large codebase | <https://code.claude.com/docs/en/large-codebases> |
| Keep Claude working toward a goal | <https://code.claude.com/docs/en/goal> |
| Code Review | <https://code.claude.com/docs/en/code-review> |

---

## A restrição que explica quase todas as regras

> "A maioria das boas práticas se apoia numa restrição: a janela de contexto enche rápido e
> o desempenho degrada conforme ela enche."

Uma única sessão de debug ou exploração pode consumir dezenas de milhares de tokens. Quando
a janela está cheia, o modelo "esquece" instruções antigas e erra mais.

## 1. Dê ao Claude como verificar o próprio trabalho

O Claude para quando o trabalho **parece** pronto. Sem um check executável, você vira o loop
de verificação.

| Estratégia | Antes | Depois |
|------------|-------|--------|
| Critérios de verificação | "implemente validação de email" | "escreva `validateEmail`. Casos: `user@example.com` true, `invalid` false, `user@.com` false. Rode os testes depois" |
| Verificação visual | "melhore o dashboard" | "[screenshot] implemente este design, tire um screenshot do resultado, compare e liste as diferenças" |
| Causa raiz, não sintoma | "o build está quebrado" | "o build falha com [erro]. Corrija e verifique. Ataque a causa raiz, não suprima o erro" |

Níveis de rigor, do mais leve ao mais forte:

1. **No prompt** — peça para rodar o check e iterar na mesma mensagem.
2. **Na sessão** — `/goal <condição>`: um avaliador separado re-checa a cada turno.
3. **Gate determinístico** — hook `Stop` que roda seu script e bloqueia o fim do turno.
4. **Segunda opinião** — subagent de verificação ou dynamic workflow que refuta o resultado.

Peça **evidência** (saída do teste, comando rodado, screenshot), não afirmação de sucesso.

## 2. Explore, planeje, depois codifique

1. **Explore** em plan mode (`Shift+Tab` até `⏸ plan mode on`, ou
   `claude --permission-mode plan`): ler arquivos e responder sem mudar nada.
2. **Planeje**: "que arquivos mudam? qual o fluxo de sessão? crie um plano". `Ctrl+G` abre o
   plano no editor.
3. **Implemente**: aprove o plano, deixe codificar verificando contra o plano.
4. **Commit**: peça mensagem descritiva e PR.

Plan mode tem overhead. Pule quando o escopo é claro e a mudança é pequena — se dá para
descrever o diff em uma frase, não planeje.

## 3. Contexto específico no prompt

| Estratégia | Antes | Depois |
|------------|-------|--------|
| Delimite a tarefa | "adicione testes para foo.py" | "escreva um teste para foo.py cobrindo o caso de usuário deslogado. Evite mocks" |
| Aponte a fonte | "por que essa API é estranha?" | "olhe o histórico git de ExecutionFactory e resuma como a API chegou aqui" |
| Referencie padrões existentes | "adicione um widget de calendário" | "veja como os widgets da home são feitos; HotDogWidget.php é um bom exemplo; siga o padrão" |
| Descreva o sintoma | "conserte o bug de login" | "login falha após timeout de sessão; veja src/auth/, especialmente refresh de token; escreva um teste que reproduz e depois corrija" |

Prompt vago é útil quando você está explorando ("o que você melhoraria neste arquivo?").

Formas de fornecer conteúdo: `@arquivo`, colar imagens, dar URLs (allowlist de domínios em
`/permissions`), `cat error.log | claude`, ou deixar o Claude buscar sozinho.

## 4. Configure o ambiente

- **`CLAUDE.md`** — `/init` gera; refine com o teste "remover isto faria o Claude errar?".
- **Permissões** — pré-aprove com `/permissions`, ligue sandbox com `/sandbox`, use Manual
  mode quando quiser aprovar tudo.
- **CLIs** — a forma mais eficiente em contexto de falar com serviços externos
  (`gh`, `aws`, `gcloud`, `sentry-cli`). Truque: *"use `foo-cli --help` para aprender a
  ferramenta e depois resolva A, B, C"*.
- **MCP** — para o que não tem CLI boa.
- **Hooks** — para o que precisa acontecer sempre; peça ao Claude para escrevê-los.
- **Skills** — conhecimento de domínio e workflows sob demanda.
- **Subagents** — trabalho isolado com tools restritas.
- **Plugins** — instale code intelligence se usa linguagem tipada.

## 5. Comunique como com um colega

- Pergunte como perguntaria a um sênior: "como funciona o logging?", "que edge cases
  `CustomerOnboardingFlowImpl` trata?". É um fluxo de onboarding eficaz.
- Para features grandes, **deixe o Claude te entrevistar**:

```text
I want to build [descrição]. Interview me in detail using the AskUserQuestion tool.
Ask about technical implementation, UI/UX, edge cases, concerns, and tradeoffs.
Keep interviewing until we've covered everything, then write a complete spec to SPEC.md.
```

Depois abra uma sessão nova para executar a spec. Specs boas são autocontidas: nomeiam
arquivos e interfaces, dizem o que está fora de escopo e terminam com uma verificação
ponta a ponta.

## 6. Gerencie a sessão

- Corrija cedo: `Esc` para interromper, `Esc Esc`/`/rewind` para voltar, "undo that",
  `/clear` entre tarefas não relacionadas.
- **Depois de duas correções falhas no mesmo ponto, `/clear` e reescreva o prompt** com o
  que você aprendeu. Sessão limpa com prompt melhor supera sessão longa com correções.
- Delegue investigação a subagents para manter a pesquisa fora do contexto principal.
- Use `/btw` para dúvidas laterais que não devem entrar no histórico.
- `/compact <foco>`, ou resumir a partir de/até um ponto pelo menu de rewind.
- Nomeie sessões (`/rename`) e trate-as como branches.

## 7. Automatize e escale

- **Não interativo:** `claude -p "prompt"`, com `--output-format json` ou
  `stream-json --verbose` para parsing.
- **Sessões paralelas:** worktrees, cross-session messaging, app desktop, cloud, agent view,
  agent teams.
- **Writer/Reviewer:** uma sessão implementa, outra (contexto fresco) revisa — contexto novo
  melhora a revisão porque não há viés pelo código recém-escrito.
- **Fan-out:** `/batch <instrução>` divide em 5–30 subagents com worktree próprio; ou faça
  seu loop:

```bash
for file in $(cat files.txt); do
  claude -p "Migrate $file from Python 2 to Python 3. Return OK or FAIL." \
    --allowedTools "Edit,Bash(git commit *)"
done
```

- **Auto mode para execução contínua:** `claude --permission-mode auto -p "fix all lint errors"`.
- **Revisão adversarial antes de dar por pronto:** `/code-review` (subagent fresco sobre o
  diff), ou um prompt próprio comparando o diff com o `PLAN.md`. Instrua a reportar só
  lacunas de correção/requisito — um revisor pedido para achar problemas sempre acha, e
  perseguir tudo leva a over-engineering.

## Antipadrões comuns

| Antipadrão | Sintoma | Correção |
|-----------|---------|----------|
| Sessão "pia de cozinha" | Tarefas não relacionadas na mesma conversa | `/clear` entre tarefas |
| Corrigir sem parar | Contexto poluído de tentativas falhas | Após 2 correções, `/clear` + prompt melhor |
| `CLAUDE.md` inchado | Regras importantes se perdem | Pode sem dó; converta em hook o que for verificável |
| Confiar sem verificar | Implementação plausível que não trata edge cases | Sempre forneça verificação |
| Exploração infinita | "investigue X" sem escopo, centenas de arquivos lidos | Delimite ou use subagents |

## Monorepos e codebases grandes

- **Onde iniciar importa:** na raiz, o Claude acessa tudo e carrega só o `CLAUDE.md` raiz
  (os de subdiretório carregam sob demanda); num subdiretório, o acesso é àquela subárvore e
  carregam o `CLAUDE.md` local **e os de todos os ancestrais**. Settings de projeto **não**
  são herdados de diretórios pai como os `CLAUDE.md`.
- **Camadas de `CLAUDE.md`:** raiz com o que vale em todo lugar; um por pacote/área com as
  convenções locais. Cada dono mantém o seu; revise em PR.
- **`CLAUDE.md` por diretório vs. rule com `paths:`**: o primeiro fica junto do código e
  carrega quando você inicia ali; o segundo fica central em `.claude/rules/` e carrega
  quando um arquivo casa com o glob.
- **Reduza o que o Claude lê:** bloqueie leitura de código gerado e vendorizado com deny
  rules, e instale code intelligence para trocar leituras amplas por lookup de símbolo.
- **Worktrees com checkout parcial** e concessão explícita de acesso entre pacotes.
- **Skills por diretório** para convenções de cada área, mantendo-as descobríveis.

## Receitas de `common-workflows`

Entender codebase novo, corrigir bugs, refatorar, trabalhar com testes, criar pull requests,
lidar com documentação, trabalhar em pastas não-código, trabalhar com imagens, referenciar
arquivos e diretórios, rodar em agenda, retomar conversas, sessões paralelas com worktrees,
planejar antes de editar, delegar pesquisa a subagents e canalizar o Claude em scripts.

## Desenvolva intuição

Os padrões são pontos de partida, não dogma. Às vezes deixar o contexto acumular é certo
(você está fundo num problema só); às vezes pular o planejamento é certo (tarefa
exploratória); às vezes um prompt vago é exatamente o que você quer.
