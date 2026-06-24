package com.ticketing.system.unit.infrastructure.persistence.SessionPersistence;

import java.time.Clock;

import com.ticketing.system.Core.Domain.users.ISessionRepository;
import com.ticketing.system.Infrastructure.persistence.SessionPersistence.MemorySessionRepository;

class MemorySessionRepositoryContractTest extends ISessionRepositoryContractTest {

    @Override
    protected ISessionRepository newRepository() {
        return new MemorySessionRepository(Clock.systemUTC());
    }
}
