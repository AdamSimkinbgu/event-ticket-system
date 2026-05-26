package com.ticketing.system.Core.Domain.Tickets;

import java.util.List;

import com.ticketing.system.Core.Domain.shared.IRepository;

// Aggregate-root entry point for the unified Ticket aggregate.
public interface ITicketRepository extends IRepository<Ticket, Integer> {

    Ticket findById(int ticktid);

    boolean save(Ticket ticket);

    // UC-8 / UC-22 — render venue map / list company sales.
    List<Ticket> findByEventId(String eventId);

    // UC-9 (quantity-mode reservation) — atomically pick N AVAILABLE tickets in a zone.
    List<Ticket> findAvailableInZone(String eventId, String zoneId, int quantity);

    // UC-8 — count for standing-zone availability display.
    int countAvailableInZone(String eventId, String zoneId);

    // UC-10 / UC-22 / UC-16 — tickets that belong to one OrderReceipt.
    List<Ticket> findByOrderReceiptId(int orderReceiptId);

    // UC-16 / UC-22 / UC-31 — purchase history per buyer.
    List<Ticket> findByHolderUserId(int holderUserId);

    // UC-20 — bulk pre-generation when an Event's VenueMap is bound.
    void saveAll(List<Ticket> tickets);
}
  