package com.ticketing.system.unit.infrastructure.persistence.ActiveOrderPersistence;

import com.ticketing.system.Core.Domain.ActiveOrder.IActiveOrderRepository;
import com.ticketing.system.Infrastructure.persistence.MemoryActiveOrderRepository;

class MemoryActiveOrderRepositoryContractTest extends IActiveOrderRepositoryContractTest {

    @Override
    protected IActiveOrderRepository newRepository() {
        return new MemoryActiveOrderRepository();
    }
}
