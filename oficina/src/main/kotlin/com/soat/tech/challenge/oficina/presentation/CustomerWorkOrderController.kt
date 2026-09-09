package com.soat.tech.challenge.oficina.presentation

import com.soat.tech.challenge.oficina.application.WorkOrderApplicationService
import com.soat.tech.challenge.oficina.application.api.dto.BudgetDecision
import com.soat.tech.challenge.oficina.application.api.dto.BudgetDecisionRequest
import com.soat.tech.challenge.oficina.application.api.dto.WorkOrderTrackingResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/customer/os")
class CustomerWorkOrderController(
    private val workOrders: WorkOrderApplicationService,
) {

    @GetMapping("/acompanhar")
    fun track(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam @NotBlank codigo: String,
    ): WorkOrderTrackingResponse = workOrders.trackForCustomer(customerId(jwt), codigo)

    @PostMapping("/aprovar-orcamento")
    fun approveQuote(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam @NotBlank codigo: String,
    ): WorkOrderTrackingResponse = workOrders.approveCustomerQuoteForCustomer(customerId(jwt), codigo)

    @PostMapping("/reprovar-orcamento")
    fun rejectQuote(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam @NotBlank codigo: String,
    ): WorkOrderTrackingResponse = workOrders.rejectCustomerQuoteForCustomer(customerId(jwt), codigo)

    @PostMapping("/orcamento/decisao")
    fun processBudgetDecision(
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody @Valid request: BudgetDecisionRequest,
    ): WorkOrderTrackingResponse = when (request.decisao) {
        BudgetDecision.APROVADO -> workOrders.approveCustomerQuoteForCustomer(customerId(jwt), request.codigo)
        BudgetDecision.RECUSADO -> workOrders.rejectCustomerQuoteForCustomer(customerId(jwt), request.codigo)
    }

    private fun customerId(jwt: Jwt): UUID = try {
        UUID.fromString(jwt.subject)
    } catch (_: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid customer subject")
    } catch (_: NullPointerException) {
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid customer subject")
    }
}
