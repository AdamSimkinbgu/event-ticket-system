package com.ticketing.system.Core.Domain.events;

public class InventoryZone {
    private final int id;
    private final String name;
    private  int capacity;
    private int reservedAmount;
    private double price;

    public InventoryZone(int id, String name, int capacity, double price) {
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
    public double getprice() {
        return price;
    }


    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }


    public boolean release(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        reservedAmount = reservedAmount - quantity;
        return true;
    }

    public void setCapacity(int newCapacity) {
        if (newCapacity < reservedAmount) {
            throw new IllegalArgumentException("New capacity cannot be less than the number of reserved tickets");
    }
        this.capacity = newCapacity;
    }

    public int getReservedAmount() {
        return reservedAmount;
    }

}
