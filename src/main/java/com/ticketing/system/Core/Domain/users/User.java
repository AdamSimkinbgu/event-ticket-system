package com.ticketing.system.Core.Domain.users;

import java.util.ArrayList;
import java.util.List;

import com.ticketing.system.Core.Application.interfaces.IPasswordHasher;
import com.ticketing.system.Core.Domain.shared.InvariantChecked;


public class User implements InvariantChecked {

    private MemberProfile memberProfile;
    private List <ManagementInvitation> managementInvitations;
    private int userId;
    private String username;
    private String email;
    private String password;
    private List<CompanyAppointment> companyAppointments;
    private int age;


    public User(int userId, String username, String email, String password, int age) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.password = password;
        this.age = age;
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

    /** Email registered at sign-up. Used for uniqueness checks. UC-11. */
    public String getEmail() {
        return email;
    }
public int getAge() {
    return age;
}

    /**
     * Verifies a candidate raw password against the stored hash. UC-12.
     *
     * <p>The hash never leaves this entity — the hasher does the comparison
     * in place. The {@link IPasswordHasher} collaborator is passed in rather
     * than held as a field so the entity stays a pure domain object.
     */
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
    public boolean isOwnerInCompany(int companyId) {
        for (CompanyAppointment appointment : companyAppointments) {
            if (this.memberProfile != null && this.memberProfile.getCompanyId() == companyId
                    && this.memberProfile.getCompanyRole() == CompanyRole.Owner) {
                // if he is an owner
                return true;
            }
        }
        return false;
    }

    public boolean hasPermissionInCompany(int companyId, Permission permission) {
        for (CompanyAppointment appointment : companyAppointments) {
            if (appointment.getCompanyId() == companyId && appointment.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    // Returns the User's appointment in the given company, or null if absent.
    public CompanyAppointment getAppointmentForCompany(int companyId) {
        for (CompanyAppointment appointment : companyAppointments) {
            if (appointment.getCompanyId() == companyId) {
                return appointment;
            }
        }
        return null;
    }

    @Override
    public void checkInvariants() {
        if (userId <= 0) {
            throw new IllegalStateException("User invariant violated: userId must be positive (was " + userId + ")");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("User invariant violated: username must be non-blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("User invariant violated: email must be non-blank");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("User invariant violated: password hash must be non-blank");
        }
        if (managementInvitations == null) {
            throw new IllegalStateException("User invariant violated: managementInvitations list must not be null");
        }
        if (companyAppointments == null) {
            throw new IllegalStateException("User invariant violated: companyAppointments list must not be null");
        }
    }
}
