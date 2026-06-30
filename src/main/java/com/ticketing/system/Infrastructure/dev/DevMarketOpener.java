package com.ticketing.system.Infrastructure.dev;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.services.SystemAdminService;

import lombok.extern.slf4j.Slf4j;

/**
 * Opens the trading market automatically in the {@code dev} profile so the app — and the demo
 * seeders' purchase flows — work out of the box without an admin manually opening it. Production
 * never runs this: a real operator opens the market through UC-32 (made reliable by the WSEP
 * handshake retry).
 *
 * <p>Ordering matters because reserve/checkout are gated on an OPEN market: {@code @Order(1)} places
 * this after {@code PlatformInitializationRunner} (@Order(0), which brings the platform to READY) and
 * before the purchasing {@code ScenarioRunner} (@Order(2)), so the market is OPEN before the scenario
 * attempts any reservation or checkout.
 *
 * <p>Delegates to the system-internal {@link SystemAdminService#ensureMarketOpen()} (idempotent, and
 * the handshake it triggers now retries). If a transient external-service outage still leaves the
 * market closed here, {@code MarketSelfHealScheduler} keeps re-attempting until it opens (#455).
 */
@Component
@Profile("dev")
@Order(1)
@Slf4j
public class DevMarketOpener implements ApplicationRunner {

    private final SystemAdminService systemAdminService;

    public DevMarketOpener(SystemAdminService systemAdminService) {
        this.systemAdminService = systemAdminService;
    }

    @Override
    public void run(ApplicationArguments args) {
        systemAdminService.ensureMarketOpen();
        if (systemAdminService.isMarketOpen()) {
            log.info("[dev] Trading market auto-opened for development.");
        } else {
            log.warn("[dev] Market not open yet (platform or external services not ready); "
                    + "the self-heal scheduler will keep retrying.");
        }
    }
}
