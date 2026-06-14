package br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.entity

import br.com.leandrotavares.accountmovementauthorizer.domain.AccountStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "accounts")
open class AccountEntity(
    @Id
    @Column(name = "id", nullable = false)
    open var id: UUID = UUID.randomUUID(),

    @Column(name = "owner_id", nullable = false)
    open var ownerId: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    open var status: AccountStatus = AccountStatus.ENABLED,

    @Column(name = "balance_amount", nullable = false)
    open var balanceAmount: Long = 0,

    @Column(name = "balance_currency", nullable = false, length = 3, columnDefinition = "char(3)")
    open var balanceCurrency: String = "BRL",

    @Column(name = "opened_at", nullable = false)
    open var openedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "received_at", nullable = false)
    open var receivedAt: OffsetDateTime = OffsetDateTime.now(),

    @Version
    @Column(name = "version", nullable = false)
    open var version: Long = 0,

    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    open var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
