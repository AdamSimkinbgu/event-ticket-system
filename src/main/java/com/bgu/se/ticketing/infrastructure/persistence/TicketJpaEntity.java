package com.bgu.se.ticketing.infrastructure.persistence;

import com.bgu.se.ticketing.domain.models.TicketStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * JPA entity mapped to the {@code tickets} table.
 */
@Entity
@Table(name = "tickets")
public class TicketJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TicketStatus status;

    @Column(name = "order_id")
    private String orderId;

    protected TicketJpaEntity() {
    }

    public TicketJpaEntity(String id, String eventId, String ownerId,
                           BigDecimal price, TicketStatus status, String orderId) {
        this.id = id;
        this.eventId = eventId;
        this.ownerId = ownerId;
        this.price = price;
        this.status = status;
        this.orderId = orderId;
    }

    public String getId() { return id; }
    public String getEventId() { return eventId; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public TicketStatus getStatus() { return status; }
    public void setStatus(TicketStatus status) { this.status = status; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
}
