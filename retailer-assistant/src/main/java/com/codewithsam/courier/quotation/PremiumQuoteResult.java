package com.codewithsam.courier.quotation;

import java.math.BigDecimal;

/**
 * The premium quote, or why there is none. quoteFee is what the assistant paid
 * the courier for this answer, in pathUSD on Tempo testnet.
 */
public record PremiumQuoteResult(
        Status status,
        DeliveryQuote quote,
        BigDecimal quoteFee,
        String paymentTx,
        String explorerUrl,
        String reason) {

    public enum Status { PAID, REFUSED_BY_POLICY, PAID_BUT_UNDELIVERED }
}
