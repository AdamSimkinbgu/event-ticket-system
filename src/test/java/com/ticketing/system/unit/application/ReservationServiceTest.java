package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Application.dto.ReservationResultDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventoryZone;

class ReservationServiceTest {

    private IEventRepository mockEventRepository;
    private IActiveOrderRepository mockActiveOrderRepository;
    private ISessionManager mockSessionManager;
    private INotificationService mockNotificationService;

    private ReservationService reservationService;

    private final String VALID_TOKEN = "valid-token";
    private final String INVALID_TOKEN = "invalid-token";

    private final int USER_ID = 1;
    private final int INVALID_USER_ID = -1;

    private final int EVENT_ID = 10;
    private final int ZONE_ID = 20;
    private final int QUANTITY = 2;

    private Event mockEvent;
    private InventoryZone mockZone;
    private ActiveOrder mockActiveOrder;

    @BeforeEach
    void setUp() {
        mockEventRepository = mock(IEventRepository.class);
        mockActiveOrderRepository = mock(IActiveOrderRepository.class);
        mockSessionManager = mock(ISessionManager.class);
        mockNotificationService = mock(INotificationService.class);

        reservationService = new ReservationService(
                mockEventRepository,
                mockActiveOrderRepository,
                mockSessionManager,
                mockNotificationService
        );

        mockEvent = mock(Event.class);
        mockZone = mock(InventoryZone.class);
        mockActiveOrder = mock(ActiveOrder.class);

        when(mockSessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
        when(mockSessionManager.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);

        when(mockEventRepository.findById(EVENT_ID)).thenReturn(mockEvent);
        when(mockEvent.getZone(ZONE_ID)).thenReturn(mockZone);

        when(mockZone.getAvailableAmount()).thenReturn(10);
        when(mockZone.getprice()).thenReturn(100.0);
    }

    @Test
    void GivenMissingToken_WhenReserveTickets_ThenThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                reservationService.reserveTickets("", EVENT_ID, ZONE_ID, QUANTITY)
        );
    }

    @Test
    void GivenNullToken_WhenReserveTickets_ThenThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                reservationService.reserveTickets(null, EVENT_ID, ZONE_ID, QUANTITY)
        );
    }

    @Test
    void GivenInvalidToken_WhenReserveTickets_ThenThrowException() {
        when(mockSessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
                reservationService.reserveTickets(INVALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
        );
    }

    @Test
    void GivenInvalidUserId_WhenReserveTickets_ThenThrowException() {
        when(mockSessionManager.extractUserId(VALID_TOKEN)).thenReturn(INVALID_USER_ID);

        assertThrows(IllegalArgumentException.class, () ->
                reservationService.reserveTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
        );
    }

    @Test
    void GivenZeroQuantity_WhenReserveTickets_ThenThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                reservationService.reserveTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, 0)
        );
    }

    @Test
    void GivenNegativeQuantity_WhenReserveTickets_ThenThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                reservationService.reserveTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, -1)
        );
    }

    @Test
    void GivenEventDoesNotExist_WhenReserveTickets_ThenThrowException() {
        when(mockEventRepository.findById(EVENT_ID)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                reservationService.reserveTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
        );
    }

    @Test
    void GivenZoneDoesNotExist_WhenReserveTickets_ThenThrowException() {
        when(mockEvent.getZone(ZONE_ID)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                reservationService.reserveTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
        );
    }

    @Test
    void GivenNotEnoughTickets_WhenReserveTickets_ThenThrowException() {
        when(mockZone.getAvailableAmount()).thenReturn(1);

        assertThrows(IllegalArgumentException.class, () ->
                reservationService.reserveTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
        );
    }

    @Test
    void GivenUserAlreadyHasActiveOrderForEvent_WhenReserveTickets_ThenThrowException() {
        when(mockActiveOrderRepository.getByUserId(USER_ID)).thenReturn(mockActiveOrder);
        when(mockActiveOrder.hasReservationForEvent(EVENT_ID)).thenReturn(true);

        assertThrows(IllegalStateException.class, () ->
                reservationService.reserveTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
        );
    }

    @Test
    void GivenValidReservationWithoutExistingActiveOrder_WhenReserveTickets_ThenReturnReservationResult() {
        when(mockActiveOrderRepository.getByUserId(USER_ID)).thenReturn(null);

        ReservationResultDTO result =
                reservationService.reserveTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY);

        assertEquals(QUANTITY, result.quantity());
    }

    @Test
    void GivenValidReservationWithExistingActiveOrder_WhenReserveTickets_ThenReturnReservationResult() {
        when(mockActiveOrderRepository.getByUserId(USER_ID)).thenReturn(mockActiveOrder);
        when(mockActiveOrder.hasReservationForEvent(EVENT_ID)).thenReturn(false);

        ReservationResultDTO result =
                reservationService.reserveTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY);

        assertEquals(EVENT_ID, result.eventId());
    }

    @Test
    void GivenMissingToken_WhenRemoveOneReservedTicket_ThenThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                reservationService.removeOneReservedTicket("", EVENT_ID, ZONE_ID)
        );
    }

    @Test
    void GivenNullToken_WhenRemoveOneReservedTicket_ThenThrowException() {
        assertThrows(IllegalArgumentException.class, () ->
                reservationService.removeOneReservedTicket(null, EVENT_ID, ZONE_ID)
        );
    }

    @Test
    void GivenInvalidToken_WhenRemoveOneReservedTicket_ThenThrowException() {
        when(mockSessionManager.validateToken(INVALID_TOKEN)).thenReturn(false);

        assertThrows(IllegalStateException.class, () ->
                reservationService.removeOneReservedTicket(INVALID_TOKEN, EVENT_ID, ZONE_ID)
        );
    }

    @Test
    void GivenInvalidUserId_WhenRemoveOneReservedTicket_ThenThrowException() {
        when(mockSessionManager.extractUserId(VALID_TOKEN)).thenReturn(INVALID_USER_ID);

        assertThrows(IllegalArgumentException.class, () ->
                reservationService.removeOneReservedTicket(VALID_TOKEN, EVENT_ID, ZONE_ID)
        );
    }

    @Test
    void GivenEventDoesNotExist_WhenRemoveOneReservedTicket_ThenThrowException() {
        when(mockEventRepository.findById(EVENT_ID)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                reservationService.removeOneReservedTicket(VALID_TOKEN, EVENT_ID, ZONE_ID)
        );
    }

    @Test
    void GivenZoneDoesNotExist_WhenRemoveOneReservedTicket_ThenThrowException() {
        when(mockEvent.getZone(ZONE_ID)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                reservationService.removeOneReservedTicket(VALID_TOKEN, EVENT_ID, ZONE_ID)
        );
    }

    @Test
    void GivenNoActiveOrder_WhenRemoveOneReservedTicket_ThenThrowException() {
        when(mockActiveOrderRepository.getByUserId(USER_ID)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () ->
                reservationService.removeOneReservedTicket(VALID_TOKEN, EVENT_ID, ZONE_ID)
        );
    }

    @Test
    void GivenActiveOrderDoesNotContainEvent_WhenRemoveOneReservedTicket_ThenThrowException() {
        when(mockActiveOrderRepository.getByUserId(USER_ID)).thenReturn(mockActiveOrder);
        when(mockActiveOrder.hasReservationForEvent(EVENT_ID)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                reservationService.removeOneReservedTicket(VALID_TOKEN, EVENT_ID, ZONE_ID)
        );
    }

    @Test
    void GivenNoReservedTicketInZone_WhenRemoveOneReservedTicket_ThenThrowException() {
        when(mockActiveOrderRepository.getByUserId(USER_ID)).thenReturn(mockActiveOrder);
        when(mockActiveOrder.hasReservationForEvent(EVENT_ID)).thenReturn(true);
        when(mockActiveOrder.hasTicket(EVENT_ID, ZONE_ID)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                reservationService.removeOneReservedTicket(VALID_TOKEN, EVENT_ID, ZONE_ID)
        );
    }

    @Test
    void GivenValidReservedTicket_WhenRemoveOneReservedTicket_ThenReturnReservationResult() {
        when(mockActiveOrderRepository.getByUserId(USER_ID)).thenReturn(mockActiveOrder);
        when(mockActiveOrder.hasReservationForEvent(EVENT_ID)).thenReturn(true);
        when(mockActiveOrder.hasTicket(EVENT_ID, ZONE_ID)).thenReturn(true);

        ReservationResultDTO result =
                reservationService.removeOneReservedTicket(VALID_TOKEN, EVENT_ID, ZONE_ID);

        assertEquals(1, result.quantity());
    }
}