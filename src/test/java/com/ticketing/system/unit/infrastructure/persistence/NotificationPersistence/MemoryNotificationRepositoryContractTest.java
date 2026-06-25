package com.ticketing.system.unit.infrastructure.persistence.NotificationPersistence;

import com.ticketing.system.Core.Domain.notifications.INotificationRepository;
import com.ticketing.system.Infrastructure.persistence.NotificationPersistence.MemoryNotificationRepository;

class MemoryNotificationRepositoryContractTest extends INotificationRepositoryContractTest {

    @Override
    protected INotificationRepository newRepository() {
        return new MemoryNotificationRepository();
    }
}
