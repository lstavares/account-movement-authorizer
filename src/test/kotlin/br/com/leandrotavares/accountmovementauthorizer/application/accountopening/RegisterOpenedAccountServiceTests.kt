package br.com.leandrotavares.accountmovementauthorizer.application.accountopening

import br.com.leandrotavares.accountmovementauthorizer.PostgreSqlIntegrationTest
import br.com.leandrotavares.accountmovementauthorizer.domain.AccountStatus
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.entity.AccountEntity
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.repository.AccountRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@ActiveProfiles("test")
@SpringBootTest
class RegisterOpenedAccountServiceIntegrationTests : PostgreSqlIntegrationTest() {
    @Autowired
    private lateinit var registerOpenedAccountService: RegisterOpenedAccountService

    @Autowired
    private lateinit var accountRepository: AccountRepository

    @BeforeEach
    fun cleanDatabase() {
        accountRepository.deleteAll()
    }

    @Test
    fun `deve cadastrar conta nova com saldo zero e moeda BRL`() {
        val command = newCommand()

        val result = registerOpenedAccountService.register(command)

        val persistedAccount = accountRepository.findById(command.accountId).orElseThrow()
        assertThat(result).isEqualTo(RegisterOpenedAccountResult.CREATED)
        assertThat(persistedAccount.id).isEqualTo(command.accountId)
        assertThat(persistedAccount.ownerId).isEqualTo(command.ownerId)
        assertThat(persistedAccount.status).isEqualTo(AccountStatus.ENABLED)
        assertThat(persistedAccount.openedAt.toInstant()).isEqualTo(command.openedAt.toInstant())
        assertThat(persistedAccount.balanceAmount).isZero()
        assertThat(persistedAccount.balanceCurrency).isEqualTo("BRL")
        assertThat(persistedAccount.receivedAt).isNotNull()
    }

    @Test
    fun `deve reprocessar mesma conta sem duplicar nem alterar dados existentes`() {
        val accountId = UUID.randomUUID()
        val originalOwnerId = UUID.randomUUID()
        val originalOpenedAt = Instant.ofEpochSecond(1_600_000_000).atOffset(ZoneOffset.UTC)
        val originalReceivedAt = Instant.ofEpochSecond(1_600_086_400).atOffset(ZoneOffset.UTC)

        val existingAccount = accountRepository.saveAndFlush(
            AccountEntity(
                id = accountId,
                ownerId = originalOwnerId,
                status = AccountStatus.DISABLED,
                balanceAmount = 99_99,
                balanceCurrency = "BRL",
                openedAt = originalOpenedAt,
                receivedAt = originalReceivedAt,
                createdAt = originalReceivedAt,
                updatedAt = originalReceivedAt,
            ),
        )
        val originalVersion = existingAccount.version

        val result = registerOpenedAccountService.register(
            newCommand(
                accountId = accountId,
                ownerId = UUID.randomUUID(),
                status = AccountStatus.ENABLED,
                openedAt = OffsetDateTime.now(ZoneOffset.UTC),
            ),
        )

        val persistedAccount = accountRepository.findById(accountId).orElseThrow()
        assertThat(result).isEqualTo(RegisterOpenedAccountResult.ALREADY_EXISTS)
        assertThat(accountRepository.count()).isEqualTo(1)
        assertThat(persistedAccount.ownerId).isEqualTo(originalOwnerId)
        assertThat(persistedAccount.status).isEqualTo(AccountStatus.DISABLED)
        assertThat(persistedAccount.balanceAmount).isEqualTo(99_99)
        assertThat(persistedAccount.openedAt.toInstant()).isEqualTo(originalOpenedAt.toInstant())
        assertThat(persistedAccount.receivedAt.toInstant()).isEqualTo(originalReceivedAt.toInstant())
        assertThat(persistedAccount.version).isEqualTo(originalVersion)
    }

    private fun newCommand(
        accountId: UUID = UUID.randomUUID(),
        ownerId: UUID = UUID.randomUUID(),
        status: AccountStatus = AccountStatus.ENABLED,
        openedAt: OffsetDateTime = Instant.ofEpochSecond(1_634_874_339).atOffset(ZoneOffset.UTC),
    ): RegisterOpenedAccountCommand =
        RegisterOpenedAccountCommand(
            accountId = accountId,
            ownerId = ownerId,
            status = status,
            openedAt = openedAt,
        )
}

class RegisterOpenedAccountServiceTests {
    @Test
    fun `deve tratar violacao de chave concorrente como conta ja existente`() {
        val command = RegisterOpenedAccountCommand(
            accountId = UUID.randomUUID(),
            ownerId = UUID.randomUUID(),
            status = AccountStatus.ENABLED,
            openedAt = OffsetDateTime.now(ZoneOffset.UTC),
        )
        val openedAccountWriter = mockk<OpenedAccountWriter>()
        val service = RegisterOpenedAccountService(openedAccountWriter)

        every { openedAccountWriter.register(command) } throws DataIntegrityViolationException("duplicate account id")

        val result = service.register(command)

        assertThat(result).isEqualTo(RegisterOpenedAccountResult.ALREADY_EXISTS)
    }
}
