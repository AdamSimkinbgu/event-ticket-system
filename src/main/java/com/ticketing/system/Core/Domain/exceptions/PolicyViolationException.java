package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when a PurchasePolicy or DiscountPolicy invariant is violated.
 * Examples: max-tickets-per-buyer exceeded, age-limit not met, discount code
 * expired. UC-9 (reservation policy check), UC-10 (checkout policy validation
 * per II.2.8.1).
 */
public class PolicyViolationException extends DomainException {

    /**
     * @param policyName the policy that was violated
     * @param reason     why the policy rejected the operation
     */
    public PolicyViolationException(String policyName, String reason) {
        super("Policy '" + policyName + "' violated: " + reason);
    }

    /**
     * @param message custom detail message
     */
    public PolicyViolationException(String message) {
        super(message);
    }
}
