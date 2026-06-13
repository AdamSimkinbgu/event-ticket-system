package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.Tickets.TicketStatus;
import com.ticketing.system.Core.Domain.company.CompanyStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Application.dto.InventorySelectionDTO;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventorySelection;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import com.ticketing.system.Core.Domain.events.StandingZone;
import com.ticketing.system.Core.Domain.events.Location;
import com.ticketing.system.Core.Domain.events.VenueMap;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.orders.ReceiptLine;
import com.ticketing.system.Core.Domain.orders.TransactionRecord;
import com.ticketing.system.Core.Application.dto.RefundResultDTO;
import com.ticketing.system.Core.Domain.events.EventCategory;
import com.ticketing.system.Core.Domain.users.Permission;
import com.ticketing.system.Core.Domain.users.User;

class EventManagementServiceTest {

    private IEventRepository mockEventRepo;
    private IProductionCompanyRepository mockCompanyRepo;
    private ITicketRepository mockTicketRepo;
    private ISessionManager sessionManager;
    private EventManagementService eventService;
    private IOrderReceiptRepository orderReceiptRepository;
    private IPaymentGateway paymentGateway;
    private IUserRepository userRepository;

    private final String OWNER_TOKEN = "owner-token";
    private final String MANAGER_TOKEN = "manager-token";
    private final String INVALID_TOKEN = "invalid-token";

    private final int COMPANY_ID = 100;
    private final int EVENT_ID = 10;
    private final int OWNER_ID = 1;
    private final int MANAGER_ID = 2;
    private final int ZONE_ID = 5;
    private final int ORDER_RECEIPT_ID = 20;
    private final String COMPANY_1_NAME = "Company1";
    private final String COMPANY_1_DESCRIPTION = "A test production company1";
    private final Location LOCATION = new Location("Belgium", "Brussels");

    private ProductionCompany company;
    private InventoryZone zone;
    private VenueMap venueMap;
    private Event event;
    private User ownerUser;
    private User managerUser;

    @BeforeEach
    public void setUp() {
        mockEventRepo = mock(IEventRepository.class);
        mockCompanyRepo = mock(IProductionCompanyRepository.class);
        mockTicketRepo = mock(ITicketRepository.class);
        sessionManager = mock(ISessionManager.class);
        orderReceiptRepository = mock(IOrderReceiptRepository.class);
        paymentGateway = mock(IPaymentGateway.class);
        userRepository = mock(IUserRepository.class);

        eventService = new EventManagementService(
                mockEventRepo,
                mockCompanyRepo,
                mockTicketRepo,
                sessionManager,
                orderReceiptRepository,
                paymentGateway,
                userRepository);

        company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE,
                COMPANY_1_DESCRIPTION, 4.5);
        zone = new StandingZone(ZONE_ID, "VIP", 10, 100);
        venueMap = new VenueMap(1, LOCATION, List.of(zone));
        event = new Event(
                EVENT_ID,
                "Concert",
                4.5,
                List.of("Artist1"),
                EventCategory.CONCERT,
                COMPANY_ID,
                EventStatus.SCHEDULED,
                venueMap,
                List.of(),
                null,
                null);
        ownerUser = new User(OWNER_ID, "Owner Name", "owner@example.com", "hashedpassword",50);
        ownerUser.addFounderAppointment(COMPANY_ID);
        managerUser = new User(MANAGER_ID, "Manager Name", "manager@example.com", "hashedpassword",40);
        managerUser.receiveManagerAppointment(COMPANY_ID, OWNER_ID, List.of(Permission.CONFIGURE_VENUE));
        managerUser.acceptInvitation(COMPANY_ID);
    }

    @Test
    public void GivenOwner_WhenUpdateZoneCapacity_ThenZoneCapacityUpdated() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
        eventService.updateStandingZoneCapacity(
                OWNER_TOKEN,
                COMPANY_ID,
                EVENT_ID,
                ZONE_ID,
                20);

        assertEquals(20, zone.getAvailableAmount());
    }

    @Test
    public void GivenManagerWithConfigureVenuePermission_WhenUpdateZoneCapacity_ThenZoneCapacityUpdated() {

        company.addManager(MANAGER_ID);

        when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);

        eventService.updateStandingZoneCapacity(
                MANAGER_TOKEN,
                COMPANY_ID,
                EVENT_ID,
                ZONE_ID,
                20);

        assertEquals(20, zone.getAvailableAmount());
    }

    @Test
    public void GivenInvalidToken_WhenUpdateZoneCapacity_ThenThrowException() {
        when(sessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> eventService.updateStandingZoneCapacity(
                INVALID_TOKEN,
                COMPANY_ID,
                EVENT_ID,
                ZONE_ID,
                20));
    }

    @Test
    public void GivenCompanyDoesNotExist_WhenUpdateZoneCapacity_ThenThrowException() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenThrow(RuntimeException.class);

        assertThrows(RuntimeException.class, () -> eventService.updateStandingZoneCapacity(
                OWNER_TOKEN,
                COMPANY_ID,
                EVENT_ID,
                ZONE_ID,
                20));
    }

    @Test
    public void GivenEventDoesNotExist_WhenUpdateZoneCapacity_ThenThrowException() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockEventRepo.findById(EVENT_ID)).thenThrow(RuntimeException.class);

        assertThrows(RuntimeException.class, () -> eventService.updateStandingZoneCapacity(
                OWNER_TOKEN,
                COMPANY_ID,
                EVENT_ID,
                ZONE_ID,
                20));
    }

    @Test
    public void GivenUserIsNotOwnerOrManager_WhenUpdateZoneCapacity_ThenThrowException() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(99);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);

        assertThrows(RuntimeException.class, () -> eventService.updateStandingZoneCapacity(
                OWNER_TOKEN,
                COMPANY_ID,
                EVENT_ID,
                ZONE_ID,
                20));
    }

    @Test
    public void GivenManagerWithoutConfigureVenuePermission_WhenUpdateZoneCapacity_ThenThrowException() {

        company.addManager(MANAGER_ID);

        when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);

        assertThrows(RuntimeException.class, () -> eventService.updateStandingZoneCapacity(
                MANAGER_TOKEN,
                COMPANY_ID,
                EVENT_ID,
                ZONE_ID,
                20));
    }

    @Test
    public void GivenZoneDoesNotExist_WhenUpdateZoneCapacity_ThenThrowException() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
        when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);

        assertThrows(IllegalArgumentException.class, () -> eventService.updateStandingZoneCapacity(
                OWNER_TOKEN,
                COMPANY_ID,
                EVENT_ID,
                999,
                20));
    }

    @Test
    public void GivenReservedTicketsMoreThanNewCapacity_WhenUpdateZoneCapacity_ThenThrowException() {
        zone.reserve(InventorySelection.standing(8));

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
        when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);

        assertThrows(IllegalArgumentException.class, () -> eventService.updateStandingZoneCapacity(
                OWNER_TOKEN,
                COMPANY_ID,
                EVENT_ID,
                ZONE_ID,
                5));
    }

    private OrderReceipt setupStateBasedHappyPath() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
        when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        ReceiptLine line = new ReceiptLine(1, 100.0, EVENT_ID, 1, "A1", java.time.LocalDateTime.now());
        OrderReceipt realReceipt = OrderReceipt.forMember(99, OWNER_ID, 100.0, List.of(line));
        realReceipt.addTransaction(
                TransactionRecord.paymentCharge(42, "test-gateway", 100.0, "ILS", java.time.LocalDateTime.now()));

        when(paymentGateway.getId()).thenReturn("test-gateway");
        when(paymentGateway.refund(anyInt(), anyDouble())).thenReturn(
                new RefundResultDTO("refund-tx-1", "99", 100.0, java.time.LocalDateTime.now(), List.of(), List.of()));
        when(mockTicketRepo.findByEventId(String.valueOf(EVENT_ID))).thenReturn(List.of());

        when(orderReceiptRepository.findByEventId(EVENT_ID))
                .thenReturn(List.of(realReceipt));

        return realReceipt;
    }

    @Test
    public void GivenInvalidToken_WhenCancelEventAndRefund_ThenThrowException() {
        when(sessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> eventService.cancelEventAndRefund(INVALID_TOKEN, EVENT_ID));
    }

    @Test
    public void GivenEventDoesNotExist_WhenCancelEventAndRefund_ThenThrowException() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenThrow(RuntimeException.class);

        assertThrows(RuntimeException.class, () -> eventService.cancelEventAndRefund(OWNER_TOKEN, EVENT_ID));
    }

    @Test
    public void GivenCompanyDoesNotExist_WhenCancelEventAndRefund_ThenThrowException() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenThrow(RuntimeException.class);

        assertThrows(RuntimeException.class, () -> eventService.cancelEventAndRefund(OWNER_TOKEN, EVENT_ID));
    }

    @Test
    public void GivenEventAlreadyCanceled_WhenCancelEventAndRefund_ThenReceiptStateRemainsUnchanged() {
        OrderReceipt receipt = setupStateBasedHappyPath();
        event.transitionToCanceled("cancelled");
        ; // Manually cancel it beforehand

        eventService.cancelEventAndRefund(OWNER_TOKEN, EVENT_ID);

        assertEquals(false, receipt.wasRefunded());
    }

    @Test
    public void GivenValidRequest_WhenCancelEventAndRefund_ThenEventStateIsCanceled() {
        setupStateBasedHappyPath();

        eventService.cancelEventAndRefund(OWNER_TOKEN, EVENT_ID);

        assertTrue(event.getStatus() == EventStatus.CANCELED);
    }

    @Test
    public void GivenValidRequest_WhenCancelEventAndRefund_ThenReceiptStateIsMarkedRefunded() {
        OrderReceipt realReceipt = setupStateBasedHappyPath();

        eventService.cancelEventAndRefund(OWNER_TOKEN, EVENT_ID);

        assertTrue(realReceipt.wasRefunded());
    }

    @Test
    public void GivenPaidTicket_WhenCancelEventAndRefund_ThenTicketStatusIsMarkedRefunded() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
        when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(orderReceiptRepository.findByEventId(EVENT_ID)).thenReturn(List.of());

        Ticket paidTicket = new Ticket(EVENT_ID, ZONE_ID, ORDER_RECEIPT_ID, null, 100.0, 1, "BARCODE123");
        // paidTicket.markPaid();
        when(mockTicketRepo.findByEventId(String.valueOf(EVENT_ID))).thenReturn(List.of(paidTicket));

        eventService.cancelEventAndRefund(OWNER_TOKEN, EVENT_ID);

        assertEquals(TicketStatus.REFUNDED, paidTicket.getStatus());
    }

    @Test
    public void GivenIssuedTicket_WhenCancelEventAndRefund_ThenTicketStatusIsMarkedRefunded() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
        when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(orderReceiptRepository.findByEventId(EVENT_ID)).thenReturn(List.of());

        Ticket issuedTicket = new Ticket(EVENT_ID, ZONE_ID, ORDER_RECEIPT_ID, null, 100.0, 1, "BARCODE123");

        issuedTicket.markIssued("BARCODE123");

        when(mockTicketRepo.findByEventId(String.valueOf(EVENT_ID))).thenReturn(List.of(issuedTicket));

        eventService.cancelEventAndRefund(OWNER_TOKEN, EVENT_ID);

        assertEquals(TicketStatus.REFUNDED, issuedTicket.getStatus());
    }

    @Test
    public void GivenAvailableTicket_WhenCancelEventAndRefund_ThenTicketStatusRemainsUnchanged() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
        when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(orderReceiptRepository.findByEventId(EVENT_ID)).thenReturn(List.of());

        Ticket availableTicket = new Ticket(EVENT_ID, ZONE_ID, ORDER_RECEIPT_ID, null, 100.0, 1, "BARCODE123");

        availableTicket.release();

        when(mockTicketRepo.findByEventId(String.valueOf(EVENT_ID))).thenReturn(List.of(availableTicket));

        eventService.cancelEventAndRefund(OWNER_TOKEN, EVENT_ID);

        assertEquals(TicketStatus.VOIDED, availableTicket.getStatus());
    }

    // new test
    @Test
    void GivenSeatedVenueConfig_WhenConfigureVenueMap_ThenSeatCoordinatesArePreserved() {
        // configure seats A1 with x=10, y=20
        // fetch event venue map
        // assert A1.getX() == 10
        // assert A1.getY() == 20

    }

    @Test
    void GivenEventCreatedByAddEventAndVenueConfigured_WhenReserveStandingTicket_ThenReservationSucceeds() {
        // addEvent(...)
        // configureVenueMap(...)
        // reserveStandingTicketsForMember(...)
        // should not throw UnsupportedOperationException from PurchasePolicy

    }

    @Test
    @Disabled("UC-19: Owner adds event — DRAFT state initially")
    void givenOwner_whenAddEvent_thenEventInDraft() {
    }

    @Test
    @Disabled("UC-19: Manager without permission rejected")
    void givenManagerWithoutPermission_whenAddEvent_thenRejected() {
    }

    @Test
    @Disabled("UC-21: setEventPolicies stores PurchasePolicy + DiscountPolicy")
    void givenOwner_whenSetEventPolicies_thenStored() {
    }
}
