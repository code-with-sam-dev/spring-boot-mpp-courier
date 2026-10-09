package com.codewithsam.courier.wallet;

import java.math.BigInteger;

/**
 * The payer's side of the chain. Signing and broadcasting are separate steps on
 * purpose: a caller can record the transaction hash before any money moves.
 */
public interface Wallet {

    String address();

    SignedTransfer signTransferWithMemo(
            String token, String recipient, BigInteger amount, String memo);

    /** Safe to call again with the same transfer: the chain accepts it once. */
    void broadcast(SignedTransfer transfer);

    /** Blocks until the transaction is mined; throws if it reverted or never landed. */
    void awaitSuccess(String txHash);

    /** True once the transaction is mined successfully, false if not seen yet. */
    boolean isSettled(String txHash);
}
