package com.soat.tech.challenge.oficina.infrastructure

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.stereotype.Component

/** Runs only after context initialization, including automatic Flyway migration. */
@Component
@ConditionalOnProperty(name = ["app.migration-only"], havingValue = "true")
class MigrationExitRunner(private val context: ConfigurableApplicationContext) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        SpringApplication.exit(context, { 0 })
    }
}
