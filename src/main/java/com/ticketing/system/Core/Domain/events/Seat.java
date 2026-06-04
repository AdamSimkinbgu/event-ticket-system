package com.ticketing.system.Core.Domain.events;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

/**
 * A single addressable seat inside a {@link SeatedZone}.
 *
 * <p>Carries its own lifecycle status and 2D layout coordinates (so the
 * UI can render an arbitrary-shaped venue without forcing a grid). The
 * label is the stable identity — coordinates can be tweaked for layout
 * without invalidating tickets that reference this seat by label.
 */
public class Seat implements InvariantChecked {

    private final String label;
    private final double x;
    private final double y;
    private SeatStatus status;

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

    /** Package-private — only {@link SeatedZone} should drive seat transitions. */
    void setStatus(SeatStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Seat status must not be null");
        }
        this.status = status;
    }

    @Override
    public void checkInvariants() {
        if (label == null || label.isBlank()) {
            throw new IllegalStateException("Seat invariant violated: label must be non-blank");
        }
        if (status == null) {
            throw new IllegalStateException("Seat invariant violated: status must not be null");
        }
    }
}
