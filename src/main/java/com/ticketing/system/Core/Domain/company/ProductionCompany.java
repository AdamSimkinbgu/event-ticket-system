package com.ticketing.system.Core.Domain.company;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.ticketing.system.Core.Domain.users.Permission;


public class ProductionCompany {
    private Inbox inbox;
    private ComapanyStatus companyStatus;
    private List<DiscountPolicy> discountPolicies;
    private List<PurchasePolicy> purchasePolicies;
    private final int companyId;
    private final int ownerId;
    private HashMap <Integer, List<Permission>> pendingManagers; 
    private HashMap <Integer, List<Permission>> managers; 

    public ProductionCompany(int companyId, int ownerId) {
        this.companyId = companyId;
        this.ownerId = ownerId;
        this.inbox = new Inbox();
        this.companyStatus = new ComapanyStatus();
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


    
    
    

    
}
