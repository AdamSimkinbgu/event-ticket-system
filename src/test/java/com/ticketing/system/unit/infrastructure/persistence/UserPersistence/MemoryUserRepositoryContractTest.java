package com.ticketing.system.unit.infrastructure.persistence.UserPersistence;

import com.ticketing.system.Core.Domain.users.IUserRepository;
import com.ticketing.system.Infrastructure.persistence.MemoryUserRepository;

class MemoryUserRepositoryContractTest extends IUserRepositoryContractTest {

    @Override
    protected IUserRepository newRepository() {
        return new MemoryUserRepository();
    }
}
