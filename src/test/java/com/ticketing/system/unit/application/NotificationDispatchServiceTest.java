package com.ticketing.system.unit.application;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class NotificationDispatchServiceTest {
    @Test @Disabled("UC-35: domain event triggers notification, online recipient → live push")
    void givenOnlineRecipient_whenDispatch_thenLivePush() {}

    @Test @Disabled("UC-36: offline recipient → PENDING storage")
    void givenOfflineRecipient_whenDispatch_thenStoredPending() {}

    @Test @Disabled("UC-37: deliverPending flips PENDING → DELIVERED in bulk")
    void givenPendingNotifications_whenDeliverPending_thenAllDelivered() {}

    @Test @Disabled("UC-37: empty PENDING is no-op")
    void givenNoPending_whenDeliverPending_thenNoop() {}
}
