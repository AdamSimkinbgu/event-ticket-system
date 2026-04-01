package com.bgu.se.ticketing.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Data Transfer Object for {@link com.bgu.se.ticketing.domain.models.Event}.
 */
public class EventDTO {

    private String id;
    private String name;
    private String description;
    private String location;
    private LocalDateTime eventDateTime;
    private int totalCapacity;
    private int availableTickets;
    private BigDecimal ticketPrice;
    private String organizerId;

    public EventDTO() {
    }

    public EventDTO(String id, String name, String description, String location,
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
    public void setId(String id) { this.id = id; }
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
