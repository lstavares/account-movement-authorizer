package br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.repository

import br.com.leandrotavares.accountmovementauthorizer.infrastructure.persistence.entity.TransactionEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface TransactionRepository : JpaRepository<TransactionEntity, UUID>
