package com.ticketing.system.Infrastructure.external;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.ticketing.system.Core.Application.interfaces.IPushNotificationService;
import com.ticketing.system.Core.Domain.notifications.Notification;

// V1 stub for IPushNotificationService — push channel adapter.
// V1: collects notifications in memory so tests can assert on them.
// V2/V3: replace with WebSocket / SSE / email when real-time push is in scope.
import org.springframework.stereotype.Component;

@Component
public class InMemoryNotificationService implements IPushNotificationService {

    // V1 stub behaviour (as the class doc states): collect notifications in memory instead of
    // throwing, so reservation/checkout flows complete. UC-35 replaces this with a real push channel.
    private final java.util.List<Notification> sentNotifications = new java.util.concurrent.CopyOnWriteArrayList<>();

    @Override
    public boolean send(int recipientUserId, Notification notification) {
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