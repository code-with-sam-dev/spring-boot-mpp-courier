package com.codewithsam.courier.quotation;

import com.codewithsam.courier.config.CourierProperties;
import com.codewithsam.courier.payment.PaidResponse;
import com.codewithsam.courier.payment.UnsafePaymentClient;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Exists only under the "break" profile: the unsafe client, for the demo of what fails. */
@RestController
@Profile("break")
public class BreakChapterController {

    private final UnsafePaymentClient unsafeClient;
    private final CourierProperties courier;

    public BreakChapterController(UnsafePaymentClient unsafeClient, CourierProperties courier) {
        this.unsafeClient = unsafeClient;
        this.courier = courier;
    }

    @GetMapping("/break/quotes/premium")
    public PaidResponse unsafePremium(@Valid QuoteRequest request) {
        return unsafeClient.get(
                URI.create(courier.baseUrl() + "/quotes/premium" + request.toQuery()));
    }
}
