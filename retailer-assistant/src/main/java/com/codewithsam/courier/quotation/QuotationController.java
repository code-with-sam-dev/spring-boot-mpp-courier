package com.codewithsam.courier.quotation;

import com.codewithsam.courier.payment.PaymentAuditEntry;
import com.codewithsam.courier.payment.PaymentAuditService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The same operations as the MCP tools, for curl. */
@RestController
@RequestMapping("/assistant")
public class QuotationController {

    private final QuotationService quotations;
    private final PaymentAuditService audit;

    public QuotationController(QuotationService quotations, PaymentAuditService audit) {
        this.quotations = quotations;
        this.audit = audit;
    }

    @GetMapping("/quotes/standard")
    public DeliveryQuote standard(@Valid QuoteRequest request) {
        return quotations.standard(request);
    }

    @GetMapping("/quotes/premium")
    public PremiumQuoteResult premium(@Valid QuoteRequest request) {
        return quotations.premium(request);
    }

    @GetMapping("/quotes/compare")
    public QuoteComparison compare(@Valid QuoteRequest request) {
        return quotations.compare(request);
    }

    @GetMapping("/payments")
    public List<PaymentAuditEntry> payments() {
        return audit.entries();
    }
}
