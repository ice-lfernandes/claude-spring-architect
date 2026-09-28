# Lessons learned — segundo `/new-feature` do mesmo caso KYC (UC-003-initiate-kyc-verification, demo-clean-arch-single-module)

Origem: redesenho completo do caso que o `lessons-learned-010` documentou. A pasta
anterior (`UC-003-request-customer-kyc-validation`) tinha sido apagada do disco antes
deste run; o pipeline rodou do zero com a mesma descrição livre em português, em duas
invocações (`/new-feature <descrição>` e depois
`/new-feature UC-003-initiate-kyc-verification` para retomar), e produziu as seis
parciais, `UC-003-spec.md` com `status: approved`, entrada nova no `BACKLOG.md` (UC-004,
o consumidor do callback) e alteração real em `docker-compose.yml` (serviço
`schema-registry`, via `docker-architect`). Usuário escolheu "Agora não" no executor e
"Só local" no push.

**Os itens do `010` foram aplicados e este run se beneficiou deles.**
`@.claude/rules/messaging.md` hoje tem § Publication timing com as duas formas nomeadas,
`messaging-architect` passo 3 tem o eixo de momento de publicação, e o template de
domínio tem a coluna `Durability` — o outbox foi escolhido **pela regra**, lendo
`must not be lost`, não improvisado como da primeira vez. O ciclo funciona. O que segue é
o que ainda escapou.

Números do próprio trilho (`.claude/audit-usage/`):

| Execução | modelo | `tokens_billable` | custo | duração | arquivos |
|---|---|---|---|---|---|
| run 1 — `15:46:31`, descrição livre | Sonnet 5 | 84.678 | USD 0,73 | 282s | 2 |
| run 2 — `15:58:01`, resume | Opus 5 | 186.946 | USD 7,52 | 870s | 7 |
| **total** | — | **271.624** | **USD 8,25** | ~19min | 9 |

`tokens_self` por nó no run 2: `domain-modeling` 30.037 (USD 0,91) · `rest-api-architect`
25.530 (0,81) · `messaging-architect` 26.787 (1,01) · `docker-architect` 14.455 (0,77) ·
`persistence-architect` 26.431 (0,98) · `test-architect` 37.059 (1,60) · `git-publish`
14.732 (1,21).

**Troca de modelo no meio do caso multiplicou o custo por 10 com o mesmo pipeline.** Run 1
em Sonnet 5 custou USD 0,73; run 2 em Opus 5, USD 7,52 — mesma skill, mesmo caso, mais
parciais. O trilho não registra o modelo por nó, só no cabeçalho do relatório, então a
comparação acima teve que ser reconstruída à mão. Vale registrar o modelo em cada linha de
`history.jsonl`.

---

## 1. `disable-model-invocation: true` não bloqueou a invocação pelo modelo

**Onde:** `.claude/skills/new-feature/SKILL.md:4` (frontmatter, flag presente);
`SKILL.md:16-18` (*"`disable-model-invocation: true` hides this skill from the model: the
`Skill` tool can't call it, and trying is blocked"*); `@CLAUDE.md` § Routing (*"manual
only: the user types `/new-feature <description>`, the model can't invoke it"*).

**O que ocorreu:** no run 1 o usuário escreveu o texto `/new-feature apos criacao de
customer...` como mensagem comum, sem passar pelo slash command do cliente. O modelo
chamou `Skill(new-feature)` e a skill **carregou normalmente** (`Launching skill:
new-feature`). No run 2, aí sim, veio como `<command-name>/new-feature</command-name>`
de verdade. Ou seja: a flag está no arquivo, a documentação afirma que bloqueia, e não
bloqueou.

**Lições:**

- Nenhuma garantia do framework pode depender só dessa flag. A barreira real contra
  "mais de um use case por run" e contra invocação fora de hora é o **guardrail de
  entrada** da própria skill (tabela fechada de input), que felizmente existe e rodou.
- Se o comportamento esperado é bloqueio, é bug do runtime e merece report. Se não é,
  `CLAUDE.md` e o `SKILL.md` estão mentindo sobre a própria arquitetura, e o texto
  "Form 2 / manual only" precisa virar "convenção, não garantia".

## 2. `$ARGUMENTS` interpolado dentro do corpo gerou instrução autocontraditória

**Onde:** `.claude/skills/new-feature/SKILL.md:416-417`.

**O que ocorreu:** o fonte diz

> `- ArchUnit, install now → **invoke** test-architect via the Skill tool with empty $ARGUMENTS (setup mode)`

e o que chegou ao modelo no run 2 foi *"with empty `UC-003-initiate-kyc-verification`
(setup mode)"* — o placeholder foi substituído pelo argumento real. A instrução passou a
mandar invocar `test-architect` em **setup mode** passando um argumento que significa
exatamente **design mode**. Neste run o pre-flight nem chegou a rodar (ArchUnit já estava
instalado e o usuário escolheu "Agora não"), então o defeito não causou dano — mas estava
armado. Mesmo sintoma no run 1, onde o trecho apareceu com a descrição inteira da feature
interpolada no meio da frase.

**Lições:**

- Texto de skill que **fala sobre** o argumento não pode escrever `$ARGUMENTS` literal.
  Escrever "com argumento vazio" e, se precisar mostrar a sintaxe, escapar.
- Vale um lint no exportador de blueprint: `$ARGUMENTS` fora de um bloco de código, em
  skill que recebe argumento, é quase sempre esse bug.

**Reproduzido em 2026-09-27** (remediação, `@.claude/decisions/0056-lessons-learned-012-remediation.md`):
não era um ponto, eram **7 ocorrências em 6 skills**, todas frases *sobre* o argumento e
portanto todas interpoladas em execução — `test-architect:34` dizia "`$ARGUMENTS` empty" e
chegava como "`UC-003-initiate-kyc-verification` empty". As sete foram reescritas ("the
argument", "the target above") e o lint ficou no modo `schema` (bloco `arguments` de
`@.claude/schemas/extensions.json`), não no exportador: o defeito está na fonte, não na
exportação.

## 3. Referências mortas no corpo das skills

**Onde:** `.claude/skills/new-feature/SKILL.md:498` e `:503` — linhas literais
`- **** — ...`, sem identificador; `rest-api-architect/SKILL.md` § Exit rule — *"Inherits
D15 — — and the inconsistency P7 flagged is closed"*; `java-patterns` citado em
`new-feature` com a frase terminando em *"matches a row —."*.

**O que ocorreu:** as citações de registros de decisão foram perdidas na exportação do
blueprint para o projeto. As linhas sobreviveram, os identificadores não. Quem lê não tem
como ir atrás do "porquê", que é justamente a função daquela seção.

**Lições:** auditar o exportador — ele está comendo tokens entre `**` quando o alvo não
existe no projeto destino. Se a decisão só existe no meta-repo, a exportação deveria
substituir por texto ("decisão registrada no meta-repo"), nunca por vazio.

**Reproduzido em 2026-09-27:** seis frases mutiladas (`new-feature:498` e `:503`,
`persistence-architect:51`, `test-architect:46`, `rest-api-architect:43`,
`new-feature:102`) e **nenhum residue reportado** — `residue_markers` procura o caminho que
foi removido, então nunca vê o que a remoção deixou para trás. Corrigido como a lição pede:
`body_transforms.citation_replacement` substitui a citação por "a decision recorded in the
meta-repository", mais um `cut_shapes` novo para a frase que era só o caminho, e os
marcadores de mutilação (`****`, `— —`, `—.`) entraram em `residue_markers` como rede para a
próxima. Verificado nos 7 blueprints: duas exportações idênticas, zero residue.

## 4. Avro + schema registry: a pergunta foi iniciativa do modelo, não eixo de skill — e contraria o default da regra

**Onde:** `.claude/skills/messaging-architect/SKILL.md:113-121` (os seis eixos do passo 3:
momento de publicação, partition key, consumer group, `auto-offset-reset`, ordenação,
tabela de dedupe — **nenhum** de serialização); `@.claude/rules/messaging.md` § Topics and
serialization (*"Payload: JSON by default. A schema registry (Avro/Protobuf) is a
deliberate upgrade for when two teams need a contract they can validate at build time —
not a default, and not assumed until a use case actually needs it"*);
`messaging-architect/SKILL.md:201` (o bloco 1 da parcial **exige** um valor para
serialização).

**O que ocorreu:** a skill obriga a parcial a registrar uma serialização, mas não tem eixo
mandando perguntar, e a regra já fixa o default. O modelo transformou um default de regra
em cardápio de `AskUserQuestion` ("JSON (recomendado)" vs "Avro + schema registry"),
justificando pelo fato de o contrato ser com outro time. O usuário escolheu o upgrade.
Nenhuma necessidade de use case foi estabelecida antes da escolha — a condição que a
própria regra exige ("until a use case actually needs it") nunca foi testada.

Custo que entrou no projeto por causa dessa pergunta:

| Item | Onde |
|---|---|
| Serviço `schema-registry` no compose | `docker-compose.yml` |
| `org.apache.avro:avro`, `io.confluent:kafka-avro-serializer`, `avro-maven-plugin` | `pom.xml` (via spec) |
| Repositório `confluent` — artefato **não está no Maven Central** | `pom.xml` |
| `src/main/avro/CustomerRegistered.avsc` + código gerado | novo diretório de fontes |
| Exclusão do código gerado no gate do JaCoCo | `pom.xml` |
| `mock://` nos testes, e compatibilidade de subject deliberadamente não testada | `40-testes.md` § 3 e § 4 |

**Lições:**

- Quando a regra fixa um default **e** condiciona o upgrade, o comportamento correto é
  registrar o default e a condição na parcial — não abrir votação. Um `AskUserQuestion`
  com o upgrade listado como opção legítima faz o usuário decidir sem ver o custo
  espalhado em seis lugares.
- Se a escolha é legítima em algum caso, então vire **eixo explícito** no passo 3, com o
  gatilho escrito ("os dois lados precisam validar o contrato em build time?") e o custo
  listado na própria pergunta. Hoje a skill só garante que *algum* valor apareça na
  parcial, o que é a garantia mais fraca possível.
- Regra geral que vale para todo o pipeline: **um default de regra não é uma pergunta.**

## 5. O `<bounded-context>` do nome do tópico não existe em lugar nenhum do projeto

**Onde:** `@.claude/rules/messaging.md` § Topics and serialization fixa
`<bounded-context>.<aggregate>.<event-in-past-tense>`; nem `@CLAUDE.md`, nem
`.claude/rules/**`, nem qualquer parcial declara qual é o bounded context deste projeto.

**O que ocorreu:** o modelo perguntou ("`customers` ou `banking`?"). Usuário escolheu
`banking`. Decisão tomada por use case, não por projeto.

**Lições:** bounded context é fato de projeto, com o mesmo status do pacote base. Se
UC-004 for desenhado noutra sessão, nada impede que escolha outro prefixo e o cluster fique
com dois namespaces para o mesmo sistema. Declarar em `@CLAUDE.md` (ou numa regra) e fazer
`messaging-architect` **ler**, não perguntar.

## 6. O survey de use cases só enxerga o working tree — e o UC-003 anterior estava vivo no HEAD

**Onde:** `.claude/skills/new-feature/SKILL.md` § Entry guardrail, passo 1 —
`find docs/use-cases -mindepth 1 -maxdepth 1 -type d -name 'UC-*'`.

**O que ocorreu:** a pasta `UC-003-request-customer-kyc-validation` tinha sido apagada do
disco (com a deleção já no índice do git) antes deste run. O `find` devolveu apenas UC-001
e UC-002; `use-case-design` concluiu "próximo número é 003" e criou
`UC-003-initiate-kyc-verification`. O histórico do repositório agora tem **dois UC-003
diferentes**, e o commit deste run remove um enquanto adiciona o outro. Nada no pipeline
percebeu.

**Lições:**

- O survey precisa cruzar o disco com `git ls-tree -d --name-only HEAD docs/use-cases` e
  parar (ou avisar alto) quando um `UC-NNN` existe no HEAD e não no disco: ou o número foi
  reciclado, ou alguém apagou trabalho aprovado sem commitar.
- Vale a mesma checagem para o `BACKLOG.md`: a entrada antiga do callback de KYC sumiu junto
  com a pasta, e este run a recriou com outro texto — ninguém comparou.

## 7. Nenhum passo do guardrail olha o índice do git no início do run

**Onde:** `new-feature/SKILL.md` § Entry guardrail passo 2 (decide worktree e
`ROOT_IGNORED_BY_PARENT`, e só); `git-publish/SKILL.md` passo 1 (prevê `UNTRACKED`,
`TRACKED` sujo, `TRACKED` limpo e `ROOT_IGNORED_BY_PARENT`).

**O que ocorreu:** o índice já continha 1.503 linhas de deleção **anteriores ao run** (a
pasta UC-003 antiga inteira e o `lessons-learned-010.md`). Isso só apareceu no passo 1 do
`git-publish`, no fim de tudo. A tabela de estados da skill não tem linha para "o índice
carrega coisa que não é deste run", então o tratamento foi improvisado: listar, explicar e
perguntar. Funcionou, mas por decisão do modelo, não por procedimento.

**Lições:**

- Guardrail passo 2 deveria reportar índice sujo logo no começo — é o momento barato de
  decidir, e evita que o commit final misture faxina alheia com o entregável do run.
- `git-publish` precisa do quinto estado na tabela: **índice com mudanças pré-existentes**,
  com a pergunta de escopo (só os caminhos do run × tudo) já escrita na skill, em vez de
  depender de o modelo notar.

## 8. O teste de fronteira e a Form B do outbox se contradizem

**Onde:** `use-case-design/references/scope-boundary.md:27-29` — *"Email, push, queue
publication, and external service calls **never** meet condition 1: they aren't
transactional with the database. So they **always** split off into another use case"*;
`@.claude/rules/messaging.md` § Publication timing, Form B — a linha do outbox é escrita
**na mesma transação** da mudança de estado.

**O que ocorreu:** com outbox, a publicação passa a compartilhar transação — exatamente o
que o "never" nega. O modelo decidiu, por conta própria, que registro + append no outbox é
**um** use case (as duas condições do teste são satisfeitas), e seguiu. Se tivesse aplicado
o texto ao pé da letra, teria proposto um terceiro use case reativo e o desenho seria outro.

**Lições:** `scope-boundary.md` precisa da exceção explícita: publicação em broker conta
como efeito separado **sob Form A**; sob Form B, o append é parte da mesma transação e não
divide o use case. Sem isso, dois runs iguais separam o caso de jeitos diferentes.

## 9. As colunas do outbox foram inventadas na parcial de mensageria e corrigidas na consolidação

**Onde:** `25-mensageria.md` § 6 (escrita neste run pedindo `next_attempt_at`,
`failure_reason`, `created_at`); `persistence-architect/templates/OutboxEventTable.sql.example`
(exemplar canônico: `event_id`, `aggregate_id`, `event_type`, `payload`, `occurred_at`,
`published_at`, `attempts`, `last_error`, `dead_lettered` — **sem** nenhuma das três).

**O que ocorreu:** `messaging-architect` passo 4a manda listar as colunas "como
requisitos, nunca DDL", e não manda ler o exemplar canônico antes. O modelo inventou nomes.
`persistence-architect` então adotou o exemplar e a consolidação registrou três linhas em
`## Resolved divergences`.

Uma dessas linhas **não é cosmética**: sem `next_attempt_at`, o retry deixou de ser backoff
exponencial por linha e virou "repoll fixo de 2s até `max-attempts`". Retry é território de
`messaging-architect` pela própria tabela de precedência da consolidação (*"Topic, delivery
guarantee, retry/DLQ → `25-mensageria.md`"*), mas a mudança entrou pela precedência de
**coluna** (`20-persistencia.md`). Ou seja, uma decisão de comportamento foi resolvida por
uma regra desenhada para forma de tabela.

**Lições:**

- `messaging-architect` passo 4a deve citar `OutboxEventTable.sql.example` como fonte da
  lista de colunas. Requisito inventado vira divergência garantida.
- A tabela de precedência precisa de desempate quando os dois donos colidem no mesmo fato:
  coluna é de persistência, **semântica de retry é de mensageria**, e um caso em que a
  segunda depende da primeira deveria parar o pipeline e perguntar, como a própria skill
  manda para "divergência que a tabela não resolve".

## 10. Um use case que não cria classe de use case não tem regra de numeração nem de slug

**Onde:** `@.claude/rules/naming.md` § Architecture vocabulary e
`use-case-design/references/scope-boundary.md:145-152` assumem que todo UC nasce com
`<Verb><Noun>UseCase`; `new-feature/SKILL.md` § Spec lifecycle assume "um `/new-feature` =
um use case".

**O que ocorreu:** UC-003 não cria classe nenhuma de use case — `RegisterCustomerUseCase` é
**CHANGE**. Todo o conteúdo do caso é extensão de dois casos aprovados (UC-001 e UC-002),
distribuído pela seção `## Impact on approved use cases`. O número e o slug
(`initiate-kyc-verification`) foram inventados pelo modelo: descrevem a capacidade nova, não
o gatilho, ao contrário do que a referência manda (*"The use case's verb is the trigger's"* —
o gatilho aqui continua sendo "register customer").

**Lições:**

- Definir o caso "extensão de use case aprovado": ou ganha numeração própria com regra de
  slug explícita, ou vira um artefato diferente (change request) com seu próprio template.
  Hoje cada run resolve de um jeito e a rastreabilidade sofre — ninguém, lendo
  `UC-001-register-customer/`, descobre que o comportamento dele mudou em UC-003.
- Complementar: o `00-caso-de-uso.md` de um caso aprovado deveria ganhar um índice de
  "alterado por", mantido fora do arquivo imutável (um `CHANGELOG.md` na pasta do UC, por
  exemplo). Sem isso, a imutabilidade protege o texto e perde o histórico.

## 11. CPF sendo logado cru desde o UC-001 — defeito real encontrado de lado

**Onde:** `@.claude/rules/logging.md` (§ Masking mechanism, exige zero dado sensível cru no
log); `GlobalHttpMethodLogAspect` loga DTO de request e response por padrão (opt-out);
`RegisterCustomerRequest`/`RegisterCustomerResponse` não implementam `LogMask` nem marcam
`securityNumber` — `grep -rln "LogMask\|MaskSensitiveData" src/main/java/**/infrastructure/`
não retorna nada.

**O que ocorreu:** `rest-api-architect` passo 5 só aplica máscara a campos que
`10-dominio.md` marcou como sensíveis. O `10-dominio.md` do UC-001 **não marcou** (o bloco
de candidatos a masking nem aparece lá). Resultado: o campo atravessou desenho,
implementação, revisão e build verde vazando CPF no log. Foi descoberto neste run só porque
`domain-modeling` do UC-003 marcou o campo, e o `rest-api-architect` foi conferir os DTOs
existentes.

**Lições:**

- Sensibilidade não pode depender de uma parcial lembrar. O catálogo de
  `@.claude/rules/value-objects.md` (cpf, cnpj, email, phone, document) **já é** a lista de
  candidatos: derive dela mecanicamente — todo campo cujo tipo está no catálogo é candidato
  a masking até que alguém escreva por que não é.
- Mesma fragilidade em `test-architect` passo 4: o teste de máscara só é exigido quando
  `30-rest.md` marcou o campo. Dois elos frágeis em série, e nada mecânico no `verify`.
- Cabe um teste de arquitetura: todo DTO em `infrastructure.rest.dto` com campo cujo nome
  ou tipo bate com o catálogo tem que implementar `LogMask`. ArchUnit já está instalado.

## 12. `docker-architect` recebeu um retrato errado do `docker-compose.yml`

**Onde:** bloco `## Current compose state` injetado na invocação da skill;
`docker-architect/SKILL.md` passo 3 manda usar o mesmo `grep -A2 "^services:"`.

**O que ocorreu:** o bloco injetado listou `app:`, `postgres:` e `postgres-data:` — três
entradas, **uma delas um volume**, e omitindo `otel-collector` e `kafka`, que estão no
arquivo. O `grep -A2` pega duas linhas após `services:` e nada mais. O modelo só não
duplicou o serviço `kafka` porque leu o arquivo inteiro por conta própria antes de editar.

**Lições:**

- Trocar o `grep` por leitura real do YAML (as chaves de `services:`), tanto na injeção
  quanto no passo 3. O retrato errado é pior que retrato nenhum: ele convida a skill a
  recriar o que já existe, e a tabela de decisão dela ("um serviço já presente é deixado em
  paz") depende inteiramente dessa lista estar certa.

**Reproduzido em 2026-09-27** num compose de fixture com quatro serviços e dois volumes: o
`grep -E "^\s{2}\S+:$"` devolveu `app`, `postgres`, `otel-collector` e `postgres-data` —
perdeu `kafka` (tinha comentário na mesma linha) e promoveu um volume a serviço. Substituído
por um `awk` limitado ao bloco `services:`, na injeção e nos três passos que repetiam o
`grep -A2` (`docker-architect` 3, `messaging-architect` 9, `persistence-architect` 9).

## 13. Serviço novo escrito à mão, sem template, sem verificação de tag nem de healthcheck

**Onde:** `docker-compose.yml`, bloco `schema-registry` adicionado neste run;
`docker-architect/SKILL.md` § Service catalog não tem linha para schema registry (manda
"escrever o bloco à mão a partir daquela forma" — o que é permitido).

**O que ocorreu:** o bloco saiu por inferência do modelo em três pontos:
`confluentinc/cp-schema-registry:8.3.2` (tag escolhida por espelhar a versão do serializer
resolvida no `packages.confluent.io`, **sem verificar que a tag existe** no registro de
imagens); `curl` no healthcheck (**sem confirmar que existe na imagem**); ausência de
volume (correto — o estado vive no tópico `_schemas` — mas também inferido). Além disso, o
passo 7 da skill manda encerrar o relatório com `java .claude/hooks/ArchHook.java compose`
e dizer o que ele pega; **isso não foi feito**.

**Lições:**

- Verificar tag de imagem é barato (`docker manifest inspect`, ou o registry HTTP) e o
  projeto já tem a disciplina "versão vem do metadata, nunca da memória" para o Maven. Vale
  a mesma disciplina para tag de imagem.
- Toda imagem nova merece uma linha no catálogo da skill depois do primeiro uso, senão o
  próximo run improvisa de novo, diferente.

## 14. Referência órfã dentro do `docker-compose.yml` apontando para pasta que não existe mais

**Onde:** comentário do serviço `kafka`, que citava
`docs/use-cases/UC-003-request-customer-kyc-validation/25-mensageria.md` — pasta apagada.
Corrigido de passagem neste run para o slug novo.

**Lições:** quando uma pasta de UC é apagada, ninguém varre o repositório atrás de
referências a ela. Um alvo fácil para `/arch-doctor`: grep por `docs/use-cases/UC-` em
arquivos versionados e reportar caminho inexistente.

## 15. `.claude/audit-usage/*` fica permanentemente sujo e fora de qualquer decisão

**O que ocorreu:** `history.jsonl` e `nodes.jsonl` são versionados e mudam a cada `Stop`;
o relatório novo nasce como untracked. No `git-publish` deste run ficaram de fora do commit
por decisão do modelo, sem que nada no framework diga o que fazer com eles. O commit
anterior do repositório (`0bba5ec "audit usage"`) mostra que em outro momento a decisão foi
a oposta.

**Lições:** decidir de uma vez, e escrever onde: ou entram no commit do run que os gerou
(e então `git-publish` os adiciona aos caminhos por padrão), ou saem do versionamento.
Oscilar entre os dois é o pior dos mundos — o diff do próximo run sempre vem sujo.

## 16. Pedido explícito de `.gitignore` não foi executado, e a pasta acabou apagada

**O que ocorreu:** a primeira tarefa desta sessão foi *"add `docs/lessons-learned/` no
.gitignore"*. A checagem mostrou que não havia regra de ignore e que o arquivo estava
rastreado, e a resposta foi *"nada a fazer"* — literalmente correto e **errado quanto à
intenção**: o pedido era criar a regra, não verificar se ela existia. Ação certa seria
acrescentar a entrada e rodar `git rm --cached docs/lessons-learned/`. Em vez disso, a pasta
inteira foi apagada (fora desta sessão) e a deleção entrou no commit deste run.

**Lições:**

- "Já está do jeito que você pediu?" e "faça o que eu pedi" são perguntas diferentes;
  quando o pedido é imperativo ("adicione X"), verificar não substitui executar.
- Efeito colateral aberto: este arquivo recria `docs/lessons-learned/` **rastreado**, e o
  `.gitignore` continua sem a entrada. Precisa de decisão — versionar as lições (elas são
  documentação de projeto) ou ignorá-las (são notas locais). As duas são defensáveis; a
  ambiguidade não.

---

## Resumo do que virou inferência do modelo neste run

Nenhum destes tem eixo, regra ou template que os fixe. Todos foram decididos por
`AskUserQuestion` improvisado ou por julgamento direto:

| Decisão | Deveria vir de | Impacto |
|---|---|---|
| Serialização Avro + schema registry | regra (default JSON) — § 4 | alto: serviço, 3 deps, repo externo, plugin, exclusão de cobertura |
| Bounded context `banking` | `@CLAUDE.md` — § 5 | médio: nome de tópico é contrato estável |
| Outbox não divide o use case | `scope-boundary.md` — § 8 | alto: define quantos use cases existem |
| Colunas e pacing de retry do outbox | exemplar canônico + dono de retry — § 9 | médio: mudou comportamento do relay |
| Número e slug de um caso sem classe nova | `naming.md` — § 10 | médio: rastreabilidade |
| Quem constrói o evento (agregado × use case) | nenhuma regra cobre | baixo: ambos defensáveis, mas oscila entre casos |
| Forma da porta do gate `ACTIVE` (`findById` × `existsActiveById`) | nenhuma regra cobre | baixo |
| Remoção de `existsById` de porta de caso aprovado | processo de Impact cobre o *como*, não o *se* | médio: mexe em código implementado |
| Tag e healthcheck do `schema-registry` | catálogo da `docker-architect` — § 13 | médio: build pode quebrar em runtime |
| Backfill de `status` para `ACTIVE` nas linhas existentes | legítimo perguntar; mas nada obriga a perguntar | alto se esquecido: migration NOT NULL em tabela populada |

---

## Remediação (2026-09-27)

Dois registros, em dois runs:

- `@.claude/decisions/0056-lessons-learned-012-remediation.md` — os mecânicos: itens 2, 3,
  6, 7, 12, 14, o modelo por nó no trilho, e a decisão de versionamento (§ 15 e § 16:
  `.claude/audit-usage/` versionado, `docs/lessons-learned/` ignorado). Item 1 fechado sem
  ação, por decisão do usuário.
- `@.claude/decisions/0057-lessons-learned-012-inferences.md` — as sete inferências: 4
  (eixo de serialização com o custo dentro da pergunta), 5 (bounded context perguntado no
  `/init-project` e **lido** depois), 8 (exceção da Form B no boundary test, pelos
  critérios de negócio), 9 (**outbox inteiro passa a ser de `persistence-architect`** —
  tabela, colunas, claim query e pacing; mensageria declara a garantia), 10 (`UC-NNN`
  próprio, regra de slug para o caso sem classe nova, `CHANGELOG.md` por pasta alterada),
  11 (§ Masking candidates em `logging.md` + duas regras ArchUnit guardadas por
  `COMMONS_INSTALLED`), 13 (tag verificada no registro, healthcheck confirmado ou omitido,
  linha no catálogo + exemplar do `schema-registry`).

Três itens da tabela acima continuam sem dono por serem defensáveis nas duas formas: quem
constrói o evento (agregado × use case), a forma da porta do gate `ACTIVE`, e o backfill de
`status`. A remoção de `existsById` de porta de caso aprovado passa a ter rastro pelo
`CHANGELOG.md` do item 10, mas o *se* continua sendo decisão de run.
