package com.ticketing.system.Infrastructure.persistence;

import com.ticketing.system.Core.Domain.notifications.INotificationRepository;
import com.ticketing.system.Core.Domain.notifications.Notification;
import com.ticketing.system.Core.Domain.notifications.NotificationStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;


@Repository
public class MemoryNotificationRepository implements INotificationRepository {
    private final Map<String, Notification> storage = new HashMap<>();
    
    @Override
    public void save(Notification notification) {
        storage.put(notification.getId(), notification);
    }

    @Override
    public Notification findById(String id) {
        return storage.get(id);
    }

    @Override
    public List<Notification> findByRecipientAndStatus(int recipientUserId, NotificationStatus status) {
        return storage.values().stream()
                .filter(n -> n.getRecipientUserId() == recipientUserId)
                .filter(n -> n.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<Notification> findByRecipient(int recipientUserId) {
        return storage.values().stream()
                .filter(n -> n.getRecipientUserId() == recipientUserId)
                .collect(Collectors.toList());
    }
}
