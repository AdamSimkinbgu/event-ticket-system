package com.ticketing.system.Infrastructure.persistence.ProductionCompanyPersistence;

import com.ticketing.system.Infrastructure.persistence.RepositoryLocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Repository;

import com.ticketing.system.Core.Domain.company.CompanyStatus;
import com.ticketing.system.Core.Domain.company.IProductionCompanyRepository;
import com.ticketing.system.Core.Domain.company.ProductionCompany;

/**
 * In-memory {@link IProductionCompanyRepository} for V1. Lets Spring wire
 * CatalogService / CompanyManagementService / EventManagementService.
 */
@Repository
public class MemoryProductionCompanyRepository implements IProductionCompanyRepository {

    private final Map<Integer, ProductionCompany> companiesById = new ConcurrentHashMap<>();
    private final AtomicInteger idSequence = new AtomicInteger(1);
    private final RepositoryLocks<Integer> locks = new RepositoryLocks<>();

    @Override
    public void lockForUpdate(Integer id) {
        locks.lock(id);
    }

    @Override
    public void unlock(Integer id) {
        locks.unlock(id);
    }

    @Override
    public void save(ProductionCompany company) {
        companiesById.put(company.getCompanyId(), company);
    }

    @Override
    public void updateCompany(ProductionCompany company) {
        companiesById.put(company.getCompanyId(), company);
    }

    @Override
    public ProductionCompany getCompanyById(int companyId) {
        if (!companiesById.containsKey(companyId)) {
            throw new RuntimeException("Company with ID " + companyId + " not found");
        }
        return companiesById.get(companyId);
    }

    @Override
    public Optional<ProductionCompany> findByName(String name) {
        if (name == null)
            return Optional.empty();
        for (ProductionCompany c : companiesById.values()) {
            if (name.equals(c.getName()))
                return Optional.of(c);
        }
        return Optional.empty();
    }

    @Override
    public boolean existsByName(String name) {
        return findByName(name).isPresent();
    }

    @Override
    public List<ProductionCompany> findActive() {
        List<ProductionCompany> result = new ArrayList<>();
        for (ProductionCompany c : companiesById.values()) {
            if (c.getStatus() == CompanyStatus.ACTIVE)
                result.add(c);
        }
        return result;
    }

    @Override
    public List<ProductionCompany> findByFounder(int founderUserId) {
        List<ProductionCompany> result = new ArrayList<>();
        for (ProductionCompany c : companiesById.values()) {
            if (c.getFounderId() == founderUserId)
                result.add(c);
        }
        return result;
    }

    @Override
    public int nextId() {
        return idSequence.getAndIncrement();
    }
}
