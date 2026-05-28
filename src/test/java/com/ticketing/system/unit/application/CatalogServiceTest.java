package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Application.dto.CatalogSearchFiltersDTO;
import com.ticketing.system.Core.Application.dto.EventSummaryDTO;
import com.ticketing.system.Core.Application.dto.VenueMapDTO;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.services.CatalogService;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.company.CompanyStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventCategory;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import com.ticketing.system.Core.Domain.events.StandingZone;
import com.ticketing.system.Core.Domain.events.Location;
import com.ticketing.system.Core.Domain.events.VenueMap;
import com.ticketing.system.Core.Domain.exceptions.EventNotFoundException;
import com.ticketing.system.Core.Domain.exceptions.InvalidTokenException;
import com.ticketing.system.Core.Domain.exceptions.NullVenueMapException;
import com.ticketing.system.Core.Domain.exceptions.SessionExpiredException;

class CatalogServiceTest {

    private ISessionManager mockSessionManager;
    private IEventRepository mockEventRepository;
    private IProductionCompanyRepository mockCompanyRepository;
    private ITicketRepository mockTicketRepository;
    private CatalogService catalogService;

    private static final String VALID_TOKEN = "valid-token";
    private static final int EVENT_ID = 1;
    private static final Location LOCATION = new Location("Belgium", "Brussels");

    @BeforeEach
    void setUp() {
        mockSessionManager = mock(ISessionManager.class);
        mockEventRepository = mock(IEventRepository.class);
        mockCompanyRepository = mock(IProductionCompanyRepository.class);
        mockTicketRepository = mock(ITicketRepository.class);
        catalogService = new CatalogService(
                mockSessionManager,
                mockEventRepository,
                mockCompanyRepository,
                mockTicketRepository
        );
    }

    @Test @Disabled("UC-3: browseEventCatalog returns active events from active companies")
    void givenActiveCompanies_whenBrowse_thenReturnsActiveEvents() {}

    @Test @Disabled("UC-3: events from closed companies excluded")
    void givenClosedCompany_whenBrowse_thenEventsExcluded() {}

    // -------------------------------------------------------------------------
    // UC-7: searchGlobal
    // -------------------------------------------------------------------------

    // UC-7: searchGlobal throws InvalidTokenException for an invalid token
    @Test
    void givenInvalidToken_whenSearchGlobal_thenThrowsInvalidTokenException() {
        when(mockSessionManager.validateToken(VALID_TOKEN)).thenReturn(false);
        CatalogSearchFiltersDTO filters = emptyFilters();

        assertThrows(InvalidTokenException.class,
                () -> catalogService.searchGlobal(VALID_TOKEN, filters));
    }

    // UC-7: searchGlobal propagates SessionExpiredException from the session manager
    @Test
    void givenExpiredToken_whenSearchGlobal_thenThrowsSessionExpiredException() {
        when(mockSessionManager.validateToken(VALID_TOKEN)).thenThrow(new SessionExpiredException());
        CatalogSearchFiltersDTO filters = emptyFilters();

        assertThrows(SessionExpiredException.class,
                () -> catalogService.searchGlobal(VALID_TOKEN, filters));
    }

    // UC-7: searchGlobal returns all matching events when no company-rating filters are set
    @Test
    void givenValidTokenAndNoFilters_whenSearchGlobal_thenReturnsAllMatchingEvents() {
        CatalogSearchFiltersDTO filters = emptyFilters();
        Event event1 = createMockEvent(1, 10);
        Event event2 = createMockEvent(2, 20);
        ProductionCompany company1 = createMockCompany("Company A", 4.5);
        ProductionCompany company2 = createMockCompany("Company B", 3.0);

        when(mockSessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockEventRepository.search(filters)).thenReturn(List.of(event1, event2));
        when(mockCompanyRepository.getCompanyById(10)).thenReturn(company1);
        when(mockCompanyRepository.getCompanyById(20)).thenReturn(company2);

        List<EventSummaryDTO> results = catalogService.searchGlobal(VALID_TOKEN, filters);

        assertEquals(2, results.size());
    }

    // UC-7: searchGlobal excludes events whose company rating is below minCompanyRating
    @Test
    void givenMinCompanyRatingFilter_whenSearchGlobal_thenExcludesLowRatedCompanies() {
        CatalogSearchFiltersDTO filters = new CatalogSearchFiltersDTO(
                null, null, null, null, null, null, null, null, null, null, null, 3.5, null);
        Event event1 = createMockEvent(1, 10);
        Event event2 = createMockEvent(2, 20);
        ProductionCompany highRated = createMockCompany("Company A", 4.5);
        ProductionCompany lowRated  = createMockCompany("Company B", 2.0);

        when(mockSessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockEventRepository.search(filters)).thenReturn(List.of(event1, event2));
        when(mockCompanyRepository.getCompanyById(10)).thenReturn(highRated);
        when(mockCompanyRepository.getCompanyById(20)).thenReturn(lowRated);

        List<EventSummaryDTO> results = catalogService.searchGlobal(VALID_TOKEN, filters);

        assertEquals(1, results.size());
        assertEquals("Event 1", results.get(0).name());
    }

    // UC-7: searchGlobal excludes events whose company rating is above maxCompanyRating
    @Test
    void givenMaxCompanyRatingFilter_whenSearchGlobal_thenExcludesHighRatedCompanies() {
        CatalogSearchFiltersDTO filters = new CatalogSearchFiltersDTO(
                null, null, null, null, null, null, null, null, null, null, null, null, 3.0);
        Event event1 = createMockEvent(1, 10);
        Event event2 = createMockEvent(2, 20);
        ProductionCompany highRated = createMockCompany("Company A", 4.5);
        ProductionCompany lowRated  = createMockCompany("Company B", 2.0);

        when(mockSessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockEventRepository.search(filters)).thenReturn(List.of(event1, event2));
        when(mockCompanyRepository.getCompanyById(10)).thenReturn(highRated);
        when(mockCompanyRepository.getCompanyById(20)).thenReturn(lowRated);

        List<EventSummaryDTO> results = catalogService.searchGlobal(VALID_TOKEN, filters);

        assertEquals(1, results.size());
        assertEquals("Event 2", results.get(0).name());
    }

    // UC-7: searchGlobal returns an empty list when the repository returns no events
    @Test
    void givenValidTokenAndEmptyRepository_whenSearchGlobal_thenReturnsEmptyList() {
        CatalogSearchFiltersDTO filters = emptyFilters();
        when(mockSessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockEventRepository.search(filters)).thenReturn(List.of());

        List<EventSummaryDTO> results = catalogService.searchGlobal(VALID_TOKEN, filters);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    // -------------------------------------------------------------------------
    // UC-7: searchByCompany
    // -------------------------------------------------------------------------

    // UC-7: searchByCompany throws InvalidTokenException for an invalid token
    @Test
    void givenInvalidToken_whenSearchByCompany_thenThrowsInvalidTokenException() {
        when(mockSessionManager.validateToken(VALID_TOKEN)).thenReturn(false);
        CatalogSearchFiltersDTO filters = emptyFilters();

        assertThrows(InvalidTokenException.class,
                () -> catalogService.searchByCompany(VALID_TOKEN, 10, filters));
    }

    // UC-7: searchByCompany propagates SessionExpiredException from the session manager
    @Test
    void givenExpiredToken_whenSearchByCompany_thenThrowsSessionExpiredException() {
        when(mockSessionManager.validateToken(VALID_TOKEN)).thenThrow(new SessionExpiredException());
        CatalogSearchFiltersDTO filters = emptyFilters();

        assertThrows(SessionExpiredException.class,
                () -> catalogService.searchByCompany(VALID_TOKEN, 10, filters));
    }

    // UC-7: searchByCompany returns only events belonging to the requested company
    @Test
    void givenValidTokenAndCompanyId_whenSearchByCompany_thenReturnsOnlyThatCompanysEvents() {
        CatalogSearchFiltersDTO filters = emptyFilters();
        Event event1 = createMockEvent(1, 10);
        Event event2 = createMockEvent(2, 20);
        ProductionCompany company = createMockCompany("Company A", 4.5);

        when(mockSessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockEventRepository.search(filters)).thenReturn(List.of(event1, event2));
        when(mockCompanyRepository.getCompanyById(10)).thenReturn(company);

        List<EventSummaryDTO> results = catalogService.searchByCompany(VALID_TOKEN, 10, filters);

        assertEquals(1, results.size());
        assertEquals("Event 1", results.get(0).name());
    }

    // UC-7: searchByCompany does NOT apply the company-rating filter (II.2.3.2)
    @Test
    void givenCompanyRatingFilter_whenSearchByCompany_thenCompanyRatingFilterNotApplied() {
        // minCompanyRating=4.0 but the company rating is only 1.5 — must still be returned
        CatalogSearchFiltersDTO filters = new CatalogSearchFiltersDTO(
                null, null, null, null, null, null, null, null, null, null, null, 4.0, null);
        Event event1 = createMockEvent(1, 10);
        ProductionCompany lowRated = createMockCompany("Company A", 1.5);

        when(mockSessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockEventRepository.search(filters)).thenReturn(List.of(event1));
        when(mockCompanyRepository.getCompanyById(10)).thenReturn(lowRated);

        List<EventSummaryDTO> results = catalogService.searchByCompany(VALID_TOKEN, 10, filters);

        assertEquals(1, results.size());
    }

    // UC-7: searchByCompany returns an empty list when no events match the given company id
    @Test
    void givenValidTokenAndNoMatchingCompanyEvents_whenSearchByCompany_thenReturnsEmptyList() {
        CatalogSearchFiltersDTO filters = emptyFilters();
        Event event1 = createMockEvent(1, 20); // belongs to a different company

        when(mockSessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockEventRepository.search(filters)).thenReturn(List.of(event1));

        List<EventSummaryDTO> results = catalogService.searchByCompany(VALID_TOKEN, 10, filters);

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }


    // UC-8: getEventVenueMap returns correct venue map for event
    @Test
    void givenValidTokenAndEventWithVenueMap_whenGetEventVenueMap_thenReturnsCorrectVenueMapDTO() {
        InventoryZone zone = new StandingZone(10, "Floor", 200, 50);
        VenueMap venueMap = new VenueMap(5, LOCATION, List.of(zone));
        Event mockEvent = mock(Event.class);
        when(mockEvent.getVenueMap()).thenReturn(venueMap);
        when(mockEvent.getCompanyId()).thenReturn(10);

        ProductionCompany mockCompany = mock(ProductionCompany.class);
        when(mockCompany.getStatus()).thenReturn(CompanyStatus.ACTIVE);
        when(mockCompanyRepository.getCompanyById(10)).thenReturn(mockCompany);

        when(mockSessionManager.validateCredential(VALID_TOKEN)).thenReturn(true);
        when(mockEventRepository.findById(EVENT_ID)).thenReturn(mockEvent);

        VenueMapDTO result = catalogService.getEventVenueMap(VALID_TOKEN, EVENT_ID);

        assertNotNull(result);
        assertEquals(5, result.eventId());
        assertEquals(1, result.inventoryZones().size());
        assertEquals(10, result.inventoryZones().get(0).getId());
        assertEquals("Floor", result.inventoryZones().get(0).getName());
        assertEquals(200, result.inventoryZones().get(0).getCapacity());
        assertEquals(50, result.inventoryZones().get(0).getPrice());
    }

    // UC-8: getEventVenueMap throws InvalidTokenException when credential is invalid
    // (including expired). Phase 4.3 of the auth rework unified the rejection path —
    // validateCredential collapses "expired" and "invalid" into a single false return,
    // so Catalog can no longer distinguish the two. Callers who need that distinction
    // should call AuthenticationService directly.
    @Test
    void givenInvalidOrExpiredCredential_whenGetEventVenueMap_thenThrowsInvalidTokenException() {
        when(mockSessionManager.validateCredential(VALID_TOKEN)).thenReturn(false);

        assertThrows(InvalidTokenException.class,
                () -> catalogService.getEventVenueMap(VALID_TOKEN, EVENT_ID));
    }

    // UC-8: getEventVenueMap throws EventNotFoundException when event does not exist
    @Test
    void givenValidTokenAndNonExistentEvent_whenGetEventVenueMap_thenThrowsEventNotFoundException() {
        when(mockSessionManager.validateCredential(VALID_TOKEN)).thenReturn(true);
        when(mockEventRepository.findById(EVENT_ID)).thenReturn(null);

        assertThrows(EventNotFoundException.class,
                () -> catalogService.getEventVenueMap(VALID_TOKEN, EVENT_ID));
    }

    // UC-8: getEventVenueMap throws NullVenueMapException when event has no venue map
    @Test
    void givenValidTokenAndEventWithNullVenueMap_whenGetEventVenueMap_thenThrowsNullVenueMapException() {
        Event mockEvent = mock(Event.class);
        when(mockEvent.getVenueMap()).thenReturn(null);
        when(mockEvent.getCompanyId()).thenReturn(10);

        ProductionCompany mockCompany = mock(ProductionCompany.class);
        when(mockCompany.getStatus()).thenReturn(CompanyStatus.ACTIVE);
        when(mockCompanyRepository.getCompanyById(10)).thenReturn(mockCompany);

        when(mockSessionManager.validateCredential(VALID_TOKEN)).thenReturn(true);
        when(mockEventRepository.findById(EVENT_ID)).thenReturn(mockEvent);

        assertThrows(NullVenueMapException.class,
                () -> catalogService.getEventVenueMap(VALID_TOKEN, EVENT_ID));
    }

    // UC-8: mapper preserves all zones and their insertion order for a multi-zone venue map
    @Test
    void givenMultiZoneVenueMap_whenGetEventVenueMap_thenAllZonesReturnedInOrder() {
        InventoryZone zone1 = new StandingZone(1, "Floor",   100, 50);
        InventoryZone zone2 = new StandingZone(2, "Balcony",  80, 30);
        InventoryZone zone3 = new StandingZone(3, "VIP",      20, 150);
        VenueMap venueMap = new VenueMap(5, LOCATION, List.of(zone1, zone2, zone3));

        Event mockEvent = mock(Event.class);
        when(mockEvent.getVenueMap()).thenReturn(venueMap);
        when(mockEvent.getCompanyId()).thenReturn(10);

        ProductionCompany mockCompany = mock(ProductionCompany.class);
        when(mockCompany.getStatus()).thenReturn(CompanyStatus.ACTIVE);
        when(mockCompanyRepository.getCompanyById(10)).thenReturn(mockCompany);

        when(mockSessionManager.validateCredential(VALID_TOKEN)).thenReturn(true);
        when(mockEventRepository.findById(EVENT_ID)).thenReturn(mockEvent);

        VenueMapDTO result = catalogService.getEventVenueMap(VALID_TOKEN, EVENT_ID);

        assertEquals(3, result.inventoryZones().size());
        assertEquals(1, result.inventoryZones().get(0).getId());
        assertEquals(2, result.inventoryZones().get(1).getId());
        assertEquals(3, result.inventoryZones().get(2).getId());
    }

    // UC-8: a venue map with no zones still produces a valid (non-null, empty-list) DTO
    @Test
    void givenZeroZoneVenueMap_whenGetEventVenueMap_thenReturnsDTOWithEmptyZoneList() {
        VenueMap venueMap = new VenueMap(5, LOCATION, List.of());

        Event mockEvent = mock(Event.class);
        when(mockEvent.getVenueMap()).thenReturn(venueMap);
        when(mockEvent.getCompanyId()).thenReturn(10);

        ProductionCompany mockCompany = mock(ProductionCompany.class);
        when(mockCompany.getStatus()).thenReturn(CompanyStatus.ACTIVE);
        when(mockCompanyRepository.getCompanyById(10)).thenReturn(mockCompany);

        when(mockSessionManager.validateCredential(VALID_TOKEN)).thenReturn(true);
        when(mockEventRepository.findById(EVENT_ID)).thenReturn(mockEvent);

        VenueMapDTO result = catalogService.getEventVenueMap(VALID_TOKEN, EVENT_ID);

        assertNotNull(result);
        assertNotNull(result.inventoryZones());
        assertTrue(result.inventoryZones().isEmpty());
    }

    // UC-8: null or blank token must raise InvalidTokenException, not SessionExpiredException
    @Test
    void givenNullToken_whenGetEventVenueMap_thenThrowsInvalidTokenException() {
        when(mockSessionManager.validateCredential(null)).thenReturn(false);

        assertThrows(InvalidTokenException.class,
                () -> catalogService.getEventVenueMap(null, EVENT_ID));
    }

    @Test
    void givenBlankToken_whenGetEventVenueMap_thenThrowsInvalidTokenException() {
        when(mockSessionManager.validateCredential("")).thenReturn(false);

        assertThrows(InvalidTokenException.class,
                () -> catalogService.getEventVenueMap("", EVENT_ID));
    }



    
    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CatalogSearchFiltersDTO emptyFilters() {
        return new CatalogSearchFiltersDTO(
                null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private Event createMockEvent(int id, int companyId) {
        Event mockEvent = mock(Event.class);
        when(mockEvent.getId()).thenReturn(id);
        when(mockEvent.getName()).thenReturn("Event " + id);
        when(mockEvent.getStatus()).thenReturn(EventStatus.ON_SALE);
        when(mockEvent.getRating()).thenReturn(4.0);
        when(mockEvent.getCategory()).thenReturn(EventCategory.MUSIC);
        when(mockEvent.getCompanyId()).thenReturn(companyId);
        InventoryZone zone = new StandingZone(1, "Floor", 100, 50);
        VenueMap venueMap = new VenueMap(id, LOCATION, List.of(zone));
        when(mockEvent.getVenueMap()).thenReturn(venueMap);
        when(mockEvent.getShowDates()).thenReturn(List.of());
        return mockEvent;
    }

    private ProductionCompany createMockCompany(String name, double rating) {
        ProductionCompany mockCompany = mock(ProductionCompany.class);
        when(mockCompany.getName()).thenReturn(name);
        when(mockCompany.getRating()).thenReturn(rating);
        return mockCompany;
    }
}
