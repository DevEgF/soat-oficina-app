package com.soat.tech.challenge.oficina.application.api.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class BudgetDecisionRequest(
    @field:NotBlank
    val codigo: String,

    @field:NotNull
    val decisao: BudgetDecision,
)

enum class BudgetDecision {
    APROVADO,
    RECUSADO,
}
