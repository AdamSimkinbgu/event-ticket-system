package com.ticketing.system.Core.Domain.users;

import java.util.List;

public class CompanyAppointment {
    private int companyId;
    private int targetId;
    private int inviterId;
    private List<Permission> permissions;

    public CompanyAppointment(int companyId, int targetId, int inviterId, List<Permission> permissions) {
        this.companyId = companyId;
        this.targetId = targetId;
        this.inviterId = inviterId;
        this.permissions = permissions;
    }

}