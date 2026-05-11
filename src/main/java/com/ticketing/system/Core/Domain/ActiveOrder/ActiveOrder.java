package com.ticketing.system.Core.Domain.ActiveOrder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;



public class ActiveOrder {

    private String userId;
    private String status;
    private List<CartLineItem> items;

public ActiveOrder(String userId) {
        this.userId = userId;
        this.items = new ArrayList<>();
       

    }

    
public void addReservation(String eventId,String zoneId ,int quantity,double price, LocalDateTime addedAt) {
    for (int i=1; i <=quantity; i=i+1){
        CartLineItem newItem = new CartLineItem(eventId, zoneId,price, addedAt);
          this.items.add(newItem);
    }
    
    }
public List<CartLineItem> getItems() {
    return items;
    }    
    
   public List<CartLineItem> ReturnToStock() {
    List<CartLineItem> returnToStock = new ArrayList<>();

            returnToStock.addAll(items);
                 clear();
             return returnToStock;
    }    
    

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public boolean hasExpiredItem() {
        for (CartLineItem item : items) {
            if (item.isExpired()) {
                return true;
            }
        }
        return false;
    }

    public String getUserId() { return userId; }
    public String getStatus() { return status; }

public boolean validateCanCheckout() {
        if (isEmpty()) {
            throw new IllegalStateException("Cannot checkout an empty order");
        }

        if (hasExpiredItem()) {
            throw new IllegalStateException("Cannot checkout because one or more tickets expired");
        }
        return true;
    }

      public List<CartLineItem> buy() {

        List<CartLineItem> ticketToBUY = new ArrayList<>();

         if (isEmpty()) {
        throw new IllegalStateException("Cannot buy an empty order");
         }

        if (hasExpiredItem()) {
        throw new IllegalStateException("Cannot sell order: one or more tickets expired");
        }
         ticketToBUY.addAll(items);
          clear();
         return ticketToBUY;
       }


 public void clear() {
     items.clear();
}

    // ---------------------------------------------------------------------------
    // Skeleton additions for ActiveOrder.
    // ---------------------------------------------------------------------------

    // UC-9 — sum of priceAtReservation across all lines (for cart-display + checkout pre-check).
    public double getTotalPrice() {
        throw new UnsupportedOperationException("UC-9: not implemented");
    }

    // UC-5 / UC-9 — minimum remaining time across lines (the cart expires when the first line does).
    public java.time.Duration getRemainingTime() {
        throw new UnsupportedOperationException("UC-5/9: not implemented");
    }

    // UC-9 — remove a single ticket from the cart; releases its lock.
    public void removeReservation(String ticketId) {
        throw new UnsupportedOperationException("UC-9: not implemented");
    }

    // UC-9 — adjust quantity-mode reservation in a standing zone.
    public void updateQuantity(String zoneId, int newQuantity) {
        throw new UnsupportedOperationException("UC-9: not implemented");
    }

    // Identifier accessors (in case ActiveOrder gains its own id beyond userId).
    public String getOrderId() {
        throw new UnsupportedOperationException("not implemented (add orderId field)");
    }

    public String getSessionId() {
        throw new UnsupportedOperationException("not implemented (add sessionId field for Guest carts)");
    }

    public java.time.LocalDateTime getCreatedAt() {
        throw new UnsupportedOperationException("not implemented (add createdAt field)");
    }

    // UC-2 — explicit expire transition (called by sweeper).
    public void expire() {
        throw new UnsupportedOperationException("UC-2: not implemented");
    }
}
