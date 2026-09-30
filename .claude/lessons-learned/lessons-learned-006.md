# Lessons learned — pipeline `/new-feature` completo, ponta a ponta (UC-001-register-customer)

Origem: uma execução real do `/new-feature` no projeto de demo
`demo-clean-arch-single-module` (blueprint `clean-architecture-single-module`), pedido
"crie um endpoint rest para cadastrar customers, deve ser persistido no banco de dados
sql" (id, name, securityNumber). Primeira vez que o pipeline completo rodou até o fim
nesse projeto: `use-case-design` → `domain-modeling` → `rest-api-architect` →
`persistence-architect` → `test-architect` → consolidação → aprovação → pré-flight
(ArchUnit + commons-logging) → `java-spring-boot-developer` → gate de coverage vermelho
→ correção → `git-publish`. Sete subagents no total, dois deles precisaram de uma
segunda tentativa.

---

## 1. Corrida entre `Skill` (design) e `Agent` (executor) reabre a fase de design do guard e bloqueia um subagent legítimo

**Onde:** `.claude/hooks/ArchHook.java` § guard (`guardCall`/`guardWrite`,
~linha 1893-1965); `.claude/schemas/extensions.json` bloco `guard`.

**O que ocorreu:** no pré-flight do executor, dois `AskUserQuestion` deram "instalar
agora" para ArchUnit e para commons-logging. Eu disparei `Skill(test-architect,
args="")` (setup mode) e `Agent(commons-logging-installer)` no mesmo turno, em
paralelo. O `Skill` call abre a fase de design (`guardCall`, `tool_name=Skill`); o
`Agent` call com `subagent_type` numa allowlist fecha essa mesma fase (apaga o arquivo
de estado). Como os dois PreToolUse aconteceram no mesmo turno, a ordem não é
garantida — o `Skill` reabriu a fase **depois** de um `Agent` anterior (para
`archunit-installer`) já tê-la fechado, e o `commons-logging-installer` recém-lançado
bateu em "❌ Design phase open (test-architect)" em toda escrita sob `src/`. Resultado:
138 mil tokens, 59 tool calls, ~6 minutos de subagent, **zero arquivos escritos** — só
descobriu o bloqueio e voltou. Precisei relançar o mesmo agente sozinho (sem nenhum
`Skill` em paralelo) pra a escrita passar.

**Lições:**

- O mecanismo em si é correto — a fase de design *deveria* impedir escrita direta sob
  `src/`. O problema é a garantia de ordem quando duas chamadas que abrem/fecham o
  mesmo arquivo de estado disparam no mesmo turno. `guardCall` não é atômico em relação
  a duas invocações concorrentes de ferramentas diferentes.
- Regra prática até isso ser corrigido na fonte: **nunca** disparar `Skill(<design
  skill>)` e `Agent(<executor>)` no mesmo turno/mensagem. Serializar: primeiro o
  `Skill` (se for realmente necessário rodar setup mode via `Skill` em vez de invocar o
  `archunit-installer` direto), depois, num turno separado, os `Agent` calls.
- Possível correção na fonte: `guardWrite` já lê o campo `agent_type` do payload da
  própria chamada de escrita pra reconhecer que ela vem de dentro de um executor
  (independente do estado do arquivo). Nesta sessão esse campo não pareceu bastar —
  mesmo depois de `commons-logging-installer` entrar na allowlist de
  `executor_agents` (gap 2 abaixo), a primeira tentativa continuou bloqueada. Vale
  confirmar, com um teste dedicado, se `agent_type` chega de fato populado no payload
  de PreToolUse quando a escrita parte de um subagent disparado via `Agent` tool nesse
  ambiente — se não chega, a única porta de saída real é o `Skill`/`Agent` que fecha o
  arquivo de estado, e a ordem de disparo importa muito mais do que o design do hook
  assume.

---

## 2. `commons-logging-installer` não estava na allowlist `guard.executor_agents`

**Onde:** `.claude/schemas/extensions.json` bloco `guard.executor_agents` (antes desta
sessão: só `["java-spring-boot-developer", "archunit-installer"]`).

**O que ocorreu:** o próprio agente `commons-logging-installer` tem, na sua definição
(`@.claude/agents/commons-logging-installer.md`), o mesmo precedente documentado de
`archunit-installer` — "escreve sob `src/`, instalado uma vez". Mas nunca tinha sido
adicionado à lista que o hook realmente lê. Corrigido nesta sessão (nas duas cópias:
meta-repo e a derivada do projeto de demo — só a do meta-repo entrou no commit, porque
`examples/` está no `.gitignore`, ver gap 7).

**Lições:**

- Invariante 9/10 do `@CLAUDE.md` (lista de campos/servers tem um dono só, e cada
  arquivo derivado tem que bater com o canônico) vale também pra essa allowlist — ela é
  dado, igual `extensions.json` já documenta, mas nada verificou que os três agentes
  executores documentados em prosa (`java-spring-boot-developer.md`,
  `archunit-installer.md`, `commons-logging-installer.md`) realmente batem com os três
  nomes dentro de `guard.executor_agents`. Um `ArchHook.java schema` (ou um job novo em
  `validate.yml`) que confira "todo agente cujo doc reivindica o papel de executor está
  na allowlist" fecharia essa classe de gap de vez.

---

## 3. `spring-boot-starter-aop` continua na definição do `commons-logging-installer`, mesmo já corrigido em outro exemplar

**Onde:** `.claude/agents/commons-logging-installer.md` linhas 42, 99 e 146 (a
dependência `spring-boot-starter-aop`, sem versão). Comparar com
`.claude/skills/rest-api-architect/templates/IdempotencyAspect.java.example:51`, que
**já** documenta: "pre-Boot-4 `spring-boot-starter-aop`; that name isn't in the Boot 4
BOM at all."

**O que ocorreu:** o Spring Boot 4 renomeou `spring-boot-starter-aop` para
`spring-boot-starter-aspectj`. O subagente `commons-logging-installer` bateu nisso ao
vivo — `'dependencies.dependency.version' ... is missing` ao resolver o POM — e teve
que descobrir e corrigir sozinho, no meio da instalação, algo que um exemplar irmão do
mesmo meta-repo já sabia.

**Lições:**

- O conhecimento "Boot 4 renomeou X pra Y" precisa de um lugar único (candidato: uma
  nota em `@.claude/skills/project-bootstrap/references/dependency-catalog.md`, que já
  é o catálogo central de dependências, ou uma seção nova ali de "renomeações
  conhecidas entre major versions do Boot") — não um comentário isolado dentro de um
  `.java.example` que só quem já está montando aquele arquivo específico vai ler.
  Invariante 2 do `@CLAUDE.md` (dono único) vale aqui: hoje esse fato tem exatamente um
  dono certo (`IdempotencyAspect.java.example`) e pelo menos um lugar errado que
  deveria citá-lo e não cita (`commons-logging-installer.md`).

---

## 4. Pacote `application.service` não existe no `packages.map` do blueprint `clean-architecture-single-module`, mas os exemplares de idempotência assumem ele

**Onde:** `.claude/skills/persistence-architect/templates/IdempotentExecution.java.example`
(pacote fixo `com.example.demo.application.service`); `packages.map` de
`.claude/blueprints/clean-architecture-single-module/clean-architecture-single-module.yaml`
(não declara `application.service` — só `hexagonal.yaml` declara, linha 83).

**O que ocorreu:** `20-persistencia.md` (escrito por mim, `persistence-architect`)
seguiu o exemplar e apontou `IdempotentExecution` pra `application/service`. Isso é
correto pro vocabulário hexagonal, mas o blueprint ativo aqui é single-module, cujo
`packages.map` e cuja tabela em `CLAUDE.md` nunca previram esse pacote. O executor
(`java-spring-boot-developer`) teve que decidir sozinho, em runtime, criar
`application.service` do zero, e documentar a decisão editando `CLAUDE.md` por conta
própria — uma decisão de arquitetura (novo pacote, nova linha na tabela de módulos)
tomada por um agente de execução, não pelo blueprint nem por nenhuma skill de design.

**Lições:**

- Isso é exatamente o tipo de decisão que o invariante 7 do `@CLAUDE.md`
  ("arquiteturas são dado") deveria capturar: o pacote pra infraestrutura de
  idempotência compartilhada (não pertence a nenhum agregado específico) devia estar
  no `packages.map` de **todo** blueprint que suporta REST + `Idempotency-Key`, com um
  nome fixo por vocabulário (`application.service` no hexagonal,
  provavelmente `application.usecase` mesmo, ou um novo `application.shared`, no
  single-module — precisa de decisão, não de improviso).
- Até essa decisão existir, qualquer novo caso de uso com `Idempotency-Key` num
  blueprint que não seja hexagonal vai bater na mesma lacuna e cada executor vai
  reinventar (ou nomear diferente) o mesmo pacote.

---

## 5. `ArchitectureTest.java` pré-instalado (pré-flight) colide com a própria convenção de nomes do blueprint single-module

**Onde:** regras `use_cases_are_named_by_convention` e `outbound_ports_are_interfaces`
em `ArchitectureTest.java` (gerado a partir de
`.claude/skills/test-architect/templates/ArchitectureTest.java.example` pelo
`archunit-installer`, traduzido pro projeto). Comparar com `naming.md` § Architecture
vocabulary: no vocabulário single-module o use case é uma classe concreta e o comando
(`<Verb><Noun>Command`) **co-habita o mesmo pacote** `application.usecase` — não existe
"toda classe de `application.usecase` termina em `UseCase`" nem "toda classe de
`application.port` é interface" (a infraestrutura de idempotência do gap 4 precisa de
classes concretas nesse nível também).

**O que ocorreu:** as duas regras, do jeito que o `archunit-installer` gerou (porque
rodou **antes** de qualquer classe de negócio existir — `allowEmptyShould(true)`,
correto pro pré-flight, mas cego pro que ia entrar depois), bloquearam
`RegisterCustomerCommand` e as classes da idempotência. O executor teve que editar
`ArchitectureTest.java` no meio da implementação pra abrir uma exceção nomeada
("`Command` é a única exceção co-localizada") — funcionou, mas é o mesmo agente de
execução decidindo, de novo, algo que devia estar certo desde a instalação.

**Lições:**

- `archunit-installer` traduz pacotes a partir do `packages.map`, mas não parece
  traduzir também as **regras de nomenclatura** a partir do que `naming.md` §
  Architecture vocabulary já diz sobre aquele blueprint especificamente (single-module:
  concreto, sem interface, comando co-localizado). O exemplar
  `ArchitectureTest.java.example` provavelmente foi escrito pensando em hexagonal
  (`UseCase` interface + `Service`), e a tradução pro single-module devia ajustar as
  regras de convenção de nome junto com os nomes de pacote, não só os nomes de pacote.

---

## 6. `30-rest.md` (esta mesma execução): sobreposição não resolvida entre validação de shape e invariante de domínio pro mesmo campo

**Onde:** `docs/use-cases/UC-001-register-customer/30-rest.md` § 2 (`@NotBlank` em
`name` no DTO) vs § 3 (lista `CUSTOMER_NAME_REQUIRED`, `ValidationException`, como se
esse errorCode pudesse aparecer pra esse mesmo request). `.claude/skills/rest-api-architect/SKILL.md`
passo 6 ("Fix the error map") não pede explicitamente pra cruzar a validação de shape
do passo 5 com a tabela de invariantes de `10-dominio.md` antes de listar as duas como
se fossem cenários igualmente alcançáveis.

**O que ocorreu:** eu mesmo (rodando `rest-api-architect`) escrevi as duas seções sem
perceber que `@NotBlank` intercepta o `name` em branco **antes** de qualquer chance do
domínio lançar `CUSTOMER_NAME_REQUIRED` por esse caminho — o executor só percebeu isso
na hora de escrever o teste de contrato, e teve que decidir sozinho qual das duas
comportamentos testar (ficou com o 400 de bean validation, documentado num comentário
de classe do teste).

**Lições:**

- O passo 6 do `rest-api-architect` devia dizer explicitamente: pra cada linha do mapa
  de erro que vem de uma invariante de domínio (não de idempotência/estrutural), checar
  se o mesmo campo já tem uma anotação de bean validation no passo 5 que intercepta o
  mesmo cenário primeiro. Se sim, marcar no partial qual das duas é a que realmente
  acontece (e por quê a outra é módulo morto por esse endpoint específico) — não listar
  as duas como se fossem paralelas.

---

## 7. Guard de spec aprovado libera só a linha `status:`, não os checkboxes do checklist de implementação

**Onde:** `.claude/hooks/ArchHook.java` `isStatusClose` (~linha 1980-1988) — a exceção
que libera editar um spec `approved` exige, literalmente, `old_string` contendo
`"status: approved"` e `new_string` igual a `old_string` só com esse trecho trocado por
`"status: implemented"`. Qualquer outro `Edit` no mesmo arquivo (por exemplo, marcar
`[x]` num item da checklist de implementação) bate no mesmo bloqueio "specs são
imutáveis".

**O que ocorreu:** o executor reportou não conseguir marcar nenhum item da checklist de
19 passos dentro do próprio `UC-001-spec.md` enquanto o build não ficasse verde — o que
faz sentido pro *status* (não devia virar `implemented` sem build verde), mas bloqueia
também o **progresso incremental** que a decisão 0024 (gap 18,
`lessons-learned-001`/`0024-lessons-learned-001-remediation.md`) pediu explicitamente:
"executor rewritten: incremental writing per checklist step, with resume".

**Lições:**

- A checklist dentro do spec `approved` é exatamente o mecanismo de resumo que a
  correção do gap 18 introduziu — mas o guard, que veio depois, não abriu espaço pra
  ela. Se o executor tem que rodar em mais de uma sessão (spec grande, interrupção no
  meio), hoje ele não tem onde persistir "cheguei até o passo 9" dentro do próprio
  arquivo que o guard protege.
- Duas saídas possíveis, a decidir: (a) `isStatusClose` (ou uma regra irmã) passa a
  aceitar também edições que só marcam `[ ]` → `[x]` na seção de checklist, mantendo o
  resto do arquivo intacto; ou (b) o progresso incremental muda de lugar — sai do
  `UC-NNN-spec.md` congelado e vai pra um arquivo próprio, sem `frozen_statuses`,
  específico do executor (ex.: `UC-NNN-progress.md`), e o spec continua imutável de
  verdade.

---

## 8. `examples/` virou 100% gitignored, mas `/new-feature`/`git-publish` assumem que a raiz do projeto é versionada de verdade

**Onde:** commit `5e2522d` ("chore: ignore examples/ directory"), meta-repo;
`.claude/skills/new-feature/SKILL.md` § Entry guardrail passo 2 (worktree) e § End of
flow (invoca `git-publish` sem checar se o próprio diretório do projeto está
ignorado); `.claude/skills/git-publish/SKILL.md` passo 1 (lê `git status`, mas não
`git check-ignore` sobre os próprios paths do projeto).

**O que ocorreu:** rodar o pipeline completo dentro de
`examples/demo-clean-arch-single-module/` — sem intenção de commitar o resultado do
demo, só de exercitar o pipeline — chegou até `git-publish` com uma mensagem de commit
`feat(UC-001-register-customer): ...` pronta, mas `git status` só mostrava um arquivo
sujo e sem nenhuma relação com a feature (`extensions.json`, gap 2), porque todo o
resto (`src/`, `docs/use-cases/`) está ignorado. Só não virou um commit enganoso porque
percebi a divergência e perguntei antes de commitar — nada no guardrail teria pego
isso sozinho.

**Lições:**

- O passo 2 do guardrail do `/new-feature` (que já distingue "não é repo git" de
  "está num worktree") ganharia um terceiro caso: "a raiz do projeto está dentro de um
  `.gitignore` do repositório pai" — detectável com `git check-ignore -q .` a partir da
  raiz do projeto. Nesse caso, o pipeline pode seguir normalmente (o trabalho em disco
  continua válido), mas `git-publish`, no fim, devia avisar antes de perguntar "commitar
  agora?" em vez de assumir que o diff bate com o que acabou de ser feito.
- Esse gap é específico de projetos de demo/exemplo dentro do próprio meta-repo — não
  afeta um projeto gerado de verdade por `/init-project` em outro diretório, que nunca
  vai estar sob o `.gitignore` do meta-repo. Mas como este meta-repo agora mantém
  exemplos versionados dessa forma (ver `examples/README.md` se existir, ou o commit
  citado), vale o guardrail mesmo assim.

---

## 9. Nenhuma das cinco skills de design lê `logging.md` — o CPF do UC-001 vai pro log em texto puro, hoje, por padrão

**Onde:** `@.claude/rules/logging.md` § Masking mechanism e § Per class type. Contrato
(`reads`) de `domain-modeling/SKILL.md`, `rest-api-architect/SKILL.md`,
`persistence-architect/SKILL.md`, `test-architect/SKILL.md` e `use-case-design/SKILL.md`
— nenhum cita `@.claude/rules/logging.md`, confirmado nesta sessão lendo o corpo de
cada um. Evidência concreta no código gerado:
`infrastructure/rest/dto/RegisterCustomerRequest.java` e
`RegisterCustomerResponse.java` não implementam `LogMask`; `securityNumber` (CPF) não
tem `@MaskSensitiveData(maskedType = MaskedType.DOCUMENT)` — o tipo `DOCUMENT` já
existe pronto em `MaskedType.java`. `application/usecase/RegisterCustomerUseCase.java`
não tem `@LogExecution`.

**O que ocorreu:** `commons/logging/GlobalHttpMethodLogAspect.java`, instalado no
pré-flight desta mesma execução, é **default-on**
(`@ConditionalOnProperty(..., matchIfMissing = true)`) e loga parâmetros + resposta de
**todo** endpoint REST automaticamente — sem precisar de nenhuma anotação no
controller pra isso acontecer. Como nenhuma das cinco skills de design decide aplicar
`LogMask`/`@MaskSensitiveData`/`@LogExecution` no que elas mesmas especificam, e o
prompt que mandei pro `java-spring-boot-developer` também não mencionou essa
obrigação, o resultado é: o CPF de todo customer registrado sai em texto puro no log
assim que o endpoint roda — violação direta de `logging.md`: "Zero *raw* sensitive
data in the log".

**Lições:**

- `logging.md` precisa entrar no `reads` de `domain-modeling` (decide quais campos do
  agregado são PII — a mesma informação que já alimenta `value-objects.md`'s catálogo
  de VOs com regra de formação, tipo `Cpf`/`SecurityNumber`, é o sinal natural de "isso
  é sensível") e de `rest-api-architect` (decide, no bloco de DTOs, se o DTO de
  entrada/saída implementa `LogMask` e quais campos levam `@MaskSensitiveData` — a
  mesma tabela de "Fix the DTOs" já existente no passo 5 do `rest-api-architect` é o
  lugar natural).
- `test-architect` também devia entrar: um teste que serializa/loga o DTO e afirma que
  o campo sensível não aparece em claro é o único jeito mecânico de verificar essa
  regra — hoje "Zero raw sensitive data" não tem nenhum "How to verify" além de review
  humano (o próprio `logging.md` já admite isso na sua seção final, mas nunca ganhou um
  dono que feche a lacuna).
- Efeito colateral do fato de `commons-logging-installer` rodar num pré-flight
  **separado e mecânico** (sem interview, sem contato com o spec da feature sendo
  implementada — ver `.claude/agents/commons-logging-installer.md`): ele instala a
  ferramenta, mas nada no pipeline liga "ferramenta instalada" a "esta feature
  específica precisa usá-la". O gap não é do instalador — é de nenhuma etapa seguinte
  assumir essa responsabilidade.

---

## 10. Abordagem de duas camadas pra `Idempotency-Key` (manual no primeiro endpoint, `@Idempotent` a partir do segundo) — sugestão do usuário: usar `@Idempotent` desde o primeiro

**Onde:** `.claude/skills/rest-api-architect/SKILL.md` passo 7: "**First
`Idempotency-Key` endpoint in the project → the controller calls `IdempotentExecution`
by hand** ... **Second one → switch to `@Idempotent` + `IdempotencyAspect`**".

**O que ocorreu:** segui a regra à risca — `CustomerController` (primeiro endpoint
protegido do projeto) chama `IdempotentExecution` manualmente, com
(de)serialização própria da resposta armazenada (`encode`/`decode` por delimitador,
`hashBody` com `MessageDigest` na própria classe). O usuário, revisando o resultado,
considera que vale a pena tratar como obrigação da skill usar `@Idempotent` **desde o
primeiro** endpoint, não só a partir do segundo.

**Lições (posição do usuário, registrada como está — decisão de arquitetura, não algo
que eu tenha verificado como bug):**

- A justificativa original da regra (não introduzir o aspecto pra um único ponto de
  uso, custo de lookup por reflexão + índice de argumento do corpo) fica mais fraca
  quando comparada ao custo real observado aqui: o controller do primeiro endpoint
  ficou com ~30 linhas a mais só de (de)serialização manual da resposta armazenada, que
  o `@Idempotent`/`IdempotencyAspect` já resolve de forma genérica — código que
  provavelmente seria descartado assim que o segundo endpoint protegido aparecesse e
  forçasse a migração pro aspecto mesmo assim.
- Se adotado, o passo 7 de `rest-api-architect/SKILL.md` perde o "primeiro vs segundo
  endpoint" e passa a: toda vez que `20-persistencia.md`/`30-rest.md` decidirem que
  `idempotency_keys` é **NEW** (primeira vez no projeto), a mesma passada já monta
  `IdempotencyAspect` + `@Idempotent` de uma vez, e nenhum endpoint futuro nem o
  primeiro precisa do caminho manual. Fica pra decisão futura (não tomada aqui) avaliar
  se isso vale um novo registro em `.claude/decisions/`.
