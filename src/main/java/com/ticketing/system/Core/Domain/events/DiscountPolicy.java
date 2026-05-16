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
   



    public double calculateFinalPrice(int quantity,double price ,LocalDateTime now) {
   // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'calculatePrice'");
}  
public double calculatePriceforoneticket(int quantity,double price ,LocalDateTime now) {
    return calculateFinalPrice(quantity, price , now) /quantity;
}  
   
}
