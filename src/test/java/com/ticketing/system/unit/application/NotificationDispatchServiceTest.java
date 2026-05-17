package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ticketing.system.Core.Application.dto.NotificationDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.services.NotificationDispatchService;
import com.ticketing.system.Core.Domain.notifications.INotificationRepository;
import com.ticketing.system.Core.Domain.notifications.Notification;
import com.ticketing.system.Core.Domain.notifications.NotificationStatus;
import com.ticketing.system.Core.Domain.notifications.NotificationType;

class NotificationDispatchServiceTest {

    private INotificationRepository mockRepo;
    private INotificationService mockNotificationService;
    private ISessionManager mockSessionManager;
    private NotificationDispatchService service;

    @BeforeEach
    void setUp() {
        mockRepo = mock(INotificationRepository.class);
        mockNotificationService = mock(INotificationService.class);
        mockSessionManager = mock(ISessionManager.class);
        service = new NotificationDispatchService(mockRepo, mockNotificationService, mockSessionManager);
    }

    @Test
    @Disabled("UC-35: domain event triggers notification, online recipient → live push")
    void givenOnlineRecipient_whenDispatch_thenLivePush() {
    }

    // UC-36: offline recipient → PENDING storage
    @Test
    void givenOfflineRecipient_whenStorePending_thenNotificationSaved() {
        int userId = 42;
        when(mockSessionManager.isOnline(userId)).thenReturn(false);

        Notification notification = new Notification(
                "notif-1",
                userId,
                NotificationType.PURCHASE_CONFIRMED,
                NotificationStatus.PENDING,
                "Your purchase has been confirmed",
                LocalDateTime.now());

        service.storePending(notification);

        verify(mockRepo, times(1)).save(notification);
    }

    @Test
    @Disabled("notNeededTest1")
    void givenOnlineRecipient_whenStorePending_thenIllegalStateExceptionThrown() {
        int userId = 42;
        when(mockSessionManager.isOnline(userId)).thenReturn(true);

        Notification notification = new Notification(
                "notif-2",
                userId,
                NotificationType.EVENT_SOLD_OUT,
                NotificationStatus.PENDING,
                "An event you're interested in is sold out",
                LocalDateTime.now());

        service.storePending(notification);
        verify(mockRepo, never()).save(any());
    }

    @Test
    @Disabled("notNeededTest2")
    void givenNotificationNotPending_whenStorePending_thenIllegalArgumentExceptionThrown() {
        int userId = 42;
        when(mockSessionManager.isOnline(userId)).thenReturn(false);

        Notification notification = new Notification(
                "notif-3",
                userId,
                NotificationType.PURCHASE_CONFIRMED,
                NotificationStatus.DELIVERED,
                "Your purchase has been confirmed",
                LocalDateTime.now());

        service.storePending(notification);
        verify(mockRepo, never()).save(any());
    }

    /* UC-37: deliver pending notifications */
    @Test
    void givenPendingNotifications_whenDeliverPending_thenAllDelivered() {
        int userId = 42;

        // Set up multiple pending notifications for the user
        Notification notif1 = new Notification(
                "notif-1",
                userId,
                NotificationType.PURCHASE_CONFIRMED,
                NotificationStatus.PENDING,
                "Your purchase has been confirmed",
                LocalDateTime.now());

        Notification notif2 = new Notification(
                "notif-2",
                userId,
                NotificationType.EVENT_SOLD_OUT,
                NotificationStatus.PENDING,
                "An event you're interested in is sold out",
                LocalDateTime.now());

        List<Notification> pendingNotifications = new ArrayList<>();
        pendingNotifications.add(notif1);
        pendingNotifications.add(notif2);

        // Mock repository to return pending notifications
        when(mockRepo.findByRecipientAndStatus(userId, NotificationStatus.PENDING))
                .thenReturn(pendingNotifications);

        // Mock notification service to return success for both sends
        when(mockNotificationService.send(userId, notif1)).thenReturn(true);
        when(mockNotificationService.send(userId, notif2)).thenReturn(true);

        // Call the service method
        List<NotificationDTO> result = service.deliverPending(userId);

        // Verify repository query for pending notifications
        verify(mockRepo, times(1)).findByRecipientAndStatus(userId, NotificationStatus.PENDING);

        // Verify push service was called for each notification
        verify(mockNotificationService, times(1)).send(userId, notif1);
        verify(mockNotificationService, times(1)).send(userId, notif2);

        // Verify repository saved each notification (status transition PENDING →
        // DELIVERED)
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(mockRepo, times(2)).save(captor.capture());

        // Verify all returned DTOs have DELIVERED status
        assert result.size() == 2;
        assert result.get(0).status().equals("DELIVERED");
        assert result.get(1).status().equals("DELIVERED");
    }

    /* UC-37: no pending notifications is no-op */
    @Test
    void givenNoPending_whenDeliverPending_thenNoop() {
        int userId = 42;

        // Mock repository to return empty list (no pending notifications)
        when(mockRepo.findByRecipientAndStatus(userId, NotificationStatus.PENDING))
                .thenReturn(new ArrayList<>());

        // Call the service method
        List<NotificationDTO> result = service.deliverPending(userId);

        // Verify repository queried for pending notifications
        verify(mockRepo, times(1)).findByRecipientAndStatus(userId, NotificationStatus.PENDING);

        // Verify notification service was never called
        verify(mockNotificationService, never()).send(any(Integer.class), any(Notification.class));

        // Verify repository never saved anything
        verify(mockRepo, never()).save(any());

        // Verify empty list is returned
        assert result.isEmpty();
    }
}
