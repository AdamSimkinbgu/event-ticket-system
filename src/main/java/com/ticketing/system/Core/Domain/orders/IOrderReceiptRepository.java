package com.ticketing.system.Core.Domain.orders;

import java.util.List;
import java.util.Optional;

import com.ticketing.system.Core.Application.dto.GlobalHistoryFiltersDTO;

// Aggregate-root entry point for the OrderReceipt aggregate.
public interface IOrderReceiptRepository {

    void save(OrderReceipt orderReceipt);

    Optional<OrderReceipt> findById(String orderReceiptId);

    // UC-16 — member's own purchase history.
    List<OrderReceipt> findByHolderUserId(int holderUserId);

    // UC-22 — company-scoped sales (filtered down to the company's events).
    List<OrderReceipt> findByEventIds(List<String> eventIds);

    // UC-31 — global view with filters (admin only).
    List<OrderReceipt> findGlobal(GlobalHistoryFiltersDTO filters, int page, int size);
}
