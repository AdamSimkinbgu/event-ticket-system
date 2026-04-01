package com.bgu.se.ticketing.infrastructure.persistence;

import com.bgu.se.ticketing.domain.models.OrderStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity mapped to the {@code orders} table.
 */
@Entity
@Table(name = "orders")
public class OrderJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private String id;

    @Column(name = "buyer_id", nullable = false)
    private String buyerId;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Tickets are stored separately; we store their IDs here for reconstruction
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_ticket_ids", joinColumns = @JoinColumn(name = "order_id"))
    @Column(name = "ticket_id")
    private List<String> ticketIds = new ArrayList<>();

    protected OrderJpaEntity() {
    }

    public OrderJpaEntity(String id, String buyerId, String eventId,
                          OrderStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.buyerId = buyerId;
        this.eventId = eventId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getBuyerId() { return buyerId; }
    public String getEventId() { return eventId; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<String> getTicketIds() { return ticketIds; }
    public void setTicketIds(List<String> ticketIds) { this.ticketIds = ticketIds; }
}
