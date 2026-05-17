package com.ticketing.system.Core.Domain.events;

import java.time.LocalDateTime;
import java.util.Map;

public class DiscountPolicy {
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
   public double calculateFinalPrice(double basePricePerTicket, int quantity){

     throw new UnsupportedOperationException("Unimplemented method 'validate'");
    }



    public double calculate(int quantity, Double priceAtoneticketReservation,LocalDateTime now) {
  
   // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'calculatePrice'");
}

    public double calculatePriceforoneticket(int quantity, Double priceAtoneticketReservation, LocalDateTime now) {
       return calculate(quantity, priceAtoneticketReservation, now) / quantity;
    }  
   
}
