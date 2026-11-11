# AGENTS.md — AutoFolio API

Guia para agentes de IA (Claude Code, Codex, Gemini e outros) que trabalham neste repositório. Ele é curto de propósito: diz **o que ler e quando**, e aponta para a documentação que é a fonte da verdade. Não copie regras daqui para o código nem para outros documentos; cite a fonte.

> **Projeto:** API REST stateless do AutoFolio (gestão de estoque, vendas, caixa e portfólio público de vendedores de veículos). Java 21 + Spring Boot 3.3, PostgreSQL + Flyway, JWT (OAuth2 Resource Server), Cloudinary. Visão completa no [README](README.md).

---

## 1. Discovery progressivo

Não leia o repositório inteiro antes de começar. Carregue contexto **em camadas**, só até o ponto que a tarefa exige:

| Camada | Quando | O que ler |
|---|---|---|
| **0. Sempre** | Toda tarefa | Este arquivo inteiro. |
| **1. Regras** | Antes de escrever ou revisar código | Em [ADR-001](docs/adr/ADR-001-padroes-obrigatorios-de-engenharia.md), só as seções que a tarefa toca (veja a tabela abaixo). A ADR-001 é **obrigatória**: violar uma regra dela é sempre correção obrigatória. |
| **2. Contexto da tarefa** | Ao entender o pedido | O card no roadmap (veja §5) e, no [README](README.md), só as seções do domínio afetado (Endpoints, Contrato de erros, Segurança, Banco). |
| **3. Código vizinho** | Antes de criar algo novo | Como o projeto já resolve o mesmo problema: o pacote do domínio (`com.autofolio.<domínio>`) e `shared/`, `config/`. Use `grep`/`find`; abra arquivos inteiros só quando forem mudar ou servirem de modelo. |
| **4. Decisões e pendências** | Se a tarefa toca algo com histórico | As ADRs relacionadas (§2) e o [backlog](docs/backlog.md), que pode já prever a mudança ou um débito que a tarefa deve fechar. |

**Mapa: se a tarefa mexe em… leia antes…**

| Assunto | ADR-001 | Outras fontes |
|---|---|---|
| Logs, exceções, diagnóstico | §1 (logs e rastreabilidade) | `shared/exception/ExceptionSummary` |
| Resposta de erro / validação | §2 (contrato de erro) | README → "Contrato de erros"; ADR-003 |
| Autenticação, senha, tokens, conta | §3 (segurança) | `auth/`, `auth.security/`, `config/SecurityConfig` |
| I/O externo, banco, performance | §4 (performance e I/O) | `config/CloudinaryProperties`, `application.yml` |
| Configuração, `.env`, profiles, migrations | §5 (configuração) | README → "Variáveis de ambiente" e "Profiles" |
| Testes | §6 (testes) | `AbstractPostgresIT`, `ApplicationContextIT` |
| Estilo, lint, warnings | §7 (qualidade) | [ADR-002](docs/adr/ADR-002-estilo-de-codigo-checkstyle-google-4-espacos.md) |
| Commits, revisão, entrega | §8 (processo) | §4 deste arquivo |

## 2. ADRs (Architecture Decision Records)

Ficam em [`docs/adr/`](docs/adr/). Hoje:

| ADR | Decisão |
|---|---|
| [ADR-001](docs/adr/ADR-001-padroes-obrigatorios-de-engenharia.md) | Regras obrigatórias de engenharia (logs, erros, segurança, I/O, configuração, testes, qualidade, processo) e desvios conscientes do roadmap |
| [ADR-002](docs/adr/ADR-002-estilo-de-codigo-checkstyle-google-4-espacos.md) | Google Java Style com indentação de 4 espaços, verificado pelo Checkstyle no build |
| [ADR-003](docs/adr/ADR-003-404-para-recurso-de-outro-usuario.md) | Recurso de outro usuário responde 404, igual a inexistente |

**Como usar:**
- **Ao revisar**, cite a regra violada: `viola ADR-001 §1.6`.
- **Nunca contradiga uma ADR em silêncio.** Se a tarefa (ou o card do roadmap) pede algo que conflita com uma ADR, pare e leve a decisão ao dono do repositório. A ADR vence até ele decidir.
- **Crie uma ADR nova** (`ADR-00N-titulo-em-kebab-case.md`, formato das existentes: Status, Date, Deciders, Context, Decision, Options Considered, Trade-off Analysis, Consequences) quando a decisão for **transversal** (vale para mais de um domínio) ou tiver **alternativas reais com trade-off**. Exemplos: escolha de biblioteca, contrato de API, estratégia de segurança. Ajuste pontual de regra existente é emenda na ADR, não ADR nova.
- **Desvio consciente do roadmap** (o card pede X, o projeto faz Y por decisão) é registrado na seção "Desvios conscientes do roadmap" da ADR-001.
- **Pendência** (algo a fazer depois) vai para o [backlog](docs/backlog.md) com prioridade e card/sprint, nunca só num comentário ou numa ADR.

## 3. Comandos

O projeto usa **JDK 21**. O Maven do Homebrew pode rodar em Java 26, que quebra o Mockito; force o 21:

```bash
export JAVA_HOME=~/.sdkman/candidates/java/21.0.9-zulu

mvn checkstyle:check                                          # 0 violações (também roda na fase validate)
mvn test -Dtest='*Test,*IT' -Dsurefire.runOrder=random        # suíte oficial: tudo numa JVM, ordem aleatória
mvn verify                                                     # + cobertura JaCoCo (≥ 80% linhas e branches por *Service)
mvn test -Pexternal-tests                                      # só sob demanda: upload real ao Cloudinary
mvn spring-boot:run -Dspring-boot.run.profiles=dev            # API local (Postgres: docker compose up -d db)
```

"Pronto" significa: checkstyle 0, suíte oficial verde, `mvn verify` verde, critérios do card atendidos e nada fora do escopo alterado.

## 4. Processo de entrega

- **Agentes nunca fazem commit, push, reset ou checkout de arquivos, nem criam branches.** Quem commita é o dono do repositório, ao fim de cada task.
- **Fluxo de cada task:** implementação → revisão de erros do projeto inteiro, **sempre primeiro e sozinha** → revisões de arquitetura, performance e manutenibilidade (em paralelo, só depois da de erros) → revisão geral do tech lead → warnings → QA funcional → commit pelo dono. Mudança de produção feita depois de uma aprovação volta ao ciclo (ADR-001 §8.2).
- **Várias tasks sem commit entre elas** (quando o dono autoriza): snapshot não destrutivo por task e, no fim, comandos prontos para um commit por task (ADR-001 §8.6). Os scripts `snap.sh` e `taskdiff.sh` ficam em `~/.cache/egeon-autofolio/`.
- **Mensagem de commit:** Conventional Commits com o id do card, ex.: `feat(AF-017): implementar listagem de veículos` (ADR-001 §8.5).
- **Dependência nova** (inclusive plugin de build) só com aprovação explícita do dono (ADR-001 §5.5).
- **Nunca edite o `.env`** nem o exponha; documente variáveis novas no `.env.example` sem valores reais.

## 5. Onde está o quê

| Item | Local |
|---|---|
| Roadmap e cards (AF-XXX), DoD | `doc/AutoFolio_Roadmap_Sprints.docx` (fora do git: `doc/` está no `.gitignore`). Versão em texto para agentes: `~/.cache/egeon-autofolio/roadmap.txt` (`textutil -convert txt -stdout doc/AutoFolio_Roadmap_Sprints.docx`). |
| Regras e decisões | `docs/adr/` |
| Pendências priorizadas | `docs/backlog.md` |
| Visão, endpoints, contrato de erro, execução | `README.md` |
| Skills técnicas de referência | `../autofolio-*/SKILL.md` (backend-architect, concurrency-performance, quality-assurance, devops-media, frontend-expert) |
| Migrations | `src/main/resources/db/migration` (`V1`…; nunca edite uma já aplicada) |

## 6. Armadilhas conhecidas

- **`.env` vira System property da JVM inteira** (bloco estático do `AutoFolioApplication`). Teste que resolve configuração sem subir contexto não pode depender de `systemProperties`/`systemEnvironment` (ADR-001 §6.6).
- **Toda variável do `.env` precisa de mapeamento explícito no yml** (`chave: ${VARIAVEL:default}`); o dotenv não faz relaxed binding (ADR-001 §5.2).
- **`getReferenceById` é proibido**; busque por id **e** dono (`findByIdAndUserId`) e responda 404 (ADR-003).
- **Builders Lombok de entidade** precisam preencher todo campo `nullable = false` (o IntelliJ Ultimate acusa; o Qodana Community não).
- **Nada de PII em log**: o datasource usa `logServerErrorDetail=false`; logger de bind (`BasicBinder`/TRACE) é proibido (ADR-001 §1.6).
- **O IntelliJ não aplica o filtro de tag do surefire**: "All Tests" na IDE roda o `CloudinaryIntegrationTest` (upload real) se `CLOUDINARY_*` estiver no `.env`. Use Tags `!external`.
