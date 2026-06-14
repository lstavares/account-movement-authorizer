# Account Movement Authorizer

Aplicação de autorização de movimentações de conta, criada como base evolutiva para consumir eventos, validar autorizações e registrar rastreabilidade de decisões.

## Objetivo

Esta aplicação será responsável por autorizar movimentações financeiras em contas bancárias, mantendo consistência de saldo e rastreabilidade das decisões. A Etapa 2 prepara a persistência base e a infraestrutura local; consumidor SQS, endpoint de transações e regras de autorização ainda serão implementados em etapas futuras.

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

## Persistência e infraestrutura local

O projeto usa PostgreSQL, Flyway e Spring Data JPA. O schema inicial cria as tabelas `accounts` e `transactions`, com validação do schema pelo Hibernate (`ddl-auto: validate`).

Para subir a infraestrutura básica:

```bash
docker compose up -d postgres localstack
```

Para popular a fila SQS local com contas sintéticas:

```bash
docker compose --profile seed up message-generator
```

Para consultar mensagens da fila:

```bash
aws --endpoint-url=http://localhost:4566 --region sa-east-1 sqs receive-message --queue-url http://localhost:4566/000000000000/conta-bancaria-criada --max-number-of-messages 10
```

## Decisões de modelagem

- O package base do projeto é `br.com.leandrotavares.accountmovementauthorizer`.
- Valores monetários são persistidos como inteiros em centavos (`Long`), evitando `Double`.
- `transactions.id` representa o `transactionId` e prepara a idempotência futura das autorizações.
- `accounts.id` será usado como chave idempotente para abertura de contas recebidas via SQS.
- `transactions.account_id` é uma referência lógica, sem FK obrigatória nesta fase, para permitir auditoria futura de recusas por conta inexistente (`ACCOUNT_NOT_FOUND`).
- `AccountRepository.findByIdForUpdate` já prepara lock pessimista para consistência de saldo em cenários concorrentes.
- Observabilidade começa com Actuator. Prometheus/Grafana ficam fora desta etapa.

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
./gradlew clean test
```
