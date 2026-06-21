package com.ticketing.system.Core.Domain.company;

import java.util.ArrayList;
import java.util.List;

import com.ticketing.system.Core.Domain.exceptions.UnauthorizedActionException;
import com.ticketing.system.Core.Domain.shared.InvariantChecked;
import com.ticketing.system.Core.Domain.policies.purchase.AndPurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.NoPurchasePolicy;
import com.ticketing.system.Core.Domain.policies.purchase.PurchasePolicy;

public class ProductionCompany implements InvariantChecked {
    private final int companyId;
    /**
     * The original creator of the company. Immutable: the founder cannot be
     * removed from {@link #ownerIds} and cannot self-resign. The founder
     * always remains an owner — the two roles are coupled by design.
     */
    private final int founderId;
    /**
     * All current owners (includes {@link #founderId} at all times). Any owner
     * can add or remove another owner; non-founder owners can also self-resign.
     */
    private final List<Integer> ownerIds;
    private CompanyStatus companyStatus;
    private String name;
    private String description;
    private Double rating;
    private List<DiscountPolicy> discountPolicies;
    private PurchasePolicy purchasePolicy;
    private List<Integer> managers;
    
    public ProductionCompany(int companyId, int founderId, String name, CompanyStatus companyStatus, String description, Double rating) {
        this.companyId = companyId;
        this.founderId = founderId;
        this.ownerIds = new ArrayList<>();
        this.ownerIds.add(founderId); // founder is the first owner
        this.name = name;
        this.description = description;
        this.rating = rating;
        this.companyStatus = companyStatus;
        this.discountPolicies = new ArrayList<>();
        this.purchasePolicy = new NoPurchasePolicy();
        this.managers = new ArrayList<>();
        checkInvariants();
    }

    public void addManager(int targetId) {
        if (managers.contains(targetId)) {
            throw new RuntimeException("The target user is already a manager of this company");
        }
        managers.add(targetId);
    }


    /**
     * Add a new owner. Caller (the {@code actorId}) must already be an owner.
     * No-op if {@code newOwnerId} is already an owner.
     *
     * @throws UnauthorizedActionException
     * if {@code actorId} is not an owner
     */
    public void addOwner(int actorId, int newOwnerId) {
        if (!isOwner(actorId)) {
            throw new UnauthorizedActionException("Only existing owners can add new owners");
        }
        if (this.ownerIds.contains(newOwnerId)) {
            return;
        }
        if (this.managers.contains(newOwnerId)) {
            // If the new owner is currently a manager, they should be removed from the managers list, upgrade.
            managers.remove(Integer.valueOf(newOwnerId));
        }
        this.ownerIds.add(newOwnerId);
    }

    /**
     * Remove an owner. Caller must be an owner. The founder cannot be removed.
     * @throws UnauthorizedActionException
     * if {@code actorId} is not an owner.
     * @throws IllegalStateException                                                   
     * if {@code targetId} is the founder, or {@code targetId} is not an owner.
     */
    public void removeOwner(int actorId, int targetId) {
        if (!isOwner(actorId)) {
            throw new UnauthorizedActionException("Only owners can remove other owners");
        }
        if (targetId == this.founderId) {
            throw new IllegalStateException("The founder cannot be removed as owner");
        }
        if (!this.ownerIds.contains(targetId)) {
            throw new IllegalStateException("User is not an owner of this company");
        }
        this.ownerIds.remove(Integer.valueOf(targetId));
    }

    /**
     * Self-resign as owner. The founder cannot resign (their two roles —
     * founder and owner — are immutably coupled).
     * @throws IllegalStateException if the caller is the founder or not an owner
     */
    public void resignAsOwner(int userId) {
        if (userId == this.founderId) {
            throw new IllegalStateException("The founder cannot resign as owner");
        }
        if (!this.ownerIds.contains(userId)) {
            throw new IllegalStateException("User is not an owner of this company");
        }
        this.ownerIds.remove(Integer.valueOf(userId));
    }


    // general appointment revoke for manager or owner, whatever is active for the target user right now.
    public void RevokeAppointment(int targetId) {
        if (targetId == founderId) {
            throw new IllegalStateException("The founder cannot be revoked");
        }
        if (managers.contains(targetId)) {
            managers.remove(Integer.valueOf(targetId));
        } else if (ownerIds.contains(targetId)) {
            ownerIds.remove(Integer.valueOf(targetId));
        } else {
            throw new RuntimeException("User is not a manager or owner, cannot revoke appointment");
        }
    }

    // ---------------------------------------------------------------------------
    // Skeleton additions — CompanyStatus lifecycle + cycle check + getters.
    // ---------------------------------------------------------------------------
    public List<Integer> getManagers() {
        return this.managers;
    }

    /**
     * Legacy accessor — returns the founder's id. Most call sites should
     * migrate to {@link #isOwner(int)} for permission checks or
     * {@link #getOwnerIds()} for the full owner list.
     */
    public int getOwnerId() {
        return this.founderId;
    }

    public List<Integer> getOwnersIds() {
        return this.ownerIds;
    }

    /** Snapshot of all current owners (includes the founder). */
    public List<Integer> getOwnerIds() {
        return new ArrayList<>(this.ownerIds);
    }

    /** True iff {@code userId} is a current owner of this company. */
    public boolean isOwner(int userId) {
        return this.ownerIds.contains(userId);
    }

    

    public CompanyStatus getStatus() {
        return this.companyStatus;
    }

    public boolean isActive() {
        return this.companyStatus == CompanyStatus.ACTIVE;
    }

    /*
     * // II.4.13.x (Cancelled v0; defensive).
     * public void close(String reason) {
     * throw new
     * UnsupportedOperationException("II.4.13.x (Cancelled in v0): not implemented"
     * );
     * }
     * 
     * // II.4.14 (Cancelled v0; defensive).
     * public void reopen() {
     * throw new
     * UnsupportedOperationException("II.4.14 (Cancelled in v0): not implemented");
     * }
     */

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

    // UC-18 — Founder is the original creator, immutable for the lifetime of the
    // company.
    public int getFounderId() {
        return founderId;
    }

    public int getCompanyId() {
        return companyId;
    }

    /**
     * Permission gate for owner-only actions. Now accepts <em>any</em> current
     * owner (per the multi-owner refactor) rather than founder-only.
     *
     * @throws UnauthorizedActionException if {@code ownerId2} is not a current
     *                                     owner
     */
    public void checkowner(int ownerId2) {
        if (!isOwner(ownerId2)) {
            throw new UnauthorizedActionException("Only an owner can perform this action");
        }
    }

    @Override
    public void checkInvariants() {
        if (companyId <= 0) {
            throw new IllegalStateException(
                    "ProductionCompany invariant violated: companyId must be positive (was " + companyId + ")");
        }
        if (founderId <= 0) {
            throw new IllegalStateException(
                    "ProductionCompany invariant violated: founderId must be positive (was " + founderId + ")");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("ProductionCompany invariant violated: name must be non-blank");
        }
        if (companyStatus == null) {
            throw new IllegalStateException("ProductionCompany invariant violated: status must not be null");
        }
        if (discountPolicies == null) {
            throw new IllegalStateException(
                    "ProductionCompany invariant violated: discountPolicies list must not be null");
        }
       if (purchasePolicy == null) {
             throw new IllegalStateException("ProductionCompany invariant violated: purchasePolicy must not be null");
}
        if (managers == null) {
            throw new IllegalStateException("ProductionCompany invariant violated: managers map must not be null");
        }

        if (ownerIds == null || ownerIds.isEmpty()) {
            throw new IllegalStateException("ProductionCompany invariant violated: ownerIds list must be non-empty");
        }
        if (!ownerIds.contains(founderId)) {
            throw new IllegalStateException("ProductionCompany invariant violated: founder must always be in ownerIds");
        }
        // No owner can also be a manager (the roles are mutually exclusive)
        for (Integer ownerIdEntry : ownerIds) {
            if (managers.contains(ownerIdEntry)) {
                throw new IllegalStateException(
                        "ProductionCompany invariant violated: owner " + ownerIdEntry + " cannot also be a manager");
            }
        }
    }

    public PurchasePolicy getPurchasePolicy() {
    if (this.purchasePolicy == null) {
        this.purchasePolicy = new NoPurchasePolicy();
    }

    return this.purchasePolicy;
}

public void setPurchasePolicy(PurchasePolicy purchasePolicy) {
    if (purchasePolicy == null) {
        throw new IllegalArgumentException("Purchase policy cannot be null");
    }

    this.purchasePolicy = purchasePolicy;
}

public void extendPurchasePolicy(PurchasePolicy additionalPolicy) {
    if (additionalPolicy == null) {
        throw new IllegalArgumentException("Additional purchase policy cannot be null");
    }

    if (this.purchasePolicy == null) {
        this.purchasePolicy = new NoPurchasePolicy();
    }

    this.purchasePolicy = new AndPurchasePolicy(
            this.purchasePolicy,
            additionalPolicy
    );
}

}
