package com.ticketing.system.unit.infrastructure.persistence.EventPersistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Application.dto.CatalogSearchFiltersDTO;
import com.ticketing.system.Core.Domain.events.DiscountPolicy;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.StandingZone;
import com.ticketing.system.Core.Domain.events.Location;
import com.ticketing.system.Core.Domain.policies.purchase.NoPurchasePolicy;
import com.ticketing.system.Core.Domain.events.ShowDate;
import com.ticketing.system.Core.Domain.events.VenueMap;
import com.ticketing.system.Core.Domain.events.EventCategory;

// Contract tests every IEventRepository implementation must satisfy. Future JPA-backed
// adapter will subclass this with its own newRepository() factory; tests are reused.
public abstract class IEventRepositoryContractTest {

    protected abstract IEventRepository newRepository();

    private IEventRepository eventRepo;

    // Far-future timestamps used across test events (ShowDate enforces future-only
    // times).
    private static final LocalDateTime FUTURE_START = LocalDateTime.of(2099, 6, 1, 18, 0);
    private static final LocalDateTime FUTURE_END = LocalDateTime.of(2099, 6, 1, 22, 0);
    private static final Location LOCATION = new Location("Belgium", "Brussels");

    @BeforeEach
    void setUp() {
        eventRepo = newRepository();
    }

    // Builds a minimal valid Event with specified category and one zone priced at
    // 50.
    protected Event buildEvent(int id, String name, Double rating, int companyId, EventStatus status,
            EventCategory category) {
        VenueMap venueMap = new VenueMap(id, LOCATION, List.of(new StandingZone(1, "Floor", 100, 50)));
        ShowDate showDate = new ShowDate(FUTURE_START, FUTURE_END);
        return new Event(id, name, rating, List.of("Artist A"), category, companyId, status,
                venueMap, List.of(showDate), new NoPurchasePolicy(), new DiscountPolicy(0));
    }

    // === save ===

    @Test
    void WhenSave_GivenValidEvent_returnsTheSavedEvent() {
        Event event = buildEvent(1, "Rock Night", 4.5, 10, EventStatus.ON_SALE, EventCategory.CONCERT);
        assertTrue(eventRepo.save(event));
    }

    // === findById ===

    @Test
    void givenSavedEvent_whenFindById_thenReturnsEvent() {
        eventRepo.save(buildEvent(1, "Rock Night", 4.5, 10, EventStatus.ON_SALE, EventCategory.CONCERT));

        Event found = eventRepo.findById(1);

        assertNotNull(found);
        assertEquals(1, found.getId());
        assertEquals("Rock Night", found.getName());
    }

    // === findByCompanyId ===

    @Test
    void givenEventsForMultipleCompanies_whenFindByCompanyId_thenReturnsOnlyMatchingCompanyEvents() {
        eventRepo.save(buildEvent(1, "Event A", 4.5, 10, EventStatus.ON_SALE, EventCategory.CONCERT));
        eventRepo.save(buildEvent(2, "Event B", 3.8, 10, EventStatus.DRAFT, EventCategory.CONCERT));
        eventRepo.save(buildEvent(3, "Event C", 4.2, 20, EventStatus.ON_SALE, EventCategory.CONCERT));

        List<Event> result = eventRepo.findByCompanyId(10);

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(e -> e.getCompanyId() == 10));
    }

    @Test
    void givenNoEventsForCompany_whenFindByCompanyId_thenReturnsEmptyList() {
        eventRepo.save(buildEvent(1, "Event A", 4.5, 10, EventStatus.ON_SALE, EventCategory.CONCERT));

        List<Event> result = eventRepo.findByCompanyId(99);

        assertTrue(result.isEmpty());
    }

    // === findIdsByCompany ===

    @Test
    void givenEventsForCompany_whenFindIdsByCompany_thenReturnsOnlyIdsForThatCompany() {
        eventRepo.save(buildEvent(1, "Event A", 4.5, 10, EventStatus.ON_SALE, EventCategory.CONCERT));
        eventRepo.save(buildEvent(2, "Event B", 3.8, 10, EventStatus.DRAFT, EventCategory.CONCERT));
        eventRepo.save(buildEvent(3, "Event C", 4.2, 20, EventStatus.ON_SALE, EventCategory.CONCERT));

        List<Integer> ids = eventRepo.findIdsByCompany(10);

        assertEquals(2, ids.size());
        assertTrue(ids.containsAll(List.of(1, 2)));
    }

    // === findActiveByCompany ===

    @Test
    void givenMixedStatusEventsForCompany_whenFindActiveByCompany_thenReturnsOnlyOnSaleEventsForThatCompany() {
        eventRepo.save(buildEvent(1, "On Sale", 4.5, 10, EventStatus.ON_SALE, EventCategory.CONCERT));
        eventRepo.save(buildEvent(2, "Draft", 3.8, 10, EventStatus.DRAFT, EventCategory.CONCERT));
        eventRepo.save(buildEvent(3, "On Sale Other Company", 4.2, 20, EventStatus.ON_SALE, EventCategory.CONCERT));

        List<Event> result = eventRepo.findActiveByCompany(10);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getId());
    }

    // === findByStatus ===

    @Test
    void givenEventsWithDifferentStatuses_whenFindByStatus_thenReturnsOnlyMatchingStatusEvents() {
        eventRepo.save(buildEvent(1, "Event A", 4.5, 10, EventStatus.ON_SALE, EventCategory.CONCERT));
        eventRepo.save(buildEvent(2, "Event B", 3.8, 10, EventStatus.DRAFT, EventCategory.CONCERT));
        eventRepo.save(buildEvent(3, "Event C", 4.2, 20, EventStatus.ON_SALE, EventCategory.CONCERT));

        List<Event> onSale = eventRepo.findByStatus(EventStatus.ON_SALE);
        List<Event> draft = eventRepo.findByStatus(EventStatus.DRAFT);

        assertEquals(2, onSale.size());
        assertEquals(1, draft.size());
        assertTrue(onSale.stream().allMatch(e -> e.getStatus() == EventStatus.ON_SALE));
    }

    @Test
    void givenNoEventsMatchingStatus_whenFindByStatus_thenReturnsEmptyList() {
        eventRepo.save(buildEvent(1, "Event A", 4.5, 10, EventStatus.ON_SALE, EventCategory.CONCERT));

        List<Event> result = eventRepo.findByStatus(EventStatus.CANCELED);

        assertTrue(result.isEmpty());
    }

    // === searchAll — eventName ===

    @Test
    void givenMatchingEventName_whensearchAll_thenReturnsMatchingEvent() {
        eventRepo.save(buildEvent(1, "Jazz Festival", 4.5, 10, EventStatus.ON_SALE, EventCategory.CONCERT));
        eventRepo.save(buildEvent(2, "Rock Night", 3.8, 10, EventStatus.ON_SALE, EventCategory.CONCERT));

        List<Event> result = eventRepo.searchAll(new CatalogSearchFiltersDTO(
                "Jazz", null, null, null, null, null, null, null, null, null, null, null, null));

        assertEquals(1, result.size());
        assertEquals("Jazz Festival", result.get(0).getName());
    }

    @Test
    void givenNonMatchingEventName_whensearchAll_thenReturnsEmpty() {
        eventRepo.save(buildEvent(1, "Jazz Festival", 4.5, 10, EventStatus.ON_SALE, EventCategory.CONCERT));

        List<Event> result = eventRepo.searchAll(new CatalogSearchFiltersDTO(
                "Opera", null, null, null, null, null, null, null, null, null, null, null, null));

        assertTrue(result.isEmpty());
    }

    // === searchAll — artistName ===

    @Test
    void givenMatchingArtistName_whensearchAll_thenReturnsMatchingEvent() {
        VenueMap vm = new VenueMap(1, LOCATION, List.of(new StandingZone(1, "Floor", 100, 50)));
        Event event = new Event(1, "Concert", 4.5, List.of("John Doe", "Jane Smith"),
                EventCategory.CONCERT, 10, EventStatus.ON_SALE, vm,
                List.of(new ShowDate(FUTURE_START, FUTURE_END)),
                new NoPurchasePolicy(), new DiscountPolicy(0));
        eventRepo.save(event);

        List<Event> result = eventRepo.searchAll(new CatalogSearchFiltersDTO(
                null, "John", null, null, null, null, null, null, null, null, null, null, null));

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getId());
    }

    // === searchAll — category ===

    @Test
    void givenMatchingCategory_whensearchAll_thenReturnsMatchingEvent() {
        eventRepo.save(buildEvent(1, "Hamlet", 4.5, 10, EventStatus.ON_SALE, EventCategory.MUSIC)); // MUSIC
        VenueMap vm = new VenueMap(2, LOCATION, List.of(new StandingZone(1, "Stalls", 100, 60)));
        Event theaterEvent = new Event(2, "Hamlet2", 4.5, List.of("Actor B"),
                EventCategory.THEATER, 10, EventStatus.ON_SALE, vm,
                List.of(new ShowDate(FUTURE_START, FUTURE_END)),
                new NoPurchasePolicy(), new DiscountPolicy(0));
        eventRepo.save(theaterEvent);

        List<Event> result = eventRepo.searchAll(new CatalogSearchFiltersDTO(
                null, null, "MUSIC", null, null, null, null, null, null, null, null, null, null));

        assertEquals(1, result.size());
        assertEquals("Hamlet", result.get(0).getName());
    }

    @Test
    void givenUnknownCategory_whensearchAll_thenReturnsEmpty() {
        eventRepo.save(buildEvent(1, "Event A", 4.5, 10, EventStatus.ON_SALE, EventCategory.CONCERT));

        List<Event> result = eventRepo.searchAll(new CatalogSearchFiltersDTO(
                null, null, "NOT_A_CATEGORY", null, null, null, null, null, null, null, null, null, null));

        assertTrue(result.isEmpty());
    }

    // === searchAll — keywords ===

    @Test
    void givenMatchingKeywordInEventName_whensearchAll_thenReturnsMatchingEvent() {
        eventRepo.save(buildEvent(1, "Summer Festival", 4.5, 10, EventStatus.ON_SALE, EventCategory.CONCERT));
        eventRepo.save(buildEvent(2, "Rock Night", 4.5, 10, EventStatus.ON_SALE, EventCategory.CONCERT));

        List<Event> result = eventRepo.searchAll(new CatalogSearchFiltersDTO(
                null, null, null, "Summer", null, null, null, null, null, null, null, null, null));

        assertEquals(1, result.size());
        assertEquals("Summer Festival", result.get(0).getName());
    }

    @Test
    void givenMatchingKeywordInArtistName_whensearchAll_thenReturnsMatchingEvent() {
        VenueMap vm = new VenueMap(1, LOCATION, List.of(new StandingZone(1, "Floor", 100, 50)));
        Event event = new Event(1, "Night Out", 4.5, List.of("Coldplay"),
                EventCategory.CONCERT, 10, EventStatus.ON_SALE, vm,
                List.of(new ShowDate(FUTURE_START, FUTURE_END)),
                new NoPurchasePolicy(), new DiscountPolicy(0));
        eventRepo.save(event);

        List<Event> result = eventRepo.searchAll(new CatalogSearchFiltersDTO(
                null, null, null, "Coldplay", null, null, null, null, null, null, null, null, null));

        assertEquals(1, result.size());
    }

    // === searchAll — date range ===

    @Test
    void givenEventWithShowDateInsideRange_whensearchAll_thenReturnsMatchingEvent() {
        eventRepo.save(buildEvent(1, "Future Show", 4.5, 10, EventStatus.ON_SALE, EventCategory.CONCERT)); // show date:
                                                                                                           // 2099-06-01

        List<Event> result = eventRepo.searchAll(new CatalogSearchFiltersDTO(
                null, null, null, null, null, null,
                LocalDate.of(2099, 5, 1), LocalDate.of(2099, 7, 1),
                null, null, null, null, null));

        assertEquals(1, result.size());
    }

    @Test
    void givenEventWithShowDateOutsideRange_whensearchAll_thenReturnsEmpty() {
        eventRepo.save(buildEvent(1, "Future Show", 4.5, 10, EventStatus.ON_SALE, EventCategory.CONCERT)); // show date:
                                                                                                           // 2099-06-01

        List<Event> result = eventRepo.searchAll(new CatalogSearchFiltersDTO(
                null, null, null, null, null, null,
                LocalDate.of(2100, 1, 1), LocalDate.of(2100, 12, 31),
                null, null, null, null, null));

        assertTrue(result.isEmpty());
    }

    // === searchAll — price range ===

    @Test
    void givenEventWithZoneInPriceRange_whensearchAll_thenReturnsMatchingEvent() {
        eventRepo.save(buildEvent(1, "Affordable Show", 4.5, 10, EventStatus.ON_SALE, EventCategory.CONCERT)); // zone
                                                                                                               // price
                                                                                                               // = 50

        List<Event> result = eventRepo.searchAll(new CatalogSearchFiltersDTO(
                null, null, null, null, 10.0, 100.0, null, null, null, null, null, null, null));

        assertEquals(1, result.size());
    }

    @Test
    void givenEventWithZonePriceBelowMinPrice_whensearchAll_thenReturnsEmpty() {
        eventRepo.save(buildEvent(1, "Cheap Show", 4.5, 10, EventStatus.ON_SALE, EventCategory.CONCERT)); // zone price
                                                                                                          // = 50

        List<Event> result = eventRepo.searchAll(new CatalogSearchFiltersDTO(
                null, null, null, null, 100.0, 500.0, null, null, null, null, null, null, null));

        assertTrue(result.isEmpty());
    }

    // === searchAll — all-null filters ===

    @Test
    void givenAllNullFilters_whensearchAll_thenReturnsAllEvents() {
        eventRepo.save(buildEvent(1, "Event A", 4.5, 10, EventStatus.ON_SALE, EventCategory.CONCERT));
        eventRepo.save(buildEvent(2, "Event B", 3.8, 10, EventStatus.DRAFT, EventCategory.CONCERT));

        List<Event> result = eventRepo.searchAll(new CatalogSearchFiltersDTO(
                null, null, null, null, null, null, null, null, null, null, null, null, null));

        assertEquals(2, result.size());
    }
}
