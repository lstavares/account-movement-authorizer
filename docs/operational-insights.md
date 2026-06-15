# Operational Insights

Esta fase adiciona visibilidade operacional ao servico sem criar nova funcionalidade bancaria. As metricas ajudam a observar consumo SQS, autorizacoes, falhas funcionais e latencia em ambiente local ou em uma instalacao com Actuator/Prometheus.

Isto nao e um benchmark formal. Os numeros locais dependem da maquina, Docker, PostgreSQL, LocalStack, JVM warm-up e concorrencia usada no teste.

## Endpoints

A aplicacao ja expoe endpoints operacionais seguros:

```bash
curl -i http://localhost:8080/actuator/metrics
curl -i http://localhost:8080/actuator/prometheus
```

Para inspecionar uma metrica especifica pelo Actuator:

```bash
curl -s http://localhost:8080/actuator/metrics/transaction.authorizations
curl -s http://localhost:8080/actuator/metrics/transaction.authorization.duration
curl -s http://localhost:8080/actuator/metrics/account.opening.messages
```

## Metricas customizadas

As metricas sao registradas no codigo com nomes idiomaticos do Micrometer, usando ponto. No endpoint Prometheus, esses nomes aparecem convertidos para o formato Prometheus, com underscores e sufixos como `_total` ou `_seconds`.

| Nome Micrometer | Nome Prometheus esperado | Tags | Ajuda a diagnosticar |
| --- | --- | --- | --- |
| `account.opening.messages` | `account_opening_messages_total` | `result=created|already_exists|invalid|error` | Resultado do processamento individual de mensagens SQS de abertura de conta. |
| `sqs.poll.messages.received` | `sqs_poll_messages_received_total` | sem tags customizadas | Volume de mensagens recebidas pelo poller. |
| `transaction.authorizations` | `transaction_authorizations_total` | `type=CREDIT|DEBIT`, `status=SUCCEEDED|FAILED` | Throughput de autorizacoes processadas por tipo e resultado funcional. |
| `transaction.authorization.failures` | `transaction_authorization_failures_total` | `reason=INSUFFICIENT_FUNDS|ACCOUNT_NOT_FOUND|ACCOUNT_DISABLED|IDEMPOTENCY_CONFLICT|VALIDATION_ERROR` | Motivos de falha funcional, conflito idempotente e erro de validacao. |
| `transaction.authorization.duration` | `transaction_authorization_duration_seconds_count`, `transaction_authorization_duration_seconds_sum` | `type=CREDIT|DEBIT|UNKNOWN`, `outcome=SUCCEEDED|FAILED|IDEMPOTENCY_CONFLICT|VALIDATION_ERROR|ERROR` | Latencia do fluxo de autorizacao observado. |
| `account.opening.processing.duration` | `account_opening_processing_duration_seconds_count`, `account_opening_processing_duration_seconds_sum` | `result=created|already_exists|invalid|error` | Latencia de processamento de cada mensagem SQS. |

IDs como `transactionId`, `accountId`, `messageId`, `ownerId` e UUIDs nao sao usados como tags para evitar alta cardinalidade. Quando uteis, aparecem apenas nos logs key-value.

## Como observar SQS

Depois de subir a aplicacao e popular a fila local:

```bash
docker compose --profile seed up message-generator
```

Consulte o Prometheus:

```bash
curl -s http://localhost:8080/actuator/prometheus | grep -E 'account_opening_messages|sqs_poll_messages_received|account_opening_processing_duration'
```

Indicadores praticos:

- `sqs_poll_messages_received_total`: o poller recebeu mensagens da fila.
- `account_opening_messages_total{result="created"}`: contas novas foram persistidas.
- `account_opening_messages_total{result="already_exists"}`: mensagens foram reprocessadas de forma idempotente.
- `account_opening_messages_total{result="invalid"}`: payloads invalidos foram encontrados e nao deletados.
- `account_opening_messages_total{result="error"}`: houve erro inesperado no processamento.
- `account_opening_processing_duration_seconds_count`: quantidade de mensagens com tempo registrado.
- `account_opening_processing_duration_seconds_sum`: soma das duracoes em segundos.

Exemplo de media aproximada de processamento SQS em Prometheus:

```promql
rate(account_opening_processing_duration_seconds_sum[5m])
/
rate(account_opening_processing_duration_seconds_count[5m])
```

## Como observar autorizacoes

Gere algumas transacoes pela API ou pelo script local e consulte:

```bash
curl -s http://localhost:8080/actuator/prometheus | grep -E 'transaction_authorizations|transaction_authorization_failures|transaction_authorization_duration'
```

Indicadores praticos:

- `transaction_authorizations_total{type="CREDIT",status="SUCCEEDED"}`: creditos aprovados.
- `transaction_authorizations_total{type="DEBIT",status="SUCCEEDED"}`: debitos aprovados.
- `transaction_authorizations_total{type="DEBIT",status="FAILED"}`: debitos ou autorizacoes recusadas funcionalmente.
- `transaction_authorization_failures_total{reason="INSUFFICIENT_FUNDS"}`: debitos recusados por saldo insuficiente.
- `transaction_authorization_failures_total{reason="ACCOUNT_NOT_FOUND"}`: autorizacoes para conta inexistente.
- `transaction_authorization_failures_total{reason="ACCOUNT_DISABLED"}`: autorizacoes para conta desabilitada.
- `transaction_authorization_failures_total{reason="IDEMPOTENCY_CONFLICT"}`: mesmo `transactionId` usado com payload diferente.
- `transaction_authorization_failures_total{reason="VALIDATION_ERROR"}`: payload, UUID, moeda, tipo ou valor invalidos.

Exemplos Prometheus:

```promql
rate(transaction_authorizations_total[5m])
rate(transaction_authorization_failures_total[5m])
rate(transaction_authorization_duration_seconds_sum[5m])
/
rate(transaction_authorization_duration_seconds_count[5m])
```

Observacao: `transaction.authorizations` mede chamadas processadas com resultado funcional, incluindo replay idempotente aceito. Conflitos de idempotencia e erros de validacao aparecem em `transaction.authorization.failures`.

Erros de validacao sao rejeitados antes da construcao do comando de autorizacao. Por isso, eles aparecem com `type="UNKNOWN"`; a serie `transaction_authorization_duration_seconds_count{outcome="VALIDATION_ERROR"}` funciona principalmente como marcador de ocorrencia desses eventos no handler.

## Smoke/load local leve

O script local executa uma carga simples com `curl`, alternando pequenos creditos e debitos para uma conta existente:

```bash
bash scripts/smoke-load-transactions.sh <accountId> [requests=100] [concurrency=8] [baseUrl=http://localhost:8080]
```

Exemplo:

```bash
bash scripts/smoke-load-transactions.sh 5b19c8b6-0cc4-4c72-a989-0c2ee15fa975 100 8 http://localhost:8080
```

O script mostra:

- total de requests planejados;
- concorrencia usada;
- tempo total aproximado;
- resumo simples dos HTTP status codes;
- comandos para consultar as metricas no Prometheus.

O script altera o saldo da conta local e nao deve ser executado contra ambientes compartilhados ou produtivos. Ele nao faz parte de `./gradlew clean test`.

## Logs

Os logs continuam em formato simples key-value. Eles podem incluir:

- autorizacao: `transactionId`, `accountId`, `type`, `status`, `failureReason`, `durationMs`;
- SQS: `messageId`, `accountId`, `result`, `queueName`, `durationMs`.

Payloads completos nao sao logados.

## Limitacoes

- As metricas em memoria reiniciam quando a aplicacao reinicia.
- O ambiente local nao representa capacidade real de producao.
- Nao ha DLQ/redrive real nesta fase.
- Nao foram adicionados endpoints de negocio, tabelas, migrations, autenticacao, consulta de saldo ou extrato.
