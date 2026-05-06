package com.ticketing.system.Core.Domain.users;

import java.util.Collection;
import java.util.List;


public class User {

    private MemberProfile memberProfile;
    private List <ManagementInvitation> managementInvitations;
    private Inbox inbox;
    private int userId;
    private String username;
    private String password;
    private List<CompanyAppointment> companyAppointments;
    
    
    
  
    public void InvitetoCompanyAppointment(int companyId, int ownerid, List<Permission> permissions) {
        managementInvitations.add(new ManagementInvitation(companyId, this.userId, ownerid, permissions));
    }
    public ManagementInvitation acceptInvitation(int companyId) {
     for (ManagementInvitation invitation : managementInvitations) {
         if (invitation.getCompanyId() == companyId) {
             memberProfile.setAsManager(companyId);
             managementInvitations.remove(invitation);
             companyAppointments.add(new CompanyAppointment(companyId, this.userId, invitation.getInviterId(), invitation.getPermissions()));
             return invitation;
         }
         
     }
        throw new RuntimeException("No invitation found for the specified company");    
    }

    public List<ManagementInvitation> getManagementInvitations() {
       return this.managementInvitations;
    }

    public void rejectInvitation(int companyId) {
        for (ManagementInvitation invitation : managementInvitations) {
            if (invitation.getCompanyId() == companyId) {
                managementInvitations.remove(invitation);
                return;
            }
        }
        throw new RuntimeException("No invitation found for the specified company");
    }

    public void removeCompanyAppointment(int companyId) {
        for (CompanyAppointment appointment : companyAppointments) {
            if (appointment.getCompanyId() == companyId) {
                companyAppointments.remove(appointment);
                this.memberProfile.RevokeManagerRole(companyId);
                return;
            }
        }
        throw new RuntimeException("No appointment found for the specified company");
    }

}
