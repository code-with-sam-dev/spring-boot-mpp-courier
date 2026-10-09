package com.codewithsam.courier.payment;

import com.codewithsam.courier.wallet.SignedTransfer;
import com.stripe.mpp.Challenge;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DO NOT USE. The first version, kept for the "break it" chapter. It pays whatever
 * a challenge asks, keeps no record, and retries a failed request from the top,
 * so a lost response is paid for twice.
 */
public class UnsafePaymentClient {

    private static final Logger log =
            LoggerFactory.getLogger(UnsafePaymentClient.class);

    private final CourierHttp http;
    private final TempoChargePayer payer;
    private final PaymentAuditService audit;

    public UnsafePaymentClient(CourierHttp http, TempoChargePayer payer,
            PaymentAuditService audit) {
        this.http = http;
        this.payer = payer;
        this.audit = audit;
    }

    public PaidResponse get(URI uri) {
        try {
            return payAndFetch(uri);
        } catch (RuntimeException firstFailure) {
            log.warn("request failed ({}), retrying", firstFailure.getMessage());
            // A fresh request: a fresh challenge, and a fresh payment.
            return payAndFetch(uri);
        }
    }

    private PaidResponse payAndFetch(URI uri) {
        HttpReply first = http.get(uri, null, null);
        if (!first.isPaymentRequired()) {
            return PaidResponse.free(first.body());
        }
        Challenge challenge = TempoChargePayer.challengeFrom(first);
        ChargeOffer offer = ChargeOffer.from(challenge);

        SignedTransfer transfer = payer.sign(challenge);
        payer.settle(transfer);
        audit.record("unsafe", AuditOutcome.PAID, offer, transfer.hash(), "no policy");

        String credential = payer.authorization(challenge, transfer.hash());
        HttpReply paid = http.get(uri, credential, null);
        if (!paid.isOk()) {
            throw new IllegalStateException("courier answered " + paid.status());
        }
        return new PaidResponse(paid.body(), offer.amount(), transfer.hash());
    }
}
