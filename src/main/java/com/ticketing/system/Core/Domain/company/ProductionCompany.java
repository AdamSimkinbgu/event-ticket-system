package com.ticketing.system.Core.Domain.company;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.ticketing.system.Core.Domain.exceptions.UnauthorizedActionException;
import com.ticketing.system.Core.Domain.users.Permission;



public class ProductionCompany {
    private CompanyStatus companyStatus;
    private List<DiscountPolicy> discountPolicies;
    private List<PurchasePolicy> purchasePolicies;
    private final int companyId;
    private final int ownerId;
    private HashMap <Integer, List<Permission>> pendingManagers; 
    private HashMap <Integer, List<Permission>> managers; 

    public ProductionCompany(int companyId, int ownerId) {
        this.companyId = companyId;
        this.ownerId = ownerId;
        this.companyStatus = new CompanyStatus();
        this.discountPolicies = new ArrayList<>();
        this.purchasePolicies = new ArrayList<>();
        this.managers = new HashMap<>();
        this.pendingManagers = new HashMap<>();
    }

    public int getOwnerId() {
        return this.ownerId;
    }

    public void validateManagerInvitation(int companyId, int targetId, int ownerId, List<Permission> permissions) {
   
        if (this.companyId != companyId) {
            throw new RuntimeException("Invalid company");
        }
        else if (managers.containsKey(targetId) || pendingManagers.containsKey(targetId)) {
            throw new RuntimeException("User is already a manager or has a pending invitation");
        }
        else if (targetId == this.ownerId) {
            throw new RuntimeException("Cannot invite the company owner as a manager");
        }
        else if (permissions == null || permissions.isEmpty()) {
            throw new RuntimeException("Invalid permissions");
        }
        else{
        pendingManagers.put(targetId, permissions);
        }
    }


    public void acceptManagerInvitation(int targetId) {
    if (!pendingManagers.containsKey(targetId)) {
        throw new RuntimeException("No pending manager invitation for this user");
    }
    List<Permission> permissions = pendingManagers.get(targetId);
    pendingManagers.remove(targetId);
    managers.put(targetId, permissions);
}

    public void rejectManagerInvitation(int targetId) {
        if (!pendingManagers.containsKey(targetId)) {
            throw new RuntimeException("No pending manager invitation for this user");
        }
        pendingManagers.remove(targetId);
       
    }

    public void RevokeManager(int targetId) {
        if (managers.containsKey(targetId)) {
            managers.remove(targetId);
        }
        else {
            throw new RuntimeException("User is not a manager");
        }
    }

    public void ModifyManagerPermissions(int companyId2, int targetId, List<Permission> newPermissions) {
        if (this.companyId != companyId2) {
            throw new RuntimeException("Invalid company");
        }
        else if (!managers.containsKey(targetId)) {
            throw new RuntimeException("User is not a manager");
        }
        else if (newPermissions == null || newPermissions.isEmpty()) {
            throw new RuntimeException("Invalid permissions");
        }
        else {
            managers.put(targetId, newPermissions);
        }
    }

    // ---------------------------------------------------------------------------
    // Skeleton additions — CompanyStatus lifecycle + cycle check + getters.
    // ---------------------------------------------------------------------------
    public HashMap<Integer, List<Permission>> getManagers() {
        return this.managers;
    }


    public CompanyStatus getStatus() {
        throw new UnsupportedOperationException("not implemented (add status enum field)");
    }

    public boolean isActive() {
        throw new UnsupportedOperationException("not implemented");
    }

    // II.4.13.x (Cancelled v0; defensive).
    public void close(String reason) {
        throw new UnsupportedOperationException("II.4.13.x (Cancelled in v0): not implemented");
    }

    // II.4.14 (Cancelled v0; defensive).
    public void reopen() {
        throw new UnsupportedOperationException("II.4.14 (Cancelled in v0): not implemented");
    }

    public String getName() {
        throw new UnsupportedOperationException("not implemented (add name field)");
    }

    public String getDescription() {
        throw new UnsupportedOperationException("not implemented (add description field)");
    }

    // UC-18 — Founder is the original creator (currently aliased to ownerId; see open Q).
    public int getFounderId() {
        return ownerId;
    }

    public int getCompanyId() {
        return companyId;
    }

    // UC-23 — II.4.8.3 cycle prevention. Walks the appointment tree and returns false
    // if the proposed appointment would create a cycle.
    public boolean canAppoint(int appointerUserId, int appointeeUserId) {
        throw new UnsupportedOperationException("UC-23 / II.4.8.3: not implemented");
    }

    public void ValidateManagerOrOwner(int userId) {
        if (userId == ownerId) {
            return; 
        }

        List<Permission> userPermissions = managers.get(userId);
        if (userPermissions == null || !userPermissions.contains(Permission.CONFIGURE_VENUE)) {
            throw new RuntimeException("User is not authorized to configure venue");
        }
    }

    public HashMap<Integer, List<Permission>> getPendingManagers() {

        return this.pendingManagers;
    }

    public void checkowner(int ownerId2) {
        if (this.ownerId != ownerId2) {
            throw new UnauthorizedActionException ("Only the owner can perform this action");
        }
    }
}
