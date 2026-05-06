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
    }


    public int inviteManager(int ownerid, int targetId, List<Permission> permissions) {
           if (ownerid != this.ownerId) {
            throw new RuntimeException("Only the owner can assign managers");
        }
        else if (managers.containsKey(targetId) || targetId == ownerId) {
            throw new RuntimeException("User is already a manager or the owner");
        }else if (permissions == null || permissions.isEmpty()) {
            throw new IllegalArgumentException("Manager must receive at least one permission");
        } else {
            pendingManagers.put(targetId, permissions);
        }
        return 1;
    }

    
    

    
}
