package com.ticketing.system.Core.Domain.ActiveOrder;

import java.time.LocalDateTime;
import java.time.Duration; 

public class OrderitemLine {

    private String ticketId;
    private double priceAtReservation;
    private String seatInfo;
    private LocalDateTime addedAt; 
    private final Duration EXPIRATION_LIMIT; 

    public OrderitemLine(String ticketId, double priceAtReservation, String seatInfo, LocalDateTime addedAt) {
        this.ticketId = ticketId;
        this.priceAtReservation = priceAtReservation;
        this.seatInfo = seatInfo;
        this.addedAt = addedAt;
        EXPIRATION_LIMIT = Duration.ofMinutes(10);
    }

    // Getters
    public String getTicketId() { return ticketId; }
    public double getPriceAtReservation() { return priceAtReservation; }
    public String getSeatInfo() { return seatInfo; }
    public LocalDateTime getAddedAt() { return addedAt; }


    public Duration getRemainingTime() {

    if (addedAt == null) {
        return Duration.ZERO;
    }

  
    Duration elapsedTime = Duration.between(addedAt, LocalDateTime.now());

    Duration remaining = EXPIRATION_LIMIT.minus(elapsedTime);

    if (remaining.isNegative()|| remaining.isZero()) {
        
        return Duration.ZERO;
    } else {
     
        return remaining;
    }
}
    public boolean isExpired() {
        return getRemainingTime().isZero();
    }

}