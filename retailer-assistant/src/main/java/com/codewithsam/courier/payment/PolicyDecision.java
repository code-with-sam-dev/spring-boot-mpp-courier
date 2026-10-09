package com.codewithsam.courier.payment;

public record PolicyDecision(boolean approved, String reason) {

    static PolicyDecision approve() {
        return new PolicyDecision(true, "within policy");
    }

    static PolicyDecision refuse(String reason) {
        return new PolicyDecision(false, reason);
    }
}
