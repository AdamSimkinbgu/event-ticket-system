package com.ticketing.system.Infrastructure.persistence.OrderReceiptPersistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ticketing.system.Core.Domain.orders.OrderReceipt;

/**
 * Spring Data JPA repository for {@link OrderReceipt} — the auto-implemented SQL backing
 * {@link JpaOrderReceiptRepository}. The application layer never sees this type; it depends only on
 * the {@code IOrderReceiptRepository} domain port. Owned receipt-line and transaction value lists
 * persist as {@code @ElementCollection} side-tables by cascade with the receipt.
 *
 * <p>{@link JpaSpecificationExecutor} backs the admin global-history query, whose optional filters
 * are assembled dynamically. The event-id lookups join into the receipt-line rows.
 */
public interface SpringDataOrderReceiptRepository
        extends JpaRepository<OrderReceipt, Integer>, JpaSpecificationExecutor<OrderReceipt> {

    /** Member receipts for a buyer (guest receipts have a null userid, so they are excluded). */
    List<OrderReceipt> findByUserid(int userid);

    @Query("select distinct r from OrderReceipt r join r.receiptLines l where l.eventid = :eventId")
    List<OrderReceipt> findByEventId(@Param("eventId") int eventId);

    @Query("select distinct r from OrderReceipt r join r.receiptLines l where l.eventid in :eventIds")
    List<OrderReceipt> findByEventIds(@Param("eventIds") List<Integer> eventIds);

    /** Highest existing receiptId (0 when empty) — seeds the assigned-id sequence across restarts. */
    @Query("select coalesce(max(r.receiptId), 0) from OrderReceipt r")
    int findMaxReceiptId();
}
