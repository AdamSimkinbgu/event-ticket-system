package com.ticketing.system.Core.Domain.ActiveOrder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ticketing.system.Core.Domain.orders.OrderItemLine;

public class ActiveOrder {

    private String userId;
    private String status;
    private List<OrderItemLine> items;

public ActiveOrder(String userId) {
        this.userId = userId;
        this.items = new ArrayList<>();
        this.status = "Active";

    }
public void addReservation(String ticketId, double price, String seatInfo) {
        OrderItemLine newItem = new OrderItemLine(ticketId, price, seatInfo);
        this.items.add(newItem);
        this.status = "Reserved";
    }

    
    public List<String> removeExpiredItems() {
    List<String> expiredTicketIds = new ArrayList<>();
    
    List<OrderItemLine> validItems = new ArrayList<>();

    for (int i = 0; i < items.size(); i++) {
        OrderItemLine item = items.get(i);
        
        if (item.isExpired()) {
            expiredTicketIds.add(item.getTicketId());
        } else {
            validItems.add(item);
        }
    }

    this.items.clear();
    this.items.addAll(validItems);


    return expiredTicketIds;
}
    

    public String getUserId() { return userId; }
    public List<OrderItemLine> getItems() { return Collections.unmodifiableList(items); }
    public String getStatus() { return status; }
}
