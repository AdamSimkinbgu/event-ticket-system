package com.ticketing.system.Core.Domain.users;

import java.util.Collection;
import java.util.ArrayList;
import java.util.List;

import com.ticketing.system.Core.Application.interfaces.IPasswordHasher;


public class User {

    private MemberProfile memberProfile;
    private List <ManagementInvitation> managementInvitations;
    private int userId;
    private String username;
    private String email;
    private String password;
    private List<CompanyAppointment> companyAppointments;

    public User(int userId, String username, String email, String password) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.password = password;
        this.managementInvitations = new ArrayList<>();
        this.memberProfile = new MemberProfile();
        this.companyAppointments = new ArrayList<>();
        // Messaging is its own aggregate now (Conversation / IConversationRepository in messaging/);
        // User no longer holds an inbox field.
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
    public void ModifyManagerPermissions(int companyId, int targetId, List<Permission> newPermissions) {
        for (CompanyAppointment appointment : companyAppointments) {
            if (appointment.getCompanyId() == companyId && appointment.getTargetId() == targetId) {
                appointment.setPermissions(newPermissions);
                return;
            }
        }
        throw new RuntimeException("No appointment found for the specified company and target user");
    }

    // ---------------------------------------------------------------------------
    // Skeleton additions — accessors + secure password change.
    // ---------------------------------------------------------------------------

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    // UC-12 — verifies a candidate raw password against the stored hash.
    // Hash never leaves this entity; the hasher does the comparison in place.
    public boolean verifyPassword(String rawPassword, IPasswordHasher hasher) {
        return hasher.matches(rawPassword, this.password);
    }

    public MemberProfile getMemberProfile() {
        return memberProfile;
    }

    public List<CompanyAppointment> getCompanyAppointments() {
        return companyAppointments;
    }

    // UC-11 / future profile-edit — domain receives already-hashed password (per lecture 2).
    public void changePassword(String newHashedPassword) {
        throw new UnsupportedOperationException("UC-11: not implemented");
    }

    // Helper for permission-checking flows (UC-19/21/22/24).
    public boolean hasAppointmentInCompany(int companyId) {
        throw new UnsupportedOperationException("not implemented");
    }

    // Returns the User's appointment in the given company, or null if absent.
    public CompanyAppointment getAppointmentForCompany(int companyId) {
        throw new UnsupportedOperationException("not implemented");
    }
}
