package com.ticketing.system.Core.Domain.users;

/**
 * A discrete authority that can be granted to a company Manager via a
 * CompanyAppointment (UC-24 / II.4.7.2). An Owner always holds all permissions;
 * a Manager holds only the subset explicitly granted to them.
 *
 * <p>TODO: the full set is still open — team to confirm per UC-24 / II.4.7.2
 * (open question #10 in the design walkthrough).
 */
public enum Permission {
    /** Manage ticket inventory and availability. */
    MANAGE_INVENTORY,
    /** Configure the venue map and seating layout. */
    CONFIGURE_VENUE,
    /** Edit purchase and discount policies. */
    EDIT_POLICIES,
    /** View sales reports and history. */
    VIEW_SALES,
    /** Respond to customer inquiries and complaints. */
    RESPOND_TO_INQUIRIES
}
