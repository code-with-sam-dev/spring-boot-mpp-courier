package com.codewithsam.courier.provider.misbehaviour;

import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * For the "break it" chapter only. Drops the first N paid responses AFTER the
 * payment is verified and recorded, as a timeout or a crashed proxy would.
 * Zero, the default, is an honest courier.
 */
@Component
public class LostResponseSimulator {

    private static final Logger log =
            LoggerFactory.getLogger(LostResponseSimulator.class);

    private final int responsesToLose;
    private final AtomicInteger lost = new AtomicInteger();

    public LostResponseSimulator(
            @Value("${courier.misbehaviour.lose-paid-responses:0}")
            int responsesToLose) {
        this.responsesToLose = responsesToLose;
    }

    public void afterPaidResponseBuilt() {
        if (lost.incrementAndGet() <= responsesToLose) {
            log.warn("dropping a paid response on purpose");
            throw new SimulatedLostResponseException();
        }
        lost.decrementAndGet();
    }

    public int lostSoFar() {
        return lost.get();
    }
}
