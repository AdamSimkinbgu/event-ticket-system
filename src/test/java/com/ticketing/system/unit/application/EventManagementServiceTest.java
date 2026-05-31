package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Application.interfaces.IPaymentGateway;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;
import com.ticketing.system.Core.Domain.Tickets.TicketStatus;
import com.ticketing.system.Core.Domain.company.CompanyStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventorySelection;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import com.ticketing.system.Core.Domain.events.StandingZone;
import com.ticketing.system.Core.Domain.events.Location;
import com.ticketing.system.Core.Domain.events.VenueMap;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;
import com.ticketing.system.Core.Domain.orders.ReceiptLine;
import com.ticketing.system.Core.Domain.events.EventCategory;
import com.ticketing.system.Core.Domain.users.Permission;

class EventManagementServiceTest {

    private IEventRepository mockEventRepo;
    private IProductionCompanyRepository mockCompanyRepo;
    private ITicketRepository mockTicketRepo;
    private ISessionManager sessionManager;
    private EventManagementService eventService;
    private IOrderReceiptRepository orderReceiptRepository;
    private IPaymentGateway paymentGateway;

    private final String OWNER_TOKEN = "owner-token";
    private final String MANAGER_TOKEN = "manager-token";
    private final String INVALID_TOKEN = "invalid-token";

    private final int COMPANY_ID = 100;
    private final int OTHER_COMPANY_ID = 200;
    private final int EVENT_ID = 10;
    private final int OWNER_ID = 1;
    private final int MANAGER_ID = 2;
    private final int ZONE_ID = 5;
    private final String COMPANY_1_NAME = "Company1";
    private final String COMPANY_1_DESCRIPTION = "A test production company1";
    private final Location LOCATION = new Location("Belgium", "Brussels");


    private ProductionCompany company;
    private InventoryZone zone;
    private VenueMap venueMap;
    private Event event;

    @BeforeEach
    public void setUp() {
        mockEventRepo = mock(IEventRepository.class);
        mockCompanyRepo = mock(IProductionCompanyRepository.class);
        mockTicketRepo = mock(ITicketRepository.class);
        sessionManager = mock(ISessionManager.class);
        orderReceiptRepository = mock(IOrderReceiptRepository.class);
        paymentGateway = mock(IPaymentGateway.class);

        eventService = new EventManagementService(
                mockEventRepo,
                mockCompanyRepo,
                mockTicketRepo,
                sessionManager,
                orderReceiptRepository,
                paymentGateway
        );

        company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
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
                null
        );
    }


    @Test
    public void GivenOwner_WhenUpdateZoneCapacity_ThenZoneCapacityUpdated() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);

        eventService.updateStandingZoneCapacity(
                OWNER_TOKEN,
                COMPANY_ID,
                EVENT_ID,
                ZONE_ID,
                20
        );

        assertEquals(20, zone.getAvailableAmount());
    }

       @Test
    public void GivenManagerWithConfigureVenuePermission_WhenUpdateZoneCapacity_ThenZoneCapacityUpdated() {
        company.validateManagerInvitation(
                COMPANY_ID,
                MANAGER_ID,
                OWNER_ID,
                List.of(Permission.CONFIGURE_VENUE)
        );
        company.acceptManagerInvitation(MANAGER_ID);

        when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);

        eventService.updateStandingZoneCapacity(
                MANAGER_TOKEN,
                COMPANY_ID,
                EVENT_ID,
                ZONE_ID,
                20
        );

        assertEquals(20, zone.getAvailableAmount());
    }

    @Test
    public void GivenInvalidToken_WhenUpdateZoneCapacity_ThenThrowException() {
        when(sessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

        assertThrows(RuntimeException.class, () ->
                eventService.updateStandingZoneCapacity(
                        INVALID_TOKEN,
                        COMPANY_ID,
                        EVENT_ID,
                        ZONE_ID,
                        20
                )
        );
    }

    @Test
    public void GivenCompanyDoesNotExist_WhenUpdateZoneCapacity_ThenThrowException() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(null);

        assertThrows(RuntimeException.class, () ->
                eventService.updateStandingZoneCapacity(
                        OWNER_TOKEN,
                        COMPANY_ID,
                        EVENT_ID,
                        ZONE_ID,
                        20
                )
        );
    }

    @Test
    public void GivenEventDoesNotExist_WhenUpdateZoneCapacity_ThenThrowException() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(null);

        assertThrows(RuntimeException.class, () ->
                eventService.updateStandingZoneCapacity(
                        OWNER_TOKEN,
                        COMPANY_ID,
                        EVENT_ID,
                        ZONE_ID,
                        20
                )
        );
    }

    @Test
    public void GivenUserIsNotOwnerOrManager_WhenUpdateZoneCapacity_ThenThrowException() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(99);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);

        assertThrows(RuntimeException.class, () ->
                eventService.updateStandingZoneCapacity(
                        OWNER_TOKEN,
                        COMPANY_ID,
                        EVENT_ID,
                        ZONE_ID,
                        20
                )
        );
    }

    @Test
    public void GivenManagerWithoutConfigureVenuePermission_WhenUpdateZoneCapacity_ThenThrowException() {
        company.validateManagerInvitation(
                COMPANY_ID,
                MANAGER_ID,
                OWNER_ID,
                List.of(Permission.MANAGE_INVENTORY)
        );
        company.acceptManagerInvitation(MANAGER_ID);

        when(sessionManager.validateToken(MANAGER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(MANAGER_TOKEN)).thenReturn(MANAGER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);

        assertThrows(RuntimeException.class, () ->
                eventService.updateStandingZoneCapacity(
                        MANAGER_TOKEN,
                        COMPANY_ID,
                        EVENT_ID,
                        ZONE_ID,
                        20
                )
        );
    }

    @Test
    public void GivenZoneDoesNotExist_WhenUpdateZoneCapacity_ThenThrowException() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);

        assertThrows(IllegalArgumentException.class, () ->
                eventService.updateStandingZoneCapacity(
                        OWNER_TOKEN,
                        COMPANY_ID,
                        EVENT_ID,
                        999,
                        20
                )
        );
    }

    @Test
    public void GivenReservedTicketsMoreThanNewCapacity_WhenUpdateZoneCapacity_ThenThrowException() {
        zone.reserve(InventorySelection.standing(8));

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);

        assertThrows(IllegalArgumentException.class, () ->
                eventService.updateStandingZoneCapacity(
                        OWNER_TOKEN,
                        COMPANY_ID,
                        EVENT_ID,
                        ZONE_ID,
                        5
                )
        );
    }


    private OrderReceipt setupStateBasedHappyPath() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        
        ReceiptLine line = new ReceiptLine(1, 100.0, EVENT_ID, 1, "A1", java.time.LocalDateTime.now());
        OrderReceipt realReceipt = OrderReceipt.forMember(99, 100.0, List.of(line));
        
        when(orderReceiptRepository.findByEventId(EVENT_ID))
            .thenReturn(List.of(realReceipt));
            
       

        return realReceipt;
    }

    @Test
    public void GivenInvalidToken_WhenCancelEventAndRefund_ThenThrowException() {
        when(sessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

        assertThrows(RuntimeException.class, () ->
                eventService.cancelEventAndRefund(INVALID_TOKEN, EVENT_ID)
        );
    }

    @Test
    public void GivenEventDoesNotExist_WhenCancelEventAndRefund_ThenThrowException() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(null);

        assertThrows(RuntimeException.class, () ->
                eventService.cancelEventAndRefund(OWNER_TOKEN, EVENT_ID)
        );
    }

    @Test
    public void GivenCompanyDoesNotExist_WhenCancelEventAndRefund_ThenThrowException() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(null);

        assertThrows(RuntimeException.class, () ->
                eventService.cancelEventAndRefund(OWNER_TOKEN, EVENT_ID)
        );
    }

    @Test
    public void GivenEventAlreadyCanceled_WhenCancelEventAndRefund_ThenReceiptStateRemainsUnchanged() {
        OrderReceipt receipt = setupStateBasedHappyPath();
        event.setCanceled(true); // Manually cancel it beforehand

        eventService.cancelEventAndRefund(OWNER_TOKEN, EVENT_ID);

        assertEquals(false, receipt.wasRefunded());
    }

    @Test
    public void GivenValidRequest_WhenCancelEventAndRefund_ThenEventStateIsCanceled() {
        setupStateBasedHappyPath();

        eventService.cancelEventAndRefund(OWNER_TOKEN, EVENT_ID);

        assertTrue(event.isCancelled());
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
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(orderReceiptRepository.findByEventId(EVENT_ID)).thenReturn(List.of());

        Ticket paidTicket = new Ticket(EVENT_ID, ZONE_ID, 100.0, 1, "BARCODE123");
        
        when(mockTicketRepo.findByEventId(String.valueOf(EVENT_ID))).thenReturn(List.of(paidTicket));

        eventService.cancelEventAndRefund(OWNER_TOKEN, EVENT_ID);

        assertEquals(TicketStatus.REFUNDED, paidTicket.getStatus());
    }


@Test
    public void GivenIssuedTicket_WhenCancelEventAndRefund_ThenTicketStatusIsMarkedRefunded() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(orderReceiptRepository.findByEventId(EVENT_ID)).thenReturn(List.of());

        Ticket issuedTicket = new Ticket(EVENT_ID, ZONE_ID, 100.0, 1, "BARCODE123");
        
        issuedTicket.markIssued("BARCODE123"); 
        
        when(mockTicketRepo.findByEventId(String.valueOf(EVENT_ID))).thenReturn(List.of(issuedTicket));

        eventService.cancelEventAndRefund(OWNER_TOKEN, EVENT_ID);

        assertEquals(TicketStatus.REFUNDED, issuedTicket.getStatus());
    }

    @Test
    public void GivenAvailableTicket_WhenCancelEventAndRefund_ThenTicketStatusRemainsUnchanged() {
        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(orderReceiptRepository.findByEventId(EVENT_ID)).thenReturn(List.of());

        Ticket availableTicket = 
            new Ticket(EVENT_ID, ZONE_ID, 100.0, 1, "BARCODE123");
        
        availableTicket.release(); 
        
        when(mockTicketRepo.findByEventId(String.valueOf(EVENT_ID))).thenReturn(List.of(availableTicket));

        eventService.cancelEventAndRefund(OWNER_TOKEN, EVENT_ID);

        assertEquals(TicketStatus.VOIDED, availableTicket.getStatus());
    }

    @Test @Disabled("UC-19: Owner adds event — DRAFT state initially")
    void givenOwner_whenAddEvent_thenEventInDraft() {}

    @Test @Disabled("UC-19: Manager without permission rejected")
    void givenManagerWithoutPermission_whenAddEvent_thenRejected() {}

    

    @Test @Disabled("UC-21: setEventPolicies stores PurchasePolicy + DiscountPolicy")
    void givenOwner_whenSetEventPolicies_thenStored() {}
}
