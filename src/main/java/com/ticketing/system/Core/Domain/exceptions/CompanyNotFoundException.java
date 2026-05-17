package com.ticketing.system.Core.Domain.exceptions;

// Specific subclass of EntityNotFoundException for ProductionCompany lookups.
// UC-18, UC-19, UC-22, UC-25.
public class CompanyNotFoundException extends EntityNotFoundException {

    public CompanyNotFoundException(Object companyId) {
        super("ProductionCompany", companyId);
    }
}
