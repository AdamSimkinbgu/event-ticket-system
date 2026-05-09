package com.ticketing.system.Core.Domain.ActiveOrder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;



public class ActiveOrder {

    private String userId;
    private String status;
    private List<OrderitemLine> items;

public ActiveOrder(String userId) {
        this.userId = userId;
        this.items = new ArrayList<>();
       

    }

    
public void addReservation(String eventId,String zoneId ,int quantity,double price, LocalDateTime addedAt) {
    for (int i=1; i <=quantity; i=i+1){
        OrderitemLine newItem = new OrderitemLine(eventId, zoneId,price, addedAt);
          this.items.add(newItem);
    }
    
    }
public List<OrderitemLine> getItems() {
    return items;
    }    
    
   public List<OrderitemLine> ReturnToStock() {
    List<OrderitemLine> returnToStock = new ArrayList<>();

            returnToStock.addAll(items);
                 clear();
             return returnToStock;
    }    
    

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public boolean hasExpiredItem() {
        for (OrderitemLine item : items) {
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

      public List<OrderitemLine> buy() {

        List<OrderitemLine> ticketToBUY = new ArrayList<>();

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
  

}
