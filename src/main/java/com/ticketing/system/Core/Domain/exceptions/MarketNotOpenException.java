package com.ticketing.system.Core.Domain.exceptions;

/**
 * Thrown when a transaction is attempted while the market is not OPEN.
 * UC-32 (the admin must open the market before transactions are allowed).
 */
public class MarketNotOpenException extends DomainException {

    /** Creates the exception with the default "market is not open" message. */
    public MarketNotOpenException() {
        super("Market is not currently open for transactions");
    }

    /**
     * @param reason why the market is not open
     */
    public MarketNotOpenException(String reason) {
        super("Market is not open: " + reason);
    }
}
