package com.ticketing.system.Core.Domain.ActiveOrder;

import com.ticketing.system.Core.Application.dto.ActiveOrderDTO.CartLineDTO;

import java.time.LocalDateTime;
import java.time.Duration;

public class CartLineItem {

    private int eventId;
    private int zoneId;
    private double priceAtoneticketReservation;
    private LocalDateTime addedAt;
    private final Duration EXPIRATION_LIMIT = Duration.ofMinutes(10);

    public CartLineItem(int eventId, int zoneId, double priceAtReservation, LocalDateTime addedAt) {
        this.eventId = eventId;
        this.zoneId = zoneId;
        this.priceAtoneticketReservation = priceAtReservation;
        this.addedAt = addedAt;
    }

    // Getters
    public int geteventId() {
        return eventId;
    }

    public int getzoneId() {
        return zoneId;
    }

    public double getPriceAtReservation() {
        return priceAtoneticketReservation;
    }

    public LocalDateTime getAddedAt() {
        return addedAt;
    }

    public Duration getRemainingTime() {

        if (addedAt == null) {
            return Duration.ZERO;
        }

        Duration elapsedTime = Duration.between(addedAt, LocalDateTime.now());

        Duration remaining = EXPIRATION_LIMIT.minus(elapsedTime);

        if (remaining.isNegative() || remaining.isZero()) {

            return Duration.ZERO;
        } else {

            return remaining;
        }
    }

    public boolean isExpired() {
        return getRemainingTime().isZero();
    }

    public CartLineDTO toDTO() {
        return new CartLineDTO(
                eventId,
                "Event Name Placeholder", // This should be replaced with actual event name retrieval logic
                zoneId,
                null, // seatNumber is null for standing-zone tickets
                priceAtoneticketReservation,
                addedAt);
    }

}