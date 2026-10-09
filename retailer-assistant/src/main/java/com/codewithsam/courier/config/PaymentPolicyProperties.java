package com.codewithsam.courier.config;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** The payment authority the assistant has. Nothing outside these limits is paid. */
@ConfigurationProperties("assistant.payment.policy")
public record PaymentPolicyProperties(
        BigDecimal maxPerRequest,
        BigDecimal dailyBudget,
        List<String> allowedRecipients,
        String currency,
        long chainId) {
}
