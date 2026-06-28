package com.ticketing.system.Core.Domain.company;

import java.util.List;
import java.util.Optional;

import com.ticketing.system.Core.Domain.shared.IRepository;

/**
 * Aggregate-root entry point for the ProductionCompany aggregate.
 */
public interface IProductionCompanyRepository extends IRepository<ProductionCompany, Integer> {

    /**
     * @param companyId the company id
     * @return the company, or {@code null} if none exists with that id
     */
    ProductionCompany getCompanyById(int companyId);

    /**
     * @param company the company whose changes to persist
     */
    void updateCompany(ProductionCompany company);

    /**
     * UC-18 — uniqueness check at registration.
     *
     * @param name the company name
     * @return the company with that name if present
     */
    Optional<ProductionCompany> findByName(String name);

    /**
     * @param name the company name
     * @return {@code true} if a company with that name already exists
     */
    boolean existsByName(String name);

    /**
     * UC-3 / UC-7 — the public catalog excludes closed/suspended companies.
     *
     * @return all active companies
     */
    List<ProductionCompany> findActive();

    /**
     * The owner's own list of companies they founded (informational).
     *
     * @param founderUserId the founder's user id
     * @return companies founded by the user
     */
    List<ProductionCompany> findByFounder(int founderUserId);

    /**
     * Admin view — every company in the system regardless of status.
     *
     * @return all companies
     */
    List<ProductionCompany> findAll();

    /**
     * UC-18 — persist a newly-registered company.
     *
     * @param company the company to persist
     */
    void save(ProductionCompany company);

    /**
     * UC-18 — generate a company id.
     *
     * @return the next available company id
     */
    int nextId();
}
