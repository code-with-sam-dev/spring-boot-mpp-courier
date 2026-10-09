package com.codewithsam.courier.wallet;

import java.math.BigInteger;
import java.util.Map;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;
import tools.jackson.databind.JsonNode;

/**
 * Pays with an ordinary EIP-1559 transaction, which Tempo accepts unchanged.
 * There is no gas coin on Tempo: fees are charged in a USD stablecoin.
 */
public class TempoWallet implements Wallet {

    private static final int RECEIPT_POLLS = 60;

    private final TempoRpc rpc;
    private final long chainId;
    private final Credentials credentials;

    public TempoWallet(TempoRpc rpc, long chainId, String privateKey) {
        this.rpc = rpc;
        this.chainId = chainId;
        this.credentials = Credentials.create(privateKey);
    }

    @Override
    public String address() {
        return credentials.getAddress();
    }

    @Override
    public SignedTransfer signTransferWithMemo(
            String token, String recipient, BigInteger amount, String memo) {
        String data = TempoCalls.transferWithMemo(recipient, amount, memo);
        BigInteger nonce =
                rpc.quantity("eth_getTransactionCount", address(), "pending");
        BigInteger gasPrice = rpc.quantity("eth_gasPrice");
        RawTransaction tx = RawTransaction.createTransaction(chainId, nonce,
                estimateGas(token, data), token, BigInteger.ZERO, data,
                gasPrice, gasPrice.multiply(BigInteger.TWO));
        return sign(tx, chainId, credentials);
    }

    static SignedTransfer sign(RawTransaction tx, long chainId,
            Credentials credentials) {
        String raw = Numeric.toHexString(
                TransactionEncoder.signMessage(tx, chainId, credentials));
        return new SignedTransfer(Hash.sha3(raw), raw);
    }

    @Override
    public void broadcast(SignedTransfer transfer) {
        if (isSettled(transfer.hash())) {
            return;
        }
        try {
            rpc.call("eth_sendRawTransaction", transfer.rawTransaction());
        } catch (TempoRpcException e) {
            // A node that already has this exact transaction says so; that is fine.
            if (!e.getMessage().contains("already known")) {
                throw e;
            }
        }
    }

    @Override
    public void awaitSuccess(String txHash) {
        for (int poll = 0; poll < RECEIPT_POLLS; poll++) {
            if (isSettled(txHash)) {
                return;
            }
            sleep(500);
        }
        throw new TempoRpcException("no receipt after 30 s for " + txHash);
    }

    @Override
    public boolean isSettled(String txHash) {
        JsonNode receipt = rpc.call("eth_getTransactionReceipt", txHash);
        if (receipt == null || receipt.isNull()) {
            return false;
        }
        if (!"0x1".equals(receipt.get("status").asString())) {
            throw new TempoRpcException("transaction reverted: " + txHash);
        }
        return true;
    }

    // A brand new account's first transfer costs about 545,000 gas on Tempo
    // (account creation is charged), so never hard-code a gas limit.
    private BigInteger estimateGas(String token, String data) {
        BigInteger estimate = rpc.quantity("eth_estimateGas",
                Map.of("from", address(), "to", token, "data", data));
        return estimate.multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TempoRpcException("interrupted", e);
        }
    }
}
