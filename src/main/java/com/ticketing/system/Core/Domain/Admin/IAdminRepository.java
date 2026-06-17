package com.ticketing.system.Core.Domain.Admin;

import com.ticketing.system.Core.Domain.shared.IRepository;

// Aggregate-root entry point for the Admin aggregate.
public interface IAdminRepository extends IRepository<Admin, Integer> {

    Admin findById(int adminId);

    Admin findByUsername(String username);

    // Used by SystemAdminService.initializePlatform() per UC-1 / I.1.4
    // (default-admin existence check).
    boolean existsAny();

    void save(Admin admin);
}
