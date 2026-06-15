package br.com.leandrotavares.accountmovementauthorizer.application.transactionauthorization

import br.com.leandrotavares.accountmovementauthorizer.PostgreSqlIntegrationTest
import br.com.leandrotavares.accountmovementauthorizer.domain.AccountStatus
import br.com.leandrotavares.accountmovementauthorizer.domain.FailureReason
import br.com.leandrotavares.accountmovementauthorizer.domain.TransactionStatus
import br.com.leandrotavares.accountmovementauthorizer.domain.TransactionType
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.observability.OperationalMetrics
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.entity.AccountEntity
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.repository.AccountRepository
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.repository.TransactionRepository
import io.micrometer.core.instrument.MeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@ActiveProfiles("test")
@SpringBootTest
class AuthorizeTransactionServiceIntegrationTests : PostgreSqlIntegrationTest() {
    @Autowired
    private lateinit var authorizeTransactionService: AuthorizeTransactionService

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var meterRegistry: MeterRegistry

    @BeforeEach
    fun cleanDatabase() {
        transactionRepository.deleteAll()
        accountRepository.deleteAll()
    }

    @Test
    fun `deve aprovar credito em conta existente e aumentar saldo`() {
        val account = accountRepository.saveAndFlush(newAccount(balanceAmount = 100_00))
        val command = newCommand(
            accountId = account.id,
            type = TransactionType.CREDIT,
            amountValue = 97_07,
        )
        val authorizationsBefore = counterValue(
            OperationalMetrics.TRANSACTION_AUTHORIZATIONS,
            "type",
            "CREDIT",
            "status",
            "SUCCEEDED",
        )
        val durationBefore = timerCount(
            OperationalMetrics.TRANSACTION_AUTHORIZATION_DURATION,
            "type",
            "CREDIT",
            "outcome",
            "SUCCEEDED",
        )

        val result = authorizeTransactionService.authorize(command)

        val persistedAccount = accountRepository.findById(account.id).orElseThrow()
        val persistedTransaction = transactionRepository.findById(command.transactionId).orElseThrow()
        assertThat(result.status).isEqualTo(TransactionStatus.SUCCEEDED)
        assertThat(result.accountBalanceAmount).isEqualTo(197_07)
        assertThat(persistedAccount.balanceAmount).isEqualTo(197_07)
        assertThat(persistedTransaction.status).isEqualTo(TransactionStatus.SUCCEEDED)
        assertThat(persistedTransaction.balanceBeforeAmount).isEqualTo(100_00)
        assertThat(persistedTransaction.balanceAfterAmount).isEqualTo(197_07)
        assertThat(
            counterValue(
                OperationalMetrics.TRANSACTION_AUTHORIZATIONS,
                "type",
                "CREDIT",
                "status",
                "SUCCEEDED",
            ),
        ).isEqualTo(authorizationsBefore + 1)
        assertThat(
            timerCount(
                OperationalMetrics.TRANSACTION_AUTHORIZATION_DURATION,
                "type",
                "CREDIT",
                "outcome",
                "SUCCEEDED",
            ),
        ).isEqualTo(durationBefore + 1)
    }

    @Test
    fun `deve aprovar debito com saldo suficiente e reduzir saldo`() {
        val account = accountRepository.saveAndFlush(newAccount(balanceAmount = 100_00))
        val command = newCommand(
            accountId = account.id,
            type = TransactionType.DEBIT,
            amountValue = 25_00,
        )

        val result = authorizeTransactionService.authorize(command)

        val persistedAccount = accountRepository.findById(account.id).orElseThrow()
        val persistedTransaction = transactionRepository.findById(command.transactionId).orElseThrow()
        assertThat(result.status).isEqualTo(TransactionStatus.SUCCEEDED)
        assertThat(result.accountBalanceAmount).isEqualTo(75_00)
        assertThat(persistedAccount.balanceAmount).isEqualTo(75_00)
        assertThat(persistedTransaction.status).isEqualTo(TransactionStatus.SUCCEEDED)
        assertThat(persistedTransaction.balanceBeforeAmount).isEqualTo(100_00)
        assertThat(persistedTransaction.balanceAfterAmount).isEqualTo(75_00)
    }

    @Test
    fun `deve falhar debito com saldo insuficiente e nao alterar saldo`() {
        val account = accountRepository.saveAndFlush(newAccount(balanceAmount = 10_00))
        val command = newCommand(
            accountId = account.id,
            type = TransactionType.DEBIT,
            amountValue = 10_01,
        )
        val authorizationsBefore = counterValue(
            OperationalMetrics.TRANSACTION_AUTHORIZATIONS,
            "type",
            "DEBIT",
            "status",
            "FAILED",
        )
        val failuresBefore = counterValue(
            OperationalMetrics.TRANSACTION_AUTHORIZATION_FAILURES,
            "reason",
            "INSUFFICIENT_FUNDS",
        )
        val durationBefore = timerCount(
            OperationalMetrics.TRANSACTION_AUTHORIZATION_DURATION,
            "type",
            "DEBIT",
            "outcome",
            "FAILED",
        )

        val result = authorizeTransactionService.authorize(command)

        val persistedAccount = accountRepository.findById(account.id).orElseThrow()
        val persistedTransaction = transactionRepository.findById(command.transactionId).orElseThrow()
        assertThat(result.status).isEqualTo(TransactionStatus.FAILED)
        assertThat(result.failureReason).isEqualTo(FailureReason.INSUFFICIENT_FUNDS)
        assertThat(result.accountBalanceAmount).isEqualTo(10_00)
        assertThat(persistedAccount.balanceAmount).isEqualTo(10_00)
        assertThat(persistedTransaction.status).isEqualTo(TransactionStatus.FAILED)
        assertThat(persistedTransaction.failureReason).isEqualTo(FailureReason.INSUFFICIENT_FUNDS)
        assertThat(persistedTransaction.balanceBeforeAmount).isEqualTo(10_00)
        assertThat(persistedTransaction.balanceAfterAmount).isEqualTo(10_00)
        assertThat(
            counterValue(
                OperationalMetrics.TRANSACTION_AUTHORIZATIONS,
                "type",
                "DEBIT",
                "status",
                "FAILED",
            ),
        ).isEqualTo(authorizationsBefore + 1)
        assertThat(
            counterValue(
                OperationalMetrics.TRANSACTION_AUTHORIZATION_FAILURES,
                "reason",
                "INSUFFICIENT_FUNDS",
            ),
        ).isEqualTo(failuresBefore + 1)
        assertThat(
            timerCount(
                OperationalMetrics.TRANSACTION_AUTHORIZATION_DURATION,
                "type",
                "DEBIT",
                "outcome",
                "FAILED",
            ),
        ).isEqualTo(durationBefore + 1)
    }

    @Test
    fun `deve gravar falha quando conta nao existe`() {
        val accountId = UUID.randomUUID()
        val command = newCommand(accountId = accountId)

        val result = authorizeTransactionService.authorize(command)

        val persistedTransaction = transactionRepository.findById(command.transactionId).orElseThrow()
        assertThat(result.status).isEqualTo(TransactionStatus.FAILED)
        assertThat(result.failureReason).isEqualTo(FailureReason.ACCOUNT_NOT_FOUND)
        assertThat(result.accountBalanceAmount).isZero()
        assertThat(accountRepository.findById(accountId)).isEmpty
        assertThat(persistedTransaction.status).isEqualTo(TransactionStatus.FAILED)
        assertThat(persistedTransaction.failureReason).isEqualTo(FailureReason.ACCOUNT_NOT_FOUND)
        assertThat(persistedTransaction.balanceBeforeAmount).isNull()
        assertThat(persistedTransaction.balanceAfterAmount).isNull()
    }

    @Test
    fun `deve gravar falha quando conta esta desabilitada`() {
        val account = accountRepository.saveAndFlush(
            newAccount(status = AccountStatus.DISABLED, balanceAmount = 50_00),
        )
        val command = newCommand(accountId = account.id)

        val result = authorizeTransactionService.authorize(command)

        val persistedAccount = accountRepository.findById(account.id).orElseThrow()
        val persistedTransaction = transactionRepository.findById(command.transactionId).orElseThrow()
        assertThat(result.status).isEqualTo(TransactionStatus.FAILED)
        assertThat(result.failureReason).isEqualTo(FailureReason.ACCOUNT_DISABLED)
        assertThat(result.accountBalanceAmount).isEqualTo(50_00)
        assertThat(persistedAccount.balanceAmount).isEqualTo(50_00)
        assertThat(persistedTransaction.status).isEqualTo(TransactionStatus.FAILED)
        assertThat(persistedTransaction.failureReason).isEqualTo(FailureReason.ACCOUNT_DISABLED)
        assertThat(persistedTransaction.balanceBeforeAmount).isEqualTo(50_00)
        assertThat(persistedTransaction.balanceAfterAmount).isEqualTo(50_00)
    }

    @Test
    fun `deve retornar transacao existente quando transaction id for reenviado com payload igual`() {
        val account = accountRepository.saveAndFlush(newAccount(balanceAmount = 100_00))
        val command = newCommand(
            accountId = account.id,
            type = TransactionType.CREDIT,
            amountValue = 25_00,
        )

        val firstResult = authorizeTransactionService.authorize(command)
        val secondResult = authorizeTransactionService.authorize(command)

        val persistedAccount = accountRepository.findById(account.id).orElseThrow()
        assertThat(firstResult.status).isEqualTo(TransactionStatus.SUCCEEDED)
        assertThat(secondResult.status).isEqualTo(TransactionStatus.SUCCEEDED)
        assertThat(secondResult.timestamp.toInstant()).isEqualTo(firstResult.timestamp.toInstant())
        assertThat(persistedAccount.balanceAmount).isEqualTo(125_00)
        assertThat(transactionRepository.count()).isEqualTo(1)
    }

    @Test
    fun `deve rejeitar mesmo transaction id com payload diferente`() {
        val account = accountRepository.saveAndFlush(newAccount(balanceAmount = 100_00))
        val transactionId = UUID.randomUUID()
        val firstCommand = newCommand(
            transactionId = transactionId,
            accountId = account.id,
            type = TransactionType.CREDIT,
            amountValue = 25_00,
        )
        val conflictingCommand = newCommand(
            transactionId = transactionId,
            accountId = account.id,
            type = TransactionType.DEBIT,
            amountValue = 25_00,
        )

        authorizeTransactionService.authorize(firstCommand)

        assertThatThrownBy { authorizeTransactionService.authorize(conflictingCommand) }
            .isInstanceOf(IdempotencyConflictException::class.java)
        assertThat(accountRepository.findById(account.id).orElseThrow().balanceAmount).isEqualTo(125_00)
        assertThat(transactionRepository.count()).isEqualTo(1)
    }

    @Test
    fun `dois debitos concorrentes na mesma conta nao devem deixar saldo inconsistente`() {
        val account = accountRepository.saveAndFlush(newAccount(balanceAmount = 100_00))
        val commands = listOf(
            newCommand(accountId = account.id, type = TransactionType.DEBIT, amountValue = 70_00),
            newCommand(accountId = account.id, type = TransactionType.DEBIT, amountValue = 70_00),
        )

        val results = runConcurrently(commands)

        val persistedAccount = accountRepository.findById(account.id).orElseThrow()
        assertThat(results.map { it.status }).containsExactlyInAnyOrder(
            TransactionStatus.SUCCEEDED,
            TransactionStatus.FAILED,
        )
        assertThat(results.single { it.status == TransactionStatus.FAILED }.failureReason)
            .isEqualTo(FailureReason.INSUFFICIENT_FUNDS)
        assertThat(persistedAccount.balanceAmount).isEqualTo(30_00)
        assertThat(transactionRepository.count()).isEqualTo(2)
    }

    @Test
    fun `dois requests concorrentes com mesmo transaction id devem aplicar no maximo uma vez`() {
        val account = accountRepository.saveAndFlush(newAccount(balanceAmount = 100_00))
        val command = newCommand(
            accountId = account.id,
            type = TransactionType.CREDIT,
            amountValue = 25_00,
        )

        val results = runConcurrently(listOf(command, command))

        val persistedAccount = accountRepository.findById(account.id).orElseThrow()
        assertThat(results.map { it.status }).containsOnly(TransactionStatus.SUCCEEDED)
        assertThat(results.map { it.transactionId }).containsOnly(command.transactionId)
        assertThat(persistedAccount.balanceAmount).isEqualTo(125_00)
        assertThat(transactionRepository.count()).isEqualTo(1)
    }

    private fun runConcurrently(commands: List<AuthorizeTransactionCommand>): List<AuthorizeTransactionResult> {
        val executor = Executors.newFixedThreadPool(commands.size)
        val start = CountDownLatch(1)

        return try {
            val futures = commands.map { command ->
                executor.submit<AuthorizeTransactionResult> {
                    start.await()
                    authorizeTransactionService.authorize(command)
                }
            }

            start.countDown()
            futures.map { it.get(10, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun newCommand(
        transactionId: UUID = UUID.randomUUID(),
        accountId: UUID = UUID.randomUUID(),
        type: TransactionType = TransactionType.CREDIT,
        amountValue: Long = 10_00,
        amountCurrency: String = "BRL",
    ): AuthorizeTransactionCommand =
        AuthorizeTransactionCommand(
            transactionId = transactionId,
            accountId = accountId,
            type = type,
            amountValue = amountValue,
            amountCurrency = amountCurrency,
        )

    private fun newAccount(
        id: UUID = UUID.randomUUID(),
        status: AccountStatus = AccountStatus.ENABLED,
        balanceAmount: Long = 0,
    ): AccountEntity =
        AccountEntity(
            id = id,
            ownerId = UUID.randomUUID(),
            status = status,
            balanceAmount = balanceAmount,
            balanceCurrency = "BRL",
            openedAt = OffsetDateTime.now().minusDays(1),
            receivedAt = OffsetDateTime.now(),
            createdAt = OffsetDateTime.now(),
            updatedAt = OffsetDateTime.now(),
        )

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
