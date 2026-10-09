package com.codewithsam.courier.provider.quote;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/** Delivery prices in GBP. Paying for a quote is separate from paying for delivery. */
@Service
public class RateCard {

    public QuoteResponse standard(QuoteRequest request) {
        return quote("Parcel Post Standard", "3.20", "0.85", request, 3);
    }

    public QuoteResponse premium(QuoteRequest request) {
        return quote("Swift Courier Premium", "6.40", "1.10", request, 1);
    }

    private static QuoteResponse quote(String courier, String base, String perKg,
            QuoteRequest request, int days) {
        BigDecimal price = new BigDecimal(base)
                .add(new BigDecimal(perKg).multiply(request.weightKg()))
                .setScale(2, RoundingMode.HALF_UP);
        return new QuoteResponse(courier, price, "GBP", days);
    }
}
