package com.ticketing.system.acceptance;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NotificationAcceptanceTest {

    // UC-35
    @Test @Disabled("UC-35 main: TicketsPurchased event → buyer notification (I.5.2)")
    void GivenTicketsPurchased_WhenDispatch_ThenBuyerNotified() {}
    @Test @Disabled("UC-35 main: EventSoldOut event → producer notification (I.5.1)")
    void GivenEventSoldOut_WhenDispatch_ThenProducerNotified() {}
    @Test @Disabled("UC-35 alt: ManagerRevoked event → role-changed notification (I.5.1)")
    void GivenManagerRevoked_WhenDispatch_ThenManagerNotified() {}

    // UC-36
    @Test @Disabled("UC-36 main: offline recipient → notification stored as PENDING (I.6.1)")
    void GivenOfflineRecipient_WhenDispatch_ThenPending() {}
    @Test @Disabled("UC-36 alt: online recipient → no PENDING storage")
    void GivenOnlineRecipient_WhenDispatch_ThenNotPending() {}

    // UC-37
    @Test @Disabled("UC-37 main: login → all PENDING delivered (I.6.2)")
    void GivenPendingNotifications_WhenLogin_ThenAllDelivered() {}
    @Test @Disabled("UC-37 alt: empty PENDING is no-op")
    void GivenNoPending_WhenLogin_ThenNoop() {}
}
