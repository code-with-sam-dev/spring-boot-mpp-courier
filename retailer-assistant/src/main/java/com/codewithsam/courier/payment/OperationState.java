package com.codewithsam.courier.payment;

/** Where one paid request has got to. It only ever moves forward. */
public enum OperationState {
    /** Signed and written down; it may or may not be on chain yet. */
    PENDING_PAYMENT,
    /** Mined. From here on only the same credential is ever presented. */
    BROADCAST,
    /** The courier returned the result and a matching receipt. */
    REDEEMED,
    /** Paid, but the courier would not hand the result over. Never paid again. */
    UNDELIVERED
}
