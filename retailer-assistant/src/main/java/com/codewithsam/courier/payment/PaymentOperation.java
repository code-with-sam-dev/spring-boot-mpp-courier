package com.codewithsam.courier.payment;

import com.codewithsam.courier.wallet.SignedTransfer;
import com.stripe.mpp.Challenge;

public record PaymentOperation(
        String requestKey,
        OperationState state,
        String wwwAuthenticate,
        String txHash,
        String rawTransaction,
        String body,
        String receipt) {

    public Challenge challenge() {
        return Challenge.fromWwwAuthenticate(wwwAuthenticate).getFirst();
    }

    public SignedTransfer signedTransfer() {
        return new SignedTransfer(txHash, rawTransaction);
    }
}
