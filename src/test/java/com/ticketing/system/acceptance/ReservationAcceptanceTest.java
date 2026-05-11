package com.ticketing.system.acceptance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ReservationAcceptanceTest {

    // UC-5
    @Test @Disabled("UC-5 main: first selection creates ActiveOrder + locks first ticket")
    void GivenNoActiveOrder_WhenReserve_ThenOrderCreated() {}
    @Test @Disabled("UC-5 alt: Guest cart linked to session")
    void GivenGuest_WhenReserve_ThenLinkedToSession() {}

    // UC-9
    @Test @Disabled("UC-9 main: reserve specific seated ticket succeeds")
    void GivenAvailableSeat_WhenReserve_ThenLocked() {}
    @Test @Disabled("UC-9 main: reserve quantity from standing zone succeeds")
    void GivenAvailableCapacity_WhenReserveQuantity_ThenLocked() {}
    @Test @Disabled("UC-9 negative: SLR.1.2 — concurrent reservation conflict")
    void GivenSeatLockedByA_WhenBReserves_ThenBRejected() {}
    @Test @Disabled("UC-9 negative: purchase policy violation rejects (II.4.3.1)")
    void GivenMaxPerBuyerExceeded_WhenReserve_ThenRejected() {}

    // UC-2
    @Test @Disabled("UC-2 main: expired ActiveOrder releases tickets back to AVAILABLE")
    void GivenExpiredOrder_WhenSweep_ThenTicketsAvailable() {}
}
