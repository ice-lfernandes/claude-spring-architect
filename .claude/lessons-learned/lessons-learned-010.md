# Lessons learned — pipeline `/new-feature` completo com mensageria (UC-003-request-customer-kyc-validation, demo-clean-arch-single-module)

Origem: primeira execução ponta a ponta do `/new-feature` em que o passo 5
(`messaging-architect`) realmente roda — descrição livre em português pedindo que, após
a criação do Customer, um evento Kafka seja publicado para um serviço externo de KYC, com
`status` do cliente saindo `KYC_IN_PROGRESS` na criação. O pipeline rodou em duas
invocações (`/new-feature <descrição>` e depois `/new-feature UC-003-request-customer-kyc-validation`
para retomar), produziu as seis parciais, `UC-003-spec.md` com `status: approved`, uma
entrada nova no `BACKLOG.md` (o use case de callback, separado pelo teste de fronteira) e
uma alteração real em `docker-compose.yml` (serviço `kafka`, via `docker-architect`).
Usuário escolheu "Not now" no executor e "No, skip" no `git-publish`.

Números do próprio trilho de auditoria (`.claude/audit-usage/history.jsonl` e
`nodes.jsonl`, projeto de demo):

| Execução | `tokens_billable` | duração | arquivos |
|---|---|---|---|
| run 1 — `10:37:06`, descrição livre | 97.993 | 207s | 2 |
| run 2 — `10:48:26`, resume | 224.415 | 936s | 7 |
| **total** | **322.408** | ~19min | 9 |

`tokens_self` por nó, na ordem em que rodaram: `use-case-design` 56.963 ·
`domain-modeling` 37.694 · `rest-api-architect` 20.636 · `persistence-architect` 32.316 ·
`messaging-architect` 52.065 · `docker-architect` 11.842 · `test-architect` 49.668 ·
`git-publish` 9.537. Orquestrador: 41.030 (run 1) + 10.657 (run 2).

O pipeline **funcionou** — o teste de fronteira separou dois use cases corretamente, a
imutabilidade de spec aprovada foi respeitada via `## Impact on approved use cases`, o
guardrail previu o `ROOT_IGNORED_BY_PARENT` antes do `git-publish` tropeçar nele. Os itens
abaixo são as lacunas que a execução expôs, não falhas de resultado.

---

## 1. O padrão transactional outbox foi inferido pelo modelo; `messaging.md` não fixa a relação entre a publicação e a transação, e o template da própria skill assume a forma oposta

**Onde:** `@.claude/rules/messaging.md:31-42` (§ Delivery semantics — fixa at-least-once,
`acks=all`, `enable.idempotence=true` e consumidor idempotente, e nunca diz *onde* a
publicação fica em relação ao commit); `.claude/skills/messaging-architect/templates/KafkaProducerAdapter.java.example:12,48,54,56`
(o adapter da porta de saída importa e chama `KafkaTemplate` direto — forma
publish-after-commit); `.claude/skills/use-case-design/examples/UC-106-confirm-order/00-caso-de-uso.md:90`
(*"`ConfirmOrderUseCase`: publishes after commit, not before"* — a única forma de
publicação documentada no repo inteiro).

**O que ocorreu:** `grep -ril outbox .claude/` no meta-repo retorna **um único** arquivo, e
só numa frase incidental de linha de tabela de testes do exemplo UC-106
(*"persists state and the outbox/event publication"*). Nenhuma regra, nenhum template,
nenhum passo de skill nomeia o padrão. O modelo montou o cardápio de opções no
`AskUserQuestion` do `domain-modeling` por conta própria (`Outbox: same transaction, async
relay` vs. publicação direta), o usuário escolheu outbox, e a partir daí a parcial
`25-mensageria.md` teve que registrar **divergência contra o template da sua própria
skill**, em prosa, na tabela de cabeçalho:

> *"`templates/KafkaProducerAdapter.java.example` assumes the outbound port's
> implementation calls `KafkaTemplate` directly (...). `00-caso-de-uso.md`/`10-dominio.md`
> fixed **outbox instead**."*

Ou seja: o desenho saiu coerente, mas por cima do exemplar da skill, não a partir dele.

**Lições:**

- `@.claude/rules/messaging.md` § Delivery semantics precisa nomear as formas reais de
  publicação e o critério de escolha entre elas — no mínimo **publish-after-commit**
  (a forma atual dos templates) e **outbox + relay**. Hoje a regra fixa *garantia de
  entrega* e deixa *momento de publicação* aberto, e é justamente o momento que decide
  se existe uma tabela nova, uma porta nova e um componente `@Scheduled` no projeto.
- `messaging-architect` passo 3 precisa de um eixo de entrevista pra isso: *"o evento
  precisa sobreviver a uma queda de broker entre o commit e o publish?"*. A tabela de
  eixos atual (`SKILL.md:113-121`) só pergunta partition key, group id, `auto-offset-reset`,
  ordenação e tabela de dedupe — todos eixos de consumidor. Um use case que só produz,
  como este, não encontra nenhum eixo aplicável e a skill registra *"No `AskUserQuestion`
  this pass"*, enquanto a decisão mais cara do desenho (outbox) acontece duas skills antes,
  no `domain-modeling`, que não é dono do assunto.
- Uma skill cujo template contradiz a parcial que ela mesma escreveu é um bug de exemplar,
  não do modelo. Se as duas formas ficam, precisa de um segundo par de templates
  (`OutboxRelayPublisher.java.example` + o bloco de configuração dele); se só uma fica, o
  eixo de entrevista some junto e o modelo para de ter o que inventar.
- Sem registro, a próxima sessão que chegar no mesmo ponto pode escolher a outra forma
  pelo mesmo projeto, sem motivo técnico pra divergência — exatamente o risco que o
  `lessons-learned-009` já levantou pra escolha de vendor de observabilidade.

---

## 2. `outbox_events` e `OutboxRelayGateway` são infraestrutura compartilhada e não têm exemplar, embora o repo já tenha o mecanismo pronto para esse caso (`idempotency_keys`, passo 4a)

**Onde:** `.claude/skills/persistence-architect/SKILL.md:132-142` (passo **4a** —
`idempotency_keys` modelada uma vez, compartilhada por todo endpoint, com
`templates/IdempotencyKeyTable.sql.example`, `IdempotencyKeyStore.java.example` e
`IdempotentExecution.java.example`); nada equivalente para outbox em
`persistence-architect/templates/` nem em `messaging-architect/templates/`.

**O que ocorreu:** a tabela `outbox_events` que saiu desta execução é explicitamente
compartilhada — `UC-003-spec.md` § 3 registra *"`outbox_events` `NEW` — shared, reused by
every later event"*. Mesma natureza de `idempotency_keys`: uma tabela por projeto, não
por agregado. Mas ela foi desenhada do zero, em prosa, dentro de `20-persistencia.md`:
colunas, tipo `jsonb` do payload, índice parcial, e depois as três colunas de retry
(`attempts smallint`, `last_error text`, `dead_lettered boolean`) acrescentadas numa
segunda passada. A porta `OutboxRelayGateway` e o record `PendingOutboxEvent` também
nasceram em prosa, dentro de `25-mensageria.md` § 2, com assinatura escrita à mão.

**Lições:**

- Falta um passo **4b** em `persistence-architect`, espelho exato do 4a: quando
  `25-mensageria.md` exigir outbox e o passo 2 não encontrar `outbox_events`, modelar uma
  vez a partir de `templates/OutboxEventTable.sql.example` +
  `templates/OutboxEventStore.java.example`, e reusar depois. O mecanismo já existe e já
  está provado pra `idempotency_keys` — falta só a segunda instância.
- A porta `OutboxRelayGateway` (`claimPending`/`markPublished`/`markFailed`) e o
  `PendingOutboxEvent` são forma, não decisão de negócio: pertencem a
  `templates/*.example`, por invariante 3. Escritos em prosa, cada UC futuro de mensageria
  vai derivar uma assinatura diferente pro mesmo conceito.
- As três colunas de retry (`attempts`, `last_error`, `dead_lettered`) e o índice parcial
  `WHERE published_at IS NULL AND NOT dead_lettered` são consequência mecânica da seção
  § Retry and DLQ da regra, não escolha por use case. Num template elas vêm de graça; em
  prosa elas chegaram atrasadas, e foi isso que forçou o item 3.

---

## 3. Ordem do pipeline: messaging roda depois de persistence, e isso fez persistence rodar duas vezes — o mesmo modo de falha que a nota "Why REST runs before persistence" existe para evitar

**Onde:** `.claude/skills/new-feature/SKILL.md:70-75` (§ *Why REST runs before
persistence* — *"The order follows who generates requirements for whom (...) With
persistence first, the table was discovered after the partial was written, and persistence
ran twice"*); a mesma seção lista `messaging-architect` como passo **5**, depois de
`persistence-architect` no passo 4.

**O que ocorreu:** `messaging-architect` descobriu, no passo 5, requisitos de schema que
`20-persistencia.md` já estava escrito sem: `attempts`, `last_error`, `dead_lettered`, e a
mudança do índice parcial. A parcial de persistência foi **editada três vezes** depois de
pronta, e a tabela de parciais do `UC-003-spec.md` registra a cicatriz:

> `| 20-persistencia.md | persistence-architect | ✅ (revised once, after 25-mensageria.md flagged the relay's own port/schema need) |`

`25-mensageria.md` § 2 inclusive descreve o hand-off à mão, inventando o mecanismo na hora:
*"This is a **follow-up requirement on this same UC-003 folder** (...) `/new-feature`
resumes `persistence-architect` once more before consolidation, the same way
`20-persistencia.md` itself resumed after `30-rest.md`'s own schema requirements."* —
o modelo reconheceu que estava reproduzindo, em prosa, o mecanismo que o repo já formalizou
pro REST.

**Lições:**

- `messaging-architect` depende só do passo 2 (`10-dominio.md`), conforme a própria tabela
  *Integrates with* do orquestrador. Mover messaging para antes de persistence é grátis e
  resolve a causa, não o sintoma.
- Independente da ordem, `25-mensageria.md` precisa de um bloco **`Schema requirements`**,
  idêntico ao bloco 4 de `30-rest.md` (`rest-api-architect/SKILL.md:` passo 7, *"Write the
  schema requirements down (...) a requirement left in prose here is a second persistence
  pass later"*). A frase já está escrita na skill vizinha, descrevendo exatamente o que
  aconteceu aqui.
- Fazer as duas coisas é o correto: a reordenação evita a segunda passada, o bloco a
  documenta quando ela for inevitável (uma execução retomada, ou `messaging-architect`
  rodado na mão). Hoje o repo tem o mecanismo pra um caminho (REST) e não pro outro
  (mensageria), embora os dois gerem requisito de schema.
- A tabela de precedência da consolidação (`new-feature/SKILL.md` § Consolidation) dá
  `Table, column, key, index, migration` para `20-persistencia.md`. Com messaging gerando
  coluna, essa linha fica ambígua na primeira vez que as duas parciais discordarem de
  verdade — vale explicitar que o requisito nasce em mensageria e a forma final é de
  persistência.

---

## 4. A proibição de `infrastructure.messaging` depender de `infrastructure.persistence` foi derivada corretamente pelo modelo, mas nada mecânico a garantia antes do código existir

**Onde:** tabela de módulos do `CLAUDE.md` do projeto gerado (`infrastructure.messaging`
→ *May depend on: `application`, `domain`*); `.claude/forbidden-imports.txt` +
`ArchHook.java check`, que só agem sobre arquivos escritos; `@.claude/rules/architecture-ddd.md`
§ Adapters, que fala de "um adapter só conhece as abstrações que precisa" sem nomear o
caso de um adapter ler estado persistido por outro.

**O que ocorreu:** o relay do outbox precisa ler e atualizar `outbox_events`, cuja entidade
JPA vive em `infrastructure.persistence`. O modelo identificou o conflito e criou
`OutboxRelayGateway` na camada de application, com a justificativa correta em
`25-mensageria.md` § 2 (*"same discipline `IdempotentExecution` already applies for
`idempotency_keys`"*). Bom desenho — e inteiramente dependente de o modelo ter lido a
tabela de módulos e ligado os pontos. O `ArchHook.java check` e o ArchUnit só reprovariam
o import errado **depois** do executor escrever a classe, ou seja, depois de a spec já ter
consagrado o desenho errado.

**Lições:**

- Vale uma linha em `@.claude/rules/architecture-ddd.md` § Adapters: um componente que
  varre ou poll-a estado persistido (relay, scheduler, reconciliador) lê esse estado por
  uma abstração da camada de application, nunca pela entidade ou repositório de um adapter
  irmão. É norma de direção de dependência, é leaf, e não nomeia skill nenhuma —
  respeita o invariante 1.
- O acerto aqui foi sorte de leitura, não enforcement. A regra escrita transforma uma
  derivação em critério, e é ela que o `messaging-architect` cita quando o próximo relay
  aparecer.

---

## 5. As injeções de pré-checagem no frontmatter usam caminho relativo e quebram silenciosamente quando o cwd do shell derivou — `docker-architect` reportou que `docker-compose.yml` não existia

**Onde:** `.claude/skills/docker-architect/SKILL.md:21` —
`` !`test -f docker-compose.yml && grep -E "^\s{2}\S+:$" docker-compose.yml || echo "(no docker-compose.yml at project root — run project-bootstrap first)"` ``;
`.claude/skills/use-case-design/SKILL.md:16` — `` !`find docs/use-cases -mindepth 1 -maxdepth 1 -type d -name 'UC-*' ...` ``;
mesma forma relativa nas injeções de `domain-modeling`, `rest-api-architect`,
`persistence-architect`, `messaging-architect` e `test-architect`.

**O que ocorreu:** o bloco `## Current compose state` do `docker-architect` veio com
`(no docker-compose.yml at project root — run project-bootstrap first)` com o arquivo
existindo e completo na raiz. Causa: um `Bash` anterior tinha feito
`cd .../skills/messaging-architect && cat templates/...`, e o shell persistente ficou
dentro do diretório da skill. A injeção rodou naquele cwd e o `test -f` falhou.

Consequência prática: a **entry rule** da skill (*"without `docker-compose.yml` at the
project root, there's nothing to extend (...) stop and say to run `project-bootstrap`"*)
foi acionada por um falso negativo. Um estado injetado que erra não é ausência de
informação — é informação errada, e ela ativa um guard que aborta a skill. Só não abortou
porque o procedimento inteiro já estava em contexto e foi possível verificar o disco por
fora e seguir à mão.

**Lições:**

- Toda injeção `` !`...` `` de frontmatter precisa ser independente de cwd:
  `${CLAUDE_PROJECT_DIR}/docker-compose.yml`, `find ${CLAUDE_PROJECT_DIR}/docs/use-cases ...`.
  O repo já usa essa variável nos hooks de `settings.json`
  (`java ${CLAUDE_PROJECT_DIR}/.claude/hooks/ArchHook.java audit compact`) — é idioma
  conhecido e o conserto é de uma linha por skill.
- É uma lacuna **latente em sete skills**, não um acidente de uma. As outras seis passaram
  nesta execução porque o cwd estava na raiz na hora; nenhuma delas é mais robusta que a
  que falhou.
- Uma injeção que erra e mente é pior que uma que não roda. O fallback
  `|| echo "(no docker-compose.yml...)"` não distingue "não existe" de "não consegui
  olhar" — vale o `||` dizer qual dos dois foi, ou o `test` ser absoluto e o fallback
  voltar a significar só uma coisa.
- Regra de conduta que vale registrar: `cd` para dentro de um diretório de skill num
  `Bash` intermediário contamina todas as injeções seguintes da sessão. Ler template com
  `Read` em caminho absoluto, não com `cd` + `cat`.

---

## 6. A tag de imagem do Kafka precisou ser combinada entre `docker-architect` e `test-architect` por um comentário de YAML, e nenhum hook verifica se elas continuam iguais

**Onde:** `.claude/skills/docker-architect/SKILL.md` passo 4 (*"Use the **same image tag**
`test-architect` pinned, when one exists (...) If none is pinned yet, use the tag the
engine's template ships with and say so in the report"*); `docker-compose.yml:62-65` do
projeto de demo, onde a combinação virou comentário; `ArchHook.java` modo `compose`, que
checa serviço `running` e colisão de porta de host e **não** compara tag nenhuma
(`grep -n "DockerImageName\|image:" .claude/hooks/ArchHook.java` não retorna nada).

**O que ocorreu:** `docker-architect` rodou **antes** de `test-architect` (encadeado por
`messaging-architect` passo 9), então não havia tag pinada pra espelhar. Escolheu
`apache/kafka:3.8.0` e deixou a instrução na direção inversa, num comentário do compose:

> *"Tag not yet mirrored anywhere else — first Kafka use case in the project,
> `test-architect` pins the same tag in `TestcontainersConfiguration.java` when it runs
> for this UC, not the other way around."*

`test-architect` cumpriu — `40-testes.md` registra `apache/kafka:3.8.0` no bloco de dados
e doubles. Funcionou porque as duas skills rodaram na mesma janela de contexto, na mesma
execução. Numa execução retomada depois de `/clear`, ou com `test-architect` rodado na mão
meses depois, o único elo entre as duas metades é um comentário de YAML que ninguém é
obrigado a ler.

**Lições:**

- `ArchHook.java` modo `compose` já é o dono do assunto e já roda dentro do `doctor`:
  comparar cada `image:` do `docker-compose.yml` com cada `DockerImageName.parse` em
  `src/test` é a versão mecânica de uma promessa que hoje é prosa em duas skills. Pelo
  invariante 6, se a tag *tem* que bater, é hook, não parágrafo.
- O protocolo de handshake está escrito nas duas skills como condicional ("quando já
  existir uma tag") e a ordem real de execução decide qual lado manda. Os dois casos estão
  documentados, mas o resultado é que a fonte da verdade muda conforme a ordem — o hook
  remove a ambiguidade sem precisar escolher um dono.
- `project-bootstrap` já passou por isso com Postgres: o comentário em
  `docker-compose.yml:23-25` diz *"Tag matched to the one pinned in
  TestcontainersConfiguration.java"*. Duas ocorrências do mesmo acordo mantido à mão é o
  sinal de que ele merece verificação.

---

## 7. A branch "Not now" do `/new-feature` assume que a fase de design não escreve nada fora de `docs/`, e isso é falso sempre que persistence ou messaging encadeia `docker-architect`

**Onde:** `.claude/skills/new-feature/SKILL.md:414-416` — *"**Not now** → **invoke**
`git-publish` (...) with the paths `docs/use-cases/UC-NNN-<slug>/` and
`docs/use-cases/BACKLOG.md` as the only paths to stage."*

**O que ocorreu:** esta execução, sendo design-only, produziu uma alteração real fora de
`docs/`: o serviço `kafka` e o volume `kafka-data` em `docker-compose.yml`, escritos por
`docker-architect` — que é dono legítimo daquele arquivo e foi encadeado corretamente por
`messaging-architect` passo 9. Seguir a instrução literal deixaria essa mudança órfã,
não commitada, sem ninguém mencionando. Foi preciso desviar da instrução e declarar o
desvio no `args` do `git-publish`.

**Lições:**

- A lista de paths dessa branch precisa ser "a pasta do UC, o `BACKLOG.md`, mais qualquer
  arquivo que um owner não-design tenha escrito nesta execução" — hoje na prática isso
  significa `docker-compose.yml` e `docker/init/**`, os dois territórios do
  `docker-architect`.
- A premissa embutida ("design não toca em nada fora de `docs/`") é quase verdadeira e por
  isso perigosa: vale só até o pipeline encadear uma skill de infraestrutura, o que ele faz
  desde que `persistence-architect` passo 9 e `messaging-architect` passo 9 existem. Uma
  premissa que já era falsa quando foi escrita.
- O § Contract do orquestrador diz *"**Never writes under `src/`**"* e lista exceções
  nomeadas (os dois installers). A mesma precisão falta pro que o design escreve **fora**
  de `src/` e fora de `docs/` — `docker-compose.yml` não aparece em nenhuma das duas
  listas de Writes.

---

## 8. `AskUserQuestion` com uma única opção falhou no meio da entrevista de domínio; o pitfall está documentado, mas só no `CLAUDE.md` do projeto gerado e nunca no passo que monta o batch

**Onde:** `.claude/skills/project-bootstrap/templates/root.CLAUDE.md.example:66-67` — o
único lugar do meta-repo onde o problema está escrito (*"`AskUserQuestion` with one option
fails with `InputValidationError`. A question with one option isn't a question: decide,
and record the decision."*); ausente do `CLAUDE.md` § Known pitfalls **deste** repo;
ausente do passo 3 de `domain-modeling` (`SKILL.md:` *"Ask only what's missing"*, sem
limite de batch e sem mínimo de opções), e ausente também das versões de
`messaging-architect:113`, `persistence-architect:109`, `rest-api-architect:109` e
`test-architect:115`, que só dizem *"at most 4 questions per call"*.

**O que ocorreu:** a entrevista do `domain-modeling` montou quatro perguntas de uma vez;
duas tinham uma opção só (efeitos colaterais, e quais invariantes acrescentar). A chamada
inteira foi rejeitada:

```
InputValidationError: ... "too_small" ... path: ["questions",1,"options"]
```

Correção: decidir em silêncio os dois itens de opção única — que é exatamente o que a
norma manda — e reenviar só as duas perguntas com escolha real. Uma ida e volta perdida,
um `AskUserQuestion` inteiro descartado.

**Lições:**

- A norma está certa e no lugar errado pra quem precisa dela. Quem monta o batch é o passo
  3 de cada skill de pipeline, e é lá que falta a linha: *"nunca uma pergunta com menos de
  2 opções reais — decida esse item e registre a decisão na parcial"*. O limite superior
  ("no máximo 4") está escrito em quatro skills; o limite inferior, em nenhuma.
- `domain-modeling` passo 3 é o único do pipeline sem **nenhum** enquadramento de batch —
  nem máximo, nem mínimo — e foi exatamente onde a falha aconteceu. Não é coincidência que
  valha ignorar.
- O `CLAUDE.md` deste meta-repo não carrega o pitfall, embora as skills que sofrem dele
  rodem aqui durante o desenvolvimento. Por invariante 2 a norma tem um dono só; o que
  falta é a **citação** nos pontos de uso, não uma segunda cópia.

---

## 9. `use-case-design` fixa o *tipo* de um componente de domínio na tabela de componentes antes de os critérios de `value-objects.md` serem aplicados, e isso gera divergência previsível e recorrente

**Onde:** tabela de componentes de `00-caso-de-uso.md` (produzida por
`use-case-design/templates/use-case-spec.md.example`), contra
`@.claude/rules/value-objects.md` critério 3; resultado registrado em `UC-003-spec.md`
§ Resolved divergences.

**O que ocorreu:** a spec-mãe declarou `CustomerStatus` como value object genérico.
`domain-modeling` aplicou o critério 3 da regra (conjunto fechado, sem regra de formação a
validar em runtime) e corrigiu pra `enum`. Divergência legítima, resolvida pelo mecanismo
certo, e registrada:

> `| CustomerStatus's shape | generic value object (00-caso-de-uso.md's component table) | plain enum | 10-dominio.md, value-objects.md criterion 3 |`

O problema não é a divergência ter sido resolvida — é ela ser estruturalmente garantida.
`use-case-design` não lê `value-objects.md` (não está no § Contract dele) e mesmo assim
escreve a forma do componente. Todo campo de conjunto fechado vai repetir isso.

**Lições:**

- Pra campo de domínio, a tabela de componentes da spec-mãe deveria nomear o componente e
  deixar a forma aberta — *"value object ou enum — `domain-modeling` decide"* — em vez de
  cravar uma das duas. O dono da forma é `domain-modeling`, por § Boundary da própria
  `use-case-design`; cravar antes é opinar fora do território.
- Divergência prevista não é auditoria, é ruído. A seção § Resolved divergences existe pra
  registrar conflito real entre donos; enchê-la com colisões que o desenho do pipeline
  garante gasta linha de spec e atenção de leitor sem informar nada.
- Segunda divergência da mesma execução (`OutboxEventStore` → `DomainEventPublisher`) é do
  mesmo tipo: a spec-mãe nomeou uma porta de saída, `domain-modeling` renomeou porque o
  nome vazava a técnica de implementação. Duas de duas divergências do run vêm da spec-mãe
  decidindo coisa de domínio.

---

## 10. Achado de débito técnico não tem destino: `40-testes.md` sinalizou uma lacuna real de masking e ela ficou em prosa, dentro de uma parcial que ninguém vai reabrir

**Onde:** `docs/use-cases/UC-003-request-customer-kyc-validation/40-testes.md:86-93`
(*"Known project-level gap, not introduced by this use case, not this partial's to
close"*); `docs/use-cases/BACKLOG.md`, cujo template
(`use-case-design/templates/backlog.md.example`) carrega só use cases futuros.

**O que ocorreu:** `test-architect` percebeu que `RegisterCustomerResponse` não implementa
`LogMask`/`@MaskSensitiveData` em `name` e `securityNumber`, embora a maquinaria de masking
esteja instalada desde o `commons-logging-installer` e os dois campos estejam marcados como
sensíveis desde o UC-001 — ou seja, `GlobalHttpMethodLogAspect` loga o DTO cru em toda
requisição, hoje, em produção. Achado correto, fora do escopo do UC-003, corretamente
declarado fora de escopo. E depositado num parágrafo de uma parcial de testes de um use
case que nem foi implementado.

**Lições:**

- `BACKLOG.md` é o destino de use case futuro, não de débito técnico. Um achado desses não
  vira `/new-feature`: não tem trigger, não tem efeito colateral novo, não tem fronteira a
  delimitar. Precisa de destino próprio — segunda tabela no `BACKLOG.md` com dono
  declarado, ou `docs/DEBT.md`.
- É o segundo lugar do pipeline onde uma skill encontra algo verdadeiro e não tem onde
  colocar; o primeiro é `## Impact on approved use cases`, que existe justamente porque
  esse problema já foi resolvido uma vez, pro caso de mudança em spec aprovada. O padrão é
  conhecido, falta aplicá-lo à classe "lacuna pré-existente".
- Quem mais provavelmente encontra esse tipo de coisa é `test-architect`, porque é a única
  skill que olha o projeto inteiro atrás de cobertura. Vale a skill ter no passo 8 um
  lugar explícito pra despejar achado fora de escopo, em vez de improvisar um parágrafo
  em negrito no fim do bloco 4.

---

## 11. Custo: três das oito skills da execução não fizeram entrevista nenhuma e rodaram inline de todo jeito — 113.575 tokens (≈35% do run) sem precisar do contexto conversacional

**Onde:** `.claude/skills/messaging-architect/SKILL.md` § *Why this is a skill and not a
subagent*; `test-architect` § *Why design mode isn't a subagent, and setup mode is*
(*"Step 3 of design mode goes back to asking the user what no prior partial fixes (...) A
subagent doesn't see the conversation, so design mode stays inline"*); `docker-architect`
§ *Why this is a skill and not a subagent*; `@CLAUDE.md` invariante 5 (agente existe por
contexto, ferramenta ou modelo).

**O que ocorreu:** `tokens_self` de `messaging-architect` 52.065, `test-architect` 49.668,
`docker-architect` 11.842 — e as três parciais registram, por escrito, que não houve
pergunta nenhuma:

- `25-mensageria.md`: *"No `AskUserQuestion` this pass: this use case only **produces** (...)
  there's no consumer-side axis to ask about."*
- `40-testes.md`: *"No `AskUserQuestion` this pass: `30-rest.md` § 5 and `25-mensageria.md`
  § 4 already name every scenario that needs its own case."*
- `docker-architect`: com pasta de UC no argumento, o passo 2 lê as parciais e vai direto
  pro 3, sem entrevista.

Somadas, 113.575 tokens `self` de trabalho puramente mecânico, na mesma janela onde já
estavam os seis SKILL.md anteriores, as seis parciais e todas as entrevistas. Nada disso é
descarregado: `test-architect`, no passo 6, custa 49.668 pra escrever um arquivo de 100
linhas porque carrega tudo que veio antes.

**Lições:**

- A justificativa "não é subagent porque o passo 3 entrevista o usuário" é verdadeira pra
  invocação **manual** e falsa pra invocação **encadeada pelo `/new-feature`** — nesse
  caminho as parciais upstream já respondem os eixos, e esta execução demonstrou isso em
  três skills de três. A forma deveria depender do caminho de invocação, não da skill.
- Proposta a avaliar: um **modo orquestrado** por skill de pipeline, que roda em subagent
  quando os eixos de entrevista já estão fixados pelas parciais anteriores, e mantém a
  forma inline pro `/skill` na mão. Cai exatamente no motivo 1 do invariante 5 (preservar
  contexto), que é o único dos três que se aplica aqui — ferramentas e modelo continuam
  os mesmos.
- O critério mecânico de decisão existe e já está nas parciais: se a skill escreveria
  *"No `AskUserQuestion` this pass"*, ela não precisava da conversa. Isso é detectável
  antes de invocar, lendo as parciais que o orquestrador já leu.
- A economia entre as duas invocações de `/new-feature` deste run foi, na prática, zero
  desperdício: o guardrail linha 2 pegou o resume e pulou `use-case-design`. O run 1 fechou
  porque o hook de auditoria fecha run em `Stop`, não porque houve retrabalho. O custo a
  atacar é o acúmulo **dentro** de um run, não a fronteira entre runs — e `/clear` entre
  runs, que o `CLAUDE.md` já recomenda, não toca nesse acúmulo.

---

## 12. O projeto de exemplo é ignorado pelo repo pai, então todo run termina sem commit — e os dois gates do `git-publish` são código morto no único lugar onde são exercitados

**Onde:** `.gitignore` do meta-repo (`examples/` ignorado inteiro);
`.claude/skills/new-feature/SKILL.md` § Entry guardrail passo 2, terceiro caso
(`ROOT_IGNORED_BY_PARENT`); `.claude/skills/git-publish/SKILL.md` passo 1, mesma condição.

**O que ocorreu:** a parte de framework **funcionou como projetada**: o guardrail detectou
o `git check-ignore -q .` na entrada, carregou o fato pro contexto da execução, passou pro
`git-publish` no `args`, e o `git-publish` avisou em vez de assumir que o
`git status --porcelain` (que só mostrava um arquivo não relacionado, do repo pai) fosse a
feature. Exatamente o que os dois trechos prometem.

O resultado, porém, é beco sem saída: o projeto nunca teve `.git` próprio, o pai o ignora
de propósito, e a única saída oferecida é `git init` de um repo novo e avulso — que o
usuário recusou, com razão. Os artefatos deste run (sete arquivos de spec, uma entrada de
backlog, o serviço `kafka`) ficam sem versão, sem diff, sem review.

**Lições:**

- Como o projeto de demo é o único lugar onde o pipeline roda de verdade, o efeito é que
  **o gate 1 e o gate 2 do `git-publish` nunca são exercitados até o fim** — o caminho
  feliz da skill (commit + push) não tem cobertura prática em nenhuma execução real deste
  repo. Uma skill cujo caminho principal nunca roda é uma skill que ninguém sabe se
  funciona.
- Duas saídas possíveis, e ambas são decisão de topologia de repositório, não de skill:
  negar o ignore pros artefatos que interessam (`!examples/*/docs/`,
  `!examples/*/docker-compose.yml`), ou o projeto de exemplo virar submódulo/repo próprio
  de verdade. Enquanto nenhuma for tomada, todo run futuro acaba igual a este e o aviso do
  guardrail é informação correta sobre um problema que ninguém está resolvendo.
- Sem histórico no exemplo, o próprio ciclo de lessons-learned perde matéria-prima: não há
  diff entre UC-001, UC-002 e UC-003 pra comparar o que o pipeline gerou em cada etapa da
  evolução do meta-repo.
