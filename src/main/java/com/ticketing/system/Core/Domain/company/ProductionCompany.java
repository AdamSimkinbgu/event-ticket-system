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


    
    
    

    
}
