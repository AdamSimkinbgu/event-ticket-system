package com.ticketing.system.Core.Domain.users;

import java.util.List;

public class ManagementInvitation {

    private final int companyId;
    private final int targetId;
    private final int inviterId;
    private final List<Permission> permissions;

    public ManagementInvitation(int companyId, int targetId, int inviterId, List<Permission> permissions) {
        this.companyId = companyId;
        this.targetId = targetId;
        this.inviterId = inviterId;
        this.permissions = permissions;
    }

    public int getCompanyId() {
         return this.companyId;
    }

    public int getTargetId() {
        return this.targetId;
    }

    public int getInviterId() {
        return this.inviterId;
    }

    public List<Permission> getPermissions() {
        return this.permissions;
    }


    
}
