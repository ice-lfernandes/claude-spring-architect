# Lessons learned — instalação do plugin `spring-architect` via `/spring-architect:arch-adopt` (demo-clean-arch-single-module)

Origem: primeira execução do `arch-adopt` neste projeto, partindo do plugin
`claude-spring-architect@0.1.0` instalado pelo marketplace. O projeto já tinha um
`.claude/` completo gerado por `/init-project` em 2025-09-17, mas **sem**
`.arch-provenance.json` — ou seja, o caminho de *install* da skill, não o de *update*,
apesar de não ser um projeto virgem.

Resultado final: sucesso. 134 arquivos escritos, `schema` exit 0, `doctor`
`✅ Setup operational`, provenance `133 file(s) unchanged since`, nenhuma citação morta.
Blueprint efetivo: `clean-architecture-single-module-service`, uma variante local do
`clean-architecture-single-module` oficial.

Os itens abaixo são as lacunas que a execução expôs, não falhas de resultado. Todos foram
verificados nesta sessão; onde a causa não foi confirmada, está dito explicitamente.

Ambiente: macOS (Darwin 25.6.0), zsh, OpenJDK Temurin 21.0.11, `GH_TOKEN` não definido,
repositório de origem público.

---

## 1. `arch-adopt` não consegue resolver `base_url` a partir do que o plugin carrega — bootstrap circular

**Onde:** `skills/arch-adopt/SKILL.md:40-41` (*"Read `base_url`, `git_url`, `auth_env` and
`default_ref` from the `source` block of `.claude/schemas/extensions.json` — this project's
copy if it has one, otherwise from the plugin's"*); `SKILL.md:155-156` (§ Contract,
*"never a URL from memory"*); `.plugin-source.json` do plugin publicado.

**O que ocorreu:** os dois caminhos que a skill oferece falharam.

- **A cópia do plugin não existe.** O plugin publicado contém exatamente um arquivo
  (`skills/arch-adopt/SKILL.md`), por decisão registrada no seu próprio
  `.plugin-source.json`: *"everything ELSE the architecture consists of (...) is fetched at
  adopt time and is deliberately not vendored here"*. Não há
  `.claude/schemas/extensions.json` no plugin. `find <plugin> -name extensions.json`
  retorna vazio.
- **A cópia do projeto existia, mas sem o bloco `source`.** O `extensions.json` gerado por
  `/init-project` em 2025-09-17 tinha os blocos `types`, `forbidden_everywhere` etc., e
  `grep '"source"'` nele retornava zero. Só passou a ter o bloco **depois** deste export.

O `.plugin-source.json` carrega `git_url`, `raw_url`, `auth_env` e `ref` — mas **não**
`base_url`. `raw_url` serve para um arquivo por vez, não para o tarball que o passo 3
espera.

Saída usada: um fetch extra, não previsto pela skill, de
`https://raw.githubusercontent.com/.../v0.1.0/.claude/schemas/extensions.json`, só para ler
`source.base_url` de lá (`extensions.json:131`) e então executar o passo 3 como escrito.

**Lições:**

- O passo 2 precisa de um terceiro caminho, e o plugin já carrega os dados para ele:
  `.plugin-source.json` tem `raw_url` e `ref`, o que basta para buscar o `extensions.json`
  da origem e ler `source` de lá. Tornar isso o passo explícito — não uma improvisação de
  quem executa — mantém a invariante "nunca uma URL de memória" sem depender de um arquivo
  que o plugin decidiu não vendorizar.
- Alternativa mais simples: adicionar `base_url` ao `.plugin-source.json`. O custo é
  duplicar um valor que o `extensions.json` já possui, com o risco de divergência que o
  próprio repositório trata como bug.
- Um projeto gerado antes do bloco `source` existir **não consegue se atualizar sozinho** —
  que é justamente a propriedade que o comentário do bloco `source` reivindica
  (*"which is what lets a project update itself without the plugin"*). Vale um modo de
  diagnóstico que detecte um `extensions.json` sem `source` e diga o que fazer.

---

## 2. `ArchHook.java export` pendura indefinidamente quando `stdin` não é redirecionado

**Onde:** `skills/arch-adopt/SKILL.md:102-103` (passo 5, sem `</dev/null`) contra
`SKILL.md:114-115` (passo 6, com `</dev/null` em `schema` e `doctor`).

**O que ocorreu:** a invocação do passo 5 exatamente como documentada, com a saída
redirecionada para arquivo, não retornou — foi interrompida pelo timeout de 120s do
executor e movida para background:

```
CLAUDE_PROJECT_DIR=$S/arch-src java $S/arch-src/.claude/hooks/ArchHook.java export . \
  --blueprint <id> --ref v0.1.0 --dry-run 2>&1 > $S/dryrun.txt
→ Command did not complete within its 120s timeout
```

A mesma invocação com `</dev/null` acrescentado retornou em segundos, `exit=0`. A execução
sem redirecionamento de saída também tinha funcionado, o que sugere que o modo lê `stdin`
quando não está ligado a um TTY interativo.

**Causa não confirmada:** não foi investigado *qual* leitura de `stdin` o modo `export`
faz — `ArchHook.java` tem 184.738 bytes e os modos compartilham entrada para o protocolo de
hook. A reprodução, porém, é determinística.

**Lições:**

- Acrescentar `</dev/null` ao bloco do passo 5, como já está nos comandos do passo 6. É uma
  linha de documentação e elimina um travamento que, para quem executa em background ou em
  CI, aparece como "o export não termina" sem nenhuma mensagem.
- Melhor ainda: `export` não deveria ler `stdin`. Os modos que participam do protocolo de
  hook precisam disso; um modo de linha de comando invocado por humano, não.

---

## 3. O stamp grava `"commit": "unknown"` quando a origem é um tarball

**Onde:** `.claude/.arch-provenance.json` gerado (`"commit": "unknown"`) contra
`skills/arch-adopt/SKILL.md:126`, que manda o relatório trazer
`ref <ref> (<short sha>)`.

**O que ocorreu:** o passo 3 extrai um tarball do `codeload`, que não contém `.git`. O
`export` então não tem de onde resolver o sha e grava `unknown`. O relatório do passo 6,
porém, pede o short sha — que **é** conhecido nesse ponto, porque o passo 2 já rodou
`git ls-remote` e recebeu `f667f796a157c19ac0d2bb997346f7b991de1966` junto com a tag. A
informação existe na sessão e não chega ao arquivo que deveria preservá-la.

**Consequência prática:** a proveniência registra uma tag, e tag é um ponteiro móvel. Um
`v0.1.0` re-tagueado deixa o stamp apontando para um conteúdo diferente do que foi
instalado, sem nada detectar. É a mesma classe de problema que o `default_ref: latest-tag`
existe para evitar.

**Lições:**

- Dar ao `export` uma flag `--commit <sha>` e o `arch-adopt` passa o sha que o passo 2 já
  resolveu. O modo continua determinístico: o sha é entrada, não algo que ele descobre.
- Enquanto isso, o passo 6 deveria comparar o `commit` do stamp com o sha resolvido e
  relatar quando for `unknown`, em vez de o relatório imprimir um short sha que o arquivo
  não contém.

---

## 4. Um blueprint customizado não tem onde morar: o `export` lê blueprints da origem e não copia nenhum para o projeto

**Onde:** `skills/arch-adopt/SKILL.md:94-96` (*"Option 3 chosen: copy (...) to
`/tmp/arch-src/.claude/blueprints/<name>/<name>.yaml`"*); `SKILL.md:161` (*"May write
`/tmp/arch-src/**` — the fetched tree"*); bloco `export.copy` do `extensions.json`, que não
tem nenhuma entrada sob `blueprints/`.

**O que ocorreu:** este projeto precisou de uma variante do blueprint oficial (ver item 5).
Seguindo o passo 4, ela foi criada no tree baixado. O export a consumiu corretamente — o
`doctor` confirma `blueprint clean-architecture-single-module-service`. Mas:

- o `.yaml` que **define a arquitetura do projeto** ficou num diretório temporário;
- o `.arch-provenance.json` grava esse id, e o passo 4 do próximo `arch-adopt` vai lê-lo do
  stamp e **não perguntar nada**;
- o próximo export falha, porque a origem não tem esse blueprint — e falha num ponto em que
  ninguém foi avisado de que precisava preservar um arquivo.

A skill declara o custo do custom em termos de `packages.map` errado
(`SKILL.md:89-93`), que é real, mas **não** menciona que o artefato não sobrevive.

Contornado à mão neste projeto: o `.yaml` foi copiado para
`.claude/blueprints/clean-architecture-single-module-service/` e uma nota em
`CLAUDE.md` § Known pitfalls descreve o `cp -r` necessário antes do próximo update. Nada
disso é do plugin — é reparo local de uma lacuna do plugin.

**Lições:**

- Quando o blueprint escolhido não é um dos oficiais, o `export` deveria copiá-lo para o
  projeto (`.claude/blueprints/<id>/<id>.yaml`) e o `arch-adopt` deveria preferir a cópia
  local à da origem ao resolver o id do stamp. Sem isso, a opção 3 do passo 4 produz um
  projeto que não pode ser atualizado.
- O `doctor` tem como detectar isso hoje: stamp com um `blueprint` que não existe nem na
  origem nem no projeto é um estado diagnosticável, e é melhor descobri-lo num diagnóstico
  do que num export interrompido.
- O texto do custo da opção 3 deveria incluir esta frase, não só a do `packages.map`.

---

## 5. O blueprint oficial mapeia `application.shared`; o projeto que o próprio pipeline gerou usa `application.service`

**Onde:** `blueprints/clean-architecture-single-module/clean-architecture-single-module.yaml:61`
(`application.shared: "application.shared"`, com comentário citando `lessons-learned-006 § 4`)
contra o layout real deste projeto:
`src/main/java/com/example/democleanarchsinglemodule/application/service/` e três testes que
o referenciam, entre eles
`src/test/java/com/example/democleanarchsinglemodule/application/service/IdempotentExecutionTest.java`.

**O que ocorreu:** o passo 4 (install) comparou o layout com os `packages.map` dos
blueprints. Dez dos onze valores casaram exatamente; o décimo primeiro divergiu no nome.
O `CLAUDE.md` gerado em 2025-09-17 documenta `application.service` como o pacote dos
componentes de aplicação compartilhados, "e.g. `IdempotentExecution`" — que é, palavra por
palavra, o exemplo que o comentário do blueprint oficial usa para justificar
`application.shared`. São o mesmo conceito com dois nomes.

Se o export tivesse rodado com o blueprint oficial, `rules/naming.md` e seis templates de
skills passariam a citar `application.shared` enquanto o código permanece em
`application.service`. `forbidden-imports.txt` **não** quebraria — ele não está na lista do
export e sua regra usa o prefixo `application.`, que cobre os dois — mas a próxima
`/new-feature` criaria `application/shared/` ao lado do `application/service/` existente,
dois pacotes para um papel.

**Causa confirmada em 2026-09-27, durante a remediação (0055):** nenhuma das três
hipóteses — não houve renomeação. `git log -S 'application.shared' -- .claude/blueprints/`
retorna um único commit, `496b10f` (2026-09-17, remediação do lessons-learned-006), que
**adicionou** a chave; `git log -S 'application.service'` sobre o mesmo diretório não
retorna nada, ou seja, esse valor nunca existiu no blueprint. O `application/service/`
deste projeto é um nome que o executor inventou quando o blueprint ainda não tinha entrada
para o papel — exatamente a falha que `496b10f` entrou para impedir, descrita em
`lessons-learned-006 § 4`.

Consequência para a lição abaixo: um teste de CI contra *renomeação* de valor em
`packages.map` não teria pego este caso, e guarda uma falha ainda não observada
(invariante 6 ao contrário). Foi descartado no 0055; o que entra é o aviso no passo 4.

**Lições:**

- Este é o achado com maior custo potencial, porque é **silencioso**: sem o passo 4 do
  `arch-adopt` comparar nomes, o export sobrescreve as normas e a divergência só aparece na
  próxima feature gerada, como um pacote duplicado que ninguém pediu. Vale um teste em
  `validate.yml` que verifique que os `packages.map` dos blueprints não mudam de valor entre
  releases sem uma nota de migração — renomear um pacote num blueprint é uma mudança
  quebrante para todo projeto já gerado com ele.
- O passo 4 deveria tratar "o layout casa com N-1 de N pacotes" como um caso próprio, com
  uma pergunta que mostre a divergência, em vez de deixar a detecção na mão de quem executa.
  Aqui funcionou; não é garantido que funcione sempre.
- Projetos gerados antes do `.arch-provenance.json` caem no caminho de *install* mesmo tendo
  um `.claude/` completo. Detectar um `.claude/` sem stamp e tratá-lo como "update sem
  proveniência" — lendo o blueprint do `rules/00-index.md`, que o nomeia em prosa — evita
  re-perguntar uma arquitetura que o projeto já tem.

---

## 6. O gate de worktree limpa barrou a execução por um arquivo que o próprio export sobrescreve

**Onde:** `skills/arch-adopt/SKILL.md:25-35` (passo 1); bloco `export.copy` do
`extensions.json`, que inclui
`{ "from": ".claude/skills/project-bootstrap/templates/audit-pricing.json.example",
"to": ".claude/audit-usage/pricing.json" }`.

**O que ocorreu:** a primeira invocação parou no passo 1, corretamente, com
`M .claude/audit-usage/pricing.json` como única modificação pendente. O usuário fez rollback
e a execução seguiu. Mas esse arquivo **está** na lista `export.copy`: seria sobrescrito
pelo export de qualquer forma. O gate bloqueou por um arquivo cujo conteúdo prévio o
próprio export descarta.

**Correção de um palpite errado desta sessão:** foi levantado que o hook `audit` teria
escrito o arquivo a cada `Stop`. Verificado e falso — `grep -n 'pricing' ArchHook.java`
mostra apenas leituras (`Files.isRegularFile`, `readOrNull`, `Json.parse`). O hook lê
`pricing.json`, nunca escreve. A origem da modificação não foi determinada.

**Lições:**

- O gate está certo em ser cego: distinguir "arquivo que vai ser sobrescrito" de "trabalho
  que vai ser perdido" é exatamente o julgamento que uma verificação determinística não
  deve tentar fazer. Mas a mensagem pode ajudar: quando os arquivos sujos são todos da lista
  do export, dizer isso permite ao usuário decidir com informação, em vez de tratar como um
  bloqueio opaco.
- `pricing.json` é um arquivo que o usuário tem motivo legítimo para editar (a própria
  documentação manda preencher: *"preencha `.claude/audit-usage/pricing.json`"*, visto em
  `ArchHook.java:2663`) e que o export sobrescreve sem aviso no relatório. Ou ele sai de
  `export.copy` em projetos que já o têm, ou o relatório precisa nomeá-lo entre os
  overwrites — hoje só `settings.json` aparece ali.

---

## 7. Atritos menores

- **`doctor` conclui `✅ Setup operational` exibindo um `❌` no corpo.** A linha
  `CLAUDE_PROJECT_DIR . ❌ NOT set — hooks use the current directory` aparece em toda
  execução (é o normal fora de um hook), e ainda assim compete visualmente com falhas reais.
  Um `ℹ️`, ou a supressão quando não é um problema, deixaria o `❌` significar uma coisa só.
- **`/tmp/arch-src` está fixo em cinco pontos da skill** (`SKILL.md:61,62,81,94,102`).
  Ambientes que fornecem um diretório temporário isolado por sessão — o caso aqui — precisam
  substituir o caminho em todos eles, e um esquecido silenciosamente mistura dois trees. Um
  único ponto de definição no começo do procedimento resolve.
- **O relatório do passo 6 pede contagens que o `export` não emite.** O template exige
  `<n> written · <n> unchanged · <n> overwritten`; a saída do modo traz `Files ....... 134` e
  uma linha `Overwrites`, sem separar "unchanged". Os números só saem cruzando com
  `git status --short` depois. Ou o `export` emite os três, ou o template pede o que existe.
- **`timeout` não existe no macOS.** Irrelevante para o plugin — foi um comando desta
  sessão, não da skill — mas vale como lembrete de que os exemplos do repositório rodam em
  três sistemas e `coreutils` não é uma dependência declarada.

---

## Resumo para o meta-repo

| # | Item | Onde corrigir | Gravidade |
|---|---|---|---|
| 1 | `base_url` inalcançável pelo plugin; projetos antigos sem bloco `source` | `SKILL.md:40-41` + `.plugin-source.json` | **Alta** — bloqueia o passo 3 |
| 2 | `export` pendura sem `</dev/null>` | `SKILL.md:102-103` + `ArchHook.java` | **Alta** — falha sem mensagem |
| 3 | `"commit": "unknown"` no stamp | `export` (flag `--commit`) + `SKILL.md:126` | Média — proveniência presa a uma tag móvel |
| 4 | Blueprint customizado não sobrevive no projeto | `export.copy` + `SKILL.md:89-96` | **Alta** — quebra o próximo update |
| 5 | `application.shared` vs. `application.service` | `validate.yml` + `SKILL.md` passo 4 | **Alta** — divergência silenciosa |
| 6 | Gate barra por arquivo que o export sobrescreve | `SKILL.md:25-35` + `export.copy` | Baixa — ruído, não perda |
| 7 | Atritos menores (`❌` em `doctor`, `/tmp` fixo, contagens do relatório) | vários | Baixa |

Itens 1, 2, 4 e 5 impedem que um projeto se atualize sozinho, que é a propriedade central
que o `arch-adopt` e o bloco `source` existem para garantir.
