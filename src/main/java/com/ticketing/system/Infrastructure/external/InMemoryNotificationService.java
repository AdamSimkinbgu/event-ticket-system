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

    @Override
    public boolean send(int recipientUserId, Notification notification) {
        // For V1, we simulate success for testing, or return false to test PENDING flow.
        // In a real stub, we might collect these in a list for assertions.
        return true; 
    }

    @Override
    public boolean isReachable(int recipientUserId) {
        // V1 assume always reachable if online (checked by dispatcher via SessionManager)
        return true;
    }
}