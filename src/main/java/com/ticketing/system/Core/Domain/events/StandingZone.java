package com.ticketing.system.Core.Domain.events;

import com.ticketing.system.Core.Domain.events.InventorySelection;

/**
 * Counter-based zone — no addressable seats, just "N tickets available".
 *
 * <p>This is the pre-refactor {@link InventoryZone} behavior preserved
 * verbatim. All synchronization happens inside the zone via a private
 * {@code inventoryLock} (a separate object field, not {@code this}, so
 * external code can't accidentally lock and starve internal ops).
 */
public class StandingZone extends InventoryZone {

    private int capacity;
    private int reservedAmount;
    private int soldAmount;
    private final Object inventoryLock = new Object();

    public StandingZone(int id, String name, int capacity, double price) {
        super(id, name, price);
        if (capacity < 0) {
            throw new IllegalArgumentException("Capacity cannot be negative");
        }
        this.capacity = capacity;
        this.reservedAmount = 0;
        this.soldAmount = 0;
    }



    @Override
    public int getAvailableAmount() {
        synchronized (inventoryLock) {
            return capacity - reservedAmount - soldAmount;
        }
    }

    @Override
    public int getReservedAmount() {
        synchronized (inventoryLock) {
            return reservedAmount;
        }
    }

    @Override
    public int getSoldAmount() {
        synchronized (inventoryLock) {
            return soldAmount;
        }
    }



    @Override
    public boolean checkAvailability(int quantity) {
        synchronized (inventoryLock) {
            validatePositiveQuantity(quantity);

            int availableAmount = capacity - reservedAmount - soldAmount;

            if (availableAmount < quantity) {
                throw new IllegalStateException("remaining " + availableAmount + " tickets available");
            }

            return true;
        }
    }










    @Override
    public boolean reserve(InventorySelection selection) {
        if (!selection.isStandingSelection()) {
            throw new IllegalArgumentException("Standing zone cannot reserve specific seats");
        }
        int quantity = selection.getQuantity();
        synchronized (inventoryLock) {
            validatePositiveQuantity(quantity);

            int availableAmount = capacity - reservedAmount - soldAmount;

            if (availableAmount < quantity) {
                throw new IllegalStateException("remaining " + availableAmount + " tickets available");
            }

            reservedAmount = reservedAmount + quantity;
            return true;
        }
    }




    @Override
    public boolean release(InventorySelection selection) {
        if (!selection.isStandingSelection()) {
            throw new IllegalArgumentException("Standing zone cannot release specific seats");
        }
        int quantity = selection.getQuantity();
        synchronized (inventoryLock) {
            validatePositiveQuantity(quantity);

            if (quantity > reservedAmount) {
                throw new IllegalStateException("Cannot release more tickets than reserved");
            }

            reservedAmount = reservedAmount - quantity;
            return true;
        }
    }



    
    @Override
    public boolean confirmSale(InventorySelection selection) {
        if (!selection.isStandingSelection()) {
            throw new IllegalArgumentException("Standing zone cannot confirm specific seats");
        }

        int quantity = selection.getQuantity();

        synchronized (inventoryLock) {
            validatePositiveQuantity(quantity);

            if (quantity > reservedAmount) {
                throw new IllegalStateException("Cannot confirm more tickets than reserved");
            }

            reservedAmount -= quantity;
            soldAmount += quantity;
            return true;
        }
    }

















    @Override
    public int getCapacity() {
        synchronized (inventoryLock) {
            return capacity;
        }
    }



    //? Note: for riskness reasons, we'll just ignore this and we can just use the addPlaces and removePlaces methods to adjust capacity up or down as needed.
    // public void setStandingCapacity(int newCapacity) {
    //     synchronized (inventoryLock) {
    //         if (newCapacity < 0) {
    //             throw new IllegalArgumentException("Capacity cannot be negative");
    //         }

    //         if (newCapacity < reservedAmount + soldAmount) {
    //             throw new IllegalArgumentException("New capacity cannot be less than reserved + sold tickets");
    //         }

    //         this.capacity = newCapacity;
    //     }
    // }



    public void addPlaces(int amountToAdd) {
        // can add places without really checking, not harmful
        synchronized (inventoryLock) {
            validatePositiveQuantity(amountToAdd);
            this.capacity += amountToAdd;
        }
    }

    public void removePlaces(int amountToRemove) {
        // can only remove places if it doesn't cause capacity to drop below reserved + sold, 
        // otherwise we would have to cancel existing reservations or sales, which is not allowed.
        synchronized (inventoryLock) {
            validatePositiveQuantity(amountToRemove);

            int availableAmount = this.getAvailableAmount();
            if (amountToRemove > availableAmount) {
                throw new IllegalArgumentException(
                        "Cannot remove " + amountToRemove + " places; only "
                                + availableAmount + " places are available to remove");
            }

            this.capacity -= amountToRemove;
        }
    }






    @Override
    public ZoneType getZoneType() {
        return ZoneType.STANDING;
    }


    private void validatePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    @Override
    public void checkInvariants() {
        synchronized (inventoryLock) {
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("StandingZone invariant violated: name must be non-blank");
            }
            if (capacity < 0) {
                throw new IllegalStateException("StandingZone invariant violated: capacity must be >= 0 (was " + capacity + ")");
            }
            if (reservedAmount < 0) {
                throw new IllegalStateException("StandingZone invariant violated: reservedAmount must be >= 0 (was " + reservedAmount + ")");
            }
            if (reservedAmount > capacity) {
                throw new IllegalStateException("StandingZone invariant violated: reservedAmount (" + reservedAmount + ") must be <= capacity (" + capacity + ")");
            }
            if (price < 0) {
                throw new IllegalStateException("StandingZone invariant violated: price must be >= 0 (was " + price + ")");
            }
        }
    }
}
