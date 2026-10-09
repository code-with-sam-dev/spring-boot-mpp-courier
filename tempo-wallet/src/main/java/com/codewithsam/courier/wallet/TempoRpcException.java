package com.codewithsam.courier.wallet;

public class TempoRpcException extends RuntimeException {

    public TempoRpcException(String message) {
        super(message);
    }

    public TempoRpcException(String message, Throwable cause) {
        super(message, cause);
    }
}
