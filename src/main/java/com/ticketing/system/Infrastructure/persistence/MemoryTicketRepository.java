package com.ticketing.system.Infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.ticketing.system.Core.Domain.Tickets.ITicketRepository;
import com.ticketing.system.Core.Domain.Tickets.Ticket;

/**
 * In-memory {@link ITicketRepository} for V1. Lets Spring wire
 * CatalogService / CheckoutService / EventManagementService /
 * MemberAccountService.
 *
 * <p>Note: the interface mixes {@code int} (findById, holderUserId) and
 * {@code String} (findByEventId, findByOrderReceiptId) keys — for the
 * String-keyed methods we parse to int where possible and throw on the
 * orderReceiptId join (Ticket has no orderReceiptId field yet — UC-22 work).
 */
@Repository
public class MemoryTicketRepository implements ITicketRepository {

    private final Map<Integer, Ticket> ticketsById = new ConcurrentHashMap<>();
    private final RepositoryLocks<Integer> locks = new RepositoryLocks<>();

    @Override
    public void lockForUpdate(Integer id) { locks.lock(id); }

    @Override
    public void unlock(Integer id) { locks.unlock(id); }

    @Override
    public boolean save(Ticket ticket) {
        ticketsById.put(ticket.getId(), ticket);
        return true;
    }

    @Override
    public Ticket findById(int ticketId) {
        return ticketsById.get(ticketId);
    }

    @Override
    public List<Ticket> findByEventId(String eventId) {
        int target;
        try {
            target = Integer.parseInt(eventId);
        } catch (NumberFormatException e) {
            return List.of();
        }
        List<Ticket> result = new ArrayList<>();
        for (Ticket t : ticketsById.values()) {
            if (t.getEventId() == target) result.add(t);
        }
        return result;
    }

    @Override
    public List<Ticket> findAvailableInZone(String eventId, String zoneId, int quantity) {
        int eventTarget;
        int zoneTarget;
        try {
            eventTarget = Integer.parseInt(eventId);
            zoneTarget = Integer.parseInt(zoneId);
        } catch (NumberFormatException e) {
            return List.of();
        }
        List<Ticket> result = new ArrayList<>();
        for (Ticket t : ticketsById.values()) {
            if (t.getEventId() == eventTarget
                    && t.getZoneId() == zoneTarget
                    && quantity > result.size()) {
                result.add(t);
            }
        }
        return result;
    }

    @Override
    public int countAvailableInZone(String eventId, String zoneId) {
        int eventTarget;
        int zoneTarget;
        try {
            eventTarget = Integer.parseInt(eventId);
            zoneTarget = Integer.parseInt(zoneId);
        } catch (NumberFormatException e) {
            return 0;
        }
        int count = 0;
        for (Ticket t : ticketsById.values()) {
            if (t.getEventId() == eventTarget && t.getZoneId() == zoneTarget) count++;
        }
        return count;
    }

    @Override
    public List<Ticket> findByOrderReceiptId(int orderReceiptId) {
        List<Ticket> result = new ArrayList<>();
        for (Ticket t : ticketsById.values()) {
            if (t.getOrderReceiptId() == orderReceiptId) result.add(t);
        }
        return result;
    }

    @Override
    public List<Ticket> findByHolderUserId(int holderUserId) {
        Integer target = holderUserId;
        List<Ticket> result = new ArrayList<>();
        for (Ticket t : ticketsById.values()) {
            if (target.equals(t.getHolderUserId())) result.add(t);
        }
        return result;
    }

    @Override
    public void saveAll(List<Ticket> tickets) {
        if (tickets == null) return;
        for (Ticket t : tickets) save(t);
    }
}
