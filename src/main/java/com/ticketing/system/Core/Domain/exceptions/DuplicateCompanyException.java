package com.ticketing.system.Core.Domain.exceptions;

// Thrown when company registration uses an already-taken company name. UC-18.
public class DuplicateCompanyException extends DomainException {

    public DuplicateCompanyException(String name) {
        super("Company name already registered: " + name);
    }
}
