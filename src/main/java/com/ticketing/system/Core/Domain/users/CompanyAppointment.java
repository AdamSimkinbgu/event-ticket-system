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

    public int getCompanyId() {
        return this.companyId;
    }

    public int getTargetId() {
      return this.targetId;
    }

    public void setPermissions(List<Permission> newPermissions) {
        this.permissions = newPermissions;
    }

    // ---------------------------------------------------------------------------
    // Skeleton additions — AppointmentStatus lifecycle + accessors.
    // ---------------------------------------------------------------------------

    // UC-23 / UC-24 — PENDING -> ACTIVE on target acceptance.
    public void accept() {
        throw new UnsupportedOperationException("UC-23/24: not implemented");
    }

    // UC-23 / UC-24 — PENDING -> REJECTED on target rejection.
    public void reject() {
        throw new UnsupportedOperationException("UC-23/24: not implemented");
    }

    // UC-24 — ACTIVE -> REVOKED. Owner appointments do NOT support revoke (II.4.9 Cancelled in v0).
    public void revoke() {
        throw new UnsupportedOperationException("UC-24: not implemented");
    }

    // UC-24 — replace permission set with validation (existing setPermissions does raw set).
    public void updatePermissions(List<Permission> newPermissions) {
        throw new UnsupportedOperationException("UC-24: not implemented (with validation)");
    }

    public boolean hasPermission(Permission permission) {
        throw new UnsupportedOperationException("not implemented");
    }

    public AppointmentStatus getStatus() {
        throw new UnsupportedOperationException("not implemented (add status field)");
    }

    public CompanyRole getRole() {
        throw new UnsupportedOperationException("not implemented (add role field)");
    }

    public int getInviterId() {
        return inviterId;
    }

    public List<Permission> getPermissions() {
        return permissions;
    }

    public String getAppointmentId() {
        throw new UnsupportedOperationException("not implemented (add appointmentId field)");
    }
}