package com.codewithsam.courier.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codewithsam.courier.provider.misbehaviour.LostResponseSimulator;
import com.codewithsam.courier.provider.misbehaviour.SimulatedLostResponseException;
import org.junit.jupiter.api.Test;

class LostResponseTest {

    @Test
    void losesOnlyAsManyPaidResponsesAsConfigured() {
        LostResponseSimulator simulator = new LostResponseSimulator(1);

        assertThatThrownBy(simulator::afterPaidResponseBuilt)
                .isInstanceOf(SimulatedLostResponseException.class);
        simulator.afterPaidResponseBuilt();
        assertThat(simulator.lostSoFar()).isEqualTo(1);
    }

    @Test
    void anHonestCourierLosesNothing() {
        LostResponseSimulator simulator = new LostResponseSimulator(0);

        simulator.afterPaidResponseBuilt();

        assertThat(simulator.lostSoFar()).isZero();
    }
}
