# Account Movement Authorizer

Aplicação de autorização de movimentações de conta, criada como base evolutiva para consumir eventos, validar autorizações e registrar rastreabilidade de decisões.

## Objetivo

A primeira versão deste repositório contém apenas o bootstrap da aplicação. As regras de domínio de conta, transação, integração com SQS, persistência e auditoria serão implementadas nas próximas etapas.

## Stack inicial

- Kotlin
- Spring Boot 3
- Gradle Kotlin DSL
- Spring Web
- Spring Validation
- Spring Data JPA
- PostgreSQL Driver
- Flyway
- Spring Boot Actuator
- springdoc-openapi 2.x (compatível com Spring Boot 3)
- AWS SDK SQS v2
- Jackson Kotlin
- JUnit 5
- MockK
- Testcontainers PostgreSQL

## Decisões da V1

- O package base do projeto é `br.com.leandrotavares.accountmovementauthorizer`.
- Apesar das dependências de JPA, PostgreSQL e Flyway já estarem preparadas, a configuração de banco ainda não é ativada nesta etapa para manter o bootstrap simples e executável sem infraestrutura externa.
- Conta inexistente será tratada em etapa futura como uma recusa de autorização para conta não previamente recebida via SQS. Para simplificar a V1, a API poderá retornar o erro funcional `ACCOUNT_NOT_FOUND`, sem implementar uma solução complexa de auditoria para conta inexistente neste momento.
- A abertura de contas usará `accounts.id` como chave idempotente na primeira versão de domínio. Não será criada a tabela `account_opening_messages` agora; uma evolução futura poderá adicionar auditoria detalhada e DLQ.
- Observabilidade começa apenas com Actuator. Prometheus/Grafana ficam fora desta etapa, mantendo pontos simples para métricas futuras.

## Como executar

```bash
./gradlew bootRun
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

## Como testar

```bash
./gradlew test
```
