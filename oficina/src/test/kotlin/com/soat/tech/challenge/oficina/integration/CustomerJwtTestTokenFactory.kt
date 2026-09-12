package com.soat.tech.challenge.oficina.integration

import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Instant
import java.util.UUID
import javax.crypto.SecretKey

class CustomerJwtTestTokenFactory(signingKey: SecretKey) {

	private val encoder: JwtEncoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(signingKey))

	fun customer(customerId: UUID): String = token(customerId.toString(), listOf("CUSTOMER"))

	fun customerSubject(subject: String): String = token(subject, listOf("CUSTOMER"))

	fun staff(subject: String = "admin"): String = token(subject, listOf("ADMIN"))

	private fun token(subject: String, scopes: List<String>): String {
		val now = Instant.now()
		val claims = JwtClaimsSet.builder()
			.issuer("oficina")
			.audience(listOf("oficina-api"))
			.issuedAt(now)
			.expiresAt(now.plusSeconds(300))
			.subject(subject)
			.claim("env", "hml")
			.claim("scope", scopes)
			.build()
		val header = JwsHeader.with(MacAlgorithm.HS256).build()
		return encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
	}
}
