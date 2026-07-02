package com.ticketing.system.unit.infrastructure.persistence.ProductionCompanyPersistence;

import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Infrastructure.persistence.ProductionCompanyPersistence.MemoryProductionCompanyRepository;

class MemoryProductionCompanyRepositoryContractTest extends IProductionCompanyRepositoryContractTest {

    @Override
    protected IProductionCompanyRepository newRepository() {
        return new MemoryProductionCompanyRepository();
    }
}
