package com.ticketing.system.Infrastructure.external;

import com.ticketing.system.Core.Application.interfaces.IPushNotificationService;
import com.ticketing.system.Core.Domain.notifications.Notification;

import org.springframework.stereotype.Component;

// V1 stub for IPushNotificationService — push channel adapter.
// V1: simulates a reachable channel by always returning true (no real delivery yet);
//     the dispatcher persists notifications, so nothing is stored here.
// V2/V3: replace with WebSocket / SSE / email when real-time push is in scope.

@Component
public class InMemoryNotificationService implements IPushNotificationService {

    // V1 stub behaviour (as the class doc states): collect notifications in memory
    // instead of
    // throwing, so reservation/checkout flows complete. UC-35 replaces this with a
    // real push channel.
    private final java.util.List<Notification> sentNotifications = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Override
    public boolean send(int recipientUserId, Notification notification) {
        // For V1, we simulate success for testing, or return false to test PENDING
        // flow.
        // In a real stub, we might collect these in a list for assertions.
        sentNotifications.add(notification);
        return true;
    }

    @Override
    public boolean isReachable(int recipientUserId) {
        return true;
    }

    public java.util.List<Notification> getSentNotifications() {
        return java.util.List.copyOf(sentNotifications);
    }
}
