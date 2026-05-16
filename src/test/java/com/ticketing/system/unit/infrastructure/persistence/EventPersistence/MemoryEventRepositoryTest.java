package com.ticketing.system.unit.infrastructure.persistence.EventPersistence;

import com.ticketing.system.Core.Domain.events.IEventRepository;
import com.ticketing.system.Infrastructure.persistence.MemoryEventRepository;

public class MemoryEventRepositoryTest extends IEventRepositoryContractTest {

    @Override
    protected IEventRepository newRepository() {
        return new MemoryEventRepository();
    }

}
