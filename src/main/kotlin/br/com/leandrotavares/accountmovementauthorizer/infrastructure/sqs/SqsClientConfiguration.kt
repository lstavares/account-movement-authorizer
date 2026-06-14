package br.com.leandrotavares.accountmovementauthorizer.infrastructure.sqs

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsClient
import java.net.URI

@Configuration
@EnableConfigurationProperties(AccountOpeningSqsProperties::class)
class SqsClientConfiguration {
    @Bean
    @ConditionalOnProperty(
        prefix = "app.sqs.account-opening",
        name = ["enabled"],
        havingValue = "true",
    )
    fun accountOpeningSqsClient(properties: AccountOpeningSqsProperties): SqsClient =
        SqsClient.builder()
            .endpointOverride(URI.create(properties.endpoint))
            .region(Region.of(properties.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(
                        properties.accessKeyId,
                        properties.secretAccessKey,
                    ),
                ),
            )
            .build()
}
