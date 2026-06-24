package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Application.dto.SystemAnalyticsDTO;
import com.ticketing.system.Core.Application.interfaces.ISystemMetrics;
import com.ticketing.system.Core.Application.interfaces.MetricType;
import com.ticketing.system.Core.Application.services.SystemAnalyticsService;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;

/**
 * Unit tests for {@link SystemAnalyticsService} (UC-46 / #43, #279). The metrics
 * port and order-receipt repository are stubbed so the rate / throughput math
 * and the purchase derivation are verified in isolation.
 */
class SystemAnalyticsServiceTest {

    private static final Instant T0 = Instant.parse("2026-01-01T12:00:00Z");
    private static final int WINDOW = 5;

    private ISystemMetrics metrics;
    private IOrderReceiptRepository orderReceiptRepository;
    private SystemAnalyticsService service;

    @BeforeEach
    void setUp() {
        metrics = mock(ISystemMetrics.class);
        orderReceiptRepository = mock(IOrderReceiptRepository.class);
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        service = new SystemAnalyticsService(metrics, orderReceiptRepository, clock, WINDOW);
    }

    @Test
    void computeAnalytics_computesRatesThroughputAndDerivesPurchasesFromReceipts() {
        when(metrics.count(MetricType.VISITOR_ENTRY, Duration.ofMinutes(WINDOW))).thenReturn(10L);
        when(metrics.count(MetricType.VISITOR_EXIT, Duration.ofMinutes(WINDOW))).thenReturn(5L);
        when(metrics.count(MetricType.REGISTRATION, Duration.ofMinutes(WINDOW))).thenReturn(0L);
        when(metrics.count(MetricType.RESERVATION, Duration.ofMinutes(WINDOW))).thenReturn(15L);
        when(metrics.count(MetricType.RESERVATION, Duration.ofMinutes(60))).thenReturn(40L);

        when(metrics.total(MetricType.VISITOR_ENTRY)).thenReturn(100L);
        when(metrics.total(MetricType.VISITOR_EXIT)).thenReturn(30L);
        when(metrics.total(MetricType.REGISTRATION)).thenReturn(8L);
        when(metrics.total(MetricType.RESERVATION)).thenReturn(50L);

        // Two receipts inside the 5-min window, one only inside the hour. Build the stubbed
        // receipts first — nesting receiptAt()'s own when(...) inside thenReturn(...) would
        // trip Mockito's UnfinishedStubbing check.
        LocalDateTime base = LocalDateTime.ofInstant(T0, ZoneOffset.UTC);
        OrderReceipt now = receiptAt(base);
        OrderReceipt twoMinAgo = receiptAt(base.minusMinutes(2));
        OrderReceipt thirtyMinAgo = receiptAt(base.minusMinutes(30));
        when(orderReceiptRepository.findGlobal(any())).thenReturn(List.of(now, twoMinAgo, thirtyMinAgo));

        SystemAnalyticsDTO dto = service.computeAnalytics();

        assertEquals(70L, dto.activeVisitors());
        assertEquals(2.0, dto.visitorEntryRatePerMin(), 1e-9);   // 10 / 5
        assertEquals(1.0, dto.visitorExitRatePerMin(), 1e-9);    // 5 / 5
        assertEquals(0.0, dto.registrationRatePerMin(), 1e-9);
        assertEquals(3.0, dto.reservationRatePerMin(), 1e-9);    // 15 / 5
        assertEquals(0.4, dto.purchaseRatePerMin(), 1e-9);       // 2 receipts / 5
        assertEquals(40L, dto.reservationThroughputHr());
        assertEquals(3L, dto.purchaseThroughputHr());            // all three within the hour
        assertEquals(100L, dto.totalVisitors());
        assertEquals(8L, dto.totalRegistrations());
        assertEquals(50L, dto.totalReservations());
        assertEquals(WINDOW, dto.windowMinutes());
    }

    @Test
    void computeAnalytics_clampsActiveVisitorsAtZero_whenExitsExceedEntries() {
        when(metrics.total(MetricType.VISITOR_ENTRY)).thenReturn(3L);
        when(metrics.total(MetricType.VISITOR_EXIT)).thenReturn(8L);
        when(orderReceiptRepository.findGlobal(any())).thenReturn(List.of());

        SystemAnalyticsDTO dto = service.computeAnalytics();

        assertEquals(0L, dto.activeVisitors());
        assertEquals(0.0, dto.purchaseRatePerMin(), 1e-9);
        assertEquals(0L, dto.purchaseThroughputHr());
    }

    private static OrderReceipt receiptAt(LocalDateTime purchaseTime) {
        OrderReceipt receipt = mock(OrderReceipt.class);
        when(receipt.getPurchaseTime()).thenReturn(purchaseTime);
        return receipt;
    }
}
