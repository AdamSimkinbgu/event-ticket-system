package com.ticketing.system.unit.domain;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

// Unit tests for the Notification aggregate (UC-35/36/37).
class NotificationTest {

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
