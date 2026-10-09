package com.codewithsam.courier.payment;

/** Money moved, the result did not arrive, and the client refuses to pay twice. */
public class PaidButUndeliveredException extends RuntimeException {

    private final String txHash;

    public PaidButUndeliveredException(String txHash) {
        super("paid in " + txHash + " but the courier did not return the quote;"
                + " not paying again, retry later or ask the courier");
        this.txHash = txHash;
    }

    public String txHash() {
        return txHash;
    }
}
