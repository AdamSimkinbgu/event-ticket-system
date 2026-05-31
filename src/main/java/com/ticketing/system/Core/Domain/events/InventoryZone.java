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
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Zone name must be non-blank");
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

    public abstract ZoneType getZoneType();

    public abstract int getCapacity();

    public abstract int getAvailableAmount();

    public abstract int getReservedAmount();

    public abstract boolean checkAvailability(int quantity);

    public abstract boolean reserve(InventorySelection selection);

    public abstract boolean release(InventorySelection selection);

    public abstract boolean confirmSale(InventorySelection selection);

    public boolean isStanding() {
        return getZoneType() == ZoneType.STANDING;
    }

    public boolean isSeated() {
        return getZoneType() == ZoneType.SEATED;
    }
    
    // setCapacity is overriden only in standing zone, will throw here if called on seated zone; the seat-based methods must be used instead
    public void setStandingCapacity(int newCapacity) {
        if (!isStanding()) {
            throw new UnsupportedOperationException("Cannot update capacity directly for a seated zone");
        }
        //Note: won't get here if it's a standing zone
    }
}
