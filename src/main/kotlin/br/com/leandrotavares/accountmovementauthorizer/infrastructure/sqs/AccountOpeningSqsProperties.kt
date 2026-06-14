package br.com.leandrotavares.accountmovementauthorizer.infrastructure.sqs

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.sqs.account-opening")
data class AccountOpeningSqsProperties(
    var enabled: Boolean = false,
    var endpoint: String = "http://localhost:4566",
    var region: String = "sa-east-1",
    var queueName: String = "conta-bancaria-criada",
    var queueUrl: String = "http://localhost:4566/000000000000/conta-bancaria-criada",
    var accessKeyId: String = "test",
    var secretAccessKey: String = "test",
    var maxMessages: Int = 10,
    var waitTimeSeconds: Int = 10,
    var visibilityTimeoutSeconds: Int = 30,
    var pollDelayMs: Long = 1_000,
)
