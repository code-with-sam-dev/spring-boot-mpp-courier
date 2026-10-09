package com.codewithsam.courier.provider.payment;

import com.stripe.mpp.Challenge;
import com.stripe.mpp.Receipt;
import com.stripe.mpp.error.PaymentRequiredException;
import com.stripe.mpp.server.ChargeRequest;
import com.stripe.mpp.server.MppHandler;
import com.stripe.mpp.server.VerifyResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Lets a request through only with a verified payment for the premium quote. */
@Service
public class PaymentGate {

    private static final Logger log = LoggerFactory.getLogger(PaymentGate.class);

    private final MppHandler payments;
    private final ChargeRequest premiumQuoteCharge;

    public PaymentGate(MppHandler payments, ChargeRequest premiumQuoteCharge) {
        this.payments = payments;
        this.premiumQuoteCharge = premiumQuoteCharge;
    }

    /**
     * Returns the receipt of a verified payment. Throws a PaymentException otherwise,
     * which PaymentProblemAdvice turns into 402 with a fresh challenge.
     */
    public Receipt charge(String authorization) {
        VerifyResult result = payments.charge(authorization, premiumQuoteCharge);
        if (result instanceof VerifyResult.Verified verified) {
            log.info("payment verified, tx {}", verified.receipt().reference());
            return verified.receipt();
        }
        throw new PaymentRequiredException(payments.realm(), premiumQuoteCharge.description());
    }

    public Challenge freshChallenge() {
        return payments.challenge(premiumQuoteCharge);
    }
}
