package com.ticketing.system.Core.Domain.ActiveOrder;

public interface IActiveOrderRepository {
    ActiveOrder getByUserId(String userId);
    void save(ActiveOrder activeOrder);
    void delete(String userId);
}