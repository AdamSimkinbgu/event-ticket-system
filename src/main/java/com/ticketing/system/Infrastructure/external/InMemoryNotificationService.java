package com.ticketing.system.Infrastructure.external;

import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Domain.notifications.Notification;

// V1 stub for INotificationService — push channel adapter.
// V1: collects notifications in memory so tests can assert on them.
// V2/V3: replace with WebSocket / SSE / email when real-time push is in scope.
// All bodies are stubs — owned by the team member assigned to UC-35.
public class InMemoryNotificationService implements INotificationService {

    @Override
    public boolean send(int recipientUserId, Notification notification) {
        throw new UnsupportedOperationException("UC-35: in-memory send not implemented");
    }

    @Override
    public boolean isReachable(int recipientUserId) {
        throw new UnsupportedOperationException("UC-35: not implemented");
    }
}
