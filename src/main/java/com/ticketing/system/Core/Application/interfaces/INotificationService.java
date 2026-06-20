package com.ticketing.system.Core.Application.interfaces;

import java.util.List;

// High-level, application-facing notification facade. Each notifyXxx method translates a
// business event into a Notification domain object and delegates to NotificationDispatchService,
// which persists it and routes delivery. The low-level push channel (WebSocket / SSE / email)
// is a separate port, IPushNotificationService.
// V1 implementation is NotificationService.
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
