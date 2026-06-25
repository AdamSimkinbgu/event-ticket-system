package com.ticketing.system.unit.infrastructure.persistence.OrderReceiptPersistence;

import com.ticketing.system.Core.Domain.orders.IOrderReceiptRepository;
import com.ticketing.system.Infrastructure.persistence.OrderReceiptPersistence.MemoryOrderReceiptRepository;

class MemoryOrderReceiptRepositoryContractTest extends IOrderReceiptRepositoryContractTest {

    @Override
    protected IOrderReceiptRepository newRepository() {
        return new MemoryOrderReceiptRepository();
    }
}
