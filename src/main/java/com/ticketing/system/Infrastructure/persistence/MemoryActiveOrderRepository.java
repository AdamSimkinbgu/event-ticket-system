package com.ticketing.system.Infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Repository;

import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;
import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;

/**
 * In-memory {@link IActiveOrderRepository} for V1.
 *
 * <p>
 * Storage is a single {@link CopyOnWriteArrayList} of carts; lookups scan.
 * Identity collapse for {@code save()} matches by userId for Member carts and
 * by sessionId for Guest carts, so re-saving a promoted cart replaces any
 * stale Member-cart row with the same userId (D9a edge: prefer current
 * Guest-turned-Member cart over a prior orphaned Member cart).
 */
@Repository
public class MemoryActiveOrderRepository implements IActiveOrderRepository {

    private final List<ActiveOrder> carts = new CopyOnWriteArrayList<>();
    private final RepositoryLocks<String> locks = new RepositoryLocks<>();

    @Override
    public void lockForUpdate(String id) { locks.lock(id); }

    @Override
    public void unlock(String id) { locks.unlock(id); }

    @Override
    public void save(ActiveOrder activeOrder) {
        // Remove any existing cart with the same identity, then add.
        carts.removeIf(existing -> sameIdentity(existing, activeOrder));
        carts.add(activeOrder);
    }

    @Override
    public ActiveOrder getByUserId(int userId) {
        Integer target = userId;
        for (ActiveOrder cart : carts) {
            if (target.equals(cart.userIdOrNull())) {
                return cart;
            }
        }
        return null;
    }

    @Override
    public Optional<ActiveOrder> getBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        for (ActiveOrder cart : carts) {
            if (sessionId.equals(cart.getSessionId())) {
                return Optional.of(cart);
            }
        }
        return Optional.empty();
    }

    @Override
    public void delete(ActiveOrder activeOrder) {
        if (activeOrder == null)
            return;
        carts.remove(activeOrder);
    }

    @Override
    public List<ActiveOrder> findExpired() {

        List<ActiveOrder> result = new ArrayList<>();
        for (ActiveOrder cart : carts) {
            if (cart.hasExpiredItem()) {
                result.add(cart);
            }
        }
        return result;
    }

    private boolean sameIdentity(ActiveOrder a, ActiveOrder b) {
        // Member identity collapses on userId.
        if (a.userIdOrNull() != null && a.userIdOrNull().equals(b.userIdOrNull())) {
            return true;
        }
        // Pure-Guest identity collapses on sessionId.
        if (a.userIdOrNull() == null && b.userIdOrNull() == null
                && a.getSessionId() != null
                && a.getSessionId().equals(b.getSessionId())) {
            return true;
        }
        return false;
    }
}
