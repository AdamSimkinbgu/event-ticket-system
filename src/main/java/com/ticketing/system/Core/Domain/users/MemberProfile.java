package com.ticketing.system.Core.Domain.users;

/*
    this class is irelevant for the current implementation 
    it will be deleted in the futere
    */

//TODO: delete this class 
public class MemberProfile {

    private int companyId;
    CompanyRole companyRole;

    public void setAsManager(int companyId) {
        this.companyId = companyId;
        this.companyRole = CompanyRole.Manager;
    }

    public void RevokeManagerRole(int companyId2) {
        if (this.companyId == companyId2) {
            // this.companyRole = CompanyRole.None;
        }
    }

    public int getCompanyId() {
        return this.companyId;
    }

    public CompanyRole getCompanyRole() {
        return this.companyRole;
    }

}
