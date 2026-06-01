package com.ticketing.system.unit.domain;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Application.dto.InventorySelectionDTO;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import com.ticketing.system.Core.Domain.events.Seat;
import com.ticketing.system.Core.Domain.events.SeatStatus;
import com.ticketing.system.Core.Domain.events.SeatedZone;
import com.ticketing.system.Core.Domain.events.StandingZone;
import com.ticketing.system.support.BaseDomainTest;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.ticketing.system.Core.Domain.events.Seat;
import com.ticketing.system.Core.Domain.events.SeatStatus;
import com.ticketing.system.Core.Domain.events.SeatedZone;
import com.ticketing.system.Core.Domain.events.ZoneType;

public class InventoryZoneTest extends BaseDomainTest {

    private InventoryZone zone;

    private final int ZONE_ID = 5;

    @BeforeEach
    public void setUp() {
        zone = track(new StandingZone(ZONE_ID, "VIP", 10, 100));
    }

    @Test
    void GivenManyThreadsReserveSameInventoryZone_WhenReserveStanding_ThenDoNotOverReserve()
            throws InterruptedException {

        int capacity = 5;
        int numberOfThreads = 20;
        int quantityPerRequest = 1;

        InventoryZone realZone = track(new StandingZone(ZONE_ID, "VIP", capacity, 100.0));

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

                        realZone.reserve(InventorySelectionDTO.standing(quantityPerRequest));

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
    public void GivenHigherCapacity_WhenSetStandingCapacity_ThenCapacityUpdated() {
        zone.setStandingCapacity(15);

        assertEquals(15, zone.getCapacity());
        assertEquals(15, zone.getAvailableAmount());
    }

    @Test
    public void GivenLowerCapacityThanReserved_WhenSetStandingCapacity_ThenThrowsException() {
        zone.reserve(InventorySelectionDTO.standing(5));

        assertThrows(IllegalArgumentException.class, () -> zone.setStandingCapacity(4));
    }

    @Test
    public void GivenValidQuantity_WhenReleaseStanding_ThenTicketsReturnedToAvailableAmount() {
        zone.reserve(InventorySelectionDTO.standing(5));

        zone.release(InventorySelectionDTO.standing(2));

        assertEquals(7, zone.getAvailableAmount());
        assertEquals(3, zone.getReservedAmount());
    }

    @Test
    public void GivenReleaseMoreThanReserved_WhenReleaseStanding_ThenThrowsException() {
        zone.reserve(InventorySelectionDTO.standing(3));

        assertThrows(IllegalStateException.class, () -> zone.release(InventorySelectionDTO.standing(4)));
    }

    @Test
    public void GivenInvalidQuantity_WhenReserveStanding_ThenThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> zone.reserve(InventorySelectionDTO.standing(0)));
    }

    


    @Test
    void GivenReservedSeat_WhenReserveAgain_ThenRejected() {
        SeatedZone zone = new SeatedZone(
                1,
                "Orchestra",
                100,
                List.of(new Seat("A1", 0, 0)));

        zone.reserve(InventorySelectionDTO.seated(List.of("A1")));

        assertThrows(
                IllegalStateException.class,
                () -> zone.reserve(InventorySelectionDTO.seated(List.of("A1"))));
    }
    




    @Test
    void GivenStandingZone_WhenReserveSeatLabels_ThenRejected() {
        StandingZone zone = new StandingZone(1, "General", 100, 50);

        assertThrows(
                IllegalArgumentException.class,
                () -> zone.reserve(InventorySelectionDTO.seated(List.of("A1"))));
    }
    







    @Test
    void GivenSeatedZone_WhenCreated_ThenCapacityAndAvailabilityAreDerivedFromSeats() {
        SeatedZone seatedZone = track(new SeatedZone(
                1,
                "Orchestra",
                120.0,
                List.of(
                        new Seat("A1", 0, 0),
                        new Seat("A2", 1, 0),
                        new Seat("A3", 2, 0)
                )
        ));

        assertEquals(3, seatedZone.getCapacity());
        assertEquals(3, seatedZone.getAvailableAmount());
        assertEquals(0, seatedZone.getReservedAmount());
        assertEquals(0, seatedZone.getSoldAmount());
        assertEquals(ZoneType.SEATED, seatedZone.getZoneType());
    }

    @Test
    void GivenDuplicateSeatLabels_WhenCreateSeatedZone_ThenThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                new SeatedZone(
                        1,
                        "Orchestra",
                        120.0,
                        List.of(
                                new Seat("A1", 0, 0),
                                new Seat("A1", 1, 0)
                        )
                )
        );
    }

    @Test
    void GivenAvailableSeats_WhenReserveSeats_ThenSeatsBecomeReserved() {
        SeatedZone seatedZone = track(new SeatedZone(
                1,
                "Orchestra",
                120.0,
                List.of(
                        new Seat("A1", 0, 0),
                        new Seat("A2", 1, 0),
                        new Seat("A3", 2, 0)
                )
        ));

        seatedZone.reserve(InventorySelectionDTO.seated(List.of("A1", "A2")));

        assertEquals(SeatStatus.RESERVED, seatedZone.getSeat("A1").getStatus());
        assertEquals(SeatStatus.RESERVED, seatedZone.getSeat("A2").getStatus());
        assertEquals(SeatStatus.AVAILABLE, seatedZone.getSeat("A3").getStatus());
        assertEquals(1, seatedZone.getAvailableAmount());
        assertEquals(2, seatedZone.getReservedAmount());
    }

    @Test
    void GivenDuplicateSeatLabels_WhenReserveSeats_ThenThrowsAndDoesNotReserveSeat() {
        SeatedZone seatedZone = track(new SeatedZone(
                1,
                "Orchestra",
                120.0,
                List.of(
                        new Seat("A1", 0, 0),
                        new Seat("A2", 1, 0)
                )
        ));

        assertThrows(IllegalArgumentException.class, () ->
                seatedZone.reserve(InventorySelectionDTO.seated(List.of("A1", "A1")))
        );

        assertEquals(SeatStatus.AVAILABLE, seatedZone.getSeat("A1").getStatus());
        assertEquals(SeatStatus.AVAILABLE, seatedZone.getSeat("A2").getStatus());
        assertEquals(2, seatedZone.getAvailableAmount());
        assertEquals(0, seatedZone.getReservedAmount());
    }

    @Test
    void GivenUnknownSeat_WhenReserveSeats_ThenThrowsAndNoSeatIsReserved() {
        SeatedZone seatedZone = track(new SeatedZone(
                1,
                "Orchestra",
                120.0,
                List.of(
                        new Seat("A1", 0, 0),
                        new Seat("A2", 1, 0)
                )
        ));

        assertThrows(IllegalArgumentException.class, () ->
                seatedZone.reserve(InventorySelectionDTO.seated(List.of("A1", "Z9")))
        );

        assertEquals(SeatStatus.AVAILABLE, seatedZone.getSeat("A1").getStatus());
        assertEquals(SeatStatus.AVAILABLE, seatedZone.getSeat("A2").getStatus());
        assertEquals(2, seatedZone.getAvailableAmount());
    }

    @Test
    void GivenReservedSeat_WhenReserveAgain_ThenThrowsException() {
        SeatedZone seatedZone = track(new SeatedZone(
                1,
                "Orchestra",
                120.0,
                List.of(new Seat("A1", 0, 0))
        ));

        seatedZone.reserve(InventorySelectionDTO.seated(List.of("A1")));

        assertThrows(IllegalStateException.class, () ->
                seatedZone.reserve(InventorySelectionDTO.seated(List.of("A1")))
        );

        assertEquals(SeatStatus.RESERVED, seatedZone.getSeat("A1").getStatus());
    }

    @Test
    void GivenReservedSeats_WhenReleaseSeats_ThenSeatsBecomeAvailable() {
        SeatedZone seatedZone = track(new SeatedZone(
                1,
                "Orchestra",
                120.0,
                List.of(
                        new Seat("A1", 0, 0),
                        new Seat("A2", 1, 0)
                )
        ));

        seatedZone.reserve(InventorySelectionDTO.seated(List.of("A1", "A2")));
        seatedZone.release(InventorySelectionDTO.seated(List.of("A1")));

        assertEquals(SeatStatus.AVAILABLE, seatedZone.getSeat("A1").getStatus());
        assertEquals(SeatStatus.RESERVED, seatedZone.getSeat("A2").getStatus());
        assertEquals(1, seatedZone.getAvailableAmount());
        assertEquals(1, seatedZone.getReservedAmount());
    }

    @Test
    void GivenAvailableSeat_WhenReleaseSeats_ThenThrowsException() {
        SeatedZone seatedZone = track(new SeatedZone(
                1,
                "Orchestra",
                120.0,
                List.of(new Seat("A1", 0, 0))
        ));

        assertThrows(IllegalStateException.class, () ->
                seatedZone.release(InventorySelectionDTO.seated(List.of("A1")))
        );

        assertEquals(SeatStatus.AVAILABLE, seatedZone.getSeat("A1").getStatus());
    }

    @Test
    void GivenReservedSeats_WhenConfirmSale_ThenSeatsBecomeSold() {
        SeatedZone seatedZone = track(new SeatedZone(
                1,
                "Orchestra",
                120.0,
                List.of(
                        new Seat("A1", 0, 0),
                        new Seat("A2", 1, 0),
                        new Seat("A3", 2, 0)
                )
        ));

        seatedZone.reserve(InventorySelectionDTO.seated(List.of("A1", "A2")));
        seatedZone.confirmSale(InventorySelectionDTO.seated(List.of("A1", "A2")));

        assertEquals(SeatStatus.SOLD, seatedZone.getSeat("A1").getStatus());
        assertEquals(SeatStatus.SOLD, seatedZone.getSeat("A2").getStatus());
        assertEquals(SeatStatus.AVAILABLE, seatedZone.getSeat("A3").getStatus());
        assertEquals(1, seatedZone.getAvailableAmount());
        assertEquals(0, seatedZone.getReservedAmount());
        assertEquals(2, seatedZone.getSoldAmount());
    }

    @Test
    void GivenAvailableSeat_WhenConfirmSaleWithoutReservation_ThenThrowsException() {
        SeatedZone seatedZone = track(new SeatedZone(
                1,
                "Orchestra",
                120.0,
                List.of(new Seat("A1", 0, 0))
        ));

        assertThrows(IllegalStateException.class, () ->
                seatedZone.confirmSale(InventorySelectionDTO.seated(List.of("A1")))
        );

        assertEquals(SeatStatus.AVAILABLE, seatedZone.getSeat("A1").getStatus());
    }

    @Test
    void GivenStandingZone_WhenReserveSeatSelection_ThenThrowsException() {
        StandingZone standingZone = track(new StandingZone(1, "General Admission", 100, 50.0));

        assertThrows(IllegalArgumentException.class, () ->
                standingZone.reserve(InventorySelectionDTO.seated(List.of("A1")))
        );

        assertEquals(100, standingZone.getAvailableAmount());
    }

    @Test
    void GivenSeatedZone_WhenReserveStandingQuantity_ThenThrowsException() {
        SeatedZone seatedZone = track(new SeatedZone(
                1,
                "Orchestra",
                120.0,
                List.of(new Seat("A1", 0, 0))
        ));

        assertThrows(IllegalArgumentException.class, () ->
                seatedZone.reserve(InventorySelectionDTO.standing(1))
        );

        assertEquals(SeatStatus.AVAILABLE, seatedZone.getSeat("A1").getStatus());
    }

    @Test
    void GivenManyThreadsReserveSameSeat_WhenReserve_ThenOnlyOneSucceeds()
            throws InterruptedException {

        SeatedZone seatedZone = track(new SeatedZone(
                1,
                "Orchestra",
                120.0,
                List.of(new Seat("A1", 0, 0))
        ));

        int numberOfThreads = 20;

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        try {
            for (int i = 0; i < numberOfThreads; i++) {
                executorService.submit(() -> {
                    try {
                        readyLatch.countDown();
                        startLatch.await();

                        seatedZone.reserve(InventorySelectionDTO.seated(List.of("A1")));
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

            assertTrue(finished);
            assertEquals(1, successCount.get());
            assertEquals(numberOfThreads - 1, failureCount.get());
            assertEquals(SeatStatus.RESERVED, seatedZone.getSeat("A1").getStatus());
            assertEquals(0, seatedZone.getAvailableAmount());
            assertEquals(1, seatedZone.getReservedAmount());

        } finally {
            executorService.shutdownNow();
        }
    }



    @Test
    void GivenStandingZoneReservedTickets_WhenConfirmSale_ThenReservedDecreasesAndSoldIncreases() {
        StandingZone zone = new StandingZone(1, "General", 100, 50);

        zone.reserve(InventorySelectionDTO.standing(3));
        zone.confirmSale(InventorySelectionDTO.standing(3));

        assertEquals(0, zone.getReservedAmount());
        assertEquals(3, zone.getSoldAmount());
        assertEquals(97, zone.getAvailableAmount());
    }




}