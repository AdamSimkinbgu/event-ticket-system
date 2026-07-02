package com.ticketing.system.Infrastructure.scheduling;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Application.services.SystemAdminService;

import lombok.extern.slf4j.Slf4j;

/**
 * Self-heals the trading market in the {@code dev} profile (#455). If a transient external-service
 * outage — e.g. a cold-starting WSEP endpoint — left the market closed at boot, this periodically
 * re-attempts the open so the system recovers <i>without a restart</i> (V3 Req 6).
 *
 * <p>{@link SystemAdminService#ensureMarketOpen()} is idempotent and respects an admin's deliberate
 * close, so this is a no-op once the market is open (or was closed on purpose). Dev-only: production
 * opens the market through an admin (UC-32), which the WSEP handshake retry already makes reliable.
 */
@Component
@Profile("dev")
@Slf4j
public class MarketSelfHealScheduler {

    private final SystemAdminService systemAdminService;

    public MarketSelfHealScheduler(SystemAdminService systemAdminService) {
        this.systemAdminService = systemAdminService;
    }

    @Scheduled(fixedDelayString = "${market.self-heal-delay-ms:30000}")
    public void reopenMarketIfNeeded() {
        systemAdminService.ensureMarketOpen();
    }
}
