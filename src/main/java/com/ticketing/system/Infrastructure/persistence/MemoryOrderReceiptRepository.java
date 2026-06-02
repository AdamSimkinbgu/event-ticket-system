package com.ticketing.system.Infrastructure.persistence;

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

    private final Map<Integer, OrderReceipt> receiptsById = new ConcurrentHashMap<>();
    private final AtomicInteger idSequence = new AtomicInteger(1);
    private final RepositoryLocks<Integer> locks = new RepositoryLocks<>();
    

    @Override
    public void lockForUpdate(Integer id) {
        locks.lock(id);
    }

    @Override
    public void unlock(Integer id) {
        locks.unlock(id);
    }

    @Override
    public int nextId() {
        return idSequence.getAndIncrement();
    }



    @Override
    public void save(OrderReceipt orderReceipt) {
        if (orderReceipt == null) {
            throw new IllegalArgumentException("orderReceipt must not be null");
        }

        if (orderReceipt.getId() <= 0) {
            throw new IllegalArgumentException("orderReceipt id must be positive");
        }

        orderReceipt.checkInvariants();
        receiptsById.put(orderReceipt.getId(), orderReceipt);
    }

    
    
    @Override
    public Optional<OrderReceipt> findByOrderReceiptId(int orderReceiptId) {
        return Optional.ofNullable(receiptsById.get(orderReceiptId));
    }

    // UC-16 — member's own purchase history.
    @Override
    public List<OrderReceipt> findByHolderUserId(int holderUserId) {
        return receiptsById.values().stream()
                .filter(OrderReceipt::isMemberReceipt)   // Only consider member receipts since only they have holderUserId
                .filter(receipt -> receipt.getHolderUserId().equals(holderUserId))
                .collect(Collectors.toList());
    }



    @Override
    public List<OrderReceipt> findByEventIds(List<Integer> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return List.of();
        }

        return receiptsById.values().stream()
                .filter(receipt -> receipt.getReceiptLines().stream()
                        .anyMatch(line -> eventIds.contains(line.getEventId())))
                .collect(Collectors.toList());
    }




    // UC-22 — company-scoped sales (filtered down to the company's events).
    /**
     * A receipt repository cannot correctly know company ownership by itself.
     * Use EventRepository.findIdsByCompany(companyId), then call findByEventIds(...).
     */
    // @Override
    // @Deprecated
    // public List<OrderReceipt> findByCompanyId(int companyId) {
    //     //TODO: LOOK INTO THIS AND LATER DELETE THIS FUNCTION */
    //     throw new UnsupportedOperationException("Use eventRepository.findIdsByCompany(companyId) and then findByEventIds(eventIds)");
    // }
    


    // UC-31 — global view with filters
    // A System Admin can query the full cross-company purchase history, with filters by buyer, production company, event, or date range.

    //TODO: We only implement buyer and event filters here. Date range and company filters require more complex querying that would be inefficient to do in-memory without indexes.
    @Override
    public List<OrderReceipt> findGlobal(GlobalHistoryFiltersDTO filters) {
        if (filters == null) {
            return List.copyOf(receiptsById.values());
        }

        List<Integer> requestedEventIds = filters.eventIds() == null
                ? null
                : filters.eventIds().stream()
                        .map(id -> {
                             try {
                                 return Integer.parseInt(id);
                             } catch (NumberFormatException e) {
                                 return null;
                             }
                         })
                         .filter(id -> id != null)
                        .toList();

        return receiptsById.values().stream()
                .filter(receipt -> {
                    boolean matches = true;

                    if (filters.buyerUserId() != null) {
                        matches &= receipt.isMemberReceipt()
                                && receipt.getHolderUserId().equals(filters.buyerUserId());
                    }

                    if (requestedEventIds != null && !requestedEventIds.isEmpty()) {
                        matches &= receipt.getReceiptLines().stream()
                                .anyMatch(line -> requestedEventIds.contains(line.getEventId()));
                    }

                    if (filters.fromDate() != null) {
                        matches &= !receipt.getPurchaseTime()
                                .isBefore(filters.fromDate().atStartOfDay());
                    }

                    if (filters.toDate() != null) {
                        matches &= !receipt.getPurchaseTime()
                                .isAfter(filters.toDate().atTime(23, 59, 59));
                    }

                    return matches;
                })
                .collect(Collectors.toList());
    }
    


    @Override
    public List<OrderReceipt> findByEventId(int eventId) {
        return receiptsById.values().stream()
                .filter(receipt -> receipt.containsEventId(eventId))
                .collect(Collectors.toList());
    }


    public Map<Integer, OrderReceipt> getReceiptsById() {
        return receiptsById;
    }

}
    

    