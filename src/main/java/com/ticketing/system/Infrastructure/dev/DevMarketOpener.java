package com.ticketing.system.Infrastructure.dev;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.dto.MarketControlRequestDTO;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.services.SystemAdminService;

import lombok.extern.slf4j.Slf4j;

/**
 * Opens the trading market automatically in the {@code dev} profile so the app —
 * and the demo seeders' purchase flows — work out of the box without an admin
 * manually opening it. Production never runs this: a real operator opens the
 * market through UC-32 once the platform has been verified.
 *
 * <p>Ordering matters now that reserve/checkout are gated on an OPEN market:
 * {@code @Order(1)} places this after {@code PlatformInitializationRunner}
 * (@Order(0), which brings the platform to READY) and before the purchasing
 * {@code ScenarioRunner} (@Order(2)). The market is therefore OPEN before the
 * scenario attempts any reservation or checkout.
 */
@Component
@Profile("dev")
@Order(1)
@Slf4j
public class DevMarketOpener implements ApplicationRunner {

    // Mirrors SystemAdminService.DEFAULT_ADMIN_ID — the default admin minted during init.
    private static final int DEFAULT_ADMIN_ID = 1;

    private final SystemAdminService systemAdminService;
    private final ISessionManager sessionManager;
    private final String adminUsername;

    public DevMarketOpener(SystemAdminService systemAdminService,
                           ISessionManager sessionManager,
                           @Value("${platform.admin.username}") String adminUsername) {
        this.systemAdminService = systemAdminService;
        this.sessionManager = sessionManager;
        this.adminUsername = adminUsername;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            // Mint a short-lived admin session for the default admin and open the market
            // through the real authorization path (requireSystemAdmin).
            String adminToken = sessionManager.generateAdminToken(DEFAULT_ADMIN_ID, adminUsername);
            systemAdminService.openMarket(new MarketControlRequestDTO("OPEN", "dev auto-open", adminToken));
            log.info("[dev] Trading market auto-opened for development.");
        } catch (RuntimeException e) {
            // Non-fatal: if the platform never reached READY (e.g. a stubbed service was
            // toggled unreachable), leave the market closed and let the dev open it manually.
            log.warn("[dev] Could not auto-open the market (platform may not be initialized): {}", e.getMessage());
        }
    }
}
