# Lessons learned — pipeline de metrics ausente no OTLP collector e porta fixa colidindo entre projetos-irmãos (demo-clean-arch-single-module)

Origem: usuário trouxe um WARN do log da aplicação —
`OtlpMeterRegistry` falhando ao publicar métricas com
`HTTP status code 404 and body 404 page not found` contra
`http://localhost:4318/v1/metrics`. Primeira hipótese, confirmada lendo
`docker/init/otel-collector/otel-collector-config.yml`: o `service.pipelines` só
declara `traces`, então o receiver OTLP nunca registra a rota `/v1/metrics` — 404
correto. Apliquei o fix (pipeline `metrics` adicionado) direto no projeto de demo e
pedi pra reiniciar o container. Usuário voltou: "problema persiste". Investigando de
novo, `docker compose ps` mostrou só o `postgres` up — o `otel-collector` **deste**
projeto nunca tinha subido. Tentando subir manualmente:
`Bind for 0.0.0.0:4318 failed: port is already allocated`. A porta estava presa por um
container de **outro** projeto gerado (`demo-clean-architecture-single-module-init-project-example-otel-collector-1`),
rodando há 8 dias, com a mesma config antiga (só `traces`) — era ele quem respondia o
404, não o collector deste projeto, que existia mas nunca tinha conseguido iniciar.
Parei o container órfão, recriei o deste projeto (`--force-recreate`), confirmei
`POST /v1/metrics` retornando 200. O fix de pipeline sozinho era necessário mas não
suficiente — o sintoma real tinha duas causas empilhadas, e a segunda mascarou a
primeira.

---

## 1. O exemplar do `otel-collector-config.yml` só liga a pipeline de traces — todo projeto gerado com a feature `observability` nasce publicando 404 pra `/v1/metrics`

**Onde:** `.claude/skills/docker-architect/templates/otel-collector-config.yml.example:19-24`
(`service.pipelines` com só `traces:`);
`.claude/skills/project-bootstrap/templates/features/observability/application-observability.yml.example`
(fragmento só configura `management.otlp.tracing.endpoint` — não toca em
`management.otlp.metrics.export`, então o auto-config do Actuator fica ligado por
padrão assim que `micrometer-registry-otlp` está no classpath, mirando
`localhost:4318/v1/metrics` por padrão); `@.claude/rules/observability.md` § Metrics
(fala de cardinalidade e alarme, nada sobre o receiver do lado do collector precisar
declarar a pipeline correspondente).

**O que ocorreu:** dois exemplares donos de metades diferentes do mesmo contrato —
`docker-architect` decide o que o collector aceita, `project-bootstrap` decide o que a
aplicação exporta — divergiram sem nenhuma regra checando consistência entre eles. O
resultado é determinístico: qualquer projeto gerado com `observability` ativo, com
`otel-collector` no ar, emite esse WARN a cada ciclo de publish, sempre, não é
específico deste projeto de demo.

**Lições:**

- O exemplar do collector devia nascer com `metrics` (e, já que é vendor-neutro por
  design — ver comentário do próprio arquivo — também `logs`, se/quando a feature
  exportar log via OTLP) ao lado de `traces`, com o mesmo exporter `debug`: não exige
  decisão nova de vendor, é o mesmo padrão já adotado pra traces.
- Alternativa caso o projeto não queira metrics via OTLP: `application-observability.yml.example`
  desabilitar explicitamente (`management.otlp.metrics.export.enabled: false`) e
  comentar o porquê — hoje não faz nem uma coisa nem outra, fica meio-fiado em
  silêncio.
- `observability.md` § Metrics não cobre o lado do receiver/pipeline do collector, só o
  que a aplicação mede. Falta uma linha ligando os dois exemplares donos desse
  contrato (`docker-architect`'s config do collector × `project-bootstrap`'s fragmento
  de `application.yml`) — sem isso, o próximo sinal OTLP que algum exemplar decidir
  exportar (logs, por exemplo) pode reproduzir o mesmo gap de novo, do zero.

---

## 2. Porta 4318 fixa em todo projeto gerado + nenhum ciclo de vida documentado pra derrubar o collector — container órfão de um projeto silenciosamente atende o tráfego OTLP de outro

**Onde:** `.claude/skills/docker-architect/templates/otel-collector-service.yml.example:13-14`
(`ports: - "4318:4318"`, hardcoded); `.claude/skills/docker-architect/SKILL.md:136`
chama isso de "fixed dev port", comparando de propósito com Postgres em 5432 — mas
Postgres tem motivo pra ficar de pé entre sessões (volume nomeado, dado que persiste);
o collector não exporta pra lugar nenhum útil fora da sessão atual (`debug` exporter,
sem backend real) e não tem por que continuar rodando depois que o projeto foi
fechado. Nenhum arquivo (`docker-architect/SKILL.md`, `arch-doctor`, `CLAUDE.md` do
projeto gerado) documenta "derrube o collector quando terminar" nem avisa sobre risco
de colisão de porta entre projetos-irmãos gerados do mesmo blueprint.

**O que ocorreu:** exatamente esse cenário — um container de um projeto de exemplo
diferente (`demo-clean-architecture-single-module-init-project-example`), esquecido no
ar há 8 dias, segurando a porta 4318 do host. O collector do projeto atual existia
(`docker compose up -d` tinha criado o container em algum momento) mas nunca
conseguiu de fato iniciar — ficou em estado `Created`, não `Up` — e ninguém percebeu
porque `docker compose up -d` reporta sucesso mesmo quando um serviço falha silenciosamente
ao bindar a porta se o comando não for checado linha a linha depois. A aplicação
mandava métricas e traces pro collector errado o tempo todo, que respondia 404 com uma
config que, por coincidência, tinha o mesmíssimo gap da lição 1 — isso levou a
primeira investigação pro caminho errado ("minha config tá incompleta" em vez de "não é
nem o meu collector").

**Lições:**

- `docker-architect` ou `arch-doctor` deviam checar, depois de subir os serviços, se
  cada um realmely está `Up` (não só `Created`/`Exited`) — hoje nada verifica isso, e
  `docker compose up -d` não falha o comando quando um serviço individual não sobe.
- Vale um check específico pra esse tipo de colisão: container de **outro** projeto
  (nome de serviço igual, prefixo de projeto diferente) publicando a mesma porta fixa
  que este projeto também usa. Um `docker ps --format '{{.Names}}\t{{.Ports}}' | grep
  4318` já teria apontado a causa raiz em segundos em vez de uma investigação em várias
  etapas.
- Documentar, no `CLAUDE.md` do projeto gerado ou no `docker-architect/SKILL.md`, que o
  `otel-collector` (ao contrário do Postgres) não tem motivo pra persistir entre
  sessões e deve ser derrubado (`docker compose down` ou `stop otel-collector`) ao
  terminar — ou, alternativa mais robusta, parar de fixar a porta host e publicar em
  porta efêmera pro dev que realmente precisa acessar de fora do compose network (a
  aplicação, dentro da rede, já fala com `otel-collector:4318` independente da
  publicação pro host).
