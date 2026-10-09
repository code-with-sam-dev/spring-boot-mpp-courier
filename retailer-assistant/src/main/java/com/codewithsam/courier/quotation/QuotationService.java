package com.codewithsam.courier.quotation;

import com.codewithsam.courier.config.CourierProperties;
import com.codewithsam.courier.payment.MppPaymentClient;
import com.codewithsam.courier.payment.PaidButUndeliveredException;
import com.codewithsam.courier.payment.PaidResponse;
import com.codewithsam.courier.payment.PaymentRefusedException;
import com.codewithsam.courier.quotation.PremiumQuoteResult.Status;
import com.codewithsam.courier.wallet.TempoNetwork;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

@Service
public class QuotationService {

    private final CourierProperties courier;
    private final RestClient restClient;
    private final MppPaymentClient paymentClient;
    private final JsonMapper json;
    private final TempoNetwork network;
    private final Clock clock;

    public QuotationService(CourierProperties courier, RestClient.Builder restClient,
            MppPaymentClient paymentClient, JsonMapper json, TempoNetwork network,
            Clock clock) {
        this.courier = courier;
        this.restClient = restClient.build();
        this.paymentClient = paymentClient;
        this.json = json;
        this.network = network;
        this.clock = clock;
    }

    public DeliveryQuote standard(QuoteRequest request) {
        return restClient.get().uri(uri("/quotes/standard", request))
                .retrieve().body(DeliveryQuote.class);
    }

    public PremiumQuoteResult premium(QuoteRequest request) {
        // One parcel, one day, one payment: asking again returns the quote we bought.
        String requestKey = "premium-quote|" + request.fingerprint() + "|"
                + LocalDate.now(clock);
        try {
            PaidResponse paid = paymentClient.get(uri("/quotes/premium", request), requestKey);
            return new PremiumQuoteResult(Status.PAID,
                    json.readValue(paid.body(), DeliveryQuote.class), paid.amountPaid(),
                    paid.txHash(), paid.txHash() == null ? null
                            : network.transactionUrl(paid.txHash()), null);
        } catch (PaymentRefusedException refused) {
            return new PremiumQuoteResult(Status.REFUSED_BY_POLICY, null, BigDecimal.ZERO,
                    null, null, refused.getMessage());
        } catch (PaidButUndeliveredException undelivered) {
            return new PremiumQuoteResult(Status.PAID_BUT_UNDELIVERED, null, null,
                    undelivered.txHash(), network.transactionUrl(undelivered.txHash()),
                    undelivered.getMessage());
        }
    }

    public QuoteComparison compare(QuoteRequest request) {
        return new QuoteComparison(standard(request), premium(request));
    }

    URI uri(String path, QuoteRequest request) {
        return URI.create(courier.baseUrl() + path + request.toQuery());
    }
}
