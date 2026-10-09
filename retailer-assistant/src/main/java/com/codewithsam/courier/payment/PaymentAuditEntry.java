package com.codewithsam.courier.payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentAuditEntry(
        long id,
        Instant at,
        String client,
        AuditOutcome outcome,
        BigDecimal amount,
        String recipient,
        String challengeId,
        String txHash,
        String explorerUrl,
        String detail) {
}
