# Insights operacionais

Esta fase adiciona visibilidade operacional ao serviço sem criar nova funcionalidade bancária. As métricas ajudam a observar consumo SQS, autorizações, falhas funcionais e latência em ambiente local ou em uma instalação com Actuator/Prometheus.

Isto não é um benchmark formal. Os números locais dependem da máquina, Docker, PostgreSQL, LocalStack, JVM warm-up e concorrência usada no teste.

## Endpoints

A aplicação já expõe endpoints operacionais seguros:

```bash
curl -i http://localhost:8080/actuator/metrics
curl -i http://localhost:8080/actuator/prometheus
```

Para inspecionar uma métrica específica pelo Actuator:

```bash
curl -s http://localhost:8080/actuator/metrics/transaction.authorizations
curl -s http://localhost:8080/actuator/metrics/transaction.authorization.duration
curl -s http://localhost:8080/actuator/metrics/account.opening.messages
```

## Métricas customizadas

As métricas são registradas no código com nomes idiomáticos do Micrometer, usando ponto. No endpoint Prometheus, esses nomes aparecem convertidos para o formato Prometheus, com underscores e sufixos como `_total` ou `_seconds`.

| Nome Micrometer | Nome Prometheus esperado | Tags | Ajuda a diagnosticar |
| --- | --- | --- | --- |
| `account.opening.messages` | `account_opening_messages_total` | `result=created|already_exists|invalid|error` | Resultado do processamento individual de mensagens SQS de abertura de conta. |
| `sqs.poll.messages.received` | `sqs_poll_messages_received_total` | sem tags customizadas | Volume de mensagens recebidas pelo poller. |
| `transaction.authorizations` | `transaction_authorizations_total` | `type=CREDIT|DEBIT`, `status=SUCCEEDED|FAILED` | Throughput de autorizações processadas por tipo e resultado funcional. |
| `transaction.authorization.failures` | `transaction_authorization_failures_total` | `reason=INSUFFICIENT_FUNDS|ACCOUNT_NOT_FOUND|ACCOUNT_DISABLED|IDEMPOTENCY_CONFLICT|VALIDATION_ERROR` | Motivos de falha funcional, conflito idempotente e erro de validação. |
| `transaction.authorization.duration` | `transaction_authorization_duration_seconds_count`, `transaction_authorization_duration_seconds_sum` | `type=CREDIT|DEBIT|UNKNOWN`, `outcome=SUCCEEDED|FAILED|IDEMPOTENCY_CONFLICT|VALIDATION_ERROR|ERROR` | Latência do fluxo de autorização observado. |
| `account.opening.processing.duration` | `account_opening_processing_duration_seconds_count`, `account_opening_processing_duration_seconds_sum` | `result=created|already_exists|invalid|error` | Latência de processamento de cada mensagem SQS. |

IDs como `transactionId`, `accountId`, `messageId`, `ownerId` e UUIDs não são usados como tags para evitar alta cardinalidade. Quando úteis, aparecem apenas nos logs key-value.

## Como observar SQS

Depois de subir a aplicação e popular a fila local:

```bash
docker compose --profile seed up message-generator
```

Consulte o Prometheus:

```bash
curl -s http://localhost:8080/actuator/prometheus | grep -E 'account_opening_messages|sqs_poll_messages_received|account_opening_processing_duration'
```

Indicadores práticos:

- `sqs_poll_messages_received_total`: o poller recebeu mensagens da fila.
- `account_opening_messages_total{result="created"}`: contas novas foram persistidas.
- `account_opening_messages_total{result="already_exists"}`: mensagens foram reprocessadas de forma idempotente.
- `account_opening_messages_total{result="invalid"}`: payloads inválidos foram encontrados e não deletados.
- `account_opening_messages_total{result="error"}`: houve erro inesperado no processamento.
- `account_opening_processing_duration_seconds_count`: quantidade de mensagens com tempo registrado.
- `account_opening_processing_duration_seconds_sum`: soma das durações em segundos.

Exemplo de média aproximada de processamento SQS em Prometheus:

```promql
rate(account_opening_processing_duration_seconds_sum[5m])
/
rate(account_opening_processing_duration_seconds_count[5m])
```

## Como observar autorizações

Gere algumas transações pela API ou pelo script local e consulte:

```bash
curl -s http://localhost:8080/actuator/prometheus | grep -E 'transaction_authorizations|transaction_authorization_failures|transaction_authorization_duration'
```

Indicadores práticos:

- `transaction_authorizations_total{type="CREDIT",status="SUCCEEDED"}`: créditos aprovados.
- `transaction_authorizations_total{type="DEBIT",status="SUCCEEDED"}`: débitos aprovados.
- `transaction_authorizations_total{type="DEBIT",status="FAILED"}`: débitos ou autorizações recusadas funcionalmente.
- `transaction_authorization_failures_total{reason="INSUFFICIENT_FUNDS"}`: débitos recusados por saldo insuficiente.
- `transaction_authorization_failures_total{reason="ACCOUNT_NOT_FOUND"}`: autorizações para conta inexistente.
- `transaction_authorization_failures_total{reason="ACCOUNT_DISABLED"}`: autorizações para conta desabilitada.
- `transaction_authorization_failures_total{reason="IDEMPOTENCY_CONFLICT"}`: mesmo `transactionId` usado com payload diferente.
- `transaction_authorization_failures_total{reason="VALIDATION_ERROR"}`: payload, UUID, moeda, tipo ou valor inválidos.

Exemplos Prometheus:

```promql
rate(transaction_authorizations_total[5m])
rate(transaction_authorization_failures_total[5m])
rate(transaction_authorization_duration_seconds_sum[5m])
/
rate(transaction_authorization_duration_seconds_count[5m])
```

Observação: `transaction.authorizations` mede chamadas processadas com resultado funcional, incluindo replay idempotente aceito. Conflitos de idempotência e erros de validação aparecem em `transaction.authorization.failures`.

Erros de validação são rejeitados antes da construção do comando de autorização. Por isso, eles aparecem com `type="UNKNOWN"`; a série `transaction_authorization_duration_seconds_count{outcome="VALIDATION_ERROR"}` funciona principalmente como marcador de ocorrência desses eventos no handler.

## Smoke/load local leve

O script local executa uma carga simples com `curl`, alternando pequenos créditos e débitos para uma conta existente:

```bash
bash scripts/smoke-load-transactions.sh <accountId> [requests=100] [concurrency=8] [baseUrl=http://localhost:8080]
```

Exemplo:

```bash
bash scripts/smoke-load-transactions.sh 5b19c8b6-0cc4-4c72-a989-0c2ee15fa975 100 8 http://localhost:8080
```

O script mostra:

- total de requests planejados;
- concorrência usada;
- tempo total aproximado;
- resumo simples dos HTTP status codes;
- comandos para consultar as métricas no Prometheus.

O script altera o saldo da conta local e não deve ser executado contra ambientes compartilhados ou produtivos. Ele não faz parte de `./gradlew clean test`.

## Logs

Os logs continuam em formato simples key-value. Eles podem incluir:

- autorização: `transactionId`, `accountId`, `type`, `status`, `failureReason`, `durationMs`;
- SQS: `messageId`, `accountId`, `result`, `queueName`, `durationMs`.

Payloads completos não são logados.

## Limitações

- As métricas em memória reiniciam quando a aplicação reinicia.
- O ambiente local não representa capacidade real de produção.
- Não há DLQ/redrive real nesta fase.
- Não foram adicionados endpoints de negócio, tabelas, migrations, autenticação, consulta de saldo ou extrato.
