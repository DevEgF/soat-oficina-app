package com.soat.tech.challenge.oficina.infrastructure.web

import com.soat.tech.challenge.oficina.infrastructure.AppEnvironment
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter(
    appEnvironment: AppEnvironment,
    @Value("\${spring.application.name:oficina}") private val service: String,
) : OncePerRequestFilter() {

    private val environment = appEnvironment.value

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader(CORRELATION_ID_HEADER)
            ?.takeIf(SAFE_CORRELATION_ID::matches)
            ?: UUID.randomUUID().toString()

        response.setHeader(CORRELATION_ID_HEADER, requestId)
        MDC.put(REQUEST_ID_KEY, requestId)
        MDC.put(ENVIRONMENT_KEY, environment)
        MDC.put(SERVICE_KEY, service)
        try {
            filterChain.doFilter(request, response)
        } finally {
            try {
                log.atInfo()
                    .addKeyValue("status", response.status)
                    .log("request.completed")
            } finally {
                MDC.remove(REQUEST_ID_KEY)
                MDC.remove(ENVIRONMENT_KEY)
                MDC.remove(SERVICE_KEY)
            }
        }
    }

    private companion object {
        const val CORRELATION_ID_HEADER = "X-Correlation-Id"
        const val REQUEST_ID_KEY = "requestId"
        const val ENVIRONMENT_KEY = "environment"
        const val SERVICE_KEY = "service"
        val SAFE_CORRELATION_ID = Regex("[A-Za-z0-9._=-]{1,64}")
        val log = LoggerFactory.getLogger(CorrelationIdFilter::class.java)
    }
}
