package com.ticketing.system.Core.Domain.users;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;


public class User {

    private MemberProfile memberProfile;
    private List <ManagementInvitation> managementInvitations;
    private Inbox inbox;
    private int userId;
    private String username;
    private String password;
    private List<CompanyAppointment> companyAppointments;

    public User(int userId, String username, String password) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.managementInvitations = new ArrayList<>();
        this.inbox = new Inbox();
        this.memberProfile = new MemberProfile();
        this.companyAppointments = new ArrayList<>();
    }
    
    
    
  
    public void InvitetoCompanyAppointment(int companyId, int ownerid, List<Permission> permissions) {
         ManagementInvitation invitation = new ManagementInvitation(companyId, this.userId, ownerid, permissions);
        managementInvitations.add(invitation);
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

    public void setAsManager(int companyId) {
        memberProfile.setAsManager(companyId);
    }




    
}
