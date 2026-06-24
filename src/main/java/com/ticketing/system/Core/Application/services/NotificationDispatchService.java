package com.ticketing.system.Core.Application.services;

import java.util.List;
import lombok.extern.slf4j.Slf4j;

import com.ticketing.system.Core.Application.dto.NotificationDTO;
import com.ticketing.system.Core.Application.interfaces.IPushNotificationService;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Domain.notifications.INotificationRepository;
import com.ticketing.system.Core.Domain.notifications.Notification;
import com.ticketing.system.Core.Domain.notifications.NotificationStatus;

// Owns the entire notification subsystem (UC-35, UC-36, UC-37).
// - UC-35: dispatch on domain event → check online → push live or hand off to UC-36
// - UC-36: persist as PENDING for offline recipients
// - UC-37: deliver pending on MemberLoggedIn event (PENDING → DELIVERED)
//
// The Domain-Events pattern was committed at UC-35 (see design_walkthrough_summary.md §6).
// Listener wiring (which domain events trigger dispatchFromEvent) lives in code, off-diagram.
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationDispatchService {

    private final INotificationRepository notificationRepository;
    private final IPushNotificationService pushService; // low-level push channel
    private final ISessionManager sessionManager;

    public NotificationDispatchService(
            INotificationRepository notificationRepository,
            IPushNotificationService pushService,
            ISessionManager sessionManager) {
        this.notificationRepository = notificationRepository;
        this.pushService = pushService;
        this.sessionManager = sessionManager;
    }

    /**
     * Central dispatch point for all notifications.
     * 1. Stores the notification as PENDING.
     * 2. Checks if the recipient is online.
     * 3. If online, attempts a live push via IPushNotificationService.
     * 4. Updates status to DELIVERED if push succeeds.
     */
    public void dispatch(Notification notification) {
        // Precondition: a freshly-built notification must be PENDING. Enforced before any
        // persistence/push so we never store an unexpected status or have markSent() throw
        // mid-flow (Notification.markSent() requires the current status to be PENDING).
        if (!notification.isPending()) {
            throw new IllegalArgumentException(
                    "dispatch() requires a PENDING notification, but got status: " + notification.getStatus());
        }

        log.info("Dispatching notification for userId={}, type={}",
                notification.getRecipientUserId(), notification.getType());

        // Step 1: Persist (UC-36 logic integrated)
        notificationRepository.save(notification);

        // Step 2: Online check (UC-35)
        int userId = notification.getRecipientUserId();
        if (sessionManager.isOnline(userId)) {
            log.debug("User {} is online, attempting live push", userId);
            
            // Step 3: Attempt delivery
            boolean pushSuccess = pushService.send(userId, notification);
            if (pushSuccess) {
                notification.markSent();
                notificationRepository.save(notification); // Update status to DELIVERED
                log.info("Notification id={} delivered live to userId={}", notification.getId(), userId);
            } else {
                log.warn("Failed to push notification id={} live to userId={}. Remaining PENDING.", 
                        notification.getId(), userId);
            }
        } else {
            log.info("User {} is offline. Notification id={} saved as PENDING for next login.", 
                    userId, notification.getId());
        }
    }

    // UC-35: triggered from a domain event.
    public void dispatchFromEvent(Object domainEvent) {
        // This will eventually resolve the notification details from the event 
        // and call dispatch(Notification).
        throw new UnsupportedOperationException("UC-35: not implemented");
    }

    // UC-36: store an offline notification in PENDING state.
    // Deprecated: logic now moved into dispatch()
    @Deprecated
    public void storePending(Notification notification) {
        dispatch(notification);
    }

    // UC-37: triggered by MemberLoggedIn event.
    public List<NotificationDTO> deliverPending(int userId) {
        log.info("Delivering pending notifications for userId={}", userId);

        List<Notification> pendingNotifications = notificationRepository.findByRecipientAndStatus(userId,
                NotificationStatus.PENDING);
        List<NotificationDTO> deliveredNotifications = new java.util.ArrayList<>();

        for (Notification notification : pendingNotifications) {
            boolean pushSuccess = pushService.send(userId, notification);
            if (pushSuccess) {
                notification.markSent();
                notificationRepository.save(notification); // persist the status change
                deliveredNotifications.add(notification.toDTO()); // convert to DTO for return
            } else {
                log.error(
                        "Failed to push notification id={} to userId={}. Will remain pending for next login attempt.",
                        notification.getId(), userId);
            }
        }
        log.info("Delivered {} pending notifications to userId={}", deliveredNotifications.size(), userId);
        return deliveredNotifications;
    }
}
