package com.codewithsam.courier.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class PathUsdTest {

    @Test
    void fiveCentsIsFiftyThousandBaseUnits() {
        assertThat(PathUsd.toBaseUnits(new BigDecimal("0.05")))
                .isEqualTo(BigInteger.valueOf(50_000));
    }

    @Test
    void baseUnitsConvertBackToDollars() {
        assertThat(PathUsd.fromBaseUnits(BigInteger.valueOf(5_000_000)))
                .isEqualByComparingTo("5.00");
    }

    @Test
    void moreThanSixDecimalsIsRejected() {
        assertThatThrownBy(() -> PathUsd.toBaseUnits(new BigDecimal("0.0000001")))
                .isInstanceOf(ArithmeticException.class);
    }
}
