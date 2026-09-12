package com.soat.tech.challenge.oficina.infrastructure

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {

	@Bean
	fun openAPI(): OpenAPI = OpenAPI()
		.info(
			Info()
				.title("Oficina — Tech Challenge SOAT")
				.description(
					"MVP oficina: fluxo por papéis (swimlane). JWT via POST /api/public/auth/login. " +
						"Prefixos: /api/admin (SCOPE_ADMIN), /api/attendant (SCOPE_ATTENDANT), " +
						"/api/technician (SCOPE_TECHNICIAN), /api/warehouse (SCOPE_WAREHOUSE). " +
						"Customer: GET/POST /api/customer/os/* com token CUSTOMER.",
				)
				.version("1.0.0"),
		)
		.components(
			Components().addSecuritySchemes(
				"bearer-jwt",
				SecurityScheme()
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT"),
			),
		)
}
