package com.ticketing.system.Infrastructure.dev.seed.scenario;

import com.ticketing.system.Core.Application.services.AuthenticationService;
import com.ticketing.system.Core.Application.services.CatalogService;
import com.ticketing.system.Core.Application.services.CheckoutService;
import com.ticketing.system.Core.Application.services.CompanyManagementService;
import com.ticketing.system.Core.Application.services.EventManagementService;
import com.ticketing.system.Core.Application.services.MessagingService;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Infrastructure.dev.seed.DemoClock;
import com.ticketing.system.Infrastructure.dev.seed.MemoryRepoCleaner;
import com.ticketing.system.Infrastructure.dev.seed.SeedHarness;
import com.ticketing.system.Infrastructure.dev.seed.SeedReport;
import com.ticketing.system.Presentation.dev.DevUserSeeder;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Initializes the system from an editable scenario file at startup — the
 * config-driven replacement for the old hardcoded {@code DemoDataSeeder}.
 * Reads the file named by {@code seed.scenario}, parses it into operations, and
 * runs each through the real application services via {@link ScenarioOps},
 * classifying every step in a {@link SeedReport} (PASS / SKIPPED / FAIL /
 * BLOCKED) that is logged at the end. Because each line is a real service call,
 * the run doubles as a smoke test — a grader who edits the file and mistypes a
 * step sees exactly which line failed and why.
 *
 * <p>Properties:
 * <ul>
 *   <li>{@code seed.scenario} — the file to run (default
 *       {@code classpath:scenarios/demo.scenario}; the TA review scenario is
 *       {@code classpath:scenarios/review.scenario}; a filesystem file works
 *       too: {@code file:/path/to/my.scenario}).</li>
 *   <li>{@code seed.mode} — {@code off} (skip), {@code wipe} (clear repos +
 *       re-seed dev personas, then run), anything else runs the scenario (the
 *       in-memory dev repos start empty each boot).</li>
 *   <li>{@code seed.fail-fast} — {@code true} stops at the first unexpected
 *       error (the partial report is still logged); {@code false} (default)
 *       runs every line and collects failures.</li>
 * </ul>
 */
@Component
@Profile("dev")
@Order(2)
@Slf4j
public class ScenarioRunner implements ApplicationRunner {

    private final AuthenticationService authenticationService;
    private final CompanyManagementService companyManagementService;
    private final EventManagementService eventManagementService;
    private final ReservationService reservationService;
    private final CheckoutService checkoutService;
    private final CatalogService catalogService;
    private final MessagingService messagingService;
    private final IUserRepository userRepository;
    private final MemoryRepoCleaner memoryRepoCleaner;
    private final DevUserSeeder devUserSeeder;
    private final ResourceLoader resourceLoader;
    private final java.time.Clock clock;
    private final String seedMode;
    private final boolean failFast;
    private final String scenarioLocation;

    public ScenarioRunner(
            AuthenticationService authenticationService,
            CompanyManagementService companyManagementService,
            EventManagementService eventManagementService,
            ReservationService reservationService,
            CheckoutService checkoutService,
            CatalogService catalogService,
            MessagingService messagingService,
            IUserRepository userRepository,
            MemoryRepoCleaner memoryRepoCleaner,
            DevUserSeeder devUserSeeder,
            ResourceLoader resourceLoader,
            java.time.Clock clock,
            @Value("${seed.mode:idempotent}") String seedMode,
            @Value("${seed.fail-fast:false}") boolean failFast,
            @Value("${seed.scenario:classpath:scenarios/demo.scenario}") String scenarioLocation) {
        this.authenticationService = authenticationService;
        this.companyManagementService = companyManagementService;
        this.eventManagementService = eventManagementService;
        this.reservationService = reservationService;
        this.checkoutService = checkoutService;
        this.catalogService = catalogService;
        this.messagingService = messagingService;
        this.userRepository = userRepository;
        this.memoryRepoCleaner = memoryRepoCleaner;
        this.devUserSeeder = devUserSeeder;
        this.resourceLoader = resourceLoader;
        this.clock = clock;
        this.seedMode = seedMode == null ? "idempotent" : seedMode.trim().toLowerCase();
        this.failFast = failFast;
        this.scenarioLocation = scenarioLocation;
    }

    @Override
    public void run(ApplicationArguments args) {
        switch (seedMode) {
            case "off" -> log.info("seed.mode=off — scenario init skipped");
            case "wipe" -> {
                log.info("seed.mode=wipe — clearing in-memory repositories before init");
                memoryRepoCleaner.clearAll();
                devUserSeeder.run(null);   // a wipe nukes dev.member/dev.admin too
                runScenario();
            }
            default -> runScenario();
        }
    }

    private void runScenario() {
        String text;
        try {
            Resource resource = resourceLoader.getResource(scenarioLocation);
            if (!resource.exists()) {
                log.error("scenario file not found: {} — nothing seeded", scenarioLocation);
                return;
            }
            try (var in = resource.getInputStream()) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("could not read scenario file {}: {}", scenarioLocation, e.getMessage(), e);
            return;
        }

        List<ScenarioCommand> commands = new ScenarioParser().parse(text);
        SeedReport report = new SeedReport();
        SeedHarness harness = new SeedHarness(report, failFast);
        ScenarioContext ctx = new ScenarioContext();
        ScenarioOps ops = new ScenarioOps(
            authenticationService, companyManagementService, eventManagementService,
            reservationService, checkoutService, catalogService, messagingService,
            userRepository, new DemoClock(clock));

        log.info("running scenario '{}' ({} operations)", scenarioLocation, commands.size());
        try {
            for (ScenarioCommand cmd : commands) {
                ops.execute(cmd, ctx, harness);
            }
        } catch (SeedHarness.SeedAbortException abort) {
            log.error("scenario aborted (fail-fast): {}", abort.getMessage());
        } finally {
            String summary = report.render();
            if (report.hasFailures()) {
                log.warn("scenario '{}' finished WITH FAILURES — see report below.{}", scenarioLocation, summary);
            } else {
                log.info("scenario '{}' finished clean.{}", scenarioLocation, summary);
            }
        }
    }
}
