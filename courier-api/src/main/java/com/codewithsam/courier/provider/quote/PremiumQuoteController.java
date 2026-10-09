package com.codewithsam.courier.provider.quote;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import com.codewithsam.courier.provider.misbehaviour.LostResponseSimulator;
import com.codewithsam.courier.provider.payment.PaymentGate;
import com.codewithsam.courier.provider.redemption.Redemption;
import com.codewithsam.courier.provider.redemption.RedemptionService;
import com.stripe.mpp.Receipt;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PremiumQuoteController {

    private final RateCard rateCard;
    private final PaymentGate paymentGate;
    private final RedemptionService redemptions;
    private final LostResponseSimulator lostResponses;

    public PremiumQuoteController(RateCard rateCard, PaymentGate paymentGate,
            RedemptionService redemptions, LostResponseSimulator lostResponses) {
        this.rateCard = rateCard;
        this.paymentGate = paymentGate;
        this.redemptions = redemptions;
        this.lostResponses = lostResponses;
    }

    @GetMapping("/quotes/premium")
    public ResponseEntity<QuoteResponse> premiumQuote(
            @Valid QuoteRequest request,
            @RequestHeader(name = AUTHORIZATION, required = false) String authorization,
            @RequestHeader(name = "Idempotency-Key", required = false)
            String idempotencyKey) {
        // Already paid for this exact request, but the answer never arrived.
        Optional<Redemption> earlier =
                redemptions.findEarlier(authorization, idempotencyKey, request);
        if (earlier.isPresent()) {
            return paid(earlier.get().quote(), earlier.get().receipt());
        }

        Receipt receipt = paymentGate.charge(authorization);
        QuoteResponse quote = rateCard.premium(request);
        redemptions.record(receipt, authorization, idempotencyKey, request, quote);
        lostResponses.afterPaidResponseBuilt();
        return paid(quote, receipt.toPaymentReceipt());
    }

    private static ResponseEntity<QuoteResponse> paid(QuoteResponse quote,
            String receipt) {
        return ResponseEntity.ok()
                .header("Payment-Receipt", receipt)
                .cacheControl(CacheControl.empty().cachePrivate())
                .body(quote);
    }
}
