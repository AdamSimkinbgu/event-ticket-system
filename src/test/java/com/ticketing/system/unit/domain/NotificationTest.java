package com.ticketing.system.unit.domain;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.ticketing.system.support.BaseDomainTest;

// Unit tests for the Notification aggregate (UC-35/36/37).
// Extends BaseDomainTest so future (currently @Disabled) tests get automatic
// invariant verification via track(aggregate).
class NotificationTest extends BaseDomainTest {

    @Test
    @Disabled("V1: markDelivered transitions PENDING -> DELIVERED (UC-37)")
    void givenPendingNotification_whenMarkDelivered_thenStatusDelivered() {}

    @Test
    @Disabled("V1: markRead transitions DELIVERED -> READ")
    void givenDeliveredNotification_whenMarkRead_thenStatusRead() {}

    @Test
    @Disabled("V1: markRead from PENDING is invalid")
    void givenPendingNotification_whenMarkRead_thenThrows() {}
}
