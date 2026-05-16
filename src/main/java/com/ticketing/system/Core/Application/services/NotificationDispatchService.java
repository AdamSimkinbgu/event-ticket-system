package com.ticketing.system.Core.Application.services;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ticketing.system.Core.Application.dto.NotificationDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
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
public class NotificationDispatchService {

    private final INotificationRepository notificationRepository;
    private final INotificationService notificationService; // push channel port
    private final ISessionManager sessionManager;
    private final Logger logger = LoggerFactory.getLogger(NotificationDispatchService.class);

    public NotificationDispatchService(
            INotificationRepository notificationRepository,
            INotificationService notificationService,
            ISessionManager sessionManager
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.sessionManager = sessionManager;
    }

    // UC-35: triggered from a domain event. Resolves recipient, checks online state,
    // routes to live push (UC-35 online branch) or persists for later (UC-36 offline branch).
    // Parameter type stays Object until the team defines the domain-event base interface in code
    // (events are off-diagram per the design walkthrough).
    public void dispatchFromEvent(Object domainEvent) {
        throw new UnsupportedOperationException("UC-35: not implemented");
    }

    // UC-36: store an offline notification in PENDING state.
    public void storePending(Notification notification) {

        logger.info("Storing pending notification for userId={}, type={}", notification.getRecipientUserId(), notification.getType());
        try{
        notificationRepository.save(notification);
        }
        catch(Exception e){
            logger.error("Failed to store pending notification for userId={}, type={}. Error: {}", notification.getRecipientUserId(), notification.getType(), e.getMessage());
            throw e; // rethrow or handle as needed
        }
    }

    // UC-37: triggered by MemberLoggedIn event. Pushes all PENDING notifications for the user
    // and flips them to DELIVERED in bulk. Returns the delivered notifications so the caller
    // can show them to the just-logged-in user.
    public List<NotificationDTO> deliverPending(int userId) {
        logger.info("Delivering pending notifications for userId={}", userId);

        List<Notification> pendingNotifications = notificationRepository.findByRecipientAndStatus(userId, NotificationStatus.PENDING);
        List<NotificationDTO> deliveredNotifications = new java.util.ArrayList<>();

        for(Notification notification : pendingNotifications) {
            boolean pushSuccess = notificationService.send(userId, notification);
            if (pushSuccess) {
                notification.markDelivered();
                notificationRepository.save(notification); // persist the status change
                deliveredNotifications.add(notification.toDTO()); // convert to DTO for return
            } else {
                // Handle push failure as needed (e.g. log, retry later, etc.)
                logger.error("Failed to push notification id={} to userId={}. Will remain pending for next login attempt.", notification.getId(), userId);
            }
        }
        logger.info("Delivered {} pending notifications to userId={}", deliveredNotifications.size(), userId);
        return deliveredNotifications;

    }
}
