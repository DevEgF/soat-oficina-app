package com.soat.tech.challenge.oficina.infrastructure

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class AppEnvironment(
    @Value("\${app.environment}") rawValue: String,
) {
    val value: String = rawValue.trim().also {
        require(it in ALLOWED_VALUES) { "app.environment must be one of: hml, prod" }
    }

    private companion object {
        val ALLOWED_VALUES = setOf("hml", "prod")
    }
}
