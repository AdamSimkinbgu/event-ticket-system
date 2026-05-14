package com.ticketing.system.Core.Domain.ActiveOrder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;



public class ActiveOrder {
 
    private int userId;
    private String status;
    private List<CartLineItem> items;

public ActiveOrder(int userId) {
        this.userId = userId;
        this.items = new ArrayList<>();
       

    }

    
public  void addReservation(int eventId,int zoneId ,int quantity,double price, LocalDateTime addedAt) {
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

    public int getUserId() { return userId; }
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
         ticketToBUY.addAll(items);
          clear();
         return ticketToBUY;
       }


 public void clear() {
     items.clear();
}
public boolean hasReservationForEvent(int eventId) {
    for (CartLineItem item : items) {
        if (item.geteventId() == eventId && !item.isExpired()) {
            return true;
        }
    }
    return false;
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

   public void removeOneTicket(int eventId, int zoneId) {
    for (int i = 0 ; i<= items.size() - 1; i=i+1) {
        CartLineItem item = items.get(i);

        if (item.geteventId() == eventId && item.getzoneId() == zoneId) {
            items.remove(i);
            return;
        }
    }

    throw new IllegalArgumentException("No reservation found for this event and zone");
}
  
    public java.time.LocalDateTime getCreatedAt() {
        throw new UnsupportedOperationException("not implemented (add createdAt field)");
    }

    // UC-2 — explicit expire transition (called by sweeper).
    public void expire() {
        throw new UnsupportedOperationException("UC-2: not implemented");
    }
}
