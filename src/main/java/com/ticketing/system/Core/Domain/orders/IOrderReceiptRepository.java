package com.ticketing.system.Core.Domain.orders;

import java.util.List;
import java.util.Optional;

import com.ticketing.system.Core.Application.dto.GlobalHistoryFiltersDTO;
import com.ticketing.system.Core.Domain.shared.IRepository;

/**
 * Aggregate-root entry point for the OrderReceipt aggregate.
 */
public interface IOrderReceiptRepository extends IRepository<OrderReceipt, Integer> {

    /**
     * @return the next available order-receipt id
     */
    int nextId();

    /**
     * @param orderReceipt the receipt to persist
     */
    void save(OrderReceipt orderReceipt);

    /**
     * @param orderReceiptId the receipt id
     * @return the receipt if present
     */
    Optional<OrderReceipt> findByOrderReceiptId(int orderReceiptId);

    /**
     * UC-16 — a member's own purchase history.
     *
     * @param holderUserId the buyer's user id
     * @return the buyer's order receipts
     */
    List<OrderReceipt> findByHolderUserId(int holderUserId);

    /**
     * @param eventIds the event ids to match
     * @return receipts containing tickets for any of the given events
     */
    List<OrderReceipt> findByEventIds(List<Integer> eventIds);

    // UC-22 — company-scoped sales (filtered down to the company's events).
    // Because this method(findByCompanyId) is conceptually in the wrong place. A receipt does not know company ownership directly.
    // @Deprecated
    // List<OrderReceipt> findByCompanyId(int companyId);

    /**
     * UC-31 — global view with filters (admin only).
     *
     * @param filters the date/scope filters to apply
     * @return receipts matching the filters
     */
    List<OrderReceipt> findGlobal(GlobalHistoryFiltersDTO filters);

    /**
     * @param eventId the event id
     * @return receipts containing tickets for the event
     */
    List<OrderReceipt> findByEventId(int eventId);
}
