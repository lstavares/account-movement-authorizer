# Arquitetura

## Arquitetura local

```mermaid
flowchart LR
    client["HTTP client / avaliador"] --> app["Spring Boot app<br/>profile local"]
    app --> postgres["PostgreSQL<br/>accounts / transactions"]
    app --> localstack["LocalStack<br/>SQS"]
    generator["message-generator<br/>profile seed"] --> localstack
    localstack --> app
```

## Fluxo de abertura de conta via SQS

```mermaid
sequenceDiagram
    participant Generator as message-generator
    participant SQS as LocalStack SQS
    participant App as AccountOpeningSqsPoller
    participant DB as PostgreSQL

    Generator->>SQS: envia account-opening messages
    App->>SQS: receiveMessage
    SQS-->>App: mensagem com account.id
    App->>App: valida e converte payload
    App->>DB: insere conta com saldo inicial zero
    DB-->>App: CREATED ou ja existente
    App->>SQS: deleteMessage em sucesso/idempotencia
```

## Fluxo de autorizacao de transacao

```mermaid
sequenceDiagram
    participant Client as Cliente HTTP
    participant API as TransactionAuthorizationController
    participant Service as AuthorizeTransactionService
    participant DB as PostgreSQL

    Client->>API: POST /transactions/{transactionId}
    API->>Service: command validado
    Service->>DB: consulta transactionId
    alt transacao ja existe com mesmo payload
        DB-->>Service: transacao existente
        Service-->>API: resultado idempotente
    else nova transacao
        Service->>DB: lock pessimista da conta
        alt CREDIT ou DEBIT autorizado
            Service->>DB: atualiza saldo e registra SUCCEEDED
        else falha funcional
            Service->>DB: registra FAILED sem alterar saldo quando aplicavel
        end
        Service-->>API: envelope de resposta
    end
    API-->>Client: 200, 400 ou 409
```

## Proposta de arquitetura cloud publica

```mermaid
flowchart LR
    clients["Clientes"] --> edge["API Gateway ou Load Balancer"]
    edge --> service["Servico containerizado<br/>ECS / EKS / App Runner"]
    service --> rds["PostgreSQL gerenciado<br/>RDS"]
    sqs["SQS<br/>conta-bancaria-criada"] --> service
    service --> cw["CloudWatch<br/>logs, metricas e alarmes"]
    secrets["Secrets Manager / Parameter Store"] --> service
    registry["Container Registry"] --> service
    pipeline["CI/CD"] --> registry
    pipeline --> deploy["Deploy canary<br/>ou blue/green"]
    deploy --> service
```

## Observabilidade e operacao

- Logs em formato simples key-value para permitir busca por `transactionId`, `accountId`, `messageId`, `status` e `failureReason`.
- Actuator exposto somente com endpoints operacionais seguros: health, info, metrics e prometheus.
- Metricas Prometheus podem ser coletadas por Prometheus gerenciado, agente OpenTelemetry ou CloudWatch Agent, conforme a plataforma escolhida.

## Escalabilidade

- A API pode escalar horizontalmente porque a consistencia de saldo depende do lock transacional no PostgreSQL.
- O consumidor SQS pode escalar horizontalmente, desde que a idempotencia por `accounts.id` seja mantida.
- Contas muito quentes podem gerar contencao no banco; particionamento funcional ou filas por chave seriam proximos passos se esse gargalo aparecer.
