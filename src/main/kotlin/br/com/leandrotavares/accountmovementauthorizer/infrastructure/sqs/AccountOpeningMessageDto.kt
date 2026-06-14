package br.com.leandrotavares.accountmovementauthorizer.infrastructure.sqs

import com.fasterxml.jackson.annotation.JsonProperty

data class AccountOpeningMessageDto(
    val account: AccountOpeningAccountDto,
)

data class AccountOpeningAccountDto(
    val id: String,
    val owner: String,
    @JsonProperty("created_at")
    val createdAt: String,
    val status: String,
)
