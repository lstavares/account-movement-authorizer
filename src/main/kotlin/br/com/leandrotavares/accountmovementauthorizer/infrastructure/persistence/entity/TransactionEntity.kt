package br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.entity

import br.com.leandrotavares.accountmovementauthorizer.domain.FailureReason
import br.com.leandrotavares.accountmovementauthorizer.domain.TransactionStatus
import br.com.leandrotavares.accountmovementauthorizer.domain.TransactionType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "transactions")
open class TransactionEntity(
    @Id
    @Column(name = "id", nullable = false)
    open var id: UUID = UUID.randomUUID(),

    @Column(name = "account_id", nullable = false)
    open var accountId: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    open var type: TransactionType = TransactionType.CREDIT,

    @Column(name = "amount_value", nullable = false)
    open var amountValue: Long = 0,

    @Column(name = "amount_currency", nullable = false, length = 3, columnDefinition = "char(3)")
    open var amountCurrency: String = "BRL",

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    open var status: TransactionStatus = TransactionStatus.SUCCEEDED,

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", length = 50)
    open var failureReason: FailureReason? = null,

    @Column(name = "balance_before_amount")
    open var balanceBeforeAmount: Long? = null,

    @Column(name = "balance_after_amount")
    open var balanceAfterAmount: Long? = null,

    @Column(name = "requested_at", nullable = false)
    open var requestedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "created_at", nullable = false)
    open var createdAt: OffsetDateTime = OffsetDateTime.now(),
)
