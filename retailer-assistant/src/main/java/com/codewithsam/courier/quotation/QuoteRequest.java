package com.codewithsam.courier.quotation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.web.util.UriComponentsBuilder;

public record QuoteRequest(
        @NotBlank String from,
        @NotBlank String to,
        @NotNull @DecimalMin("0.1") @DecimalMax("30") BigDecimal weightKg) {

    public String toQuery() {
        return UriComponentsBuilder.newInstance()
                .queryParam("from", from)
                .queryParam("to", to)
                .queryParam("weightKg", weightKg.toPlainString())
                .encode().build().toUriString();
    }

    public String fingerprint() {
        return String.join("|", from.strip().toLowerCase(Locale.ROOT),
                to.strip().toLowerCase(Locale.ROOT),
                weightKg.stripTrailingZeros().toPlainString());
    }
}
