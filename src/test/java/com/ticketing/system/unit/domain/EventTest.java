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
import com.ticketing.system.Core.Domain.events.StandingZone;
import com.ticketing.system.Core.Domain.events.Location;
import com.ticketing.system.Core.Domain.events.VenueMap;
import com.ticketing.system.Core.Domain.events.EventCategory;
import com.ticketing.system.support.BaseDomainTest;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;

import com.ticketing.system.Core.Domain.events.DiscountPolicy;
import com.ticketing.system.Core.Domain.events.InventorySelection;
import com.ticketing.system.Core.Domain.events.PurchasePolicy;
import com.ticketing.system.Core.Domain.events.Seat;
import com.ticketing.system.Core.Domain.events.SeatStatus;
import com.ticketing.system.Core.Domain.events.SeatedZone;

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
        zone = track(new StandingZone(ZONE_ID, "VIP", 10, 100));

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
                null));
    }

    @Test
    public void GivenExistingZone_WhenUpdateZoneCapacity_ThenCapacityUpdated() {
        event.updateStandingZoneCapacity(ZONE_ID, 20, COMPANY_ID);

        assertEquals(20, zone.getAvailableAmount());
    }

    @Test
    public void GivenMissingZone_WhenUpdateZoneCapacity_ThenThrowException() {
        assertThrows(IllegalArgumentException.class, () -> event.updateStandingZoneCapacity(999, 20, COMPANY_ID));
    }

    @Test
    public void GivenWrongCompany_WhenUpdateZoneCapacity_ThenThrowException() {
        assertThrows(RuntimeException.class, () -> event.updateStandingZoneCapacity(ZONE_ID, 20, 999));
    }

    @Test
    @Disabled("V1: getZone returns matching zone")
    void givenEvent_whenGetZoneById_thenReturnsZone() {
    }

    @Test
    @Disabled("V1: getZone throws on unknown id")
    void givenEvent_whenGetUnknownZone_thenThrows() {
    }

    @Test
    @Disabled("V1: reserveTickets succeeds when available + policy passes")
    void givenAvailableZone_whenReserveTickets_thenSuccess() {
    }

    @Test
    @Disabled("V1: reserveTickets fails when policy rejects (II.4.3.1)")
    void givenPolicyViolation_whenReserveTickets_thenRejected() {
    }

    @Test
    @Disabled("V1: calculatePrice applies DiscountPolicy")
    void givenDiscountPolicy_whenCalculatePrice_thenAppliesDiscount() {
    }

    @Test
    void GivenStandingZone_WhenReserveInventoryStandingSelection_ThenQuantityReserved() {
        StandingZone standingZone = track(new StandingZone(1, "General Admission", 10, 50.0));
        Event event = createEventWithZones(List.of(standingZone));

        event.reserveStandingSpots(1, 3);

        assertEquals(7, standingZone.getAvailableAmount());
        assertEquals(3, standingZone.getReservedAmount());
    }

    @Test
    void GivenSeatedZone_WhenReserveInventorySeatSelection_ThenExactSeatsReserved() {
        SeatedZone seatedZone = track(new SeatedZone(
                2,
                "Orchestra",
                120.0,
                List.of(
                        new Seat("A1", 0, 0),
                        new Seat("A2", 1, 0),
                        new Seat("A3", 2, 0))));
        Event event = createEventWithZones(List.of(seatedZone));

        event.reserveSeats(2, List.of("A1", "A2"));

        assertEquals(SeatStatus.RESERVED, seatedZone.getSeat("A1").getStatus());
        assertEquals(SeatStatus.RESERVED, seatedZone.getSeat("A2").getStatus());
        assertEquals(SeatStatus.AVAILABLE, seatedZone.getSeat("A3").getStatus());
    }

    @Test
    void GivenSeatedZone_WhenReleaseInventorySeatSelection_ThenExactSeatsReleased() {
        SeatedZone seatedZone = track(new SeatedZone(
                2,
                "Orchestra",
                120.0,
                List.of(
                        new Seat("A1", 0, 0),
                        new Seat("A2", 1, 0))));
        Event event = createEventWithZones(List.of(seatedZone));

        event.reserveSeats(2, List.of("A1", "A2"));
        event.releaseSeats(2, List.of("A1"));

        assertEquals(SeatStatus.AVAILABLE, seatedZone.getSeat("A1").getStatus());
        assertEquals(SeatStatus.RESERVED, seatedZone.getSeat("A2").getStatus());
    }

    @Test
    void GivenSeatedZone_WhenConfirmSale_ThenReservedSeatsBecomeSold() {
        SeatedZone seatedZone = track(new SeatedZone(
                2,
                "Orchestra",
                120.0,
                List.of(
                        new Seat("A1", 0, 0),
                        new Seat("A2", 1, 0))));
        Event event = createEventWithZones(List.of(seatedZone));

        event.reserveSeats(2, List.of("A1"));
        event.confirmSeatSale(2, List.of("A1"));

        assertEquals(SeatStatus.SOLD, seatedZone.getSeat("A1").getStatus());
        assertEquals(SeatStatus.AVAILABLE, seatedZone.getSeat("A2").getStatus());
    }

    @Test
    void GivenSeatedZone_WhenUpdateZoneCapacity_ThenThrowsException() {
        SeatedZone seatedZone = track(new SeatedZone(
                ZONE_ID,
                "Orchestra",
                120.0,
                List.of(
                        new Seat("A1", 0, 0),
                        new Seat("A2", 1, 0))));
        Event event = createEventWithZones(List.of(seatedZone));

        assertThrows(IllegalStateException.class, () -> event.updateStandingZoneCapacity(ZONE_ID, 100, COMPANY_ID));

        assertEquals(2, seatedZone.getCapacity());
    }

    @Test
    void GivenStandingZone_WhenUpdateZoneCapacity_ThenCapacityUpdated() {
        StandingZone standingZone = track(new StandingZone(ZONE_ID, "General Admission", 10, 50.0));
        Event event = createEventWithZones(List.of(standingZone));

        event.updateStandingZoneCapacity(ZONE_ID, 20, COMPANY_ID);

        assertEquals(20, standingZone.getCapacity());
        assertEquals(20, standingZone.getAvailableAmount());
    }

    @Test
    void GivenPurchasePolicyRejectsQuantity_WhenReserveInventory_ThenThrowsException() {
        StandingZone standingZone = track(new StandingZone(1, "General Admission", 10, 50.0));

        PurchasePolicy rejectingPolicy = new PurchasePolicy(0);

        Event event = track(new Event(
                EVENT_ID,
                "Concert",
                4.5,
                ARTISTS,
                EventCategory.CONCERT,
                COMPANY_ID,
                EventStatus.SCHEDULED,
                new VenueMap(1, LOCATION, List.of(standingZone)),
                List.of(),
                rejectingPolicy,
                noDiscountPolicy()));

        assertThrows(IllegalStateException.class, () -> event.reserveStandingSpots(1, 1));

        assertEquals(10, standingZone.getAvailableAmount());
        assertEquals(0, standingZone.getReservedAmount());
    }





    // test helper functions:

    private PurchasePolicy acceptingPurchasePolicy() {
        return new PurchasePolicy(10);
    }
    

    private DiscountPolicy noDiscountPolicy() {
        return new DiscountPolicy(0) {
            @Override
            public double calculate(int quantity, Double priceAtOneTicketReservation, LocalDateTime now) {
                return quantity * priceAtOneTicketReservation;
            }

            @Override
            public double calculatePriceforoneticket(int quantity, Double priceAtOneTicketReservation,
                    LocalDateTime now) {
                return priceAtOneTicketReservation;
            }
        };
    }

    private Event createEventWithZones(List<InventoryZone> zones) {
        return track(new Event(
                EVENT_ID,
                "Concert",
                4.5,
                ARTISTS,
                EventCategory.CONCERT,
                COMPANY_ID,
                EventStatus.SCHEDULED,
                new VenueMap(1, LOCATION, zones),
                List.of(),
                acceptingPurchasePolicy(),
                noDiscountPolicy()));
    }
}
