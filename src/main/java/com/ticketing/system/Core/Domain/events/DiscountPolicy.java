package com.ticketing.system.Core.Domain.events;

import java.time.LocalDateTime;
import java.util.Map;

public class DiscountPolicy {
    //TODO: needs to be implemented according to the requirements, for now it just has a simple percentage discount that can be applied to the price of a ticket.
    private final double discountPercentage;

    public DiscountPolicy(double discountPercentage) {
        if (discountPercentage < 0 || discountPercentage > 100) {
            throw new IllegalArgumentException("Invalid discount percentage");
        }
        this.discountPercentage = discountPercentage;
    }

    public double apply(double price) {
        return price * (1 - discountPercentage / 100);
    }

    public double calculateFinalPrice(double basePricePerTicket, int quantity) {
        return apply(basePricePerTicket) * quantity;
    }
    
    public double calculate(int quantity, Double priceAtOneTicketReservation, LocalDateTime now) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        if (priceAtOneTicketReservation == null || priceAtOneTicketReservation < 0) {
            throw new IllegalArgumentException("Price must be non-negative");
        }

        double base = quantity * priceAtOneTicketReservation;
        return apply(base);
    }

    public double calculatePriceforoneticket(int quantity, Double priceAtOneTicketReservation, LocalDateTime now) {
        return calculate(quantity, priceAtOneTicketReservation, now) / quantity;
    }
   
}
