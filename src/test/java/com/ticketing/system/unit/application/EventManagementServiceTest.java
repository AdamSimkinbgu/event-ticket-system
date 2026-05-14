package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.company.CompanyStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import com.ticketing.system.Core.Domain.events.VenueMap;
import com.ticketing.system.Core.Domain.events.eventCategory;
import com.ticketing.system.Core.Domain.users.Permission;

class EventManagementServiceTest {

    private IEventRepository mockEventRepo;
    private IProductionCompanyRepository mockCompanyRepo;
    private ITicketRepository mockTicketRepo;
    private ISessionManager sessionManager;
    private EventManagementService eventService;

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

        eventService = new EventManagementService(
                mockEventRepo,
                mockCompanyRepo,
                mockTicketRepo,
                sessionManager
        );

        company = new ProductionCompany(COMPANY_ID, OWNER_ID, COMPANY_1_NAME, CompanyStatus.ACTIVE, COMPANY_1_DESCRIPTION, 4.5);
        zone = new InventoryZone(ZONE_ID, "VIP", 10, 100);
        venueMap = new VenueMap(1, List.of(zone));
        event = new Event(
                EVENT_ID,
                "Concert",
                List.of("Artist1"),
                eventCategory.CONCERT,
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

        eventService.addCapacitoesToVenueMapZone(
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

        eventService.addCapacitoesToVenueMapZone(
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
                eventService.addCapacitoesToVenueMapZone(
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
                eventService.addCapacitoesToVenueMapZone(
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
                eventService.addCapacitoesToVenueMapZone(
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
                eventService.addCapacitoesToVenueMapZone(
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
                eventService.addCapacitoesToVenueMapZone(
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
                eventService.addCapacitoesToVenueMapZone(
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
        zone.reserve(8);

        when(sessionManager.validateToken(OWNER_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(OWNER_TOKEN)).thenReturn(OWNER_ID);
        when(mockCompanyRepo.getCompanyById(COMPANY_ID)).thenReturn(company);
        when(mockEventRepo.findById(EVENT_ID)).thenReturn(event);

        assertThrows(IllegalArgumentException.class, () ->
                eventService.addCapacitoesToVenueMapZone(
                        OWNER_TOKEN,
                        COMPANY_ID,
                        EVENT_ID,
                        ZONE_ID,
                        5
                )
        );
    }










    @Test @Disabled("UC-19: Owner adds event — DRAFT state initially")
    void givenOwner_whenAddEvent_thenEventInDraft() {}

    @Test @Disabled("UC-19: Manager without permission rejected")
    void givenManagerWithoutPermission_whenAddEvent_thenRejected() {}

    

    @Test @Disabled("UC-21: setEventPolicies stores PurchasePolicy + DiscountPolicy")
    void givenOwner_whenSetEventPolicies_thenStored() {}
}
