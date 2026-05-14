package com.ticketing.system.Infrastructure.external;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Domain.notifications.Notification;
import com.ticketing.system.Core.Domain.notifications.NotificationStatus;
import com.ticketing.system.Core.Domain.notifications.NotificationType;

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
    @Override
public void notifyPurchaseCompleted(int userId, double totalPrice, List<Integer> ticketIds) {
    Notification notification = new Notification(
            UUID.randomUUID().toString(),
            userId,
            NotificationType.PURCHASE_CONFIRMED,
            NotificationStatus.PENDING,
            "Purchase completed successfully. Total price: " + totalPrice,
            LocalDateTime.now()
    );

    send(userId, notification);
}
@Override
public void notifyPurchaseFailed(int userId, String message) {
    Notification notification = new Notification(
            UUID.randomUUID().toString(),
            userId,
            NotificationType.PURCHASE_FAILED,
            NotificationStatus.PENDING,
            message,
            LocalDateTime.now()
    );

    send(userId, notification);
}

@Override
public void notifyTicketReservationSuccess(int userId, int eventId, int zoneId, int quantity) {
    String message = "Ticket reservation completed successfully. " +
            "eventId=" + eventId +
            ", zoneId=" + zoneId +
            ", quantity=" + quantity;

    Notification notification = new Notification(
            java.util.UUID.randomUUID().toString(),
            userId,
            NotificationType.TICKET_RESERVATION_SUCCESS,
            NotificationStatus.PENDING,
            message,
            java.time.LocalDateTime.now()
    );

send(userId, notification);
}

@Override
public void notifyTicketReservationFailure(int userId, int eventId, int zoneId, String reason) {
    String message = "Ticket reservation failed. " +
            "eventId=" + eventId +
            ", zoneId=" + zoneId +
            ", reason=" + reason;

    Notification notification = new Notification(
            java.util.UUID.randomUUID().toString(),
            userId,
            NotificationType.TICKET_RESERVATION_FAILURE,
            NotificationStatus.PENDING,
            message,
            java.time.LocalDateTime.now()
    );

  send(userId, notification);
}

@Override
public void notifyRemoveTicketReservationFailure(int userId, int eventId, int zoneId, String reason) {
    String message = " remove Ticket reservation failed. " +
            "eventId=" + eventId +
            ", zoneId=" + zoneId +
            ", reason=" + reason;

    Notification notification = new Notification(
            java.util.UUID.randomUUID().toString(),
            userId,
            NotificationType.REMOVE_TICKET_RESERVATION_FAILURE,
            NotificationStatus.PENDING,
            message,
            java.time.LocalDateTime.now()
    );

  send(userId, notification);
}


@Override
public void notifyRemoveTicketReservationSuccess(int userId, int eventId, int zoneId, int reason) {
   String message = " remove Ticket reservation completed successfully. " +
            "eventId=" + eventId +
            ", zoneId=" + zoneId +
            ", reason=" + reason;

    Notification notification = new Notification(
            java.util.UUID.randomUUID().toString(),
            userId,
            NotificationType.REMOVE_TICKET_RESERVATION_SUCCESS,
            NotificationStatus.PENDING,
            message,
            java.time.LocalDateTime.now()
    );

  send(userId, notification);
}



}