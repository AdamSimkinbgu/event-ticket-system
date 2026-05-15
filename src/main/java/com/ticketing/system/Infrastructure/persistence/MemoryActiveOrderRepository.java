package com.ticketing.system.Infrastructure.persistence;

import java.util.List;

import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;

public class MemoryActiveOrderRepository implements IActiveOrderRepository {

    List<ActiveOrder> activeOrders;

    public MemoryActiveOrderRepository() {
        this.activeOrders = new java.util.ArrayList<>();
    }

    @Override
    public ActiveOrder getByUserId(int userId) {
        return activeOrders.stream()
                .filter(order -> order.getUserId() == userId)
                .findFirst()
                .orElse(null);
    }

    @Override
    public void save(ActiveOrder activeOrder) {
        delete(String.valueOf(activeOrder.getUserId())); // Ensure no duplicates for the same user.
        activeOrders.add(activeOrder);
    }

    @Override
    public void delete(String userId) {
        activeOrders.removeIf(order -> String.valueOf(order.getUserId()).equals(userId));
    }

    @Override
    public ActiveOrder getBySessionId(String sessionId) {
        throw new UnsupportedOperationException(
                "Session-based retrieval not implemented in MemoryActiveOrderRepository");
    }

    @Override
    public List<ActiveOrder> findExpired() {
        return activeOrders.stream()
                .filter(ActiveOrder::isExpired)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public boolean existsForUser(String userId) {
        return activeOrders.stream()
                .anyMatch(order -> String.valueOf(order.getUserId()).equals(userId));
    }

}
