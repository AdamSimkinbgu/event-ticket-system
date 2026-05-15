package com.ticketing.system.unit.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventoryZone;
import com.ticketing.system.Core.Application.dto.ReservationResultDTO; 

class ReservationServiceTest {

    private IEventRepository mockEventRepo;
    private IActiveOrderRepository mockOrderRepo;
    private ISessionManager mockSessionManager;
    private INotificationService mockNotification;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        mockEventRepo = mock(IEventRepository.class);
        mockOrderRepo = mock(IActiveOrderRepository.class);
        mockSessionManager = mock(ISessionManager.class);
        mockNotification = mock(INotificationService.class);
        
        service = new ReservationService(mockEventRepo, mockOrderRepo, mockSessionManager, mockNotification);
    }

    @Test
    void givenNoActiveOrder_whenReserveForGuest_thenOrderCreatedAndTicketLocked() {
        String sessionId = "session123";
        int eventId = 1;
        int zoneId = 1;
        int quantity = 2;

        Event mockEvent = mock(Event.class);
        InventoryZone mockZone = mock(InventoryZone.class);

        when(mockEventRepo.findById(eventId)).thenReturn(mockEvent);
        when(mockEvent.getZone(zoneId)).thenReturn(mockZone);
        when(mockZone.getAvailableAmount()).thenReturn(10);
        when(mockZone.getprice()).thenReturn(50);

        when(mockOrderRepo.getBySessionId(sessionId)).thenReturn(Optional.empty());

        ReservationResultDTO result = service.reserveTicketsForGuest(sessionId, eventId, zoneId, quantity);

        assertNotNull(result, "Reservation should succeed and return DTO");
        assertEquals(eventId, result.getEventId());
        assertEquals(zoneId, result.getZoneId());
        assertEquals(quantity, result.getQuantity());

        verify(mockZone, times(1)).reserve(quantity);

        ArgumentCaptor<ActiveOrder> orderCaptor = ArgumentCaptor.forClass(ActiveOrder.class);
        verify(mockOrderRepo, times(1)).save(orderCaptor.capture());

        ActiveOrder savedOrder = orderCaptor.getValue();
        assertNotNull(savedOrder);
        assertEquals(sessionId, savedOrder.getSessionId());
        assertEquals(2, savedOrder.getItems().size()); 
    }

    @Test
    void givenExistingActiveOrder_whenReserveForGuest_thenTicketsAppended() {
        String sessionId = "session123";
        int eventId = 1; 
        int zoneId = 1;
        int quantity = 1;

        Event mockEvent = mock(Event.class);
        InventoryZone mockZone = mock(InventoryZone.class);
        ActiveOrder existingOrder = new ActiveOrder(sessionId); 

        when(mockEventRepo.findById(eventId)).thenReturn(mockEvent);
        when(mockEvent.getZone(zoneId)).thenReturn(mockZone);
        when(mockZone.getAvailableAmount()).thenReturn(10);
        when(mockZone.getprice()).thenReturn(50);

        when(mockOrderRepo.getBySessionId(sessionId)).thenReturn(Optional.of(existingOrder));

        ReservationResultDTO result = service.reserveTicketsForGuest(sessionId, eventId, zoneId, quantity);

        assertNotNull(result, "Reservation should succeed");

        verify(mockZone, times(1)).reserve(quantity);
        verify(mockOrderRepo, times(1)).save(existingOrder);
        assertEquals(1, existingOrder.getItems().size());
    }

    @Test
    void givenTicketsUnavailable_whenReserveForGuest_thenExceptionThrown() {
        String sessionId = "session123";
        int eventId = 1;
        int zoneId = 1;
        int quantity = 5;

        Event mockEvent = mock(Event.class);
        InventoryZone mockZone = mock(InventoryZone.class);

        when(mockEventRepo.findById(eventId)).thenReturn(mockEvent);
        when(mockEvent.getZone(zoneId)).thenReturn(mockZone);
        when(mockZone.getAvailableAmount()).thenReturn(2); 
        when(mockOrderRepo.getBySessionId(sessionId)).thenReturn(Optional.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            service.reserveTicketsForGuest(sessionId, eventId, zoneId, quantity);
        });

        assertTrue(exception.getMessage().contains("Only 2 tickets left"));

        verify(mockOrderRepo, never()).save(any());
        verify(mockZone, never()).reserve(anyInt());
    }
    
    @Test
    void givenUserAlreadyHasOrderForEvent_whenReserveForGuest_thenExceptionThrown() {
        String sessionId = "session123";
        int eventId = 1;
        int zoneId = 1;
        int quantity = 2;

        Event mockEvent = mock(Event.class);
        InventoryZone mockZone = mock(InventoryZone.class);
        ActiveOrder existingOrder = mock(ActiveOrder.class);

        when(mockEventRepo.findById(eventId)).thenReturn(mockEvent);
        when(mockEvent.getZone(zoneId)).thenReturn(mockZone);
        
        when(mockOrderRepo.getBySessionId(sessionId)).thenReturn(Optional.of(existingOrder));
        
        when(existingOrder.hasReservationForEvent(eventId)).thenReturn(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            service.reserveTicketsForGuest(sessionId, eventId, zoneId, quantity);
        });

        assertEquals("User already has an active order for this event", exception.getMessage());
        
        verify(mockZone, never()).reserve(anyInt());
        verify(mockOrderRepo, never()).save(any());
    }

    @Test
    @Disabled("UC-9: subsequent reservation appends to existing ActiveOrder")
    void givenActiveOrder_whenReserve_thenLineAppended() {
    }

    @Test
    @Disabled("UC-9: SLR.1.2 — concurrent reservation of same ticket rejected")
    void givenTicketLockedByA_whenBReserves_thenBRejected() {
    }

    @Test
    @Disabled("UC-9: purchase policy violation rejects reservation (II.2.4)")
    void givenPolicyViolation_whenReserve_thenRejected() {
    }

    @Test
    @Disabled("UC-2: expiration sweep releases locked tickets back to AVAILABLE")
    void givenExpiredOrder_whenSweep_thenTicketsReleased() {
    }

    @Test
    @Disabled("UC-13: restoreActiveOrder reattaches member's pending cart on login")
    void givenMemberWithPendingOrder_whenLogin_thenOrderRestored() {
    }
}