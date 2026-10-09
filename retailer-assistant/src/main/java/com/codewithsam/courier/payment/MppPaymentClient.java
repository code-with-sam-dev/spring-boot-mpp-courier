package com.codewithsam.courier.payment;

import com.codewithsam.courier.wallet.SignedTransfer;
import com.stripe.mpp.Challenge;
import com.stripe.mpp.Receipt;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;

/**
 * An HTTP client that can pay for what it fetches, following MPP:
 * GET, 402 with a challenge, policy check, pay on Tempo, GET again with a credential.
 *
 * Paying twice for one request is the failure it exists to prevent. Every paid
 * request is written down (PaymentOperationStore) before money moves, and once a
 * transfer is signed the client only ever resumes it: same transaction, same
 * credential, same Idempotency-Key.
 */
public class MppPaymentClient {

    private static final Logger log = LoggerFactory.getLogger(MppPaymentClient.class);
    private static final int DELIVERY_ATTEMPTS = 3;

    private final CourierHttp http;
    private final PaymentPolicyService policy;
    private final TempoChargePayer payer;
    private final PaymentOperationStore operations;
    private final PaymentAuditService audit;

    public MppPaymentClient(CourierHttp http, PaymentPolicyService policy,
            TempoChargePayer payer, PaymentOperationStore operations,
            PaymentAuditService audit) {
        this.http = http;
        this.policy = policy;
        this.payer = payer;
        this.operations = operations;
        this.audit = audit;
    }

    /** requestKey names the logical request: the same key is never paid for twice. */
    public PaidResponse get(URI uri, String requestKey) {
        Optional<PaymentOperation> earlier = operations.find(requestKey);
        if (earlier.isPresent()) {
            return resume(uri, earlier.get());
        }

        HttpReply first = http.get(uri, null, idempotencyKey(requestKey));
        if (!first.isPaymentRequired()) {
            return PaidResponse.free(first.body());
        }

        Challenge challenge = TempoChargePayer.challengeFrom(first);
        PolicyDecision decision = policy.approve(ChargeOffer.from(challenge));
        if (!decision.approved()) {
            throw new PaymentRefusedException(decision.reason());
        }

        SignedTransfer transfer = payer.sign(challenge);
        // Written down before any money moves. From here on we only ever resume.
        return resume(uri, operations.startPayment(requestKey, challenge, transfer));
    }

    private PaidResponse resume(URI uri, PaymentOperation operation) {
        Challenge challenge = operation.challenge();
        ChargeOffer offer = ChargeOffer.from(challenge);
        String txHash = operation.txHash();
        switch (operation.state()) {
            case REDEEMED -> {
                return new PaidResponse(operation.body(), offer.amount(), txHash);
            }
            case PENDING_PAYMENT -> {
                // Broadcasts the SAME signed transfer; a no-op if it is already mined.
                payer.settle(operation.signedTransfer());
                operations.markBroadcast(operation.requestKey());
                audit.record("safe", AuditOutcome.PAID, offer, txHash,
                        "transfer mined");
            }
            case BROADCAST, UNDELIVERED -> log.info("resuming paid request {}", txHash);
        }
        return redeem(uri, operation, offer);
    }

    private PaidResponse redeem(URI uri, PaymentOperation operation,
            ChargeOffer offer) {
        String txHash = operation.txHash();
        String authorization = payer.authorization(operation.challenge(), txHash);
        for (int attempt = 1; attempt <= DELIVERY_ATTEMPTS; attempt++) {
            Optional<HttpReply> reply =
                    presentCredential(uri, authorization, operation);
            if (reply.isPresent() && reply.get().isOk()) {
                String body = reply.get().body();
                String receipt = matchingReceipt(reply.get(), txHash);
                operations.markRedeemed(operation.requestKey(), body, receipt);
                audit.record("safe", AuditOutcome.DELIVERED, offer, txHash,
                        "receipt matches");
                return new PaidResponse(body, offer.amount(), txHash);
            }
            if (reply.isPresent() && !reply.get().isServerError()) {
                break; // the courier answered, and the answer is no
            }
            audit.record("safe", AuditOutcome.RESPONSE_LOST, offer, txHash,
                    "attempt " + attempt + ": no answer, same credential again");
        }
        operations.markUndelivered(operation.requestKey());
        audit.record("safe", AuditOutcome.UNDELIVERED, offer, txHash,
                "not paying again");
        throw new PaidButUndeliveredException(txHash);
    }

    private Optional<HttpReply> presentCredential(URI uri, String authorization,
            PaymentOperation operation) {
        try {
            String key = idempotencyKey(operation.requestKey());
            return Optional.of(http.get(uri, authorization, key));
        } catch (ResourceAccessException timeout) {
            return Optional.empty();
        }
    }

    private static String matchingReceipt(HttpReply reply, String txHash) {
        String header = reply.headers().getFirst("Payment-Receipt");
        if (header == null || !txHash.equalsIgnoreCase(
                Receipt.fromPaymentReceipt(header).reference())) {
            throw new IllegalStateException("200 without a receipt for " + txHash);
        }
        return header;
    }

    private static String idempotencyKey(String requestKey) {
        byte[] bytes = requestKey.getBytes(StandardCharsets.UTF_8);
        return UUID.nameUUIDFromBytes(bytes).toString();
    }
}
