package com.ticketing.system.unit.application;

import com.ticketing.system.Core.Application.dto.ReservationResultDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

public class ReservationServiceTest {

    private IEventRepository eventRepository;
    private IActiveOrderRepository activeOrderRepository;
    private ISessionManager sessionManager;
    private INotificationService notificationService;

    private ReservationService reservationService;

    private Event event;
    private InventoryZone zone;
    private ActiveOrder activeOrder;

    private final String VALID_TOKEN = "valid-token";
    private final int USER_ID = 1;
    private final int EVENT_ID = 10;
    private final int ZONE_ID = 5;
    private final int QUANTITY = 2;

    @BeforeEach
    void setUp() {
        eventRepository = mock(IEventRepository.class);
        activeOrderRepository = mock(IActiveOrderRepository.class);
        sessionManager = mock(ISessionManager.class);
        notificationService = mock(INotificationService.class);

        event = mock(Event.class);
        zone = mock(InventoryZone.class);
        activeOrder = mock(ActiveOrder.class);

        reservationService = new ReservationService(
                eventRepository,
                activeOrderRepository,
                sessionManager,
                notificationService
        );
    }

    
    @Test
    void GivenValidRequest_WhenRemoveReservedTickets_ThenReturnReservationResult() {
        when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(eventRepository.findById(EVENT_ID)).thenReturn(event);
        when(event.getZone(ZONE_ID)).thenReturn(zone);
        when(activeOrderRepository.getByUserId(USER_ID)).thenReturn(activeOrder);
        when(activeOrder.hasReservationForEvent(EVENT_ID)).thenReturn(true);
        when(activeOrder.countTickets(EVENT_ID, ZONE_ID)).thenReturn(QUANTITY);

        ReservationResultDTO result =
                reservationService.removeReservedTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY);

        assertEquals(EVENT_ID, result.getEventId());
    }

    @Test
    void GivenMissingToken_WhenRemoveReservedTickets_ThenThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                reservationService.removeReservedTickets(null, EVENT_ID, ZONE_ID, QUANTITY)
        );
    }

    @Test
    void GivenInvalidToken_WhenRemoveReservedTickets_ThenThrowException() {
        when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
                reservationService.removeReservedTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
        );
    }

    @Test
    void GivenInvalidQuantity_WhenRemoveReservedTickets_ThenThrowException() {
        when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);

        assertThrows(IllegalArgumentException.class, () ->
                reservationService.removeReservedTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, 0)
        );
    }

    @Test
    void GivenEventDoesNotExist_WhenRemoveReservedTickets_ThenThrowException() {
        when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(eventRepository.findById(EVENT_ID)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                reservationService.removeReservedTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
        );
    }

    @Test
    void GivenZoneDoesNotExist_WhenRemoveReservedTickets_ThenThrowException() {
        when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(eventRepository.findById(EVENT_ID)).thenReturn(event);
        when(event.getZone(ZONE_ID)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                reservationService.removeReservedTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
        );
    }

    @Test
    void GivenActiveOrderDoesNotExist_WhenRemoveReservedTickets_ThenThrowException() {
        when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(eventRepository.findById(EVENT_ID)).thenReturn(event);
        when(event.getZone(ZONE_ID)).thenReturn(zone);
        when(activeOrderRepository.getByUserId(USER_ID)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                reservationService.removeReservedTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
        );
    }

  @Test
void GivenOrderDoesNotContainEvent_WhenRemoveReservedTickets_ThenThrowException() {
    when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
    when(eventRepository.findById(EVENT_ID)).thenReturn(event);
    when(event.getZone(ZONE_ID)).thenReturn(zone);
    when(activeOrderRepository.getByUserId(USER_ID)).thenReturn(activeOrder);

    doThrow(new IllegalArgumentException("Active order does not contain this event"))
            .when(activeOrder)
            .removeTickets(EVENT_ID, ZONE_ID, QUANTITY);

    Exception exception = assertThrows(IllegalArgumentException.class, () ->
            reservationService.removeReservedTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
    );

    assertEquals("Active order does not contain this event", exception.getMessage());
}

    @Test
void GivenNotEnoughReservedTickets_WhenRemoveReservedTickets_ThenThrowException() {
    when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
    when(eventRepository.findById(EVENT_ID)).thenReturn(event);
    when(event.getZone(ZONE_ID)).thenReturn(zone);
    when(activeOrderRepository.getByUserId(USER_ID)).thenReturn(activeOrder);

    doThrow(new IllegalArgumentException("Not enough reserved tickets to remove"))
            .when(activeOrder)
            .removeTickets(EVENT_ID, ZONE_ID, QUANTITY);

    Exception exception = assertThrows(IllegalArgumentException.class, () ->
            reservationService.removeReservedTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
    );

    assertEquals("Not enough reserved tickets to remove", exception.getMessage());
}@Test
void GivenValidMemberRequest_WhenReserveTicketsForMember_ThenReturnReservationResult() {
    when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
    when(eventRepository.findById(EVENT_ID)).thenReturn(event);
    when(event.getZone(ZONE_ID)).thenReturn(zone);
    when(activeOrderRepository.getByUserId(USER_ID)).thenReturn(activeOrder);

    when(activeOrder.hasReservationForEvent(EVENT_ID)).thenReturn(false);
    when(zone.getAvailableAmount()).thenReturn(10);
    when(zone.getprice()).thenReturn(100.0);

    ReservationResultDTO result =
            reservationService.reserveTicketsForMember(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY);

    assertEquals(EVENT_ID, result.getEventId());
}

@Test
void GivenMissingToken_WhenReserveTicketsForMember_ThenThrowException() {
    assertThrows(IllegalArgumentException.class, () ->
            reservationService.reserveTicketsForMember(null, EVENT_ID, ZONE_ID, QUANTITY)
    );
}

@Test
void GivenInvalidToken_WhenReserveTicketsForMember_ThenThrowException() {
    when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(false);

    assertThrows(IllegalStateException.class, () ->
            reservationService.reserveTicketsForMember(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
    );
}

@Test
void GivenInvalidUserId_WhenReserveTicketsForMember_ThenThrowException() {
    when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(VALID_TOKEN)).thenReturn(0);

    assertThrows(IllegalArgumentException.class, () ->
            reservationService.reserveTicketsForMember(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
    );
}

@Test
void GivenInvalidQuantity_WhenReserveTicketsForMember_ThenThrowException() {
    when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);

    assertThrows(IllegalArgumentException.class, () ->
            reservationService.reserveTicketsForMember(VALID_TOKEN, EVENT_ID, ZONE_ID, 0)
    );
}

@Test
void GivenEventDoesNotExist_WhenReserveTicketsForMember_ThenThrowException() {
    when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
    when(eventRepository.findById(EVENT_ID)).thenReturn(null);

    assertThrows(IllegalArgumentException.class, () ->
            reservationService.reserveTicketsForMember(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
    );
}

@Test
void GivenZoneDoesNotExist_WhenReserveTicketsForMember_ThenThrowException() {
    when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
    when(eventRepository.findById(EVENT_ID)).thenReturn(event);
    when(event.getZone(ZONE_ID)).thenReturn(null);

    assertThrows(IllegalArgumentException.class, () ->
            reservationService.reserveTicketsForMember(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
    );
}

@Test
void GivenUserAlreadyHasReservationForEvent_WhenReserveTicketsForMember_ThenThrowException() {
    when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
    when(eventRepository.findById(EVENT_ID)).thenReturn(event);
    when(event.getZone(ZONE_ID)).thenReturn(zone);
    when(activeOrderRepository.getByUserId(USER_ID)).thenReturn(activeOrder);

    when(activeOrder.hasReservationForEvent(EVENT_ID)).thenReturn(true);

    assertThrows(IllegalStateException.class, () ->
            reservationService.reserveTicketsForMember(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
    );
}

@Test
void GivenNotEnoughTickets_WhenReserveTicketsForMember_ThenThrowException() {
    when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
    when(eventRepository.findById(EVENT_ID)).thenReturn(event);
    when(event.getZone(ZONE_ID)).thenReturn(zone);
    when(activeOrderRepository.getByUserId(USER_ID)).thenReturn(activeOrder);

    when(activeOrder.hasReservationForEvent(EVENT_ID)).thenReturn(false);

    when(zone.reserve(QUANTITY))
            .thenThrow(new IllegalStateException("remaining 1 tickets available"));

    assertThrows(IllegalStateException.class, () ->
            reservationService.reserveTicketsForMember(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
    );
}
@Test
void GivenNoActiveOrder_WhenReserveTicketsForMember_ThenCreateNewOrderAndReturnResult() {
    when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
    when(eventRepository.findById(EVENT_ID)).thenReturn(event);
    when(event.getZone(ZONE_ID)).thenReturn(zone);
    when(activeOrderRepository.getByUserId(USER_ID)).thenReturn(null);

    when(zone.getAvailableAmount()).thenReturn(10);
    when(zone.getprice()).thenReturn(100.0);

    ReservationResultDTO result =
            reservationService.reserveTicketsForMember(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY);

    assertEquals(EVENT_ID, result.getEventId());
}
@Test
void GivenValidGuestRequest_WhenReserveTicketsForGuest_ThenReturnReservationResult() {
    String sessionId = "guest-session";

    when(eventRepository.findById(EVENT_ID)).thenReturn(event);
    when(event.getZone(ZONE_ID)).thenReturn(zone);
    when(activeOrderRepository.getBySessionId(sessionId)).thenReturn(Optional.of(activeOrder));

    when(activeOrder.hasReservationForEvent(EVENT_ID)).thenReturn(false);
    when(zone.getAvailableAmount()).thenReturn(10);
    when(zone.getprice()).thenReturn(100.0);

    ReservationResultDTO result =
            reservationService.reserveTicketsForGuest(sessionId, EVENT_ID, ZONE_ID, QUANTITY);

    assertEquals(EVENT_ID, result.getEventId());
}

@Test
void GivenMissingSessionId_WhenReserveTicketsForGuest_ThenThrowException() {
    assertThrows(IllegalArgumentException.class, () ->
            reservationService.reserveTicketsForGuest(null, EVENT_ID, ZONE_ID, QUANTITY)
    );
}

@Test
void GivenBlankSessionId_WhenReserveTicketsForGuest_ThenThrowException() {
    assertThrows(IllegalArgumentException.class, () ->
            reservationService.reserveTicketsForGuest("   ", EVENT_ID, ZONE_ID, QUANTITY)
    );
}

@Test
void GivenInvalidQuantity_WhenReserveTicketsForGuest_ThenThrowException() {
    String sessionId = "guest-session";

    assertThrows(IllegalArgumentException.class, () ->
            reservationService.reserveTicketsForGuest(sessionId, EVENT_ID, ZONE_ID, 0)
    );
}

@Test
void GivenEventDoesNotExist_WhenReserveTicketsForGuest_ThenThrowException() {
    String sessionId = "guest-session";

    when(eventRepository.findById(EVENT_ID)).thenReturn(null);

    assertThrows(IllegalArgumentException.class, () ->
            reservationService.reserveTicketsForGuest(sessionId, EVENT_ID, ZONE_ID, QUANTITY)
    );
}

@Test
void GivenZoneDoesNotExist_WhenReserveTicketsForGuest_ThenThrowException() {
    String sessionId = "guest-session";

    when(eventRepository.findById(EVENT_ID)).thenReturn(event);
    when(event.getZone(ZONE_ID)).thenReturn(null);

    assertThrows(IllegalArgumentException.class, () ->
            reservationService.reserveTicketsForGuest(sessionId, EVENT_ID, ZONE_ID, QUANTITY)
    );
}

@Test
void GivenGuestAlreadyHasReservationForEvent_WhenReserveTicketsForGuest_ThenThrowException() {
    String sessionId = "guest-session";

    when(eventRepository.findById(EVENT_ID)).thenReturn(event);
    when(event.getZone(ZONE_ID)).thenReturn(zone);
    when(activeOrderRepository.getBySessionId(sessionId)).thenReturn(Optional.of(activeOrder));

    when(activeOrder.hasReservationForEvent(EVENT_ID)).thenReturn(true);

    assertThrows(IllegalStateException.class, () ->
            reservationService.reserveTicketsForGuest(sessionId, EVENT_ID, ZONE_ID, QUANTITY)
    );
}

@Test
void GivenNotEnoughTickets_WhenReserveTicketsForGuest_ThenThrowException() {
    String sessionId = "guest-session";

    when(eventRepository.findById(EVENT_ID)).thenReturn(event);
    when(event.getZone(ZONE_ID)).thenReturn(zone);
    when(activeOrderRepository.getBySessionId(sessionId)).thenReturn(Optional.of(activeOrder));

    when(activeOrder.hasReservationForEvent(EVENT_ID)).thenReturn(false);

    when(zone.reserve(QUANTITY))
            .thenThrow(new IllegalStateException("remaining 1 tickets available"));

    Exception exception = assertThrows(IllegalStateException.class, () ->
            reservationService.reserveTicketsForGuest(sessionId, EVENT_ID, ZONE_ID, QUANTITY)
    );

    assertEquals("remaining 1 tickets available", exception.getMessage());
}
@Test
void GivenNoActiveOrder_WhenReserveTicketsForGuest_ThenCreateNewOrderAndReturnResult() {
    String sessionId = "guest-session";

    when(eventRepository.findById(EVENT_ID)).thenReturn(event);
    when(event.getZone(ZONE_ID)).thenReturn(zone);
    when(activeOrderRepository.getBySessionId(sessionId)).thenReturn(Optional.empty());

    when(zone.getAvailableAmount()).thenReturn(10);
    when(zone.getprice()).thenReturn(100.0);

    ReservationResultDTO result =
            reservationService.reserveTicketsForGuest(sessionId, EVENT_ID, ZONE_ID, QUANTITY);

    assertEquals(EVENT_ID, result.getEventId());
}


@Test
void GivenManyGuestsReserveSameZoneConcurrently_WhenReserveTicketsForGuest_ThenDoNotOverReserve() throws InterruptedException {
    int capacity = 5;
    int numberOfThreads = 20;
    int quantityPerRequest = 1;

    InventoryZone realZone = new InventoryZone(ZONE_ID, "VIP", capacity, 100.0);

    when(eventRepository.findById(EVENT_ID)).thenReturn(event);
    when(event.getZone(ZONE_ID)).thenReturn(realZone);

    ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
    CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    for (int i = 0; i < numberOfThreads; i = i + 1) {
        final String sessionId = "guest-session-" + i;

        when(activeOrderRepository.getBySessionId(sessionId)).thenReturn(Optional.empty());

        executorService.submit(() -> {
            try {
                readyLatch.countDown();
                startLatch.await();

                reservationService.reserveTicketsForGuest(
                        sessionId,
                        EVENT_ID,
                        ZONE_ID,
                        quantityPerRequest
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
    executorService.shutdown();

    assertEquals(true, finished);
    assertEquals(capacity, successCount.get());
    assertEquals(numberOfThreads - capacity, failureCount.get());
    assertEquals(0, realZone.getAvailableAmount());
    assertEquals(capacity, realZone.getReservedAmount());
}
@Test
void GivenManyMembersReserveSameZoneConcurrently_WhenReserveTicketsForMember_ThenDoNotOverReserve()
        throws InterruptedException {

    int capacity = 5;
    int numberOfThreads = 20;
    int quantityPerRequest = 1;

    InventoryZone realZone = new InventoryZone(ZONE_ID, "VIP", capacity, 100.0);

    when(eventRepository.findById(EVENT_ID)).thenReturn(event);
    when(event.getZone(ZONE_ID)).thenReturn(realZone);

    ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
    CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    for (int i = 0; i < numberOfThreads; i = i + 1) {
        final String token = "valid-token-" + i;
        final int userId = i + 1;

        when(sessionManager.validateToken(token)).thenReturn(true);
        when(sessionManager.extractUserId(token)).thenReturn(userId);
        when(activeOrderRepository.getByUserId(userId)).thenReturn(null);

        executorService.submit(() -> {
            try {
                readyLatch.countDown();
                startLatch.await();

                reservationService.reserveTicketsForMember(
                        token,
                        EVENT_ID,
                        ZONE_ID,
                        quantityPerRequest
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
    executorService.shutdown();

    assertEquals(true, finished);
    assertEquals(capacity, successCount.get());
    assertEquals(numberOfThreads - capacity, failureCount.get());
    assertEquals(0, realZone.getAvailableAmount());
    assertEquals(capacity, realZone.getReservedAmount());
}
@Test
void GivenManyThreadsRemoveReservedTicketsForMemberConcurrently_WhenRemoveReservedTickets_ThenDoNotOverRelease()
        throws InterruptedException {

    int initialReservedTickets = 5;
    int capacity = 10;
    int numberOfThreads = 20;
    int quantityPerRemove = 1;

    InventoryZone realZone = new InventoryZone(ZONE_ID, "VIP", capacity, 100.0);
    realZone.reserve(initialReservedTickets);

    ActiveOrder realActiveOrder = new ActiveOrder(USER_ID);
    realActiveOrder.addReservation(
            EVENT_ID,
            ZONE_ID,
            initialReservedTickets,
            100.0,
            LocalDateTime.now()
    );

    when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
    when(sessionManager.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
    when(eventRepository.findById(EVENT_ID)).thenReturn(event);
    when(event.getZone(ZONE_ID)).thenReturn(realZone);
    when(activeOrderRepository.getByUserId(USER_ID)).thenReturn(realActiveOrder);

    ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
    CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    for (int i = 0; i < numberOfThreads; i = i + 1) {
        executorService.submit(() -> {
            try {
                readyLatch.countDown();
                startLatch.await();

                reservationService.removeReservedTickets(
                        VALID_TOKEN,
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
    executorService.shutdown();

    assertEquals(true, finished);
    assertEquals(initialReservedTickets, successCount.get());
    assertEquals(numberOfThreads - initialReservedTickets, failureCount.get());
    assertEquals(0, realActiveOrder.countTickets(EVENT_ID, ZONE_ID));
    assertEquals(0, realZone.getReservedAmount());
    assertEquals(capacity, realZone.getAvailableAmount());
}
}