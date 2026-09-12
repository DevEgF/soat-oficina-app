package com.soat.tech.challenge.oficina.infrastructure.observability

import com.fasterxml.jackson.databind.ObjectMapper
import com.soat.tech.challenge.oficina.domain.port.BusinessMetricOperation
import com.soat.tech.challenge.oficina.domain.port.WorkOrderStage
import com.soat.tech.challenge.oficina.infrastructure.AppEnvironment
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class CloudWatchEmfMetricAdapterTest {
    private val mapper = ObjectMapper()
    private val output = ByteArrayOutputStream()
    private val adapter = CloudWatchEmfMetricAdapter(
        mapper,
        AppEnvironment("hml"),
        Clock.fixed(Instant.parse("2026-09-09T12:34:56Z"), ZoneOffset.UTC),
        PrintStream(output),
    )

    @Test
    fun `created metric is emitted as a root EMF JSON object with fixed dimensions`() {
        adapter.workOrderCreated()

        val event = mapper.readTree(output.toString().trim())
        val metadata = event.path("_aws").path("CloudWatchMetrics")[0]
        assertEquals(Instant.parse("2026-09-09T12:34:56Z").toEpochMilli(), event.path("_aws").path("Timestamp").asLong())
        assertEquals("Oficina", metadata.path("Namespace").asText())
        assertEquals("oficina", event.path("ServiceName").asText())
        assertEquals("hml", event.path("Environment").asText())
        assertEquals(1.0, event.path("WorkOrdersCreated").asDouble())
        assertEquals("Count", metadata.path("Metrics")[0].path("Unit").asText())
        assertEquals(listOf("ServiceName", "Environment"), metadata.path("Dimensions")[0].map { it.asText() })
        assertFalse(event.toString().contains("customerId"))
        assertFalse(event.toString().contains("requestId"))
        assertFalse(output.toString().contains("message"))
    }

    @Test
    fun `stage duration emits only allowlisted status values`() {
        WorkOrderStage.entries.forEach { stage ->
            output.reset()
            adapter.stageCompleted(stage, Duration.ofMillis(2750))
            val event = mapper.readTree(output.toString().trim())
            assertEquals(stage.name, event.path("Status").asText())
            assertEquals(2750.0, event.path("WorkOrderStageDurationMs").asDouble())
            assertTrue(event.path("Status").asText() in setOf("DIAGNOSIS", "APPROVAL", "EXECUTION"))
            assertEquals(
                listOf("ServiceName", "Environment", "Status"),
                event.path("_aws").path("CloudWatchMetrics")[0].path("Dimensions")[0].map { it.asText() },
            )
        }
    }

    @Test
    fun `processing failure emits only allowlisted operation values`() {
        BusinessMetricOperation.entries.forEach { operation ->
            output.reset()
            adapter.processingFailed(operation)
            val event = mapper.readTree(output.toString().trim())
            assertEquals(operation.name, event.path("Operation").asText())
            assertEquals(1.0, event.path("WorkOrderProcessingFailures").asDouble())
            assertTrue(event.path("Operation").asText() in setOf("CREATE", "TRACK", "QUOTE_DECISION", "TRANSITION"))
        }
    }
}
