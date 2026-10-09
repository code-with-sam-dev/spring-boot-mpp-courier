package com.codewithsam.courier.provider.redemption;

import com.codewithsam.courier.provider.quote.QuoteResponse;
import java.time.Instant;

/** One settled payment and exactly what it bought. */
public record Redemption(
        String txHash,
        String challengeId,
        String idempotencyKey,
        String requestFingerprint,
        QuoteResponse quote,
        String receipt,
        Instant redeemedAt) {
}
