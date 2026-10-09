package com.codewithsam.courier.payment;

import com.codewithsam.courier.wallet.SignedTransfer;
import com.codewithsam.courier.wallet.TempoNetwork;
import com.codewithsam.courier.wallet.Wallet;
import com.stripe.mpp.Challenge;
import com.stripe.mpp.Credential;
import com.stripe.mpp.methods.tempo.Attribution;
import java.util.List;
import java.util.Map;

/**
 * Turns a Tempo charge challenge into money on chain and a credential that proves
 * it. Knows nothing about limits: that is PaymentPolicyService's job.
 */
public class TempoChargePayer {

    private final Wallet wallet;
    private final TempoNetwork network;

    public TempoChargePayer(Wallet wallet, TempoNetwork network) {
        this.wallet = wallet;
        this.network = network;
    }

    public static Challenge challengeFrom(HttpReply reply) {
        List<String> headers = reply.headers().getOrEmpty("WWW-Authenticate");
        List<Challenge> challenges = Challenge.fromWwwAuthenticate(headers);
        return challenges.stream()
                .filter(challenge -> "tempo".equals(challenge.method()))
                .findFirst()
                .orElse(challenges.getFirst());
    }

    /**
     * Every MPP SDK rejects a Tempo payment unless its memo is bound to this server
     * (realm) and this challenge (id), so nobody can claim someone else's transfer.
     */
    public static String memoFor(Challenge challenge) {
        return Attribution.encode(challenge.realm(), challenge.id());
    }

    public SignedTransfer sign(Challenge challenge) {
        ChargeOffer offer = ChargeOffer.from(challenge);
        return wallet.signTransferWithMemo(offer.currency(), offer.recipient(),
                offer.amountUnits(), memoFor(challenge));
    }

    public void settle(SignedTransfer transfer) {
        wallet.broadcast(transfer);
        wallet.awaitSuccess(transfer.hash());
    }

    /** Push mode: we broadcast ourselves and hand the courier the transaction hash. */
    public String authorization(Challenge challenge, String txHash) {
        String payer = "did:pkh:eip155:" + network.chainId() + ":" + wallet.address();
        return new Credential(challenge.toEcho(),
                Map.of("type", "hash", "hash", txHash), payer).toAuthorization();
    }

    public String explorerUrl(String txHash) {
        return network.transactionUrl(txHash);
    }
}
