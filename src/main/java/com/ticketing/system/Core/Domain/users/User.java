package com.ticketing.system.Core.Domain.users;

import java.security.Permission;
import java.util.List;


public class User {

    MemberProfile memberProfile;
    List <SystemRole> systemRoles;
    Inbox inbox;
    int userId;
    String username;
    String password;
    public void addCompanyAppointment(int companyId, int ownerid, List<Permission> permissions) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addCompanyAppointment'");
    }
    public void setAsmanager(int companyId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setAsmanager'");
    }
    public void InvitetoCompanyAppointment(int companyId, int ownerid,
            List<com.ticketing.system.Core.Domain.users.Permission> permissions) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'InvitetoCompanyAppointment'");
    }

    




    
}
