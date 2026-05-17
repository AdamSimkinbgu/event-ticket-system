package com.ticketing.system.Core.Domain.orders;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ticketing.system.Core.Application.dto.GlobalHistoryFiltersDTO;

// Aggregate-root entry point for the OrderReceipt aggregate.
public interface IOrderReceiptRepository {

    int nextId();
    
    void save(OrderReceipt orderReceipt);

    Optional<OrderReceipt> findByOrderReceiptId(int orderReceiptId);

    // UC-16 — member's own purchase history.
    List<OrderReceipt> findByHolderUserId(int holderUserId);

    List<OrderReceipt> findByEventIds(List<Integer> eventIds);

    // UC-22 — company-scoped sales (filtered down to the company's events).
    List<OrderReceipt> findByCompanyId(int companyId);

    // UC-31 — global view with filters (admin only).
    List<OrderReceipt> findGlobal(GlobalHistoryFiltersDTO filters);

    List<OrderReceipt> findByEventId(int eventId);
    
    Map<String, OrderReceipt> getReceiptsById() ;
}
