package com.ticketing.system.Core.Domain.company;

import java.util.List;
import java.util.Optional;

// Aggregate-root entry point for the ProductionCompany aggregate.
public interface IProductionCompanyRepository {

    ProductionCompany getCompanyById(int companyId);

    void updateCompany(ProductionCompany company);

    // UC-18 — uniqueness check at registration.
    Optional<ProductionCompany> findByName(String name);

    boolean existsByName(String name);

    // UC-3 / UC-7 — public catalog excludes closed/suspended companies.
    List<ProductionCompany> findActive();

    // Owner's own list of companies they founded (informational).
    List<ProductionCompany> findByFounder(int founderUserId);

    // UC-18 — persist newly-registered company.
    void save(ProductionCompany company);

    // UC-18- genrate company id 
    int nextId();
}
