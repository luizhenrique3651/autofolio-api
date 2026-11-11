# ADR-001: Padrões obrigatórios de engenharia da AutoFolio API

**Status:** Accepted
**Date:** 2026-10-31
**Deciders:** Luiz Henrique Albuquerque Coelho (dono do produto e do repositório)
**Escopo:** todo código, configuração e teste da `autofolio-api` a partir da Sprint 1 (AF-009 em diante)

## Context

A Sprint 1 (AF-009 a AF-012: login, refresh, recuperação e redefinição de senha) foi implementada por uma bancada de agentes: desenvolvedor, revisores de arquitetura, performance e manutenibilidade, tech lead, revisores de erros e de warnings, e QA. Durante essas tasks, aconteceram problemas que se repetiram:

- **Erros visíveis só na IDE:** um bean que não subia sem profile e warnings de inspeção chegaram ao fim do ciclo sem serem detectados. Os testes passavam porque usavam mocks.
- **Mudanças sem revisão:** correções feitas depois da aprovação (por bug do QA) chegaram a ir para commit sem passar pelo tech lead.
- **Diagnóstico tratado como opcional:** achados sobre log e rastreamento de erro foram classificados como "observação" e quase ficaram de fora.
- **Contrato de erro divergente:** havia dois formatos de erro (ProblemDetail próprio e o padrão do Spring, em inglês).
- **Commit incompleto:** arquivos novos ficaram fora do commit, e os testes não compilavam a partir daquele ponto do histórico.

A Definition of Done do roadmap (seção 2.3) já pedia testes, lint e Swagger, mas não cobria esses pontos. Esta ADR consolida o que foi decidido e passa a valer como regra.

## Decision

As regras abaixo são **obrigatórias**. Revisões devem tratar qualquer violação como correção obrigatória, nunca como opcional.

### 1. Logs e rastreabilidade de erros em produção

1. **Nenhum achado sobre log, nível, stacktrace, correlação ou dado pessoal em log é opcional.** Entra sempre como correção obrigatória.
2. **Toda requisição tem um `X-Request-Id`.** O `RequestIdFilter` aceita o id do cliente (`[A-Za-z0-9._-]{1,64}`) ou gera um UUID. O id vai para o MDC (`requestId`) e volta no header da resposta. O CORS libera o envio e a leitura desse header.
3. **O MDC chega ao `@Async`** via `TaskDecorator`. Código assíncrono novo deve usar o executor configurado.
4. **O padrão de log inclui `%X{requestId}`** em todos os profiles.
5. **Todo `ProblemDetail` traz `requestId`**, além de `timestamp`.
6. **Log nunca contém token, hash completo, senha ou e-mail.** Identifique pelo `userId`. Para token, use no máximo um prefixo do hash. O datasource usa `logServerErrorDetail=false` (pgjdbc não inclui `Detail: Key (coluna)=(valor)` nas mensagens, que o Hibernate logaria em ERROR), e o logger de bind (`BasicBinder`/TRACE) é proibido em qualquer profile.
7. **Falha esperada de dependência externa** (ex.: SMTP) sai em `ERROR`, com `userId`, tipo da exceção e tipo da causa raiz, sem a mensagem quando ela puder conter dado pessoal. A stacktrace completa vai para `DEBUG`.
8. **Falha inesperada** sai em `ERROR` com stacktrace, **uma única vez**. Quem relança não loga a stacktrace; ela fica no handler global. As linhas `ERROR` do `SqlExceptionHelper` (driver, só SQLState e mensagem, sem stacktrace) não contam como essa "única vez"; o `ERROR` com contexto (`userId`) é o nosso. Banco indisponível é falha esperada de dependência (§1.7): 1 linha `ERROR` sem stacktrace.
9. **O catch-all 500** loga o erro e relança `AccessDeniedException` e `AuthenticationException`, para não transformá-las em 500.
10. **`orElseThrow()` sem argumento é proibido** em código de produção. A exceção precisa explicar o que faltou.

### 2. Contrato de erro da API

1. **Todo erro responde `application/problem+json`** (RFC 7807), montado pela factory `ProblemDetails`, com `type` da autofolio, `title` em português, `status`, `error` (reason phrase), `path`, `timestamp` e `requestId`.
2. **O `GlobalExceptionHandler` estende `ResponseEntityExceptionHandler`.** Assim, 404, 405, 406, 415 e demais erros do Spring MVC seguem o mesmo contrato.
3. **400 de validação** traz a propriedade `errors`, uma lista de objetos `{ field, message }` (corpo, parâmetros e `ConstraintViolation`). **JSON malformado** dá 400 com mensagem genérica, sem detalhes do parser.
4. **Fluxos sensíveis não revelam existência de conta:**
   - mesmo 401 para e-mail inexistente e senha errada, inclusive no tempo de resposta;
   - `forgot-password` responde sempre 202;
   - mesma resposta para token inexistente, expirado ou revogado.
5. **Mapeamento de exceções específicas:**
   1. **`DataIntegrityViolationException` vira 409 genérico**, sem nome de constraint nem SQL. O log WARN leva os tipos da exceção e da causa raiz, o nome da constraint e a origem (1º frame `com.autofolio`); a stacktrace, que traz o SQL com valores, só vai para DEBUG.
   2. **Recurso inexistente ou de outro usuário** lança `EntityNotFoundException` (404), conforme a [ADR-003](ADR-003-404-para-recurso-de-outro-usuario.md). O `getReferenceById` não é usado (a `jakarta.persistence.EntityNotFoundException` dele não tem mapeamento): referencie o dono por id, como no `Vehicle`.
   3. **`ConstraintViolationException`** só vira 400 quando a violação é de parâmetro de método; de retorno ou de entidade (pre-persist) é bug nosso e vira 500 com log ERROR.
   4. **Regra de validação que depende de contexto** (ex.: ano atual) lança `RequestValidationException` (400 com `errors`).
   5. **Falha de infraestrutura na autenticação** (banco fora ao checar a conta) responde 503 pelo entry point; o validador registra 1 ERROR com `userId` e os tipos da exceção, sem stacktrace nem mensagem, e a stacktrace vai para DEBUG (§1.8).
   6. **Enum inválido no JSON** dá 400 com `errors` (campo e valores aceitos); JSON sintaticamente inválido segue genérico.
   7. **O 500** responde "Erro interno — tente novamente.".
6. **O `detail` dos erros do Spring MVC é traduzido em `messages.properties`** (`problemDetail.<FQCN>`), sem mapear exceção a exceção no handler.
7. **5xx gerado pelo Spring MVC** (ex.: `HttpMessageNotWritableException`) também sai em `ERROR` com stacktrace, uma vez, no `handleExceptionInternal`.

### 3. Segurança e autenticação

1. **JWT de acesso** HS256 via `spring-boot-starter-oauth2-resource-server` (Nimbus), sem filtro JWT próprio. Validade de 1 hora. A chave tem no mínimo 32 bytes.
2. **Rotas públicas ignoram Bearer inválido** (`BearerTokenResolver`). Um token expirado no header não pode derrubar login, refresh ou portfólio.
3. **Refresh token opaco** (UUID), validade de 30 dias, persistido, não rotacionado, com vários dispositivos por usuário.
4. **Token de uso único** (redefinição de senha) é armazenado **apenas como hash SHA-256** e consumido de forma **atômica**, por `UPDATE` condicional na mesma transação da operação.
5. **Senha:** BCrypt força 12 e a política `@StrongPassword` (8 a 72 bytes, 1 número, 1 maiúscula). A política vale igual no cadastro e na redefinição: o cadastro nunca exige menos que a redefinição.
6. **Redefinir a senha** revoga todos os refresh tokens e os demais tokens de reset pendentes do usuário.

### 4. Performance e I/O

1. **I/O lento** (SMTP, chamadas externas) roda fora da requisição (`@Async` sobre virtual threads), **sem transação nem conexão de banco aberta** durante a chamada.
2. **Todo cliente de rede tem timeout explícito.** SMTP: 5 segundos para conexão, leitura e escrita.
3. **`spring.jpa.open-in-view: false`.** Operações de CPU caras, como BCrypt, não seguram conexão do pool.
4. **Health check não depende de recurso não crítico.** `management.health.mail.enabled: false`.
5. **Operações de limpeza** em endpoints públicos são restritas ao escopo do usuário e cobertas por índice.

### 5. Configuração

1. **A configuração base (`application.yml`) só tem o que vale para todos os profiles.** Ela **não** define `spring.datasource` nem `cloudinary.*` (credenciais) e exige `JWT_SECRET`. Quem define banco e Cloudinary são os profiles: `dev` (com defaults de dev, onde fizer sentido) e `prod` (sem defaults, fail-fast). Sem profile, o contexto só sobe se `spring.datasource.*` e `cloudinary.*` forem passados por propriedade, como faz o `ApplicationContextIT`. Todo bean obrigatório novo deve resolver com esse conjunto mínimo (base + propriedades), nunca depender só de um profile.
2. **Toda variável lida do `.env`** é mapeada explicitamente num yml (`chave: ${VARIAVEL:default}`). O `dotenv-java` grava System properties com o nome da variável, que não se ligam automaticamente a propriedades com ponto.
3. **Cada configuração tem o default em um só lugar** (yml ou constante), nunca duplicado em `@Value`.
4. **O `.env.example` lista todas as variáveis** usadas por qualquer profile, sem valores reais. O `.env` nunca é commitado nem editado por agentes.
5. **Dependência nova só com aprovação explícita do dono do repositório.**
6. **Toda mudança de schema é uma migration Flyway nova.** Migrations aplicadas nunca são editadas. O Hibernate roda com `ddl-auto: validate`.

### 6. Testes

1. **Testes de integração** usam Testcontainers sobre a base comum `AbstractPostgresIT` (PostgreSQL singleton). Nunca usam o banco local.
2. **O `ApplicationContextIT`** sobe o contexto completo sem profile (com `spring.datasource.*` e `cloudinary.*` por propriedade, §5.1) e sem mocks e deve continuar passando. Ele cobre o que mocks escondem, como beans ausentes.
3. **Testes nunca enviam e-mail nem chamam serviço externo real.** Exceção conhecida: `CloudinaryIntegrationTest`, isolado com `@Tag("external")`: o surefire exclui a tag por padrão e ele só roda sob demanda com `mvn test -Pexternal-tests`. Tem `@Timeout` e é ignorado (Assumptions) sem as credenciais `CLOUDINARY_*`. Todo teste novo que chame serviço externo real deve usar a mesma tag.
4. **Toda decisão de log e segurança tem teste:** formato do log sem dado pessoal, ramo de cada exceção, concorrência no consumo de tokens e `requestId` no corpo igual ao do header.
5. **O comando oficial** `mvn test -Dtest='*Test,*IT' -Dsurefire.runOrder=random` e o `mvn verify` (que cobra o check de cobertura da §6.7) passam antes de qualquer revisão.
6. **Testes independentes de ordem e de ambiente.** A suíte inteira passa numa única JVM em ordem aleatória (`mvn test -Dtest='*Test,*IT' -Dsurefire.runOrder=random`), que é como a IDE roda. O bloco estático do `AutoFolioApplication` grava o `.env` como System properties para a JVM inteira. Por isso, teste que resolve configuração sem subir o contexto não pode usar as fontes `systemProperties`/`systemEnvironment`, nem depender de variável do SO.
7. **Cobertura:** o JaCoCo roda no `mvn test` (relatório em `target/site/jacoco/index.html`) e o `mvn verify` exige no mínimo 80% de linhas e de branches em cada `*Service`. Teste vazio para inflar o número é proibido.

### 7. Qualidade estática

1. **Inspeções do IntelliJ** (Qodana Community, perfil "Project Default") rodam em toda task. Todo achado é tratado, exceto a regra `unused`, que é falso positivo sem o plugin Spring. Inspeções exclusivas do Ultimate (autowire, SQL) são cobertas pelo `ApplicationContextIT` e pela configuração do Data Source na IDE.
2. **Compilação com `-Xlint:all`** não tem warnings novos.
3. **Estilo:** Google Java Style com indentação de 4 espaços, verificado pelo Checkstyle no build, que bloqueia (fase `validate`, `src/main` e `src/test`, zero violações na DoD). Regras em `config/checkstyle/checkstyle.xml`; supressões só em `config/checkstyle/suppressions.xml`, justificadas. Ver [ADR-002](ADR-002-estilo-de-codigo-checkstyle-google-4-espacos.md). Suprimida hoje: `AbbreviationAsWordInName` nos `*IT.java` (o sufixo `IT` é a convenção do filtro `-Dtest='*IT'`).
4. **Warnings** são avaliados um a um pelo tech lead (corrigir, adiar como débito ou ignorar com justificativa).

### 8. Processo de entrega

1. **Fluxo de toda task:**
   1. desenvolvimento;
   2. revisão de **erros** no projeto inteiro, **sempre primeiro e sozinha**: as demais só começam depois do resultado dela;
   3. revisões paralelas de **arquitetura**, **performance** e **manutenibilidade** (o diff avaliado contra a codebase inteira);
   4. revisão geral do **tech lead**;
   5. **warnings**, com laço tech lead ↔ desenvolvedor;
   6. **QA funcional** contra os critérios do card e esta ADR;
   7. **commit pelo dono do repositório**.
2. **Mudança de produção feita depois de uma aprovação** (inclusive correção de bug do QA) volta ao ciclo de revisão e ao QA. Exceção: correções de warnings, que o tech lead reavalia diretamente. Mudanças só em `src/test` ou sem efeito em runtime não voltam, mas são informadas.
3. **Agentes nunca fazem commit, push, reset ou checkout de arquivos.** A cada task, o dono do repositório commita.
4. **Antes do commit**, `git status` não pode ter arquivo da task fora do stage (use `git add -A` e confira). O commit precisa compilar e passar nos testes sozinho.
5. **A mensagem de commit segue Conventional Commits** com o id do card: `feat(AF-XXX): ...`.
6. **Várias tasks sem commit entre elas** (quando o dono do repositório autoriza): ao fim de cada task aprovada, grava-se um snapshot não destrutivo da árvore (`snap.sh <nome>`: índice temporário + `git write-tree`, sem tocar branch, índice ou working tree), e a revisão da task seguinte olha só o diff desde esse snapshot (`taskdiff.sh <snapshot>`). Ao final, o maestro entrega os comandos prontos para **um commit por task** (`git read-tree <tree-da-task>` + `git commit` em sequência e `git add -A` no último), depois de confirmar que o snapshot base é igual ao `HEAD`. Os scripts ficam em `~/.cache/egeon-autofolio/`.

## Options Considered

### Opção A: Regras implícitas, decididas task a task (situação até a AF-011)

| Dimensão | Avaliação |
|---|---|
| Complexidade | Baixa no início, crescente com o tempo |
| Custo | Alto: retrabalho e rodadas extras de revisão |
| Escalabilidade | Ruim: cada revisor ou agente novo reaprende as regras |
| Familiaridade | Depende de quem lembra do histórico |

**Prós:** nenhuma burocracia inicial.
**Contras:** regras se perdem entre tasks e contextos limpos; achados importantes viram "opcional"; revisões discordam entre si; o histórico de decisões fica espalhado em conversas.

### Opção B: Só ampliar a Definition of Done do roadmap

| Dimensão | Avaliação |
|---|---|
| Complexidade | Baixa |
| Custo | Baixo |
| Escalabilidade | Média |
| Familiaridade | Alta |

**Prós:** um único lugar já conhecido.
**Contras:** a DoD é um checklist sem o porquê. Não registra decisões técnicas (bibliotecas, armazenamento de token, contrato de erro), e o roadmap está num `.docx` fora do controle de versão (`doc/` está no `.gitignore`).

### Opção C: ADR versionada no repositório com as regras obrigatórias (escolhida)

| Dimensão | Avaliação |
|---|---|
| Complexidade | Média |
| Custo | Baixo: um documento mantido junto do código |
| Escalabilidade | Boa: base de referência para revisores, QA e novos agentes |
| Familiaridade | Padrão de mercado (ADR) |

**Prós:** versionada com o código; traz o porquê de cada regra; pode ser citada em revisões ("viola ADR-001 §1.6"); decisões futuras entram como novas ADRs.
**Contras:** precisa ser mantida. Uma regra desatualizada aqui engana mais do que ajuda.

## Trade-off Analysis

A Opção C troca um pequeno custo de manutenção por previsibilidade. Em tasks já entregues, o custo de não ter regras explícitas foi alto: a AF-010 e a AF-011 precisaram de várias rodadas extras de revisão e teste por causa de pontos (contrato de erro, log, bean sem profile) que regras escritas teriam evitado na primeira passada. A DoD (Opção B) continua valendo como checklist de saída e é complementada por esta ADR, que é a fonte das regras técnicas.

## Consequences

- **Fica mais fácil:** revisar com critério objetivo, recusar opcionais que são obrigatórios, integrar um revisor ou agente novo e explicar decisões ao front-end. Exemplos para o front: `requestId` no corpo, `X-Request-Id` no header e política de senha no cadastro.
- **Fica mais difícil:** atalhos. Toda correção de produção depois de uma aprovação custa uma nova rodada de revisão.
- **Precisa ser revisitado:**
  - esta ADR, a cada nova decisão transversal (que deve gerar ADR própria ou uma emenda aqui);
  - as supressões do checkstyle, a cada nova exceção;
  - a exceção do `CloudinaryIntegrationTest`.

## Action Items

Os itens de acompanhamento (pendências, hardening e débitos) ficam em [docs/backlog.md](../backlog.md), com prioridade, sprint/card sugerido e status.

## Desvios conscientes do roadmap

Pontos em que a implementação diverge do texto do card, por decisão registrada:

| Card | Roadmap diz | Implementado | Motivo |
|---|---|---|---|
| AF-001 | Branch de produção `main` | `master` | Branch já existente no repositório; renomear exige ajuste de proteção e remoto (ver backlog) |
| AF-015 | Resposta `{ timestamp, status, error, message, path }` | `ProblemDetail` (RFC 7807) com `type`, `title`, `detail`, `error`, `path`, `timestamp`, `requestId` | Um único contrato de erro, padrão de mercado; ver §2.1 |
| AF-016 | `yearMod` não detalhado como obrigatório | `yearMod` obrigatório (igual a `yearFab` ou o seguinte) | Evita veículo sem ano-modelo no portfólio; CHECK no banco (V6) |
| AF-014 | Falhas de upload sem código definido | 502 em falha ou timeout do Cloudinary | A falha é de dependência externa, não do cliente nem nossa |
| AF-018/019 | 403 para veículo de outro usuário | 404, igual a inexistente | Não revelar a existência do recurso; ver [ADR-003](ADR-003-404-para-recurso-de-outro-usuario.md) |
