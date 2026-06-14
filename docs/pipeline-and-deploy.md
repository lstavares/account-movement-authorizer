# Pipeline e deploy

## CI atual

O workflow `.github/workflows/ci.yml` executa em pull requests e pushes para `main` ou `master`.

Etapas:

1. Checkout do codigo.
2. Setup do Java 21.
3. Cache Gradle via `actions/setup-java`.
4. Execucao de `./gradlew clean test --no-daemon`.

O workflow nao usa secrets, nao publica imagem e nao faz deploy real.

## Pipeline alvo

Uma esteira de producao poderia seguir:

1. Build e testes unitarios/integracao.
2. Analise estatica basica e checagem de vulnerabilidades de dependencias.
3. Build da imagem Docker versionada com SHA/tag semantica.
4. Scan da imagem.
5. Push para registry privado.
6. Deploy automatico em staging.
7. Smoke tests contra `/actuator/health`, `/v3/api-docs` e fluxo de autorizacao controlado.
8. Promocao para producao com canary ou blue/green.
9. Monitoramento de erro, latencia, saturacao e metricas de negocio.
10. Rollback para a imagem anterior em caso de degradacao.

## Deploy conceitual

- Entrada por API Gateway ou Load Balancer.
- Aplicacao containerizada em ECS, EKS, App Runner ou servico equivalente.
- Banco em PostgreSQL gerenciado, como RDS.
- SQS gerenciado para eventos de abertura de conta.
- Configuracoes por variaveis de ambiente e segredos em Secrets Manager ou Parameter Store.
- Logs e metricas centralizados em CloudWatch ou plataforma equivalente.

## Rollback

O rollback deve reutilizar a imagem previamente aprovada e manter compatibilidade de schema. Como esta fase nao cria migrations, o rollback operacional e simples: trocar a task/release ativa para a versao anterior.
