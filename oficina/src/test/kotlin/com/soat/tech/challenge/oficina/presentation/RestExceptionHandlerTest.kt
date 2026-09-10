package com.soat.tech.challenge.oficina.presentation

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import com.soat.tech.challenge.oficina.domain.exception.NotFoundException
import com.soat.tech.challenge.oficina.domain.port.BusinessMetricOperation
import com.soat.tech.challenge.oficina.domain.port.BusinessMetricsPort
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import io.mockk.every

class RestExceptionHandlerTest {

    private val businessMetrics = mockk<BusinessMetricsPort>(relaxed = true)
    private val handler = RestExceptionHandler(businessMetrics)

    @AfterEach
    fun clearMdc() = MDC.clear()

    @Test
    fun `unexpected exception returns generic response with request id and sanitized log`() {
        MDC.put("requestId", "req-500")
        val logger = LoggerFactory.getLogger(RestExceptionHandler::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            val response = handler.unexpected(
                RuntimeException("SQL select password from customer where cpf=12345678900"),
                MockHttpServletRequest("POST", "/api/attendant/ordens-servico"),
            )

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
            assertEquals(ErrorBody("Internal server error", "req-500"), response.body)
            assertEquals(1, appender.list.size)
            val event = appender.list.single()
            assertEquals("request.failed exceptionClass=java.lang.RuntimeException", event.formattedMessage)
            assertEquals(null, event.throwableProxy)
            assertFalse(event.formattedMessage.contains("password"))
            assertFalse(event.formattedMessage.contains("12345678900"))
            verify(exactly = 1) { businessMetrics.processingFailed(BusinessMetricOperation.CREATE) }
        } finally {
            logger.detachAppender(appender)
        }
    }

    @Test
    fun `unexpected request paths map to fixed low-cardinality operations`() {
        val cases = listOf(
            MockHttpServletRequest("GET", "/api/customer/os/acompanhar") to BusinessMetricOperation.TRACK,
            MockHttpServletRequest("POST", "/api/customer/os/orcamento/decisao") to BusinessMetricOperation.QUOTE_DECISION,
            MockHttpServletRequest("POST", "/api/technician/ordens-servico/id/submeter-plano") to BusinessMetricOperation.TRANSITION,
        )

        cases.forEach { (request, operation) ->
            handler.unexpected(RuntimeException("failure"), request)
            verify(exactly = 1) { businessMetrics.processingFailed(operation) }
        }
    }

    @Test
    fun `handled domain and security errors do not count as processing failures`() {
        handler.notFound(NotFoundException("missing"))
        handler.unauthorized(BadCredentialsException("bad"))
        handler.accessDenied(AccessDeniedException("denied"))

        verify(exactly = 0) { businessMetrics.processingFailed(any()) }
        confirmVerified(businessMetrics)
    }

    @Test
    fun `telemetry failure does not replace the original internal server response`() {
        every { businessMetrics.processingFailed(any()) } throws IllegalStateException("telemetry unavailable")

        val response = handler.unexpected(
            RuntimeException("original failure"),
            MockHttpServletRequest("POST", "/api/technician/ordens-servico/id/concluir-servicos"),
        )

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Internal server error", response.body?.message)
    }

    @Test
    fun `security exceptions preserve unauthorized and forbidden statuses`() {
        MDC.put("requestId", "req-security")

        val unauthorized = handler.unauthorized(BadCredentialsException("credential detail"))
        val forbidden = handler.accessDenied(AccessDeniedException("subject detail"))

        assertEquals(HttpStatus.UNAUTHORIZED, unauthorized.statusCode)
        assertEquals(ErrorBody("Invalid credentials", "req-security"), unauthorized.body)
        assertEquals(HttpStatus.FORBIDDEN, forbidden.statusCode)
        assertEquals(ErrorBody("Forbidden", "req-security"), forbidden.body)
    }
}
