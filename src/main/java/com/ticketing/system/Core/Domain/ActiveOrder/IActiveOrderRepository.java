package com.ticketing.system.Core.Domain.ActiveOrder;

public interface IActiveOrderRepository {
    ActiveOrder getByUserId(String userId);
    void save(ActiveOrder activeOrder);
    void delete(String userId);
    void add(String buyerId, String eventId, String zoneId, int quantity);
}