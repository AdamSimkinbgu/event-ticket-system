package com.ticketing.system.Core.Domain.users;

public class MemberProfile {

    private int companyId;
    CompanyRole companyRole;

    public void setAsManager(int companyId) {
        this.companyId = companyId;
        this.companyRole = CompanyRole.Manager;
    }
    
}
