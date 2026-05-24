package com.ticketing.system.acceptance;

import com.ticketing.system.Core.Application.dto.ReservationResultDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventoryZone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class ReservationAcceptanceTests {

    private ReservationService reservationService;

    private IEventRepository eventRepository;
    private IActiveOrderRepository activeOrderRepository;
    private ISessionManager sessionManager;
    private INotificationService notificationService;

    private Event event;
    private InventoryZone zone;
    private ActiveOrder activeOrder;

    @BeforeEach
    void setUp() {
        eventRepository = mock(IEventRepository.class);
        activeOrderRepository = mock(IActiveOrderRepository.class);
        sessionManager = mock(ISessionManager.class);
        notificationService = mock(INotificationService.class);

        reservationService = new ReservationService(
                eventRepository,
                activeOrderRepository,
                sessionManager,
                notificationService
        );

        event = mock(Event.class);
        zone = mock(InventoryZone.class);
        activeOrder = new ActiveOrder(1);

        when(sessionManager.validateToken("validToken")).thenReturn(true);
        when(sessionManager.extractUserId("validToken")).thenReturn(1);

        when(eventRepository.findById(100)).thenReturn(event);
        when(event.getZone(1)).thenReturn(zone);

        when(zone.getAvailableAmount()).thenReturn(10);
        when(zone.getprice()).thenReturn(50.0);

        when(activeOrderRepository.getByUserId(1)).thenReturn(activeOrder);
    }

    @Test
    void GivenValidMemberReservation_WhenReserveTicketsForMember_ThenReturnReservationResult() {
        ReservationResultDTO result = reservationService.reserveTicketsForMember(
                "validToken",
                100,
                1,
                2
        );

        assertEquals(2, result.getQuantity());
    }

    @Test
    void GivenInvalidToken_WhenReserveTicketsForMember_ThenThrowException() {
        when(sessionManager.validateToken("badToken")).thenReturn(false);

        String result;

        try {
            reservationService.reserveTicketsForMember(
                    "badToken",
                    100,
                    1,
                    2
            );

            result = "NO_EXCEPTION";

        } catch (Exception e) {
            result = e.getMessage();
        }

        assertEquals("Invalid or expired authentication token", result);
    }

    @Test
    void GivenQuantityIsZero_WhenReserveTicketsForMember_ThenThrowException() {
        String result;

        try {
            reservationService.reserveTicketsForMember(
                    "validToken",
                    100,
                    1,
                    0
            );

            result = "NO_EXCEPTION";

        } catch (Exception e) {
            result = e.getMessage();
        }

        assertEquals("Quantity must be positive", result);
    }

    @Test
    void GivenEventNotFound_WhenReserveTicketsForMember_ThenThrowException() {
        when(eventRepository.findById(100)).thenReturn(null);

        String result;

        try {
            reservationService.reserveTicketsForMember(
                    "validToken",
                    100,
                    1,
                    2
            );

            result = "NO_EXCEPTION";

        } catch (Exception e) {
            result = e.getMessage();
        }

        assertEquals("Event not found: 100", result);
    }

    @Test
    void GivenZoneNotFound_WhenReserveTicketsForMember_ThenThrowException() {
        when(event.getZone(1)).thenReturn(null);

        String result;

        try {
            reservationService.reserveTicketsForMember(
                    "validToken",
                    100,
                    1,
                    2
            );

            result = "NO_EXCEPTION";

        } catch (Exception e) {
            result = e.getMessage();
        }

        assertEquals("Zone not found: 1", result);
    }

 
   @Test
void GivenNotEnoughTickets_WhenReserveTicketsForMember_ThenThrowException() {
    when(zone.reserve(2)).thenThrow(new IllegalStateException("Only 1 tickets left"));

    String result;

    try {
        reservationService.reserveTicketsForMember(
                "validToken",
                100,
                1,
                2
        );

        result = "NO_EXCEPTION";

    } catch (Exception e) {
        result = e.getMessage();
    }

    assertEquals("Only 1 tickets left", result);
}

    @Test
    void GivenUserAlreadyHasReservationForEvent_WhenReserveTicketsForMember_ThenThrowException() {
        activeOrder.addReservation(100, 1, 1, 50.0, java.time.LocalDateTime.now());

        String result;

        try {
            reservationService.reserveTicketsForMember(
                    "validToken",
                    100,
                    1,
                    2
            );

            result = "NO_EXCEPTION";

        } catch (Exception e) {
            result = e.getMessage();
        }

        assertEquals("User already has an active order for this event", result);
    }

    @Test
    void GivenValidGuestReservation_WhenReserveTicketsForGuest_ThenReturnReservationResult() {
        when(activeOrderRepository.getBySessionId("guest-session"))
                .thenReturn(Optional.empty());

        ReservationResultDTO result = reservationService.reserveTicketsForGuest(
                "guest-session",
                100,
                1,
                3
        );

        assertEquals(3, result.getQuantity());
    }

    @Test
    void GivenMissingGuestSession_WhenReserveTicketsForGuest_ThenThrowException() {
        String result;

        try {
            reservationService.reserveTicketsForGuest(
                    "",
                    100,
                    1,
                    3
            );

            result = "NO_EXCEPTION";

        } catch (Exception e) {
            result = e.getMessage();
        }

        assertEquals("Missing session ID", result);
    }

    @Test
    void GivenValidReservedTickets_WhenRemoveReservedTickets_ThenReturnReservationResult() {
        activeOrder.addReservation(100, 1, 3, 50.0, java.time.LocalDateTime.now());

        ReservationResultDTO result = reservationService.removeReservedTickets(
                "validToken",
                100,
                1,
                2
        );

        assertEquals(2, result.getQuantity());
    }

    @Test
    void GivenRemoveQuantityIsZero_WhenRemoveReservedTickets_ThenThrowException() {
        String result;

        try {
            reservationService.removeReservedTickets(
                    "validToken",
                    100,
                    1,
                    0
            );

            result = "NO_EXCEPTION";

        } catch (Exception e) {
            result = e.getMessage();
        }

        assertEquals("Quantity must be positive", result);
    }


    
    @Test
    void GivenNoActiveOrder_WhenRemoveReservedTickets_ThenThrowException() {
        when(activeOrderRepository.getByUserId(1)).thenReturn(null);

        String result;

        try {
            reservationService.removeReservedTickets(
                    "validToken",
                    100,
                    1,
                    1
            );

            result = "NO_EXCEPTION";

        } catch (Exception e) {
            result = e.getMessage();
        }

        assertEquals("Active order not found", result);
    }

    @Test
    void GivenActiveOrderDoesNotContainEvent_WhenRemoveReservedTickets_ThenThrowException() {
        String result;

        try {
            reservationService.removeReservedTickets(
                    "validToken",
                    100,
                    1,
                    1
            );

            result = "NO_EXCEPTION";

        } catch (Exception e) {
            result = e.getMessage();
        }

        assertEquals("Active order does not contain this event", result);
    }

    @Test
    void GivenNotEnoughReservedTickets_WhenRemoveReservedTickets_ThenThrowException() {
        activeOrder.addReservation(100, 1, 1, 50.0, java.time.LocalDateTime.now());

        String result;

        try {
            reservationService.removeReservedTickets(
                    "validToken",
                    100,
                    1,
                    2
            );

            result = "NO_EXCEPTION";

        } catch (Exception e) {
            result = e.getMessage();
        }

        assertEquals("Not enough reserved tickets to remove", result);
    }


}