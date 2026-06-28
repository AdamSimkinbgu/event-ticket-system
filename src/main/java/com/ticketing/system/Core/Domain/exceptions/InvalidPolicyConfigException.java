package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when an Owner submits a malformed PurchasePolicy / DiscountPolicy.
 * Examples: negative max-per-buyer, percentageOff &gt; 100%, invalidFrom &gt;
 * validUntil. UC-21.
 */
public class InvalidPolicyConfigException extends DomainException {

    /**
     * @param reason what is wrong with the policy configuration
     */
    public InvalidPolicyConfigException(String reason) {
        super("Invalid policy configuration: " + reason);
    }
}
