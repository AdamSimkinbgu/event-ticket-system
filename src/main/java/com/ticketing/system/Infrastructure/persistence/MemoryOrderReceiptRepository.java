package com.ticketing.system.Infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.ticketing.system.Core.Application.dto.GlobalHistoryFiltersDTO;
import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Core.Domain.orders.OrderReceipt;

/**
 * In-memory {@link IOrderReceiptRepository} for V1. Lets Spring wire
 * CheckoutService / MemberAccountService / SystemAdminService.
 *
 * <p>Receipts are keyed by their {@code receiptId} converted to String —
 * the interface signatures use String keys throughout. UC-31's
 * filter/page/size global query throws until real admin reporting is built.
 */
@Repository
public class MemoryOrderReceiptRepository implements IOrderReceiptRepository {

    private final Map<String, OrderReceipt> receiptsById = new ConcurrentHashMap<>();
    private final AtomicInteger idSequence = new AtomicInteger(1);

    @Override
    public int nextId() {
        return idSequence.getAndIncrement();
    }

    @Override
    public void save(OrderReceipt orderReceipt) {
        receiptsById.put(String.valueOf(orderReceipt.getId()), orderReceipt);
        // In a real implementation, we might return false if the save failed for some reason (e.g. DB error);
    }
    
    @Override
    public Optional<OrderReceipt> findByOrderReceiptId(int orderReceiptId) {
        return Optional.ofNullable(receiptsById.get(String.valueOf(orderReceiptId)));
    }

    // UC-16 — member's own purchase history.
    @Override
    public List<OrderReceipt> findByHolderUserId(int holderUserId) {
        return receiptsById.values().stream()
                .filter(receipt -> receipt.getHolderUserId() == holderUserId)
                .collect(Collectors.toList());
    }

    @Override
    public List<OrderReceipt> findByEventIds(List<Integer> eventIds) {
        // Implementation to find receipts by event IDs in memory
        return receiptsById.values().stream()
                .filter(receipt -> eventIds.contains(receipt.geteventId()))
                .collect(Collectors.toList());
    }


    // UC-22 — company-scoped sales (filtered down to the company's events).
    @Override
    public List<OrderReceipt> findByCompanyId(int companyId) {
        // Implementation to find receipts by company ID in memory
        return receiptsById.values().stream()
                .filter(receipt -> receipt.geteventId() == companyId) // Assuming eventId corresponds to companyId for simplicity; adjust as needed
                .collect(Collectors.toList());
    }
    


    // UC-31 — global view with filters
    // A System Admin can query the full cross-company purchase history, with filters by buyer, production company, event, or date range.
    @Override
    public List<OrderReceipt> findGlobal(GlobalHistoryFiltersDTO filters) {
        // Implementation to find receipts globally with filters in memory
        return receiptsById.values().stream()
                .filter(receipt -> {
                    boolean matches = true;
                    if (filters.buyerUserId() != null) {
                        matches &= receipt.getHolderUserId() == filters.buyerUserId();
                    }
                    if (filters.companyId() != null) {
                        matches &= receipt.geteventId() == filters.companyId(); // Assuming eventId corresponds to companyId for simplicity; adjust as needed
                    }
                    if (filters.eventIds() != null) {
                        matches &= filters.eventIds().contains(receipt.geteventId());
                    }
                    if (filters.fromDate() != null) {
                        matches &= !receipt.getPurchaseTime().isBefore(filters.fromDate().atStartOfDay());
                    }
                    if (filters.toDate() != null) {
                        matches &= !receipt.getPurchaseTime().isAfter(filters.toDate().atTime(23, 59, 59));
                    }
                    return matches;
                })
                .collect(Collectors.toList());
    }
    @Override
    public List<OrderReceipt> findByEventId(int eventId) {
        return receiptsById.values().stream()
                .filter(receipt -> receipt.getReceiptLines().stream()
                        .anyMatch(line -> line.getEventId() == eventId))
                .collect(Collectors.toList());
    }

    public Map<String, OrderReceipt> getReceiptsById() {
        return receiptsById;
    }

}
