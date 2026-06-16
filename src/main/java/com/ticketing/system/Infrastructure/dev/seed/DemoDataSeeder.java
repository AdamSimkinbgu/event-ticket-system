package com.ticketing.system.Infrastructure.dev.seed;

import com.ticketing.system.Core.Application.dto.EventDetailDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Application.services.CheckoutService;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Application.services.MessagingService;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.notifications.INotificationRepository;
import com.ticketing.system.Core.Domain.users.IUserRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;

/**
 * Orchestrator for the full demo data graph — users, companies,
 * appointments, events, orders, conversations, notifications. Runs
 * after {@code DevUserSeeder} (which seeds the two dev-panel personas)
 * via {@link Order @Order(2)}, and skips its work entirely when the
 * sentinel user {@code naim.founder} is already present.
 *
 * <p>The per-domain helpers ({@link DemoUsers}, {@link DemoCompanies},
 * {@link DemoEvents}, {@link DemoOrders}, {@link DemoMessaging},
 * {@link DemoNotifications}) are intentionally plain classes — they
 * share state by passing maps along the call chain, and we want a
 * fresh {@link DemoClock} per run so re-anchored dates stay current
 * after a reset.
 */
@Component
@Profile("dev")
@Order(2)
@Slf4j
public class DemoDataSeeder implements ApplicationRunner {

    private static final String SENTINEL_USERNAME = DemoUsers.NAIM_FOUNDER;

    private final AuthenticationService authenticationService;
    private final CompanyManagementService companyManagementService;
    private final EventManagementService eventManagementService;
    private final ReservationService reservationService;
    private final CheckoutService checkoutService;
    private final MessagingService messagingService;
    private final IUserRepository userRepository;
    private final IEventRepository eventRepository;
    private final INotificationRepository notificationRepository;
    private final Clock clock;

    public DemoDataSeeder(
            AuthenticationService authenticationService,
            CompanyManagementService companyManagementService,
            EventManagementService eventManagementService,
            ReservationService reservationService,
            CheckoutService checkoutService,
            MessagingService messagingService,
            IUserRepository userRepository,
            IEventRepository eventRepository,
            INotificationRepository notificationRepository,
            Clock clock) {
        this.authenticationService = authenticationService;
        this.companyManagementService = companyManagementService;
        this.eventManagementService = eventManagementService;
        this.reservationService = reservationService;
        this.checkoutService = checkoutService;
        this.messagingService = messagingService;
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (alreadySeeded()) {
            log.info("demo data already present (sentinel {} found), skipping seed", SENTINEL_USERNAME);
            return;
        }
        try {
            seed();
            log.info("demo data seeded successfully");
        } catch (RuntimeException e) {
            log.error("demo data seed failed mid-pipeline: {}", e.getMessage(), e);
        }
    }

    /** Run the full pipeline. Used at boot and on manual reset. */
    public void seed() {
        DemoClock demoClock = new DemoClock(clock);

        Map<String, SeededUser> users =
            new DemoUsers(authenticationService, userRepository).seed();

        Map<String, ProductionCompanyDTO> companies =
            new DemoCompanies(companyManagementService, users).seed();

        Map<String, EventDetailDTO> events =
            new DemoEvents(eventManagementService, eventRepository, users, companies, demoClock).seed();

        new DemoOrders(reservationService, checkoutService, users, events).seed();
        new DemoMessaging(authenticationService, messagingService, userRepository, users, companies).seed();
        new DemoNotifications(notificationRepository, users, demoClock).seed();
    }

    private boolean alreadySeeded() {
        return userRepository.findByUsername(SENTINEL_USERNAME).isPresent();
    }
}
