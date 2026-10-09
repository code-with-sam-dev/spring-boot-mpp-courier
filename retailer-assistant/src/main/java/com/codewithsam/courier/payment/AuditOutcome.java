package com.codewithsam.courier.payment;

public enum AuditOutcome {
    /** The policy allowed this payment; counts towards the daily budget. */
    APPROVED,
    /** The policy said no. Nothing was signed or broadcast. */
    REFUSED,
    /** The transfer is on chain. */
    PAID,
    /** Paid, but the answer did not arrive. The same credential is presented again. */
    RESPONSE_LOST,
    /** The courier returned the quote with a receipt for this transfer. */
    DELIVERED,
    /** Paid, and the courier would not hand the result over. Needs a person. */
    UNDELIVERED
}
