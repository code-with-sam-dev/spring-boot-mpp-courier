package com.codewithsam.courier.payment;

import com.codewithsam.courier.wallet.PathUsd;
import com.stripe.mpp.Challenge;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;

/** What a 402 challenge is asking the assistant to pay, read before paying anything. */
public record ChargeOffer(
        String challengeId,
        String realm,
        String method,
        String intent,
        BigInteger amountUnits,
        String currency,
        String recipient,
        long chainId,
        Instant expires) {

    public static ChargeOffer from(Challenge challenge) {
        Map<String, Object> request = challenge.request();
        Object details = request.getOrDefault("methodDetails", Map.of());
        Object chainId = details instanceof Map<?, ?> map ? map.get("chainId") : null;
        return new ChargeOffer(
                challenge.id(),
                challenge.realm(),
                challenge.method(),
                challenge.intent(),
                new BigInteger(String.valueOf(request.get("amount"))),
                String.valueOf(request.get("currency")),
                String.valueOf(request.get("recipient")),
                chainId instanceof Number number ? number.longValue() : -1,
                challenge.expires() == null ? null
                        : Instant.parse(challenge.expires()));
    }

    /** pathUSD has 6 decimals, so 50000 base units is 0.05. */
    public BigDecimal amount() {
        return PathUsd.fromBaseUnits(amountUnits);
    }
}
