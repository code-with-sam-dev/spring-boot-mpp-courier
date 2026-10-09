package com.codewithsam.courier.payment;

import com.codewithsam.courier.config.PaymentPolicyProperties;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * Decides whether the assistant may pay a challenge, before anything is signed.
 * An agent must never have unlimited payment authority: this is the limit.
 */
@Service
public class PaymentPolicyService {

    private final PaymentPolicyProperties limits;
    private final PaymentAuditService audit;
    private final Clock clock;

    public PaymentPolicyService(PaymentPolicyProperties limits, PaymentAuditService audit,
            Clock clock) {
        this.limits = limits;
        this.audit = audit;
        this.clock = clock;
    }

    /** Synchronized so two payments cannot both squeeze under the daily budget. */
    public synchronized PolicyDecision approve(ChargeOffer offer) {
        PolicyDecision decision = check(offer);
        audit.record("safe", decision.approved() ? AuditOutcome.APPROVED : AuditOutcome.REFUSED,
                offer, null, decision.reason());
        return decision;
    }

    private PolicyDecision check(ChargeOffer offer) {
        if (!"tempo".equals(offer.method())) {
            return PolicyDecision.refuse("payment method " + offer.method() + " is not allowed");
        }
        if (!"charge".equals(offer.intent())) {
            return PolicyDecision.refuse("intent " + offer.intent() + " is not allowed");
        }
        if (offer.chainId() != limits.chainId()) {
            return PolicyDecision.refuse("chain " + offer.chainId()
                    + " is not the approved chain " + limits.chainId());
        }
        if (!limits.currency().equalsIgnoreCase(offer.currency())) {
            return PolicyDecision.refuse("currency " + offer.currency() + " is not pathUSD");
        }
        if (limits.allowedRecipients().stream()
                .noneMatch(allowed -> allowed.equalsIgnoreCase(offer.recipient()))) {
            return PolicyDecision.refuse(
                    "recipient " + offer.recipient() + " is not an approved courier");
        }
        if (offer.expires() == null) {
            return PolicyDecision.refuse("challenge has no expiry");
        }
        if (!offer.expires().isAfter(clock.instant())) {
            return PolicyDecision.refuse("challenge expired at " + offer.expires());
        }
        BigDecimal amount = offer.amount();
        if (amount.compareTo(limits.maxPerRequest()) > 0) {
            return PolicyDecision.refuse(money(amount) + " pathUSD is over the "
                    + money(limits.maxPerRequest()) + " per-request limit");
        }
        BigDecimal spentAfter = audit.spentToday().add(amount);
        if (spentAfter.compareTo(limits.dailyBudget()) > 0) {
            return PolicyDecision.refuse(money(amount) + " pathUSD would bring today's spend to "
                    + money(spentAfter) + ", over the " + money(limits.dailyBudget())
                    + " daily budget");
        }
        return PolicyDecision.approve();
    }

    private static String money(BigDecimal value) {
        BigDecimal plain = value.stripTrailingZeros();
        return plain.setScale(Math.max(2, plain.scale())).toPlainString();
    }
}
