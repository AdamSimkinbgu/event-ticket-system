package com.bgu.se.ticketing.domain.models;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a single ticket for an event.
 *
 * <p>A Ticket is owned by the Order aggregate but can be referenced independently.
 * This class is framework-agnostic.
 */
public class Ticket {

    private final String id;
    /** References the Event aggregate by ID. */
    private final String eventId;
    /** References the User aggregate (owner/buyer). */
    private String ownerId;
    private BigDecimal price;
    private TicketStatus status;

    public Ticket(String id, String eventId, String ownerId, BigDecimal price, TicketStatus status) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.price = Objects.requireNonNull(price, "price must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    /** Factory method that generates a new random ID. */
    public static Ticket create(String eventId, String ownerId, BigDecimal price) {
        return new Ticket(UUID.randomUUID().toString(), eventId, ownerId, price, TicketStatus.RESERVED);
    }

    /** Marks this ticket as confirmed (payment succeeded). */
    public void confirm() {
        if (status != TicketStatus.RESERVED) {
            throw new IllegalStateException("Only RESERVED tickets can be confirmed");
        }
        this.status = TicketStatus.CONFIRMED;
    }

    /** Cancels this ticket. */
    public void cancel() {
        if (status == TicketStatus.CANCELLED) {
            throw new IllegalStateException("Ticket is already cancelled");
        }
        this.status = TicketStatus.CANCELLED;
    }

    /** Transfers ownership to a new buyer. */
    public void transferTo(String newOwnerId) {
        Objects.requireNonNull(newOwnerId, "newOwnerId must not be null");
        if (status != TicketStatus.CONFIRMED) {
            throw new IllegalStateException("Only CONFIRMED tickets can be transferred");
        }
        this.ownerId = newOwnerId;
    }

    public String getId() { return id; }
    public String getEventId() { return eventId; }
    public String getOwnerId() { return ownerId; }
    public BigDecimal getPrice() { return price; }
    public TicketStatus getStatus() { return status; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ticket other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Ticket{id='" + id + "', eventId='" + eventId + "', status=" + status + "}";
    }
}
