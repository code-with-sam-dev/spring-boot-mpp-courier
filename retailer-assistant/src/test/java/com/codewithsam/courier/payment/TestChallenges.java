package com.codewithsam.courier.payment;

import com.stripe.mpp.Challenge;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Real MPP challenges, built offline the way the courier's mpp-java builds them. */
final class TestChallenges {

    static final String COURIER = "0xe614e43e4fbf9867bdf4e2ddbd3f628658b93ddb";
    static final String PATH_USD = "0x20c0000000000000000000000000000000000000";
    static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");

    private TestChallenges() {
    }

    static Challenge premiumQuote() {
        return charge("tempo", "50000", PATH_USD, COURIER, 42431, NOW.plusSeconds(300));
    }

    static Challenge charge(String method, String units, String currency,
            String recipient, int chainId, Instant expires) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("amount", units);
        request.put("currency", currency);
        request.put("methodDetails", Map.of("chainId", chainId));
        request.put("recipient", recipient);
        return Challenge.create("test-secret", "localhost:8090", method, "charge",
                request, expires.toString(), "Premium courier quotation", null);
    }
}
