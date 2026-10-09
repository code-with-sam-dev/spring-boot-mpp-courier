package com.codewithsam.courier.payment;

import com.codewithsam.courier.wallet.SignedTransfer;
import com.codewithsam.courier.wallet.TempoRpcException;
import com.codewithsam.courier.wallet.Wallet;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A chain in memory: counts every transfer signed and every broadcast. */
class FakeWallet implements Wallet {

    record Transfer(String token, String recipient, BigInteger amount, String memo) {
    }

    final List<Transfer> signed = new ArrayList<>();
    final List<String> broadcasts = new ArrayList<>();
    final Set<String> onChain = new LinkedHashSet<>();
    int broadcastFailuresToSimulate;

    @Override
    public String address() {
        return "0x103604c41634224c9914bd79988369d694c69e2a";
    }

    @Override
    public SignedTransfer signTransferWithMemo(
            String token, String recipient, BigInteger amount, String memo) {
        signed.add(new Transfer(token, recipient, amount, memo));
        String hash = "0x%064x".formatted(signed.size());
        return new SignedTransfer(hash, "0xraw" + signed.size());
    }

    @Override
    public void broadcast(SignedTransfer transfer) {
        if (broadcastFailuresToSimulate > 0) {
            broadcastFailuresToSimulate--;
            throw new TempoRpcException("RPC unreachable");
        }
        broadcasts.add(transfer.hash());
        onChain.add(transfer.hash());
    }

    @Override
    public void awaitSuccess(String txHash) {
        if (!onChain.contains(txHash)) {
            throw new TempoRpcException("no receipt for " + txHash);
        }
    }

    @Override
    public boolean isSettled(String txHash) {
        return onChain.contains(txHash);
    }

    int transfersOnChain() {
        return onChain.size();
    }
}
