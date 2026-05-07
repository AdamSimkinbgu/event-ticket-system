package com.ticketing.system.Core.Domain.ActiveOrder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
    for (int i=0; i<=quantity; i=i+1){
        OrderitemLine newItem = new OrderitemLine(eventId, zoneId,price, addedAt);
          this.items.add(newItem);
    }
    
    }

    
   // public List<String> removeExpiredItems() {
   // List<String> expiredTicketIds = new ArrayList<>();
    //
    //List<OrderitemLine> validItems = new ArrayList<>();

    //for (int i = 0; i < items.size(); i++) {
      ///  OrderitemLine item = items.get(i);
        
       // if (item.isExpired()) {
         //   expiredTicketIds.add(item.getTicketId());
       // } else {
         //   validItems.add(item);
        //}
   //      }

    //this.items.clear();
    //this.items.addAll(validItems);


    //return expiredTicketIds;
        //}
    

    public String getUserId() { return userId; }
    public List<OrderitemLine> getItems() { return Collections.unmodifiableList(items); }
    public String getStatus() { return status; }
}
