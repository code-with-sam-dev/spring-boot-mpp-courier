package com.codewithsam.courier.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.crypto.transaction.type.Transaction1559;

class TempoWalletTest {

    @Test
    void theHashIsKnownBeforeAnythingIsBroadcast() throws Exception {
        Credentials payer = Credentials.create(Keys.createEcKeyPair());
        RawTransaction tx = RawTransaction.createTransaction(42431, BigInteger.ONE,
                BigInteger.valueOf(600_000), PathUsd.ADDRESS, BigInteger.ZERO,
                "0x95777d59", BigInteger.TEN, BigInteger.TWO.multiply(BigInteger.TEN));

        SignedTransfer signed = TempoWallet.sign(tx, 42431, payer);

        var decoded = (SignedRawTransaction)
                TransactionDecoder.decode(signed.rawTransaction());
        assertThat(decoded.getFrom()).isEqualToIgnoringCase(payer.getAddress());
        assertThat(((Transaction1559) decoded.getTransaction()).getChainId())
                .isEqualTo(42431L);
        assertThat(signed.hash()).startsWith("0x").hasSize(66);
        assertThat(TempoWallet.sign(tx, 42431, payer).hash()).isEqualTo(signed.hash());
    }
}
