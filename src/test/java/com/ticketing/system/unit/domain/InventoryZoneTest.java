package com.ticketing.system.unit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Domain.events.InventoryZone;

public class InventoryZoneTest {

    private InventoryZone zone;

    private final int ZONE_ID = 5;

    @BeforeEach
    public void setUp() {
        zone = new InventoryZone(ZONE_ID, "VIP", 10, 100);
    }

    @Test
    void GivenManyThreadsReserveSameInventoryZone_WhenReserve_ThenDoNotOverReserve()
            throws InterruptedException {

        int capacity = 5;
        int numberOfThreads = 20;
        int quantityPerRequest = 1;

        InventoryZone realZone = new InventoryZone(ZONE_ID, "VIP", capacity, 100.0);

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        try {
            for (int i = 0; i < numberOfThreads; i = i + 1) {
                executorService.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();

                        realZone.reserve(quantityPerRequest);

                        successCount.incrementAndGet();

                    } catch (Exception e) {
                        failureCount.incrementAndGet();

                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown();

            boolean finished = doneLatch.await(5, TimeUnit.SECONDS);

            assertEquals(true, finished);
            assertEquals(capacity, successCount.get());
            assertEquals(numberOfThreads - capacity, failureCount.get());
            assertEquals(0, realZone.getAvailableAmount());
            assertEquals(capacity, realZone.getReservedAmount());

        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    public void GivenHigherCapacity_WhenSetCapacity_ThenCapacityUpdated() {
        zone.setCapacity(15);

        assertEquals(15, zone.getCapacity());
        assertEquals(15, zone.getAvailableAmount());
    }

    @Test
    public void GivenLowerCapacityThanReserved_WhenSetCapacity_ThenThrowsException() {
        zone.reserve(5);

        assertThrows(IllegalArgumentException.class, () -> zone.setCapacity(4));
    }

    @Test
    public void GivenValidQuantity_WhenRelease_ThenTicketsReturnedToAvailableAmount() {
        zone.reserve(5);

        zone.release(2);

        assertEquals(7, zone.getAvailableAmount());
        assertEquals(3, zone.getReservedAmount());
    }

    @Test
    public void GivenReleaseMoreThanReserved_WhenRelease_ThenThrowsException() {
        zone.reserve(3);

        assertThrows(IllegalStateException.class, () -> zone.release(4));
    }

    @Test
    public void GivenInvalidQuantity_WhenReserve_ThenThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> zone.reserve(0));
    }
}