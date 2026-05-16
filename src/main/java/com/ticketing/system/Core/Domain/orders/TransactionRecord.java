package com.ticketing.system.Core.Domain.orders;

import java.time.LocalDateTime;

public class TransactionRecord {
    private String description;
    private double amount;
    private LocalDateTime timestamp;

    public TransactionRecord(String description, double amount, LocalDateTime timestamp) {
        this.description = description;
        this.amount = amount;
        this.timestamp = timestamp;
    }

    public String getDescription() {
        return description;
    }

    public double getAmount() {
        return amount;
    }

    public java.time.LocalDateTime getTimestamp() {
        return timestamp;
    }


    
    
}
