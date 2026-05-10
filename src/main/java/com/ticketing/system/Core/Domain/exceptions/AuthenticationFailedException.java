package com.ticketing.system.Core.Domain.exceptions;

// Thrown when login credentials don't match. Always raised with a generic message
// to prevent username-enumeration attacks (lecture 2's security guidance).
// UC-12.
public class AuthenticationFailedException extends DomainException {

    public AuthenticationFailedException() {
        super("Invalid credentials");
    }
}
