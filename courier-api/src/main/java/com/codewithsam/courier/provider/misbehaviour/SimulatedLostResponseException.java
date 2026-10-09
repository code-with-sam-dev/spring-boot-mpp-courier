package com.codewithsam.courier.provider.misbehaviour;

public class SimulatedLostResponseException extends RuntimeException {

    public SimulatedLostResponseException() {
        super("paid response dropped on purpose (lose-paid-responses)");
    }
}
