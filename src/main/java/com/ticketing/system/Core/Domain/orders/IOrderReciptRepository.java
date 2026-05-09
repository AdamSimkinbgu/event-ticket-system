package com.ticketing.system.Core.Domain.orders;

import java.util.List;

import com.ticketing.system.Core.Domain.ActiveOrder.ActiveOrder;

public interface IOrderReciptRepository {

    void save(OrderReceipt orderRecipts);
    
}
