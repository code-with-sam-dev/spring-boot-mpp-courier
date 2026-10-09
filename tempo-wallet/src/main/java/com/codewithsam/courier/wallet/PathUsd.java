package com.codewithsam.courier.wallet;

import java.math.BigDecimal;
import java.math.BigInteger;

/** pathUSD, the test stablecoin on Tempo Moderato. A TIP-20 token with 6 decimals. */
public final class PathUsd {

    public static final String ADDRESS = "0x20c0000000000000000000000000000000000000";
    public static final int DECIMALS = 6;

    private PathUsd() {
    }

    /** 0.05 becomes 50000. Throws if the amount has more than 6 decimal places. */
    public static BigInteger toBaseUnits(BigDecimal dollars) {
        return dollars.movePointRight(DECIMALS).toBigIntegerExact();
    }

    public static BigDecimal fromBaseUnits(BigInteger units) {
        return new BigDecimal(units).movePointLeft(DECIMALS);
    }
}
