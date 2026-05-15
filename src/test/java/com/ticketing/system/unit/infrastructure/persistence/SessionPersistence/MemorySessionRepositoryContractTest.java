package com.ticketing.system.unit.infrastructure.persistence.SessionPersistence;

import com.ticketing.system.Core.Domain.users.ISessionRepository;
import com.ticketing.system.Infrastructure.persistence.MemorySessionRepository;

class MemorySessionRepositoryContractTest extends ISessionRepositoryContractTest {

    @Override
    protected ISessionRepository newRepository() {
        return new MemorySessionRepository();
    }
}
