package com.ticketing.system.unit.domain;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

// Unit tests for the Event aggregate (Event + VenueMap + InventoryZone + ShowDate + policies).
class EventTest {

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
