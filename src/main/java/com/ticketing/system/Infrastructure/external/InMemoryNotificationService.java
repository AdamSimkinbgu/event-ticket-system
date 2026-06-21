package com.ticketing.system.Infrastructure.external;

import com.ticketing.system.Core.Application.interfaces.IPushNotificationService;
import com.ticketing.system.Core.Domain.notifications.Notification;

import org.springframework.stereotype.Component;

// V1 stub for IPushNotificationService — push channel adapter.
// V1: simulates a reachable channel by always returning true (no real delivery yet).
//     Sent notifications are also collected in memory (see getSentNotifications) so tests
//     can assert what was pushed; the dispatcher remains the durable store of record.
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
        // V1: always report success (no real channel yet) and record the notification so
        // tests can assert what was sent. Tests that need a failed/PENDING delivery should
        // mock IPushNotificationService to return false rather than rely on this stub.
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
