package com.ticketing.system.Core.Domain.users;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import com.ticketing.system.Core.Domain.exceptions.InvalidPermissionException;
import com.ticketing.system.Core.Domain.shared.InvariantChecked;

import java.util.List;

public class CompanyAppointment implements InvariantChecked {
    private final int appointmentId; // Unique identifier for the appointment (could be UUID or int)
    private final int companyId; // ID of the company this appointment belongs to
    private final int targetId; // ID of the user being appointed
    private final int inviterId; // ID of the user who created the appointment
    private final CompanyRole role; // Role assigned to the target user
    private AppointmentStatus status; // Current status of the appointment
    private EnumSet<Permission> permissions; // Permissions granted to the target user
    private final LocalDateTime createdAt; // Timestamp of when the appointment was created

    public CompanyAppointment(
            int appointmentId,
            int companyId,
            int targetId,
            int inviterId,
            CompanyRole role,
            AppointmentStatus status,
            List<Permission> permissions) {
        this.appointmentId = appointmentId;
        this.companyId = companyId;
        this.targetId = targetId;
        this.inviterId = inviterId;
        this.role = role;
        this.status = status;
        // Empty/null-safe: EnumSet.copyOf throws on an empty non-EnumSet collection, and would
        // run before checkInvariants(). The Manager/Owner permission rules are enforced below.
        this.permissions = (permissions == null || permissions.isEmpty())
                ? EnumSet.noneOf(Permission.class)
                : EnumSet.copyOf(permissions);
        this.createdAt = LocalDateTime.now();
        checkInvariants();
    }

    @Override
    public void checkInvariants() {
        if (role == null) {
            throw new IllegalStateException("CompanyAppointment invariant violated: role must not be null");
        }
        if (status == null) {
            throw new IllegalStateException("CompanyAppointment invariant violated: status must not be null");
        }
        if (permissions == null) {
            throw new IllegalStateException("CompanyAppointment invariant violated: permissions must not be null");
        }
        if (role == CompanyRole.Manager && permissions.isEmpty()) {
            throw new IllegalStateException("CompanyAppointment invariant violated: Manager role must have at least one permission");
        }
        if (role == CompanyRole.Owner && !permissions.isEmpty()) {
            throw new IllegalStateException("CompanyAppointment invariant violated: Owner role must not have explicit permissions");
        }
        if (createdAt == null) {
            throw new IllegalStateException("CompanyAppointment invariant violated: createdAt must not be null");
        }
    }

    // this constructor is specifically for the creation of the company founder
    // appointment, which is always an active owner with no permissions (as owners
    // have all permissions implicitly).
    public static CompanyAppointment FoundingAppointment(
            int appointmentId,
            int companyId,
            int targetId,
            int inviterId) {
        return new CompanyAppointment(
                appointmentId,
                companyId,
                targetId,
                inviterId,
                CompanyRole.Owner,
                AppointmentStatus.ACTIVE,  // active because the founder is active immediately, used when opening a company.
                null);
    }

    public static CompanyAppointment ManagerAppointment(
            int appointmentId,
            int companyId,
            int targetId,
            int inviterId,
            List<Permission> permissions) {
        return new CompanyAppointment(
                appointmentId,
                companyId,
                targetId,
                inviterId,
                CompanyRole.Manager,
                AppointmentStatus.PENDING,
                permissions);
    }

    public static CompanyAppointment OwnerAppointment(
            int appointmentId,
            int companyId,
            int targetId,
            int inviterId) {
        return new CompanyAppointment(
                appointmentId,
                companyId,
                targetId,
                inviterId,
                CompanyRole.Owner,
                AppointmentStatus.PENDING,
                null);
    }

    public void setPermissions(EnumSet<Permission> newPermissions) {
        this.permissions = newPermissions;
    }

    // ---------------------------------------------------------------------------
    // Skeleton additions — AppointmentStatus lifecycle + accessors.
    // ---------------------------------------------------------------------------

    // UC-23 / UC-24 — PENDING -> ACTIVE on target acceptance.
    public void accept() {
        if (this.status != AppointmentStatus.PENDING) {
            throw new IllegalStateException("Only pending appointments can be accepted.");
        }
        this.status = AppointmentStatus.ACTIVE;
    }

    // UC-23 / UC-24 — PENDING -> REJECTED on target rejection.
    public void reject() {
        if (this.status != AppointmentStatus.PENDING) {
            throw new IllegalStateException("Only pending appointments can be rejected.");
        }
        this.status = AppointmentStatus.REJECTED;
    }

    // UC-24 — ACTIVE -> REVOKED. Owner appointments do NOT support revoke (II.4.9 Cancelled in v0).
    public void revoke(int revokerId) {
        if (this.status != AppointmentStatus.ACTIVE) {
            throw new IllegalStateException("Only active appointments can be revoked.");
        }
        if (this.role == CompanyRole.Owner) {
            if (this.inviterId != revokerId && this.targetId != revokerId) {
                // if the appointment is an owner appointment, either the original inviter or the target themselves can revoke it (per UC-24 owner revoke condition).
                throw new IllegalArgumentException("Only the original inviter or target can revoke this appointment.");
            }
        } else {
            if (this.inviterId != revokerId) {
                throw new IllegalArgumentException("Only the original inviter can revoke this appointment.");
            }
        }
        this.status = AppointmentStatus.REVOKED;
    }

    // UC-24 — replace permission set with validation (existing setPermissions does
    // raw set).
    public void updatePermissions(EnumSet<Permission> newPermissions) {
        if (this.role != CompanyRole.Manager) {
            throw new InvalidPermissionException("Only manager appointments can have permissions.");
        }
        if (this.status != AppointmentStatus.ACTIVE) {
            throw new IllegalStateException("Only active appointments can have permissions updated.");
        }
        if (newPermissions == null || newPermissions.isEmpty()) {
            throw new IllegalArgumentException("Manager role must have at least one permission.");
        }
        this.permissions = EnumSet.copyOf(newPermissions);
    }

    public boolean hasPermission(Permission permission) {
        if (this.status != AppointmentStatus.ACTIVE) {
            return false; // Inactive appointments do not grant permissions
        }
        if (this.role == CompanyRole.Owner) {
            return true; // Owners have all permissions
        }
        return this.permissions.contains(permission);
    }
    
    
    /// Getters for all fields (no setters except for permissions, as role/status
    /// changes are handled by specific methods above to enforce lifecycle rules).

    public int getCompanyId() {
        return this.companyId;
    }

    public int getTargetId() {
        return this.targetId;
    }

    public AppointmentStatus getStatus() {
        return this.status;
    }

    public CompanyRole getRole() {
        return this.role;
    }

    public int getInviterId() {
        return inviterId;
    }

    public EnumSet<Permission> getPermissions() {
        return permissions;
    }

    public int getAppointmentId() {
        return this.appointmentId;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }
}