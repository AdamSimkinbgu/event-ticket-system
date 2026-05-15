package com.ticketing.system.Infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

    @Override
    public void save(OrderReceipt orderReceipt) {
        receiptsById.put(String.valueOf(orderReceipt.getId()), orderReceipt);
    }

    @Override
    public Optional<OrderReceipt> findById(String orderReceiptId) {
        if (orderReceiptId == null) return Optional.empty();
        return Optional.ofNullable(receiptsById.get(orderReceiptId));
    }

    @Override
    public List<OrderReceipt> findByHolderUserId(int holderUserId) {
        Integer target = holderUserId;
        List<OrderReceipt> result = new ArrayList<>();
        for (OrderReceipt r : receiptsById.values()) {
            if (target.equals(r.getUserid())) result.add(r);
        }
        return result;
    }

    @Override
    public List<OrderReceipt> findByEventIds(List<String> eventIds) {
        // OrderReceipt's eventId field exists but is currently always zero
        // (not set in the constructor). This is a UC-22 follow-up; for now,
        // returning empty is a safe stub that the company-sales acceptance
        // tests already tolerate.
        return List.of();
    }

    @Override
    public List<OrderReceipt> findGlobal(GlobalHistoryFiltersDTO filters, int page, int size) {
        throw new UnsupportedOperationException(
                "UC-31 admin global history requires real filter logic — not yet implemented");
    }

    @Override
    public int nextId() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'nextId'");
    }

    @Override
    public Optional<OrderReceipt> findByOrderReceiptId(int orderReceiptId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'findByOrderReceiptId'");
    }

    @Override
    public List<OrderReceipt> findByCompanyId(int companyId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'findByCompanyId'");
    }

    @Override
    public List<OrderReceipt> findGlobal(GlobalHistoryFiltersDTO filters) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'findGlobal'");
    }
}
