# Lessons learned — nenhum exemplar de dashboard/UI de observabilidade existe; o vendor de visualização fica sempre por conta de uma pergunta espontânea

Origem: no mesmo projeto de demo do `lessons-learned-008` (collector OTLP corrigido,
`traces`+`metrics` publicando certo), usuário perguntou se já existia alguma
configuração de dashboard/UI pra observabilidade. Resposta: não — `docker-compose.yml`
só tem `app`, `postgres`, `otel-collector`, e o collector usa exporter `debug`
(loga span/métrica cru no próprio stdout do container, sem UI nenhuma). Usuário então
pediu recomendação de vendor pra visualização, com pontos fortes/fracos — respondido
com tabela comparando Grafana+Tempo+Prometheus, Jaeger, Zipkin, SigNoz e SaaS
(Datadog/New Relic/Honeycomb). Nenhuma dessas opções existe hoje como exemplar em
`docker-architect`: quem chega até "preciso de um backend real" depende de perguntar
espontaneamente e receber uma resposta ad-hoc, não de um caminho guiado pelo próprio
repo.

---

## 1. `docker-architect` para propositalmente no exporter `debug` e não oferece nenhum próximo passo pra um backend real — decisão de vendor fica sem exemplar, sem template, sem lição registrada

**Onde:** `.claude/skills/docker-architect/SKILL.md` (tabela de serviços, linha do
OpenTelemetry Collector: "No engine choice to make here — one vendor-neutral collector,
always the same shape, unlike Postgres/MySQL/Kafka which branch on a real decision" —
ou seja, o próprio skill trata a ausência de vendor como definitivo, não como um passo
futuro pendente); `.claude/skills/docker-architect/templates/otel-collector-config.yml.example:15-17`
(exporter `debug` com comentário "swap the exporter... once the project picks one
(Jaeger, Tempo, a SaaS vendor)" — cita as opções em prosa, mas não existe nenhum
`templates/*.example` pra nenhuma delas); `@.claude/rules/observability.md` (fala de
correlação e do que medir, nunca de onde visualizar).

**O que ocorreu:** Postgres, MySQL e Kafka têm ramificação real de engine no
`docker-architect` (o usuário escolhe, o skill sabe gerar cada um). O collector OTLP
não tem — é sempre a mesma forma, sempre `debug`. Isso é uma decisão deliberada (não
adivinhar vendor de observabilidade por conta própria), mas o resultado prático é que
**nenhum projeto gerado por este meta-repo jamais ganha uma UI de observabilidade**, a
menos que alguém pergunte no chat e receba uma resposta improvisada — que não vira
exemplar, não fica registrada em lugar nenhum, e precisa ser reconstruída do zero na
próxima vez que outro projeto (ou este mesmo, numa sessão futura) chegar ao mesmo
ponto.

**Lições:**

- Falta um exemplar (ou um conjunto deles, um por vendor) em
  `docker-architect/templates/` pros backends mais prováveis — no mínimo
  Grafana+Tempo+Prometheus (self-host, OTLP nativo, maior comunidade) e Jaeger
  (mínimo, só traces, 1 container). `docker-architect` já tem o padrão de "engine
  branch" pra Postgres/MySQL/Kafka — o mesmo padrão de pergunta ao usuário +
  template por opção resolveria isso sem quebrar a filosofia de "vendor-neutro por
  padrão".
- O comentário em `otel-collector-config.yml.example:15-17` promete a troca de
  exporter mas não entrega nenhum dos três exemplos que cita — ou o comentário some,
  ou os templates aparecem. Documentação que aponta pra um caminho que não existe é
  pior que não apontar.
- `docker-architect/SKILL.md` podia terminar o passo do collector perguntando (via
  `AskUserQuestion`, condicionalmente — só quando `observability` está ativo) se o
  projeto quer um backend de visualização agora ou depois; hoje o skill roda até o
  fim sem nunca levantar essa pergunta, e o único jeito de saber que a lacuna existe
  é o usuário perceber sozinho (como nesta sessão) que "debug" não é dashboard nenhum.
- Sem um exemplar fixo, cada resposta ad-hoc corre o risco de divergir da próxima —
  este chat recomendou Grafana+Tempo+Prometheus; a próxima sessão, sem este registro,
  podia recomendar outra coisa pro mesmo tipo de projeto, sem motivo técnico pra
  divergência.
