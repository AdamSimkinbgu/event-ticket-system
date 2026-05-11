package com.ticketing.system.Core.Domain.ActiveOrder;

import java.time.LocalDateTime;
import java.time.Duration; 

public class CartLineItem {

    private String eventId;
    private String zoneId;
    private double priceAtoneticketReservation;
    private LocalDateTime addedAt;
    private final Duration EXPIRATION_LIMIT = Duration.ofMinutes(10);

    public CartLineItem(String eventId,String zoneId, double priceAtReservation, LocalDateTime addedAt) {
        this.eventId = eventId;
        this.zoneId = zoneId;
        this.priceAtoneticketReservation = priceAtReservation;
        this.addedAt = addedAt;
    }

    // Getters
    public String geteventId() { return eventId; }
     public String getzoneId() { return zoneId; }

    public double getPriceAtReservation() { return priceAtoneticketReservation; }

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