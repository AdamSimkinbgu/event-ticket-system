package com.ticketing.system.Core.Domain.events;

import com.ticketing.system.Core.Domain.shared.InvariantChecked;

public class InventoryZone implements InvariantChecked {
    private final int id;
    private final String name;
    private  int capacity;
    private int reservedAmount;
    private  double price;
     private final Object inventoryLock = new Object();
    

    public InventoryZone(int id, String name, int capacity, double price) {
        if (capacity < 0) {
        throw new IllegalArgumentException("Capacity cannot be negative");
    }

    if (price < 0) {
        throw new IllegalArgumentException("Price cannot be negative");
    }
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.reservedAmount = 0;
        this.price=price;
    }

    public int getAvailableAmount() {
        synchronized (inventoryLock) {
            return capacity - reservedAmount;
        }
    }


    public boolean checkAvailability(int quantity) {
        synchronized (inventoryLock) {
            validatePositiveQuantity(quantity);

            int availableAmount = capacity - reservedAmount;

            if (availableAmount < quantity) {
                throw new IllegalStateException("remaining " + availableAmount + " tickets available");
            }

            return true;
        }
    }
      public boolean reserve(int quantity) {
        synchronized (inventoryLock) {
            validatePositiveQuantity(quantity);

            int availableAmount = capacity - reservedAmount;

            if (availableAmount < quantity) {
                throw new IllegalStateException("remaining " + availableAmount + " tickets available");
            }

            reservedAmount = reservedAmount + quantity;
            return true;
        }
    }

    public int getId() {
        return id;
    }
    public double getprice() {
        return price;
    }


    public String getName() {
        return name;
    }

   public int getCapacity() {
        synchronized (inventoryLock) {
            return capacity;
        }
    }

  

    public boolean release(int quantity) {
        synchronized (inventoryLock) {
            validatePositiveQuantity(quantity);

            if (quantity > reservedAmount) {
                throw new IllegalStateException("Cannot release more tickets than reserved");
            }

            reservedAmount = reservedAmount - quantity;
            return true;
        }
    }


      public void setCapacity(int newCapacity) {
        synchronized (inventoryLock) {
            if (newCapacity < 0) {
                throw new IllegalArgumentException("Capacity cannot be negative");
            }

            if (newCapacity < reservedAmount) {
                throw new IllegalArgumentException("New capacity cannot be less than the number of reserved tickets");
            }

            this.capacity = newCapacity;
        }
    }


    public int getReservedAmount() {
        synchronized (inventoryLock) {
            return reservedAmount;
        }
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
                throw new IllegalStateException("InventoryZone invariant violated: name must be non-blank");
            }
            if (capacity < 0) {
                throw new IllegalStateException("InventoryZone invariant violated: capacity must be >= 0 (was " + capacity + ")");
            }
            if (reservedAmount < 0) {
                throw new IllegalStateException("InventoryZone invariant violated: reservedAmount must be >= 0 (was " + reservedAmount + ")");
            }
            if (reservedAmount > capacity) {
                throw new IllegalStateException("InventoryZone invariant violated: reservedAmount (" + reservedAmount + ") must be <= capacity (" + capacity + ")");
            }
            if (price < 0) {
                throw new IllegalStateException("InventoryZone invariant violated: price must be >= 0 (was " + price + ")");
            }
        }
    }
}