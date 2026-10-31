# AutoFolio API

Backend do **AutoFolio**, sistema para vendedores autônomos de veículos gerenciarem estoque, vendas, fluxo de caixa e clientes, com um **portfólio público** acessível por URL (`/{slug}`).

API REST stateless em **Java 21 + Spring Boot 3.3**, com PostgreSQL, Flyway, autenticação JWT e upload de mídia no Cloudinary.

> **Status:** Fase 1 (MVP) em desenvolvimento. Sprints 0 e 1 (fundação e autenticação) concluídas. Veja o [roadmap](#-roadmap).

---

## Sumário

- [Stack](#-stack)
- [Arquitetura](#-arquitetura)
- [Endpoints](#-endpoints)
- [Contrato de erros e rastreabilidade](#-contrato-de-erros-e-rastreabilidade)
- [Segurança](#-segurança)
- [Banco de dados](#-banco-de-dados)
- [Como executar localmente](#-como-executar-localmente)
- [Variáveis de ambiente](#-variáveis-de-ambiente)
- [Testes](#-testes)
- [Padrões de desenvolvimento](#-padrões-de-desenvolvimento)
- [Roadmap](#-roadmap)

---

## 🧰 Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 (Virtual Threads habilitadas) |
| Framework | Spring Boot 3.3 (Web, Data JPA, Validation, Security, Mail, Actuator) |
| Autenticação | Spring Security 6 + OAuth2 Resource Server (JWT HS256, Nimbus) |
| Banco de dados | PostgreSQL 16 |
| Migrações | Flyway |
| Mídia | Cloudinary (SDK `cloudinary-http44`) |
| Documentação | springdoc-openapi (Swagger UI) |
| Testes | JUnit 5, Mockito, AssertJ, MockMvc, Testcontainers |
| Infra local | Docker Compose (PostgreSQL + pgAdmin) |

---

## 🏗️ Arquitetura

Organização **por domínio** (feature-based packaging), com camadas **Controller → Service → Repository** dentro de cada domínio.

```
com.autofolio
├── auth          Cadastro, login, refresh token, recuperação e redefinição de senha
├── vehicle       Veículos e fotos                     (Sprint 2–3)
├── sale          Vendas e estorno                     (Sprint 3)
├── cashflow      Fluxo de caixa                       (Sprint 4)
├── client        Clientes e LGPD                      (Sprint 4)
├── portfolio     Portfólio público                    (Sprint 5)
├── dashboard     KPIs e gráficos                      (Sprint 5)
├── report        Relatórios CSV/PDF                   (Sprint 6)
├── config        Security, JWT, Clock, Async, Cloudinary, PasswordEncoder
└── shared
    ├── exception    GlobalExceptionHandler + factory ProblemDetails (RFC 7807)
    ├── security     TokenHasher (SHA-256)
    ├── validation   @StrongPassword
    └── web          RequestIdFilter (X-Request-Id / MDC)
```

Convenções adotadas:

- **DTOs como Java Records**, validados com Jakarta Bean Validation.
- **Erros no formato `ProblemDetail`** (RFC 7807), centralizados no `GlobalExceptionHandler`.
- **Toda mudança de schema** é uma migration Flyway nova; migrations aplicadas nunca são editadas.
- **I/O lento fora da requisição**: envio de e-mail roda em `@Async` sobre virtual threads, sem transação aberta.
- **`Clock` injetável** para regras dependentes de tempo (expiração de tokens), o que torna os testes determinísticos.

---

## 🔌 Endpoints

Base: `/api/v1`. A documentação interativa fica em **`/swagger-ui.html`** (spec em `/api-docs`).

### Autenticação (`/api/v1/auth`, pública)

| Método | Rota | Descrição | Respostas |
|---|---|---|---|
| `POST` | `/register` | Cadastra vendedor (nome, e-mail, slug, senha); devolve `{ accessToken, refreshToken, user }` | `201`, `400`, `409` |
| `POST` | `/login` | Autentica por e-mail e senha | `200`, `400`, `401` |
| `POST` | `/refresh` | Gera novo access token a partir do refresh token | `200`, `400`, `401` |
| `POST` | `/forgot-password` | Envia e-mail com link de redefinição | `202` (sempre) |
| `POST` | `/reset-password` | Redefine a senha com o token recebido por e-mail | `200`, `400` |

Exemplo de login (resposta com `expiresIn`, ao contrário do register):

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"vendedor@exemplo.com","password":"Senha123"}'
```

```json
{ "accessToken": "eyJ...", "refreshToken": "1f0c...", "expiresIn": 3600 }
```

As rotas privadas exigem `Authorization: Bearer <accessToken>`.

### Monitoramento

| Rota | Observação |
|---|---|
| `/actuator/health`, `/actuator/info`, `/actuator/metrics` | Expostos, mas exigem autenticação por enquanto |

---

## 🧾 Contrato de erros e rastreabilidade

Todo erro responde `application/problem+json`:

```json
{
  "type": "https://autofolio.com/errors/unauthorized",
  "title": "Não Autorizado",
  "status": 401,
  "detail": "Credenciais inválidas.",
  "timestamp": "2026-10-31T12:00:00Z",
  "requestId": "3b1f6c2e-..."
}
```

- 400 de validação traz a propriedade `errors` com os campos inválidos.
- 404, 405, 406 e 415 seguem o mesmo formato, com `title` em português.
- **`X-Request-Id`**: toda resposta devolve o header. O cliente pode enviar o próprio id (`[A-Za-z0-9._-]{1,64}`); caso contrário, a API gera um UUID. O mesmo id aparece no corpo dos erros (`requestId`) e em **todas as linhas de log** (`%X{requestId}`), inclusive nas tarefas `@Async`. Ao reportar um problema, informe esse id. O CORS libera o envio e a leitura do header pelo navegador (`allowedHeaders`/`exposedHeaders`).

---

## 🔐 Segurança

- **JWT de acesso** HS256, validade de **1 hora**, claims `sub` (userId), `slug`, `iat` e `exp`.
- **Refresh token** opaco (UUID), validade de **30 dias**, persistido em `refresh_tokens`. Permite vários dispositivos e não é rotacionado.
- **Senhas** com BCrypt força 12. A política (`@StrongPassword`) vale para cadastro e redefinição: mínimo de 8 caracteres, no máximo 72 bytes, com ao menos 1 número e 1 letra maiúscula.
- **Sem enumeração de usuários**: login responde o mesmo 401 para e-mail inexistente e senha errada (inclusive no tempo de resposta), e `forgot-password` responde sempre 202.
- **Token de redefinição**: UUID com validade de 1 hora, guardado apenas como **hash SHA-256** e consumido de forma atômica. Após a redefinição, todos os refresh tokens do usuário são revogados.
- **Logs sem dados sensíveis**: nunca registram token, hash completo, senha ou e-mail. Falhas são logadas com `userId` e tipo da exceção.
- **CORS** liberado apenas para `FRONTEND_URL`. CSRF desabilitado (API stateless).

Rotas públicas: `/api/v1/auth/**`, `/api/v1/portfolio/**` e a documentação Swagger. Todo o resto exige JWT.

---

## 🗄️ Banco de dados

PostgreSQL com migrations em `src/main/resources/db/migration`:

| Migration | Conteúdo |
|---|---|
| `V1__init_schema.sql` | Schema core: usuários, perfis, veículos, vendas, clientes, fluxo de caixa; ENUMs, índices e chaves UUID |
| `V2__create_refresh_tokens.sql` | Tabela `refresh_tokens` |
| `V3__create_password_reset_tokens.sql` | Tabela `password_reset_tokens` |
| `V4__hash_password_reset_tokens.sql` | Token de redefinição passa a ser armazenado como hash (`token_hash`) |

O Hibernate roda com `ddl-auto: validate`: quem altera o schema é sempre o Flyway.

---

## 🚀 Como executar localmente

**Pré-requisitos:** Java 21, Maven 3.9+ e Docker.

1. **Configure o ambiente**

   ```bash
   cp .env.example .env
   ```

   Preencha os valores (veja [Variáveis de ambiente](#-variáveis-de-ambiente)). O `.env` é carregado automaticamente na inicialização pelo `dotenv-java`, sem precisar exportar nada no terminal.

2. **Suba o banco**

   ```bash
   docker compose up -d db        # PostgreSQL em localhost:5432
   docker compose up -d pgadmin   # opcional: pgAdmin em http://localhost:5050
   ```

3. **Rode a API com o profile `dev`**

   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

   A API sobe em `http://localhost:8080`, e o Flyway aplica as migrations automaticamente.

4. **E-mail em dev (opcional):** por padrão a API envia para um SMTP local sem autenticação em `localhost:1025`. Use qualquer captura de e-mail local (ex.: Mailpit ou MailHog) para ver os e-mails de redefinição. Sem SMTP, o envio falha apenas no log e a API continua respondendo normalmente.

### Profiles

| Profile | Uso |
|---|---|
| *(nenhum)* | Só a configuração base (`application.yml`), que não define banco nem Cloudinary: é preciso passar `spring.datasource.*` e `cloudinary.*` por propriedade. As variáveis `DATABASE_URL` e `CLOUDINARY_*` só são lidas pelos profiles `dev` e `prod` |
| `dev` | Banco local com defaults, SQL no log, SMTP local sem TLS, logs `DEBUG` em `com.autofolio` |
| `prod` | Exige todas as credenciais (banco, SMTP, JWT), SMTP com STARTTLS, logs `INFO` |

---

## 🔑 Variáveis de ambiente

| Variável | Obrigatória | Descrição |
|---|---|---|
| `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` | sim | Credenciais do PostgreSQL (usadas pela API e pelo Docker Compose) |
| `DATABASE_URL` | prod | URL JDBC. Em dev, o padrão é `jdbc:postgresql://localhost:5432/autofolio_dev` |
| `JWT_SECRET` | sim | Chave HS256 com **no mínimo 32 bytes** (a API não sobe com chave menor) |
| `FRONTEND_URL` | prod | Origem liberada no CORS e base do link de redefinição de senha. Padrão: `http://localhost:5173` |
| `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` | sim (`dev` e `prod`) | Credenciais do Cloudinary. Sem elas o contexto não sobe |
| `MAIL_HOST` | prod | Servidor SMTP. Padrão de dev: `localhost` |
| `MAIL_PORT` | não | Porta SMTP. Padrão: `1025` em dev, `587` em prod |
| `MAIL_USER`, `MAIL_PASS` | prod | Credenciais SMTP |
| `MAIL_FROM` | não | Remetente dos e-mails. Padrão: `noreply@autofolio.com` |
| `PGADMIN_DEFAULT_EMAIL`, `PGADMIN_DEFAULT_PASSWORD` | não | Acesso ao pgAdmin local |

> Nunca commite o `.env`: ele já está no `.gitignore`.

---

## 🧪 Testes

```bash
mvn test                    # testes unitários e de controller (+ CloudinaryIntegrationTest, que usa rede)
mvn test -Dtest='*IT'       # testes de integração (PostgreSQL real via Testcontainers)
```

- **Integração** usa Testcontainers com um PostgreSQL singleton compartilhado (`AbstractPostgresIT`). Precisa do Docker rodando e não toca no banco local.
- **`ApplicationContextIT`** sobe o contexto completo sem profile e sem mocks, para garantir que todos os beans são criados.
- Os testes nunca enviam e-mail de verdade.
- O padrão do surefire não roda as classes `*IT` no `mvn test`. Rode-as com o segundo comando.
- `CloudinaryIntegrationTest` faz upload real e depende de rede externa, então pode falhar de forma intermitente.

Meta de qualidade (Definition of Done): cobertura ≥ 80% nos services.

---

## 📐 Padrões de desenvolvimento

As regras obrigatórias de engenharia (logs e rastreabilidade, contrato de erro, segurança, configuração, testes, qualidade e processo de entrega) estão na **[ADR-001](docs/adr/ADR-001-padroes-obrigatorios-de-engenharia.md)**. Decisões transversais novas entram como novas ADRs em `docs/adr/`.

**Branches**

| Tipo | Padrão |
|---|---|
| Feature | `feature/AF-XXX-descricao` |
| Fix | `fix/AF-XXX-descricao` |
| Chore / Docs / Test / Refactor | `chore/…`, `docs/…`, `test/…`, `refactor/…` |

Fluxo: `feature/AF-XXX` → `develop` (PR) → `master`.

**Commits** ([Conventional Commits](https://www.conventionalcommits.org/) com o id do card):

```
feat(AF-008): implement user registration endpoint
fix(AF-025): revert vehicle status on sale error
chore(AF-003): configure docker-compose local env
```

**Definition of Done**

- Critérios de aceite do card verificados.
- Testes unitários da lógica de negócio, com cobertura ≥ 80% no módulo.
- Sem violações de lint/formatação.
- Endpoint documentado no Swagger (`@Operation`, `@ApiResponse`).
- PR mergeado em `develop` sem conflitos.

---

## 🗺️ Roadmap

12 sprints de 2 semanas (Fase 1 — MVP). Backend nas sprints 0–6, DevOps na 7, frontend nas 8–11 e go-live na 12.

| Sprint | Foco | Status |
|---|---|---|
| 0 | Fundação: skeleton, Docker Compose, Flyway (schema completo), Cloudinary | ✅ Concluída |
| 1 | Auth: Spring Security, register, login JWT, refresh, recuperação e redefinição de senha | ✅ Concluída |
| 2 | Perfil do vendedor + CRUD de veículos | ⏳ Próxima |
| 3 | Fotos (Cloudinary) + registro de vendas e estorno | |
| 4 | Fluxo de caixa + clientes (LGPD) | |
| 5 | Portfólio público + dashboard de KPIs + e-mails | |
| 6 | Relatórios CSV/PDF, Swagger completo, testes | |
| 7 | DevOps: VPS, Docker prod, Nginx + HTTPS, CI/CD, backup, monitoramento | |
| 8–11 | Frontend (React + Vite) | |
| 12 | QA e go-live | |

---

Desenvolvido por Luiz Henrique Albuquerque Coelho · Licença [Apache 2.0](LICENSE).
