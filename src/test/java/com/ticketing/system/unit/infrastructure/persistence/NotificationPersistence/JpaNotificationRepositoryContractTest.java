package com.ticketing.system.unit.infrastructure.persistence.NotificationPersistence;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.ticketing.system.Core.Domain.notifications.INotificationRepository;
import com.ticketing.system.Infrastructure.persistence.NotificationPersistence.JpaNotificationRepository;
import com.ticketing.system.Infrastructure.persistence.NotificationPersistence.SpringDataNotificationRepository;

/**
 * Runs the {@link INotificationRepositoryContractTest} suite against the JPA adapter on an
 * embedded H2 schema. {@code @ActiveProfiles("jpa")} activates {@link JpaNotificationRepository};
 * {@code @DataJpaTest} provides H2 + a real {@code notifications} table (with the {@code data}
 * JSON column wired through {@code NotificationDataJsonConverter}). Each test starts from an
 * empty table ({@link #cleanTable()}) so the suite is order-independent. CI never touches a
 * real database.
 */
@DataJpaTest
@ActiveProfiles("jpa")
@Import(JpaNotificationRepository.class)
class JpaNotificationRepositoryContractTest extends INotificationRepositoryContractTest {

    @Autowired
    private JpaNotificationRepository repository;

    @Autowired
    private SpringDataNotificationRepository data;

    @BeforeEach
    void cleanTable() {
        data.deleteAll();
    }

    @Override
    protected INotificationRepository newRepository() {
        return repository;
    }
}
