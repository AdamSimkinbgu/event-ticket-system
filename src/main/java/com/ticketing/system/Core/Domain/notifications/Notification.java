package com.ticketing.system.Core.Domain.notifications;

import com.ticketing.system.Core.Application.dto.NotificationDTO;
import com.ticketing.system.Core.Domain.shared.InvariantChecked;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

// Aggregate root for the Notification aggregate (UC-35 / UC-36 / UC-37 design walkthrough).
// Promoted from a sub-entity of User to its own aggregate so high-volume PENDING storage
// and login-time delivery don't drag the User aggregate.
//
// Cross-aggregate references by ID per course rules:
//   recipientUserId — User aggregate
public class Notification implements InvariantChecked {

    private final String id;
    private final int recipientUserId;
    private final NotificationType type;
    private NotificationStatus status;
    private final String message;
    private final LocalDateTime createdAt;
    /**
     * Structured payload — type-specific key/value data the recipient (or UI) can
     * render. E.g. PURCHASE_CONFIRMED carries {@code orderId}, {@code total},
     * {@code eventName}; EVENT_CANCELLED carries {@code eventId}, {@code reason}.
     * The {@link #message} stays the human-readable summary; {@code data} is the
     * machine-readable companion. Never null — empty map for notifications with
     * no extra context.
     */
    private final Map<String, Object> data;

    /**
     * Backward-compatible 6-arg constructor — leaves {@link #data} as an empty map.
     * Existing callers that don't carry structured payload data keep working.
     */
    public Notification(String id, int recipientUserId, NotificationType type,
            NotificationStatus status, String message, LocalDateTime createdAt) {
        this(id, recipientUserId, type, status, message, createdAt, new HashMap<>());
    }

    /**
     * Full constructor including the structured {@code data} payload. New callers
     * (e.g. CheckoutService building a PURCHASE_CONFIRMED notification) should use
     * this and populate the relevant type-specific keys.
     */
    public Notification(String id, int recipientUserId, NotificationType type,
            NotificationStatus status, String message, LocalDateTime createdAt,
            Map<String, Object> data) {
        this.id = id;
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.status = status;
        this.message = message;
        this.createdAt = createdAt;
        this.data = data == null ? new HashMap<>() : new HashMap<>(data);
        checkInvariants();
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

    /**
     * Unmodifiable view of the structured payload. Never null — returns an empty
     * map for notifications that don't carry extra data.
     */
    public Map<String, Object> getData() {
        return Collections.unmodifiableMap(data);
    }

    // Lifecycle transition: invoked by NotificationDispatchService.deliverPending
    // (UC-37).
    public void markDelivered() {
        if (status != NotificationStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot mark as delivered a notification that is not PENDING. Current status: " + status);
        }
        this.status = NotificationStatus.DELIVERED;
        checkInvariants();
    }

    public void markPending() {
        if (status != NotificationStatus.DELIVERED) {
            throw new IllegalStateException(
                    "Cannot mark as pending a notification that is not DELIVERED. Current status: " + status);
        }
        this.status = NotificationStatus.PENDING;
        checkInvariants();
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

    @Override
    public void checkInvariants() {
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Notification invariant violated: id must be non-blank");
        }
        if (recipientUserId <= 0) {
            throw new IllegalStateException("Notification invariant violated: recipientUserId must be positive (was " + recipientUserId + ")");
        }
        if (type == null) {
            throw new IllegalStateException("Notification invariant violated: type must not be null");
        }
        if (status == null) {
            throw new IllegalStateException("Notification invariant violated: status must not be null");
        }
        if (createdAt == null) {
            throw new IllegalStateException("Notification invariant violated: createdAt must not be null");
        }
        if (data == null) {
            throw new IllegalStateException("Notification invariant violated: data map must not be null (use empty map for no payload)");
        }
    }
}
