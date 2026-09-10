package com.soat.tech.challenge.oficina.infrastructure.observability

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.soat.tech.challenge.oficina.domain.port.BusinessMetricOperation
import com.soat.tech.challenge.oficina.domain.port.BusinessMetricsPort
import com.soat.tech.challenge.oficina.domain.port.WorkOrderStage
import com.soat.tech.challenge.oficina.infrastructure.AppEnvironment
import java.io.PrintStream
import java.time.Clock
import java.time.Duration
import org.springframework.stereotype.Component

@Component
class CloudWatchEmfMetricAdapter(
    private val objectMapper: ObjectMapper,
    private val appEnvironment: AppEnvironment,
    private val clock: Clock,
    private val output: PrintStream = System.out,
) : BusinessMetricsPort {

    override fun workOrderCreated() = emit("WorkOrdersCreated", 1.0, "Count")

    override fun stageCompleted(status: WorkOrderStage, duration: Duration) =
        emit("WorkOrderStageDurationMs", duration.toMillis().toDouble(), "Milliseconds", "Status", status.name)

    override fun processingFailed(operation: BusinessMetricOperation) =
        emit("WorkOrderProcessingFailures", 1.0, "Count", "Operation", operation.name)

    private fun emit(metricName: String, value: Double, unit: String, dimensionName: String? = null, dimensionValue: String? = null) {
        output.println(buildEvent(metricName, value, unit, dimensionName, dimensionValue).toString())
    }

    internal fun buildEvent(
        metricName: String,
        value: Double,
        unit: String,
        dimensionName: String? = null,
        dimensionValue: String? = null,
    ): ObjectNode {
        val dimensions = mutableListOf("ServiceName", "Environment")
        if (dimensionName != null && dimensionValue != null) dimensions += dimensionName

        val metric = objectMapper.createObjectNode().apply {
            put("Name", metricName)
            put("Unit", unit)
        }
        val cloudWatchMetric = objectMapper.createObjectNode().apply {
            put("Namespace", "Oficina")
            set<ObjectNode>("Metrics", objectMapper.createArrayNode().add(metric))
            set<ObjectNode>("Dimensions", objectMapper.createArrayNode().add(objectMapper.valueToTree(dimensions)))
        }
        val aws = objectMapper.createObjectNode().apply {
            put("Timestamp", clock.instant().toEpochMilli())
            set<ObjectNode>("CloudWatchMetrics", objectMapper.createArrayNode().add(cloudWatchMetric))
        }
        return objectMapper.createObjectNode().apply {
            set<ObjectNode>("_aws", aws)
            put("ServiceName", "oficina")
            put("Environment", appEnvironment.value)
            if (dimensionName != null && dimensionValue != null) put(dimensionName, dimensionValue)
            put(metricName, value)
        }
    }
}
