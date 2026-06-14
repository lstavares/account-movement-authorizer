package br.com.leandrotavares.accountmovementauthorizer.infrastructure.sqs

import br.com.leandrotavares.accountmovementauthorizer.application.accountopening.RegisterOpenedAccountResult
import br.com.leandrotavares.accountmovementauthorizer.application.accountopening.RegisterOpenedAccountService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import java.util.UUID

@Component
@ConditionalOnProperty(
    prefix = "app.sqs.account-opening",
    name = ["enabled"],
    havingValue = "true",
)
class AccountOpeningSqsPoller(
    private val sqsClient: SqsClient,
    private val properties: AccountOpeningSqsProperties,
    private val parser: AccountOpeningMessageParser,
    private val registerOpenedAccountService: RegisterOpenedAccountService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.sqs.account-opening.poll-delay-ms:1000}")
    fun pollScheduled() {
        pollOnce()
    }

    fun pollOnce() {
        val messages = try {
            sqsClient.receiveMessage(
                ReceiveMessageRequest.builder()
                    .queueUrl(properties.queueUrl)
                    .maxNumberOfMessages(properties.maxMessages)
                    .waitTimeSeconds(properties.waitTimeSeconds)
                    .visibilityTimeout(properties.visibilityTimeoutSeconds)
                    .build(),
            ).messages()
        } catch (ex: Exception) {
            logger.error(
                "Failed to receive account opening messages. queueName={} queueUrl={}",
                properties.queueName,
                properties.queueUrl,
                ex,
            )
            return
        }

        messages.forEach { message ->
            processMessage(message)
        }
    }

    private fun processMessage(message: Message) {
        val messageId = message.messageId() ?: "unknown"
        var accountId: UUID? = null

        try {
            val command = parser.parse(message.body().orEmpty())
            accountId = command.accountId

            val result = registerOpenedAccountService.register(command)
            logger.info(
                "Account opening message processed. messageId={} accountId={} result={} queueName={}",
                messageId,
                accountId,
                result,
                properties.queueName,
            )

            deleteMessage(message, accountId, result)
        } catch (ex: InvalidAccountOpeningMessageException) {
            logger.error(
                "Invalid account opening message. messageId={} queueName={}. Message will not be deleted.",
                messageId,
                properties.queueName,
                ex,
            )
        } catch (ex: Exception) {
            logger.error(
                "Failed to process account opening message. messageId={} accountId={} queueName={}. Message will not be deleted.",
                messageId,
                accountId,
                properties.queueName,
                ex,
            )
        }
    }

    private fun deleteMessage(
        message: Message,
        accountId: UUID,
        result: RegisterOpenedAccountResult,
    ) {
        val receiptHandle = message.receiptHandle()
        if (receiptHandle.isNullOrBlank()) {
            logger.error(
                "Cannot delete account opening message without receipt handle. messageId={} accountId={} result={} queueName={}",
                message.messageId(),
                accountId,
                result,
                properties.queueName,
            )
            return
        }

        try {
            sqsClient.deleteMessage(
                DeleteMessageRequest.builder()
                    .queueUrl(properties.queueUrl)
                    .receiptHandle(receiptHandle)
                    .build(),
            )
            logger.info(
                "Account opening message deleted. messageId={} accountId={} result={} queueName={}",
                message.messageId(),
                accountId,
                result,
                properties.queueName,
            )
        } catch (ex: Exception) {
            logger.error(
                "Failed to delete account opening message. messageId={} accountId={} result={} queueName={}",
                message.messageId(),
                accountId,
                result,
                properties.queueName,
                ex,
            )
        }
    }
}
