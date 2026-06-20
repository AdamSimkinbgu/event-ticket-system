package com.ticketing.system.unit.infrastructure.seed;

import com.ticketing.system.Core.Domain.events.EventStatus;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.notifications.INotificationRepository;
import com.ticketing.system.Core.Domain.notifications.NotificationStatus;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Infrastructure.dev.seed.DemoCompanies;
import com.ticketing.system.Infrastructure.dev.seed.DemoUsers;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test: booting under {@code @ActiveProfiles("dev")} triggers
 * {@code DevUserSeeder} (@Order 1) then {@code DemoDataSeeder} (@Order 2)
 * during the Spring context start. The assertions check that the
 * resulting state has the seeded users, companies, events, and
 * notifications — i.e. the whole pipeline ran cleanly mid-boot.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Disabled("blocked on InMemoryNotificationService.send throwing UnsupportedOperationException — "
       + "every reservation flow routes through it and currently can't complete. Re-enable once "
       + "UC-35's in-memory push stub no-ops instead of throwing.")
class DemoDataSeederTest {

    @Autowired private IUserRepository userRepository;
    @Autowired private IProductionCompanyRepository companyRepository;
    @Autowired private IEventRepository eventRepository;
    @Autowired private INotificationRepository notificationRepository;
    @Autowired private IOrderReceiptRepository orderReceiptRepository;

    @Test
    void devUserSeederLeavesTheTwoDevPersonas() {
        assertTrue(userRepository.findByUsername("dev.member").isPresent(),
            "dev.member must be present after DevUserSeeder runs");
        assertTrue(userRepository.findByUsername("dev.admin").isPresent(),
            "dev.admin must be present after DevUserSeeder runs");
    }

    @Test
    void demoUsersAllPresent() {
        assertTrue(userRepository.findByUsername(DemoUsers.NAIM_FOUNDER).isPresent());
        assertTrue(userRepository.findByUsername(DemoUsers.MOSHE_FOUNDER).isPresent());
        assertTrue(userRepository.findByUsername(DemoUsers.BENTZION_FOUNDER).isPresent());
        assertTrue(userRepository.findByUsername(DemoUsers.FAOUR_MANAGER).isPresent());
        assertTrue(userRepository.findByUsername(DemoUsers.MOHAMAD_MANAGER).isPresent());
        assertTrue(userRepository.findByUsername(DemoUsers.BEN_MANAGER).isPresent());
        assertTrue(userRepository.findByUsername(DemoUsers.AVI_BUYER).isPresent());
        assertTrue(userRepository.findByUsername(DemoUsers.DANA_BUYER).isPresent());
        assertTrue(userRepository.findByUsername(DemoUsers.IDO_BUYER).isPresent());
        assertTrue(userRepository.findByUsername(DemoUsers.MAYA_BUYER).isPresent());
    }

    @Test
    void demoCompaniesAllActive() {
        var active = companyRepository.findActive();
        assertEquals(4, active.size(), "expected 4 seeded companies");
        var names = active.stream().map(c -> c.getName()).toList();
        assertTrue(names.contains(DemoCompanies.LIVE_NATION));
        assertTrue(names.contains(DemoCompanies.COCA_COLA));
        assertTrue(names.contains(DemoCompanies.HABIMA));
        assertTrue(names.contains(DemoCompanies.SHUNI));
    }

    @Test
    void demoEventsAreOnSale() {
        var onSale = eventRepository.findByStatus(EventStatus.ON_SALE);
        assertEquals(13, onSale.size(), "expected 13 events transitioned to ON_SALE");
    }

    @Test
    void demoOrdersWereCheckedOut() {
        int naimId = userRepository.findByUsername(DemoUsers.AVI_BUYER).orElseThrow().getUserId();
        assertFalse(orderReceiptRepository.findByHolderUserId(naimId).isEmpty(),
            "Avi should have at least one past order receipt");
    }

    @Test
    void notificationsPendingAndDeliveredExist() {
        int aviId = userRepository.findByUsername(DemoUsers.AVI_BUYER).orElseThrow().getUserId();
        assertFalse(notificationRepository.findByRecipientAndStatus(aviId, NotificationStatus.PENDING).isEmpty(),
            "Avi should have at least one PENDING notification");
        assertFalse(notificationRepository.findByRecipientAndStatus(aviId, NotificationStatus.DELIVERED).isEmpty(),
            "Avi should have at least one DELIVERED notification");
    }
}
