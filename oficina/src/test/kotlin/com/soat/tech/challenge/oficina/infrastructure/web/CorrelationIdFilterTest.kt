package com.soat.tech.challenge.oficina.infrastructure.web

import com.soat.tech.challenge.oficina.infrastructure.AppEnvironment
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.MDC
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import java.util.UUID

class CorrelationIdFilterTest {

    private val filter = CorrelationIdFilter(appEnvironment = AppEnvironment("hml"), service = "oficina")

    @AfterEach
    fun clearMdc() = MDC.clear()

    @Test
    fun `runs at highest precedence before security filters`() {
        assertEquals(
            Ordered.HIGHEST_PRECEDENCE,
            CorrelationIdFilter::class.java.getAnnotation(Order::class.java).value,
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["req-123", "gateway-request="])
    fun `preserves safe incoming correlation id and propagates context`(correlationId: String) {
        val request = MockHttpServletRequest().apply {
            addHeader("X-Correlation-Id", correlationId)
            addHeader("Authorization", "Bearer secret-token")
            setContent("sensitive body".toByteArray())
        }
        val response = MockHttpServletResponse()
        var contextDuringChain: Map<String, String>? = null

        filter.doFilter(request, response, FilterChain { _, _ ->
            contextDuringChain = MDC.getCopyOfContextMap()
        })

        assertEquals(correlationId, response.getHeader("X-Correlation-Id"))
        assertEquals(correlationId, contextDuringChain?.get("requestId"))
        assertEquals("hml", contextDuringChain?.get("environment"))
        assertEquals("oficina", contextDuringChain?.get("service"))
        assertTrue(contextDuringChain?.values?.none { it.contains("secret-token") || it.contains("sensitive body") } == true)
        assertNull(MDC.get("requestId"))
        assertNull(MDC.get("environment"))
        assertNull(MDC.get("service"))
    }

    @Test
    fun `generates uuid when correlation id is missing`() {
        val response = MockHttpServletResponse()

        filter.doFilter(MockHttpServletRequest(), response, MockFilterChain())

        UUID.fromString(response.getHeader("X-Correlation-Id"))
        assertNull(MDC.get("requestId"))
    }

    @Test
    fun `generates uuid when correlation id is invalid`() {
        val request = MockHttpServletRequest().apply {
            addHeader("X-Correlation-Id", "invalid id with spaces and Authorization Bearer secret")
        }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        UUID.fromString(response.getHeader("X-Correlation-Id"))
        assertNull(MDC.get("requestId"))
    }
}
