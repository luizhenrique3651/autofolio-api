# AutoFolio API - Backend

Backend API para o sistema de gerenciamento de portfólio de veículos **AutoFolio**.

## 🏗️ Arquitetura do Projeto

O projeto adota os princípios da **Clean Architecture** (Arquitetura Limpa) com uma organização de pacotes orientada a funcionalidades (**Feature-based Packaging**).

### Organização de Pacotes
O código é organizado por domínios de negócio para garantir alta coesão e baixo acoplamento:
- `com.autofolio.auth`: Autenticação e Autorização (JWT Stateless).
- `com.autofolio.vehicle`: Gestão de inventário de veículos.
- `com.autofolio.sale`: Processamento de vendas e transações.
- `com.autofolio.cashflow`: Controle financeiro e fluxo de caixa.
- `com.autofolio.config`: Configurações centrais do Spring Boot.
- `com.autofolio.shared`: Componentes, utilitários e validações compartilhadas (ex: `SaturdayValidator`).

### Camadas Internas
Dentro de cada funcionalidade, seguimos a separação rigorosa de responsabilidades:
1.  **Controller (Interface)**: Endpoints REST, validação de entrada e mapeamento de DTOs.
2.  **Service (Negócio)**: Implementação das regras de negócio e orquestração.
3.  **Repository (Persistência)**: Abstração de acesso a dados com Spring Data JPA.
4.  **Domain**: Entidades JPA e Records (DTOs imutáveis) para transporte de dados.

## 🚀 Tecnologias Core

- **Java 21**: Uso intensivo de **Virtual Threads** para alta performance em operações de I/O.
- **Spring Boot 3.3**: Framework base para a aplicação.
- **PostgreSQL**: Banco de dados relacional robusto.
- **Flyway**: Versionamento e migração de schema de banco de dados.
- **Spring Security 6**: Segurança baseada em JWT sem estado.
- **Testcontainers**: Testes de integração confiáveis com instâncias reais de bancos de dados.

## ⚙️ Configurações de Performance

Para maximizar a escalabilidade na VPS, as **Virtual Threads** estão habilitadas nativamente:
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

## 🛠️ Como Executar

1.  Certifique-se de ter o **Java 21** instalado.
2.  Configure as variáveis de ambiente necessárias (`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`).
3.  Execute o comando Maven:
    ```bash
    mvn clean spring-boot:run
    ```

---
Desenvolvido com foco em escalabilidade, manutenibilidade e qualidade de código.
