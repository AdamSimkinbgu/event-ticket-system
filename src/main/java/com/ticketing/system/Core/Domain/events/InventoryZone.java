package com.ticketing.system.Core.Domain.events;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

/**
 * Abstract base for all inventory-zone types inside a {@link VenueMap}.
 *
 * <p>Two concrete shapes:
 * <ul>
 *   <li>{@link StandingZone} — counter-based. Capacity is a single number;
 *       reservations and releases are integer arithmetic.</li>
 *   <li>{@link SeatedZone} — catalog of named, addressable seats with
 *       coordinates. Reservations target specific seat labels.</li>
 * </ul>
 *
 * <p>Common state (id, name, price) lives on this class. Pre-purchase
 * inventory state (capacity/reservations) lives on the subclass and is
 * mediated through {@link #getCapacity()} / {@link #getAvailableAmount()} /
 * {@link #getReservedAmount()}.
 *
 * <p>The legacy counter-based methods ({@code reserve(int)} / {@code release(int)} /
 * {@code checkAvailability(int)} / {@code setCapacity(int)}) are defined here
 * with default throwing implementations so existing callers that hold an
 * {@code InventoryZone} reference continue to work for standing zones.
 * Seated-zone callers must downcast or use the typed
 * {@link SeatedZone#reserveSeats(java.util.List)} / {@link SeatedZone#releaseSeats(java.util.List)} methods.
 */
public abstract class InventoryZone implements InvariantChecked {

    protected final int id;
    protected final String name;
    protected double price;

    protected InventoryZone(int id, String name, double price) {
        if (price < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getprice() {
        return price;
    }

    /** Total inventory units in this zone (seats for SeatedZone, capacity for StandingZone). */
    public abstract int getCapacity();

    /** Units currently available for new reservations. */
    public abstract int getAvailableAmount();

    /** Units currently held by un-purchased reservations. */
    public abstract int getReservedAmount();

    // ---------------------------------------------------------------------
    // Legacy counter-based API. Concrete on StandingZone; throws on SeatedZone.
    // ---------------------------------------------------------------------

    public boolean reserve(int quantity) {
        throw new UnsupportedOperationException(
                "reserve(int) is a StandingZone operation — for SeatedZone, downcast and call reserveSeats(List<String>) instead");
    }

    public boolean release(int quantity) {
        throw new UnsupportedOperationException(
                "release(int) is a StandingZone operation — for SeatedZone, downcast and call releaseSeats(List<String>) instead");
    }

    public boolean checkAvailability(int quantity) {
        throw new UnsupportedOperationException(
                "checkAvailability(int) is a StandingZone operation");
    }

    public void setCapacity(int newCapacity) {
        throw new UnsupportedOperationException(
                "setCapacity(int) is a StandingZone operation — SeatedZone capacity is derived from its seat list");
    }
}
