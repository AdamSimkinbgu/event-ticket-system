package com.ticketing.system.Core.Application.interfaces;

import java.util.List;

import com.ticketing.system.Core.Domain.notifications.Notification;

// Port for the live push channel (WebSocket / SSE / email — V2/V3 decision).
// V1 implementation is InMemoryNotificationService for test assertions.
// Used by NotificationDispatchService for online-recipient delivery (UC-35).
public interface INotificationService {

    // UC-35 — push a notification to an online recipient.
    // Returns true if the push was acknowledged (or queued successfully).
    boolean send(int recipientUserId, Notification notification);

    // Used by the dispatcher when the recipient's reachability isn't already known
    // from ISessionManager (e.g. multi-device scenarios where session-online != channel-reachable).
    boolean isReachable(int recipientUserId);

    void notifyPurchaseCompleted(int userId, double totalPrice, List<Integer> list);

    void notifyPurchaseFailed(int userId, String string);
}
