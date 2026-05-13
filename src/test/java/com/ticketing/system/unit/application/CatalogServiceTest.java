package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Application.dto.VenueMapDTO;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.services.CatalogService;
import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import com.ticketing.system.Core.Domain.events.VenueMap;
import com.ticketing.system.Core.Domain.exceptions.EventNotFoundException;
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

    @Test @Disabled("UC-7: searchGlobal applies all filters (price/date/location/rating)")
    void givenFilters_whenSearchGlobal_thenFiltered() {}

    @Test @Disabled("UC-7: searchByCompany excludes the rating filter (II.2.3.2)")
    void givenCompanyScope_whenSearch_thenRatingNotApplied() {}


    // UC-8: getEventVenueMap returns correct venue map for event
    @Test
    void givenValidTokenAndEventWithVenueMap_whenGetEventVenueMap_thenReturnsCorrectVenueMapDTO() {
        InventoryZone zone = new InventoryZone(10, "Floor", 200, 50);
        VenueMap venueMap = new VenueMap(5, List.of(zone));
        Event mockEvent = mock(Event.class);
        when(mockEvent.getVenueMap()).thenReturn(venueMap);

        when(mockSessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockEventRepository.findById(EVENT_ID)).thenReturn(mockEvent);

        VenueMapDTO result = catalogService.getEventVenueMap(VALID_TOKEN, EVENT_ID);

        assertNotNull(result);
        assertEquals(5, result.getId());
        assertEquals(1, result.getInventoryZones().size());
        assertEquals(10, result.getInventoryZones().get(0).getId());
        assertEquals("Floor", result.getInventoryZones().get(0).getName());
        assertEquals(200, result.getInventoryZones().get(0).getCapacity());
        assertEquals(50, result.getInventoryZones().get(0).getPrice());
    }

    // UC-8: getEventVenueMap throws SessionExpiredException when token is expired
    @Test
    void givenExpiredToken_whenGetEventVenueMap_thenThrowsSessionExpiredException() {
        when(mockSessionManager.validateToken(VALID_TOKEN)).thenThrow(new SessionExpiredException());

        assertThrows(SessionExpiredException.class,
                () -> catalogService.getEventVenueMap(VALID_TOKEN, EVENT_ID));
    }

    // UC-8: getEventVenueMap throws EventNotFoundException when event does not exist
    @Test
    void givenValidTokenAndNonExistentEvent_whenGetEventVenueMap_thenThrowsEventNotFoundException() {
        when(mockSessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockEventRepository.findById(EVENT_ID)).thenReturn(null);

        assertThrows(EventNotFoundException.class,
                () -> catalogService.getEventVenueMap(VALID_TOKEN, EVENT_ID));
    }

    // UC-8: getEventVenueMap throws NullVenueMapException when event has no venue map
    @Test
    void givenValidTokenAndEventWithNullVenueMap_whenGetEventVenueMap_thenThrowsNullVenueMapException() {
        Event mockEvent = mock(Event.class);
        when(mockEvent.getVenueMap()).thenReturn(null);

        when(mockSessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockEventRepository.findById(EVENT_ID)).thenReturn(mockEvent);

        assertThrows(NullVenueMapException.class,
                () -> catalogService.getEventVenueMap(VALID_TOKEN, EVENT_ID));
    }
}
