package com.codewithsam.courier.payment;

import java.math.BigDecimal;

/** The courier's answer, and what (if anything) it cost. */
public record PaidResponse(String body, BigDecimal amountPaid, String txHash) {

    static PaidResponse free(String body) {
        return new PaidResponse(body, BigDecimal.ZERO, null);
    }
}
