package com.ticketing.system.Infrastructure.dev.seed;

import com.ticketing.system.Core.Application.dto.EventDetailDTO;
import com.ticketing.system.Core.Application.dto.ProductionCompanyDTO;
import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Application.services.CheckoutService;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Application.services.MessagingService;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Core.Domain.Admin.IAdminRepository;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.notifications.INotificationRepository;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Presentation.dev.DevUserSeeder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * via {@link Order @Order(2)}.
 *
 * <p>Behavior is selected by the {@code seed.mode} Spring property:
 * <ul>
 *   <li>{@code idempotent} (default) — skip if the sentinel user
 *       {@code naim.founder} already exists; seed otherwise.</li>
 *   <li>{@code wipe} — call {@link MemoryRepoCleaner#clearAll()},
 *       re-run {@code DevUserSeeder} to repopulate the two personas,
 *       then seed unconditionally.</li>
 *   <li>{@code off} — do nothing.</li>
 * </ul>
 *
 * <p>Trigger a wipe-and-reseed from the command line:
 * <pre>./mvnw spring-boot:run -Dspring-boot.run.profiles=dev \
 *     -Dspring-boot.run.arguments=--seed.mode=wipe</pre>
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
    private final IAdminRepository adminRepository;
    private final IEventRepository eventRepository;
    private final INotificationRepository notificationRepository;
    private final MemoryRepoCleaner memoryRepoCleaner;
    private final DevUserSeeder devUserSeeder;
    private final Clock clock;
    private final String seedMode;

    public DemoDataSeeder(
            AuthenticationService authenticationService,
            CompanyManagementService companyManagementService,
            EventManagementService eventManagementService,
            ReservationService reservationService,
            CheckoutService checkoutService,
            MessagingService messagingService,
            IUserRepository userRepository,
            IAdminRepository adminRepository,
            IEventRepository eventRepository,
            INotificationRepository notificationRepository,
            MemoryRepoCleaner memoryRepoCleaner,
            DevUserSeeder devUserSeeder,
            Clock clock,
            @Value("${seed.mode:idempotent}") String seedMode) {
        this.authenticationService = authenticationService;
        this.companyManagementService = companyManagementService;
        this.eventManagementService = eventManagementService;
        this.reservationService = reservationService;
        this.checkoutService = checkoutService;
        this.messagingService = messagingService;
        this.userRepository = userRepository;
        this.adminRepository = adminRepository;
        this.eventRepository = eventRepository;
        this.notificationRepository = notificationRepository;
        this.memoryRepoCleaner = memoryRepoCleaner;
        this.devUserSeeder = devUserSeeder;
        this.clock = clock;
        this.seedMode = seedMode == null ? "idempotent" : seedMode.trim().toLowerCase();
    }

    @Override
    public void run(ApplicationArguments args) {
        switch (seedMode) {
            case "off" -> log.info("seed.mode=off — demo data seed skipped");
            case "wipe" -> wipeAndReseed();
            default -> runIfNotSeeded();
        }
    }

    private void runIfNotSeeded() {
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

    /** Wipe every in-memory repo, re-seed dev personas, then seed demo data. */
    public void wipeAndReseed() {
        log.info("seed.mode=wipe — clearing all in-memory repositories");
        memoryRepoCleaner.clearAll();
        // DevUserSeeder runs first at boot via @Order(1), but a wipe nukes
        // those rows too — re-run it manually so dev.member and dev.admin
        // are back before the demo seed needs them.
        devUserSeeder.run(null);
        try {
            seed();
            log.info("demo data wiped and re-seeded successfully");
        } catch (RuntimeException e) {
            log.error("demo data re-seed failed mid-pipeline: {}", e.getMessage(), e);
        }
    }

    /** Run the full pipeline once. Used at boot and during wipe-and-reseed. */
    public void seed() {
        DemoClock demoClock = new DemoClock(clock);

        Map<String, SeededUser> users =
            new DemoUsers(authenticationService, userRepository).seed();

        Map<String, ProductionCompanyDTO> companies =
            new DemoCompanies(companyManagementService, users).seed();

        Map<String, EventDetailDTO> events =
            new DemoEvents(eventManagementService, eventRepository, users, companies, demoClock).seed();

        // Leaf seeders: tolerate stages whose feature is not built yet (UnsupportedOperationException)
        // by skipping them; any other exception still aborts the seed so real bugs surface.
        runTolerant("orders", () ->
            new DemoOrders(reservationService, checkoutService, users, events).seed());
        runTolerant("messaging", () ->
            new DemoMessaging(authenticationService, messagingService, userRepository, adminRepository,
                users, companies).seed());
        runTolerant("notifications", () ->
            new DemoNotifications(notificationRepository, users, demoClock).seed());
    }

    /** Runs a leaf sub-seeder, skipping it (with a log line) only when the feature it needs is not
     *  implemented yet. Any other exception propagates so genuine bugs still fail the seed. */
    private void runTolerant(String stage, Runnable subSeeder) {
        try {
            subSeeder.run();
        } catch (UnsupportedOperationException notImplemented) {
            log.warn("demo seed: skipped '{}' — feature not implemented yet ({})", stage, notImplemented.getMessage());
        }
    }

    private boolean alreadySeeded() {
        return userRepository.findByUsername(SENTINEL_USERNAME).isPresent();
    }
}
