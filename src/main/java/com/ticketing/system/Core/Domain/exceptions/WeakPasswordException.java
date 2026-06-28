package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when a password fails complexity rules at registration / change.
 * UC-11. SLR.2 (data security &amp; privacy).
 */
public class WeakPasswordException extends DomainException {

    /**
     * @param reason which complexity rule was not met
     */
    public WeakPasswordException(String reason) {
        super("Password does not meet security requirements: " + reason);
    }
}
