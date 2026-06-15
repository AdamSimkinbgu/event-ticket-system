package com.ticketing.system.unit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.InventorySelection;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import com.ticketing.system.Core.Domain.events.StandingZone;
import com.ticketing.system.Core.Domain.events.Location;
import com.ticketing.system.Core.Domain.events.VenueMap;
import com.ticketing.system.Core.Domain.policies.purchase.NoPurchasePolicy;
import com.ticketing.system.Core.Domain.events.EventCategory;
import com.ticketing.system.support.BaseDomainTest;

import java.time.LocalDateTime;
import com.ticketing.system.Core.Domain.events.DiscountPolicy;
import com.ticketing.system.Core.Domain.policies.purchase.PurchasePolicy;
import com.ticketing.system.Core.Domain.events.Seat;
import com.ticketing.system.Core.Domain.events.SeatStatus;
import com.ticketing.system.Core.Domain.events.SeatedZone;
import com.ticketing.system.Core.Domain.events.ShowDate;

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
                List.of(new ShowDate(LocalDateTime.now().plusDays(30), LocalDateTime.now().plusDays(30).plusHours(2))),
                null,
                new DiscountPolicy(0) ));
                   
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

        event.transitionToOnSale();
        event.reserveInventory(1, InventorySelection.standing(3));

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
        
        event.transitionToOnSale();
        event.reserveInventory(2, InventorySelection.seated(List.of("A1", "A2"), "test-order"));

        assertEquals(SeatStatus.RESERVED, seatedZone.getSeatStatus("A1"));
        assertEquals(SeatStatus.RESERVED, seatedZone.getSeatStatus("A2"));
        assertEquals(SeatStatus.AVAILABLE, seatedZone.getSeatStatus("A3"));
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
        
        event.transitionToOnSale();
        event.reserveInventory(2, InventorySelection.seated(List.of("A1", "A2"), "test-order"));
        event.releaseInventory(2, InventorySelection.seated(List.of("A1"), "test-order"));

        assertEquals(SeatStatus.AVAILABLE, seatedZone.getSeatStatus("A1"));
        assertEquals(SeatStatus.RESERVED, seatedZone.getSeatStatus("A2"));
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

        event.transitionToOnSale();
        event.reserveInventory(2, InventorySelection.seated(List.of("A1"), "test-order"));
        event.confirmInventorySale(2, InventorySelection.seated(List.of("A1"), "test-order"));

        assertEquals(SeatStatus.SOLD, seatedZone.getSeatStatus("A1"));
        assertEquals(SeatStatus.AVAILABLE, seatedZone.getSeatStatus("A2"));
    }

    
    @Test
    void GivenPurchasePolicyRejectsQuantity_WhenReserveInventory_ThenThrowsException() {
        StandingZone standingZone = track(new StandingZone(1, "General Admission", 10, 50.0));

        PurchasePolicy rejectingPolicy = new NoPurchasePolicy();

        Event event = track(new Event(
                EVENT_ID,
                "Concert",
                4.5,
                ARTISTS,
                EventCategory.CONCERT,
                COMPANY_ID,
                EventStatus.SCHEDULED,
                new VenueMap(1, LOCATION, List.of(standingZone)),
                List.of(new ShowDate(LocalDateTime.now().plusDays(15), LocalDateTime.now().plusDays(15).plusHours(2))),
                rejectingPolicy,
                noDiscountPolicy()));

        assertThrows(IllegalStateException.class, () -> event.reserveInventory(1, InventorySelection.standing(1)));

        assertEquals(10, standingZone.getAvailableAmount());
        assertEquals(0, standingZone.getReservedAmount());
    }






    @Test
    void GivenScheduledEvent_WhenAddStandingZone_ThenZoneIsAdded() {
        StandingZone existingZone = track(new StandingZone(1, "General", 100, 50));
        Event event = createEventWithZones(List.of(existingZone));

        int newZoneId = event.addStandingZone("VIP Standing", 30, 120.0, COMPANY_ID);

        InventoryZone addedZone = event.getVenueMap().getZone(newZoneId);

        assertEquals("VIP Standing", addedZone.getName());
        assertEquals(30, addedZone.getCapacity());
        assertEquals(30, addedZone.getAvailableAmount());
    }

    @Test
    void GivenScheduledEvent_WhenAddSeatedZone_ThenZoneIsAdded() {
        StandingZone existingZone = track(new StandingZone(1, "General", 100, 50));
        Event event = createEventWithZones(List.of(existingZone));

        int newZoneId = event.addSeatedZone(
                "Balcony",
                150.0,
                List.of(
                        new Seat("B1", 0, 0),
                        new Seat("B2", 1, 0)
                ),
                COMPANY_ID
        );

        InventoryZone addedZone = event.getVenueMap().getZone(newZoneId);

        assertEquals("Balcony", addedZone.getName());
        assertEquals(2, addedZone.getCapacity());
        assertEquals(2, addedZone.getAvailableAmount());
    }

    @Test
    void GivenScheduledEvent_WhenRemoveEmptyZone_ThenZoneIsRemoved() {
        StandingZone zoneToRemove = track(new StandingZone(1, "General", 100, 50));
        StandingZone remainingZone = track(new StandingZone(2, "VIP", 20, 150));

        Event event = createEventWithZones(List.of(zoneToRemove, remainingZone));

        event.removeInventoryZone(1, COMPANY_ID);

        assertThrows(IllegalArgumentException.class, () -> event.getVenueMap().getZone(1));
        assertEquals("VIP", event.getVenueMap().getZone(2).getName());
    }

    @Test
    void GivenReservedInventoryInZone_WhenRemoveZone_ThenThrowsException() {
        StandingZone zone = track(new StandingZone(1, "General", 100, 50));
        Event event = createEventWithZones(List.of(zone));

        event.transitionToOnSale();
        event.reserveInventory(1, InventorySelection.standing(5));

        assertThrows(IllegalStateException.class, () ->
                event.removeInventoryZone(1, COMPANY_ID)
        );

        assertEquals(100, zone.getCapacity());
        assertEquals(5, zone.getReservedAmount());
    }

    @Test
    void GivenOnSaleEvent_WhenAddStandingZone_ThenThrowsException() {
        StandingZone existingZone = track(new StandingZone(1, "General", 100, 50));

        Event event = track(new Event(
                EVENT_ID,
                "Concert",
                4.5,
                ARTISTS,
                EventCategory.CONCERT,
                COMPANY_ID,
                EventStatus.ON_SALE,
                new VenueMap(1, LOCATION, List.of(existingZone)),
                List.of(new ShowDate(
                        LocalDateTime.now().plusDays(30),
                        LocalDateTime.now().plusDays(30).plusHours(2)
                )),
                acceptingPurchasePolicy(),
                noDiscountPolicy()
        ));

        assertThrows(IllegalStateException.class, () ->
                event.addStandingZone("Late Zone", 50, 90.0, COMPANY_ID)
        );
    }

    @Test
    void GivenOnSaleEvent_WhenRemoveZone_ThenThrowsException() {
        StandingZone existingZone = track(new StandingZone(1, "General", 100, 50));

        Event event = track(new Event(
                EVENT_ID,
                "Concert",
                4.5,
                ARTISTS,
                EventCategory.CONCERT,
                COMPANY_ID,
                EventStatus.ON_SALE,
                new VenueMap(1, LOCATION, List.of(existingZone)),
                List.of(new ShowDate(
                        LocalDateTime.now().plusDays(30),
                        LocalDateTime.now().plusDays(30).plusHours(2)
                )),
                acceptingPurchasePolicy(),
                noDiscountPolicy()
        ));

        assertThrows(IllegalStateException.class, () ->
                event.removeInventoryZone(1, COMPANY_ID)
        );
    }

    @Test
    void GivenWrongCompany_WhenAddZone_ThenThrowsException() {
        StandingZone existingZone = track(new StandingZone(1, "General", 100, 50));
        Event event = createEventWithZones(List.of(existingZone));

        assertThrows(RuntimeException.class, () ->
                event.addStandingZone("Wrong Company Zone", 50, 90.0, 999)
        );
    }

    @Test
    void GivenDuplicateZoneName_WhenAddZone_ThenThrowsException() {
        StandingZone existingZone = track(new StandingZone(1, "General", 100, 50));
        Event event = createEventWithZones(List.of(existingZone));

        assertThrows(IllegalArgumentException.class, () -> event.addStandingZone("General", 50, 90.0, COMPANY_ID));
    }








    @Test
    void GivenScheduledEvent_WhenAddSeatsToSeatedZone_ThenSeatsAdded() {
        SeatedZone seatedZone = track(new SeatedZone(
                ZONE_ID,
                "Orchestra",
                120.0,
                List.of(new Seat("A1", 0, 0))
        ));

        Event event = createEventWithZones(List.of(seatedZone));

        event.addSeatsToSeatedZone(
                ZONE_ID,
                List.of(new Seat("A2", 1, 0), new Seat("A3", 2, 0)),
                COMPANY_ID
        );

        assertEquals(3, seatedZone.getCapacity());
        assertEquals(SeatStatus.AVAILABLE, seatedZone.getSeatStatus("A2"));
    }

    @Test
    void GivenOnSaleEvent_WhenAddSeatsToSeatedZone_ThenThrowsException() {
        SeatedZone seatedZone = track(new SeatedZone(
                ZONE_ID,
                "Orchestra",
                120.0,
                List.of(new Seat("A1", 0, 0))
        ));

        Event event = track(new Event(
                EVENT_ID,
                "Concert",
                4.5,
                ARTISTS,
                EventCategory.CONCERT,
                COMPANY_ID,
                EventStatus.ON_SALE,
                new VenueMap(1, LOCATION, List.of(seatedZone)),
                List.of(new ShowDate(
                        LocalDateTime.now().plusDays(30),
                        LocalDateTime.now().plusDays(30).plusHours(2)
                )),
                acceptingPurchasePolicy(),
                noDiscountPolicy()
        ));

        assertThrows(IllegalStateException.class, () ->
                event.addSeatsToSeatedZone(
                        ZONE_ID,
                        List.of(new Seat("A2", 1, 0)),
                        COMPANY_ID
                )
        );
    }

    @Test
    void GivenScheduledEvent_WhenAddAndRemovePlacesFromStandingZone_ThenCapacityUpdated() {
        StandingZone standingZone = track(new StandingZone(ZONE_ID, "General", 100, 50));
        Event event = createEventWithZones(List.of(standingZone));

        event.addPlacesToStandingZone(ZONE_ID, 20, COMPANY_ID);
        event.removePlacesFromStandingZone(ZONE_ID, 10, COMPANY_ID);

        assertEquals(110, standingZone.getCapacity());
        assertEquals(110, standingZone.getAvailableAmount());
    }

    @Test
    void GivenOnSaleEvent_WhenRemovePlacesFromStandingZone_ThenThrowsException() {
        StandingZone standingZone = track(new StandingZone(ZONE_ID, "General", 100, 50));

        Event event = track(new Event(
                EVENT_ID,
                "Concert",
                4.5,
                ARTISTS,
                EventCategory.CONCERT,
                COMPANY_ID,
                EventStatus.ON_SALE,
                new VenueMap(1, LOCATION, List.of(standingZone)),
                List.of(new ShowDate(
                        LocalDateTime.now().plusDays(30),
                        LocalDateTime.now().plusDays(30).plusHours(2)
                )),
                acceptingPurchasePolicy(),
                noDiscountPolicy()
        ));

        assertThrows(IllegalStateException.class, () ->
                event.removePlacesFromStandingZone(ZONE_ID, 10, COMPANY_ID)
        );
    }





    // test helper functions:

    private PurchasePolicy acceptingPurchasePolicy() {
        return new NoPurchasePolicy();
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
                List.of(new ShowDate(LocalDateTime.now().plusDays(30), LocalDateTime.now().plusDays(30).plusHours(2))),
                acceptingPurchasePolicy(),
                noDiscountPolicy()));
    }
}
