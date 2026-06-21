package com.ticketing.system.Core.Domain.ActiveOrder;

import com.ticketing.system.Core.Application.dto.ActiveOrderDTO.CartLineDTO;
import com.ticketing.system.Core.Domain.shared.InvariantChecked;

import java.time.LocalDateTime;
import java.time.Duration;

public class CartLineItem implements InvariantChecked {

    private int eventId;
    private int zoneId;
    private double priceAtoneticketReservation;
    private LocalDateTime addedAt;
    private String seatNumber;  // null for standing zones
    private final Duration EXPIRATION_LIMIT = Duration.ofMinutes(10);

    public CartLineItem(int eventId, int zoneId, String seatNumber, double priceAtReservation, LocalDateTime addedAt) {
        this.eventId = eventId;
        this.zoneId = zoneId;
        this.seatNumber = seatNumber;
        this.priceAtoneticketReservation = priceAtReservation;
        this.addedAt = addedAt;
        checkInvariants();
    }

    @Override
    public void checkInvariants() {
        if (eventId <= 0) {
            throw new IllegalStateException("CartLineItem invariant violated: eventId must be positive (was " + eventId + ")");
        }
        if (zoneId < 0) {
            throw new IllegalStateException("CartLineItem invariant violated: zoneId must be non-negative (was " + zoneId + ")");
        }
        if (priceAtoneticketReservation < 0) {
            throw new IllegalStateException("CartLineItem invariant violated: price must be >= 0 (was " + priceAtoneticketReservation + ")");
        }
        if (addedAt == null) {
            throw new IllegalStateException("CartLineItem invariant violated: addedAt must not be null");
        }
        if (seatNumber != null && seatNumber.isBlank()) {
            throw new IllegalStateException("CartLineItem invariant violated: seatNumber must not be blank if provided");
        }
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

    /** Resets the reservation hold timer to a fresh window starting at {@code newAddedAt}. */
    public void renew(LocalDateTime newAddedAt) {
        // Validate-before-commit: roll back if newAddedAt is null, so a rejected call
        // never leaves addedAt corrupted. checkInvariants stays the sole validator.
        LocalDateTime previous = this.addedAt;
        this.addedAt = newAddedAt;
        try {
            checkInvariants();   // re-runs the existing invariant guard (addedAt != null, etc.)
        } catch (RuntimeException ex) {
            this.addedAt = previous;
            throw ex;
        }
    }

    public String getSeatNumber() {
        return seatNumber;
    }

    public boolean isSeatedTicket() {
        return seatNumber != null;
    }

    public boolean isStandingTicket() {
        return seatNumber == null;
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
                seatNumber,   // seatNumber is null for standing-zone tickets
                priceAtoneticketReservation,
                addedAt);
    }

}