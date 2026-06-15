package br.com.leandrotavares.accountmovementauthorizer.infrastructure.sqs

import br.com.leandrotavares.accountmovementauthorizer.application.accountopening.RegisterOpenedAccountCommand
import br.com.leandrotavares.accountmovementauthorizer.application.accountopening.RegisterOpenedAccountResult
import br.com.leandrotavares.accountmovementauthorizer.application.accountopening.RegisterOpenedAccountService
import br.com.leandrotavares.accountmovementauthorizer.domain.AccountStatus
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.observability.OperationalMetrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class AccountOpeningSqsPollerTests {
    private val sqsClient = mockk<SqsClient>()
    private val parser = mockk<AccountOpeningMessageParser>()
    private val registerOpenedAccountService = mockk<RegisterOpenedAccountService>()
    private val meterRegistry = SimpleMeterRegistry()
    private val operationalMetrics = OperationalMetrics(meterRegistry)
    private val properties = AccountOpeningSqsProperties(
        enabled = true,
        queueUrl = "http://localhost:4566/000000000000/conta-bancaria-criada",
        waitTimeSeconds = 1,
        visibilityTimeoutSeconds = 30,
    )
    private val poller = AccountOpeningSqsPoller(
        sqsClient = sqsClient,
        properties = properties,
        parser = parser,
        registerOpenedAccountService = registerOpenedAccountService,
        operationalMetrics = operationalMetrics,
    )

    @Test
    fun `deve deletar mensagem apos cadastro com sucesso`() {
        val message = newMessage(body = "body-1", receiptHandle = "receipt-1")
        val command = newCommand()
        val receivedBefore = counterValue(OperationalMetrics.SQS_POLL_MESSAGES_RECEIVED)
        val createdBefore = counterValue(OperationalMetrics.ACCOUNT_OPENING_MESSAGES, "result", "created")
        val durationBefore = timerCount(OperationalMetrics.ACCOUNT_OPENING_PROCESSING_DURATION, "result", "created")

        stubReceive(message)
        every { parser.parse("body-1") } returns command
        every { registerOpenedAccountService.register(command) } returns RegisterOpenedAccountResult.CREATED
        every { sqsClient.deleteMessage(any<DeleteMessageRequest>()) } returns DeleteMessageResponse.builder().build()

        poller.pollOnce()

        verify(exactly = 1) {
            sqsClient.deleteMessage(
                match<DeleteMessageRequest> {
                    it.queueUrl() == properties.queueUrl && it.receiptHandle() == "receipt-1"
                },
            )
        }
        assertThat(counterValue(OperationalMetrics.SQS_POLL_MESSAGES_RECEIVED)).isEqualTo(receivedBefore + 1)
        assertThat(counterValue(OperationalMetrics.ACCOUNT_OPENING_MESSAGES, "result", "created"))
            .isEqualTo(createdBefore + 1)
        assertThat(timerCount(OperationalMetrics.ACCOUNT_OPENING_PROCESSING_DURATION, "result", "created"))
            .isEqualTo(durationBefore + 1)
    }

    @Test
    fun `deve deletar mensagem apos duplicidade idempotente`() {
        val message = newMessage(body = "body-1", receiptHandle = "receipt-1")
        val command = newCommand()
        val alreadyExistsBefore = counterValue(
            OperationalMetrics.ACCOUNT_OPENING_MESSAGES,
            "result",
            "already_exists",
        )

        stubReceive(message)
        every { parser.parse("body-1") } returns command
        every { registerOpenedAccountService.register(command) } returns RegisterOpenedAccountResult.ALREADY_EXISTS
        every { sqsClient.deleteMessage(any<DeleteMessageRequest>()) } returns DeleteMessageResponse.builder().build()

        poller.pollOnce()

        verify(exactly = 1) {
            sqsClient.deleteMessage(
                match<DeleteMessageRequest> {
                    it.queueUrl() == properties.queueUrl && it.receiptHandle() == "receipt-1"
                },
            )
        }
        assertThat(
            counterValue(
                OperationalMetrics.ACCOUNT_OPENING_MESSAGES,
                "result",
                "already_exists",
            ),
        ).isEqualTo(alreadyExistsBefore + 1)
    }

    @Test
    fun `nao deve deletar mensagem quando servico falhar`() {
        val message = newMessage(body = "body-1", receiptHandle = "receipt-1")
        val command = newCommand()
        val errorBefore = counterValue(OperationalMetrics.ACCOUNT_OPENING_MESSAGES, "result", "error")

        stubReceive(message)
        every { parser.parse("body-1") } returns command
        every { registerOpenedAccountService.register(command) } throws RuntimeException("database unavailable")

        poller.pollOnce()

        verify(exactly = 0) { sqsClient.deleteMessage(any<DeleteMessageRequest>()) }
        assertThat(counterValue(OperationalMetrics.ACCOUNT_OPENING_MESSAGES, "result", "error"))
            .isEqualTo(errorBefore + 1)
    }

    @Test
    fun `nao deve deletar mensagem com payload invalido`() {
        val message = newMessage(body = "bad-body", receiptHandle = "receipt-1")
        val invalidBefore = counterValue(OperationalMetrics.ACCOUNT_OPENING_MESSAGES, "result", "invalid")

        stubReceive(message)
        every { parser.parse("bad-body") } throws InvalidAccountOpeningMessageException(
            "invalid payload",
            RuntimeException("invalid payload"),
        )

        poller.pollOnce()

        verify(exactly = 0) { sqsClient.deleteMessage(any<DeleteMessageRequest>()) }
        assertThat(counterValue(OperationalMetrics.ACCOUNT_OPENING_MESSAGES, "result", "invalid"))
            .isEqualTo(invalidBefore + 1)
    }

    @Test
    fun `deve continuar processando demais mensagens do batch apos falha individual`() {
        val failedMessage = newMessage(messageId = "message-1", body = "body-1", receiptHandle = "receipt-1")
        val successfulMessage = newMessage(messageId = "message-2", body = "body-2", receiptHandle = "receipt-2")
        val failedCommand = newCommand()
        val successfulCommand = newCommand()

        stubReceive(failedMessage, successfulMessage)
        every { parser.parse("body-1") } returns failedCommand
        every { parser.parse("body-2") } returns successfulCommand
        every { registerOpenedAccountService.register(failedCommand) } throws RuntimeException("database unavailable")
        every { registerOpenedAccountService.register(successfulCommand) } returns RegisterOpenedAccountResult.CREATED
        every { sqsClient.deleteMessage(any<DeleteMessageRequest>()) } returns DeleteMessageResponse.builder().build()

        poller.pollOnce()

        verify(exactly = 0) {
            sqsClient.deleteMessage(
                match<DeleteMessageRequest> {
                    it.receiptHandle() == "receipt-1"
                },
            )
        }
        verify(exactly = 1) {
            sqsClient.deleteMessage(
                match<DeleteMessageRequest> {
                    it.receiptHandle() == "receipt-2"
                },
            )
        }
    }

    private fun stubReceive(vararg messages: Message) {
        every { sqsClient.receiveMessage(any<ReceiveMessageRequest>()) } returns
            ReceiveMessageResponse.builder()
                .messages(messages.toList())
                .build()
    }

    private fun newCommand(): RegisterOpenedAccountCommand =
        RegisterOpenedAccountCommand(
            accountId = UUID.randomUUID(),
            ownerId = UUID.randomUUID(),
            status = AccountStatus.ENABLED,
            openedAt = OffsetDateTime.now(ZoneOffset.UTC),
        )

    private fun newMessage(
        messageId: String = "message-1",
        body: String,
        receiptHandle: String,
    ): Message =
        Message.builder()
            .messageId(messageId)
            .body(body)
            .receiptHandle(receiptHandle)
            .build()

    private fun counterValue(
        name: String,
        vararg tags: String,
    ): Double =
        meterRegistry.find(name).tags(*tags).counter()?.count() ?: 0.0

    private fun timerCount(
        name: String,
        vararg tags: String,
    ): Long =
        meterRegistry.find(name).tags(*tags).timer()?.count() ?: 0
}
