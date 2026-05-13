package com.ticketing.system.Core.Domain.events;

public class InventoryZone {
    private final int id;
    private final String name;
    private final int capacity;
    private int reservedAmount;
    private  int price;

    public InventoryZone(int id, String name, int capacity, int price) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.reservedAmount = 0;
        this.price=price;
    }

    public int getAvailableAmount() {
        return capacity - reservedAmount;
    }

    public boolean CheckAvailability(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (getAvailableAmount() < quantity) {
            throw new IllegalStateException("remaining "+getAvailableAmount()+" tickets available");
        }
        return true;
    }

    public boolean reserve(int quantity) {
        if (CheckAvailability(quantity)){
          reservedAmount = reservedAmount + quantity;
          return true;
        }
         throw new IllegalStateException("Not enough tickets available");
    }

    public int getId() {
        return id;
    }
public int getprice() {
        return price;
    }
public boolean release(int quantity) {
    if (quantity <= 0) {
        throw new IllegalArgumentException("Quantity must be positive");
    }

    reservedAmount = reservedAmount - quantity;
    return true;
}

}
