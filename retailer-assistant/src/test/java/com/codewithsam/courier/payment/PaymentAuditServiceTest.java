package com.codewithsam.courier.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class PaymentAuditServiceTest {

    private static final String TX = "0x" + "ab".repeat(32);

    @Test
    void recordsEachStepWithAnExplorerLink() {
        PaymentAuditService audit = new PaymentAuditService(TestDatabase.fresh(),
                Clock.fixed(TestChallenges.NOW, ZoneOffset.UTC));
        ChargeOffer offer = ChargeOffer.from(TestChallenges.premiumQuote());

        audit.record("safe", AuditOutcome.PAID, offer, TX, "broadcast and mined");

        assertThat(audit.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.client()).isEqualTo("safe");
            assertThat(entry.amount()).isEqualByComparingTo("0.05");
            assertThat(entry.recipient()).isEqualTo(TestChallenges.COURIER);
            assertThat(entry.challengeId()).isEqualTo(offer.challengeId());
            assertThat(entry.explorerUrl())
                    .isEqualTo("https://explore.testnet.tempo.xyz/tx/" + TX);
        });
    }

    @Test
    void onlyTodaysApprovalsCountTowardsTheBudget() {
        var db = TestDatabase.fresh();
        Instant yesterday = TestChallenges.NOW.minus(Duration.ofDays(1));
        ChargeOffer offer = ChargeOffer.from(TestChallenges.premiumQuote());
        new PaymentAuditService(db, Clock.fixed(yesterday, ZoneOffset.UTC))
                .record("safe", AuditOutcome.APPROVED, offer, null, "within policy");
        PaymentAuditService today = new PaymentAuditService(db,
                Clock.fixed(TestChallenges.NOW, ZoneOffset.UTC));
        today.record("safe", AuditOutcome.APPROVED, offer, null, "within policy");
        today.record("unsafe", AuditOutcome.PAID, offer, TX, "no policy");

        assertThat(today.spentToday()).isEqualByComparingTo(new BigDecimal("0.05"));
    }
}
