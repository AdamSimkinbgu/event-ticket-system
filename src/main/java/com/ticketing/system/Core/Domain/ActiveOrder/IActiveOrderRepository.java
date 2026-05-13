package com.ticketing.system.Core.Domain.ActiveOrder;

import java.util.List;
import java.util.Optional;

// Aggregate-root entry point for the ActiveOrder aggregate.
public interface IActiveOrderRepository {

    ActiveOrder getByUserId(int userId);

    void save(ActiveOrder activeOrder);

    void delete(String userId);

    // UC-5 / UC-13 — guest carts are session-bound (II.1.2 / II.1.3).
    Optional<ActiveOrder> getBySessionId(String sessionId);

    // UC-2 — sweep query for the expiration job.
    List<ActiveOrder> findExpired();

    // UC-13 — quick check before login-time restoration.
    boolean existsForUser(String userId);
}