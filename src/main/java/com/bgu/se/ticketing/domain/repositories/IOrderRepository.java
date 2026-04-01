package com.bgu.se.ticketing.domain.repositories;

import com.bgu.se.ticketing.domain.models.Order;
import com.bgu.se.ticketing.domain.models.OrderStatus;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository interface for {@link Order} aggregate roots.
 *
 * <p>Technology-agnostic. Implementations reside in the infrastructure layer.
 */
public interface IOrderRepository {

    /** Persists a new order or updates an existing one. */
    Order save(Order order);

    /** Finds an order by its unique identifier. */
    Optional<Order> findById(String id);

    /** Returns all orders placed by a given buyer. */
    List<Order> findByBuyerId(String buyerId);

    /** Returns all orders for a given event. */
    List<Order> findByEventId(String eventId);

    /** Returns all orders filtered by status. */
    List<Order> findByStatus(OrderStatus status);

    /** Deletes an order by its unique identifier. */
    void deleteById(String id);
}
