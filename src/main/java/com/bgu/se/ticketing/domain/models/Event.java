package com.bgu.se.ticketing.domain.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain aggregate root representing an event hosted on the platform.
 *
 * <p>This class is framework-agnostic and must not depend on JPA, Spring, or any
 * infrastructure concern.
 */
public class Event {

    private final String id;
    private String name;
    private String description;
    private String location;
    private LocalDateTime eventDateTime;
    private int totalCapacity;
    private int availableTickets;
    private BigDecimal ticketPrice;
    /** ID of the organizer (references User aggregate). */
    private String organizerId;

    public Event(String id,
                 String name,
                 String description,
                 String location,
                 LocalDateTime eventDateTime,
                 int totalCapacity,
                 BigDecimal ticketPrice,
                 String organizerId) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.description = description;
        this.location = Objects.requireNonNull(location, "location must not be null");
        this.eventDateTime = Objects.requireNonNull(eventDateTime, "eventDateTime must not be null");
        if (totalCapacity <= 0) {
            throw new IllegalArgumentException("totalCapacity must be positive");
        }
        this.totalCapacity = totalCapacity;
        this.availableTickets = totalCapacity;
        this.ticketPrice = Objects.requireNonNull(ticketPrice, "ticketPrice must not be null");
        this.organizerId = Objects.requireNonNull(organizerId, "organizerId must not be null");
    }

    /** Factory method that generates a new random ID. */
    public static Event create(String name,
                               String description,
                               String location,
                               LocalDateTime eventDateTime,
                               int totalCapacity,
                               BigDecimal ticketPrice,
                               String organizerId) {
        return new Event(UUID.randomUUID().toString(), name, description, location,
                eventDateTime, totalCapacity, ticketPrice, organizerId);
    }

    /**
     * Reserves the requested number of tickets if available.
     *
     * @param quantity number of tickets to reserve
     * @throws IllegalArgumentException if quantity is non-positive
     * @throws IllegalStateException    if not enough tickets are available
     */
    public void reserveTickets(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (availableTickets < quantity) {
            throw new IllegalStateException("Not enough tickets available for event: " + id);
        }
        availableTickets -= quantity;
    }

    /** Releases previously reserved tickets back into inventory. */
    public void releaseTickets(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        availableTickets = Math.min(totalCapacity, availableTickets + quantity);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = Objects.requireNonNull(name); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = Objects.requireNonNull(location); }
    public LocalDateTime getEventDateTime() { return eventDateTime; }
    public void setEventDateTime(LocalDateTime eventDateTime) { this.eventDateTime = Objects.requireNonNull(eventDateTime); }
    public int getTotalCapacity() { return totalCapacity; }
    public int getAvailableTickets() { return availableTickets; }
    public BigDecimal getTicketPrice() { return ticketPrice; }
    public void setTicketPrice(BigDecimal ticketPrice) { this.ticketPrice = Objects.requireNonNull(ticketPrice); }
    public String getOrganizerId() { return organizerId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Event other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Event{id='" + id + "', name='" + name + "', available=" + availableTickets + "}";
    }
}
