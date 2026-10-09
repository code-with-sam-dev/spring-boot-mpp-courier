package com.codewithsam.courier.provider.quote;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StandardQuoteController {

    private final RateCard rateCard;

    public StandardQuoteController(RateCard rateCard) {
        this.rateCard = rateCard;
    }

    @GetMapping("/quotes/standard")
    public QuoteResponse standardQuote(@Valid QuoteRequest request) {
        return rateCard.standard(request);
    }
}
