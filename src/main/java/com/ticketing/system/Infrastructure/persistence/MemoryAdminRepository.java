package com.ticketing.system.Infrastructure.persistence;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.ticketing.system.Core.Domain.Admin.Admin;
import com.ticketing.system.Core.Domain.Admin.IAdminRepository;

/**
 * In-memory {@link IAdminRepository} for V1.
 *
 * <p>Lets Spring wire SystemAdminService. A future JPA-backed adapter
 * replaces this class without touching the application layer.
 */
@Repository
public class MemoryAdminRepository implements IAdminRepository {

    private final Map<Integer, Admin> adminsById = new ConcurrentHashMap<>();

    @Override
    public void save(Admin admin) {
        adminsById.put(admin.getId(), admin);
    }

    @Override
    public Admin findById(int adminId) {
        return adminsById.get(adminId);
    }

    @Override
    public Admin findByUsername(String username) {
        if (username == null) return null;
        for (Admin a : adminsById.values()) {
            if (username.equals(a.getUsername())) return a;
        }
        return null;
    }

    @Override
    public boolean existsAny() {
        return !adminsById.isEmpty();
    }
}
