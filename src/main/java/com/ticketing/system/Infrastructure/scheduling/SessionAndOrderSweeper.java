package com.ticketing.system.Infrastructure.scheduling;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.CartLineItem;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Core.Domain.events.Event;
import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Core.Domain.events.InventorySelection;
import com.ticketing.system.Core.Domain.exceptions.EventNotFoundException;
import com.ticketing.system.Core.Domain.users.ISessionRepository;
import com.ticketing.system.Core.Domain.users.Session;

/**
 * UC-2 sweeper: scans for expired Sessions and ActiveOrders and releases
 * the resources they held.
 *
 * <p>Runs on a fixed delay (default 60s; override via
 * {@code sweeper.fixed-delay-ms}). Two passes per tick:
 *
 * <ol>
 *   <li><b>Expired sessions</b> — sessions past their {@code expiresAt}.
 *     For Guest sessions the attached cart is deleted and its tickets are
 *     released to inventory. For Member sessions the row is deleted but the
 *     cart (keyed by {@code userId}) is preserved per D9a — it will be
 *     re-attached to the next Member session on login.</li>
 *   <li><b>Expired carts</b> — ActiveOrders whose {@link CartLineItem} TTL
 *     (10 min from {@code addedAt}) has elapsed. The cart is unusable
 *     regardless of owner, so it's deleted and its tickets are released.
 *     This is the only place a Member's cart is destroyed without an
 *     explicit logout/end-session call.</li>
 * </ol>
 *
 * <p>The sweep is exposed as a package-private method so tests can drive it
 * directly with a controllable {@link Clock} (no need to wait for the
 * {@code @Scheduled} cadence).
 */
@Component
@Slf4j
public class SessionAndOrderSweeper {

    private final ISessionRepository sessionRepository;
    private final IActiveOrderRepository activeOrderRepository;
    private final IEventRepository eventRepository;
    private final Clock clock;

    public SessionAndOrderSweeper(
            ISessionRepository sessionRepository,
            IActiveOrderRepository activeOrderRepository,
            IEventRepository eventRepository,
            Clock clock) {
        this.sessionRepository = sessionRepository;
        this.activeOrderRepository = activeOrderRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }
    
    @Scheduled(fixedDelayString = "${sweeper.fixed-delay-ms:60000}")
    public void sweep() {
        Instant now = clock.instant();
        int sessionsCleaned = sweepExpiredSessions(now);
        int ordersCleaned = sweepExpiredOrders();
        if (sessionsCleaned > 0 || ordersCleaned > 0) {
            log.info("sweeper tick: {} session(s), {} order(s) cleaned up",
                    sessionsCleaned, ordersCleaned);
        }
    }

    /**
     * Returns the count of sessions deleted. Public so unit tests can drive
     * each pass independently of the {@code @Scheduled} cadence.
     */
    public int sweepExpiredSessions(Instant now) {
        List<Session> expired = sessionRepository.findExpiredBefore(now);
        for (Session session : expired) {
            cleanUpAttachedCart(session);
            sessionRepository.delete(session.getSessionId());
        }
        return expired.size();
    }

    /** Returns the count of orders deleted. Public for the same reason. */
    public int sweepExpiredOrders() {
        List<ActiveOrder> expired = activeOrderRepository.findExpired();
        for (ActiveOrder order : expired) {
            releaseTicketsToInventory(order);
            activeOrderRepository.delete(order);
        }
        return expired.size();
    }

    /**
     * D9a-aware cart cleanup. Guest sessions cascade-delete the cart
     * (Guest identity = the session itself). Member sessions leave the
     * cart alone; it lives on by userId until logout-restoration or its
     * own line-item expiry sweep.
     */
    private void cleanUpAttachedCart(Session session) {
        if (session.isMember())
            return;
        Optional<ActiveOrder> cartOpt = activeOrderRepository.getBySessionId(session.getSessionId());
        if (cartOpt.isPresent()) {
            releaseTicketsToInventory(cartOpt.get());
            activeOrderRepository.delete(cartOpt.get());
        }
    }

    
    
    /**
     * Groups the cart's line items by (eventId, zoneId) and releases the
     * aggregated quantity per zone via {@link Event#releaseInventory(int, InventorySelection)},
     * matching what {@code CheckoutService.returnTicketsToStock} does on
     * failed checkout.
     */
    private void releaseTicketsToInventory(ActiveOrder order) {
        // Group line items by event and zone to aggregate the release calls
        Map<Integer, Map<Integer, List<CartLineItem>>> grouped =
                order.getItems().stream()
                        .collect(Collectors.groupingBy(
                                CartLineItem::geteventId,
                                Collectors.groupingBy(CartLineItem::getzoneId)
                        ));
        // For each (event, zone) group, release the appropriate quantity back to inventory
        for (Map.Entry<Integer, Map<Integer, List<CartLineItem>>> eventEntry : grouped.entrySet()) {
            Event event = null;
            try {
                event = eventRepository.findById(eventEntry.getKey());
            } catch (EventNotFoundException e) {
                // If the event is not found, log a warning and skip releasing tickets for this event
                log.warn("Event with ID {} not found while releasing tickets for order of user id {}. Skipping.", eventEntry.getKey(), order.getUserId());
                continue;
            }
            // For each zone in the event, determine how many tickets to release
            for (Map.Entry<Integer, List<CartLineItem>> zoneEntry : eventEntry.getValue().entrySet()) {
                int zoneId = zoneEntry.getKey();
                List<CartLineItem> items = zoneEntry.getValue();
                // Release seated seats (by seat label) and standing inventory (by quantity) independently.
                List<String> seatNumbers = items.stream()
                        .map(CartLineItem::getSeatNumber)
                        .filter(s -> s != null)
                        .toList();
                
                int standingCount = (int) items.stream().filter(i -> i.getSeatNumber() == null).count();
                if (standingCount > 0) {
                    event.releaseInventory(zoneId, InventorySelection.standing(standingCount));
                }
                if (!seatNumbers.isEmpty()) {
                    event.releaseInventory(zoneId, InventorySelection.seated(seatNumbers));
                }
            }

            eventRepository.save(event);
        }
    }
}
