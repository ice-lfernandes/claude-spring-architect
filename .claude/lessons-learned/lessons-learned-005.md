# Lessons learned — pipeline `/new-feature`

Origem: uma execução real do `/new-feature` com o pedido "CRUD via REST de customer
(id, name)". Resultado: 5 casos de uso especificados numa única execução, 46 min,
~USD 15, 30 arquivos de spec (~130 KB) para um agregado de 2 campos, migrations escritas
em `src/`, commit e push sem confirmação.

---

## 1. Custo vem de releitura de contexto, não do que é escrito

**O que ocorreu:** 70% do custo foi cache read. Cada chamada de ferramenta relê a
conversa inteira. Foram ~130 chamadas com o contexto crescendo até compactar.

**Lições:**

- A alavanca de custo é **número de turnos × tamanho do contexto**. Reduzir qualquer um
  dos dois reduz o custo; reduzir o tamanho dos arquivos gerados ajuda pouco.
- Cada invocação de skill reinjeta o `SKILL.md` inteiro. Encadear dezenas de skills na
  mesma conversa acumula milhares de linhas de instrução repetida.
- Mensagens de progresso entre ferramentas ("Seguindo pro passo X", "X ok") custam
  turnos. Skills de pipeline devem reportar só no fim.
- Escrever vários arquivos no mesmo turno (chamadas paralelas) economiza turnos.
- Um spec consolidado que **copia** os partials gera output duplicado (~40% no caso
  observado). Consolidar por referência: decisões finais + divergências resolvidas,
  detalhes citados pelo caminho do partial.
- Casos de uso triviais (GET por id, DELETE) não precisam de 6 arquivos. Definir um
  caminho curto: sem tabela nova, sem exceção nova e sem pergunta ao usuário → um spec
  único.
- Skills de design não devem ter `WebSearch`/`WebFetch`. Versão de dependência vem do
  build file do projeto; houve uma pesquisa web para uma versão que já estava no `pom.xml`.
- Etapas mecânicas (consolidação, casos triviais) cabem em modelo menor via subagent.

---

## 2. Um caso de uso por execução, e só depois de aprovado

**O que ocorreu:** a descrição virou 5 casos de uso e o pipeline tratou todos na mesma
execução.

- O contexto do quinto caso carregava os quatro anteriores; o custo cresceu mais que
  linearmente e houve compactação.
- O quarto caso editou retroativamente specs do primeiro (5 edits) e reabriu decisões
  já tomadas. Nenhum spec estava fechado, então qualquer caso posterior podia mudar os
  anteriores.
- O usuário recebeu 30 arquivos de uma vez, sem aprovar nenhum individualmente.

**Lições:**

- **Um `/new-feature` produz um caso de uso.** O próximo só começa com o anterior
  fechado e aprovado.
- **Split vira backlog, não trabalho.** Detectar que a descrição contém vários casos de
  uso é papel do `use-case-design`. Dentro do pipeline ele deve: apresentar a lista em
  ordem de dependência numa única pergunta, desenhar só o escolhido e gravar os demais
  num backlog, com a descrição pronta para o próximo `/new-feature`. Sem reservar
  número, para não deixar buracos.
- **Ciclo de vida explícito no spec:** `status: draft | approved | implemented`.
  - `draft` na consolidação.
  - `approved` só com aprovação explícita do usuário no fim do pipeline.
  - `implemented` gravado pelo executor com build verde.
  - O executor só é oferecido para spec `approved`.
- **Trava de caso aberto:** novo `/new-feature` com algum spec em `draft` (ou pasta sem
  spec consolidado) é erro.
- **Spec aprovado é imutável.** O caso seguinte lê specs aprovados como contrato
  somente-leitura e reaproveita o agregado já modelado. Mudança necessária em caso
  aprovado vai numa seção `Impacto em UCs aprovados` do **novo** spec; o executor aplica
  no código. Opcional: hook que recusa edit em spec aprovado.
- Recomendar `/clear` entre execuções: contexto limpo e custo mensurável por caso de uso.
- Não adiar decisão "pensando no caso futuro" (houve `@Version` perguntado no create e
  de novo no update). Decidir o que o caso atual exige.

**Trade-off aceito:** às vezes um caso posterior ajusta o agregado (seção de impacto +
migration nova). Custa muito menos que manter N casos abertos na mesma conversa.

---

## 3. Contrato de entrada do `/new-feature`

**O que ocorreu:**

- O `/new-feature` exigia `UC-NNN-slug` como argumento, mas número e slug são **saída**
  da entrevista do `use-case-design`, que descobre a fronteira (e o split). O
  orquestrador pedia na entrada um dado que só existe depois do primeiro passo.
- O `use-case-design` espera uma descrição; o orquestrador passava um slug. As pontas
  não conversavam.
- Número e slug tinham dois donos (guardrail do orquestrador e passo final do
  `use-case-design`).
- O usuário passou texto livre, que pelo guardrail deveria dar erro. O modelo contornou
  o guardrail, dividiu o pedido por conta própria e passou um argumento híbrido
  (slug + contexto). Funcionou por improviso, não por contrato.

**Lições:**

- Número e slug têm um dono só: `use-case-design`.
- A entrada do orquestrador é uma **tabela fechada**. O modelo avalia em ordem e para na
  primeira linha que casar. Não existe linha "qualquer outro texto".

  | # | Entrada | Resultado |
  |---|---|---|
  | 1 | vazia | ✅ listar casos de uso com o `status` de cada um |
  | 2 | exatamente `^UC-\d{3}-[a-z0-9]+(-[a-z0-9]+)*$`, pasta existe, spec em `draft` ou ainda sem spec | ✅ retomar: pula entrevista, gera só os partials que faltam |
  | 3 | idem 2, spec `approved` ou `implemented` | ❌ erro: spec aprovado é imutável |
  | 4 | idem 2, pasta não existe | ❌ erro: caso não encontrado; para criar, descreva a feature |
  | 5 | contém `UC-`/`uc-` + dígito, mas não é exato (slug + contexto, slug malformado, dois slugs, caminho) | ❌ erro: argumento ambíguo |
  | 6 | texto livre, existe caso aberto | ❌ erro: retome ou aprove antes |
  | 7 | texto livre, nenhum caso aberto | ✅ novo: descrição passada **como está** ao `use-case-design` |
  | — | qualquer outra coisa | ❌ erro |

- **Erro tem formato fixo e encerra.** Motivo + uso correto. Depois do erro: nenhuma
  chamada de skill, escrita ou pergunta.

  ```text
  ❌ /new-feature: <motivo>.
  Uso: /new-feature <descrição da feature>   → novo caso de uso
       /new-feature UC-NNN-slug              → retomar caso em draft
       /new-feature                          → listar
  ```

- **O modelo não interpreta a entrada.** Não corrige slug, não separa slug de contexto,
  não deduz intenção. Guardrail que não proíbe o desvio explicitamente é contornado.
- **Decisão por comando, não por julgamento:** `grep -E` no argumento, `test -d` na
  pasta, `grep '^status:'` no spec — escritos no `SKILL.md`.
- Documentar que `/new-feature` é só manual (`disable-model-invocation`). O modelo
  tentou invocá-lo e foi bloqueado, obrigando o usuário a redigitar.

---

## 4. Fronteira: o pipeline de design nunca escreve em `src/`

**O que ocorreu:** o `persistence-architect` criou os arquivos de migration em
`src/main/resources/db/migration/`. A própria skill mandava fazer isso, enquanto o
contrato do orquestrador não listava migrations entre as saídas — as duas se
contradiziam.

**Lições:**

- O `/new-feature` e suas skills escrevem **somente** em `docs/`. Qualquer arquivo em
  `src/` é responsabilidade exclusiva do agent executor (`java-spring-boot-developer`).
- O SQL da migration vai como bloco de código dentro do partial de persistência, com o
  nome e o caminho-alvo. O executor tem um passo explícito para materializá-lo.
- Instrução em prosa não basta: um hook `PreToolUse` deve bloquear escrita em `src/**`
  enquanto houver execução de skill de design aberta.
- O contrato do orquestrador declara "never writes under `src/`".
- Convenção de nome de migration deve ser uma só entre regra, skill e executor (havia
  `V<N>__` de um lado e `V001__` do outro).

---

## 5. Fronteira: git só pelo `git-publish`

**O que ocorreu:** ao fim dos casos de uso, o modelo fez `git add`, `git commit` e
`git push` sem confirmação e sem invocar o `git-publish`. O orquestrador só previa o
`git-publish` depois do executor; sem executor não havia instrução, e o modelo
improvisou. Permissões amplas (`Bash(git *)`, `Bash(git push *)`) aprovadas durante a
execução deixaram passar sem prompt. A oferta do executor também foi pulada.

**Lições:**

- O orquestrador proíbe explicitamente `git commit`/`git push`. Commit só via
  `git-publish`, com as duas confirmações.
- Onde a instrução silencia, o modelo improvisa. Todo fim de fluxo precisa de passo
  explícito, inclusive o caminho "sem executor".
- A oferta do executor é obrigatória (pergunta explícita), não opcional.
- Nunca aprovar permissões amplas (`git *`, `python3 *`) durante um pipeline. `git push`
  fica sempre como `ask`.
- As skills do pipeline devem vir pré-autorizadas no `settings.json` gerado — foram
  aprovadas uma a uma durante a execução, e junto delas entraram as permissões amplas.

---

## 6. Retrabalho por decisão tomada no lugar errado

### 6.1 Requisito downstream descoberto tarde

**O que ocorreu:** o `use-case-design` perguntou ao usuário e decidiu "sem
idempotência". O `rest-api-architect` leu a regra de API, que exige `Idempotency-Key` em
POST de criação, e perguntou de novo. O `persistence-architect` foi re-invocado para
criar a tabela de idempotência e editar o partial duas vezes.

**Lições:**

- Uma decisão pertence à skill cuja regra a governa. O `use-case-design` registra o fato
  de negócio ("repetir o pedido cria duplicata?") e marca a decisão técnica como
  delegada.
- A ordem do pipeline precisa respeitar quem gera requisito para quem. REST gera
  requisito de schema (tabela de idempotência); persistência não gera requisito de REST.
  Ou REST roda antes de persistência, ou o orquestrador extrai os requisitos de
  infraestrutura compartilhada da regra de API antes do passo de persistência.

### 6.2 Pergunta cuja resposta seria descartada

**O que ocorreu:** o `use-case-design` perguntou "o que o endpoint retorna após criar?"
com opções de status HTTP. O usuário escolheu "201 sem corpo"; o `rest-api-architect`
sobrescreveu por regra. A skill já proibia fixar path, verbo e status — a instrução foi
ignorada.

**Lições:**

- Proibição em prosa é ignorada. Transformar em checklist antes de `AskUserQuestion`:
  "a pergunta cita código HTTP, verbo, path, idempotência ou nome de exceção? Então não
  pergunte".
- Incluir exemplo negativo explícito na skill.
- Pergunta ao usuário cuja resposta outra skill vai sobrescrever é pior que não
  perguntar: custa tempo do usuário e gera divergência.

### 6.3 Nome de exceção decidido cedo demais

**O que ocorreu:** o `use-case-design` nomeou uma exceção específica; o
`domain-modeling` rebaixou para genérica (regra: subclasse só com ≥2 call sites); o
quarto caso de uso encontrou o segundo call site e promoveu de volta, editando specs do
primeiro.

**Lições:**

- `use-case-design` descreve a situação ("nome já em uso"), não nomeia exceção.
- Com um caso por execução e spec aprovado imutável (seção 2), a promoção vai na seção
  de impacto do novo spec, sem editar o antigo.

---

## 7. Falhas de ferramenta evitáveis

| Falha | Lição |
|---|---|
| `zsh: no matches found: --include=*.java` | Glob em comando de skill sempre entre aspas: `--include='*.java'`. O shell do usuário pode ser zsh |
| `zsh: no matches found: docs/use-cases/UC-*` na injeção dinâmica `` !`ls ...` `` — o fallback `\|\| echo` nem roda | Em injeção dinâmica, usar `find <dir> -maxdepth 1 -name 'UC-*'` em vez de glob |
| `AskUserQuestion` com uma opção → `InputValidationError` | Pergunta com uma opção não é pergunta: decidir e registrar |
| Edits recusados fora do worktree; diretórios órfãos no checkout principal | Decidir worktree no guardrail de entrada, antes de qualquer escrita ou pergunta — nunca no meio do fluxo |
| Relatório de audit e `pricing.json` divergentes entre worktree e checkout principal | Definir onde o hook de audit grava quando a sessão está num worktree |

Falha repetida na mesma ferramenta é sinal de instrução ruim na skill, não de azar.

---

## 8. Audit

**O que ocorreu:**

- Uma etapa de ~12 s apareceu como a mais cara (10m38s, 23%). A janela absorveu commit,
  push, `/compact` e tarefas posteriores não relacionadas ao `/new-feature`.
- ~11 min iniciais eram espera pela resposta do usuário, contados como trabalho.
- `/compact` manual foi reportado como incidente.

**Lições:**

- A execução fecha no primeiro prompt do usuário que não seja resposta de pergunta, não
  no próximo `Stop`.
- A duração de um nó termina no último evento dele, não no início do próximo.
- Tempo de espera por `AskUserQuestion` é medido à parte.
- Diferenciar compactação manual de automática.

---

## 9. Consistência entre skills, regras e `CLAUDE.md`

**O que ocorreu:** divergências encontradas no mesmo fluxo:

- Uma skill usava `UC-NNN` e `NNNN` na mesma frase.
- O `use-case-design` nomeava a implementação `<Verb><Noun>Service` atrás de uma porta
  `<Verb><Noun>UseCase`, enquanto o `CLAUDE.md` define o caso de uso como classe
  concreta, sem interface, chamada `<Verb><Noun>UseCase`.
- Convenção de nome de migration diferente entre regra e executor.

**Lição:** cada fato tem um dono. Skills citam a regra; não reescrevem convenções. Uma
verificação periódica (ou teste do meta-repo) que busque padrões de nome divergentes
entre skills, agents, regras e `CLAUDE.md` evita que o modelo escolha um lado ao acaso.

---

## Resumo das ações para o meta-repo

1. Pipeline de design nunca escreve em `src/`: SQL no partial, hook bloqueando, executor
   materializa.
2. Git só via `git-publish`; proibição explícita no orquestrador; sem permissões amplas.
3. Tabela fechada de entrada do `/new-feature`; todo o resto é erro com mensagem fixa.
4. Um caso de uso por execução; split vira backlog; `status` no spec; trava de caso
   aberto; spec aprovado imutável.
5. Decisões técnicas fora do `use-case-design` (status HTTP, idempotência, nome de
   exceção), com checklist antes de perguntar.
6. Ordem do pipeline respeitando quem gera requisito para quem (REST → persistência).
7. Custo: sem narração entre passos, writes paralelos, consolidado por referência,
   caminho curto para casos triviais, sem web nas skills de design.
8. Globs entre aspas e `find` em injeção dinâmica.
9. Worktree decidido na entrada.
10. Audit com janelas corretas e espera do usuário separada.
11. Uma única convenção por fato (nomes de UC, use case, migration).
