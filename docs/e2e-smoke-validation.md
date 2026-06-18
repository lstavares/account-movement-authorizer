# Validação E2E Smoke

Esta fase adiciona uma validação ponta a ponta executável para demonstrar que o fluxo principal funciona fora dos testes unitários e integrados. Ela usa Docker Compose, LocalStack/SQS, HTTP, PostgreSQL e métricas Prometheus expostas pela própria aplicação.

Ela não altera regra de negócio, contrato HTTP, schema, migrations, idempotência, locking, consumidor SQS ou dashboard Grafana.

## Onde a validação se encaixa

- `./gradlew clean test`: executa testes unitários e integrados automatizados, incluindo Testcontainers.
- `scripts/smoke-load-transactions.sh`: gera tráfego HTTP simples e concorrente contra uma conta existente; útil para alimentar métricas, mas não valida todos os cenários.
- `scripts/e2e-transaction-scenarios.sh`: valida uma trilha funcional fechada com SQS, API, banco e métricas, falhando com exit code diferente de zero quando algo diverge.
- Grafana dashboard: visualiza métricas depois que há tráfego, mas não é requisito do script E2E.

## Como rodar com stack já em execução

Suba a stack e popule a fila como no fluxo local normal. Depois rode:

```bash
bash scripts/e2e-transaction-scenarios.sh
```

O script assume `http://localhost:8080`, valida o app, PostgreSQL via container e o endpoint Prometheus da aplicação, pega uma conta `ENABLED` existente e executa os cenários.

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

Depois aguarda `/actuator/health`, aguarda pelo menos uma conta persistida e executa os cenários.

## Como rodar limpando tudo antes

```bash
bash scripts/e2e-transaction-scenarios.sh --start-stack --clean
```

Use este modo em CI ou quando quiser uma demonstração do zero. Ele executa `docker compose down -v --remove-orphans` antes de subir a stack, portanto remove dados locais do volume PostgreSQL.

## Cenários validados

O script cria `transactionId`s próprios e usa o PostgreSQL como fonte da verdade para saldo, status, `failure_reason`, `balance_before_amount`, `balance_after_amount` e contagem por ID.

Ele valida:

- `CREDIT 100.00 BRL` com HTTP `200`, resposta `SUCCEEDED`, transação `SUCCEEDED`, `amount_value = 10000` e saldo aumentado.
- `DEBIT 30.00 BRL` com HTTP `200`, resposta `SUCCEEDED`, transação `SUCCEEDED` e saldo reduzido.
- `DEBIT` maior que o saldo atual com HTTP `200`, resposta `FAILED`, `failure_reason = INSUFFICIENT_FUNDS` e saldo inalterado.
- `CREDIT 50.00 BRL` para conta inexistente com HTTP `200`, resposta `FAILED` e `failure_reason = ACCOUNT_NOT_FOUND`.
- Reenvio do primeiro `CREDIT` com payload idêntico, HTTP `200`, saldo inalterado e uma única transação com o ID.
- Reenvio do primeiro `CREDIT` com valor diferente, HTTP `409`, saldo inalterado e uma única transação com o ID.
- `currency = USD` com HTTP `400` e nenhuma transação persistida.
- `amount.value = 0` com HTTP `400` e nenhuma transação persistida.

Ao final, consulta `GET /actuator/prometheus` e valida a presença das séries:

- `transaction_authorizations_total`
- `transaction_authorization_failures_total`
- `transaction_authorization_duration_seconds_count`
- `account_opening_messages_total`
- `sqs_poll_messages_received_total`

## GitHub Actions manual

O workflow manual está em `.github/workflows/e2e-smoke.yml` e roda somente via `workflow_dispatch`.

Ele executa:

```bash
./gradlew clean test --no-daemon
docker compose config
bash scripts/e2e-transaction-scenarios.sh --start-stack --clean
```

Em caso de falha, imprime `docker compose ps -a` e logs de `app`, `postgres`, `localstack` e `message-generator`. Ao final, sempre executa `docker compose down --remove-orphans -v`.

O workflow não usa secrets, não publica imagem, não faz deploy, não exige Grafana e não expõe porta pública.

## Uso em apresentação técnica

Uma receita curta para demonstração:

```bash
bash scripts/e2e-transaction-scenarios.sh --start-stack --clean
docker compose --profile observability up -d prometheus grafana
```

Depois acesse:

- App/Swagger: `http://localhost:8080`
- Grafana: `http://localhost:3000`
- Prometheus: `http://localhost:9090`

O script imprime a conta usada, saldo inicial/final, cenários validados e séries de métricas encontradas. O Grafana pode ser aberto depois para visualizar o efeito do tráfego.

## GitHub Codespaces

Codespaces pode ser usado para demonstração temporária, mas o repositório não deve commitar URL fixa.

Use as URLs da aba `PORTS`, por exemplo:

- App/Swagger: `https://<codespace-name>-8080.app.github.dev`
- Grafana: `https://<codespace-name>-3000.app.github.dev`

Para o script E2E, rode de dentro do terminal do Codespace usando a URL local:

```bash
bash scripts/e2e-transaction-scenarios.sh --start-stack --clean --base-url http://localhost:8080
```

Se tornar portas públicas para demonstração, reverta depois.

## Limitações

- Não é benchmark e não mede capacidade real de produção.
- Altera a base local ao criar transações e atualizar saldo.
- `--clean` remove volumes locais do Docker Compose.
- Usa LocalStack, não AWS real.
- Depende de Docker e de rede para build/seed quando a imagem ainda não está cacheada.
- Não substitui testes formais de performance, carga ou resiliência.
