package com.ticketing.system.unit.application;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class EventManagementServiceTest {
    @Test @Disabled("UC-19: Owner adds event — DRAFT state initially")
    void givenOwner_whenAddEvent_thenEventInDraft() {}

    @Test @Disabled("UC-19: Manager without permission rejected")
    void givenManagerWithoutPermission_whenAddEvent_thenRejected() {}

    @Test @Disabled("UC-20: VenueMap binding pre-generates Tickets")
    void givenEvent_whenBindVenueMap_thenTicketsPreGenerated() {}

    @Test @Disabled("UC-21: setEventPolicies stores PurchasePolicy + DiscountPolicy")
    void givenOwner_whenSetEventPolicies_thenStored() {}
}
