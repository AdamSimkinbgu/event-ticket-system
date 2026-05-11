package com.ticketing.system.unit.domain;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

// Unit tests for the ActiveOrder aggregate.
class ActiveOrderTest {

    @Test
    @Disabled("V1: addReservation appends CartLineItem (UC-5)")
    void givenEmptyOrder_whenAddReservation_thenLineAdded() {}

    @Test
    @Disabled("V1: validateCanCheckout rejects empty (UC-10)")
    void givenEmptyOrder_whenValidateCanCheckout_thenThrows() {}

    @Test
    @Disabled("V1: validateCanCheckout rejects expired (UC-10)")
    void givenExpiredItem_whenValidateCanCheckout_thenThrows() {}

    @Test
    @Disabled("V1: ReturnToStock empties order (UC-2 / UC-14)")
    void givenOrderWithItems_whenReturnToStock_thenOrderEmpty() {}
}
