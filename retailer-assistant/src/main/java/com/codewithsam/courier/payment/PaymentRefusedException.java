package com.codewithsam.courier.payment;

/** The policy said no. Nothing was signed and nothing was broadcast. */
public class PaymentRefusedException extends RuntimeException {

    public PaymentRefusedException(String reason) {
        super(reason);
    }
}
