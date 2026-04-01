package com.bgu.se.ticketing.application.dto;

import com.bgu.se.ticketing.domain.models.TicketStatus;

import java.math.BigDecimal;

/**
 * Data Transfer Object for {@link com.bgu.se.ticketing.domain.models.Ticket}.
 */
public class TicketDTO {

    private String id;
    private String eventId;
    private String ownerId;
    private BigDecimal price;
    private TicketStatus status;

    public TicketDTO() {
    }

    public TicketDTO(String id, String eventId, String ownerId, BigDecimal price, TicketStatus status) {
        this.id = id;
        this.eventId = eventId;
        this.ownerId = ownerId;
        this.price = price;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }
}
