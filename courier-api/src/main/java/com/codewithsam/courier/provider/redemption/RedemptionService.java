package com.codewithsam.courier.provider.redemption;

import com.codewithsam.courier.provider.quote.QuoteRequest;
import com.codewithsam.courier.provider.quote.QuoteResponse;
import com.stripe.mpp.Credential;
import com.stripe.mpp.Receipt;
import com.stripe.mpp.error.VerificationFailedException;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Remembers what each payment bought, so a client whose response was lost can
 * collect it again. mpp-java's replay store still rejects any second use of a
 * transaction hash; this check runs before it and only ever returns the SAME
 * result to the SAME request.
 */
@Service
public class RedemptionService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionService.class);

    private final RedemptionStore store;
    private final Clock clock;

    public RedemptionService(RedemptionStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public Optional<Redemption> findEarlier(
            String authorization, String idempotencyKey, QuoteRequest request) {
        Optional<Credential> credential = parse(authorization);
        Optional<Redemption> earlier = credential.flatMap(RedemptionService::paidHash)
                .flatMap(store::findByTxHash);
        if (earlier.isEmpty()) {
            return Optional.empty();
        }
        Redemption redemption = earlier.get();
        boolean sameRequest = idempotencyKey != null
                && idempotencyKey.equals(redemption.idempotencyKey())
                && redemption.requestFingerprint().equals(request.fingerprint())
                && redemption.challengeId().equals(credential.get().challenge().id());
        if (!sameRequest) {
            throw new VerificationFailedException(
                    "transaction hash already redeemed: " + redemption.txHash());
        }
        log.info("recovered paid quote for tx {}", redemption.txHash());
        return earlier;
    }

    public void record(Receipt receipt, String authorization, String idempotencyKey,
            QuoteRequest request, QuoteResponse quote) {
        String challengeId = parse(authorization).orElseThrow().challenge().id();
        store.save(new Redemption(receipt.reference(), challengeId, idempotencyKey,
                request.fingerprint(), quote, receipt.toPaymentReceipt(), clock.instant()));
    }

    private static Optional<Credential> parse(String authorization) {
        if (authorization == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Credential.fromAuthorization(authorization));
        } catch (RuntimeException malformed) {
            return Optional.empty(); // mpp-java answers malformed credentials itself
        }
    }

    private static Optional<String> paidHash(Credential credential) {
        if (credential.payload() instanceof Map<?, ?> payload
                && "hash".equals(payload.get("type"))) {
            return Optional.of(Objects.toString(payload.get("hash"), ""));
        }
        return Optional.empty();
    }
}
