# E2E Smoke Validation

Esta fase adiciona uma validacao ponta a ponta executavel para demonstrar que o fluxo principal funciona fora dos testes unitarios e integrados. Ela usa Docker Compose, LocalStack/SQS, HTTP, PostgreSQL e metricas Prometheus expostas pela propria aplicacao.

Ela nao altera regra de negocio, contrato HTTP, schema, migrations, idempotencia, locking, consumidor SQS ou dashboard Grafana.

## Onde esta validacao se encaixa

- `./gradlew clean test`: executa testes unitarios e integrados automatizados, incluindo Testcontainers.
- `scripts/smoke-load-transactions.sh`: gera trafego HTTP simples e concorrente contra uma conta existente; util para alimentar metricas, mas nao valida todos os cenarios.
- `scripts/e2e-transaction-scenarios.sh`: valida uma trilha funcional fechada com SQS, API, banco e metricas, falhando com exit code diferente de zero quando algo diverge.
- Grafana dashboard: visualiza metricas depois que ha trafego, mas nao e requisito do script E2E.

## Como rodar com stack ja em execucao

Suba a stack e popule a fila como no fluxo local normal. Depois rode:

```bash
bash scripts/e2e-transaction-scenarios.sh
```

O script assume `http://localhost:8080`, valida o app, PostgreSQL via container e o endpoint Prometheus da aplicacao, pega uma conta `ENABLED` existente e executa os cenarios.

Para usar outra URL:

```bash
bash scripts/e2e-transaction-scenarios.sh --base-url http://localhost:8080
```

## Como rodar subindo a stack

```bash
bash scripts/e2e-transaction-scenarios.sh --start-stack
```

Esse modo executa:

- `docker compose build app`
- `docker compose up -d postgres localstack app`
- `docker compose --profile seed run --rm -e TOTAL_ACCOUNTS=25 message-generator`

Depois aguarda `/actuator/health`, aguarda pelo menos uma conta persistida e executa os cenarios.

## Como rodar limpando tudo antes

```bash
bash scripts/e2e-transaction-scenarios.sh --start-stack --clean
```

Use este modo em CI ou quando quiser uma demonstracao do zero. Ele executa `docker compose down -v --remove-orphans` antes de subir a stack, portanto remove dados locais do volume PostgreSQL.

## Cenarios validados

O script cria `transactionId`s proprios e usa o PostgreSQL como fonte da verdade para saldo, status, `failure_reason`, `balance_before_amount`, `balance_after_amount` e contagem por ID.

Ele valida:

- `CREDIT 100.00 BRL` com HTTP `200`, resposta `SUCCEEDED`, transacao `SUCCEEDED`, `amount_value = 10000` e saldo aumentado.
- `DEBIT 30.00 BRL` com HTTP `200`, resposta `SUCCEEDED`, transacao `SUCCEEDED` e saldo reduzido.
- `DEBIT` maior que o saldo atual com HTTP `200`, resposta `FAILED`, `failure_reason = INSUFFICIENT_FUNDS` e saldo inalterado.
- `CREDIT 50.00 BRL` para conta inexistente com HTTP `200`, resposta `FAILED` e `failure_reason = ACCOUNT_NOT_FOUND`.
- Reenvio do primeiro `CREDIT` com payload identico, HTTP `200`, saldo inalterado e uma unica transacao com o ID.
- Reenvio do primeiro `CREDIT` com valor diferente, HTTP `409`, saldo inalterado e uma unica transacao com o ID.
- `currency = USD` com HTTP `400` e nenhuma transacao persistida.
- `amount.value = 0` com HTTP `400` e nenhuma transacao persistida.

Ao final, consulta `GET /actuator/prometheus` e valida a presenca das series:

- `transaction_authorizations_total`
- `transaction_authorization_failures_total`
- `transaction_authorization_duration_seconds_count`
- `account_opening_messages_total`
- `sqs_poll_messages_received_total`

## GitHub Actions manual

O workflow manual esta em `.github/workflows/e2e-smoke.yml` e roda somente via `workflow_dispatch`.

Ele executa:

```bash
./gradlew clean test --no-daemon
docker compose config
bash scripts/e2e-transaction-scenarios.sh --start-stack --clean
```

Em caso de falha, imprime `docker compose ps -a` e logs de `app`, `postgres`, `localstack` e `message-generator`. Ao final, sempre executa `docker compose down --remove-orphans -v`.

O workflow nao usa secrets, nao publica imagem, nao faz deploy, nao exige Grafana e nao expoe porta publica.

## Uso em apresentacao tecnica

Uma receita curta para demonstracao:

```bash
bash scripts/e2e-transaction-scenarios.sh --start-stack --clean
docker compose --profile observability up -d prometheus grafana
```

Depois acesse:

- App/Swagger: `http://localhost:8080`
- Grafana: `http://localhost:3000`
- Prometheus: `http://localhost:9090`

O script imprime a conta usada, saldo inicial/final, cenarios validados e series de metricas encontradas. O Grafana pode ser aberto depois para visualizar o efeito do trafego.

## GitHub Codespaces

Codespaces pode ser usado para demonstracao temporaria, mas o repositorio nao deve commitar URL fixa.

Use as URLs da aba `PORTS`, por exemplo:

- App/Swagger: `https://<codespace-name>-8080.app.github.dev`
- Grafana: `https://<codespace-name>-3000.app.github.dev`

Para o script E2E, rode de dentro do terminal do Codespace usando a URL local:

```bash
bash scripts/e2e-transaction-scenarios.sh --start-stack --clean --base-url http://localhost:8080
```

Se tornar portas publicas para demonstracao, reverta depois.

## Limitacoes

- Nao e benchmark e nao mede capacidade real de producao.
- Altera a base local ao criar transacoes e atualizar saldo.
- `--clean` remove volumes locais do Docker Compose.
- Usa LocalStack, nao AWS real.
- Depende de Docker e de rede para build/seed quando a imagem ainda nao esta cacheada.
- Nao substitui testes formais de performance, carga ou resiliencia.
