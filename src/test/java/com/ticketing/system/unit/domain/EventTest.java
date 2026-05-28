package com.ticketing.system.unit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import com.ticketing.system.Core.Domain.events.Location;
import com.ticketing.system.Core.Domain.events.VenueMap;
import com.ticketing.system.Core.Domain.events.EventCategory;
import com.ticketing.system.support.BaseDomainTest;

// Unit tests for the Event aggregate (Event + VenueMap + InventoryZone + ShowDate + policies).
class EventTest extends BaseDomainTest {


    private final int COMPANY_ID = 100;
    private final int EVENT_ID = 10;
    private final int ZONE_ID = 5;
    private final Location LOCATION = new Location("Belgium", "Brussels");

    private final List<String> ARTISTS = List.of("Artist 1", "Artist 2");

    private InventoryZone zone;
    private Event event;

    @BeforeEach
    public void setUp() {
        zone = track(new InventoryZone(ZONE_ID, "VIP", 10, 100));

        VenueMap venueMap = new VenueMap(1, LOCATION, List.of(zone));

        event = track(new Event(
                EVENT_ID,
                "Concert",
                4.5,
                ARTISTS,
                EventCategory.CONCERT,
                COMPANY_ID,
                EventStatus.SCHEDULED,
                venueMap,
                List.of(),
                null,
                null
        ));
    }


       @Test
    public void GivenExistingZone_WhenUpdateZoneCapacity_ThenCapacityUpdated() {
        event.updateZoneCapacity(ZONE_ID, 20, COMPANY_ID);

        assertEquals(20, zone.getAvailableAmount());
    }


    @Test
    public void GivenMissingZone_WhenUpdateZoneCapacity_ThenThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                event.updateZoneCapacity(999, 20, COMPANY_ID)
        );
    }

    @Test
    public void GivenWrongCompany_WhenUpdateZoneCapacity_ThenThrowException() {
        assertThrows(RuntimeException.class, () ->
                event.updateZoneCapacity(ZONE_ID, 20, 999)
        );
    }

    @Test
    @Disabled("V1: getZone returns matching zone")
    void givenEvent_whenGetZoneById_thenReturnsZone() {}

    @Test
    @Disabled("V1: getZone throws on unknown id")
    void givenEvent_whenGetUnknownZone_thenThrows() {}

    @Test
    @Disabled("V1: reserveTickets succeeds when available + policy passes")
    void givenAvailableZone_whenReserveTickets_thenSuccess() {}

    @Test
    @Disabled("V1: reserveTickets fails when policy rejects (II.4.3.1)")
    void givenPolicyViolation_whenReserveTickets_thenRejected() {}

    @Test
    @Disabled("V1: calculatePrice applies DiscountPolicy")
    void givenDiscountPolicy_whenCalculatePrice_thenAppliesDiscount() {}
}
