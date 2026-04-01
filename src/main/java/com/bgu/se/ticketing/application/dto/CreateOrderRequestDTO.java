package com.bgu.se.ticketing.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for placing a new order.
 */
public class CreateOrderRequestDTO {

    @NotBlank
    private String buyerId;

    @NotBlank
    private String eventId;

    @Min(1)
    private int quantity;

    public CreateOrderRequestDTO() {
    }

    public String getBuyerId() { return buyerId; }
    public void setBuyerId(String buyerId) { this.buyerId = buyerId; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
