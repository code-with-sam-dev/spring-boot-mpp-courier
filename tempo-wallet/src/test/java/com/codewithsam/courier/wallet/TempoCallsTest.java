package com.codewithsam.courier.wallet;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class TempoCallsTest {

    private static final String COURIER = "0xb66dd21779af02554d03e2a1e26d63cc3db1ea65";
    private static final String MEMO = "0x" + "ab".repeat(32);

    @Test
    void transferWithMemoUsesTheTip20Selector() {
        BigInteger fiveCents = BigInteger.valueOf(50_000);
        String data = TempoCalls.transferWithMemo(COURIER, fiveCents, MEMO);

        assertThat(data).startsWith("0x95777d59");
        // selector + three 32-byte words
        assertThat(data).hasSize(2 + 8 + 3 * 64);
    }

    @Test
    void transferWithMemoCarriesRecipientAmountAndMemo() {
        BigInteger fiveCents = BigInteger.valueOf(50_000);
        String data = TempoCalls.transferWithMemo(COURIER, fiveCents, MEMO);

        assertThat(data).contains(COURIER.substring(2));
        assertThat(data).contains(String.format("%064x", 50_000));
        assertThat(data).endsWith("ab".repeat(32));
    }

    @Test
    void balanceOfUsesTheErc20Selector() {
        assertThat(TempoCalls.balanceOf(COURIER)).startsWith("0x70a08231");
    }
}
