package com.ticketing.system.Core.Domain.notifications;

import java.util.List;

import com.ticketing.system.Core.Domain.shared.IRepository;

/**
 * Aggregate-root entry point for the Notification aggregate (UC-36 design
 * walkthrough).
 *
 * <p>Notification was promoted from a sub-entity of User to its own aggregate so
 * high-volume PENDING storage and login-time delivery (UC-37) don't drag the
 * User aggregate.
 */
public interface INotificationRepository extends IRepository<Notification, String> {

    /**
     * @param notification the notification to persist
     */
    void save(Notification notification);

    /**
     * @param notificationId the notification id
     * @return the notification, or {@code null} if none exists with that id
     */
    Notification findById(String notificationId);

    /**
     * Used by UC-37 ({@code NotificationDispatchService.deliverPending}) on
     * successful login.
     *
     * @param recipientUserId the recipient's user id
     * @param status          the status to filter by (e.g. PENDING)
     * @return the recipient's notifications in the given status
     */
    List<Notification> findByRecipientAndStatus(int recipientUserId, NotificationStatus status);

    /**
     * Used by {@code MemberAccountService} for the inbox view of a member's
     * notification history.
     *
     * @param recipientUserId the recipient's user id
     * @return all notifications for the recipient
     */
    List<Notification> findByRecipient(int recipientUserId);

    /**
     * @return the next available notification id
     */
    int nextId();
}
