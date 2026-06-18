# Pipeline e deploy

## CI atual

O workflow `.github/workflows/ci.yml` executa em pull requests e pushes para `main` ou `master`.

Etapas:

1. Checkout do código.
2. Setup do Java 21.
3. Cache Gradle via `actions/setup-java`.
4. Execução de `./gradlew clean test --no-daemon`.

O workflow não usa secrets, não publica imagem e não faz deploy real.

## Pipeline alvo

Uma esteira de produção poderia seguir:

1. Build e testes unitários/integração.
2. Análise estática básica e checagem de vulnerabilidades de dependências.
3. Build da imagem Docker versionada com SHA/tag semântica.
4. Scan da imagem.
5. Push para registry privado.
6. Deploy automático em staging.
7. Smoke tests contra `/actuator/health`, `/v3/api-docs` e fluxo de autorização controlado.
8. Promoção para produção com canary ou blue/green.
9. Monitoramento de erro, latência, saturação e métricas de negócio.
10. Rollback para a imagem anterior em caso de degradação.

## Deploy conceitual

- Entrada por API Gateway ou Load Balancer.
- Aplicação containerizada em ECS, EKS, App Runner ou serviço equivalente.
- Banco em PostgreSQL gerenciado, como RDS.
- SQS gerenciado para eventos de abertura de conta.
- Configurações por variáveis de ambiente e segredos em Secrets Manager ou Parameter Store.
- Logs e métricas centralizados em CloudWatch ou plataforma equivalente.

## Rollback

O rollback deve reutilizar a imagem previamente aprovada e manter compatibilidade de schema. Como esta fase não cria migrations, o rollback operacional é simples: trocar a task/release ativa para a versão anterior.
