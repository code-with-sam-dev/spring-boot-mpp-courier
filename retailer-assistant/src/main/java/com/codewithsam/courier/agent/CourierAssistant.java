package com.codewithsam.courier.agent;

import com.codewithsam.courier.payment.PaymentAuditEntry;
import com.codewithsam.courier.payment.PaymentAuditService;
import com.codewithsam.courier.quotation.DeliveryQuote;
import com.codewithsam.courier.quotation.PremiumQuoteResult;
import com.codewithsam.courier.quotation.QuotationService;
import com.codewithsam.courier.quotation.QuoteRequest;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * The tools an AI application can call over MCP. MCP carries the tool call;
 * the payment inside premium_delivery_quote is MPP, over plain HTTP.
 */
@Component
public class CourierAssistant {

    private final QuotationService quotations;
    private final PaymentAuditService audit;

    public CourierAssistant(QuotationService quotations, PaymentAuditService audit) {
        this.quotations = quotations;
        this.audit = audit;
    }

    @McpTool(name = "standard_delivery_quotes",
            description = "Free. Standard delivery price (GBP) and days for a parcel.")
    public DeliveryQuote standardDeliveryQuotes(
            @McpToolParam(description = "Collection town, e.g. London") String from,
            @McpToolParam(description = "Delivery town, e.g. Manchester") String to,
            @McpToolParam(description = "Parcel weight in kg, 0.1 to 30")
            BigDecimal weightKg) {
        return quotations.standard(new QuoteRequest(from, to, weightKg));
    }

    @McpTool(name = "premium_delivery_quote",
            description = "COSTS MONEY. Buys the premium courier's next-day quote. "
                    + "The courier charges a fee per request (0.05 pathUSD on Tempo "
                    + "testnet), paid automatically only if the payment policy allows "
                    + "it: per-request limit, daily budget and approved couriers. "
                    + "Returns the quote, the fee paid and the transaction, or the "
                    + "policy's reason for refusing.")
    public PremiumQuoteResult premiumDeliveryQuote(
            @McpToolParam(description = "Collection town, e.g. London") String from,
            @McpToolParam(description = "Delivery town, e.g. Manchester") String to,
            @McpToolParam(description = "Parcel weight in kg, 0.1 to 30")
            BigDecimal weightKg) {
        return quotations.premium(new QuoteRequest(from, to, weightKg));
    }

    @McpTool(name = "payment_audit",
            description = "Read only. Every payment decision so far: approved, "
                    + "refused, paid, delivered, with amounts and transaction links.")
    public List<PaymentAuditEntry> paymentAudit() {
        return audit.entries();
    }
}
