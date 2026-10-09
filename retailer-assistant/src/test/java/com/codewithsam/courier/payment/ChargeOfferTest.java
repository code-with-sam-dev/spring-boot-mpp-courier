package com.codewithsam.courier.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.stripe.mpp.Challenge;
import com.stripe.mpp.methods.tempo.Attribution;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class ChargeOfferTest {

    @Test
    void readsWhatTheCourierIsAskingFor() {
        Challenge challenge = TestChallenges.premiumQuote();
        String header = challenge.toWwwAuthenticate();
        Challenge parsed = Challenge.fromWwwAuthenticate(header).getFirst();

        ChargeOffer offer = ChargeOffer.from(parsed);

        assertThat(offer.method()).isEqualTo("tempo");
        assertThat(offer.intent()).isEqualTo("charge");
        assertThat(offer.amount()).isEqualByComparingTo("0.05");
        assertThat(offer.amountUnits()).isEqualTo(BigInteger.valueOf(50_000));
        assertThat(offer.currency()).isEqualTo(TestChallenges.PATH_USD);
        assertThat(offer.recipient()).isEqualTo(TestChallenges.COURIER);
        assertThat(offer.chainId()).isEqualTo(42431);
        assertThat(offer.expires()).isEqualTo(TestChallenges.NOW.plusSeconds(300));
        assertThat(offer.challengeId()).isEqualTo(challenge.id());
    }

    @Test
    void theMemoBindsThePaymentToThisServerAndThisChallenge() {
        Challenge challenge = TestChallenges.premiumQuote();

        String memo = TempoChargePayer.memoFor(challenge);

        assertThat(Attribution.isMppMemo(memo)).isTrue();
        assertThat(Attribution.verifyServer(memo, "localhost:8090")).isTrue();
        assertThat(Attribution.verifyChallengeBinding(memo, challenge.id())).isTrue();
        assertThat(Attribution.verifyChallengeBinding(memo, "another-id")).isFalse();
    }
}
