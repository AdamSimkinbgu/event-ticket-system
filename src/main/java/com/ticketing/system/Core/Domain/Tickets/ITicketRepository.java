package com.ticketing.system.Core.Domain.Tickets;

import java.util.List;

import com.ticketing.system.Core.Domain.shared.IRepository;

/**
 * Aggregate-root entry point for the unified Ticket aggregate.
 *
 * <p>Keys are {@code int}: Ticket stores eventId/zoneId as int, so the port uses
 * int end-to-end.
 */
public interface ITicketRepository extends IRepository<Ticket, Integer> {

    /**
     * @param ticktid the ticket id
     * @return the ticket, or {@code null} if none exists with that id
     */
    Ticket findById(int ticktid);

    /**
     * @param ticket the ticket to persist
     * @return {@code true} if the ticket was saved
     */
    boolean save(Ticket ticket);

    /**
     * UC-8 / UC-22 — render venue map / list company sales.
     *
     * @param eventId the event whose tickets to fetch
     * @return all tickets belonging to the event
     */
    List<Ticket> findByEventId(int eventId);

    /**
     * UC-9 (quantity-mode reservation) — atomically pick N AVAILABLE tickets in
     * a zone.
     *
     * @param eventId  the event
     * @param zoneId   the zone within the event
     * @param quantity how many available tickets to select
     * @return up to {@code quantity} available tickets in the zone
     */
    List<Ticket> findAvailableInZone(int eventId, int zoneId, int quantity);

    /**
     * UC-8 — count for standing-zone availability display.
     *
     * @param eventId the event
     * @param zoneId  the zone within the event
     * @return the number of AVAILABLE tickets in the zone
     */
    int countAvailableInZone(int eventId, int zoneId);

    /**
     * UC-10 / UC-22 / UC-16 — tickets that belong to one OrderReceipt.
     *
     * @param orderReceiptId the order receipt id
     * @return the tickets sold under that receipt
     */
    List<Ticket> findByOrderReceiptId(int orderReceiptId);

    /**
     * UC-16 / UC-22 / UC-31 — purchase history per buyer.
     *
     * @param holderUserId the buyer's user id
     * @return all tickets held by the buyer
     */
    List<Ticket> findByHolderUserId(int holderUserId);

    /**
     * UC-20 — bulk pre-generation when an Event's VenueMap is bound.
     *
     * @param tickets the tickets to persist
     */
    void saveAll(List<Ticket> tickets);
}
