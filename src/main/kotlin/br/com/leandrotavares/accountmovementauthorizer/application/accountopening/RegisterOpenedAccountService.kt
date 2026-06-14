package br.com.leandrotavares.accountmovementauthorizer.application.accountopening

import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.entity.AccountEntity
import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.repository.AccountRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class RegisterOpenedAccountService(
    private val openedAccountWriter: OpenedAccountWriter,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun register(command: RegisterOpenedAccountCommand): RegisterOpenedAccountResult =
        try {
            openedAccountWriter.register(command)
        } catch (ex: DataIntegrityViolationException) {
            logger.info(
                "Account opening already persisted by a concurrent process. accountId={}",
                command.accountId,
            )
            RegisterOpenedAccountResult.ALREADY_EXISTS
        }
}

@Component
class OpenedAccountWriter(
    private val accountRepository: AccountRepository,
) {
    @Transactional
    fun register(command: RegisterOpenedAccountCommand): RegisterOpenedAccountResult {
        if (accountRepository.findById(command.accountId).isPresent) {
            return RegisterOpenedAccountResult.ALREADY_EXISTS
        }

        val now = OffsetDateTime.now(ZoneOffset.UTC)

        accountRepository.saveAndFlush(
            AccountEntity(
                id = command.accountId,
                ownerId = command.ownerId,
                status = command.status,
                balanceAmount = 0,
                balanceCurrency = "BRL",
                openedAt = command.openedAt,
                receivedAt = now,
                createdAt = now,
                updatedAt = now,
            ),
        )

        return RegisterOpenedAccountResult.CREATED
    }
}
