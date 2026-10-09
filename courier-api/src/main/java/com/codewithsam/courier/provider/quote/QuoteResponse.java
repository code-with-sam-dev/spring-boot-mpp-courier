package com.codewithsam.courier.provider.quote;

import java.math.BigDecimal;

/** What delivering the parcel would cost the retailer, and how long it takes. */
public record QuoteResponse(String courier, BigDecimal price, String currency, int days) {
}
