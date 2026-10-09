package com.codewithsam.courier.provider.misbehaviour;

public class SimulatedLostResponseException extends RuntimeException {

    public SimulatedLostResponseException() {
        super("paid response deliberately dropped (courier.misbehaviour.lose-paid-responses)");
    }
}
