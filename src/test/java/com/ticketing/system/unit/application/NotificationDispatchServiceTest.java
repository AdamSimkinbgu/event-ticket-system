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

    @Test @Disabled("UC-35: domain event triggers notification, online recipient → live push")
    void givenOnlineRecipient_whenDispatch_thenLivePush() {}

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
            LocalDateTime.now()
        );
        
        service.storePending(notification);
        
        verify(mockRepo, times(1)).save(notification);
    }

    @Test @Disabled("notNeededTest1")
    void givenOnlineRecipient_whenStorePending_thenIllegalStateExceptionThrown() {
        int userId = 42;
        when(mockSessionManager.isOnline(userId)).thenReturn(true);
        
        Notification notification = new Notification(
            "notif-2",
            userId,
            NotificationType.EVENT_SOLD_OUT,
            NotificationStatus.PENDING,
            "An event you're interested in is sold out",
            LocalDateTime.now()
        );
        
         service.storePending(notification);
        verify(mockRepo, never()).save(any());
    }

    @Test @Disabled("notNeededTest2")
    void givenNotificationNotPending_whenStorePending_thenIllegalArgumentExceptionThrown() {
        int userId = 42;
        when(mockSessionManager.isOnline(userId)).thenReturn(false);
        
        Notification notification = new Notification(
            "notif-3",
            userId,
            NotificationType.PURCHASE_CONFIRMED,
            NotificationStatus.DELIVERED,
            "Your purchase has been confirmed",
            LocalDateTime.now()
        );
        
        service.storePending(notification);
        verify(mockRepo, never()).save(any());
    }

    @Test @Disabled("UC-37: deliverPending flips PENDING → DELIVERED in bulk")
    void givenPendingNotifications_whenDeliverPending_thenAllDelivered() {}

    @Test @Disabled("UC-37: empty PENDING is no-op")
    void givenNoPending_whenDeliverPending_thenNoop() {}
}

