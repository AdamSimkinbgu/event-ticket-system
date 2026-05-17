package com.ticketing.system.Core.Domain.notifications;

import com.ticketing.system.Core.Application.dto.NotificationDTO;

import java.time.LocalDateTime;

// Aggregate root for the Notification aggregate (UC-35 / UC-36 / UC-37 design walkthrough).
// Promoted from a sub-entity of User to its own aggregate so high-volume PENDING storage
// and login-time delivery don't drag the User aggregate.
//
// Cross-aggregate references by ID per course rules:
//   recipientUserId — User aggregate
public class Notification {

    private final String id;
    private final int recipientUserId;
    private final NotificationType type;
    private NotificationStatus status;
    private final String message;
    private final LocalDateTime createdAt;

    public Notification(String id, int recipientUserId, NotificationType type,
            NotificationStatus status, String message, LocalDateTime createdAt) {
        this.id = id;
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.status = status;
        this.message = message;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public int getRecipientUserId() {
        return recipientUserId;
    }

    public NotificationType getType() {
        return type;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // Lifecycle transition: invoked by NotificationDispatchService.deliverPending
    // (UC-37).
    public void markDelivered() {
        if (status != NotificationStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot mark as delivered a notification that is not PENDING. Current status: " + status);
        }
        this.status = NotificationStatus.DELIVERED;
    }

    public void markPending() {
        if (status != NotificationStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Cannot mark as pending a notification that is not DELIVERED. Current status: " + status);
        }
        this.status = NotificationStatus.PENDING;
    }

    // Lifecycle transition: invoked when the user opens the notification in the UI.
    public void markRead() {
        throw new UnsupportedOperationException("not implemented");
    }

    // State checks.
    public boolean isPending() {
        return status == NotificationStatus.PENDING;
    }

    public boolean isDelivered() {
        return status == NotificationStatus.DELIVERED;
    }

    public boolean isRead() {
        return status == NotificationStatus.READ;
    }

    public NotificationDTO toDTO() {
        return new NotificationDTO(
                id,
                type.name(),
                status.name(),
                message,
                createdAt);
    }
}
