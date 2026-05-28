package com.ticketing.system.Core.Domain.company;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.ticketing.system.Core.Domain.exceptions.UnauthorizedActionException;
import com.ticketing.system.Core.Domain.shared.InvariantChecked;
import com.ticketing.system.Core.Domain.users.Permission;



public class ProductionCompany implements InvariantChecked {
    private final int companyId;
    private final int ownerId;
    // private final List<Integer> owners;
    private CompanyStatus companyStatus;
    private String name;
    private String description;
    private Double rating;
    private List<DiscountPolicy> discountPolicies;
    private List<PurchasePolicy> purchasePolicies;
    private HashMap <Integer, List<Permission>> pendingManagers; 
    private HashMap<Integer, List<Permission>> managers;

    public ProductionCompany(int companyId, int ownerId, String name, CompanyStatus companyStatus, String description, Double rating) {
        this.companyId = companyId;
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.rating = rating;
        this.companyStatus = companyStatus;
        this.discountPolicies = new ArrayList<>();
        this.purchasePolicies = new ArrayList<>();
        this.managers = new HashMap<>();
        this.pendingManagers = new HashMap<>();
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

    public int getOwnerId() {
        return this.ownerId;
    }

    public CompanyStatus getStatus() {
        return this.companyStatus;
    }

    public boolean isActive() {
        return this.companyStatus == CompanyStatus.ACTIVE;
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
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getRating() {
        return this.rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
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

    @Override
    public void checkInvariants() {
        if (companyId <= 0) {
            throw new IllegalStateException("ProductionCompany invariant violated: companyId must be positive (was " + companyId + ")");
        }
        if (ownerId <= 0) {
            throw new IllegalStateException("ProductionCompany invariant violated: ownerId must be positive (was " + ownerId + ")");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("ProductionCompany invariant violated: name must be non-blank");
        }
        if (companyStatus == null) {
            throw new IllegalStateException("ProductionCompany invariant violated: status must not be null");
        }
        if (discountPolicies == null) {
            throw new IllegalStateException("ProductionCompany invariant violated: discountPolicies list must not be null");
        }
        if (purchasePolicies == null) {
            throw new IllegalStateException("ProductionCompany invariant violated: purchasePolicies list must not be null");
        }
        if (managers == null) {
            throw new IllegalStateException("ProductionCompany invariant violated: managers map must not be null");
        }
        if (pendingManagers == null) {
            throw new IllegalStateException("ProductionCompany invariant violated: pendingManagers map must not be null");
        }
        // No user can be in both pending and active manager maps simultaneously
        for (Integer targetId : managers.keySet()) {
            if (pendingManagers.containsKey(targetId)) {
                throw new IllegalStateException("ProductionCompany invariant violated: user " + targetId + " is both active manager and pending");
            }
        }
        // Owner cannot also be a manager
        if (managers.containsKey(ownerId) || pendingManagers.containsKey(ownerId)) {
            throw new IllegalStateException("ProductionCompany invariant violated: owner cannot also be a manager");
        }
    }
}
