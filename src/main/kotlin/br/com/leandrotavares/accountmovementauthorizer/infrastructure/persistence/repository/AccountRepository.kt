package br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.repository

import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.entity.AccountEntity
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface AccountRepository : JpaRepository<AccountEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from AccountEntity account where account.id = :id")
    fun findByIdForUpdate(@Param("id") id: UUID): AccountEntity?
}
