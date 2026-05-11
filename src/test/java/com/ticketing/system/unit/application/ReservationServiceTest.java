package com.ticketing.system.unit.application;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class ReservationServiceTest {
    @Test @Disabled("UC-5: first selection creates ActiveOrder + locks first ticket")
    void givenNoActiveOrder_whenReserve_thenOrderCreatedAndTicketLocked() {}

    @Test @Disabled("UC-9: subsequent reservation appends to existing ActiveOrder")
    void givenActiveOrder_whenReserve_thenLineAppended() {}

    @Test @Disabled("UC-9: SLR.1.2 — concurrent reservation of same ticket rejected")
    void givenTicketLockedByA_whenBReserves_thenBRejected() {}

    @Test @Disabled("UC-9: purchase policy violation rejects reservation (II.2.4)")
    void givenPolicyViolation_whenReserve_thenRejected() {}

    @Test @Disabled("UC-2: expiration sweep releases locked tickets back to AVAILABLE")
    void givenExpiredOrder_whenSweep_thenTicketsReleased() {}

    @Test @Disabled("UC-13: restoreActiveOrder reattaches member's pending cart on login")
    void givenMemberWithPendingOrder_whenLogin_thenOrderRestored() {}
}
