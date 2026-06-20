package com.ticketing.system.Core.Application.interfaces;

import java.util.List;

import com.ticketing.system.Core.Domain.notifications.Notification;

// Port for the live push channel (WebSocket / SSE / email — V2/V3 decision).
// V1 implementation is InMemoryNotificationService for test assertions.
// Used by NotificationDispatchService for online-recipient delivery (UC-35).
public interface INotificationService {

    void notifyPurchaseCompleted(int userId, double totalPrice, List<Integer> list);

    void notifyPurchaseFailed(int userId, String string);

    void notifyTicketReservationSuccess(int userId, int eventId, int zoneId, int quantity);

    void notifyTicketReservationFailure(int userId, int eventId, int zoneId, String reason);

    void notifyRemoveTicketReservationFailure(int userId, int eventId, int zoneId, String string);

    void notifyRemoveTicketReservationSuccess(int userId, int eventId, int zoneId, int i);

    void notifyEventCancelled(int userId, int eventId, String eventName);

    void notifyManagerRevoked(int userId, int companyId, String companyName);

    void notifyOwnerAppointmentPending(int userId, int companyId, String companyName);

    void notifyRoleChanged(int userId, int companyId, String companyName, String newRole);
}
