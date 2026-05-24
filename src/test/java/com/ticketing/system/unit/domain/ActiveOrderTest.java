package com.ticketing.system.unit.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;

// Unit tests for the ActiveOrder aggregate.
class ActiveOrderTest {

    private final int USER_ID = 1;
    private final int EVENT_ID = 10;
    private final int ZONE_ID = 5;

    @Test
    @Disabled("V1: addReservation appends CartLineItem (UC-5)")
    void givenEmptyOrder_whenAddReservation_thenLineAdded() {}

    @Test
    @Disabled("V1: validateCanCheckout rejects empty (UC-10)")
    void givenEmptyOrder_whenValidateCanCheckout_thenThrows() {}

    @Test
    @Disabled("V1: validateCanCheckout rejects expired (UC-10)")
    void givenExpiredItem_whenValidateCanCheckout_thenThrows() {}

    @Test
    @Disabled("V1: ReturnToStock empties order (UC-2 / UC-14)")
    void givenOrderWithItems_whenReturnToStock_thenOrderEmpty() {}

    @Test
    void GivenManyThreadsRemoveTicketsFromSameActiveOrder_WhenRemoveTickets_ThenDoNotRemoveMoreThanExists()
            throws InterruptedException {

        int initialTickets = 5;
        int numberOfThreads = 20;
        int quantityPerRemove = 1;

        ActiveOrder realActiveOrder = new ActiveOrder(USER_ID);

        realActiveOrder.addReservation(
                EVENT_ID,
                ZONE_ID,
                initialTickets,
                100.0,
                LocalDateTime.now()
        );

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

                        realActiveOrder.removeTickets(
                                EVENT_ID,
                                ZONE_ID,
                                quantityPerRemove
                        );

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
            assertEquals(initialTickets, successCount.get());
            assertEquals(numberOfThreads - initialTickets, failureCount.get());
            assertEquals(0, realActiveOrder.countTickets(EVENT_ID, ZONE_ID));

        } finally {
            executorService.shutdownNow();
        }
    }
}