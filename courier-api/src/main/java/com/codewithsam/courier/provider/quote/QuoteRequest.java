package com.codewithsam.courier.provider.quote;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Locale;

public record QuoteRequest(
        @NotBlank String from,
        @NotBlank String to,
        @NotNull @DecimalMin("0.1") @DecimalMax("30") BigDecimal weightKg) {

    /** The same parcel always gives the same fingerprint, however it was typed. */
    public String fingerprint() {
        return String.join("|",
                from.strip().toLowerCase(Locale.ROOT),
                to.strip().toLowerCase(Locale.ROOT),
                weightKg.stripTrailingZeros().toPlainString());
    }
}
