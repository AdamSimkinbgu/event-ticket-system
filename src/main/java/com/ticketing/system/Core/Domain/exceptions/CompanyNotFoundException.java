package com.ticketing.system.Core.Domain.exceptions;

/**
 * Specific subclass of {@link EntityNotFoundException} for ProductionCompany
 * lookups. UC-18, UC-19, UC-22, UC-25.
 */
public class CompanyNotFoundException extends EntityNotFoundException {

    /**
     * @param companyId the id that was looked up
     */
    public CompanyNotFoundException(Object companyId) {
        super("ProductionCompany", companyId);
    }
}
