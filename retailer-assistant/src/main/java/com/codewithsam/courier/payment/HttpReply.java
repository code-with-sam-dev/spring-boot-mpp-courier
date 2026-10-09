package com.codewithsam.courier.payment;

import org.springframework.http.HttpHeaders;

public record HttpReply(int status, HttpHeaders headers, String body) {

    public boolean isOk() {
        return status == 200;
    }

    public boolean isPaymentRequired() {
        return status == 402;
    }

    public boolean isServerError() {
        return status >= 500;
    }
}
