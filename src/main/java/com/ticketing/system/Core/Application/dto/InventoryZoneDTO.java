package com.ticketing.system.Core.Application.dto;

public class InventoryZoneDTO {
    private final int id;
    private final String name;
    private  int capacity;
    private int reservedAmount;
    private int price;

    public InventoryZoneDTO(int id, String name, int capacity, int reservedAmount, int price) {
        this.id = id;
        this.name = name;
        this.capacity = capacity;
        this.reservedAmount = reservedAmount;
        this.price=price;
    }

    public int getAvailableAmount() {
        return capacity - reservedAmount;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getPrice() {
        return price;
    }

}
