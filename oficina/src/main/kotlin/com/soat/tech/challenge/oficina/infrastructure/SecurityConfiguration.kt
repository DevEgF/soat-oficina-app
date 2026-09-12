package com.soat.tech.challenge.oficina.infrastructure

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.ProviderManager
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfiguration {

	@Bean
	fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

	@Bean
	fun userDetailsService(
		credentials: StaffCredentials,
		encoder: PasswordEncoder,
	): UserDetailsService {
		val users = listOf(
			User.builder()
				.username("master")
				.password(encoder.encode(credentials.passwordFor("master")))
				.authorities(
					"SCOPE_MASTER",
					"SCOPE_ADMIN",
					"SCOPE_ATTENDANT",
					"SCOPE_TECHNICIAN",
					"SCOPE_WAREHOUSE",
				)
				.build(),
			User.builder()
				.username("atendente")
				.password(encoder.encode(credentials.passwordFor("atendente")))
				.authorities("SCOPE_ATTENDANT")
				.build(),
			User.builder()
				.username("tecnico")
				.password(encoder.encode(credentials.passwordFor("tecnico")))
				.authorities("SCOPE_TECHNICIAN")
				.build(),
			User.builder()
				.username("admin")
				.password(encoder.encode(credentials.passwordFor("admin")))
				.authorities("SCOPE_ADMIN")
				.build(),
			User.builder()
				.username("almoxarife")
				.password(encoder.encode(credentials.passwordFor("almoxarife")))
				.authorities("SCOPE_WAREHOUSE")
				.build(),
		)
		return InMemoryUserDetailsManager(users)
	}

	/**
	 * Explícito: com OAuth2 Resource Server no mesmo [SecurityFilterChain], o manager por defeito
	 * pode não autenticar [UsernamePasswordAuthenticationToken] no /login.
	 */
	@Bean
	fun authenticationManager(
		userDetailsService: UserDetailsService,
		passwordEncoder: PasswordEncoder,
	): AuthenticationManager {
		val provider = DaoAuthenticationProvider(userDetailsService)
		provider.setPasswordEncoder(passwordEncoder)
		return ProviderManager(provider)
	}

	@Bean
	fun securityFilterChain(http: HttpSecurity, jwtDecoder: JwtDecoder): SecurityFilterChain {
		http.csrf { it.disable() }
		http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
		http.authorizeHttpRequests { authorize ->
			authorize.requestMatchers(
				"/actuator/health",
				"/actuator/health/**",
				"/v3/api-docs",
				"/v3/api-docs/**",
				"/swagger-ui/**",
				"/swagger-ui.html",
				"/api/public/auth/login",
			).permitAll()
			authorize.requestMatchers("/api/customer/**").hasAuthority("SCOPE_CUSTOMER")
			authorize.requestMatchers(
				"/api/admin/**",
				"/api/attendant/**",
				"/api/technician/**",
				"/api/warehouse/**",
			).authenticated()
			authorize.anyRequest().denyAll()
		}
		http.oauth2ResourceServer { oauth2 -> oauth2.jwt { it.decoder(jwtDecoder) } }
		return http.build()
	}
}
