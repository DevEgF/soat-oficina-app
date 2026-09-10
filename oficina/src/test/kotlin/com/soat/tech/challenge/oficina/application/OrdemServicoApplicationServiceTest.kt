package com.soat.tech.challenge.oficina.application

import com.soat.tech.challenge.oficina.application.api.dto.CreateWorkOrderRequest
import com.soat.tech.challenge.oficina.application.api.dto.WorkOrderPartLineRequest
import com.soat.tech.challenge.oficina.application.api.dto.WorkOrderServiceLineRequest
import com.soat.tech.challenge.oficina.domain.exception.BusinessRuleException
import com.soat.tech.challenge.oficina.domain.exception.InsufficientStockException
import com.soat.tech.challenge.oficina.domain.exception.InvalidStatusTransitionException
import com.soat.tech.challenge.oficina.domain.exception.NotFoundException
import com.soat.tech.challenge.oficina.domain.model.Customer
import com.soat.tech.challenge.oficina.domain.model.TaxDocument
import com.soat.tech.challenge.oficina.domain.model.PartLine
import com.soat.tech.challenge.oficina.domain.model.WorkOrder
import com.soat.tech.challenge.oficina.domain.model.Part
import com.soat.tech.challenge.oficina.domain.model.LicensePlate
import com.soat.tech.challenge.oficina.domain.model.CatalogService
import com.soat.tech.challenge.oficina.domain.model.PartReservation
import com.soat.tech.challenge.oficina.domain.model.WorkOrderStatus
import com.soat.tech.challenge.oficina.domain.model.PartReservationStatus
import com.soat.tech.challenge.oficina.domain.model.Vehicle
import com.soat.tech.challenge.oficina.domain.port.CustomerRepository
import com.soat.tech.challenge.oficina.domain.port.WorkOrderRepository
import com.soat.tech.challenge.oficina.domain.port.PartRepository
import com.soat.tech.challenge.oficina.domain.port.PartReservationRepository
import com.soat.tech.challenge.oficina.domain.port.CatalogServiceRepository
import com.soat.tech.challenge.oficina.domain.port.VehicleRepository
import com.soat.tech.challenge.oficina.domain.port.NotificationPort
import com.soat.tech.challenge.oficina.domain.port.BusinessMetricsPort
import com.soat.tech.challenge.oficina.domain.port.WorkOrderStage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionSynchronizationManager

class WorkOrderApplicationServiceTest {

	private val workOrders = mockk<WorkOrderRepository>()
	private val customers = mockk<CustomerRepository>()
	private val vehicles = mockk<VehicleRepository>()
	private val catalogServices = mockk<CatalogServiceRepository>()
	private val parts = mockk<PartRepository>()
	private val reservations = mockk<PartReservationRepository>()
	private val notificationPort = mockk<NotificationPort>(relaxed = true)
	private val businessMetrics = mockk<BusinessMetricsPort>(relaxed = true)
	private val fixedInstant = Instant.parse("2026-03-01T12:00:00Z")
	private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
	private lateinit var service: WorkOrderApplicationService

	@BeforeEach
	fun setup() {
		every { reservations.sumPendingReservedQuantityExcludingWorkOrder(any(), any()) } returns 0
		every { reservations.replacePendingReservations(any(), any()) } returns Unit
		every { reservations.cancelPendingForWorkOrder(any()) } returns Unit
		every { reservations.findPendingByWorkOrder(any()) } returns emptyList()
		every { reservations.confirmPendingForWorkOrder(any()) } returns Unit
		service = WorkOrderApplicationService(
			workOrders,
			customers,
			vehicles,
			catalogServices,
			parts,
			reservations,
			notificationPort,
			businessMetrics,
			clock,
		)
	}

	@Nested
	@DisplayName("Given no existing customer or vehicle")
	inner class GivenNewCustomerAndVehicle {

		@Test
		@DisplayName("when create then persists RECEIVED work order")
		fun createNewOrder() {
			val doc = TaxDocument.parse("52998224725")
			val sid = UUID.randomUUID()
			val pid = UUID.randomUUID()
			every { customers.findByFiscalDocument(doc) } returns Optional.empty()
			every { customers.save(any()) } answers { firstArg() }
			every { vehicles.findByLicensePlate(LicensePlate.parse("ABC1234")) } returns Optional.empty()
			every { vehicles.save(any()) } answers { firstArg() }
			every { catalogServices.findById(sid) } returns Optional.of(
				CatalogService(sid, "Oleo", null, 1000, 30),
			)
			every { parts.findById(pid) } returns Optional.of(Part(pid, "P1", "F", 500, 10))
			every { workOrders.save(any()) } answers { firstArg() }
			val req = CreateWorkOrderRequest(
				customerTaxIdDigits = "52998224725",
				customerName = "Novo",
				plate = "ABC1234",
				vehicleBrand = "VW",
				vehicleModel = "Gol",
				vehicleYear = 2020,
				services = listOf(WorkOrderServiceLineRequest(catalogServiceId = sid, quantity = 1)),
				parts = listOf(WorkOrderPartLineRequest(partId = pid, quantity = 1)),
			)
			TransactionSynchronizationManager.initSynchronization()
			try {
				val r = service.create(req)
				assertEquals(WorkOrderStatus.RECEIVED, r.status)
				verify { workOrders.save(any()) }
				verify(exactly = 0) { businessMetrics.workOrderCreated() }

				every { businessMetrics.workOrderCreated() } throws IllegalStateException("telemetry unavailable")
				TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
				verify(exactly = 1) { businessMetrics.workOrderCreated() }
			} finally {
				TransactionSynchronizationManager.clearSynchronization()
			}
		}
	}

	@Nested
	@DisplayName("Given saved work order in RECEIVED")
	inner class GivenReceivedOrder {

		@Test
		@DisplayName("when startDiagnosis then status is IN_DIAGNOSIS")
		fun startDiagnosis() {
			val id = UUID.randomUUID()
			val wo = WorkOrder.create(
				id = id,
				customerId = UUID.randomUUID(),
				vehicleId = UUID.randomUUID(),
				serviceLines = emptyList(),
				partLines = emptyList(),
			)
			every { workOrders.findById(id) } returns Optional.of(wo)
			every { workOrders.save(any()) } answers { firstArg() }
			val r = service.startDiagnosis(id)
			assertEquals(WorkOrderStatus.IN_DIAGNOSIS, r.status)
		}
	}

	@Nested
	@DisplayName("Given tracking lookup")
	inner class GivenTracking {

		@Test
		@DisplayName("when track with another customer id then returns the same not found error as an unknown order")
		fun wrongOwner() {
			val wo = WorkOrder.create(
				customerId = UUID.randomUUID(),
				vehicleId = UUID.randomUUID(),
				serviceLines = emptyList(),
				partLines = emptyList(),
			)
			every { workOrders.findByTrackingCode(wo.trackingCode) } returns Optional.of(wo)
			val error = assertFailsWith<NotFoundException> {
				service.trackForCustomer(UUID.randomUUID(), wo.trackingCode)
			}
			assertEquals("Work order not found", error.message)
		}

		@Test
		@DisplayName("when tracking code is unknown then returns work order not found")
		fun unknownOrder() {
			every { workOrders.findByTrackingCode("missing") } returns Optional.empty()
			val error = assertFailsWith<NotFoundException> {
				service.trackForCustomer(UUID.randomUUID(), " missing ")
			}
			assertEquals("Work order not found", error.message)
		}

		@Test
		@DisplayName("when track with matching document then returns masked payload")
		fun success() {
			// Customer tracking only exposes work orders released to the customer.
			val wo = WorkOrder.create(
				customerId = UUID.randomUUID(),
				vehicleId = UUID.randomUUID(),
				serviceLines = emptyList(),
				partLines = emptyList(),
			).copy(status = WorkOrderStatus.PENDING_APPROVAL)
			val doc = TaxDocument.parse("52998224725")
			every { workOrders.findByTrackingCode(wo.trackingCode) } returns Optional.of(wo)
			every { customers.findById(wo.customerId) } returns Optional.of(Customer(wo.customerId, doc, "A"))
			every { vehicles.findById(wo.vehicleId) } returns Optional.of(
				Vehicle(wo.vehicleId, wo.customerId, LicensePlate.parse("XYZ1234"), "F", "M", 2019),
			)
			val r = service.trackForCustomer(wo.customerId, wo.trackingCode)
			assertEquals(wo.trackingCode, r.trackingCode)
			assertEquals("XYZ1234", r.vehiclePlate)
		}
	}

	@Nested
	@DisplayName("Given order with part lines and stock")
	inner class GivenStock {

		@Test
		@DisplayName("when submit plan through customer approve then does not deduct stock immediately")
		fun planReserveAndCustomerApprove() {
			val id = UUID.randomUUID()
			val pid = UUID.randomUUID()
			val wo = WorkOrder.create(
				id = id,
				customerId = UUID.randomUUID(),
				vehicleId = UUID.randomUUID(),
				serviceLines = emptyList(),
				partLines = mutableListOf(PartLine(pid, 2, 100)),
			)
			wo.startDiagnosis(fixedInstant)
			every { workOrders.findById(id) } returns Optional.of(wo)
			every { parts.findById(pid) } returns Optional.of(Part(pid, "C", "N", 100, 5))
			every { workOrders.save(any()) } answers { firstArg() }
			every { customers.findById(wo.customerId) } returns Optional.of(
				Customer(wo.customerId, TaxDocument.parse("52998224725"), "X"),
			)
			every { vehicles.findById(wo.vehicleId) } returns Optional.of(
				Vehicle(wo.vehicleId, wo.customerId, LicensePlate.parse("ABC1234"), "F", "M", 2020),
			)
			service.submitPlanForInternalApproval(id)
			service.approveInternal(id)
			service.sendQuoteToCustomer(id)
			verify(exactly = 0) { parts.save(any()) }
			every { workOrders.findByTrackingCode(wo.trackingCode) } returns Optional.of(wo)
			service.approveCustomerQuoteForCustomer(wo.customerId, wo.trackingCode)
			assertEquals(WorkOrderStatus.AWAITING_PARTS_RELEASE, wo.status)
			verify(exactly = 0) { parts.save(any()) }
		}

		@Test
		@DisplayName("when submit plan without enough available stock then throws")
		fun insufficientStock() {
			val id = UUID.randomUUID()
			val pid = UUID.randomUUID()
			val wo = WorkOrder.create(
				id = id,
				customerId = UUID.randomUUID(),
				vehicleId = UUID.randomUUID(),
				serviceLines = emptyList(),
				partLines = mutableListOf(PartLine(pid, 9, 100)),
			)
			wo.startDiagnosis(fixedInstant)
			every { workOrders.findById(id) } returns Optional.of(wo)
			every { parts.findById(pid) } returns Optional.of(Part(pid, "C", "N", 100, 2))
			assertFailsWith<InsufficientStockException> {
				service.submitPlanForInternalApproval(id)
			}
			verify(exactly = 0) { businessMetrics.stageCompleted(any(), any()) }
		}

		@Test
		fun `successful plan submission records diagnosis duration from diagnosed timestamp`() {
			val id = UUID.randomUUID()
			val wo = WorkOrder.create(
				id = id,
				customerId = UUID.randomUUID(),
				vehicleId = UUID.randomUUID(),
				serviceLines = emptyList(),
				partLines = emptyList(),
			)
			wo.startDiagnosis(fixedInstant.minusSeconds(45))
			every { workOrders.findById(id) } returns Optional.of(wo)
			every { workOrders.save(any()) } answers { firstArg() }

			service.submitPlanForInternalApproval(id)

			verify(exactly = 1) { businessMetrics.stageCompleted(WorkOrderStage.DIAGNOSIS, java.time.Duration.ofSeconds(45)) }
		}
	}

	@Nested
	@DisplayName("Given internal approval flow")
	inner class GivenInternal {

		@Test
		@DisplayName("when rejectInternal then cancels reservations and sets CANCELLED")
		fun rejectInternal() {
			val id = UUID.randomUUID()
			val wo = WorkOrder.create(
				id = id,
				customerId = UUID.randomUUID(),
				vehicleId = UUID.randomUUID(),
				serviceLines = emptyList(),
				partLines = emptyList(),
			)
			wo.startDiagnosis(fixedInstant)
			wo.submitPlanForInternalApproval(fixedInstant.plusSeconds(1))
			every { workOrders.findById(id) } returns Optional.of(wo)
			every { workOrders.save(any()) } answers { firstArg() }
			service.rejectInternal(id)
			assertEquals(WorkOrderStatus.CANCELLED, wo.status)
			verify { reservations.cancelPendingForWorkOrder(id) }
		}
	}

	@Nested
	@DisplayName("Given customer quote actions")
	inner class GivenCustomerQuote {

		@Test
		@DisplayName("when rejectCustomerQuote then cancels reservations")
		fun rejectCustomer() {
			val id = UUID.randomUUID()
			val wo = WorkOrder.create(
				id = id,
				customerId = UUID.randomUUID(),
				vehicleId = UUID.randomUUID(),
				serviceLines = emptyList(),
				partLines = emptyList(),
			)
			wo.startDiagnosis(fixedInstant)
			wo.submitPlanForInternalApproval(fixedInstant.plusSeconds(1))
			wo.approveInternal(fixedInstant.plusSeconds(2))
			every { workOrders.findByTrackingCode(wo.trackingCode) } returns Optional.of(wo)
			every { customers.findById(wo.customerId) } returns Optional.of(
				Customer(wo.customerId, TaxDocument.parse("52998224725"), "X"),
			)
			every { vehicles.findById(wo.vehicleId) } returns Optional.of(
				Vehicle(wo.vehicleId, wo.customerId, LicensePlate.parse("ABC1234"), "F", "M", 2020),
			)
			every { workOrders.save(any()) } answers { firstArg() }
			service.rejectCustomerQuoteForCustomer(wo.customerId, wo.trackingCode)
			assertEquals(WorkOrderStatus.CANCELLED, wo.status)
			verify { reservations.cancelPendingForWorkOrder(id) }
		}

		@Test
		fun `successful customer decision records approval duration from quote timestamp`() {
			val wo = WorkOrder.create(
				customerId = UUID.randomUUID(),
				vehicleId = UUID.randomUUID(),
				serviceLines = emptyList(),
				partLines = emptyList(),
			).copy(status = WorkOrderStatus.PENDING_APPROVAL, quoteSentAt = fixedInstant.minusSeconds(12))
			every { workOrders.findByTrackingCode(wo.trackingCode) } returns Optional.of(wo)
			every { workOrders.save(any()) } answers { firstArg() }
			every { customers.findById(wo.customerId) } returns Optional.of(Customer(wo.customerId, TaxDocument.parse("52998224725"), "X"))
			every { vehicles.findById(wo.vehicleId) } returns Optional.of(Vehicle(wo.vehicleId, wo.customerId, LicensePlate.parse("ABC1234"), "F", "M", 2020))

			service.approveCustomerQuoteForCustomer(wo.customerId, wo.trackingCode)

			verify(exactly = 1) { businessMetrics.stageCompleted(WorkOrderStage.APPROVAL, java.time.Duration.ofSeconds(12)) }
		}

		@Test
		@DisplayName("when approveCustomerQuote in wrong status then throws")
		fun approveWrongStatus() {
			val wo = WorkOrder.create(
				customerId = UUID.randomUUID(),
				vehicleId = UUID.randomUUID(),
				serviceLines = emptyList(),
				partLines = emptyList(),
			)
			every { workOrders.findByTrackingCode(wo.trackingCode) } returns Optional.of(wo)
			every { customers.findById(wo.customerId) } returns Optional.of(
				Customer(wo.customerId, TaxDocument.parse("52998224725"), "X"),
			)
			assertFailsWith<InvalidStatusTransitionException> {
				service.approveCustomerQuoteForCustomer(wo.customerId, wo.trackingCode)
			}
		}
	}

	@Nested
	@DisplayName("Given order in execution")
	inner class GivenInExecution {

		@Test
		@DisplayName("when completeServices with pending reservations then throws")
		fun completeBlockedByPending() {
			val id = UUID.randomUUID()
			val wo = WorkOrder.create(
				id = id,
				customerId = UUID.randomUUID(),
				vehicleId = UUID.randomUUID(),
				serviceLines = emptyList(),
				partLines = emptyList(),
			).copy(
				status = WorkOrderStatus.IN_EXECUTION,
				workStartedAt = fixedInstant,
				approvedAt = fixedInstant,
			)
			every { workOrders.findById(id) } returns Optional.of(wo)
			every { reservations.findPendingByWorkOrder(id) } returns listOf(
				PartReservation(
					id = UUID.randomUUID(),
					workOrderId = id,
					partId = UUID.randomUUID(),
					quantity = 1,
					status = PartReservationStatus.PENDING,
				),
			)
			assertFailsWith<IllegalStateException> {
				service.completeServices(id)
			}
			verify(exactly = 0) { businessMetrics.stageCompleted(any(), any()) }
		}

		@Test
		@DisplayName("when completeServices and registerDelivery then DELIVERED")
		fun completeAndDeliver() {
			val id = UUID.randomUUID()
			val wo = WorkOrder.create(
				id = id,
				customerId = UUID.randomUUID(),
				vehicleId = UUID.randomUUID(),
				serviceLines = emptyList(),
				partLines = emptyList(),
			).copy(
				status = WorkOrderStatus.IN_EXECUTION,
				workStartedAt = fixedInstant,
				approvedAt = fixedInstant,
			)
			every { workOrders.findById(id) } returns Optional.of(wo)
			every { workOrders.save(any()) } answers { firstArg() }
			every { customers.findById(wo.customerId) } returns Optional.of(
				Customer(wo.customerId, TaxDocument.parse("52998224725"), "X"),
			)
			every { vehicles.findById(wo.vehicleId) } returns Optional.of(
				Vehicle(wo.vehicleId, wo.customerId, LicensePlate.parse("ABC1234"), "F", "M", 2020),
			)
			service.completeServices(id)
			service.registerDelivery(id)
			assertEquals(WorkOrderStatus.DELIVERED, wo.status)
			verify(exactly = 1) { businessMetrics.stageCompleted(WorkOrderStage.EXECUTION, java.time.Duration.ZERO) }
		}
	}

	@Nested
	@DisplayName("Given list and get")
	inner class GivenQueries {

		@Test
		@DisplayName("when list and get then returns same id")
		fun listAndGet() {
			val id = UUID.randomUUID()
			val wo = WorkOrder.create(
				id = id,
				customerId = UUID.randomUUID(),
				vehicleId = UUID.randomUUID(),
				serviceLines = emptyList(),
				partLines = emptyList(),
			)
			every { workOrders.findAll() } returns listOf(wo)
			every { workOrders.findById(id) } returns Optional.of(wo)
			assertEquals(1, service.list().size)
			assertEquals(id, service.get(id).id)
		}

		@Test
		@DisplayName("when get unknown id then NotFoundException")
		fun getMissing() {
			val id = UUID.randomUUID()
			every { workOrders.findById(id) } returns Optional.empty()
			assertFailsWith<NotFoundException> {
				service.get(id)
			}
		}
	}

	@Nested
	@DisplayName("Given order awaiting customer approval")
	inner class GivenAwaitingApproval {

		@Test
		@DisplayName("when returnToDiagnosis then back to IN_DIAGNOSIS")
		fun returnToDiagnosis() {
			val id = UUID.randomUUID()
			val wo = WorkOrder.create(
				id = id,
				customerId = UUID.randomUUID(),
				vehicleId = UUID.randomUUID(),
				serviceLines = emptyList(),
				partLines = emptyList(),
			)
			wo.startDiagnosis(fixedInstant)
			wo.submitPlanForInternalApproval(fixedInstant.plusSeconds(1))
			wo.approveInternal(fixedInstant.plusSeconds(2))
			every { workOrders.findById(id) } returns Optional.of(wo)
			every { workOrders.save(any()) } answers { firstArg() }
			service.returnToDiagnosis(id)
			assertEquals(WorkOrderStatus.IN_DIAGNOSIS, wo.status)
			verify { reservations.cancelPendingForWorkOrder(id) }
		}
	}

	@Nested
	@DisplayName("Given listing ordering and logical exclusion (Fase 2)")
	inner class GivenListingOrdering {

		private fun order(status: WorkOrderStatus, createdAt: Instant): WorkOrder =
			WorkOrder.create(
				customerId = UUID.randomUUID(),
				vehicleId = UUID.randomUUID(),
				serviceLines = emptyList(),
				partLines = emptyList(),
			).copy(status = status, createdAt = createdAt)

		@Test
		@DisplayName("when list then excludes FINALIZED and DELIVERED orders")
		fun excludesFinalizedAndDelivered() {
			val received = order(WorkOrderStatus.RECEIVED, fixedInstant)
			val finalized = order(WorkOrderStatus.FINALIZED, fixedInstant)
			val delivered = order(WorkOrderStatus.DELIVERED, fixedInstant)
			every { workOrders.findAll() } returns listOf(received, finalized, delivered)
			val result = service.list()
			assertEquals(1, result.size)
			assertEquals(received.id, result[0].id)
		}

		@Test
		@DisplayName("when list then orders by status priority (IN_EXECUTION > PENDING_APPROVAL > IN_DIAGNOSIS > RECEIVED)")
		fun ordersByStatusPriority() {
			val inExecution = order(WorkOrderStatus.IN_EXECUTION, fixedInstant)
			val pendingApproval = order(WorkOrderStatus.PENDING_APPROVAL, fixedInstant)
			val inDiagnosis = order(WorkOrderStatus.IN_DIAGNOSIS, fixedInstant)
			val received = order(WorkOrderStatus.RECEIVED, fixedInstant)
			every { workOrders.findAll() } returns listOf(received, inDiagnosis, pendingApproval, inExecution)
			val ids = service.list().map { it.id }
			assertEquals(listOf(inExecution.id, pendingApproval.id, inDiagnosis.id, received.id), ids)
		}

		@Test
		@DisplayName("when same status then oldest first (FIFO)")
		fun fifoWithinSameStatus() {
			val older = order(WorkOrderStatus.IN_DIAGNOSIS, fixedInstant)
			val newer = order(WorkOrderStatus.IN_DIAGNOSIS, fixedInstant.plusSeconds(60))
			every { workOrders.findAll() } returns listOf(newer, older)
			val ids = service.list().map { it.id }
			assertEquals(listOf(older.id, newer.id), ids)
		}
	}

	@Nested
	@DisplayName("Given external budget decision idempotency (Fase 2)")
	inner class GivenBudgetDecisionIdempotency {

		private fun quotedOrder(): WorkOrder {
			val wo = WorkOrder.create(
				customerId = UUID.randomUUID(),
				vehicleId = UUID.randomUUID(),
				serviceLines = emptyList(),
				partLines = emptyList(),
			)
			wo.startDiagnosis(fixedInstant)
			wo.submitPlanForInternalApproval(fixedInstant.plusSeconds(1))
			// approveInternal já transiciona para PENDING_APPROVAL (orçamento liberado ao cliente).
			wo.approveInternal(fixedInstant.plusSeconds(2))
			return wo
		}

		@Test
		@DisplayName("when approval decision is resent then second call is idempotent")
		fun approveIsIdempotent() {
			val wo = quotedOrder()
			every { workOrders.findByTrackingCode(wo.trackingCode) } returns Optional.of(wo)
			every { workOrders.save(any()) } answers { firstArg() }
			every { customers.findById(wo.customerId) } returns Optional.of(
				Customer(wo.customerId, TaxDocument.parse("52998224725"), "X"),
			)
			every { vehicles.findById(wo.vehicleId) } returns Optional.of(
				Vehicle(wo.vehicleId, wo.customerId, LicensePlate.parse("ABC1234"), "F", "M", 2020),
			)
			val first = service.approveCustomerQuoteForCustomer(wo.customerId, wo.trackingCode)
			assertEquals(WorkOrderStatus.AWAITING_PARTS_RELEASE, first.status)
			// Reenvio da mesma decisão não deve lançar transição inválida (idempotente).
			val second = service.approveCustomerQuoteForCustomer(wo.customerId, wo.trackingCode)
			assertEquals(WorkOrderStatus.AWAITING_PARTS_RELEASE, second.status)
		}

		@Test
		@DisplayName("when rejection decision is resent then second call is idempotent")
		fun rejectIsIdempotent() {
			val wo = quotedOrder()
			every { workOrders.findByTrackingCode(wo.trackingCode) } returns Optional.of(wo)
			every { workOrders.save(any()) } answers { firstArg() }
			every { customers.findById(wo.customerId) } returns Optional.of(
				Customer(wo.customerId, TaxDocument.parse("52998224725"), "X"),
			)
			every { vehicles.findById(wo.vehicleId) } returns Optional.of(
				Vehicle(wo.vehicleId, wo.customerId, LicensePlate.parse("ABC1234"), "F", "M", 2020),
			)
			val first = service.rejectCustomerQuoteForCustomer(wo.customerId, wo.trackingCode)
			assertEquals(WorkOrderStatus.CANCELLED, first.status)
			val second = service.rejectCustomerQuoteForCustomer(wo.customerId, wo.trackingCode)
			assertEquals(WorkOrderStatus.CANCELLED, second.status)
		}
	}
}
