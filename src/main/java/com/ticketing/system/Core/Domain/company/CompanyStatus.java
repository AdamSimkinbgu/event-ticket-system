package com.ticketing.system.Core.Domain.company;

/**
 * Operational status of a {@code ProductionCompany}. Inactive companies are
 * excluded from the public catalog (UC-3 / UC-7) and reject management
 * operations.
 */
public enum CompanyStatus {
    /** The company is operating normally and visible in the catalog. */
    ACTIVE,
    /** The company is closed/suspended; its events are hidden and locked. */
    INACTIVE
}
