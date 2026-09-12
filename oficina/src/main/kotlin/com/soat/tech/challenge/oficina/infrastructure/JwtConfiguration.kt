package com.soat.tech.challenge.oficina.infrastructure

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import java.security.MessageDigest
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Configuration
class JwtConfiguration(
	private val signingSecret: JwtSigningSecret,
	private val appEnvironment: AppEnvironment,
) {

	private val secretKey: SecretKey by lazy {
		val digest = MessageDigest.getInstance("SHA-256").digest(signingSecret.rawValue.toByteArray(Charsets.UTF_8))
		SecretKeySpec(digest, "HmacSHA256")
	}

	@Bean
	fun jwtSigningKey(): SecretKey = secretKey

	@Bean
	fun jwtDecoder(): JwtDecoder {
		val decoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build()
		val audienceValidator = OAuth2TokenValidator<Jwt> { token ->
			if (token.audience?.contains(REQUIRED_AUDIENCE) == true) {
				OAuth2TokenValidatorResult.success()
			} else {
				OAuth2TokenValidatorResult.failure(
					OAuth2Error("invalid_token", "Required audience is missing", null),
				)
			}
		}
		decoder.setJwtValidator(
			DelegatingOAuth2TokenValidator(
				JwtValidators.createDefaultWithIssuer(REQUIRED_ISSUER),
				audienceValidator,
				JwtClaimValidator<String>("env") { it == appEnvironment.value },
			),
		)
		return decoder
	}

	private companion object {
		const val REQUIRED_ISSUER = "oficina"
		const val REQUIRED_AUDIENCE = "oficina-api"
	}
}
