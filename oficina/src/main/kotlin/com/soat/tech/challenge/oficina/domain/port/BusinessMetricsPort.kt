package com.soat.tech.challenge.oficina.domain.port

import java.time.Duration

enum class WorkOrderStage {
    DIAGNOSIS,
    APPROVAL,
    EXECUTION,
}

enum class BusinessMetricOperation {
    CREATE,
    TRACK,
    QUOTE_DECISION,
    TRANSITION,
}

interface BusinessMetricsPort {
    fun workOrderCreated()
    fun stageCompleted(status: WorkOrderStage, duration: Duration)
    fun processingFailed(operation: BusinessMetricOperation)
}
