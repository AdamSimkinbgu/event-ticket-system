package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
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
import com.ticketing.system.Core.Domain.exceptions.InvalidStateTransitionException;
import com.ticketing.system.Core.Application.dto.InventorySelectionDTO;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventorySelection;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import com.ticketing.system.Core.Domain.events.StandingZone;
import com.ticketing.system.Core.Domain.events.Location;
import com.ticketing.system.Core.Domain.events.Seat;
import com.ticketing.system.Core.Domain.events.SeatedZone;
import com.ticketing.system.Core.Domain.events.ShowDate;
import com.ticketing.system.Core.Domain.events.VenueMap;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.orders.ReceiptLine;
import com.ticketing.system.Core.Domain.orders.TransactionRecord;
import com.ticketing.system.Core.Application.dto.RefundResultDTO;
import com.ticketing.system.Core.Application.dto.ZoneDetailDTO;
import com.ticketing.system.Core.Application.dto.EventDetailDTO;
import com.ticketing.system.Core.Application.dto.EventUpdateDTO;
import com.ticketing.system.Core.Application.dto.LocationDTO;
import com.ticketing.system.Core.Application.dto.ShowDateDTO;
import com.ticketing.system.Core.Application.dto.VenueMapConfigDTO;
import com.ticketing.system.Core.Domain.events.DiscountPolicy;
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
    private INotificationService notificationService;

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
        notificationService = mock(INotificationService.class);

        eventService = new EventManagementService(
                mockEventRepo,
                mockCompanyRepo,
                mockTicketRepo,
                sessionManager,
                orderReceiptRepository,
                paymentGateway,
                userRepository,
                notificationService);

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
                List.of(new ShowDate(LocalDateTime.now().plusDays(10), LocalDateTime.now().plusDays(10).plusHours(2))),
                null,
                new DiscountPolicy(0));
        ownerUser = new User(OWNER_ID, "Owner Name", "owner@example.com", "hashedpassword",50);
        ownerUser.addFounderAppointment(COMPANY_ID);
        managerUser = new User(MANAGER_ID, "Manager Name", "manager@example.com", "hashedpassword",40);
        managerUser.receiveManagerAppointment(COMPANY_ID, OWNER_ID, List.of(Permission.CONFIGURE_VENUE));
        managerUser.acceptInvitation(COMPANY_ID);
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
    public void GivenMemberReceipts_WhenCancelEventAndRefund_ThenTicketHoldersAreNotified() {
        setupStateBasedHappyPath();

        eventService.cancelEventAndRefund(OWNER_TOKEN, EVENT_ID);

        verify(notificationService).notifyEventCancelled(eq(OWNER_ID), eq(EVENT_ID), eq("Concert"));
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

    // -- UC-19: owner publishes event (SCHEDULED -> ON_SALE) -------------

    @Test
    public void GivenScheduledEvent_WhenPublishEvent_ThenStatusOnSaleAndSaved() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);

        eventService.publishEvent(OWNER_TOKEN, COMPANY_ID, EVENT_ID);

        assertEquals(EventStatus.ON_SALE, event.getStatus());
        verify(mockEventRepo).save(event);
    }

    @Test
    public void GivenInvalidToken_WhenPublishEvent_ThenThrows() {
        when(sessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

        assertThrows(RuntimeException.class,
                () -> eventService.publishEvent(INVALID_TOKEN, COMPANY_ID, EVENT_ID));
    }

    @Test
    public void GivenEventNotOwnedByCompany_WhenPublishEvent_ThenThrows() {
        int otherCompanyId = 999;
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event); // event.companyId == COMPANY_ID

        assertThrows(RuntimeException.class,
                () -> eventService.publishEvent(OWNER_TOKEN, otherCompanyId, EVENT_ID));
        assertEquals(EventStatus.SCHEDULED, event.getStatus());
    }

    @Test
    public void GivenUserWithoutPermission_WhenPublishEvent_ThenThrows() {
        User noPermissionUser = new User(MANAGER_ID, "No Perm", "noperm@example.com", "hashedpassword", 30);
        when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
        when(userRepository.getUserById(MANAGER_ID)).thenReturn(noPermissionUser);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);

        assertThrows(RuntimeException.class,
                () -> eventService.publishEvent(MANAGER_TOKEN, COMPANY_ID, EVENT_ID));
        assertEquals(EventStatus.SCHEDULED, event.getStatus());
    }

    @Test
    public void GivenDraftEvent_WhenPublishEvent_ThenThrowsInvalidStateTransition() {
        Event draftEvent = new Event(
                EVENT_ID, "Concert", 4.5, List.of("Artist1"), EventCategory.CONCERT, COMPANY_ID,
                EventStatus.DRAFT, venueMap,
                List.of(new ShowDate(LocalDateTime.now().plusDays(10), LocalDateTime.now().plusDays(10).plusHours(2))),
                null, new DiscountPolicy(0));
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(draftEvent);

        assertThrows(InvalidStateTransitionException.class,
                () -> eventService.publishEvent(OWNER_TOKEN, COMPANY_ID, EVENT_ID));
        assertEquals(EventStatus.DRAFT, draftEvent.getStatus());
    }


        ///////////////////////////Tests for configureVenueMap

        @Test
        void GivenInvalidToken_WhenConfigureVenueMap_ThenThrows() {
        when(sessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

        VenueMapConfigDTO config = new VenueMapConfigDTO(
                String.valueOf(EVENT_ID), "Venue",
                List.of(new VenueMapConfigDTO.ZoneConfigDTO("GA Floor", false, 500, null, 50.0)));

        assertThrows(RuntimeException.class,
                () -> eventService.configureVenueMap(INVALID_TOKEN, COMPANY_ID, config));
        }

        @Test
        void GivenOwnerAndStandingZone_WhenConfigureVenueMap_ThenVenueMapSavedOnEvent() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);

        VenueMapConfigDTO config = new VenueMapConfigDTO(
                String.valueOf(EVENT_ID), "Venue",
                List.of(new VenueMapConfigDTO.ZoneConfigDTO("GA Floor", false, 500, null, 50.0)));

        eventService.configureVenueMap(OWNER_TOKEN, COMPANY_ID, config);

        verify(mockEventRepo).save(event);
        assertNotNull(event.getVenueMap());
        }

        @Test
        void GivenManagerWithConfigureVenuePermission_WhenConfigureVenueMap_ThenVenueMapSaved() {
        when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
        when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);

        VenueMapConfigDTO config = new VenueMapConfigDTO(
                String.valueOf(EVENT_ID), "Venue",
                List.of(new VenueMapConfigDTO.ZoneConfigDTO("GA Floor", false, 500, null, 50.0)));

        eventService.configureVenueMap(MANAGER_TOKEN, COMPANY_ID, config);

        verify(mockEventRepo).save(event);
        }

        @Test
        void GivenManagerWithoutConfigureVenuePermission_WhenConfigureVenueMap_ThenThrows() {
        User noPermUser = new User(MANAGER_ID, "No Perm", "noperm@test.com", "hashedpassword", 30);
        noPermUser.receiveManagerAppointment(COMPANY_ID, OWNER_ID, List.of(Permission.VIEW_SALES));
        noPermUser.acceptInvitation(COMPANY_ID);

        when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
        when(userRepository.getUserById(MANAGER_ID)).thenReturn(noPermUser);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);

        VenueMapConfigDTO config = new VenueMapConfigDTO(
                String.valueOf(EVENT_ID), "Venue",
                List.of(new VenueMapConfigDTO.ZoneConfigDTO("GA Floor", false, 500, null, 50.0)));

        assertThrows(RuntimeException.class,
                () -> eventService.configureVenueMap(MANAGER_TOKEN, COMPANY_ID, config));
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

    // -------------------------------------------------------------------------
    // getEventDetail
    // -------------------------------------------------------------------------

    @Test
    void GivenOwnerToken_WhenGetEventDetail_ThenReturnsDTOWithCorrectFields() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);

        EventDetailDTO result = eventService.getEventDetail(OWNER_TOKEN, String.valueOf(EVENT_ID));

        assertEquals(String.valueOf(EVENT_ID), result.eventId());
        assertEquals("Concert", result.name());
        assertEquals(EventCategory.CONCERT, result.category());
        assertEquals(String.valueOf(COMPANY_ID), result.companyId());
        assertEquals(COMPANY_1_NAME, result.companyName());
        assertEquals(EventStatus.SCHEDULED, result.status());
        assertEquals(LOCATION, result.location());
    }

    @Test
    void GivenManagerWithPermission_WhenGetEventDetail_ThenReturnsDTOSuccessfully() {
        when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);

        EventDetailDTO result = eventService.getEventDetail(MANAGER_TOKEN, String.valueOf(EVENT_ID));

        assertEquals(String.valueOf(EVENT_ID), result.eventId());
        assertEquals("Concert", result.name());
    }

    @Test
    void GivenInvalidToken_WhenGetEventDetail_ThenThrows() {
        when(sessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

        assertThrows(RuntimeException.class,
                () -> eventService.getEventDetail(INVALID_TOKEN, String.valueOf(EVENT_ID)));
    }

    @Test
    void GivenEventNotFound_WhenGetEventDetail_ThenThrows() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenThrow(new RuntimeException("Event not found"));

        assertThrows(RuntimeException.class,
                () -> eventService.getEventDetail(OWNER_TOKEN, String.valueOf(EVENT_ID)));
    }

    @Test
    void GivenNonNumericEventId_WhenGetEventDetail_ThenThrowsIllegalArgumentException() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

        assertThrows(IllegalArgumentException.class,
                () -> eventService.getEventDetail(OWNER_TOKEN, "not-a-number"));
    }

    @Test
    void GivenUserNotInCompany_WhenGetEventDetail_ThenThrows() {
        User stranger = new User(99, "Stranger", "stranger@test.com", "hashedpw", 25);
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(99);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(userRepository.getUserById(99)).thenReturn(stranger);

        assertThrows(RuntimeException.class,
                () -> eventService.getEventDetail(OWNER_TOKEN, String.valueOf(EVENT_ID)));
    }

    @Test
    void GivenEventWithNullVenueMap_WhenGetEventDetail_ThenLocationIsNull() {
        Event draftNoVenue = new Event(
                EVENT_ID, "Draft Concert", 4.5, List.of("Artist1"),
                EventCategory.MUSIC, COMPANY_ID, EventStatus.DRAFT, null,
                List.of(new ShowDate(LocalDateTime.now().plusDays(10), LocalDateTime.now().plusDays(10).plusHours(2))),
                null, new DiscountPolicy(0));
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(draftNoVenue);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);

        EventDetailDTO result = eventService.getEventDetail(OWNER_TOKEN, String.valueOf(EVENT_ID));

        assertNull(result.location());
    }

    // -------------------------------------------------------------------------
    // editEventDetails
    // -------------------------------------------------------------------------

    @Test
    void GivenOwnerAndNewName_WhenEditEventDetails_ThenNameIsUpdated() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);

        eventService.editEventDetails(OWNER_TOKEN,
                new EventUpdateDTO(String.valueOf(EVENT_ID), "New Concert Name", null, null, null, null));

        assertEquals("New Concert Name", event.getName());
    }

    @Test
    void GivenOwnerAndNewCategory_WhenEditEventDetails_ThenCategoryIsUpdated() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);

        eventService.editEventDetails(OWNER_TOKEN,
                new EventUpdateDTO(String.valueOf(EVENT_ID), null, null, "MUSIC", null, null));

        assertEquals(EventCategory.MUSIC, event.getCategory());
    }

    @Test
    void GivenBothFieldsNull_WhenEditEventDetails_ThenNothingChanges() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);

        eventService.editEventDetails(OWNER_TOKEN,
                new EventUpdateDTO(String.valueOf(EVENT_ID), null, null, null, null, null));

        assertEquals("Concert", event.getName());
        assertEquals(EventCategory.CONCERT, event.getCategory());
    }

    @Test
    void GivenManagerWithPermission_WhenEditEventDetails_ThenSucceeds() {
        when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);

        eventService.editEventDetails(MANAGER_TOKEN,
                new EventUpdateDTO(String.valueOf(EVENT_ID), "Manager Edited", null, null, null, null));

        assertEquals("Manager Edited", event.getName());
    }

    @Test
    void GivenInvalidToken_WhenEditEventDetails_ThenThrows() {
        when(sessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> eventService.editEventDetails(INVALID_TOKEN,
                new EventUpdateDTO(String.valueOf(EVENT_ID), "New Name", null, null, null, null)));
    }

    @Test
    void GivenEventNotFound_WhenEditEventDetails_ThenThrows() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenThrow(new RuntimeException("Event not found"));

        assertThrows(RuntimeException.class, () -> eventService.editEventDetails(OWNER_TOKEN,
                new EventUpdateDTO(String.valueOf(EVENT_ID), "New Name", null, null, null, null)));
    }

    @Test
    void GivenNonNumericEventId_WhenEditEventDetails_ThenThrowsIllegalArgumentException() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);

        assertThrows(IllegalArgumentException.class, () -> eventService.editEventDetails(OWNER_TOKEN,
                new EventUpdateDTO("not-a-number", "New Name", null, null, null, null)));
    }

    @Test
    void GivenUserNotInCompany_WhenEditEventDetails_ThenThrows() {
        User stranger = new User(99, "Stranger", "stranger@test.com", "hashedpw", 25);
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(99);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(userRepository.getUserById(99)).thenReturn(stranger);

        assertThrows(RuntimeException.class, () -> eventService.editEventDetails(OWNER_TOKEN,
                new EventUpdateDTO(String.valueOf(EVENT_ID), "New Name", null, null, null, null)));
    }

    @Test
    void GivenEventOnSale_WhenEditEventDetails_ThenThrows() {
        Event onSaleEvent = new Event(
                EVENT_ID, "Concert", 4.5, List.of("Artist1"),
                EventCategory.CONCERT, COMPANY_ID, EventStatus.ON_SALE, venueMap,
                List.of(new ShowDate(LocalDateTime.now().plusDays(10), LocalDateTime.now().plusDays(10).plusHours(2))),
                null, new DiscountPolicy(0));
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(onSaleEvent);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);

        assertThrows(RuntimeException.class, () -> eventService.editEventDetails(OWNER_TOKEN,
                new EventUpdateDTO(String.valueOf(EVENT_ID), "New Name", null, null, null, null)));
    }

    @Test
    void GivenUnknownCategory_WhenEditEventDetails_ThenThrowsIllegalArgumentException() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);

        assertThrows(IllegalArgumentException.class, () -> eventService.editEventDetails(OWNER_TOKEN,
                new EventUpdateDTO(String.valueOf(EVENT_ID), null, null, "INVALID_CATEGORY", null, null)));
    }

    @Test
    void GivenEventInDraftState_WhenEditEventDetails_ThenSucceeds() {
        Event draftEvent = new Event(
                EVENT_ID, "Draft Concert", 4.5, List.of("Artist1"),
                EventCategory.CONCERT, COMPANY_ID, EventStatus.DRAFT, venueMap,
                List.of(new ShowDate(LocalDateTime.now().plusDays(10), LocalDateTime.now().plusDays(10).plusHours(2))),
                null, new DiscountPolicy(0));
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(draftEvent);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);

        eventService.editEventDetails(OWNER_TOKEN,
                new EventUpdateDTO(String.valueOf(EVENT_ID), "Updated Draft Name", null, null, null, null));

        assertEquals("Updated Draft Name", draftEvent.getName());
    }

    @Test
    void GivenOwnerAndNewDescription_WhenEditEventDetails_ThenDescriptionIsUpdated() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);

        eventService.editEventDetails(OWNER_TOKEN,
                new EventUpdateDTO(String.valueOf(EVENT_ID), null, "A brand new description", null, null, null));

        assertEquals("A brand new description", event.getDescription());
    }

    @Test
    void GivenOwnerAndNewLocation_WhenEditEventDetails_ThenVenueMapLocationIsUpdated() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);

        eventService.editEventDetails(OWNER_TOKEN,
                new EventUpdateDTO(String.valueOf(EVENT_ID), null, null, null,
                        new LocationDTO("France", "Paris"), null));

        assertEquals(new Location("France", "Paris"), event.getVenueMap().getLocation());
    }

    @Test
    void GivenOwnerAndNewShowDates_WhenEditEventDetails_ThenShowDatesAreReplaced() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);

        LocalDateTime newStart = LocalDateTime.now().plusDays(30);
        LocalDateTime newEnd = newStart.plusHours(3);
        eventService.editEventDetails(OWNER_TOKEN,
                new EventUpdateDTO(String.valueOf(EVENT_ID), null, null, null, null,
                        List.of(new ShowDateDTO(newStart, newEnd))));

        assertEquals(1, event.getShowDates().size());
        assertEquals(newStart, event.getShowDates().get(0).getStartTime());
        assertEquals(newEnd, event.getShowDates().get(0).getEndTime());
    }

    @Test
    void GivenDescribedEvent_WhenGetEventDetail_ThenReturnsDescription() {
        Event describedEvent = new Event(
                EVENT_ID, "Concert", "The headline show of the year", 4.5, List.of("Artist1"),
                EventCategory.CONCERT, COMPANY_ID, EventStatus.SCHEDULED, venueMap,
                List.of(new ShowDate(LocalDateTime.now().plusDays(10), LocalDateTime.now().plusDays(10).plusHours(2))),
                null, new DiscountPolicy(0));
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(describedEvent);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);

        EventDetailDTO result = eventService.getEventDetail(OWNER_TOKEN, String.valueOf(EVENT_ID));

        assertEquals("The headline show of the year", result.description());
    }
////////////////////////////////////////////////////// Tests for listEventsForCompany
    @Test
    void GivenOwnerAndCompanyEvents_WhenListEventsForCompany_ThenReturnsMappedEvents() {
    when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
    when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
    when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
    when(mockEventRepo.findByCompanyId(COMPANY_ID)).thenReturn(List.of(event));

    List<EventDetailDTO> result =
            eventService.listEventsForCompany(OWNER_TOKEN, COMPANY_ID);

    assertNotNull(result);
    assertEquals(1, result.size());

    EventDetailDTO dto = result.get(0);

    assertEquals(String.valueOf(EVENT_ID), dto.eventId());
    assertEquals("Concert", dto.name());
    assertEquals(4.5, dto.rating());
    assertEquals(EventCategory.CONCERT, dto.category());
    assertEquals(LOCATION, dto.location());
    assertEquals(String.valueOf(COMPANY_ID), dto.companyId());
    assertEquals(COMPANY_1_NAME, dto.companyName());
    assertEquals(EventStatus.SCHEDULED, dto.status());
    assertEquals(event.getShowDates(), dto.showDates());
}

@Test
void GivenOwnerAndNoEvents_WhenListEventsForCompany_ThenReturnsEmptyList() {
    when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
    when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
    when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
    when(mockEventRepo.findByCompanyId(COMPANY_ID)).thenReturn(List.of());

    List<EventDetailDTO> result =
            eventService.listEventsForCompany(OWNER_TOKEN, COMPANY_ID);

    assertNotNull(result);
    assertTrue(result.isEmpty());
}

@Test
void GivenInvalidToken_WhenListEventsForCompany_ThenThrowsException() {
    when(sessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

    assertThrows(RuntimeException.class,
            () -> eventService.listEventsForCompany(INVALID_TOKEN, COMPANY_ID));
}

@Test
void GivenManagerWithoutManageInventoryPermission_WhenListEventsForCompany_ThenThrowsException() {
    when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
    when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);

    assertThrows(RuntimeException.class,
            () -> eventService.listEventsForCompany(MANAGER_TOKEN, COMPANY_ID));
}

@Test
void GivenOwnerAndEventId_WhenGetEvent_ThenReturnsEventDetails() {
    when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
    when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
    when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
    when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);

    EventDetailDTO dto =
            eventService.getEvent(OWNER_TOKEN, EVENT_ID);

    assertNotNull(dto);
    assertEquals(String.valueOf(EVENT_ID), dto.eventId());
    assertEquals("Concert", dto.name());
    assertEquals(4.5, dto.rating());
    assertEquals(EventCategory.CONCERT, dto.category());
    assertEquals(LOCATION, dto.location());
    assertEquals(String.valueOf(COMPANY_ID), dto.companyId());
    assertEquals(COMPANY_1_NAME, dto.companyName());
    assertEquals(EventStatus.SCHEDULED, dto.status());
    assertEquals(event.getShowDates(), dto.showDates());
}

@Test
void GivenOwnerAndEventWithoutVenueMap_WhenGetEvent_ThenLocationIsNull() {
    Event eventWithoutVenueMap = new Event(
            EVENT_ID,
            "Concert",
            4.5,
            List.of("Artist1"),
            EventCategory.CONCERT,
            COMPANY_ID,
            EventStatus.SCHEDULED,
            null,
            event.getShowDates(),
            null,
            null
    );

    when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
    when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
    when(mockEventRepo.findById(EVENT_ID)).thenReturn(eventWithoutVenueMap);
    when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);

    EventDetailDTO dto =
            eventService.getEvent(OWNER_TOKEN, EVENT_ID);

    assertNotNull(dto);
    assertEquals(String.valueOf(EVENT_ID), dto.eventId());
    assertEquals("Concert", dto.name());
    assertNull(dto.location());
    assertEquals(String.valueOf(COMPANY_ID), dto.companyId());
    assertEquals(COMPANY_1_NAME, dto.companyName());
}

@Test
void GivenInvalidToken_WhenGetEvent_ThenThrowsException() {
    when(sessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

    assertThrows(RuntimeException.class,
            () -> eventService.getEvent(INVALID_TOKEN, EVENT_ID));
}

@Test
void GivenManagerWithoutManageInventoryPermission_WhenGetEvent_ThenThrowsException() {
    when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
    when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);
    when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);

    assertThrows(RuntimeException.class,
            () -> eventService.getEvent(MANAGER_TOKEN, EVENT_ID));
}

@Test
void GivenManagerWithConfigureVenuePermission_WhenGetEventZones_ThenReturnsStandingZone() {
    when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
    when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);
    when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);

    List<ZoneDetailDTO> result =
            eventService.getEventZones(MANAGER_TOKEN, EVENT_ID);

    assertNotNull(result);
    assertEquals(1, result.size());

    ZoneDetailDTO dto = result.get(0);

    assertEquals("VIP", dto.name());
    assertFalse(dto.seated());
    assertEquals(0, dto.rows());
    assertEquals(0, dto.seatsPerRow());
    assertEquals(10, dto.capacity());
    assertEquals(100, dto.price());
}

@Test
void GivenOwner_WhenGetEventZones_ThenReturnsStandingZone() {
    when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
    when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
    when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);

    List<ZoneDetailDTO> result =
            eventService.getEventZones(OWNER_TOKEN, EVENT_ID);

    assertNotNull(result);
    assertEquals(1, result.size());

    ZoneDetailDTO dto = result.get(0);

    assertEquals("VIP", dto.name());
    assertFalse(dto.seated());
    assertEquals(0, dto.rows());
    assertEquals(0, dto.seatsPerRow());
    assertEquals(10, dto.capacity());
    assertEquals(100, dto.price());
}

@Test
void GivenEventWithoutVenueMap_WhenGetEventZones_ThenReturnsEmptyList() {
    Event eventWithoutVenueMap = new Event(
            EVENT_ID,
            "Concert",
            4.5,
            List.of("Artist1"),
            EventCategory.CONCERT,
            COMPANY_ID,
            EventStatus.SCHEDULED,
            null,
            event.getShowDates(),
            null,
            null
    );

    when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
    when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);
    when(mockEventRepo.findById(EVENT_ID)).thenReturn(eventWithoutVenueMap);

    List<ZoneDetailDTO> result =
            eventService.getEventZones(MANAGER_TOKEN, EVENT_ID);

    assertNotNull(result);
    assertTrue(result.isEmpty());
}

@Test
void GivenEventWithEmptyVenueMap_WhenGetEventZones_ThenReturnsEmptyList() {
    VenueMap emptyVenueMap = new VenueMap(1, LOCATION, List.of());

    Event eventWithEmptyVenueMap = new Event(
            EVENT_ID,
            "Concert",
            4.5,
            List.of("Artist1"),
            EventCategory.CONCERT,
            COMPANY_ID,
            EventStatus.SCHEDULED,
            emptyVenueMap,
            event.getShowDates(),
            null,
            null
    );

    when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
    when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);
    when(mockEventRepo.findById(EVENT_ID)).thenReturn(eventWithEmptyVenueMap);

    List<ZoneDetailDTO> result =
            eventService.getEventZones(MANAGER_TOKEN, EVENT_ID);

    assertNotNull(result);
    assertTrue(result.isEmpty());
}

@Test
void GivenSeatedZoneWithSeats_WhenGetEventZones_ThenReturnsRowsAndSeatsPerRow() {
    Seat seatA1 = new Seat("A1", 0, 0);
    Seat seatA2 = new Seat("A2", 1, 0);
    Seat seatB1 = new Seat("B1", 0, 1);
    Seat seatB2 = new Seat("B2", 1, 1);

    SeatedZone seatedZone = new SeatedZone(
            ZONE_ID,
            "Seated VIP",
            250,
            List.of(seatA1, seatA2, seatB1, seatB2)
    );

    VenueMap seatedVenueMap =
            new VenueMap(1, LOCATION, List.of(seatedZone));

    Event seatedEvent = new Event(
            EVENT_ID,
            "Concert",
            4.5,
            List.of("Artist1"),
            EventCategory.CONCERT,
            COMPANY_ID,
            EventStatus.SCHEDULED,
            seatedVenueMap,
            event.getShowDates(),
            null,
            null
    );

    when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
    when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);
    when(mockEventRepo.findById(EVENT_ID)).thenReturn(seatedEvent);

    List<ZoneDetailDTO> result =
            eventService.getEventZones(MANAGER_TOKEN, EVENT_ID);

    assertNotNull(result);
    assertEquals(1, result.size());

    ZoneDetailDTO dto = result.get(0);

    assertEquals("Seated VIP", dto.name());
    assertTrue(dto.seated());
    assertEquals(2, dto.rows());
    assertEquals(2, dto.seatsPerRow());
    assertEquals(0, dto.capacity());
    assertEquals(250, dto.price());
}

@Test
void GivenSeatedZoneWithoutSeats_WhenGetEventZones_ThenRowsAndSeatsPerRowAreZero() {
    SeatedZone seatedZone = new SeatedZone(
            ZONE_ID,
            "Empty Seated Zone",
            150,
            List.of()
    );

    VenueMap seatedVenueMap =
            new VenueMap(1, LOCATION, List.of(seatedZone));

    Event seatedEvent = new Event(
            EVENT_ID,
            "Concert",
            4.5,
            List.of("Artist1"),
            EventCategory.CONCERT,
            COMPANY_ID,
            EventStatus.SCHEDULED,
            seatedVenueMap,
            event.getShowDates(),
            null,
            null
    );

    when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
    when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);
    when(mockEventRepo.findById(EVENT_ID)).thenReturn(seatedEvent);

    List<ZoneDetailDTO> result =
            eventService.getEventZones(MANAGER_TOKEN, EVENT_ID);

    assertNotNull(result);
    assertEquals(1, result.size());

    ZoneDetailDTO dto = result.get(0);

    assertEquals("Empty Seated Zone", dto.name());
    assertTrue(dto.seated());
    assertEquals(0, dto.rows());
    assertEquals(0, dto.seatsPerRow());
    assertEquals(0, dto.capacity());
    assertEquals(150, dto.price());
}

@Test
void GivenMixedStandingAndSeatedZones_WhenGetEventZones_ThenReturnsAllZones() {
    Seat seatA1 = new Seat("A1", 0, 0);
    Seat seatA2 = new Seat("A2", 1, 0);

    SeatedZone seatedZone = new SeatedZone(
            6,
            "Regular Seats",
            200,
            List.of(seatA1, seatA2)
    );

    StandingZone standingZone = new StandingZone(
            7,
            "Standing Area",
            300,
            80
    );

    VenueMap mixedVenueMap =
            new VenueMap(1, LOCATION, List.of(seatedZone, standingZone));

    Event mixedEvent = new Event(
            EVENT_ID,
            "Concert",
            4.5,
            List.of("Artist1"),
            EventCategory.CONCERT,
            COMPANY_ID,
            EventStatus.SCHEDULED,
            mixedVenueMap,
            event.getShowDates(),
            null,
            null
    );

    when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
    when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);
    when(mockEventRepo.findById(EVENT_ID)).thenReturn(mixedEvent);

    List<ZoneDetailDTO> result =
            eventService.getEventZones(MANAGER_TOKEN, EVENT_ID);

    assertNotNull(result);
    assertEquals(2, result.size());

    ZoneDetailDTO seatedDto = result.get(0);

    assertEquals("Regular Seats", seatedDto.name());
    assertTrue(seatedDto.seated());
    assertEquals(1, seatedDto.rows());
    assertEquals(2, seatedDto.seatsPerRow());
    assertEquals(0, seatedDto.capacity());
    assertEquals(200, seatedDto.price());

    ZoneDetailDTO standingDto = result.get(1);

    assertEquals("Standing Area", standingDto.name());
    assertFalse(standingDto.seated());
    assertEquals(0, standingDto.rows());
    assertEquals(0, standingDto.seatsPerRow());
    assertEquals(300, standingDto.capacity());
    assertEquals(80, standingDto.price());
}

@Test
void GivenInvalidToken_WhenGetEventZones_ThenThrowsException() {
    when(sessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

    assertThrows(RuntimeException.class,
            () -> eventService.getEventZones(INVALID_TOKEN, EVENT_ID));
}

@Test
void GivenValidTokenButUserNotFound_WhenListEventsForCompany_ThenThrowsException() {
    when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
    when(userRepository.getUserById(OWNER_ID)).thenReturn(null);

    assertThrows(RuntimeException.class,
            () -> eventService.listEventsForCompany(OWNER_TOKEN, COMPANY_ID));
}

@Test
void GivenCompanyNotFound_WhenListEventsForCompany_ThenThrowsException() {
    when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
    when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
    when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(null);
    when(mockEventRepo.findByCompanyId(COMPANY_ID)).thenReturn(List.of(event));

    assertThrows(RuntimeException.class,
            () -> eventService.listEventsForCompany(OWNER_TOKEN, COMPANY_ID));
}


/////////////////////////////////////Tests for getEvent
@Test
void GivenEventNotFound_WhenGetEvent_ThenThrowsException() {
    when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
    when(mockEventRepo.findById(EVENT_ID)).thenReturn(null);

    assertThrows(RuntimeException.class,
            () -> eventService.getEvent(OWNER_TOKEN, EVENT_ID));
}

@Test
void GivenCompanyNotFound_WhenGetEvent_ThenThrowsException() {
    when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
    when(userRepository.getUserById(OWNER_ID)).thenReturn(ownerUser);
    when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
    when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(null);

    assertThrows(RuntimeException.class,
            () -> eventService.getEvent(OWNER_TOKEN, EVENT_ID));
}

@Test
void GivenValidTokenButUserNotFound_WhenGetEvent_ThenThrowsException() {
    when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
    when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
    when(userRepository.getUserById(OWNER_ID)).thenReturn(null);

    assertThrows(RuntimeException.class,
            () -> eventService.getEvent(OWNER_TOKEN, EVENT_ID));
}


////////////////////////////////////////Tests for getEventZones
@Test
void GivenEventNotFound_WhenGetEventZones_ThenThrowsException() {
    when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
    when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);
    when(mockEventRepo.findById(EVENT_ID)).thenReturn(null);

    assertThrows(RuntimeException.class,
            () -> eventService.getEventZones(MANAGER_TOKEN, EVENT_ID));
}

@Test
void GivenValidTokenButUserNotFound_WhenGetEventZones_ThenThrowsException() {
    when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
    when(userRepository.getUserById(MANAGER_ID)).thenReturn(null);
    when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);

    assertThrows(RuntimeException.class,
            () -> eventService.getEventZones(MANAGER_TOKEN, EVENT_ID));
}

@Test
void GivenManagerWithoutConfigureVenuePermission_WhenGetEventZones_ThenThrowsException() {
    String LIMITED_MANAGER_TOKEN = "limited-manager-token";
    int LIMITED_MANAGER_ID = 3;

    User limitedManager = new User(
            LIMITED_MANAGER_ID,
            "Limited Manager",
            "limited@example.com",
            "hashedpassword",
            30
    );

    limitedManager.receiveManagerAppointment(
            COMPANY_ID,
            OWNER_ID,
            List.of(Permission.MANAGE_INVENTORY)
    );
    limitedManager.acceptInvitation(COMPANY_ID);

    when(sessionManager.validateToken(LIMITED_MANAGER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(LIMITED_MANAGER_TOKEN)).thenReturn(LIMITED_MANAGER_ID);
    when(userRepository.getUserById(LIMITED_MANAGER_ID)).thenReturn(limitedManager);
    when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);

    assertThrows(RuntimeException.class,
            () -> eventService.getEventZones(LIMITED_MANAGER_TOKEN, EVENT_ID));
}




@Test
void GivenSeatedZoneWithUnevenRows_WhenGetEventZones_ThenUsesIntegerDivisionForSeatsPerRow() {
    Seat seatA1 = new Seat("A1", 0, 0);
    Seat seatA2 = new Seat("A2", 1, 0);
    Seat seatA3 = new Seat("A3", 2, 0);
    Seat seatB1 = new Seat("B1", 0, 1);

    SeatedZone seatedZone = new SeatedZone(
            ZONE_ID,
            "Uneven Seated Zone",
            250,
            List.of(seatA1, seatA2, seatA3, seatB1)
    );

    VenueMap seatedVenueMap =
            new VenueMap(1, LOCATION, List.of(seatedZone));

    Event seatedEvent = new Event(
            EVENT_ID,
            "Concert",
            4.5,
            List.of("Artist1"),
            EventCategory.CONCERT,
            COMPANY_ID,
            EventStatus.SCHEDULED,
            seatedVenueMap,
            event.getShowDates(),
            null,
            null
    );

    when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
    when(userRepository.getUserById(MANAGER_ID)).thenReturn(managerUser);
    when(mockEventRepo.findById(EVENT_ID)).thenReturn(seatedEvent);

    List<ZoneDetailDTO> result =
            eventService.getEventZones(MANAGER_TOKEN, EVENT_ID);

    assertNotNull(result);
    assertEquals(1, result.size());

    ZoneDetailDTO dto = result.get(0);

    assertEquals("Uneven Seated Zone", dto.name());
    assertTrue(dto.seated());
    assertEquals(2, dto.rows());
    assertEquals(2, dto.seatsPerRow());
    assertEquals(0, dto.capacity());
    assertEquals(250, dto.price());
}


}
