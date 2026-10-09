package com.codewithsam.courier.payment;

import static com.codewithsam.courier.payment.TestChallenges.COURIER;
import static com.codewithsam.courier.payment.TestChallenges.NOW;
import static com.codewithsam.courier.payment.TestChallenges.PATH_USD;
import static org.assertj.core.api.Assertions.assertThat;

import com.codewithsam.courier.config.PaymentPolicyProperties;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PaymentPolicyServiceTest {

    private PaymentAuditService audit;
    private PaymentPolicyService policy;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        audit = new PaymentAuditService(TestDatabase.fresh(), clock);
        policy = new PaymentPolicyService(new PaymentPolicyProperties(
                new BigDecimal("0.10"), new BigDecimal("0.25"), List.of(COURIER),
                PATH_USD, 42431), audit, clock);
    }

    @Test
    void approvesTheNormalPremiumQuote() {
        PolicyDecision decision = policy.approve(offer(TestChallenges.premiumQuote()));

        assertThat(decision.approved()).isTrue();
        assertThat(audit.spentToday()).isEqualByComparingTo("0.05");
    }

    @Test
    void refusesAnotherPaymentMethod() {
        assertRefused(TestChallenges.charge("stripe", "50000", PATH_USD, COURIER, 42431,
                NOW.plusSeconds(60)), "payment method stripe is not allowed");
    }

    @Test
    void refusesAnotherChain() {
        assertRefused(TestChallenges.charge("tempo", "50000", PATH_USD, COURIER, 4217,
                NOW.plusSeconds(60)), "chain 4217 is not the approved chain 42431");
    }

    @Test
    void refusesAnotherCurrency() {
        String usdc = "0x20c000000000000000000000b9537d11c60e8b50";
        assertRefused(TestChallenges.charge("tempo", "50000", usdc, COURIER, 42431,
                NOW.plusSeconds(60)), "currency " + usdc + " is not pathUSD");
    }

    @Test
    void refusesAnUnknownRecipient() {
        String stranger = "0x00000000000000000000000000000000000bad00";
        assertRefused(TestChallenges.charge("tempo", "50000", PATH_USD, stranger, 42431,
                NOW.plusSeconds(60)), "recipient " + stranger + " is not an approved courier");
    }

    @Test
    void refusesAnExpiredChallenge() {
        assertRefused(TestChallenges.charge("tempo", "50000", PATH_USD, COURIER, 42431,
                NOW.minusSeconds(1)), "challenge expired at 2026-10-09T09:59:59Z");
    }

    @Test
    void refusesAPriceOverThePerRequestLimit() {
        assertRefused(TestChallenges.charge("tempo", "5000000", PATH_USD, COURIER, 42431,
                NOW.plusSeconds(60)), "5.00 pathUSD is over the 0.10 per-request limit");
    }

    @Test
    void refusesOnceTheDailyBudgetWouldBeExceeded() {
        for (int i = 0; i < 5; i++) {
            assertThat(policy.approve(offer(TestChallenges.premiumQuote())).approved()).isTrue();
        }

        assertRefused(TestChallenges.premiumQuote(),
                "0.05 pathUSD would bring today's spend to 0.30, over the 0.25 daily budget");
    }

    @Test
    void recipientsAreComparedWithoutCase() {
        assertThat(policy.approve(offer(TestChallenges.charge("tempo", "50000", PATH_USD,
                COURIER.toUpperCase().replace("0X", "0x"), 42431, NOW.plusSeconds(60))))
                .approved()).isTrue();
    }

    @Test
    void everyRefusalIsAudited() {
        policy.approve(offer(TestChallenges.charge("tempo", "5000000", PATH_USD, COURIER,
                42431, NOW.plusSeconds(60))));

        assertThat(audit.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.outcome()).isEqualTo(AuditOutcome.REFUSED);
            assertThat(entry.amount()).isEqualByComparingTo("5.00");
            assertThat(entry.detail()).contains("per-request limit");
            assertThat(entry.txHash()).isNull();
        });
        assertThat(audit.spentToday()).isZero();
    }

    private void assertRefused(com.stripe.mpp.Challenge challenge, String reason) {
        PolicyDecision decision = policy.approve(offer(challenge));
        assertThat(decision.approved()).isFalse();
        assertThat(decision.reason()).isEqualTo(reason);
    }

    private static ChargeOffer offer(com.stripe.mpp.Challenge challenge) {
        return ChargeOffer.from(challenge);
    }
}
