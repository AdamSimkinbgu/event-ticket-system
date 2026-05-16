package com.ticketing.system.unit.infrastructure.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.users.ISessionRepository;
import com.ticketing.system.Core.Domain.users.Session;
import com.ticketing.system.Infrastructure.scheduling.SessionAndOrderSweeper;

class SessionAndOrderSweeperTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private ISessionRepository sessionRepo;
    private IActiveOrderRepository orderRepo;
    private IEventRepository eventRepo;
    private Clock fixedClock;
    private SessionAndOrderSweeper sweeper;

    @BeforeEach
    void setUp() {
        sessionRepo = mock(ISessionRepository.class);
        orderRepo = mock(IActiveOrderRepository.class);
        eventRepo = mock(IEventRepository.class);
        fixedClock = Clock.fixed(T0, ZoneOffset.UTC);
        sweeper = new SessionAndOrderSweeper(sessionRepo, orderRepo, eventRepo, fixedClock);

        // Defaults: nothing expired.
        when(sessionRepo.findExpiredBefore(any())).thenReturn(List.of());
        when(orderRepo.findExpired()).thenReturn(List.of());
        when(orderRepo.getBySessionId(any())).thenReturn(Optional.empty());
    }

    // ---------------------------------------------------------------------
    // Empty / no-op sweeps
    // ---------------------------------------------------------------------

    @Test
    void emptySweep_noWorkDone() {
        sweeper.sweep();

        verify(sessionRepo).findExpiredBefore(T0);
        verify(orderRepo).findExpired();
        verify(sessionRepo, never()).delete(any());
        verify(orderRepo, never()).delete(any());
    }

    // ---------------------------------------------------------------------
    // Session sweep — Guest cleanup with cart
    // ---------------------------------------------------------------------

    @Test
    void expiredGuestSessionWithCart_deletesSessionAndCart_releasesTickets() {
        Session guest = new Session("guest-sid", null, T0.minusSeconds(7200), T0.minusSeconds(60));
        when(sessionRepo.findExpiredBefore(T0)).thenReturn(List.of(guest));

        ActiveOrder cart = ActiveOrder.forGuest("guest-sid");
        cart.addReservation(1, 10, 2, 50.0, LocalDateTime.now());  // 2 tickets, event=1, zone=10
        when(orderRepo.getBySessionId("guest-sid")).thenReturn(Optional.of(cart));

        Event event = mock(Event.class);
        when(eventRepo.findById(1)).thenReturn(event);

        int cleaned = sweeper.sweepExpiredSessions(T0);

        assertEquals(1, cleaned);
        verify(orderRepo).delete(cart);
        verify(sessionRepo).delete("guest-sid");
        verify(event).releaseTickets(10, 2);     // 2 tickets in zone 10 released
        verify(eventRepo).save(event);
    }

    @Test
    void expiredGuestSessionWithoutCart_deletesSessionOnly() {
        Session guest = new Session("orphan-sid", null, T0.minusSeconds(7200), T0.minusSeconds(60));
        when(sessionRepo.findExpiredBefore(T0)).thenReturn(List.of(guest));
        when(orderRepo.getBySessionId("orphan-sid")).thenReturn(Optional.empty());

        sweeper.sweepExpiredSessions(T0);

        verify(sessionRepo).delete("orphan-sid");
        verify(orderRepo, never()).delete(any());
        verify(eventRepo, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // Session sweep — Member cleanup (D9a: cart preserved)
    // ---------------------------------------------------------------------

    @Test
    void expiredMemberSession_deletesSession_butPreservesCart_D9a() {
        Session member = new Session("member-sid", 5, T0.minusSeconds(86400 + 60),
                T0.minusSeconds(60));
        when(sessionRepo.findExpiredBefore(T0)).thenReturn(List.of(member));

        sweeper.sweepExpiredSessions(T0);

        verify(sessionRepo).delete("member-sid");
        // Member's cart MUST survive the session expiry — restored on next login.
        verify(orderRepo, never()).getBySessionId(any());
        verify(orderRepo, never()).delete(any());
        verify(eventRepo, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // Cart sweep — line-item expiry deletes cart regardless of owner
    // ---------------------------------------------------------------------

    @Test
    void cartWithExpiredItems_deletedAndTicketsReleased() {
        ActiveOrder expiredCart = ActiveOrder.forMember(5, "sid-1");
        expiredCart.addReservation(2, 20, 3, 30.0, LocalDateTime.now());
        when(orderRepo.findExpired()).thenReturn(List.of(expiredCart));

        Event event = mock(Event.class);
        when(eventRepo.findById(2)).thenReturn(event);

        int cleaned = sweeper.sweepExpiredOrders();

        assertEquals(1, cleaned);
        verify(orderRepo).delete(expiredCart);
        verify(event).releaseTickets(20, 3);
        verify(eventRepo).save(event);
    }

    @Test
    void cartWithExpiredItemsAcrossMultipleEvents_releasesPerZoneAggregated() {
        ActiveOrder cart = ActiveOrder.forGuest("sid-multi");
        cart.addReservation(1, 10, 2, 50.0, LocalDateTime.now());   // 2 in event=1 zone=10
        cart.addReservation(1, 20, 1, 75.0, LocalDateTime.now());   // 1 in event=1 zone=20
        cart.addReservation(2, 30, 1, 100.0, LocalDateTime.now());  // 1 in event=2 zone=30
        when(orderRepo.findExpired()).thenReturn(List.of(cart));

        Event event1 = mock(Event.class);
        Event event2 = mock(Event.class);
        when(eventRepo.findById(1)).thenReturn(event1);
        when(eventRepo.findById(2)).thenReturn(event2);

        sweeper.sweepExpiredOrders();

        verify(event1).releaseTickets(10, 2);
        verify(event1).releaseTickets(20, 1);
        verify(event2).releaseTickets(30, 1);
        verify(eventRepo, times(1)).save(event1);  // saved once after both zones updated
        verify(eventRepo, times(1)).save(event2);
        verify(orderRepo).delete(cart);
    }

    @Test
    void cartWithEventMissingFromRepo_skipsReleaseGracefully() {
        ActiveOrder cart = ActiveOrder.forGuest("sid-1");
        cart.addReservation(99, 10, 1, 25.0, LocalDateTime.now());
        when(orderRepo.findExpired()).thenReturn(List.of(cart));
        when(eventRepo.findById(99)).thenReturn(null);  // event vanished

        sweeper.sweepExpiredOrders();

        // Cart is still deleted even though the inventory release was a no-op.
        verify(orderRepo).delete(cart);
        verify(eventRepo, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // Combined sweep — both passes
    // ---------------------------------------------------------------------

    @Test
    void combinedSweep_handlesSessionsAndOrders_logsAggregate() {
        Session guest = new Session("sid-g", null, T0.minusSeconds(3600), T0.minusSeconds(60));
        when(sessionRepo.findExpiredBefore(T0)).thenReturn(List.of(guest));
        when(orderRepo.getBySessionId("sid-g")).thenReturn(Optional.empty());

        ActiveOrder expiredCart = ActiveOrder.forMember(5, "sid-m");
        expiredCart.addReservation(1, 10, 1, 30.0, LocalDateTime.now());
        when(orderRepo.findExpired()).thenReturn(List.of(expiredCart));

        Event event = mock(Event.class);
        when(eventRepo.findById(1)).thenReturn(event);

        sweeper.sweep();  // entry point — drives both passes

        verify(sessionRepo).delete("sid-g");
        verify(orderRepo).delete(expiredCart);
        verify(event).releaseTickets(10, 1);
    }

    @Test
    void emptyOrderItems_releaseTicketsIsNoOp() {
        // Defensive: an ActiveOrder with no line items shouldn't blow up.
        ActiveOrder emptyCart = ActiveOrder.forGuest("sid-empty");
        when(orderRepo.findExpired()).thenReturn(List.of(emptyCart));

        sweeper.sweepExpiredOrders();

        verify(orderRepo).delete(emptyCart);
        verify(eventRepo, never()).findById(anyInt());
        verify(eventRepo, never()).save(any());
    }
}
