package com.ticketing.system.Core.Domain.users;

/**
 * A user's role within a {@code ProductionCompany}, established by a
 * CompanyAppointment. Owners hold full authority; Managers act within the
 * {@link Permission}s granted to them (UC-23 / UC-24).
 */
public enum CompanyRole {
    /** A Manager — acts within the explicit permissions granted to them. */
    Manager,
    /** An Owner — holds full authority over the company. */
    Owner
}
