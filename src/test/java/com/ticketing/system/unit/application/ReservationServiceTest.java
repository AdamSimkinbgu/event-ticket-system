package com.ticketing.system.unit.application;
import java.util.Optional;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
        when(activeOrder.hasReservationForEvent(EVENT_ID)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                reservationService.removeReservedTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
        );
    }

    @Test
    void GivenNotEnoughReservedTickets_WhenRemoveReservedTickets_ThenThrowException() {
        when(sessionManager.validateToken(VALID_TOKEN)).thenReturn(true);
        when(sessionManager.extractUserId(VALID_TOKEN)).thenReturn(USER_ID);
        when(eventRepository.findById(EVENT_ID)).thenReturn(event);
        when(event.getZone(ZONE_ID)).thenReturn(zone);
        when(activeOrderRepository.getByUserId(USER_ID)).thenReturn(activeOrder);
        when(activeOrder.hasReservationForEvent(EVENT_ID)).thenReturn(true);
        when(activeOrder.countTickets(EVENT_ID, ZONE_ID)).thenReturn(1);

        assertThrows(IllegalArgumentException.class, () ->
                reservationService.removeReservedTickets(VALID_TOKEN, EVENT_ID, ZONE_ID, QUANTITY)
        );
    }
    @Test
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
    when(zone.getAvailableAmount()).thenReturn(1);

    assertThrows(IllegalArgumentException.class, () ->
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
    when(zone.getAvailableAmount()).thenReturn(1);

    assertThrows(IllegalArgumentException.class, () ->
            reservationService.reserveTicketsForGuest(sessionId, EVENT_ID, ZONE_ID, QUANTITY)
    );
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
}