package com.ticketing.system.Core.Application.services;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.ticketing.system.Core.Application.dto.GlobalHistoryFiltersDTO;
import com.ticketing.system.Core.Application.dto.SystemAnalyticsDTO;
import com.ticketing.system.Core.Application.interfaces.ISystemMetrics;
import com.ticketing.system.Core.Application.interfaces.MetricType;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;

import lombok.extern.slf4j.Slf4j;

/**
 * Read-side aggregator for the System Analytics dashboard (UC-46 / #43, #279).
 *
 * <p>Pure query service. Visitor / registration / reservation activity comes
 * from the live {@link ISystemMetrics} counters; purchases are derived from the
 * immutable {@link OrderReceipt} history (real timestamps), so no checkout
 * instrumentation is needed. No token is taken — the admin route is
 * access-gated, mirroring {@code SystemAdminService.viewMarketState()}. Market
 * status itself is supplied separately by {@code SystemAdminService}.
 */
@Service
@Slf4j
public class SystemAnalyticsService {

    private static final int THROUGHPUT_WINDOW_MINUTES = 60;

    private final ISystemMetrics metrics;
    private final IOrderReceiptRepository orderReceiptRepository;
    private final Clock clock;
    private final int windowMinutes;

    public SystemAnalyticsService(
            ISystemMetrics metrics,
            IOrderReceiptRepository orderReceiptRepository,
            Clock clock,
            @Value("${analytics.rate-window-minutes:5}") int windowMinutes) {
        this.metrics = metrics;
        this.orderReceiptRepository = orderReceiptRepository;
        this.clock = clock;
        this.windowMinutes = windowMinutes > 0 ? windowMinutes : 5;
    }

    /** Builds the live analytics snapshot: rates over the trailing window, throughput over the hour. */
    public SystemAnalyticsDTO computeAnalytics() {
        Duration window = Duration.ofMinutes(windowMinutes);
        Duration hour = Duration.ofMinutes(THROUGHPUT_WINDOW_MINUTES);

        long entries = metrics.count(MetricType.VISITOR_ENTRY, window);
        long exits = metrics.count(MetricType.VISITOR_EXIT, window);
        long registrations = metrics.count(MetricType.REGISTRATION, window);
        long reservations = metrics.count(MetricType.RESERVATION, window);

        long activeVisitors = Math.max(0L,
                metrics.total(MetricType.VISITOR_ENTRY) - metrics.total(MetricType.VISITOR_EXIT));

        LocalDateTime now = LocalDateTime.now(clock);
        long purchasesWindow = countPurchasesSince(now.minusMinutes(windowMinutes));
        long purchasesHour = countPurchasesSince(now.minusMinutes(THROUGHPUT_WINDOW_MINUTES));

        SystemAnalyticsDTO dto = new SystemAnalyticsDTO(
                activeVisitors,
                perMinute(entries),
                perMinute(exits),
                perMinute(registrations),
                perMinute(reservations),
                perMinute(purchasesWindow),
                metrics.count(MetricType.RESERVATION, hour),
                purchasesHour,
                metrics.total(MetricType.VISITOR_ENTRY),
                metrics.total(MetricType.REGISTRATION),
                metrics.total(MetricType.RESERVATION),
                windowMinutes);
        log.debug("System analytics snapshot: {}", dto);
        return dto;
    }

    /** Counts immutable receipts whose purchaseTime is at or after {@code since}. */
    private long countPurchasesSince(LocalDateTime since) {
        // A fully-null filter matches every receipt (the date filters are LocalDate, too coarse
        // for minute-level windows), so we count by the receipt's own purchaseTime in memory.
        List<OrderReceipt> all = orderReceiptRepository.findGlobal(
                new GlobalHistoryFiltersDTO(null, null, null, null, null));
        long n = 0;
        for (OrderReceipt receipt : all) {
            LocalDateTime purchasedAt = receipt.getPurchaseTime();
            if (purchasedAt != null && !purchasedAt.isBefore(since)) {
                n++;
            }
        }
        return n;
    }

    private double perMinute(long countInWindow) {
        return windowMinutes <= 0 ? 0.0 : (double) countInWindow / windowMinutes;
    }
}
