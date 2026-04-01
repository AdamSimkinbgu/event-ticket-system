package com.bgu.se.ticketing.infrastructure.persistence;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JPA entity mapped to the {@code events} table.
 */
@Entity
@Table(name = "events")
public class EventJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "location", nullable = false)
    private String location;

    @Column(name = "event_date_time", nullable = false)
    private LocalDateTime eventDateTime;

    @Column(name = "total_capacity", nullable = false)
    private int totalCapacity;

    @Column(name = "available_tickets", nullable = false)
    private int availableTickets;

    @Column(name = "ticket_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal ticketPrice;

    @Column(name = "organizer_id", nullable = false)
    private String organizerId;

    protected EventJpaEntity() {
    }

    public EventJpaEntity(String id, String name, String description, String location,
                          LocalDateTime eventDateTime, int totalCapacity, int availableTickets,
                          BigDecimal ticketPrice, String organizerId) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.location = location;
        this.eventDateTime = eventDateTime;
        this.totalCapacity = totalCapacity;
        this.availableTickets = availableTickets;
        this.ticketPrice = ticketPrice;
        this.organizerId = organizerId;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public LocalDateTime getEventDateTime() { return eventDateTime; }
    public void setEventDateTime(LocalDateTime eventDateTime) { this.eventDateTime = eventDateTime; }
    public int getTotalCapacity() { return totalCapacity; }
    public void setTotalCapacity(int totalCapacity) { this.totalCapacity = totalCapacity; }
    public int getAvailableTickets() { return availableTickets; }
    public void setAvailableTickets(int availableTickets) { this.availableTickets = availableTickets; }
    public BigDecimal getTicketPrice() { return ticketPrice; }
    public void setTicketPrice(BigDecimal ticketPrice) { this.ticketPrice = ticketPrice; }
    public String getOrganizerId() { return organizerId; }
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }
}
