package com.ticketing.system.Core.Domain.exceptions;

// Thrown when an email fails format validation. UC-11.
public class InvalidEmailFormatException extends DomainException {

    public InvalidEmailFormatException(String email) {
        super("Invalid email format: " + email);
    }
}
