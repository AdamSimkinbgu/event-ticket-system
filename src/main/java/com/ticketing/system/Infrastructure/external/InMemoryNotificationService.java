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
