package br.com.leandrotavares.accountmovementauthorizer.application.accountopening

import br.com.leandrotavares.accountmovementauthorizer.domain.AccountStatus
import java.time.OffsetDateTime
import java.util.UUID

data class RegisterOpenedAccountCommand(
    val accountId: UUID,
    val ownerId: UUID,
    val status: AccountStatus,
    val openedAt: OffsetDateTime,
)

enum class RegisterOpenedAccountResult {
    CREATED,
    ALREADY_EXISTS,
}
