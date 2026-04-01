package com.bgu.se.ticketing.application.dto;

import com.bgu.se.ticketing.domain.models.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object for {@link com.bgu.se.ticketing.domain.models.Order}.
 */
public class OrderDTO {

    private String id;
    private String buyerId;
    private String eventId;
    private List<TicketDTO> tickets;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;

    public OrderDTO() {
    }

    public OrderDTO(String id, String buyerId, String eventId, List<TicketDTO> tickets,
                    OrderStatus status, BigDecimal totalAmount, LocalDateTime createdAt) {
        this.id = id;
        this.buyerId = buyerId;
        this.eventId = eventId;
        this.tickets = tickets;
        this.status = status;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getBuyerId() { return buyerId; }
    public void setBuyerId(String buyerId) { this.buyerId = buyerId; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public List<TicketDTO> getTickets() { return tickets; }
    public void setTickets(List<TicketDTO> tickets) { this.tickets = tickets; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
