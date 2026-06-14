package br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence

import br.com.leandrotavares.accountmovementauthorizer.PostgreSqlIntegrationTest
import br.com.leandrotavares.accountmovementauthorizer.domain.AccountStatus
import br.com.leandrotavares.accountmovementauthorizer.domain.FailureReason
import br.com.leandrotavares.accountmovementauthorizer.domain.TransactionStatus
import br.com.leandrotavares.accountmovementauthorizer.domain.TransactionType
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.entity.AccountEntity
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.entity.TransactionEntity
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.repository.AccountRepository
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.repository.TransactionRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@ActiveProfiles("test")
@SpringBootTest
class PersistenceIntegrationTests : PostgreSqlIntegrationTest() {
    @Autowired
    private lateinit var accountRepository: AccountRepository

    @Autowired
    private lateinit var transactionRepository: TransactionRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanDatabase() {
        transactionRepository.deleteAll()
        accountRepository.deleteAll()
    }

    @Test
    fun `deve persistir uma conta com saldo inicial zero`() {
        val account = accountRepository.saveAndFlush(newAccount())

        val persistedAccount = accountRepository.findById(account.id)

        assertThat(persistedAccount).isPresent
        assertThat(persistedAccount.get().balanceAmount).isZero()
        assertThat(persistedAccount.get().balanceCurrency).isEqualTo("BRL")
        assertThat(persistedAccount.get().status).isEqualTo(AccountStatus.ENABLED)
    }

    @Test
    fun `deve persistir uma transacao de credito com status succeeded`() {
        val account = accountRepository.saveAndFlush(newAccount())

        val transaction = transactionRepository.saveAndFlush(
            newTransaction(
                accountId = account.id,
                type = TransactionType.CREDIT,
                amountValue = 12_50,
                status = TransactionStatus.SUCCEEDED,
                balanceBeforeAmount = 0,
                balanceAfterAmount = 12_50,
            ),
        )

        val persistedTransaction = transactionRepository.findById(transaction.id)

        assertThat(persistedTransaction).isPresent
        assertThat(persistedTransaction.get().accountId).isEqualTo(account.id)
        assertThat(persistedTransaction.get().type).isEqualTo(TransactionType.CREDIT)
        assertThat(persistedTransaction.get().amountValue).isEqualTo(12_50)
        assertThat(persistedTransaction.get().amountCurrency).isEqualTo("BRL")
        assertThat(persistedTransaction.get().status).isEqualTo(TransactionStatus.SUCCEEDED)
    }

    @Test
    fun `deve persistir uma transacao de debito recusada por saldo insuficiente`() {
        val account = accountRepository.saveAndFlush(newAccount(balanceAmount = 1_00))

        val transaction = transactionRepository.saveAndFlush(
            newTransaction(
                accountId = account.id,
                type = TransactionType.DEBIT,
                amountValue = 2_00,
                status = TransactionStatus.FAILED,
                failureReason = FailureReason.INSUFFICIENT_FUNDS,
                balanceBeforeAmount = 1_00,
                balanceAfterAmount = 1_00,
            ),
        )

        val persistedTransaction = transactionRepository.findById(transaction.id)

        assertThat(persistedTransaction).isPresent
        assertThat(persistedTransaction.get().status).isEqualTo(TransactionStatus.FAILED)
        assertThat(persistedTransaction.get().failureReason).isEqualTo(FailureReason.INSUFFICIENT_FUNDS)
        assertThat(persistedTransaction.get().balanceBeforeAmount).isEqualTo(1_00)
        assertThat(persistedTransaction.get().balanceAfterAmount).isEqualTo(1_00)
    }

    @Test
    @Transactional
    fun `deve buscar conta com lock pessimista sem erro dentro de uma transacao`() {
        val account = accountRepository.saveAndFlush(newAccount())

        val lockedAccount = accountRepository.findByIdForUpdate(account.id)

        assertThat(lockedAccount).isNotNull
        assertThat(lockedAccount?.id).isEqualTo(account.id)
    }

    @Test
    fun `deve validar que transaction id e unico`() {
        val account = accountRepository.saveAndFlush(newAccount())
        val transactionId = UUID.randomUUID()

        transactionRepository.saveAndFlush(
            newTransaction(
                id = transactionId,
                accountId = account.id,
                type = TransactionType.CREDIT,
                amountValue = 10_00,
                status = TransactionStatus.SUCCEEDED,
            ),
        )

        assertThatThrownBy {
            jdbcTemplate.update(
                """
                INSERT INTO transactions (
                    id,
                    account_id,
                    type,
                    amount_value,
                    amount_currency,
                    status,
                    requested_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                transactionId,
                account.id,
                TransactionType.CREDIT.name,
                10_00,
                "BRL",
                TransactionStatus.SUCCEEDED.name,
                OffsetDateTime.now(),
            )
        }.isInstanceOf(DuplicateKeyException::class.java)
    }

    private fun newAccount(
        id: UUID = UUID.randomUUID(),
        balanceAmount: Long = 0,
    ): AccountEntity =
        AccountEntity(
            id = id,
            ownerId = UUID.randomUUID(),
            status = AccountStatus.ENABLED,
            balanceAmount = balanceAmount,
            balanceCurrency = "BRL",
            openedAt = OffsetDateTime.now().minusDays(1),
            receivedAt = OffsetDateTime.now(),
            createdAt = OffsetDateTime.now(),
            updatedAt = OffsetDateTime.now(),
        )

    private fun newTransaction(
        id: UUID = UUID.randomUUID(),
        accountId: UUID = UUID.randomUUID(),
        type: TransactionType,
        amountValue: Long,
        status: TransactionStatus,
        failureReason: FailureReason? = null,
        balanceBeforeAmount: Long? = null,
        balanceAfterAmount: Long? = null,
    ): TransactionEntity =
        TransactionEntity(
            id = id,
            accountId = accountId,
            type = type,
            amountValue = amountValue,
            amountCurrency = "BRL",
            status = status,
            failureReason = failureReason,
            balanceBeforeAmount = balanceBeforeAmount,
            balanceAfterAmount = balanceAfterAmount,
            requestedAt = OffsetDateTime.now(),
            createdAt = OffsetDateTime.now(),
        )
}
