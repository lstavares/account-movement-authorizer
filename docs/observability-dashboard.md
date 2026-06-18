# Dashboard de observabilidade

Esta fase opcional adiciona uma stack local/demo com Prometheus e Grafana para visualizar métricas operacionais da aplicação. Ela não adiciona funcionalidade bancária, não muda o contrato HTTP, não altera regras de autorização, idempotência, locking, migrations ou tabelas.

O dashboard pode ficar vazio antes de gerar tráfego. Popule a fila SQS e rode o script de smoke/load para alimentar os painéis.

## Serviços

A stack de observabilidade fica atrás do profile opcional `observability` do Docker Compose:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

O fluxo local básico continua funcionando sem esse profile:

```bash
docker compose up -d postgres localstack app
```

## Receita de demonstração

Suba a aplicação e a infraestrutura local:

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

- Usuário: `admin`
- Senha: `admin`

Essas credenciais são apenas para demonstração local. Elas não são adequadas para produção ou ambientes compartilhados.

O dashboard provisionado é:

- `Account Movement Authorizer - Operational Overview`

## O que o dashboard mostra

O dashboard usa métricas de baixa cardinalidade já expostas pela aplicação:

- autorizações por segundo por `type` e `status`
- total de autorizações por `status`
- falhas de autorização por `reason`
- latência média de autorização
- autorizações processadas no período selecionado
- mensagens SQS de abertura de conta processadas por `result`
- mensagens SQS recebidas pelo poller
- latência média de processamento SQS
- taxa de requests HTTP por status, quando a métrica padrão do Spring estiver disponível
- memória usada pela JVM
- uptime da aplicação

O dashboard não usa labels como `transactionId`, `accountId`, `ownerId`, `messageId` ou UUIDs.

## Health checks

Verifique o endpoint Prometheus da aplicação:

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

Codespaces pode ser usado para uma demonstração temporária, mas o repositório não deve commitar uma URL concreta de Codespaces. Use as URLs encaminhadas exibidas na aba `PORTS` do Codespaces.

Placeholders típicos:

- App/Swagger: `https://<codespace-name>-8080.app.github.dev`
- Grafana: `https://<codespace-name>-3000.app.github.dev`
- Prometheus: `https://<codespace-name>-9090.app.github.dev`

Se uma porta estiver privada, torne-a pública apenas para uma demonstração temporária e reverta depois. O fluxo local padrão não depende de Codespaces.
