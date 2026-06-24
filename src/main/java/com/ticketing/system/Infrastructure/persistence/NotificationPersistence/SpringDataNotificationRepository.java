package com.ticketing.system.Infrastructure.persistence.NotificationPersistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ticketing.system.Core.Domain.notifications.Notification;
import com.ticketing.system.Core.Domain.notifications.NotificationStatus;

/**
 * Spring Data JPA repository for {@link Notification} — the auto-implemented SQL backing
 * {@link JpaNotificationRepository}. The application layer never sees this type; it depends
 * only on the {@code INotificationRepository} domain port. The {@code data} payload is mapped
 * by {@code NotificationDataJsonConverter}, so it round-trips as a JSON text column.
 */
public interface SpringDataNotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findByRecipientUserIdAndStatus(int recipientUserId, NotificationStatus status);

    List<Notification> findByRecipientUserId(int recipientUserId);
}
