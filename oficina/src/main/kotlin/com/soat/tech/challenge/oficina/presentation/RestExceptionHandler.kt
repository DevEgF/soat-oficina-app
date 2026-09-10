package com.soat.tech.challenge.oficina.presentation

import com.soat.tech.challenge.oficina.domain.exception.BusinessRuleException
import com.soat.tech.challenge.oficina.domain.exception.DomainException
import com.soat.tech.challenge.oficina.domain.exception.InsufficientStockException
import com.soat.tech.challenge.oficina.domain.exception.InvalidLicensePlateException
import com.soat.tech.challenge.oficina.domain.exception.InvalidStatusTransitionException
import com.soat.tech.challenge.oficina.domain.exception.InvalidTaxDocumentException
import com.soat.tech.challenge.oficina.domain.exception.NotFoundException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import com.soat.tech.challenge.oficina.domain.port.BusinessMetricOperation
import com.soat.tech.challenge.oficina.domain.port.BusinessMetricsPort
import jakarta.servlet.http.HttpServletRequest

data class ErrorBody(val message: String, val requestId: String)

@RestControllerAdvice
class RestExceptionHandler(
	private val businessMetrics: BusinessMetricsPort,
) : ResponseEntityExceptionHandler() {

	@ExceptionHandler(NotFoundException::class)
	fun notFound(e: NotFoundException) =
		response(HttpStatus.NOT_FOUND, e.message ?: "Not found")

	@ExceptionHandler(
		IllegalArgumentException::class,
		InvalidTaxDocumentException::class,
		InvalidLicensePlateException::class,
	)
	fun badRequest(e: Exception): ResponseEntity<ErrorBody> {
		val msg = e.message ?: "Bad request"
		return response(HttpStatus.BAD_REQUEST, msg)
	}

	@ExceptionHandler(
		InvalidStatusTransitionException::class,
		InsufficientStockException::class,
		BusinessRuleException::class,
	)
	fun conflictDomain(e: DomainException) =
		response(HttpStatus.CONFLICT, e.message ?: "Business rule violation")

	@ExceptionHandler(IllegalStateException::class)
	fun conflictState(e: IllegalStateException) =
		response(HttpStatus.CONFLICT, e.message ?: "Invalid state")

	@ExceptionHandler(DataIntegrityViolationException::class)
	fun dataIntegrityViolation(e: DataIntegrityViolationException) =
		response(HttpStatus.CONFLICT, "Data conflict: a record with this data already exists")

	@ExceptionHandler(BadCredentialsException::class)
	fun unauthorized(e: BadCredentialsException) =
		response(HttpStatus.UNAUTHORIZED, "Invalid credentials")

	@ExceptionHandler(AuthenticationException::class)
	fun authenticationFailed(e: AuthenticationException) =
		response(HttpStatus.UNAUTHORIZED, "Unauthorized")

	@ExceptionHandler(AccessDeniedException::class)
	fun accessDenied(e: AccessDeniedException) =
		response(HttpStatus.FORBIDDEN, "Forbidden")

	@ExceptionHandler(Exception::class)
	fun unexpected(e: Exception, request: HttpServletRequest): ResponseEntity<ErrorBody> {
		log.error("request.failed exceptionClass={}", e.javaClass.name)
		runCatching { businessMetrics.processingFailed(metricOperation(request)) }
		return response(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error")
	}

	private fun metricOperation(request: HttpServletRequest): BusinessMetricOperation = when {
		request.method == "POST" && request.requestURI == "/api/attendant/ordens-servico" -> BusinessMetricOperation.CREATE
		request.method == "GET" && request.requestURI == "/api/customer/os/acompanhar" -> BusinessMetricOperation.TRACK
		request.requestURI.startsWith("/api/customer/os/") -> BusinessMetricOperation.QUOTE_DECISION
		else -> BusinessMetricOperation.TRANSITION
	}

	override fun handleExceptionInternal(
		ex: Exception,
		body: Any?,
		headers: HttpHeaders,
		statusCode: HttpStatusCode,
		request: WebRequest,
	): ResponseEntity<Any>? {
		val message = HttpStatus.resolve(statusCode.value())?.reasonPhrase ?: "Request failed"
		return ResponseEntity(ErrorBody(message, requestId()), headers, statusCode)
	}

	private fun response(status: HttpStatus, message: String) =
		ResponseEntity.status(status).body(ErrorBody(message, requestId()))

	private fun requestId() = MDC.get("requestId") ?: "unknown"

	private companion object {
		val log = LoggerFactory.getLogger(RestExceptionHandler::class.java)
	}
}
