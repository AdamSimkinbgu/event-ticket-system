package com.ticketing.system.acceptance;

import com.ticketing.system.Core.Application.dto.ReservationResultDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventorySelection;
import com.ticketing.system.Core.Domain.events.InventoryZone;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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

        event = mock(Event.class, RETURNS_DEEP_STUBS);
        zone = mock(InventoryZone.class);
        activeOrder = new ActiveOrder(1);

        when(sessionManager.validateToken("validToken")).thenReturn(true);
        when(sessionManager.extractUserId("validToken")).thenReturn(1);

        when(eventRepository.findById(100)).thenReturn(event);
        when(event.getVenueMap().getZone(1)).thenReturn(zone);

        when(zone.getAvailableAmount()).thenReturn(10);
        when(zone.getprice()).thenReturn(50.0);

        when(activeOrderRepository.getByUserId(1)).thenReturn(activeOrder);
    }

    @Test
    void GivenValidMemberReservation_WhenreserveStandingTicketsForMember_ThenReturnReservationResult() {
        ReservationResultDTO result = reservationService.reserveStandingTicketsForMember(
                "validToken",
                100,
                1,
                2
        );

        assertEquals(2, result.getQuantity());
    }

    @Test
    void GivenInvalidToken_WhenreserveStandingTicketsForMember_ThenThrowException() {
        when(sessionManager.validateToken("badToken")).thenReturn(false);

        String result;

        try {
            reservationService.reserveStandingTicketsForMember(
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
    void GivenQuantityIsZero_WhenreserveStandingTicketsForMember_ThenThrowException() {
        String result;

        try {
            reservationService.reserveStandingTicketsForMember(
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
    void GivenEventNotFound_WhenreserveStandingTicketsForMember_ThenThrowException() {
        when(eventRepository.findById(100)).thenReturn(null);

        String result;

        try {
            reservationService.reserveStandingTicketsForMember(
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
    void GivenZoneNotFound_WhenreserveStandingTicketsForMember_ThenThrowException() {
        when(event.getVenueMap().getZone(1)).thenReturn(null);

        String result;

        try {
            reservationService.reserveStandingTicketsForMember(
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
void GivenNotEnoughTickets_WhenreserveStandingTicketsForMember_ThenThrowException() {
    doThrow(new IllegalStateException("Only 1 tickets left"))
            .when(event).reserveStandingSpots(1, 2);

    String result;

    try {
        reservationService.reserveStandingTicketsForMember(
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

    // @Test
    // void GivenUserAlreadyHasReservationForEvent_WhenreserveStandingTicketsForMember_ThenThrowException() {
    //     activeOrder.addStandingReservation(100, 1, 1, 50.0, java.time.LocalDateTime.now());

    //     String result;

    //     try {
    //         reservationService.reserveStandingTicketsForMember(
    //                 "validToken",
    //                 100,
    //                 1,
    //                 2
    //         );

    //         result = "NO_EXCEPTION";

    //     } catch (Exception e) {
    //         result = e.getMessage();
    //     }

    //     assertEquals("User already has an active order for this event", result);
    // }

    @Test
    void GivenValidGuestReservation_WhenReserveTicketsForGuest_ThenReturnReservationResult() {
        when(activeOrderRepository.getBySessionId("guest-session"))
                .thenReturn(Optional.empty());

        ReservationResultDTO result = reservationService.reserveStandingTicketsForGuest(
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
            reservationService.reserveStandingTicketsForGuest(
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
    void GivenValidReservedTickets_WhenremoveReservedStandingSpots_ThenReturnReservationResult() {
        activeOrder.addStandingReservation(100, 1, 3, 50.0, java.time.LocalDateTime.now());

        ReservationResultDTO result = reservationService.removeReservedStandingSpots(
                "validToken",
                100,
                1,
                2
        );

        assertEquals(2, result.getQuantity());
    }

    @Test
    void GivenRemoveQuantityIsZero_WhenremoveReservedStandingSpots_ThenThrowException() {
        String result;

        try {
            reservationService.removeReservedStandingSpots(
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
    void GivenNoActiveOrder_WhenremoveReservedStandingSpots_ThenThrowException() {
        when(activeOrderRepository.getByUserId(1)).thenReturn(null);

        String result;

        try {
            reservationService.removeReservedStandingSpots(
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
    void GivenActiveOrderDoesNotContainEvent_WhenremoveReservedStandingSpots_ThenThrowException() {
        String result;

        try {
            reservationService.removeReservedStandingSpots(
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
    void GivenNotEnoughReservedTickets_WhenremoveReservedStandingSpots_ThenThrowException() {
        activeOrder.addStandingReservation(100, 1, 1, 50.0, java.time.LocalDateTime.now());

        String result;

        try {
            reservationService.removeReservedStandingSpots(
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




    // more acceptance tests:

    @Test
    @Disabled("Enable after test-data builder supports seated venue maps")
    void GivenMemberSelectsAvailableSeats_WhenReserveSeats_ThenSeatsAreLockedAndCartShowsSeatNumbers() {
        // Arrange:
        // 1. create active member session
        // 2. create ON_SALE event with SeatedZone seats A1, A2, A3
        // 3. save event

        // Act:
        // ReservationResultDTO result =
        //     reservationService.reserveSeatsForMember(token, eventId, zoneId, List.of("A1", "A2"));

        // Assert:
        // assertEquals(List.of("A1", "A2"), result.getSeatNumbers());
        // assert venue map shows A1/A2 RESERVED and A3 AVAILABLE
        // assert active order contains two lines with seatNumber A1/A2
    }

    @Test
    @Disabled("Enable after test-data builder supports seated venue maps")
    void GivenTwoMembersSelectSameSeat_WhenReserveConcurrently_ThenOnlyOneReservationSucceeds() {
        // Arrange:
        // 1. create two member sessions
        // 2. create event with one seated seat A1

        // Act:
        // two concurrent calls reserveSeatsForMember(..., List.of("A1"))

        // Assert:
        // exactly one succeeds
        // exactly one fails
        // seat A1 is RESERVED only once
    }

    @Test
    @Disabled("Enable after test-data builder supports seated venue maps")
    void GivenGuestSelectsSeats_WhenReserveSeats_ThenGuestCartContainsSeatNumbers() {
        // Arrange guest session + event with seated zone

        // Act reserveSeatsForGuest(sessionId, eventId, zoneId, List.of("B1", "B2"))

        // Assert result quantity = 2
        // Assert result seatNumbers = B1, B2
        // Assert cart for guest session contains B1, B2
    }
}