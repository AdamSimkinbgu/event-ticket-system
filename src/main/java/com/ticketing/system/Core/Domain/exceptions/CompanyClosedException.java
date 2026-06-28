package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when an operation references a company whose status is closed/suspended.
 * II.4.13.x (company closure, currently Cancelled in v0 scope) — defensive: even
 * though the closure UC isn't built, the domain should still reject ops on closed
 * companies. UC-3 (browse), UC-9 (reserve from a closed company's events),
 * UC-19/21/22/23/24 (manage a closed company).
 */
public class CompanyClosedException extends DomainException {

    /**
     * @param companyId the closed company's id
     */
    public CompanyClosedException(Object companyId) {
        super("Company " + companyId + " is closed");
    }
}
