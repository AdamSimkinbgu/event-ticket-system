package com.ticketing.system.unit.infrastructure.persistence.UserPersistence;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Infrastructure.persistence.UserPersistence.JpaUserRepository;
import com.ticketing.system.Infrastructure.persistence.UserPersistence.SpringDataUserRepository;

/**
 * Runs the {@link IUserRepositoryContractTest} suite against the JPA adapter on an embedded H2
 * schema. {@code @ActiveProfiles("jpa")} activates {@link JpaUserRepository}; {@code @DataJpaTest}
 * provides H2 + real {@code users}, {@code company_appointments} and {@code appointment_permissions}
 * tables. Each test starts from an empty schema ({@link #cleanTable()}) so the suite is
 * order-independent; deleting users cascades to their owned appointments and permissions. CI never
 * touches a real database.
 */
@DataJpaTest
@ActiveProfiles("jpa")
@Import(JpaUserRepository.class)
class JpaUserRepositoryContractTest extends IUserRepositoryContractTest {

    @Autowired
    private JpaUserRepository repository;

    @Autowired
    private SpringDataUserRepository data;

    @BeforeEach
    void cleanTable() {
        data.deleteAll();
    }

    @Override
    protected IUserRepository newRepository() {
        return repository;
    }
}
