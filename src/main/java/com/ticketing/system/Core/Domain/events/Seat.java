package com.ticketing.system.Core.Domain.events;

import java.time.LocalDateTime;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

/**
 * A single addressable seat inside a {@link SeatedZone}.
 *
 * <p>Carries its own lifecycle status and 2D layout coordinates (so the
 * UI can render an arbitrary-shaped venue without forcing a grid). The
 * label is the stable identity — coordinates can be tweaked for layout
 * without invalidating tickets that reference this seat by label.
 *
 * <p>When status is {@code RESERVED}, {@link #reservedByOrderKey} records
 * which {@code ActiveOrder} holds the reservation, enabling the 3-phase
 * checkout to verify ownership before confirming sale — even after the
 * event lock is released during the payment/issuance phase.
 */
public class Seat implements InvariantChecked {

    private final String label;
    private final double x;
    private final double y;
    private SeatStatus status;

    /** Non-null only while status == RESERVED; identifies the holding ActiveOrder. */
    private String reservedByOrderKey;
    /** When the hold expires (informational — enforcement is in the sweep job). */
    private LocalDateTime reservedUntil;

    public Seat(String label, double x, double y) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("Seat label must be non-blank");
        }
        this.label = label;
        this.x = x;
        this.y = y;
        this.status = SeatStatus.AVAILABLE;
    }

    public String getLabel() {
        return label;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public SeatStatus getStatus() {
        return status;
    }

    /** Returns the order key that currently holds this seat, or {@code null} if not reserved. */
    public String getReservedByOrderKey() {
        return reservedByOrderKey;
    }

    public LocalDateTime getReservedUntil() {
        return reservedUntil;
    }

    /** Package-private — only {@link SeatedZone} should drive seat transitions. */
    void setStatus(SeatStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Seat status must not be null");
        }
        this.status = status;
    }

    /**
     * Package-private — called by {@link SeatedZone} when reserving a seat.
     * Records which order holds the seat and when the hold expires.
     */
    void markReservedBy(String orderKey, LocalDateTime reservedUntil) {
        if (orderKey == null || orderKey.isBlank()) {
            throw new IllegalArgumentException("orderKey must be non-blank");
        }
        this.reservedByOrderKey = orderKey;
        this.reservedUntil = reservedUntil;
        this.status = SeatStatus.RESERVED;
    }

    /**
     * Package-private — called by {@link SeatedZone} on release or sale confirmation.
     * Clears reservation ownership.
     */
    void clearReservation() {
        this.reservedByOrderKey = null;
        this.reservedUntil = null;
    }

    @Override
    public void checkInvariants() {
        if (label == null || label.isBlank()) {
            throw new IllegalStateException("Seat invariant violated: label must be non-blank");
        }
        if (status == null) {
            throw new IllegalStateException("Seat invariant violated: status must not be null");
        }
        if (status == SeatStatus.RESERVED && (reservedByOrderKey == null || reservedByOrderKey.isBlank())) {
            throw new IllegalStateException(
                    "Seat invariant violated: RESERVED seat must have a non-blank reservedByOrderKey");
        }
        if (status != SeatStatus.RESERVED && reservedByOrderKey != null) {
            throw new IllegalStateException(
                    "Seat invariant violated: non-RESERVED seat must not have a reservedByOrderKey");
        }
    }
}
