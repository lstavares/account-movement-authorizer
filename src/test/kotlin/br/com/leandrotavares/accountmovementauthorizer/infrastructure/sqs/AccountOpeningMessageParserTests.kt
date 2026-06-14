package br.com.leandrotavares.accountmovementauthorizer.infrastructure.sqs

import br.com.leandrotavares.accountmovementauthorizer.domain.AccountStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class AccountOpeningMessageParserTests {
    private val parser = AccountOpeningMessageParser(ObjectMapper().registerKotlinModule())

    @Test
    fun `deve aceitar payload valido`() {
        val accountId = UUID.fromString("5b19c8b6-0cc4-4c72-a989-0c2ee15fa975")
        val ownerId = UUID.fromString("315e3cfe-f4af-4cd2-b298-a449e614349a")
        val payload = """
            {
              "account": {
                "id": "$accountId",
                "owner": "$ownerId",
                "created_at": "1634874339",
                "status": "ENABLED"
              }
            }
        """.trimIndent()

        val command = parser.parse(payload)

        assertThat(command.accountId).isEqualTo(accountId)
        assertThat(command.ownerId).isEqualTo(ownerId)
        assertThat(command.status).isEqualTo(AccountStatus.ENABLED)
        assertThat(command.openedAt).isEqualTo(
            Instant.ofEpochSecond(1_634_874_339).atOffset(ZoneOffset.UTC),
        )
    }

    @Test
    fun `deve rejeitar payloads invalidos`() {
        val accountId = UUID.fromString("5b19c8b6-0cc4-4c72-a989-0c2ee15fa975")
        val ownerId = UUID.fromString("315e3cfe-f4af-4cd2-b298-a449e614349a")
        val invalidPayloads = listOf(
            "{",
            """
                {
                  "account": {
                    "id": "$accountId",
                    "created_at": "1634874339",
                    "status": "ENABLED"
                  }
                }
            """.trimIndent(),
            """
                {
                  "account": {
                    "id": "not-a-uuid",
                    "owner": "$ownerId",
                    "created_at": "1634874339",
                    "status": "ENABLED"
                  }
                }
            """.trimIndent(),
            """
                {
                  "account": {
                    "id": "$accountId",
                    "owner": "$ownerId",
                    "created_at": "1634874339",
                    "status": "BLOCKED"
                  }
                }
            """.trimIndent(),
            """
                {
                  "account": {
                    "id": "$accountId",
                    "owner": "$ownerId",
                    "created_at": "not-an-epoch",
                    "status": "ENABLED"
                  }
                }
            """.trimIndent(),
        )

        invalidPayloads.forEach { payload ->
            assertThatThrownBy { parser.parse(payload) }
                .isInstanceOf(InvalidAccountOpeningMessageException::class.java)
        }
    }
}
