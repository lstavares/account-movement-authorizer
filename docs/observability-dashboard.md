# Observability Dashboard

Esta fase opcional adiciona uma stack local/demo com Prometheus e Grafana para visualizar metricas operacionais da aplicacao. Ela nao adiciona funcionalidade bancaria, nao muda o contrato HTTP, nao altera regras de autorizacao, idempotencia, locking, migrations ou tabelas.

O dashboard pode ficar vazio antes de gerar trafego. Popule a fila SQS e rode o script de smoke/load para alimentar os paineis.

## Servicos

A stack de observabilidade fica atras do profile opcional `observability` do Docker Compose:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

O fluxo local basico continua funcionando sem esse profile:

```bash
docker compose up -d postgres localstack app
```

## Receita de demonstracao

Suba a aplicacao e a infraestrutura local:

```bash
docker compose up -d postgres localstack app
```

Suba Prometheus e Grafana:

```bash
docker compose --profile observability up -d prometheus grafana
```

Popule a fila SQS local:

```bash
docker compose --profile seed up message-generator
```

Pegue uma conta persistida:

```bash
ACCOUNT_ID=$(docker compose exec -T postgres psql -U account_authorizer -d account_authorizer -Atc "select id from accounts limit 1;")
echo "$ACCOUNT_ID"
```

Rode o smoke/load local leve:

```bash
bash scripts/smoke-load-transactions.sh "$ACCOUNT_ID" 100 8 http://localhost:8080
```

Acesse as ferramentas locais:

- App/Swagger: `http://localhost:8080`
- Grafana: `http://localhost:3000`
- Prometheus: `http://localhost:9090`

Login local/demo do Grafana:

- Usuario: `admin`
- Senha: `admin`

Essas credenciais sao apenas para demonstracao local. Elas nao sao adequadas para producao ou ambientes compartilhados.

O dashboard provisionado e:

- `Account Movement Authorizer - Operational Overview`

## O que o dashboard mostra

O dashboard usa metricas de baixa cardinalidade ja expostas pela aplicacao:

- autorizacoes por segundo por `type` e `status`
- total de autorizacoes por `status`
- falhas de autorizacao por `reason`
- latencia media de autorizacao
- autorizacoes processadas no periodo selecionado
- mensagens SQS de abertura de conta processadas por `result`
- mensagens SQS recebidas pelo poller
- latencia media de processamento SQS
- taxa de requests HTTP por status, quando a metrica padrao do Spring estiver disponivel
- memoria usada pela JVM
- uptime da aplicacao

O dashboard nao usa labels como `transactionId`, `accountId`, `ownerId`, `messageId` ou UUIDs.

## Health checks

Verifique o endpoint Prometheus da aplicacao:

```bash
curl -i http://localhost:8080/actuator/prometheus
```

Verifique o Prometheus:

```bash
curl -i http://localhost:9090/-/ready
curl -s http://localhost:9090/api/v1/targets
```

Verifique o Grafana:

```bash
curl -i http://localhost:3000/api/health
curl -s -u admin:admin http://localhost:3000/api/datasources
curl -s -u admin:admin "http://localhost:3000/api/search?query=Account%20Movement"
```

## GitHub Codespaces

Codespaces pode ser usado para uma demonstracao temporaria, mas o repositorio nao deve commitar uma URL concreta de Codespaces. Use as URLs encaminhadas exibidas na aba `PORTS` do Codespaces.

Placeholders tipicos:

- App/Swagger: `https://<codespace-name>-8080.app.github.dev`
- Grafana: `https://<codespace-name>-3000.app.github.dev`
- Prometheus: `https://<codespace-name>-9090.app.github.dev`

Se uma porta estiver privada, torne-a publica apenas para uma demonstracao temporaria e reverta depois. O fluxo local padrao nao depende de Codespaces.
