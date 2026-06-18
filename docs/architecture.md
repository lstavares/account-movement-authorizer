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
    DB-->>App: CREATED ou já existente
    App->>SQS: deleteMessage em sucesso/idempotência
```

## Fluxo de autorização de transação

```mermaid
sequenceDiagram
    participant Client as Cliente HTTP
    participant API as TransactionAuthorizationController
    participant Service as AuthorizeTransactionService
    participant DB as PostgreSQL

    Client->>API: POST /transactions/{transactionId}
    API->>Service: command validado
    Service->>DB: consulta transactionId
    alt transação já existe com mesmo payload
        DB-->>Service: transação existente
        Service-->>API: resultado idempotente
    else nova transação
        Service->>DB: lock pessimista da conta
        alt CREDIT ou DEBIT autorizado
            Service->>DB: atualiza saldo e registra SUCCEEDED
        else falha funcional
            Service->>DB: registra FAILED sem alterar saldo quando aplicável
        end
        Service-->>API: envelope de resposta
    end
    API-->>Client: 200, 400 ou 409
```

## Proposta de arquitetura cloud pública

```mermaid
flowchart LR
    clients["Clientes"] --> edge["API Gateway ou Load Balancer"]
    edge --> service["Serviço containerizado<br/>ECS / EKS / App Runner"]
    service --> rds["PostgreSQL gerenciado<br/>RDS"]
    sqs["SQS<br/>conta-bancaria-criada"] --> service
    service --> cw["CloudWatch<br/>logs, métricas e alarmes"]
    secrets["Secrets Manager / Parameter Store"] --> service
    registry["Container Registry"] --> service
    pipeline["CI/CD"] --> registry
    pipeline --> deploy["Deploy canary<br/>ou blue/green"]
    deploy --> service
```

## Observabilidade e operação

- Logs em formato simples key-value para permitir busca por `transactionId`, `accountId`, `messageId`, `status` e `failureReason`.
- Actuator exposto somente com endpoints operacionais seguros: health, info, metrics e prometheus.
- Métricas Prometheus podem ser coletadas por Prometheus gerenciado, agente OpenTelemetry ou CloudWatch Agent, conforme a plataforma escolhida.

## Escalabilidade

- A API pode escalar horizontalmente porque a consistência de saldo depende do lock transacional no PostgreSQL.
- O consumidor SQS pode escalar horizontalmente, desde que a idempotência por `accounts.id` seja mantida.
- Contas muito quentes podem gerar contenção no banco; particionamento funcional ou filas por chave seriam próximos passos se esse gargalo aparecer.
