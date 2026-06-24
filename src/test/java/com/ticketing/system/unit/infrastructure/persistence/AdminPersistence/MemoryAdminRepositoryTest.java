package com.ticketing.system.unit.infrastructure.persistence.AdminPersistence;

import com.ticketing.system.Core.Domain.Admin.IAdminRepository;
import com.ticketing.system.Infrastructure.persistence.AdminPersistence.MemoryAdminRepository;

/** Runs the {@link IAdminRepositoryContractTest} suite against the in-memory adapter. */
class MemoryAdminRepositoryTest extends IAdminRepositoryContractTest {

    @Override
    protected IAdminRepository newRepository() {
        return new MemoryAdminRepository();
    }
}
