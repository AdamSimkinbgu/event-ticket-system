package com.bgu.se.ticketing.domain.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain aggregate root representing a purchase order for event tickets.
 *
 * <p>An Order is the consistency boundary: it owns its list of {@link Ticket}s and
 * enforces all invariants related to ordering. This class is framework-agnostic.
 */
public class Order {

    private final String id;
    /** References the User aggregate (the buyer). */
    private final String buyerId;
    /** References the Event aggregate. */
    private final String eventId;
    private final List<Ticket> tickets;
    private OrderStatus status;
    private final LocalDateTime createdAt;

    public Order(String id, String buyerId, String eventId, OrderStatus status, LocalDateTime createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.buyerId = Objects.requireNonNull(buyerId, "buyerId must not be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.tickets = new ArrayList<>();
    }

    /** Factory method – creates a new PENDING order with a random ID. */
    public static Order create(String buyerId, String eventId) {
        return new Order(UUID.randomUUID().toString(), buyerId, eventId,
                OrderStatus.PENDING, LocalDateTime.now());
    }

    /**
     * Adds a ticket to this order.
     *
     * @throws IllegalStateException if the order is not in PENDING state
     */
    public void addTicket(Ticket ticket) {
        Objects.requireNonNull(ticket, "ticket must not be null");
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Tickets can only be added to a PENDING order");
        }
        tickets.add(ticket);
    }

    /**
     * Confirms the order and all of its tickets.
     *
     * @throws IllegalStateException if the order is not PENDING or has no tickets
     */
    public void confirm() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be confirmed");
        }
        if (tickets.isEmpty()) {
            throw new IllegalStateException("Cannot confirm an order with no tickets");
        }
        tickets.forEach(Ticket::confirm);
        this.status = OrderStatus.CONFIRMED;
    }

    /**
     * Cancels the order and all of its tickets.
     *
     * @throws IllegalStateException if the order is already cancelled
     */
    public void cancel() {
        if (status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order is already cancelled");
        }
        tickets.forEach(Ticket::cancel);
        this.status = OrderStatus.CANCELLED;
    }

    /** Calculates the total price of all tickets in this order. */
    public BigDecimal calculateTotal() {
        return tickets.stream()
                .map(Ticket::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String getId() { return id; }
    public String getBuyerId() { return buyerId; }
    public String getEventId() { return eventId; }
    public List<Ticket> getTickets() { return Collections.unmodifiableList(tickets); }
    public OrderStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Order{id='" + id + "', buyerId='" + buyerId + "', status=" + status + "}";
    }
}
