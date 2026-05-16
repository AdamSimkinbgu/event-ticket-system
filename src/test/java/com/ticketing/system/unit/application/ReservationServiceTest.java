package com.ticketing.system.unit.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Application.interfaces.ISessionManager;
import com.ticketing.system.Core.Application.dto.ActiveOrderDTO;
import com.ticketing.system.Core.Application.interfaces.INotificationService;
import com.ticketing.system.Core.Application.services.ReservationService;
import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;

class ReservationServiceTest {
    ReservationService service;
    IEventRepository mockEventRepo;
    IActiveOrderRepository mockActiveOrderRepo;
    ISessionManager mockSessionManager;
    INotificationService mockNotificationService;

    @BeforeEach
    void setUp() {
        mockEventRepo = mock(IEventRepository.class);
        mockActiveOrderRepo = mock(IActiveOrderRepository.class);
        mockSessionManager = mock(ISessionManager.class);
        mockNotificationService = mock(INotificationService.class);
        service = new ReservationService(mockEventRepo, mockActiveOrderRepo, mockSessionManager,
                mockNotificationService);
    }

    @Test
    @Disabled("UC-5: first selection creates ActiveOrder + locks first ticket")
    void givenNoActiveOrder_whenReserve_thenOrderCreatedAndTicketLocked() {
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
    // @Disabled("UC-13: restoreActiveOrder reattaches member's pending cart on
    // login")
    void givenMemberWithPendingOrder_whenLogin_thenOrderRestored() {
        ActiveOrder mockOrder = mock(ActiveOrder.class); // details don't matter for this test
        Event mockEvent = mock(Event.class);

        when(mockOrder.toDTO()).thenReturn(new ActiveOrderDTO(
                123,
                null,
                java.time.LocalDateTime.now().minusMinutes(5),
                300,
                100.0,
                java.util.Arrays.asList(
            new ActiveOrderDTO.CartLineDTO(1, "Event 1", 2, null, 50.0, java.time.LocalDateTime.now().plusMinutes(5)),
            new ActiveOrderDTO.CartLineDTO(1, "Event 1", 3, null, 50.0, java.time.LocalDateTime.now().plusMinutes(5))
        )));

        when(mockOrder.getUserId()).thenReturn(123);
        when(mockActiveOrderRepo.getByUserId(123)).thenReturn(mockOrder);
        when(mockEventRepo.findById(1)).thenReturn(mockEvent);
        when(mockEvent.getName()).thenReturn("Event 1");

        ActiveOrderDTO result = service.restoreActiveOrder(123);

        verify(mockActiveOrderRepo, times(1)).getByUserId(123);
        verify(mockEventRepo, times(2)).findById(1);
        assert result != null;
        assert result.userId() == 123;
        assert result.lines().size() == 2;
        assert result.lines().getFirst().eventId() == 1;
        assert result.lines().getFirst().zoneId() == 2;
    }
}
