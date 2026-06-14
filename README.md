# Account Movement Authorizer

Aplicação de autorização de movimentações de conta, criada como base evolutiva para consumir eventos, validar autorizações e registrar rastreabilidade de decisões.

## Objetivo

Esta aplicação será responsável por autorizar movimentações financeiras em contas bancárias, mantendo consistência de saldo e rastreabilidade das decisões. A Etapa 3 implementa o consumidor SQS de abertura de contas; endpoint de transações e regras de autorização ainda serão implementados em etapas futuras.

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

## Consumidor SQS de abertura de contas

A aplicação consome mensagens da fila standard `conta-bancaria-criada` usando AWS SDK v2 e LocalStack:

- endpoint: `http://localhost:4566`
- region: `sa-east-1`
- queue URL: `http://localhost:4566/000000000000/conta-bancaria-criada`
- credentials locais: `test/test`

O polling é configurado em `app.sqs.account-opening`. Ele fica desligado por padrão e é habilitado no profile `local`.

Para iniciar a aplicação consumindo a fila:

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

No PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
./gradlew bootRun
```

Cada mensagem válida cria uma conta em `accounts` com:

- `id` vindo de `account.id`
- `owner_id` vindo de `account.owner`
- `status` vindo de `account.status`
- `opened_at` convertido de `account.created_at`, recebido como epoch seconds em string
- `balance_amount = 0`
- `balance_currency = BRL`
- `received_at` no momento do processamento

A idempotência é feita por `accounts.id`. Se a conta já existir, o consumidor considera a mensagem processada com sucesso, não altera nenhum campo existente e deleta a mensagem da fila. Em erro inesperado ou payload inválido, a mensagem não é deletada para permitir reprocessamento; uma DLQ real fica como evolução futura.

## Decisões de modelagem

- O package base do projeto é `br.com.leandrotavares.accountmovementauthorizer`.
- Valores monetários são persistidos como inteiros em centavos (`Long`), evitando `Double`.
- `transactions.id` representa o `transactionId` e prepara a idempotência futura das autorizações.
- `accounts.id` será usado como chave idempotente para abertura de contas recebidas via SQS.
- `transactions.account_id` é uma referência lógica, sem FK obrigatória nesta fase, para permitir auditoria futura de recusas por conta inexistente (`ACCOUNT_NOT_FOUND`).
- `AccountRepository.findByIdForUpdate` já prepara lock pessimista para consistência de saldo em cenários concorrentes.
- Observabilidade começa com Actuator. Prometheus/Grafana ficam fora desta etapa.
- O consumidor SQS de abertura de contas usa `accounts.id` como chave idempotente e trata duplicidade concorrente como conta já existente.
- Payload inválido da fila é logado e mantido na fila nesta etapa; redrive/DLQ real é uma evolução futura.

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
